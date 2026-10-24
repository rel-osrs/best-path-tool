package com.bestpath;

import com.bestpath.api.BestPathApi;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.ApiReplies;
import com.bestpath.route.ApiRequest;
import com.bestpath.route.ApiRoutes;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteService;
import com.bestpath.route.TransportSettings;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * The public API (docs/API.md), modelled on the Shortest Path plugin's: other plugins post RuneLite
 * {@link PluginMessage}s in namespace {@value BestPathApi#NAMESPACE} and Best Path answers the same way. Messages may
 * come on any thread: each hops to the client thread, where the player snapshot is taken; searches run on the route
 * worker as for any route; answers are posted on the client thread. A route asked for here is shown the same way as
 * Quest Helper's and the player's own ({@code showRoute}).
 */
final class ApiBridge
{
	/** Most queries waiting or searching at once; more are answered BUSY. */
	static final int MAX_QUERIES = 4;

	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final Supplier<RouteService> routes;
	private final Supplier<PlayerSnapshot> snapshot;
	private final Supplier<TransportSettings> settings;
	/** The "Routes from other plugins" setting. */
	private final BooleanSupplier enabled;
	/** Shows a route (client thread): the plugin's own way of asking for one. */
	private final Consumer<RouteRequest> showRoute;
	private final ApiRoutes apiRoutes = new ApiRoutes(this::post);
	private final AtomicInteger queries = new AtomicInteger();

	ApiBridge(ClientThread clientThread, EventBus eventBus, Supplier<RouteService> routes, Supplier<PlayerSnapshot> snapshot,
		Supplier<TransportSettings> settings, BooleanSupplier enabled, Consumer<RouteRequest> showRoute)
	{
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.routes = routes;
		this.snapshot = snapshot;
		this.settings = settings;
		this.enabled = enabled;
		this.showRoute = showRoute;
	}

	/** A message in namespace {@value BestPathApi#NAMESPACE} (any thread). Unknown names (our own answers too) are ignored. */
	void onMessage(PluginMessage m)
	{
		Map<String, Object> data = m.getData();
		switch (m.getName())
		{
			case BestPathApi.PATH:
				clientThread.invokeLater(() -> path(ApiRequest.read(data)));
				break;
			case BestPathApi.CLEAR:
				clientThread.invokeLater(() -> clear(ApiRequest.read(data).getSource()));
				break;
			case BestPathApi.QUERY:
				clientThread.invokeLater(() -> query(ApiRequest.read(data)));
				break;
			case BestPathApi.GET_TARGET:
				clientThread.invokeLater(() -> currentTarget(data == null ? null : data.get(BestPathApi.ID)));
				break;
			case BestPathApi.PING:
				post(new ApiRoutes.Out(BestPathApi.PONG, ApiReplies.pong(data == null ? null : data.get(BestPathApi.ID))));
				break;
			default:
				break;
		}
	}

	/** A route was published (route worker or calling thread). */
	void published(RouteService from, RouteResult r)
	{
		apiRoutes.published(r, start(from));
	}

	/** The current step changed (client thread). */
	void progress(Progress p)
	{
		RouteService service = routes.get();
		if (service != null)
		{
			apiRoutes.progress(p, service.getCurrent());
		}
	}

	/** The player arrived (client thread). */
	void arrived(RouteResult r)
	{
		apiRoutes.arrived(r);
	}

	/** Client thread. */
	private void path(ApiRequest a)
	{
		RouteService service = routes.get();
		if (service == null)
		{
			return;
		}
		RouteRequest route = apiRoutes.path(a, enabled.getAsBoolean(), service.getCurrent());
		if (route != null)
		{
			showRoute.accept(route);
			apiRoutes.started(service.getCurrent(), start(service));
		}
	}

	/** Client thread: clears the route shown when it is this source's. */
	private void clear(String source)
	{
		RouteService service = routes.get();
		if (service != null && enabled.getAsBoolean() && apiRoutes.clear(source, service.getCurrent()))
		{
			service.clear();
		}
	}

	/** Client thread: a search whose answer goes only to the caller. */
	private void query(ApiRequest a)
	{
		RouteService service = routes.get();
		if (service == null)
		{
			return;
		}
		if (!a.isValid() || !a.hasTargets())
		{
			post(failure(a, BestPathApi.REASON_INVALID, -1, a.isValid() ? "No target" : a.getProblem()));
			return;
		}
		PlayerSnapshot player = snapshot.get();
		if (player == null)
		{
			post(failure(a, BestPathApi.REASON_NOT_LOGGED_IN, -1, "Log in to find a route."));
			return;
		}
		int s = a.getStart();
		if (s != -1)
		{
			player = player.at(Tiles.x(s), Tiles.y(s), Tiles.plane(s));
		}
		int from = Tiles.pack(player.getX(), player.getY(), player.getPlane());
		if (queries.incrementAndGet() > MAX_QUERIES)
		{
			queries.decrementAndGet();
			post(failure(a, BestPathApi.REASON_BUSY, from, "Too many queries at once; ask again later."));
			return;
		}
		Object id = a.getId();
		service.query(a.toRoute(), player, settings.get(), r ->
		{
			queries.decrementAndGet();
			post(new ApiRoutes.Out(BestPathApi.RESULT, ApiReplies.result(id, r, from)));
		});
	}

	/** Client thread. */
	private void currentTarget(Object id)
	{
		RouteService service = routes.get();
		RouteRequest shown = service == null || !service.hasDestination() ? null : service.getCurrent().getRequest();
		post(new ApiRoutes.Out(BestPathApi.CURRENT_TARGET, ApiReplies.currentTarget(id, shown, shown == null ? -1 : start(service))));
	}

	private static ApiRoutes.Out failure(ApiRequest a, String reason, int start, String message)
	{
		return new ApiRoutes.Out(BestPathApi.RESULT, ApiReplies.failure(a.getId(), reason, start, message));
	}

	/** Where the route shown was searched from (packed), or -1. */
	private static int start(RouteService service)
	{
		PlayerSnapshot p = service == null ? null : service.getLastPlayer();
		return p == null ? -1 : Tiles.pack(p.getX(), p.getY(), p.getPlane());
	}

	/** Posts on the client thread (any thread; never blocks: the order of calls is the order of posts). */
	private void post(ApiRoutes.Out out)
	{
		clientThread.invokeLater(() -> eventBus.post(new PluginMessage(BestPathApi.NAMESPACE, out.getName(), out.getData())));
	}
}

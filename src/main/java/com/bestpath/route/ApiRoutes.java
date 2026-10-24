package com.bestpath.route;

import com.bestpath.api.BestPathApi;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Which shown route another plugin asked for through the public API ({@link BestPathApi}, docs/API.md), and what to
 * tell that plugin about it: found (again after each new search), no route, arrived, cleared, replaced, and the step
 * the player is on. Only the plugin whose route it is hears about it (the route header says who set it). The newest
 * destination wins, as with Quest Helper: the player's own, a clue's or another plugin's replaces an API route, which
 * is then told REPLACED.
 * <p>
 * Pure (no RuneLite event bus): what to send goes to the sink, in order, while this object's lock is held, so the
 * sink must only queue it (the plugin posts it on the client thread). Called on the client thread (messages, progress,
 * arrival) and on the route worker (results).
 */
public final class ApiRoutes
{
	/** A message to send: its name ({@link BestPathApi#STATUS}...) and data. Immutable. */
	public static final class Out
	{
		private final String name;
		private final Map<String, Object> data;

		public Out(String name, Map<String, Object> data)
		{
			this.name = name;
			this.data = Collections.unmodifiableMap(data);
		}

		public String getName()
		{
			return name;
		}

		public Map<String, Object> getData()
		{
			return data;
		}

		@Override
		public String toString()
		{
			return name + " " + data;
		}
	}

	private final Consumer<Out> sink;

	/** The message that asked for the API route shown, or null when the route shown is not one (tests read it). */
	ApiRequest owner;
	/** Its route. */
	private RouteRequest request;
	/** The generation of the last result the owner was told about (-1: none yet). */
	private long toldGeneration = -1;
	/** The step the owner was last told the player is on (-1: none yet). */
	private int toldStep = -1;
	private boolean arrived;
	/**
	 * Results older than this generation are not about the owner's route: an older search may still publish between
	 * {@link #path} and the route service taking the new request ({@link #started}). Long.MAX_VALUE until then; -1
	 * with no API route.
	 */
	private long since = -1;

	public ApiRoutes(Consumer<Out> sink)
	{
		this.sink = Objects.requireNonNull(sink);
	}

	/**
	 * A {@code path} message. Returns the route to search for (the plugin then asks for it as for any destination), or
	 * null when nothing is to be searched: a bad message or the switch off (the caller is told), or the same route
	 * already shown.
	 *
	 * @param enabled "Routes from other plugins"
	 * @param shown   the route shown now
	 */
	public synchronized RouteRequest path(ApiRequest a, boolean enabled, RouteResult shown)
	{
		if (!a.isValid() || !a.hasTargets())
		{
			send(BestPathApi.STATUS, ApiReplies.status(a.getId(), a.getSource(), BestPathApi.STATUS_INVALID,
				a.isValid() ? "No target" : a.getProblem()));
			return null;
		}
		if (!enabled)
		{
			send(BestPathApi.STATUS, ApiReplies.status(a.getId(), a.getSource(), BestPathApi.STATUS_DISABLED,
				"The player turned off \"Routes from other plugins\""));
			return null;
		}
		RouteRequest route = a.toRoute();
		if (owner != null && route.equals(request) && route.equals(shown.getRequest()) && !arrived
			&& shown.getStatus() != RouteStatus.ERROR)
		{
			owner = a; // the same route again (plugins may send each step's place more than once): keep it, newest id
			return null;
		}
		drop(BestPathApi.STATUS_REPLACED, null);
		owner = a;
		request = route;
		toldGeneration = -1;
		toldStep = -1;
		arrived = false;
		since = Long.MAX_VALUE;
		return route;
	}

	/**
	 * The route service took the route {@link #path} returned (or failed it at once: not logged in); {@code current}
	 * is the result it shows now, searched from {@code start} (packed, -1 when not known).
	 */
	public synchronized void started(RouteResult current, int start)
	{
		if (owner == null || since != Long.MAX_VALUE)
		{
			return;
		}
		since = current.getGeneration();
		published(current, start);
	}

	/** A {@code clear} message from {@code source}: true when the route shown is that source's (the plugin clears it). */
	public synchronized boolean clear(String source, RouteResult shown)
	{
		return owner != null && owner.getSource().equals(source) && request.equals(shown.getRequest());
	}

	/**
	 * A published route result (route worker or calling thread). {@code start}: where the route was searched from
	 * (packed, core Tiles; -1 when not known).
	 */
	public synchronized void published(RouteResult r, int start)
	{
		if (owner == null || r.getGeneration() < since)
		{
			return;
		}
		switch (r.getStatus())
		{
			case IDLE:
				drop(BestPathApi.STATUS_CLEARED, r);
				return;
			case ARRIVED:
				if (arrived)
				{
					clearOwner();
				}
				else
				{
					drop(BestPathApi.STATUS_ARRIVED, r);
				}
				return;
			default:
				break;
		}
		if (!request.equals(r.getRequest()))
		{
			drop(BestPathApi.STATUS_REPLACED, null); // another destination
			return;
		}
		if (r.getStatus() == RouteStatus.CALCULATING || r.isFindingWhy() || r.getGeneration() == toldGeneration)
		{
			return; // the answer follows; or the same route with its cards made again
		}
		toldGeneration = r.getGeneration();
		toldStep = -1;
		arrived = false;
		send(BestPathApi.STATUS, ApiReplies.status(owner.getId(), owner.getSource(), state(r.getStatus()), r, start));
	}

	/** The player is on another step of the route shown (client thread). */
	public synchronized void progress(Progress p, RouteResult shown)
	{
		if (owner == null || p == null || !p.isFor(shown) || !request.equals(shown.getRequest())
			|| shown.getGeneration() != toldGeneration || p.getStep() == toldStep)
		{
			return;
		}
		toldStep = p.getStep();
		send(BestPathApi.PROGRESS, ApiReplies.progress(owner.getId(), owner.getSource(), p.getStep()));
	}

	/**
	 * The player arrived at the route shown (client thread; once per route). The route may stay shown (the player's
	 * "Clear route on arrival" off), so this is told here, not only when the route ends.
	 */
	public synchronized void arrived(RouteResult shown)
	{
		if (owner == null || arrived || !request.equals(shown.getRequest()))
		{
			return;
		}
		arrived = true;
		send(BestPathApi.STATUS, ApiReplies.status(owner.getId(), owner.getSource(), BestPathApi.STATUS_ARRIVED, shown, -1));
	}

	private static String state(RouteStatus s)
	{
		switch (s)
		{
			case READY:
				return BestPathApi.STATUS_FOUND;
			case NO_ROUTE:
				return BestPathApi.STATUS_NO_ROUTE;
			case NO_DATA:
				return BestPathApi.STATUS_NO_DATA;
			default:
				return BestPathApi.STATUS_ERROR;
		}
	}

	/** Tells the owner its route ended this way, and forgets it. */
	private void drop(String state, RouteResult r)
	{
		if (owner != null)
		{
			send(BestPathApi.STATUS, ApiReplies.status(owner.getId(), owner.getSource(), state, r, -1));
		}
		clearOwner();
	}

	private void clearOwner()
	{
		owner = null;
		request = null;
		toldGeneration = -1;
		toldStep = -1;
		arrived = false;
		since = -1;
	}

	private void send(String name, Map<String, Object> data)
	{
		sink.accept(new Out(name, data));
	}
}

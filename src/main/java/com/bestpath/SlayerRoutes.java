package com.bestpath;

import com.bestpath.core.data.SlayerPlaces;
import com.bestpath.core.data.WildernessZones;
import com.bestpath.player.SlayerWatch;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteService;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.SlayerPreferred;
import com.bestpath.route.SlayerTask;
import com.bestpath.route.SlayerTimes;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

/**
 * The Slayer task on the Functions tab: reads the task from the game's vars ({@link SlayerWatch}), shows it with its
 * places' travel times, keeps the player's preferred places (per account) and, with "Automatic Slayer routes" on,
 * routes to the nearest place when a new task is given. Client thread, except where a method says otherwise.
 */
@Slf4j
final class SlayerRoutes
{
	/**
	 * Per-account key: the Slayer task the plugin saw go into task storage, so the sidebar can show it;
	 * the game's vars name only its master and boss.
	 */
	static final String STORED_KEY = "slayerStored";
	/** Per-account key: the Slayer places the player marked preferred, per task. */
	static final String PREFERRED_KEY = "slayerPreferred";

	private final BestPathConfig config;
	private final ProfileStore store;
	private final PluginContext plugin;
	private final SlayerWatch watch;
	/** Travel times to the task's places (worked out on the route worker). */
	private final SlayerTimes times = new SlayerTimes();
	/** This account's preferred places. */
	private SlayerPreferred preferred = SlayerPreferred.NONE;
	/** The task the watch handed out last (null: none since the plugin started). */
	private SlayerTask before;
	/** The last task read (state TASK), to tell a kill from a new task; null after logging out. */
	private SlayerTask lastTask;
	/**
	 * A route to the nearest preferred place just asked for, and the route to all places to search instead when none
	 * can be reached ({@link SlayerTask#nearestFallback}); null for none. Read on the route worker too.
	 */
	private final AtomicReference<RouteRequest[]> fallback = new AtomicReference<>();

	SlayerRoutes(Client client, BestPathConfig config, ProfileStore store, PluginContext plugin)
	{
		this.config = config;
		this.store = store;
		this.plugin = plugin;
		// logged out: nothing changes (no vars are read)
		this.watch = new SlayerWatch(client::getVarpValue, client::getVarbitValue, text -> store.write(null, STORED_KEY, text));
	}

	/** The map data's Slayer places loaded: the task is read on the next tick. */
	void setData(SlayerPlaces places, WildernessZones wilderness)
	{
		watch.setData(places, wilderness);
	}

	/** This account's stored task and preferred places (start, login, account switch). */
	void loadProfile()
	{
		watch.loadStored(store.read(STORED_KEY)); // the task this account put in storage, if the plugin saw it
		preferred = SlayerPreferred.fromText(store.read(PREFERRED_KEY));
		showPreferred();
	}

	/** A varbit or varp changed: the task is read again on the next tick when it is one of the task's vars. */
	void onVarChanged(int varbitId, int varpId)
	{
		watch.onVarChanged(varbitId, varpId);
	}

	/** Each game tick: the task, read once after one of its vars changed. */
	void onTick()
	{
		taskChanged(watch.changedTask());
	}

	/** Logged in or out. */
	void onLogin(boolean loggedIn)
	{
		taskChanged(watch.onLogin(loggedIn));
	}

	/**
	 * The task the game's vars name now (null: nothing new). With "Automatic Slayer routes" on, a task just given
	 * starts a route to the nearest place where kills count.
	 */
	private void taskChanged(SlayerTask task)
	{
		if (task == null)
		{
			return;
		}
		SlayerTask last = lastTask;
		boolean fresh = SlayerTask.isNewTask(before, last, task);
		if (SlayerTask.looksNew(before, task))
		{
			// every "new task" decision, to see why if the sidebar ever opens by itself when it shouldn't
			log.debug("Slayer: {} ({} left) after {} ({} left), last task {} ({} left): {}", task.identity(), task.getLeft(),
				before.identity(), before.getLeft(), last == null ? "none" : last.identity(),
				last == null ? 0 : last.getLeft(), fresh ? "a new task" : "a kill, not a new task");
		}
		if (task.getState() == SlayerTask.State.TASK)
		{
			lastTask = task;
		}
		else if (task.getState() == SlayerTask.State.LOGGED_OUT)
		{
			lastTask = null; // another account may log in
		}
		if (fresh && config.allowSlayerRouting() && !config.preferSlayerOverlay())
		{
			// among the player's preferred places for this task, if any
			SlayerTask marked = task.withPreferred(preferred);
			RouteRequest nearest = marked.nearest();
			if (nearest != null)
			{
				routeToNearest(nearest, marked.nearestFallback(l -> true));
			}
		}
		before = task;
		show(task);
		if (fresh)
		{
			plugin.openSidebar(config.openForSlayer(), true); // after the page has the new task queued
		}
	}

	/**
	 * The Functions tab shows this task (null: nothing new), with its places' travel times once they are known (a new
	 * task starts working them out on the route worker).
	 */
	private void show(SlayerTask task)
	{
		if (task == null)
		{
			return;
		}
		task = task.withPreferred(preferred); // first, so their travel times are worked out first
		if (task.getState() != SlayerTask.State.TASK)
		{
			times.forget(); // logged out, task done: the next task's times from where the player is then
		}
		else if (times.needs(task))
		{
			RouteService service = plugin.routes();
			PlayerTracker t = plugin.tracker();
			PlayerSnapshot snap = t == null ? null : t.snapshot();
			if (service != null && snap != null)
			{
				times.start(task, service, snap, plugin.settings(), () -> plugin.onClient(this::showCurrent));
			}
		}
		SlayerTask shown = times.apply(task); // (the times keep the preferred marks)
		plugin.toSidebar(sidebar -> sidebar.showSlayer(shown));
	}

	private void showCurrent()
	{
		if (watch.current() != null)
		{
			show(watch.current());
		}
	}

	/** The sidebar's "Update" on the travel times: worked out again from where the player stands now. */
	void updateTimes()
	{
		if (watch.current() != null)
		{
			times.forget();
			show(watch.current());
		}
	}

	/** The player marked or unmarked a place as preferred for a task. */
	void prefer(String task, String place, boolean on)
	{
		setPreferred(preferred.with(task, place, on));
	}

	/** Saves (when changed) and shows the preferred places, and the Slayer page with them. */
	private void setPreferred(SlayerPreferred p)
	{
		if (p.equals(preferred))
		{
			return;
		}
		preferred = p;
		store.write(null, PREFERRED_KEY, p.toText()); // logged out: kept until the next account loads its own
		showPreferred();
	}

	/** The Settings tab's list and the Slayer page, with this account's preferred places. */
	private void showPreferred()
	{
		SlayerPreferred p = preferred;
		plugin.toSidebar(sidebar -> sidebar.showSlayerPreferred(p));
		showCurrent();
	}

	/**
	 * The route to the nearest Slayer place; {@code ifNoRoute} (null: none) is searched instead when it goes only to
	 * preferred places and no route reaches one ({@link #routeFound}).
	 */
	void routeToNearest(RouteRequest nearest, RouteRequest ifNoRoute)
	{
		fallback.set(ifNoRoute == null || ifNoRoute.equals(nearest) ? null : new RouteRequest[]{nearest, ifNoRoute});
		plugin.requestRoute(nearest);
		plugin.remember(nearest);
	}

	/**
	 * Route worker: when the route to the nearest preferred place found no way to any of them, asks for the route to all
	 * places instead.
	 */
	void routeFound(RouteResult r)
	{
		RouteRequest[] f = fallback.get();
		if (f == null || r.getRequest() == null || !r.getRequest().equals(f[0]))
		{
			return;
		}
		if (r.getStatus() == RouteStatus.NO_ROUTE && fallback.compareAndSet(f, null))
		{
			plugin.onClient(() -> plugin.requestRoute(f[1]));
		}
		else if (r.getStatus() == RouteStatus.READY)
		{
			fallback.compareAndSet(f, null);
		}
	}
}

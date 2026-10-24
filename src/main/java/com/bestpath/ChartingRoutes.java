package com.bestpath;

import com.bestpath.core.data.ChartingOrder;
import com.bestpath.core.data.ChartingTasks;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.CoarseSea;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.player.PlayerBoats;
import com.bestpath.route.ChartingProgress;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteService;
import com.bestpath.route.RouteStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;

/**
 * Sea charting on the Functions tab ({@link ChartingWatch}), and routes through a charting set. A set's tasks are done in
 * a planned order (a salesman-style tour): {@link ChartingOrder} plans it on the route worker from where the
 * player is, and the route goes to each task in turn, leg by leg ({@link ChartingProgress#legTile}); once a task is done
 * it goes on to the next one (none left: the route ends). Until the order is back, and if planning fails, the route goes
 * to whichever task is quickest to reach. The order is planned again when a task the plan doesn't have becomes doable.
 * Client thread only, except the planning itself.
 */
@Slf4j
final class ChartingRoutes
{
	/** The duck counts as let go for a duck task when it leaves the inventory within this many tiles of the task's start. */
	static final int DUCK_RANGE = 40;

	private final PlayerTracker tracker;
	private final PluginContext plugin;
	private final ChartingWatch watch;
	/** The charting set the route charts; null for none. */
	private String set;
	/** The progress last read (null before the first read). */
	private ChartingProgress progress;
	/** The set's route needs looking at again (a new set, a plan back, the duck let go or picked up). */
	private boolean routeDirty;

	/**
	 * The main ocean of the map data (already in memory for sailing). The coarse sea planning uses is built from it for
	 * each plan and dropped with it (ocean pathing is rarely used, so it is not kept in memory).
	 */
	private volatile SeaGrid grid;
	/** The planned order of {@link #planSet}'s tasks (task numbers); null: none yet. */
	private List<Integer> plan;
	private String planSet;
	/** The plan asked for last (its number; older answers are dropped) and whether it is still being made. */
	private int planAsked;
	private boolean planning;
	/** A set whose planning failed: its route goes to the quickest task, without asking again. */
	private String planFailed;

	/** The duck task whose duck has been let go (-1: none), and whether the duck was carried at the last look. */
	private int duckOut = -1;
	private boolean duckCarried;

	ChartingRoutes(Client client, PlayerTracker tracker, PluginContext plugin)
	{
		this.tracker = tracker;
		this.plugin = plugin;
		this.watch = new ChartingWatch(client, tracker::bankItems, () -> boatAreas(tracker.boats()));
	}

	/**
	 * The sea areas the player's boats may enter together, for the sea charting hazards; boats not read yet:
	 * {@link ChartingProgress#ANY_BOAT}.
	 */
	static int boatAreas(PlayerBoats b)
	{
		if (b.getStatus() != PlayerBoats.Status.READ)
		{
			return ChartingProgress.ANY_BOAT;
		}
		int m = 0;
		for (Boat boat : b.getBoats())
		{
			m |= boat.areaMask();
		}
		return m;
	}

	/** The map data's charting tasks loaded: the Functions tab shows them on the next tick. */
	void setTasks(ChartingTasks tasks)
	{
		watch.setTasks(tasks);
		plan = null;
		planSet = null;
		planFailed = null;
	}

	/** The map data's main ocean (null: none), for planning a set's order. */
	void setSea(SeaGrid sea)
	{
		grid = sea;
	}

	/** An item container changed: a charting tool carried, taken out or put away; the duck let go or picked up. */
	void onItemsChanged(int containerId)
	{
		watch.onItemsChanged(containerId);
		if (containerId == InventoryID.INV || containerId == InventoryID.WORN)
		{
			boolean carried = watch.carries(ChartingProgress.DUCK);
			if (carried != duckCarried)
			{
				duckCarried = carried;
				duckChanged(carried);
			}
		}
	}

	/**
	 * The duck left the inventory (let go) or came back (picked up). Let go near the start of the set's next duck task:
	 * that task's route goes on to where the duck is picked up.
	 */
	private void duckChanged(boolean carried)
	{
		if (carried)
		{
			if (duckOut >= 0)
			{
				duckOut = -1;
				routeDirty = true;
			}
			return;
		}
		ChartingTasks.Task next = nextTask();
		WorldPoint at = tracker.mapPosition();
		if (next != null && next.isCurrentDuck() && at != null && Math.max(Math.abs(at.getX() - Tiles.x(next.getPlace())),
			Math.abs(at.getY() - Tiles.y(next.getPlace()))) <= DUCK_RANGE)
		{
			duckOut = next.getNumber();
			routeDirty = true;
		}
	}

	/** A varbit changed: a charting task done (or the quest Current Affairs) is read on the next tick. */
	void onVarbitChanged(VarbitChanged e)
	{
		watch.onVarbitChanged(e);
	}

	/** A skill changed: the Sailing level decides which tasks the player can do. */
	void onStatChanged(StatChanged e)
	{
		watch.onStatChanged(e);
	}

	/** Logged in or out: shown at once (logged out, no game tick follows). */
	void onLoginChange()
	{
		watch.onLoginChange();
		duckOut = -1;
		duckCarried = false;
		onTick();
	}

	/** Each tick: the Functions tab's sea charting, when what it shows changed, and the charting set's route. */
	void onTick()
	{
		RouteService service = plugin.routes();
		RouteResult now = service == null ? null : service.getCurrent();
		String was = set;
		if (now != null && now.getRequest() != null)
		{
			set = ChartingProgress.setOf(now.getRequest()); // null: another destination
		}
		else if (now == null || now.getStatus() != RouteStatus.ARRIVED)
		{
			set = null; // cleared; arriving at a task keeps the set until the task is done
		}
		if (set != null && !set.equals(was))
		{
			routeDirty = true;
		}
		ChartingProgress p = watch.onTick();
		if (p != null)
		{
			progress = p;
			ChartingTasks.Task duck = duckOut < 0 ? null : task(p, duckOut);
			if (duck == null || p.isDone(duck))
			{
				duckOut = -1; // picked up and done
			}
		}
		if (set != null && progress != null && progress.isLoggedIn() && (p != null || routeDirty))
		{
			routeDirty = false;
			routeSet(service, now);
		}
		if (p != null)
		{
			plugin.toSidebar(sidebar -> sidebar.showCharting(p));
		}
	}

	/** The set's route: the next leg of its planned order's next task (planned first when needed). */
	private void routeSet(RouteService service, RouteResult now)
	{
		ChartingProgress p = progress;
		List<ChartingTasks.Task> doable = p.doable(set);
		RouteRequest current = now == null ? null : now.getRequest();
		if (doable.isEmpty())
		{
			set = null;
			if (current != null)
			{
				service.clear();
			}
			return;
		}
		if (!set.equals(planFailed) && (plan == null || !set.equals(planSet) || !p.planCovers(set, plan)))
		{
			askPlan(service, doable);
		}
		ChartingTasks.Task next = nextTask();
		RouteRequest r = next == null ? p.route(set)
			: p.routeLeg(set, next, duckOut == next.getNumber(), doable.size());
		if (r != null && (current == null || !Arrays.equals(r.getTargets(), current.getTargets())))
		{
			plugin.requestRoute(r);
		}
	}

	/** The set's next task in the planned order the player can do now; null without a plan for the set. */
	private ChartingTasks.Task nextTask()
	{
		ChartingProgress p = progress;
		return p == null || plan == null || set == null || !set.equals(planSet) ? null : p.nextInPlan(set, plan);
	}

	private static ChartingTasks.Task task(ChartingProgress p, int number)
	{
		for (ChartingTasks.Task t : p.getTasks().getTasks())
		{
			if (t.getNumber() == number)
			{
				return t;
			}
		}
		return null;
	}

	/** Plans the set's order on the route worker, from where the player is; the answer comes back on the client thread. */
	private void askPlan(RouteService service, List<ChartingTasks.Task> doable)
	{
		if (planning && set.equals(planSet))
		{
			return; // one is being made for this set; a task that became doable since is planned in once it is back
		}
		int token = ++planAsked;
		planning = true;
		planSet = set;
		plan = null;
		String s = set;
		WorldPoint at = tracker.mapPosition();
		int start = at == null ? doable.get(0).getPlace() : Tiles.pack(at.getX(), at.getY(), at.getPlane());
		List<ChartingTasks.Task> tasks = List.copyOf(doable);
		SeaGrid sea = grid;
		service.runOnWorker(() ->
		{
			List<Integer> order = null;
			try
			{
				// built for this plan only (a third of a second); without sea data, straight lines
				CoarseSea coarse = sea == null ? null : CoarseSea.build(sea);
				order = numbers(ChartingOrder.plan(coarse, start, tasks));
			}
			catch (RuntimeException e)
			{
				log.warn("Planning the sea charting order failed", e);
			}
			List<Integer> got = order;
			plugin.onClient(() ->
			{
				if (token != planAsked)
				{
					return; // asked again since
				}
				planning = false;
				if (got == null)
				{
					planFailed = s;
				}
				plan = got;
				routeDirty = true;
			});
		});
	}

	private static List<Integer> numbers(List<ChartingTasks.Task> l)
	{
		List<Integer> out = new ArrayList<>(l.size());
		for (ChartingTasks.Task t : l)
		{
			out.add(t.getNumber());
		}
		return out;
	}
}

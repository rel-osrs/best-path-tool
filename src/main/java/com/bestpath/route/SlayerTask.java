package com.bestpath.route;

import com.bestpath.core.data.SlayerPlaces;
import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * The player's Slayer task for the Functions tab: the task the game's vars name, how many are left, and every place to
 * kill its monsters ({@link SlayerPlaces}), so the player can compare places. Konar's tasks count only in the area she
 * names ({@link #AREA_VARP}) and Krystilia's only in the Wilderness (OSRS Wiki); for their tasks, places where kills
 * don't count are left out. Each place may carry its travel time from the player ({@link #withTimes}). Immutable.
 */
public final class SlayerTask
{
	/** The task (column ID of the cache's DB table 113; 98 = a boss). */
	public static final int TASK_VARP = VarPlayerID.SLAYER_TARGET;
	/** How many are left. */
	public static final int COUNT_VARP = VarPlayerID.SLAYER_COUNT;
	/** The area Konar named (0: none). */
	public static final int AREA_VARP = VarPlayerID.SLAYER_AREA;
	/** The boss of a boss task. */
	public static final int BOSS_VARBIT = VarbitID.SLAYER_TARGET_BOSSID;
	/** The master who gave the task. */
	public static final int MASTER_VARBIT = VarbitID.SLAYER_MASTER;
	/** The master of the task in storage (0: none stored). */
	public static final int STORED_MASTER_VARBIT = VarbitID.SLAYER_MASTER_STORED;
	/** The boss of a stored boss task. */
	public static final int STORED_BOSS_VARBIT = VarbitID.SLAYER_TARGET_BOSSID_STORED;
	/** The master whose tasks count only in the Wilderness (by the data's master name). */
	static final String WILDERNESS_MASTER = "Krystilia";

	public enum State
	{
		/** Not logged in: the game's vars are not known. */
		LOGGED_OUT,
		/** The map data has no Slayer file (or is still loading). */
		NO_DATA,
		/** No task (none given, or all killed). */
		NO_TASK,
		TASK
	}

	public static final SlayerTask LOGGED_OUT = new SlayerTask(State.LOGGED_OUT, "", 0, null, null, null, List.of(), -1, null, null);
	public static final SlayerTask NO_DATA = new SlayerTask(State.NO_DATA, "", 0, null, null, null, List.of(), -1, null, null);
	public static final SlayerTask NO_TASK = new SlayerTask(State.NO_TASK, "", 0, null, null, null, List.of(), -1, null, null);

	/**
	 * The task in the Slayer reward "Task storage": shown, never routed to. The game's vars name only its master and, for
	 * a boss task, the boss (varbits 12443, 12444); the monster and how many are left are what the player had when the
	 * plugin saw the task go into storage ({@link #isKnown} false when it didn't see that). Immutable.
	 */
	public static final class Stored
	{
		private final String name;
		private final int left;
		private final String master;
		private final int icon;
		private final String wikiPage;

		Stored(String name, int left, String master, int icon, String wikiPage)
		{
			this.name = name;
			this.left = left;
			this.master = master;
			this.icon = icon;
			this.wikiPage = wikiPage;
		}

		/**
		 * The stored task: {@code taskId} and {@code count} as the plugin saw them before it went into storage (0: not
		 * seen), {@code bossId} varbit 12444, {@code masterId} varbit 12443.
		 */
		public static Stored of(SlayerPlaces data, int taskId, int bossId, int count, int masterId)
		{
			SlayerPlaces.Task task = data == null ? null : data.task(taskId > 0 ? taskId : bossId > 0 ? SlayerPlaces.BOSS_TASK : 0, bossId);
			String master = data == null ? null : data.masterName(masterId);
			if (task == null)
			{
				return new Stored(null, 0, master, -1, null);
			}
			String page = null;
			for (SlayerPlaces.Place p : data.places(task.getRow()))
			{
				if (page == null && !p.getSource().isEmpty())
				{
					int at = p.getSource().indexOf('@');
					page = at < 0 ? p.getSource() : p.getSource().substring(0, at);
				}
			}
			return new Stored(task.getName(), taskId > 0 ? count : 0, master, data.icon(task.getRow()), page == null ? task.getName() : page);
		}

		/** The plugin knows which monster it is. */
		public boolean isKnown()
		{
			return name != null;
		}

		/** "Hellhounds", or null when not known. */
		public String getName()
		{
			return name;
		}

		/** How many were left when it went into storage, 0 when not known. */
		public int getLeft()
		{
			return left;
		}

		/** The master who gave it, or null. */
		public String getMaster()
		{
			return master;
		}

		/** The RuneLite Slayer plugin's picture for it, or -1. */
		public int getIcon()
		{
			return icon;
		}

		/** The Wiki page about it, or null when not known. */
		public String getWikiPage()
		{
			return wikiPage;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Stored))
			{
				return false;
			}
			Stored s = (Stored) o;
			return left == s.left && icon == s.icon && Objects.equals(name, s.name) && Objects.equals(master, s.master);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(name, left, master);
		}
	}

	/** {@link Location#getTicks}: not worked out (yet). */
	public static final int NOT_TIMED = -2;
	/** {@link Location#getTicks}: no route there. */
	public static final int NO_ROUTE = -1;

	/** One place of the task (kills there count for it), with its travel time. */
	public static final class Location
	{
		private final SlayerPlaces.Place place;
		private final boolean assigned;
		private final boolean wilderness;
		/** The points routes go to: the place's, or for Krystilia only those in the Wilderness. */
		private final int[] points;
		private final int ticks;
		private final List<SlayerPlaces.Loot> loot;
		/** The player marked it preferred for this task ({@link SlayerPreferred}). */
		private final boolean preferred;
		/** Its place in the Wiki's list (the order when nothing else tells places apart). */
		private final int index;

		Location(SlayerPlaces.Place place, boolean assigned, boolean wilderness, int[] points, int ticks,
			List<SlayerPlaces.Loot> loot, boolean preferred, int index)
		{
			this.index = index;
			this.place = place;
			this.points = points;
			this.assigned = assigned;
			this.wilderness = wilderness;
			this.ticks = ticks;
			this.loot = Collections.unmodifiableList(loot);
			this.preferred = preferred;
		}

		private Location withTicks(int t)
		{
			return new Location(place, assigned, wilderness, points, t, loot, preferred, index);
		}

		private Location withPreferred(boolean on)
		{
			return on == preferred ? this : new Location(place, assigned, wilderness, points, ticks, loot, on, index);
		}

		/** The player marked it preferred for this task: it comes first, and the nearest place is picked among those. */
		public boolean isPreferred()
		{
			return preferred;
		}

		/** The special loot here (the place's and the master's). */
		public List<SlayerPlaces.Loot> getLoot()
		{
			return loot;
		}

		/** Ticks to get there from the player, {@link #NO_ROUTE}, or {@link #NOT_TIMED}. */
		public int getTicks()
		{
			return ticks;
		}

		/** Tells the place apart from the task's others (its name and first point), for its travel time. */
		public String key()
		{
			return place.getName() + "@" + place.getPoints()[0];
		}

		public SlayerPlaces.Place getPlace()
		{
			return place;
		}

		/**
		 * The points a route goes to: the place's, but on Krystilia's task only those in the Wilderness (a place partly
		 * in it, e.g. the skeletons' Edgeville Dungeon, leads only to its Wilderness part).
		 */
		public int[] getPoints()
		{
			return points.clone();
		}

		/** In the area Konar named. */
		public boolean isAssigned()
		{
			return assigned;
		}

		/** A route can go there (the map data has ground there). */
		public boolean canGo()
		{
			return place.isOnMap();
		}

		/** Any of its points is in the Wilderness. */
		public boolean isWilderness()
		{
			return wilderness;
		}
	}

	private final State state;
	private final String name;
	private final int left;
	private final String master;
	/** The area Konar named, or null (tests read it). */
	final String area;
	private final String rule;
	private final List<Location> locations;
	/** The task's picture (the RuneLite Slayer plugin's item), or -1. */
	private final int icon;
	/** The Wiki page about the task ("Slayer task/Kalphites"), or null. */
	private final String wikiPage;
	/** The task in storage, or null for none. */
	private final Stored stored;

	private SlayerTask(State state, String name, int left, String master, String area, String rule, List<Location> locations,
		int icon, String wikiPage, Stored stored)
	{
		this.stored = stored;
		this.icon = icon;
		this.wikiPage = wikiPage;
		this.state = state;
		this.name = name;
		this.left = left;
		this.master = master;
		this.area = area;
		this.rule = rule;
		this.locations = Collections.unmodifiableList(locations);
	}

	/**
	 * The task the game's vars name.
	 *
	 * @param taskId varp 395, {@code bossId} varbit 4723, {@code count} varp 394, {@code areaId} varp 2096,
	 *               {@code masterId} varbit 4067
	 */
	public static SlayerTask of(SlayerPlaces data, WildernessZones wilderness, int taskId, int bossId, int count, int areaId, int masterId)
	{
		if (data == null || data.isEmpty())
		{
			return NO_DATA;
		}
		if (taskId <= 0 || count <= 0)
		{
			return NO_TASK;
		}
		SlayerPlaces.Task task = data.task(taskId, bossId);
		String master = data.masterName(masterId);
		String area = areaId > 0 ? data.areaName(areaId) : null;
		if (area == null && areaId > 0)
		{
			area = "area " + areaId;
		}
		boolean wildOnly = WILDERNESS_MASTER.equals(master) && area == null;
		List<Location> list = new ArrayList<>();
		for (SlayerPlaces.Place p : task == null ? List.<SlayerPlaces.Place>of() : data.places(task.getRow()))
		{
			boolean assigned = areaId > 0 && p.getArea() == areaId;
			int[] wildPoints = wildernessPoints(p, wilderness);
			boolean wild = wildPoints.length > 0;
			if (areaId > 0 ? !assigned : wildOnly && !wild)
			{
				continue; // Konar, Krystilia: places where kills don't count are not shown at all
			}
			list.add(new Location(p, assigned, wild, wildOnly ? wildPoints : p.getPoints(), NOT_TIMED,
				data.lootFor(p, masterId), false, list.size()));
		}
		sort(list);
		String rule = area != null ? "Konar's task: only kills in the " + withoutThe(area) + " count."
			: wildOnly ? "Krystilia's task: only kills in the Wilderness count." : null;
		String name = task == null ? "Slayer task " + taskId : task.getName();
		return new SlayerTask(State.TASK, name, count, master, area, rule, list, task == null ? -1 : data.icon(task.getRow()),
			wikiPage(name, list), null);
	}

	/**
	 * The player's preferred places first, then Konar's area, then the others; in each the places
	 * on the map data first, then fastest first, then those without a route, then those not timed; otherwise the Wiki's
	 * order.
	 */
	private static void sort(List<Location> list)
	{
		list.sort(Comparator.comparingInt((Location l) -> l.preferred ? 0 : 1)
			.thenComparingInt(l -> l.isAssigned() ? 0 : 1)
			.thenComparingInt(l -> l.getPlace().isOnMap() ? 0 : 1)
			.thenComparingInt(l -> l.ticks >= 0 ? 0 : l.ticks == NO_ROUTE ? 1 : 2)
			.thenComparingInt(l -> Math.max(0, l.ticks))
			.thenComparingInt(l -> l.index));
	}

	/** The same task with these travel times ({@link Location#key} -> ticks or {@link #NO_ROUTE}), fastest first. */
	public SlayerTask withTimes(Map<String, Integer> times)
	{
		List<Location> list = new ArrayList<>();
		for (Location l : locations)
		{
			Integer t = times.get(l.key());
			list.add(l.withTicks(t == null ? NOT_TIMED : t));
		}
		sort(list);
		return new SlayerTask(state, name, left, master, area, rule, list, icon, wikiPage, stored);
	}

	/** The same task with the player's preferred places marked ({@link SlayerPreferred}, per task), preferred first. */
	public SlayerTask withPreferred(SlayerPreferred preferred)
	{
		List<Location> list = new ArrayList<>();
		boolean changed = false;
		for (Location l : locations)
		{
			Location m = l.withPreferred(preferred != null && preferred.has(name, l.key()));
			changed |= m != l;
			list.add(m);
		}
		if (!changed)
		{
			return this;
		}
		sort(list);
		return new SlayerTask(state, name, left, master, area, rule, list, icon, wikiPage, stored);
	}

	/** The same task with one place ({@link Location#key}) marked preferred or not (the Slayer page's star, at once). */
	public SlayerTask withPreferred(String key, boolean on)
	{
		List<Location> list = new ArrayList<>();
		for (Location l : locations)
		{
			list.add(l.key().equals(key) ? l.withPreferred(on) : l);
		}
		sort(list);
		return new SlayerTask(state, name, left, master, area, rule, list, icon, wikiPage, stored);
	}

	/** The same task with this one in storage (null: none); a stored task is shown, never routed to. */
	public SlayerTask withStored(Stored s)
	{
		if (Objects.equals(s, stored) || state == State.LOGGED_OUT || state == State.NO_DATA)
		{
			return this;
		}
		return new SlayerTask(state, name, left, master, area, rule, locations, icon, wikiPage, s);
	}

	/** The task in storage, or null for none. */
	public Stored getStored()
	{
		return stored;
	}

	/**
	 * A task the player has just been given (for "Automatic Slayer routes", which route to the nearest place at once):
	 * {@code now} is a task and {@code before} was no task or another task, both read while logged in. Not after a login,
	 * the plugin starting or the map data arriving ({@code before} logged out, no data or null), not for the stored task
	 * changing, and never for a kill ({@link #isAKill}) against {@code lastTask}, the last task read (whatever was read
	 * between: a var read mid-change can make a kill look like another task).
	 */
	public static boolean isNewTask(SlayerTask before, SlayerTask lastTask, SlayerTask now)
	{
		return looksNew(before, now) && !isAKill(lastTask, now);
	}

	/** {@code now} is a task and {@code before} no task or another task (kills not told apart: {@link #isNewTask}). */
	public static boolean looksNew(SlayerTask before, SlayerTask now)
	{
		return before != null && now != null && now.state == State.TASK
			&& (before.state == State.NO_TASK || before.state == State.TASK && !before.identity().equals(now.identity()));
	}

	/**
	 * A kill, never a new task (so a kill never opens the sidebar): the same monster as {@code lastTask} with as many or
	 * fewer left, whatever else the vars say (master, Konar's area). A master giving the same monster again gives a full
	 * amount, more than the one left before.
	 */
	public static boolean isAKill(SlayerTask lastTask, SlayerTask now)
	{
		return lastTask != null && now != null && lastTask.state == State.TASK && now.state == State.TASK
			&& lastTask.name.equals(now.name) && now.left <= lastTask.left;
	}

	/** The same task where it matters for travel times: its name, Konar's area and the master (not how many are left). */
	public String identity()
	{
		return state + " " + name + " / " + area + " / " + master;
	}

	/** The Wiki page the places came from ("Slayer task/Kalphites"), else the task's name. */
	private static String wikiPage(String name, List<Location> list)
	{
		for (Location l : list)
		{
			String s = l.place.getSource();
			if (!s.isEmpty())
			{
				int at = s.indexOf('@');
				return at < 0 ? s : s.substring(0, at);
			}
		}
		return name;
	}

	/** The task's picture: the item the RuneLite Slayer plugin shows for it, or -1. */
	public int getIcon()
	{
		return icon;
	}

	/** The Wiki page about the task ("Slayer task/Kalphites"), or null without a task. */
	public String getWikiPage()
	{
		return wikiPage;
	}

	private static String withoutThe(String s)
	{
		return s.regionMatches(true, 0, "the ", 0, 4) ? s.substring(4) : s;
	}

	/** The place's points in the Wilderness (none without the Wilderness data). */
	private static int[] wildernessPoints(SlayerPlaces.Place p, WildernessZones wilderness)
	{
		if (wilderness == null)
		{
			return new int[0];
		}
		return Arrays.stream(p.getPoints()).filter(t -> wilderness.zone(Tiles.x(t), Tiles.y(t)) > 0).toArray();
	}

	public State getState()
	{
		return state;
	}

	/** As the game writes it ("Kalphites"); empty without a task. */
	public String getName()
	{
		return name;
	}

	/** How many are left. */
	public int getLeft()
	{
		return left;
	}

	/** The master who gave it, or null when not known. */
	public String getMaster()
	{
		return master;
	}

	/** "Konar's task: only kills in the Kalphite Lair count.", or null when every place counts. */
	public String getRule()
	{
		return rule;
	}

	/**
	 * Preferred places first, then Konar's area, then the other places that count; in each, the places on the map data
	 * first, then by travel time, otherwise in the Wiki's order.
	 */
	public List<Location> getLocations()
	{
		return locations;
	}

	/** Route to one place: its points, the search picks the cheapest. */
	public RouteRequest request(Location l)
	{
		SlayerPlaces.Place p = l.getPlace();
		return RouteRequest.places("slayer " + name + " " + p.getName(), "Slayer: " + name + " - " + p.getName(), notes(l), l.getPoints());
	}

	/** Note on the route to the nearest preferred place. */
	public static final String PREFERRED_NOTE = "Picked among your preferred places.";
	/** Note on the route to all places when no route reaches a preferred one. */
	public static final String FALLBACK_NOTE = "No route reaches your preferred places from here: the nearest of all places.";

	/**
	 * Route to the nearest place where kills count (all their points; the search picks the cheapest), or null for none.
	 * With preferred places, only among them ({@link #nearest(java.util.function.Predicate)}).
	 */
	public RouteRequest nearest()
	{
		return nearest(l -> true);
	}

	/**
	 * {@link #nearest()} among the places {@code keep} lets through (the Slayer page's filters), or null for none. When
	 * some of those are preferred, only among the preferred ones a route may reach (not those timed with no route from
	 * here); with none such, among them all. When a route to the preferred ones finds no way
	 * there, the plugin falls back to {@link #nearestFallback}.
	 */
	public RouteRequest nearest(Predicate<Location> keep)
	{
		RouteRequest preferred = nearest(keep, true, null);
		return preferred != null ? preferred : nearest(keep, false, null);
	}

	/**
	 * When {@link #nearest(java.util.function.Predicate)} goes only to preferred places: the route to all places, for
	 * when no route reaches a preferred one; else null.
	 */
	public RouteRequest nearestFallback(Predicate<Location> keep)
	{
		return nearest(keep, true, null) == null ? null : nearest(keep, false, FALLBACK_NOTE);
	}

	/** The nearest of the places {@code keep} lets through (only preferred ones, or all), with this note; tests use it. */
	RouteRequest nearest(Predicate<Location> keep, boolean onlyPreferred, String note)
	{
		List<Integer> points = new ArrayList<>();
		for (Location l : locations)
		{
			if (l.canGo() && keep.test(l) && (!onlyPreferred || l.preferred && l.ticks != NO_ROUTE))
			{
				for (int t : l.getPoints())
				{
					points.add(t);
				}
			}
		}
		if (points.isEmpty())
		{
			return null;
		}
		List<String> notes = new ArrayList<>();
		if (rule != null)
		{
			notes.add(rule);
		}
		if (onlyPreferred)
		{
			notes.add(PREFERRED_NOTE);
		}
		if (note != null)
		{
			notes.add(note);
		}
		return RouteRequest.places("slayer " + name + (onlyPreferred ? " nearest preferred" : " nearest"), "Slayer: " + name
			+ (onlyPreferred ? " (nearest preferred place)" : " (nearest place)"), notes, points.stream().mapToInt(Integer::intValue).toArray());
	}

	private List<String> notes(Location l)
	{
		List<String> out = new ArrayList<>();
		String n = SlayerPlaces.plainNotes(l.getPlace().getNotes());
		if (!n.isEmpty())
		{
			out.add(n);
		}
		return out;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof SlayerTask))
		{
			return false;
		}
		SlayerTask t = (SlayerTask) o;
		if (!(state == t.state && left == t.left && name.equals(t.name) && Objects.equals(master, t.master) && Objects.equals(area, t.area)
			&& icon == t.icon && Objects.equals(stored, t.stored) && locations.size() == t.locations.size()))
		{
			return false;
		}
		for (int i = 0; i < locations.size(); i++)
		{
			if (locations.get(i).place != t.locations.get(i).place || locations.get(i).ticks != t.locations.get(i).ticks
				|| locations.get(i).preferred != t.locations.get(i).preferred)
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(state, name, left, master, area);
	}
}

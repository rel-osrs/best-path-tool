package com.bestpath.route;

import com.bestpath.core.data.ChartingTasks;
import com.bestpath.core.sea.SeaArea;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * The player's sea charting: which tasks are done (the game's charting varbits) and the Sailing level, with the routes
 * and rewards the Functions tab shows (every sea, one sea, each bonus set, every sea or ocean the player can complete).
 * Immutable; made on the client thread ({@link #read}), shown on the Swing thread.
 *
 * <p>A set's route first goes to whichever task left in it the player can do now ({@link #canDo}) is quickest to
 * reach (the route search compares them all: walking, teleports, the player's boats and the waters those boats may
 * enter); once the set's order is planned ({@link com.bestpath.core.data.ChartingOrder}) it goes task by task in that
 * order, each leg of a task in turn ({@link #legTile}). When a task is done the plugin routes on to the next
 * ({@link #setOf}).
 */
public final class ChartingProgress
{
	/** Every sea area allowed (the player's boats not known). */
	public static final int ANY_BOAT = -1;
	/** No charting data, or not logged in. */
	public static final ChartingProgress NONE = new ChartingProgress(ChartingTasks.NONE, new BitSet(), 0, false, null, null,
		ANY_BOAT, false);
	/** The key prefix of a set's route; the set's name follows ("" for every sea). */
	public static final String SET_KEY = "charting set ";
	/** The name shown for the set of every task. */
	public static final String EVERY_SEA = "Every sea";
	/**
	 * Current duck tasks need the quest Current Affairs finished: its varbit at this or more (Quest Helper
	 * ChartingCurrentStep.java a526461).
	 */
	public static final int CURRENT_AFFAIRS_DONE = 45;
	/** The weather station the meteorologist gives, and the same filled with the readings. */
	public static final int STATION_EMPTY = ItemID.SAILING_CHARTING_WEATHER_STATION_EMPTY;
	public static final int STATION_FULL = ItemID.SAILING_CHARTING_WEATHER_STATION_FULL;
	/** The current duck: out of the inventory between letting it go and picking it up. */
	public static final int DUCK = ItemID.SAILING_CHARTING_CURRENT_DUCK;
	/** Every sea and bonus set the player can complete now: each one with a task left, every task left doable now. */
	public static final String FULL_SEAS = "Every sea you can complete";
	/** Every ocean the player can complete now, as {@link #FULL_SEAS}. */
	public static final String FULL_OCEANS = "Every ocean you can complete";

	private final ChartingTasks tasks;
	/** By task number. */
	private final BitSet done;
	private final int sailingLevel;
	private final boolean loggedIn;
	/**
	 * The tools' and drinks' items the player carries or wears, and has in the bank; null: not known (every tool counts
	 * as owned, no drink as held).
	 */
	private final Set<Integer> carried;
	private final Set<Integer> banked;
	/**
	 * The sea areas at least one of the player's boats may enter (a bit per {@link SeaArea} ordinal, as
	 * {@link com.bestpath.core.sea.Boat#areaMask}); {@link #ANY_BOAT}: not known (every hazard counts as passable).
	 */
	private final int boatAreas;
	/** Whether the quest Current Affairs is finished ({@link #CURRENT_AFFAIRS_DONE}). */
	final boolean currentAffairs;

	private ChartingProgress(ChartingTasks tasks, BitSet done, int sailingLevel, boolean loggedIn, Set<Integer> carried,
		Set<Integer> banked, int boatAreas, boolean currentAffairs)
	{
		this.boatAreas = boatAreas;
		this.currentAffairs = currentAffairs;
		this.tasks = tasks;
		this.done = done;
		this.sailingLevel = sailingLevel;
		this.loggedIn = loggedIn;
		this.carried = carried;
		this.banked = banked;
	}

	/**
	 * Reads every task's varbit and where the player's charting tools are (client thread).
	 *
	 * @param varbits the game's varbit values (client::getVarbitValue)
	 * @param carries whether the player carries or wears an item; null: not known (every tool counts as owned)
	 * @param banks   whether the player's bank has an item (as last seen)
	 * @param boatAreas the sea areas the player's boats may enter together ({@link #ANY_BOAT}: not known)
	 */
	public static ChartingProgress read(ChartingTasks tasks, IntUnaryOperator varbits, int sailingLevel,
		IntPredicate carries, IntPredicate banks, int boatAreas)
	{
		boolean known = carries != null;
		BitSet done = new BitSet();
		Set<Integer> carried = known ? new HashSet<>() : null;
		Set<Integer> banked = known ? new HashSet<>() : null;
		for (ChartingTasks.Task t : tasks.getTasks())
		{
			if (varbits.applyAsInt(t.getVarbit()) > 0)
			{
				done.set(t.getNumber());
			}
			if (known)
			{
				IntStream more = t.getDrink() >= 0 ? IntStream.of(t.getDrink())
					: t.isWeather() ? IntStream.of(STATION_EMPTY, STATION_FULL) : IntStream.empty();
				int[] ids = IntStream.concat(Arrays.stream(t.getToolItems()), more).toArray();
				for (int id : ids)
				{
					if (carries.test(id))
					{
						carried.add(id);
					}
					if (banks != null && banks.test(id))
					{
						banked.add(id);
					}
				}
			}
		}
		return new ChartingProgress(tasks, done, sailingLevel, true, carried, banked, boatAreas,
			varbits.applyAsInt(VarbitID.CURRENT_AFFAIRS) >= CURRENT_AFFAIRS_DONE);
	}

	/** The tasks known, with nothing read (logged out). */
	public static ChartingProgress loggedOut(ChartingTasks tasks)
	{
		return new ChartingProgress(tasks, new BitSet(), 0, false, null, null, ANY_BOAT, false);
	}

	public ChartingTasks getTasks()
	{
		return tasks;
	}

	public boolean isLoggedIn()
	{
		return loggedIn;
	}

	public int getSailingLevel()
	{
		return sailingLevel;
	}

	public boolean isDone(ChartingTasks.Task t)
	{
		return done.get(t.getNumber());
	}

	public int doneCount()
	{
		return done.cardinality();
	}

	public int total()
	{
		return tasks.getTasks().size();
	}

	public boolean levelAllows(ChartingTasks.Task t)
	{
		return sailingLevel >= t.getLevel();
	}

	/**
	 * What the player can do now: not done, their Sailing level allows it, one of their boats may enter its hazard
	 * ({@link #boatCanEnter}), its quest is done ({@link #questAllows}), its place is known, and it is not a drink
	 * already held ({@link #holdsDrink}). Tools are assumed owned.
	 */
	public boolean canDo(ChartingTasks.Task t)
	{
		return !isDone(t) && levelAllows(t) && boatCanEnter(t) && questAllows(t) && t.hasPlace() && !holdsDrink(t);
	}

	/** False only for a current duck task while the quest Current Affairs is not finished. */
	public boolean questAllows(ChartingTasks.Task t)
	{
		return !t.isCurrentDuck() || currentAffairs;
	}


	/** Whether the player carries or wears the empty weather station (not known: no). */
	public boolean carriesEmptyStation()
	{
		return carried != null && carried.contains(STATION_EMPTY);
	}

	/** Whether the player carries or wears the weather station filled with the readings (not known: no). */
	public boolean carriesFullStation()
	{
		return carried != null && carried.contains(STATION_FULL);
	}

	/**
	 * Where the next leg of a task goes (packed; -1 if its place isn't known). A weather task is done
	 * all together, straight back to the meteorologist: the meteorologist, then with the empty station the measuring
	 * spot, then with the full one back to the meteorologist. A current duck is let go at its start and picked up at its
	 * second place ({@code duckOut}: the duck has been let go).
	 */
	public int legTile(ChartingTasks.Task t, boolean duckOut)
	{
		if (t.isWeather() && t.getSecondPlace() >= 0 && carriesEmptyStation() && !carriesFullStation())
		{
			return t.getSecondPlace();
		}
		if (t.isCurrentDuck() && duckOut && t.getSecondPlace() >= 0)
		{
			return t.getSecondPlace();
		}
		return t.getPlace();
	}

	/** What to do where {@link #legTile} goes, for the route's notes; null for a task with one place. */
	public String legNote(ChartingTasks.Task t, boolean duckOut)
	{
		if (t.isWeather() && t.getSecondPlace() >= 0)
		{
			if (carriesFullStation())
			{
				return "Take the full weather station straight back to the meteorologist.";
			}
			return carriesEmptyStation() ? "Measure the winds here, then go straight back to the meteorologist."
				: "Get a weather station from the meteorologist, then measure the winds and come straight back.";
		}
		if (t.isCurrentDuck() && t.getSecondPlace() >= 0)
		{
			return duckOut ? "Pick the duck up here. The next task goes on from here."
				: "Let the duck go here, then pick it up where the current takes it.";
		}
		return null;
	}

	/** The tasks of a set the player can do now ({@link #canDo}), in the data's order. */
	public List<ChartingTasks.Task> doable(String set)
	{
		List<ChartingTasks.Task> l = new ArrayList<>();
		for (ChartingTasks.Task t : tasksIn(set == null ? EVERY_SEA : set))
		{
			if (canDo(t))
			{
				l.add(t);
			}
		}
		return l;
	}

	/**
	 * The restricted sea area of a charting hazard, or null for none (or a hazard no task has). Each of the five
	 * charting hazards is one of the restricted sea areas.
	 */
	public static SeaArea hazardArea(String hazard)
	{
		switch (hazard)
		{
			case "Stormy seas":
				return SeaArea.TEMPOR_TANTRUM;
			case "Fetid waters":
				return SeaArea.FETID;
			case "Crystal-flecked waters":
				return SeaArea.CRYSTALLINE;
			case "Tangled kelp":
				return SeaArea.KELP;
			case "Icy seas":
				return SeaArea.NORTHERN;
			default:
				return null;
		}
	}

	/** What a boat needs for a charting hazard ("an adamant helm or better"), or null for none. */
	public static String hazardNeeds(String hazard)
	{
		SeaArea a = hazardArea(hazard);
		if (a == null)
		{
			return null;
		}
		switch (a)
		{
			case TEMPOR_TANTRUM:
				return "an oak mast or better";
			case FETID:
				return "an inoculation station";
			case CRYSTALLINE:
				return "an adamant keel or better";
			case KELP:
				return "an adamant helm or better";
			default:
				return "an Eternal brazier";
		}
	}

	/** True when the task has no hazard, the boats are not known, or one of the player's boats may enter its hazard. */
	public boolean boatCanEnter(ChartingTasks.Task t)
	{
		SeaArea a = hazardArea(t.getHazard());
		return a == null || (boatAreas & 1 << a.ordinal()) != 0;
	}

	/**
	 * A drink crate task whose drink the player has got but not drunk yet: not done, and the drink carried or in the bank
	 * (as last seen). Such a task is not routed to; the player is told to drink it.
	 */
	public boolean holdsDrink(ChartingTasks.Task t)
	{
		return t.getDrink() >= 0 && !isDone(t) && carried != null && (carried.contains(t.getDrink())
			|| banked != null && banked.contains(t.getDrink()));
	}

	/** True when the held drink is only in the bank (see {@link #holdsDrink}). */
	public boolean drinkInBank(ChartingTasks.Task t)
	{
		return holdsDrink(t) && !carried.contains(t.getDrink());
	}

	/** Every task whose drink the player holds and hasn't drunk yet, in task order. */
	public List<ChartingTasks.Task> drinksToDrink()
	{
		List<ChartingTasks.Task> out = new ArrayList<>();
		for (ChartingTasks.Task t : tasks.getTasks())
		{
			if (holdsDrink(t))
			{
				out.add(t);
			}
		}
		return out;
	}

	/**
	 * A set's tasks ({@code set} null or {@link #EVERY_SEA}: every task; {@link #FULL_SEAS} / {@link #FULL_OCEANS}: the
	 * tasks of every sea and bonus set / ocean the player can complete now).
	 */
	public List<ChartingTasks.Task> tasksIn(String set)
	{
		if (FULL_SEAS.equals(set) || FULL_OCEANS.equals(set))
		{
			List<ChartingTasks.Task> l = new ArrayList<>();
			for (String s : completable(FULL_OCEANS.equals(set)))
			{
				l.addAll(tasks.tasksIn(s));
			}
			return l;
		}
		return tasks.tasksIn(set == null || set.equals(EVERY_SEA) ? null : set);
	}

	/**
	 * True when the player can complete this set now: logged in, at least one task left, and every task left doable now
	 * ({@link #canDo}) or a drink already held.
	 */
	public boolean canComplete(String set)
	{
		if (!loggedIn)
		{
			return false;
		}
		boolean left = false;
		for (ChartingTasks.Task t : tasksIn(set))
		{
			if (!isDone(t))
			{
				if (!canDo(t) && !holdsDrink(t)) // a drink held only needs drinking
				{
					return false;
				}
				left = true;
			}
		}
		return left;
	}

	/** The seas and bonus sets ({@code oceans} false) or the oceans the player can complete now, in the data's order. */
	public List<String> completable(boolean oceans)
	{
		List<String> out = new ArrayList<>();
		for (ChartingTasks.Set s : tasks.getSets())
		{
			if (ChartingTasks.OCEAN.equals(s.getType()) == oceans && canComplete(s.getName()))
			{
				out.add(s.getName());
			}
		}
		return out;
	}

	/**
	 * What one of the big buttons does, for its tooltip: "3 seas: 12 tasks, 1,240 XP, and 570 XP for completing them."
	 * ({@link #FULL_SEAS}, {@link #FULL_OCEANS} or {@link #EVERY_SEA}).
	 */
	public String planText(String plan)
	{
		List<String> sets = EVERY_SEA.equals(plan) ? null : completable(FULL_OCEANS.equals(plan));
		int bonus = 0;
		if (sets == null)
		{
			for (ChartingTasks.Set s : tasks.getSets())
			{
				int[] c = counts(s.getName());
				bonus += c[1] > 0 && c[0] < c[1] ? s.getXp() : 0;
			}
		}
		else
		{
			for (String s : sets)
			{
				bonus += tasks.set(s).getXp();
			}
		}
		int[] c = counts(plan);
		String what = sets == null ? "Everything"
			: sets.size() + (FULL_OCEANS.equals(plan) ? sets.size() == 1 ? " ocean" : " oceans" : sets.size() == 1 ? " sea" : " seas");
		return String.format(Locale.ROOT, "%s: %d %s left, %,d XP, and %,d XP for completing them.", what, c[1] - c[0],
			c[1] - c[0] == 1 ? "task" : "tasks", xpLeft(plan), bonus);
	}

	/** A set's counts: done, all, can do now. */
	public int[] counts(String set)
	{
		int[] c = new int[3];
		for (ChartingTasks.Task t : tasksIn(set))
		{
			c[0] += isDone(t) ? 1 : 0;
			c[1]++;
			c[2] += canDo(t) ? 1 : 0;
		}
		return c;
	}

	/** The experience of a set's tasks not done yet. */
	public int xpLeft(String set)
	{
		int xp = 0;
		for (ChartingTasks.Task t : tasksIn(set))
		{
			xp += isDone(t) ? 0 : t.getXp();
		}
		return xp;
	}

	/**
	 * A set's rewards for the sidebar: "4 tasks left: 290 XP. Completing the sea: 170 XP." Every sea: the tasks' XP and
	 * the completion experience of every sea, group and ocean not complete yet.
	 */
	public String rewards(String set)
	{
		int[] c = counts(set);
		int left = c[1] - c[0];
		// the XP shown counts only what can be completed: logged in, only the tasks the player can do now (or whose drink
		// they hold) and the completion XP of what they can complete
		int doable = 0;
		int xp = 0;
		for (ChartingTasks.Task t : tasksIn(set))
		{
			if (!isDone(t) && (!loggedIn || canDo(t) || holdsDrink(t)))
			{
				doable++;
				xp += t.getXp();
			}
		}
		String s = left == 0 ? "Every task done."
			: doable == left ? String.format(Locale.ROOT, "%d %s left: %,d XP.", left, left == 1 ? "task" : "tasks", xp)
			: doable == 0 ? String.format(Locale.ROOT, "None of the %d %s left can be done now.", left, left == 1 ? "task" : "tasks")
			: String.format(Locale.ROOT, "%d of the %d tasks left can be done now: %,d XP.", doable, left, xp);
		ChartingTasks.Set info = set == null ? null : tasks.set(set);
		if (info != null)
		{
			String what = ChartingTasks.OCEAN.equals(info.getType()) ? "the ocean"
				: ChartingTasks.BONUS.equals(info.getType()) ? "the bonus set" : "the sea";
			if (left == 0)
			{
				s += String.format(Locale.ROOT, " Completing %s gave %,d XP.", what, info.getXp());
			}
			else if (!loggedIn || canComplete(set))
			{
				s += String.format(Locale.ROOT, " Completing %s: %,d XP more.", what, info.getXp());
			}
			else
			{
				s += " " + Character.toUpperCase(what.charAt(0)) + what.substring(1) + " can't be completed yet.";
			}
		}
		else if (set == null || set.equals(EVERY_SEA))
		{
			int bonus = 0;
			for (ChartingTasks.Set st : tasks.getSets())
			{
				int[] sc = counts(st.getName());
				bonus += sc[1] > 0 && sc[0] < sc[1] && (!loggedIn || canComplete(st.getName())) ? st.getXp() : 0;
			}
			if (bonus > 0)
			{
				s += String.format(Locale.ROOT, loggedIn ? " Completing the seas, bonus sets and oceans you can: %,d XP more."
					: " Completing each sea, bonus set and ocean: %,d XP more.", bonus);
			}
		}
		return s;
	}

	/**
	 * The route for a set ({@link #EVERY_SEA} or a set's name): every task in it the player can do now; the route search
	 * picks the quickest to reach. Null when there is none.
	 */
	public RouteRequest route(String set)
	{
		String name = set == null ? EVERY_SEA : set;
		List<ChartingTasks.Task> l = new ArrayList<>();
		for (ChartingTasks.Task t : tasksIn(name))
		{
			if (canDo(t))
			{
				l.add(t);
			}
		}
		if (l.isEmpty())
		{
			return null;
		}
		int[] targets = new int[l.size()];
		int xp = 0;
		for (int i = 0; i < targets.length; i++)
		{
			targets[i] = l.get(i).getPlace();
			xp += l.get(i).getXp();
		}
		return RouteRequest.places(SET_KEY + (name.equals(EVERY_SEA) ? "" : name), "Sea charting: " + name,
			List.of(String.format(Locale.ROOT, "The quickest of %d %s you can do now (%,d XP). Once it's done the route goes"
				+ " on to the next.", l.size(), l.size() == 1 ? "task" : "tasks", xp)), targets);
	}

	/** The first task of a planned order (task numbers) the player can do now in the set; null for none. */
	public ChartingTasks.Task nextInPlan(String set, List<Integer> plan)
	{
		List<ChartingTasks.Task> doable = doable(set);
		for (int n : plan)
		{
			for (ChartingTasks.Task t : doable)
			{
				if (t.getNumber() == n)
				{
					return t;
				}
			}
		}
		return null;
	}

	/** Whether a planned order (task numbers) has every task of the set the player can do now. */
	public boolean planCovers(String set, List<Integer> plan)
	{
		Set<Integer> in = new HashSet<>(plan);
		for (ChartingTasks.Task t : doable(set))
		{
			if (!in.contains(t.getNumber()))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * A set's route to the next leg of the next task of its planned order ({@code left}: the set's tasks left to do,
	 * this one too). It is keyed as the set's route ({@link #setOf}), so the plugin goes on to the next task once this
	 * one is done.
	 */
	public RouteRequest routeLeg(String set, ChartingTasks.Task t, boolean duckOut, int left)
	{
		int tile = legTile(t, duckOut);
		if (tile < 0)
		{
			return null;
		}
		String name = set == null ? EVERY_SEA : set;
		List<String> notes = new ArrayList<>();
		notes.add(t.getText() + String.format(Locale.ROOT, " (%,d XP)", t.getXp()));
		String leg = legNote(t, duckOut);
		if (leg != null)
		{
			notes.add(leg);
		}
		if (!t.getTip().isEmpty())
		{
			notes.add(t.getTip());
		}
		notes.add(left <= 1 ? "The last task you can do now in this set."
			: String.format(Locale.ROOT, "Next of the %d tasks left, in the planned order. Once it's done the route goes"
				+ " on to the next.", left));
		return RouteRequest.places(SET_KEY + (name.equals(EVERY_SEA) ? "" : name), "Sea charting: " + name, notes,
			new int[]{tile});
	}

	/** The set a route charts ({@link #EVERY_SEA} or a set's name), or null when it isn't a set's route. */
	public static String setOf(RouteRequest r)
	{
		if (r == null || r.getKey() == null || !r.getKey().startsWith(SET_KEY))
		{
			return null;
		}
		String name = r.getKey().substring(SET_KEY.length());
		return name.isEmpty() ? EVERY_SEA : name;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof ChartingProgress))
		{
			return false;
		}
		ChartingProgress p = (ChartingProgress) o;
		return tasks == p.tasks && done.equals(p.done) && sailingLevel == p.sailingLevel && loggedIn == p.loggedIn
			&& Objects.equals(carried, p.carried) && Objects.equals(banked, p.banked) && boatAreas == p.boatAreas
			&& currentAffairs == p.currentAffairs;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(done, sailingLevel, loggedIn, carried, banked, boatAreas, currentAffairs);
	}
}

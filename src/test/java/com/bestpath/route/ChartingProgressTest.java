package com.bestpath.route;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.data.ChartingTasks;
import com.bestpath.core.path.Tiles;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/** Which charting tasks are done, the sets, their rewards and routes. */
public class ChartingProgressTest
{
	public static final int BOARD = Tiles.pack(3030, 3198, 0);
	public static final int DUCK = Tiles.pack(3082, 3201, 0);
	public static final int ROCK = Tiles.pack(3079, 3231, 0);
	public static final int WRECK = Tiles.pack(3126, 3202, 0);
	public static final int CURRENT_DUCK = 31805;
	/** The quest Current Affairs's varbit: the made-up readings have it finished (45) unless a test says otherwise. */
	public static final int CA = VarbitID.CURRENT_AFFAIRS;

	/**
	 * The Ardent Ocean (5,000 XP) with the Bay of Sarim (190 XP: a board level 1, a rock level 24 with Stormy seas, a
	 * wreck) and the Kharidian Sea (a spyglass with no place); the Bonus Currents set (510 XP) with a duck (level 22) in
	 * the Bay of Sarim.
	 */
	public static ChartingTasks tasks()
	{
		return new ChartingTasks(List.of(
			new ChartingTasks.Set("Ardent Ocean", ChartingTasks.OCEAN, "", 5000),
			new ChartingTasks.Set("Kharidian Sea", ChartingTasks.SEA, "Ardent Ocean", 190),
			new ChartingTasks.Set("Bay of Sarim", ChartingTasks.SEA, "Ardent Ocean", 190),
			new ChartingTasks.Set("Miscellaneous", ChartingTasks.OCEAN, "", 15000),
			new ChartingTasks.Set("Bonus Currents", ChartingTasks.BONUS, "Miscellaneous", 510)),
			List.of(
				new ChartingTasks.Task(0, 18574, "generic", "Bay of Sarim", "", 1, 35, "", "", false, new int[]{BOARD},
					"Look at the port task board in Port Sarim.", "", "", new int[0], -1, ""),
				new ChartingTasks.Task(3, 18577, "current", "Bonus Currents", "Bay of Sarim", 22, 125, "", "", true,
					new int[]{DUCK, Tiles.pack(3033, 3152, 0)}, "Test the currents south of the small island.", "Follow the duck.",
					"Current duck", new int[]{CURRENT_DUCK}, -1, ""),
				new ChartingTasks.Task(4, 18578, "generic", "Bay of Sarim", "", 24, 35, "Stormy seas", "", false,
					new int[]{ROCK}, "Find a treacherous rock formation near Draynor Village.", "", "", new int[0], -1, ""),
				new ChartingTasks.Task(5, 18579, "generic", "Bay of Sarim", "", 1, 35, "", "", false, new int[]{WRECK},
					"Find the wreck of the Grandyozenaym.", "", "", new int[0], -1, ""),
				new ChartingTasks.Task(13, 18587, "spyglass", "Kharidian Sea", "", 1, 50, "", "", false, new int[0],
					"Use your spyglass.", "", "Spyglass", new int[]{31803}, -1, "")));
	}

	private static ChartingProgress read(int sailing, Set<Integer> doneVarbits)
	{
		return ChartingProgress.read(tasks(), v -> v == CA ? 45 : doneVarbits.contains(v) ? 1 : 0, sailing, null, null,
			ChartingProgress.ANY_BOAT);
	}

	@Test
	public void countsComeFromTheVarbits()
	{
		ChartingProgress p = read(10, Set.of(18574));
		assertTrue(p.isLoggedIn());
		assertEquals(1, p.doneCount());
		assertEquals(5, p.total());
		assertArrayEquals("done, all, can do", new int[]{1, 3, 1}, p.counts("Bay of Sarim"));
		assertArrayEquals("a bonus task counts in its bonus set", new int[]{0, 1, 0}, p.counts("Bonus Currents"));
		assertArrayEquals("the ocean: its seas", new int[]{1, 4, 1}, p.counts("Ardent Ocean"));
		assertArrayEquals(new int[]{1, 5, 1}, p.counts(ChartingProgress.EVERY_SEA));
		assertTrue(p.isDone(p.getTasks().getTasks().get(0)));
	}

	@Test
	public void canDoNeedsTheLevelAndAPlace()
	{
		ChartingProgress p = read(10, Set.of(18574));
		List<ChartingTasks.Task> t = p.getTasks().getTasks();
		assertFalse("done", p.canDo(t.get(0)));
		assertFalse("Sailing 22", p.canDo(t.get(1)));
		assertFalse("Sailing 24", p.canDo(t.get(2)));
		assertTrue(p.canDo(t.get(3)));
		assertFalse("no place", p.canDo(t.get(4)));
		assertTrue(read(24, Set.of()).canDo(t.get(2)));
	}

	@Test
	public void aSetsRouteHasEveryTaskLeftThePlayerCanDo()
	{
		ChartingProgress p = read(30, Set.of(18574));
		RouteRequest bay = p.route("Bay of Sarim");
		assertArrayEquals(new int[]{ROCK, WRECK}, bay.getTargets());
		assertEquals("Bay of Sarim", ChartingProgress.setOf(bay));
		assertEquals("Sea charting: Bay of Sarim", bay.getTitle());
		assertArrayEquals(new int[]{DUCK, ROCK, WRECK}, p.route(ChartingProgress.EVERY_SEA).getTargets());
		assertEquals(ChartingProgress.EVERY_SEA, ChartingProgress.setOf(p.route(ChartingProgress.EVERY_SEA)));
		assertArrayEquals(new int[]{DUCK}, p.route("Bonus Currents").getTargets());
		assertNull("its only task has no place", p.route("Kharidian Sea"));
		assertNull("done", read(30, Set.of(18574, 18578, 18579)).route("Bay of Sarim"));
		assertNull(ChartingProgress.setOf(new RouteRequest(3200, 3200, 0)));
	}

	@Test
	public void rewardsSayWhatIsLeftAndTheCompletionXp()
	{
		ChartingProgress p = read(30, Set.of(18574));
		assertEquals("2 tasks left: 70 XP. Completing the sea: 190 XP more.", p.rewards("Bay of Sarim"));
		assertEquals("1 task left: 125 XP. Completing the bonus set: 510 XP more.", p.rewards("Bonus Currents"));
		// the XP number shown only counts up what can actually be completed
		assertEquals("the spyglass task has no place", "2 of the 3 tasks left can be done now: 70 XP. The ocean can't be"
			+ " completed yet.", p.rewards("Ardent Ocean"));
		assertEquals("3 of the 4 tasks left can be done now: 195 XP. Completing the seas, bonus sets and oceans you can: 15,700"
			+ " XP more.", p.rewards(ChartingProgress.EVERY_SEA));
		assertEquals("None of the 1 task left can be done now. The sea can't be completed yet.", p.rewards("Kharidian Sea"));
		assertEquals("logged out: everything counts", "5 tasks left: 280 XP. Completing each sea, bonus set and ocean: 20,890 XP"
			+ " more.", ChartingProgress.loggedOut(tasks()).rewards(ChartingProgress.EVERY_SEA));
		assertEquals("Every task done. Completing the sea gave 190 XP.",
			read(30, Set.of(18574, 18578, 18579)).rewards("Bay of Sarim"));
	}

	/** The player is assumed to have all the equipment they need if they have the relevant quests and levels. */
	@Test
	public void theToolsAreTakenAsOwned()
	{
		ChartingTasks.Task duck = tasks().getTasks().get(1);
		ChartingProgress none = ChartingProgress.read(tasks(), v -> v == CA ? 45 : 0, 30, id -> false, id -> false,
			ChartingProgress.ANY_BOAT);
		assertTrue("no current duck anywhere, still doable", none.canDo(duck));
		assertTrue("no bank stop for it", none.route("Bonus Currents").getNeeds().isEmpty());
		assertFalse("the level still counts",
			ChartingProgress.read(tasks(), v -> v == CA ? 45 : 0, 20, id -> false, id -> false, ChartingProgress.ANY_BOAT).canDo(duck));
	}

	/**
	 * A task in a charting hazard can be done only when one of the player's boats
	 * may enter that restricted water (Stormy seas: Tempor Tantrum, an oak mast or better).
	 */
	@Test
	public void aHazardNeedsABoatThatMayEnterIt()
	{
		ChartingTasks.Task rock = tasks().getTasks().get(2);
		assertEquals("Stormy seas", rock.getHazard());
		int open = 1 << com.bestpath.core.sea.SeaArea.OPEN.ordinal();
		int stormy = open | 1 << com.bestpath.core.sea.SeaArea.TEMPOR_TANTRUM.ordinal();
		ChartingProgress noMast = ChartingProgress.read(tasks(), v -> v == CA ? 45 : 0, 30, id -> false, id -> false, open);
		assertFalse(noMast.boatCanEnter(rock));
		assertFalse(noMast.canDo(rock));
		assertTrue("no hazard: still doable", noMast.canDo(tasks().getTasks().get(0)));
		assertFalse("the rock is left out of the set's route", java.util.Arrays.stream(noMast.route("Bay of Sarim").getTargets())
			.anyMatch(x -> x == ROCK));
		ChartingProgress oak = ChartingProgress.read(tasks(), v -> v == CA ? 45 : 0, 30, id -> false, id -> false, stormy);
		assertTrue(oak.canDo(rock));
		assertFalse("the boats' waters count", oak.equals(noMast));
		assertTrue("boats not known: passable", ChartingProgress.read(tasks(), v -> 0, 30, id -> false, id -> false, ChartingProgress.ANY_BOAT).canDo(rock));
		assertFalse("no boat at all", ChartingProgress.read(tasks(), v -> 0, 30, id -> false, id -> false, 0).canDo(rock));
		assertEquals("an oak mast or better", ChartingProgress.hazardNeeds("Stormy seas"));
		assertEquals(com.bestpath.core.sea.SeaArea.NORTHERN, ChartingProgress.hazardArea("Icy seas"));
		assertNull(ChartingProgress.hazardArea(""));
	}

	@Test
	public void aSetsRouteIsNotARecentDestination()
	{
		assertFalse(RecentDestinations.keeps(read(30, Set.of()).route("Bay of Sarim")));
	}

	@Test
	public void sameReadingsAreEqual()
	{
		ChartingTasks t = tasks();
		ChartingProgress a = ChartingProgress.read(t, v -> v == CA ? 45 : v == 18574 ? 1 : 0, 10, null, null, ChartingProgress.ANY_BOAT);
		ChartingProgress b = ChartingProgress.read(t, v -> v == CA ? 45 : v == 18574 ? 1 : 0, 10, null, null, ChartingProgress.ANY_BOAT);
		assertEquals(a, b);
		assertFalse(a.equals(ChartingProgress.read(t, v -> v == CA ? 45 : 0, 10, null, null, ChartingProgress.ANY_BOAT)));
		assertFalse(a.equals(ChartingProgress.loggedOut(t)));
		assertFalse(ChartingProgress.loggedOut(t).isLoggedIn());
	}

	/** A route to complete every full sea the player can, another for oceans: the entire sea if they have the requirements. */
	@Test
	public void whatCanBeCompletedNow()
	{
		ChartingProgress low = read(10, Set.of(18574));
		assertTrue(low.completable(false).isEmpty());
		assertTrue(low.completable(true).isEmpty());
		assertNull(low.route(ChartingProgress.FULL_SEAS));
		ChartingProgress p = read(30, Set.of(18574));
		assertEquals(List.of("Bay of Sarim", "Bonus Currents"), p.completable(false));
		assertEquals("the Kharidian Sea's task has no place", List.of("Miscellaneous"), p.completable(true));
		assertFalse(p.canComplete(ChartingProgress.EVERY_SEA));
		RouteRequest seas = p.route(ChartingProgress.FULL_SEAS);
		assertEquals(ChartingProgress.FULL_SEAS, ChartingProgress.setOf(seas));
		assertEquals(3, seas.targetCount());
		assertArrayEquals(new int[]{DUCK}, p.route(ChartingProgress.FULL_OCEANS).getTargets());
		assertEquals("2 seas: 3 tasks left, 195 XP, and 700 XP for completing them.", p.planText(ChartingProgress.FULL_SEAS));
		assertEquals("1 ocean: 1 task left, 125 XP, and 15,000 XP for completing them.", p.planText(ChartingProgress.FULL_OCEANS));
		ChartingProgress all = read(30, Set.of(18574, 18587));
		assertTrue(all.canComplete(ChartingProgress.EVERY_SEA));
		assertFalse("a finished sea can't be completed again", read(30, Set.of(18574, 18578, 18579)).canComplete("Bay of Sarim"));
		assertFalse(ChartingProgress.loggedOut(tasks()).canComplete("Bay of Sarim"));
	}

	/** Two drink crates in the Bay of Sarim: the smuggled rum's and the marrow wine's. */
	private static ChartingTasks crates()
	{
		return new ChartingTasks(List.of(new ChartingTasks.Set("Ardent Ocean", ChartingTasks.OCEAN, "", 5000),
			new ChartingTasks.Set("Bay of Sarim", ChartingTasks.SEA, "Ardent Ocean", 190)), List.of(
			new ChartingTasks.Task(38, 18612, "crate", "Bay of Sarim", "", 12, 75, "", "", false, new int[]{WRECK},
				"Find a sealed crate.", "", "Crowbar", new int[]{31807}, 31831, "Smuggled rum"),
			new ChartingTasks.Task(40, 18614, "crate", "Bay of Sarim", "", 12, 75, "", "", false, new int[]{ROCK},
				"Find a sealed crate.", "", "Crowbar", new int[]{31807}, 31834, "Marrow wine")));
	}

	/**
	 * The player just needs to get the drinks. If the player has the drink in their bank or inventory it is not routed
	 * to anymore and they are told to drink the drink; it never gets stuck on telling the player to drink a drink.
	 */
	@Test
	public void aDrinkGotIsNotRoutedToAgain()
	{
		ChartingProgress none = ChartingProgress.read(crates(), v -> 0, 20, id -> false, id -> false, ChartingProgress.ANY_BOAT);
		assertEquals(2, none.route("Bay of Sarim").targetCount());
		assertTrue(none.drinksToDrink().isEmpty());
		ChartingProgress rum = ChartingProgress.read(crates(), v -> 0, 20, id -> id == 31831, id -> false, ChartingProgress.ANY_BOAT);
		ChartingTasks.Task rumTask = crates().getTasks().get(0);
		assertTrue(rum.holdsDrink(rum.getTasks().getTasks().get(0)));
		assertFalse(rum.canDo(rum.getTasks().getTasks().get(0)));
		assertArrayEquals("only the other crate", new int[]{ROCK}, rum.route("Bay of Sarim").getTargets());
		assertTrue("a drink held only needs drinking", rum.canComplete("Bay of Sarim"));
		ChartingProgress both = ChartingProgress.read(crates(), v -> 0, 20, id -> id == 31831, id -> id == 31834, ChartingProgress.ANY_BOAT);
		assertNull("nothing left to go to: the route ends, it doesn't wait for the drinking", both.route("Bay of Sarim"));
		assertEquals(2, both.drinksToDrink().size());
		assertFalse(both.drinkInBank(both.getTasks().getTasks().get(0)));
		assertTrue(both.drinkInBank(both.getTasks().getTasks().get(1)));
		ChartingProgress drunk = ChartingProgress.read(crates(), v -> v == 18612 ? 1 : 0, 20, id -> id == 31831, id -> false, ChartingProgress.ANY_BOAT);
		assertFalse("drunk: done, the bottle no longer counts", drunk.holdsDrink(drunk.getTasks().getTasks().get(0)));
		assertFalse("not known: no drink held", read(20, Set.of()).holdsDrink(rumTask));
	}

	/** A duck task needs Current Affairs to be done. */
	@Test
	public void aDuckTaskNeedsCurrentAffairsFinished()
	{
		ChartingTasks.Task duck = tasks().getTasks().get(1);
		ChartingProgress before = ChartingProgress.read(tasks(), v -> v == CA ? 44 : 0, 30, null, null, ChartingProgress.ANY_BOAT);
		assertFalse(before.currentAffairs);
		assertFalse(before.questAllows(duck));
		assertFalse(before.canDo(duck));
		assertTrue("other tasks don't need it", before.canDo(tasks().getTasks().get(0)));
		assertFalse("the bonus set can't be completed", before.canComplete("Bonus Currents"));
		ChartingProgress after = ChartingProgress.read(tasks(), v -> v == CA ? 45 : 0, 30, null, null, ChartingProgress.ANY_BOAT);
		assertTrue(after.canDo(duck));
		assertFalse("a different reading", before.equals(after));
	}

	private static final int METEOROLOGIST = Tiles.pack(2910, 3211, 0);
	private static final int MEASURE = Tiles.pack(2840, 3315, 0);
	private static final int PICK_UP = Tiles.pack(3033, 3152, 0);

	private static ChartingTasks weather()
	{
		return new ChartingTasks(List.of(new ChartingTasks.Set("Bonus Weather", ChartingTasks.BONUS, "Miscellaneous", 510)),
			List.of(new ChartingTasks.Task(42, 18616, ChartingTasks.WEATHER, "Bonus Weather", "Rimmington Strait", 1, 250, "",
				"", false, new int[]{METEOROLOGIST, MEASURE}, "Help the meteorologist.", "", "", new int[0], -1, "")));
	}

	/**
	 * Weather tasks are done all together: the player is not pathed to another spot after getting a reading, but
	 * straight back to the meteorologist immediately.
	 */
	@Test
	public void aWeatherTaskGoesOutAndStraightBack()
	{
		ChartingTasks.Task w = weather().getTasks().get(0);
		ChartingProgress none = ChartingProgress.read(weather(), v -> 0, 1, id -> false, id -> false,
			ChartingProgress.ANY_BOAT);
		assertEquals("first the meteorologist", METEOROLOGIST, none.legTile(w, false));
		ChartingProgress empty = ChartingProgress.read(weather(), v -> 0, 1, id -> id == ChartingProgress.STATION_EMPTY,
			id -> false, ChartingProgress.ANY_BOAT);
		assertEquals("with the empty station: the measuring spot", MEASURE, empty.legTile(w, false));
		ChartingProgress full = ChartingProgress.read(weather(), v -> 0, 1, id -> id == ChartingProgress.STATION_FULL,
			id -> false, ChartingProgress.ANY_BOAT);
		assertEquals("with the readings: straight back", METEOROLOGIST, full.legTile(w, false));
		RouteRequest r = empty.routeLeg("Bonus Weather", w, false, 3);
		assertArrayEquals(new int[]{MEASURE}, r.getTargets());
		assertEquals("Bonus Weather", ChartingProgress.setOf(r));
		assertEquals(List.of("Help the meteorologist. (250 XP)", "Measure the winds here, then go straight back to the"
			+ " meteorologist.", "Next of the 3 tasks left, in the planned order. Once it's done the route goes on to the next."),
			r.getNotes());
		assertEquals("Take the full weather station straight back to the meteorologist.", full.legNote(w, false));
		assertEquals("The last task you can do now in this set.", full.routeLeg("Bonus Weather", w, false, 1).getNotes()
			.get(2));
	}

	/** The duck leaves the inventory at the start and enters again at the end. */
	@Test
	public void aDuckIsPickedUpAtItsSecondPlace()
	{
		ChartingTasks.Task duck = tasks().getTasks().get(1);
		ChartingProgress p = read(30, Set.of());
		assertEquals(DUCK, p.legTile(duck, false));
		assertEquals(PICK_UP, p.legTile(duck, true));
		assertEquals(PICK_UP, duck.getEndPlace());
		assertEquals("Let the duck go here, then pick it up where the current takes it.", p.legNote(duck, false));
		assertArrayEquals(new int[]{PICK_UP}, p.routeLeg("Bonus Currents", duck, true, 2).getTargets());
		assertNull("one place: no leg note", p.legNote(tasks().getTasks().get(0), false));
	}

	/** A set's tasks go in the planned (salesman) order, done ones skipped. */
	@Test
	public void theNextTaskIsTheFirstOfThePlanLeftToDo()
	{
		List<Integer> plan = List.of(5, 3, 0, 4);
		ChartingProgress p = read(30, Set.of(18574)); // the board (task 0) done
		assertEquals(5, p.nextInPlan("Bay of Sarim", plan).getNumber());
		ChartingProgress later = read(30, Set.of(18574, 18579)); // and the wreck (task 5)
		assertEquals("the duck is in another set, the board is done", 4, later.nextInPlan("Bay of Sarim", plan).getNumber());
		assertNull(read(30, Set.of(18574, 18579, 18578)).nextInPlan("Bay of Sarim", plan));
		assertTrue(p.planCovers("Bay of Sarim", plan));
		assertFalse("the rock became doable and the plan hasn't got it: plan again",
			p.planCovers("Bay of Sarim", List.of(5, 0)));
		assertTrue("at Sailing 20 the rock isn't doable yet", read(20, Set.of(18574)).planCovers("Bay of Sarim", List.of(5, 0)));
	}
}

package com.bestpath.route;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.data.SlayerPlaces;
import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** The Slayer task the game's vars name, and its places in order. */
public class SlayerTaskTest
{
	/** Kalphites (id 53): the lair (Konar area 11), the cave (area 12), a place off the map data; a boss; Krystilia's ankou. */
	public static final SlayerPlaces DATA = SlayerPlaces.parse(""
		+ "master\t7\tKrystilia\n"
		+ "master\t8\tKonar\n"
		+ "master\t3\tVannaka\n"
		+ "task\t6266\t53\tKalphites\n"
		+ "task\t6292\t79\tAnkou\n"
		+ "task\t6311\t98\tBoss\n"
		+ "task\t6165\t-1\tThe Kalphite Queen\n"
		+ "boss\t7\t6165\n"
		+ "area\t11\tKalphite Lair\n"
		+ "area\t12\ttask-only Kalphite Cave\n"
		+ "place\t6266\tFar away\t-1\t?\t?\t?\t?\t1000,1000,0\tno\t-\t-\t-\n"
		+ "place\t6266\tKalphite Lair\t11\t43\tyes\tyes\tno\t3200,9490,0 3226,9500,2\tyes\tKalphite Worker\tP@1\tA rope.\n"
		+ "place\t6266\tKalphite Cave\t12\t20\tyes\tyes\tyes\t3320,9500,0\tyes\t-\tP@1\t-\n"
		+ "place\t6292\tStronghold Slayer Cave\t-1\t10\tno\tno\tno\t2470,9800,0\tyes\t-\tP@1\t-\n"
		+ "place\t6292\tWilderness Slayer Cave\t-1\t8\tyes\tyes\tno\t3385,10050,0\tyes\t-\tP@1\t-\n"
		+ "place\t6165\tKalphite Lair\t-1\t1\t?\t?\t?\t3480,9500,0\tyes\t-\tP@1\t-\n"
		+ "icon\t6266\t4144\n"
		+ "loot\tarea\tKalphite Cave\t19677 19679\tNot from ghosts.\n"
		+ "loot\tmaster\t8\t23083\t-\n");
	/** The Wilderness: from y 3520 up, as one box (enough for these tests). */
	static final WildernessZones WILD = WildernessZones.parse(List.of("2944\t3520\t512\t7000\t1"));

	/** A task just given, the last task read being {@code before} itself. */
	private static boolean isNewTask(SlayerTask before, SlayerTask now)
	{
		return SlayerTask.isNewTask(before, before, now);
	}

	private static List<String> names(SlayerTask t)
	{
		List<String> out = new ArrayList<>();
		for (SlayerTask.Location l : t.getLocations())
		{
			out.add(l.getPlace().getName() + (l.canGo() ? "" : " (no go)"));
		}
		return out;
	}

	/** The automatic path to the nearest Slayer monster comes immediately, only for a task just given. */
	@Test
	public void aNewTaskIsOneJustGiven()
	{
		SlayerTask kalphites = SlayerTask.of(DATA, WILD, 53, 0, 87, 0, 3);
		SlayerTask ankou = SlayerTask.of(DATA, WILD, 79, 0, 10, 0, 3);
		assertTrue("after no task", isNewTask(SlayerTask.NO_TASK, kalphites));
		assertTrue("another task (a swap, a Turael skip)", isNewTask(kalphites, ankou));
		assertFalse("a kill", isNewTask(kalphites, SlayerTask.of(DATA, WILD, 53, 0, 86, 0, 3)));
		assertFalse("the stored task changed", isNewTask(kalphites,
			kalphites.withStored(SlayerTask.Stored.of(DATA, 79, 0, 10, 3))));
		assertFalse("after a login", isNewTask(SlayerTask.LOGGED_OUT, kalphites));
		assertFalse("the map data arriving", isNewTask(SlayerTask.NO_DATA, kalphites));
		assertFalse("the plugin starting", isNewTask(null, kalphites));
		assertFalse("the task done", isNewTask(kalphites, SlayerTask.NO_TASK));
	}

	/** Killing a monster never automatically opens the side panel. */
	@Test
	public void aKillIsNeverANewTask()
	{
		SlayerTask kalphites = SlayerTask.of(DATA, WILD, 53, 0, 87, 0, 3);
		// a kill read while another var (here the master) looks different: not a new task
		SlayerTask oddMaster = SlayerTask.of(DATA, WILD, 53, 0, 86, 0, 8);
		assertTrue("the vars make it look like another task", SlayerTask.looksNew(kalphites, oddMaster));
		assertFalse(isNewTask(kalphites, oddMaster));
		assertFalse(SlayerTask.isNewTask(kalphites, kalphites, oddMaster));
		assertTrue(SlayerTask.isAKill(kalphites, oddMaster));
		// a read with no task between two kills
		assertFalse(SlayerTask.isNewTask(SlayerTask.NO_TASK, kalphites, SlayerTask.of(DATA, WILD, 53, 0, 86, 0, 3)));
		// the same monster given again after the task was done: a full amount, more than were left
		SlayerTask lastOne = SlayerTask.of(DATA, WILD, 53, 0, 1, 0, 3);
		assertTrue(SlayerTask.isNewTask(SlayerTask.NO_TASK, lastOne, SlayerTask.of(DATA, WILD, 53, 0, 120, 0, 3)));
		// another monster with fewer left (a swap with storage): a new task
		assertTrue(SlayerTask.isNewTask(kalphites, kalphites, SlayerTask.of(DATA, WILD, 79, 0, 10, 0, 3)));
		// after logging in: never
		assertFalse(SlayerTask.isNewTask(SlayerTask.LOGGED_OUT, null, kalphites));
	}

	@Test
	public void noTaskNoDataLoggedOut()
	{
		assertSame(SlayerTask.NO_DATA, SlayerTask.of(SlayerPlaces.NONE, WILD, 53, 0, 10, 0, 3));
		assertSame(SlayerTask.NO_DATA, SlayerTask.of(null, WILD, 53, 0, 10, 0, 3));
		assertSame(SlayerTask.NO_TASK, SlayerTask.of(DATA, WILD, 0, 0, 0, 0, 0));
		assertSame("all killed", SlayerTask.NO_TASK, SlayerTask.of(DATA, WILD, 53, 0, 0, 0, 3));
		assertEquals(SlayerTask.State.LOGGED_OUT, SlayerTask.LOGGED_OUT.getState());
	}

	@Test
	public void everyPlaceOfANormalTaskCountsTheReachableFirst()
	{
		SlayerTask t = SlayerTask.of(DATA, WILD, 53, 0, 87, 0, 3);
		assertEquals(SlayerTask.State.TASK, t.getState());
		assertEquals("Kalphites", t.getName());
		assertEquals(87, t.getLeft());
		assertEquals("Vannaka", t.getMaster());
		assertNull(t.area);
		assertNull(t.getRule());
		assertEquals(List.of("Kalphite Lair", "Kalphite Cave", "Far away (no go)"), names(t));
		RouteRequest nearest = t.nearest();
		assertEquals("Slayer: Kalphites (nearest place)", nearest.getTitle());
		assertArrayEquals("the reachable places' points only", new int[]{Tiles.pack(3200, 9490, 0), Tiles.pack(3226, 9500, 2),
			Tiles.pack(3320, 9500, 0)}, nearest.getTargets());
		RouteRequest lair = t.request(t.getLocations().get(0));
		assertEquals("Slayer: Kalphites - Kalphite Lair", lair.getTitle());
		assertEquals("slayer Kalphites Kalphite Lair", lair.getKey());
		assertEquals(List.of("A rope."), lair.getNotes());
		assertEquals(2, lair.targetCount());
	}

	@Test
	public void konarsAreaComesFirstAndOnlyItCounts()
	{
		SlayerTask t = SlayerTask.of(DATA, WILD, 53, 0, 120, 12, 8);
		assertEquals("Konar", t.getMaster());
		assertEquals("task-only Kalphite Cave", t.area);
		assertEquals("Konar's task: only kills in the task-only Kalphite Cave count.", t.getRule());
		assertEquals("places where kills don't count are not shown", List.of("Kalphite Cave"), names(t));
		assertTrue(t.getLocations().get(0).isAssigned());
		assertArrayEquals(new int[]{Tiles.pack(3320, 9500, 0)}, t.nearest().getTargets());
		assertEquals(List.of(t.getRule()), t.nearest().getNotes());
		// an area the data has no place in: nothing to go to
		SlayerTask none = SlayerTask.of(DATA, WILD, 53, 0, 120, 30, 8);
		assertEquals("area 30", none.area);
		assertNull(none.nearest());
	}

	@Test
	public void krystiliasTasksCountOnlyInTheWilderness()
	{
		SlayerTask t = SlayerTask.of(DATA, WILD, 79, 0, 40, 0, 7);
		assertEquals("Krystilia's task: only kills in the Wilderness count.", t.getRule());
		assertEquals("only places in the Wilderness are shown", List.of("Wilderness Slayer Cave"), names(t));
		assertTrue(t.getLocations().get(0).isWilderness());
		// the same task from another master: both count
		SlayerTask vannaka = SlayerTask.of(DATA, WILD, 79, 0, 40, 0, 3);
		assertEquals(List.of("Stronghold Slayer Cave", "Wilderness Slayer Cave"), names(vannaka));
		assertFalse(vannaka.getLocations().get(0).isWilderness());
	}

	/**
	 * On Krystilia's task a place partly in the
	 * Wilderness (the skeletons' Edgeville Dungeon) leads only to its Wilderness part; for others to all of it.
	 */
	@Test
	public void krystiliasPlacePartlyInTheWildernessLeadsOnlyToThatPart()
	{
		SlayerPlaces data = SlayerPlaces.parse(""
			+ "master\t7\tKrystilia\n"
			+ "master\t3\tVannaka\n"
			+ "task\t6300\t87\tSkeletons\n"
			+ "place\t6300\tEdgeville Dungeon\t-1\t?\t?\t?\t?\t3108,9954,0 3100,9900,0 3110,9890,0\tyes\t-\tP@1\t-\n");
		WildernessZones underground = WildernessZones.parse(List.of("2944\t9920\t512\t960\t1"));
		SlayerTask krystilia = SlayerTask.of(data, underground, 87, 0, 50, 0, 7);
		SlayerTask.Location l = krystilia.getLocations().get(0);
		assertTrue(l.canGo());
		assertArrayEquals(new int[]{Tiles.pack(3108, 9954, 0)}, krystilia.request(l).getTargets());
		assertArrayEquals(new int[]{Tiles.pack(3108, 9954, 0)}, krystilia.nearest().getTargets());
		SlayerTask vannaka = SlayerTask.of(data, underground, 87, 0, 50, 0, 3);
		assertEquals(3, vannaka.nearest().targetCount());
	}

	@Test
	public void aBossTaskNamesTheBoss()
	{
		SlayerTask t = SlayerTask.of(DATA, WILD, 98, 7, 3, 0, 6);
		assertEquals("The Kalphite Queen", t.getName());
		assertEquals(List.of("Kalphite Lair"), names(t));
		assertNull("a master the data doesn't name", t.getMaster());
		SlayerTask unknown = SlayerTask.of(DATA, WILD, 140, 0, 3, 0, 6);
		assertEquals("Slayer task 140", unknown.getName());
		assertTrue(unknown.getLocations().isEmpty());
		assertNull(unknown.nearest());
	}

	/** The task's picture (the Slayer plugin's), its Wiki page and each place's special loot. */
	@Test
	public void iconWikiPageAndLoot()
	{
		SlayerTask vannaka = SlayerTask.of(DATA, WildernessZones.NONE, 53, 0, 87, 0, 3);
		assertEquals(4144, vannaka.getIcon());
		assertEquals("P", vannaka.getWikiPage());
		SlayerTask.Location cave = null;
		for (SlayerTask.Location l : vannaka.getLocations())
		{
			if (l.getPlace().getName().equals("Kalphite Cave"))
			{
				cave = l;
			}
		}
		assertEquals(1, cave.getLoot().size());
		assertEquals("Not from ghosts.", cave.getLoot().get(0).getNote());
		SlayerTask konar = SlayerTask.of(DATA, WildernessZones.NONE, 53, 0, 87, 12, 8);
		for (SlayerTask.Location l : konar.getLocations())
		{
			boolean key = false;
			for (com.bestpath.core.data.SlayerPlaces.Loot x : l.getLoot())
			{
				key |= x.getItems()[0] == 23083;
			}
			assertTrue("Konar's brimstone key everywhere", key);
		}
		assertEquals("kept with the travel times", konar.getLocations().get(0).getLoot(),
			konar.withTimes(java.util.Map.of()).getLocations().get(0).getLoot());
		assertEquals(-1, SlayerTask.of(DATA, WildernessZones.NONE, 79, 0, 5, 0, 7).getIcon());
		assertEquals(-1, SlayerTask.NO_TASK.getIcon());
	}
}

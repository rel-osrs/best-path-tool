package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.data.WildernessZones;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.SlayerTask;
import com.bestpath.route.SlayerTaskTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** The Functions tab's Slayer task part. */
public class SlayerSectionTest
{
	private final List<String> calls = new ArrayList<>();
	private int routeTabShown;

	private SlayerSection section()
	{
		return new SlayerSection(new PanelActions()
		{
			@Override
			public void findRoute(RouteRequest request)
			{
				calls.add(request.getTitle() + " " + request.targetCount());
			}

			@Override
			public void clearRoute()
			{
			}

			@Override
			public void useClue()
			{
			}

			@Override
			public void skipForThisRoute(String transportId)
			{
			}

			@Override
			public void clearSkips()
			{
			}

			@Override
			public void settingsChanged(com.bestpath.route.TransportSettings settings)
			{
			}

			@Override
			public void updateSlayerTimes()
			{
				calls.add("update times");
			}

			@Override
			public void openWiki(String page)
			{
				calls.add("wiki " + page);
			}

			@Override
			public void findSlayerNearest(RouteRequest nearest, RouteRequest ifNoRoute)
			{
				calls.add(nearest.getTitle() + " " + nearest.targetCount() + (ifNoRoute == null ? "" : " else " + ifNoRoute.targetCount()));
			}

			@Override
			public void preferSlayerPlace(String task, String place, boolean on)
			{
				calls.add("prefer " + task + " " + place.substring(0, place.indexOf('@')) + " " + on);
			}
		}, () -> routeTabShown++, new IconCache(IconProvider.NONE));
	}

	@Test
	public void sayWhyThereIsNothingToShow()
	{
		SlayerSection s = section();
		assertEquals(SlayerSection.LOGGED_OUT, s.summary());
		assertTrue(s.goButtons.isEmpty());
		s.show(SlayerTask.NO_TASK);
		assertEquals(SlayerSection.NO_TASK, s.summary());
		s.show(SlayerTask.NO_DATA);
		assertEquals(SlayerSection.NO_DATA, s.summary());
	}

	@Test
	public void everyReachablePlaceHasAGoAndTheNearestUsesThemAll()
	{
		SlayerSection s = section();
		s.show(SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3));
		assertEquals("Kalphites: 87 left (from Vannaka).", s.summary());
		assertEquals("the place off the map data has none", 2, s.goButtons.size());
		assertTrue(s.nearest.isEnabled());
		s.goButtons.get(1).doClick();
		s.nearest.doClick();
		assertEquals(List.of("Slayer: Kalphites - Kalphite Cave 1", "Slayer: Kalphites (nearest place) 3"), calls);
		assertEquals(2, routeTabShown);
	}

	@Test
	public void konarsAreaIsMarkedAndTheRestHaveNoGo()
	{
		SlayerSection s = section();
		SlayerTask t = SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 120, 11, 8);
		s.show(t);
		assertEquals(1, s.goButtons.size());
		assertEquals("Konar's area", SlayerSection.mark(t.getLocations().get(0)));
		assertEquals("the places that don't count are not shown", 1, t.getLocations().size());
		// an area without a known place: no Go at all
		s.show(SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 120, 30, 8));
		assertFalse(s.nearest.isEnabled());
		assertTrue(s.goButtons.isEmpty());
		assertTrue(texts(s).contains(SlayerSection.NO_PLACES_COUNT));
	}

	@Test
	public void travelTimesAndNotesAreOnTheCards()
	{
		SlayerSection s = section();
		SlayerTask t = SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3);
		assertEquals(SlayerSection.TIMING, SlayerSection.travel(t, t.getLocations().get(0)));
		assertEquals("no Go, no time", "", SlayerSection.travel(t, t.getLocations().get(2)));
		SlayerTask timed = t.withTimes(java.util.Map.of(t.getLocations().get(0).key(), 236));
		assertEquals("Travel: 236 ticks (2 min 22 s)", SlayerSection.travel(timed, timed.getLocations().get(0)));
		assertEquals(SlayerSection.NOT_TIMED, SlayerSection.travel(timed, timed.getLocations().get(1)));
		SlayerTask none = t.withTimes(java.util.Map.of(t.getLocations().get(0).key(), SlayerTask.NO_ROUTE));
		assertEquals("No route from here", SlayerSection.travel(none, none.getLocations().get(0)));
		s.show(timed);
		assertTrue("the Wiki's notes on the card", texts(s).contains("A rope."));
		assertTrue(texts(s).contains("Travel: 236 ticks (2 min 22 s)"));
		s.update.doClick();
		assertEquals(List.of("update times"), calls);
		assertEquals("the times don't show the Route tab", 0, routeTabShown);
	}

	private static List<String> texts(java.awt.Container c)
	{
		List<String> out = new ArrayList<>();
		for (java.awt.Component k : c.getComponents())
		{
			if (k instanceof WrapLabel)
			{
				out.add(((WrapLabel) k).getText());
			}
			else if (k instanceof java.awt.Container)
			{
				out.addAll(texts((java.awt.Container) k));
			}
		}
		return out;
	}

	/** Monster number, single / multi, cannon and safespot each have their own line with a check mark or a cross. */
	@Test
	public void factsComeFromTheWikisComparison()
	{
		SlayerTask t = SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3);
		SlayerTask.Location lair = t.getLocations().get(0);
		assertEquals("43 monsters", SlayerSection.amount(lair.getPlace()));
		assertEquals("[+ Multi-combat, + Cannon, x Safespot]", SlayerSection.facts(lair).toString());
		assertEquals("[+ Multi-combat, + Cannon, + Safespot]", SlayerSection.facts(t.getLocations().get(1)).toString());
		assertEquals("unknown facts are left out", "[]", SlayerSection.facts(t.getLocations().get(2)).toString());
		assertEquals("", SlayerSection.amount(t.getLocations().get(2).getPlace()));
	}

	/** The helper text includes no links; a point per line. */
	@Test
	public void notesHaveNoLinks()
	{
		assertEquals(List.of("Bones can be buried.", "Safespot behind the pillar."), SlayerSection.notes(
			"*Bones can be buried. *Safespot behind the pillar. [https://oldschool.runescape.wiki/w/File:A.png image]"));
		assertEquals(List.of("Do not bring items you are unwilling to lose."),
			SlayerSection.notes("'''Do not''' bring items you are unwilling to lose."));
		assertEquals(List.of(), SlayerSection.notes("[https://oldschool.runescape.wiki/images/B.gif Click here for a picture guide.]"));
	}

	/** Each has a wiki button to go to the relevant wiki page; the task is in its own card. */
	@Test
	public void wikiButtonsAndTheTaskCard()
	{
		SlayerSection s = section();
		s.show(SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3));
		List<javax.swing.JButton> wiki = s.wikiButtons;
		assertEquals("the task's and each place's", 4, wiki.size());
		wiki.get(0).doClick();
		wiki.get(1).doClick();
		assertEquals(List.of("wiki P", "wiki Kalphite Lair"), calls.subList(0, 2));
		assertEquals("Stronghold of Security", SlayerSection.placePage(new com.bestpath.core.data.SlayerPlaces.Place(1,
			"Stronghold of Security: Catacomb of Famine", -1, "?", "?", "?", "?", new int[]{0}, true, "", "", "")));
		assertTrue(labels(s).contains("Kalphites"));
		assertTrue(labels(s).contains("87 left"));
		assertTrue(labels(s).contains("From Vannaka"));
		assertTrue("the cave's special loot", labels(s).contains("Special loot:"));
		assertTrue(texts(s).contains("Not from ghosts."));
	}

	/** Some spiritual creatures drop dragon boots, some do not: each place's own loot, with its monster. */
	@Test
	public void eachPlaceShowsItsOwnLoot()
	{
		com.bestpath.core.data.SlayerPlaces data = com.bestpath.core.data.SlayerPlaces.parse(""
			+ "master\t3\tVannaka\n"
			+ "task\t6266\t89\tSpiritual Creatures\n"
			+ "place\t6266\tGod Wars Dungeon\t-1\t6\tyes\tno\tno\t2870,5300,2\tyes\t-\tP@1\t-\n"
			+ "place\t6266\tGod Wars Dungeon (5)\t-1\t6\tyes\tno\tno\t2880,5300,2\tyes\t-\tP@1\t-\n"
			+ "loot\tplace\t6266:God Wars Dungeon (5)\t11840 11841 11842 11843 11844 11845 11846 11847 11848 11849\tFrom Spiritual mage.\n");
		SlayerTask t = SlayerTask.of(data, WildernessZones.NONE, 89, 0, 50, 0, 3);
		assertEquals(0, t.getLocations().get(0).getLoot().size());
		assertEquals(1, t.getLocations().get(1).getLoot().size());
		SlayerSection s = section();
		s.show(t);
		assertTrue(texts(s).contains("From Spiritual mage."));
		assertEquals("one place has special loot", 1, java.util.Collections.frequency(labels(s), "Special loot:"));
	}

	/** Toggles filter the list: multicombat, cannonable, safespottable. */
	@Test
	public void theFiltersKeepOnlyThePlacesWithEveryTickedFact()
	{
		SlayerSection s = section();
		s.show(SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3));
		javax.swing.JCheckBox[] f = {s.onlyMulti, s.onlyCannon, s.onlySafespot};
		f[0].doClick(); // multi-combat: the lair and the cave
		assertEquals(2, s.goButtons.size());
		assertTrue(texts(s).contains("Showing 2 of 3 places."));
		f[2].doClick(); // and a safespot: the cave only
		assertEquals(1, s.goButtons.size());
		assertTrue(labels(s).contains("Kalphite Cave"));
		assertFalse(labels(s).contains("Kalphite Lair"));
		s.nearest.doClick();
		assertEquals("the nearest among those shown", List.of("Slayer: Kalphites (nearest place) 1"), calls);
		// another task keeps the ticks: Ankou has no place with a safespot
		s.show(SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 79, 0, 10, 0, 3));
		assertTrue(texts(s).contains(SlayerSection.NONE_SHOWN));
		assertFalse(s.nearest.isEnabled());
		f[0].doClick();
		f[2].doClick();
		assertEquals("none ticked: all", 2, s.goButtons.size());
		assertFalse(texts(s).stream().anyMatch(t -> t.startsWith("Showing")));
	}

	/** The stored task is shown, if any; the plugin does not route to stored tasks. */
	@Test
	public void theStoredTaskHasACardWithoutAGo()
	{
		SlayerSection s = section();
		SlayerTask t = SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3);
		s.show(t.withStored(SlayerTask.Stored.of(SlayerTaskTest.DATA, 79, 0, 12, 3)));
		assertTrue(s.storedCard.isVisible());
		List<String> card = labels(s.storedCard);
		assertTrue(card.containsAll(List.of("Stored task", "Ankou", "12 left", "From Vannaka")));
		assertTrue(texts(s.storedCard).contains(SlayerSection.STORED_NOTE));
		assertEquals("only the current task's places", 2, s.goButtons.size());
		assertEquals("the stored task's Wiki last", 5, s.wikiButtons.size());
		assertEquals("Kalphites: 87 left (from Vannaka). Stored: Ankou.", s.summary());
		// no task now, one in storage the plugin didn't see go in
		s.show(SlayerTask.NO_TASK.withStored(SlayerTask.Stored.of(SlayerTaskTest.DATA, 0, 0, 0, 8)));
		assertTrue(s.storedCard.isVisible());
		assertTrue(labels(s.storedCard).containsAll(List.of(SlayerSection.STORED_UNKNOWN, "From Konar")));
		assertTrue(texts(s.storedCard).contains(SlayerSection.STORED_HOW));
		assertEquals(SlayerSection.NO_TASK + " Stored: a task.", s.summary());
		s.show(SlayerTask.NO_TASK);
		assertFalse(s.storedCard.isVisible());
	}

	/** A "preferred" system (a star, at the top, only among them). */
	@Test
	public void theStarMarksAPlacePreferredAndTheNearestGoesThere()
	{
		SlayerSection s = section();
		s.show(SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3));
		assertEquals("every place has a star", 3, s.starButtons.size());
		assertFalse(((StarIcon) s.starButtons.get(1).getIcon()).filled);
		s.starButtons.get(1).doClick(); // the cave
		assertEquals(List.of("prefer Kalphites Kalphite Cave true"), calls);
		SlayerTask t = s.task;
		assertEquals("at the top at once", "Kalphite Cave", t.getLocations().get(0).getPlace().getName());
		assertTrue(t.getLocations().get(0).isPreferred());
		assertEquals("Preferred", SlayerSection.mark(t.getLocations().get(0)));
		assertTrue(((StarIcon) s.starButtons.get(0).getIcon()).filled);
		assertTrue(texts(s).contains(String.format(SlayerSection.PREFERRED_LINE, "preferred place")));
		s.nearest.doClick();
		assertEquals("only the cave; every place if no route reaches it", "Slayer: Kalphites (nearest preferred place) 1 else 3",
			calls.get(1));
		s.starButtons.get(0).doClick(); // unmark
		assertEquals("prefer Kalphites Kalphite Cave false", calls.get(2));
		assertEquals("back in the Wiki's order", "Kalphite Lair", s.task.getLocations().get(0).getPlace().getName());
		assertTrue(s.task.getLocations().stream().noneMatch(SlayerTask.Location::isPreferred));
		assertFalse(texts(s).contains(String.format(SlayerSection.PREFERRED_LINE, "preferred place")));
		s.nearest.doClick();
		assertEquals("Slayer: Kalphites (nearest place) 3", calls.get(3));
	}

	private static List<String> labels(java.awt.Container c)
	{
		List<String> out = new ArrayList<>();
		for (java.awt.Component k : c.getComponents())
		{
			if (k instanceof javax.swing.JLabel)
			{
				out.add(((javax.swing.JLabel) k).getText());
			}
			if (k instanceof java.awt.Container)
			{
				out.addAll(labels((java.awt.Container) k));
			}
		}
		return out;
	}
}

package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.NoRouteReasons;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.Skill;
import com.bestpath.route.NoRouteWhy;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import com.bestpath.route.TransportSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The Route tab on "no route": why in plain words, then a card per thing the player can't use. */
public class RouteTabNoRouteTest
{
	private final List<TransportSettings> changed = new ArrayList<>();
	private final List<String> skips = new ArrayList<>();
	private TransportSettings current = TransportSettings.defaults().withBlacklisted("gate2", "Gate");

	private RouteTab tab()
	{
		PanelActions actions = new PanelActions()
		{
			@Override
			public void findRoute(RouteRequest request)
			{
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
				skips.add(transportId);
			}

			@Override
			public void clearSkips()
			{
			}

			@Override
			public void settingsChanged(TransportSettings settings)
			{
				changed.add(settings);
			}
		};
		return new RouteTab(actions, () -> current, new IconCache(IconProvider.NONE));
	}

	private static RouteStep step(String title, StepInfo info)
	{
		return new RouteStep(RouteStep.Kind.TRAVERSAL, title, "", 2, List.of(), "x", new int[0], -1, -1).withInfo(info);
	}

	@Test
	public void waysNeedingAsManyThingsHaveAnOrBetweenThem() throws Exception
	{
		// all the ways with the same number of limiting factors show up, with an "or" between the cards
		NoRouteWhy.Blocker crevice = new NoRouteWhy.Blocker(step("Crevice", StepInfo.NONE), List.of(), List.of(), null, false, List.of());
		NoRouteWhy.Blocker gate = new NoRouteWhy.Blocker(step("Ancient Gate", StepInfo.NONE), List.of(), List.of(), null, false, List.of());
		NoRouteWhy.Blocker rope = new NoRouteWhy.Blocker(step("Rope", StepInfo.NONE), List.of(), List.of(), null, false, List.of());
		NoRouteWhy why = NoRouteWhy.ofWays(NoRouteReasons.Kind.BLOCKED,
			"There are 3 ways there, each needing 1 thing you can't use yet:", List.of(),
			List.of(List.of(crevice), List.of(gate), List.of(rope)));
		assertEquals(List.of(List.of(crevice), List.of(gate), List.of(rope)), why.getWays());
		RouteResult r = RouteResults.noRoute(5, new RouteRequest(3228, 3210, 0), "No route to the destination.", List.of(), why);
		SwingUtilities.invokeAndWait(() ->
		{
			RouteTab t = tab();
			t.show(r, Set.of());
			assertEquals(3, t.blockedCards.size());
			assertEquals(2, t.orLabels);
			// one way: no "or"
			t.show(RouteResults.noRoute(6, new RouteRequest(3228, 3210, 0), "No route.", List.of(),
				NoRouteWhy.ofWays(NoRouteReasons.Kind.BLOCKED, "x", List.of(), List.of(List.of(crevice)))), Set.of());
			assertEquals(0, t.orLabels);
		});
	}

	@Test
	public void theReasonsAndACardForEachThing() throws Exception
	{
		StepInfo agility = new StepInfo("Gate", null, "SHORTCUT", false, "", "", -1,
			List.of(StepInfo.Need.skill(Skill.AGILITY, 30).withStatus(com.bestpath.route.NeedStatus.MISSING)));
		NoRouteWhy.Blocker first = new NoRouteWhy.Blocker(step("Gate", agility), List.of("A members world"), List.of(), null, false, List.of());
		NoRouteWhy.Blocker second = new NoRouteWhy.Blocker(step("Gate 2", StepInfo.NONE), List.of(),
			List.of("You chose never to use this."), "gate2", false, List.of());
		NoRouteWhy why = NoRouteWhy.ofWays(NoRouteReasons.Kind.BLOCKED, "The way there needs 2 things you can't use yet:",
			List.of(), List.of(List.of(first, second)));
		RouteResult r = RouteResults.noRoute(5, new RouteRequest(3228, 3210, 0), "No route to the destination.", List.of(), why)
			.withSearchMillis(420);
		assertEquals("Calculated in 420 ms\nThe way there needs 2 things you can't use yet:", RouteTab.noRouteText(r));
		SwingUtilities.invokeAndWait(() ->
		{
			RouteTab t = tab();
			t.show(r, Set.of());
			assertTrue(t.cards.isEmpty());
			assertEquals(2, t.blockedCards.size());
			BlockedCard a = t.blockedCards.get(0);
			assertEquals(List.of("Gate", "X A members world"), a.lines);
			assertNull(a.restore);
			BlockedCard b = t.blockedCards.get(1);
			assertEquals(List.of("Gate 2", "You chose never to use this."), b.lines);
			assertNotNull(b.restore);
			b.restore.doClick();
			assertEquals(1, changed.size());
			assertTrue(!changed.get(0).isBlacklisted("gate2"));
			// a route found later: the "no route" cards go
			t.show(RouteResults.ready(6, new RouteRequest(3228, 3210, 0), List.of()), Set.of());
			assertTrue(t.blockedCards.isEmpty());
		});
	}

	@Test
	public void aTypeTurnedOffHasItsTickBox() throws Exception
	{
		// "Turned off in Settings: <type>" gets the Settings tab's tick box: its state now, a click flips
		// it (saved, a new search), and nothing else changes
		current = TransportSettings.defaults().withCategory(TransportSettings.Category.SHORTCUTS, false)
			.withBlacklisted("gate2", "Gate");
		NoRouteWhy.Blocker off = new NoRouteWhy.Blocker(step("Squeeze-through Gap", StepInfo.NONE), List.of(),
			List.of("Turned off in Settings: Shortcuts."), null, false, TransportSettings.Category.SHORTCUTS);
		NoRouteWhy.Blocker both = new NoRouteWhy.Blocker(step("Gate 2", StepInfo.NONE), List.of(),
			List.of("Turned off in Settings: Shortcuts.", "You chose never to use this."), "gate2", false,
			TransportSettings.Category.SHORTCUTS);
		NoRouteWhy why = NoRouteWhy.ofWays(NoRouteReasons.Kind.BLOCKED, "The way there needs 2 things you can't use yet:",
			List.of(), List.of(List.of(off, both)));
		RouteResult r = RouteResults.noRoute(5, new RouteRequest(3228, 3210, 0), "No route to the destination.", List.of(), why);
		SwingUtilities.invokeAndWait(() ->
		{
			RouteTab t = tab();
			t.show(r, Set.of());
			BlockedCard a = t.blockedCards.get(0);
			assertNull(a.restore);
			assertEquals(1, a.toggles.size());
			JCheckBox box = a.toggles.get(0);
			assertFalse("shows the state now: off", box.isSelected());
			box.doClick();
			assertTrue(box.isSelected());
			assertEquals(1, changed.size());
			assertTrue(changed.get(0).isEnabled(TransportSettings.Category.SHORTCUTS));
			assertTrue(changed.get(0).isBlacklisted("gate2")); // nothing else changes
			// clicked again (before the new search shows): off again
			current = changed.get(0);
			box.doClick();
			assertFalse(box.isSelected());
			assertEquals(2, changed.size());
			assertFalse(changed.get(1).isEnabled(TransportSettings.Category.SHORTCUTS));
			BlockedCard b = t.blockedCards.get(1);
			assertEquals(1, b.toggles.size());
			assertNotNull(b.restore);
		});
		// no tick boxes given: none on the card
		assertTrue(new NoRouteWhy.Blocker(step("x", StepInfo.NONE), List.of(), List.of(), null, false, List.of()).getToggles().isEmpty());
	}

	@Test
	public void aBoatAndTheShipwrightsHaveTheirTickBoxes() throws Exception
	{
		// the same tick box for a boat turned off and for "A shipwright may bring my boat"
		current = TransportSettings.defaults().withBoatAllowed(2, false).withShipwrights(false);
		NoRouteWhy.Toggle boat = NoRouteWhy.Toggle.boat(2, "Boat 2: Skiff", com.bestpath.core.sea.BoatType.SKIFF);
		NoRouteWhy.Blocker b = new NoRouteWhy.Blocker(step("Boat 2: Skiff", StepInfo.NONE), List.of(),
			List.of("Turned off in Settings (your boats).", "A shipwright must bring it, and shipwrights are turned off in Settings."),
			null, false, List.of(boat, NoRouteWhy.Toggle.shipwrights()));
		NoRouteWhy why = NoRouteWhy.ofWays(NoRouteReasons.Kind.BY_BOAT, "The way there is by boat, and needs 1 thing you can't use yet:",
			List.of(), List.of(List.of(b)));
		RouteResult r = RouteResults.noRoute(5, new RouteRequest(3228, 3210, 0), "No route to the destination.", List.of(), why);
		SwingUtilities.invokeAndWait(() ->
		{
			RouteTab t = tab();
			t.show(r, Set.of());
			BlockedCard c = t.blockedCards.get(0);
			assertEquals(2, c.toggles.size());
			assertFalse(c.toggles.get(0).isSelected());
			assertFalse(c.toggles.get(1).isSelected());
			c.toggles.get(0).doClick();
			assertTrue(changed.get(0).isBoatAllowed(2));
			assertFalse(changed.get(0).isShipwrights());
			current = changed.get(0);
			c.toggles.get(1).doClick();
			assertTrue(changed.get(1).isBoatAllowed(2));
			assertTrue(changed.get(1).isShipwrights());
			assertEquals(TransportSettings.defaults(), changed.get(1));
		});
		// a tick box already on (the player ticked it in Settings meanwhile) shows ticked
		current = TransportSettings.defaults();
		SwingUtilities.invokeAndWait(() ->
		{
			RouteTab t = tab();
			t.show(r, Set.of());
			assertTrue(t.blockedCards.get(0).toggles.get(0).isSelected());
		});
	}

	@Test
	public void skipOnACardSkipsItsTransportForThisRoute() throws Exception
	{
		// a skip button on a "no route" explanation: the next way is shown without it
		NoRouteWhy.Blocker gate = new NoRouteWhy.Blocker(step("Gate", StepInfo.NONE), List.of("A members world"), List.of(), null,
			false, List.of());
		NoRouteWhy.Blocker never = new NoRouteWhy.Blocker(new RouteStep(RouteStep.Kind.TRAVERSAL, "Gate 2", "", 2, List.of(), "gate2",
			new int[0], -1, -1), List.of(), List.of("You chose never to use this."), "gate2", false, List.of());
		NoRouteWhy.Blocker skipped = new NoRouteWhy.Blocker(step("Rope", StepInfo.NONE), List.of(),
			List.of("You skipped this for this route (Undo above allows it again)."), null, true, List.of());
		NoRouteWhy.Blocker boat = new NoRouteWhy.Blocker(new RouteStep(RouteStep.Kind.BOAT, "A boat", "", 0, List.of(), null,
			new int[0], -1, -1), List.of(), List.of("You have no boat."), null, false, List.of());
		NoRouteWhy why = NoRouteWhy.ofWays(NoRouteReasons.Kind.BLOCKED, "The way there needs 4 things you can't use yet:",
			List.of(), List.of(List.of(gate, never, skipped, boat)));
		RouteResult r = RouteResults.noRoute(5, new RouteRequest(3228, 3210, 0), "No route to the destination.", List.of(), why);
		SwingUtilities.invokeAndWait(() ->
		{
			RouteTab t = tab();
			t.show(r, Set.of());
			List<BlockedCard> c = t.blockedCards;
			assertNotNull(c.get(0).skip);
			assertNotNull("a blacklisted one: Skip and Restore", c.get(1).skip);
			assertNotNull(c.get(1).restore);
			assertNull("already skipped", c.get(2).skip);
			assertNull("a boat is no transport", c.get(3).skip);
			c.get(1).skip.doClick();
			assertEquals(List.of("gate2"), skips);
			assertTrue("Skip changes no Settings", changed.isEmpty());
		});
	}

	@Test
	public void withoutAWhyTheOldLine()
	{
		RouteResult r = RouteResults.noRoute(5, new RouteRequest(3228, 3210, 0), "No route to the destination.", List.of(), null);
		assertEquals("No route to the destination.", RouteTab.noRouteText(r));
		// a requirement's leaf count words
		assertEquals("Agility 30", new Requirement.SkillLevel(Skill.AGILITY, 30).toText());
	}
}

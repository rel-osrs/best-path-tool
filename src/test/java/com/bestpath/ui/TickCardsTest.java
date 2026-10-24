package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStep;
import com.bestpath.route.TransportSettings;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.Test;

/**
 * The - / + on step cards: the card shows its total ticks right above them at once, never below
 * 0 ticks and never broken down; quick clicks are sent as one settings change (one save, one re-route); the Settings
 * tab lists the changes with each transport's total.
 */
public class TickCardsTest
{
	/** Records the settings changes the Route tab sends. */
	private static final class Recorder implements PanelActions
	{
		final List<TransportSettings> sent = new ArrayList<>();
		TransportSettings current = TransportSettings.defaults();

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
		}

		@Override
		public void clearSkips()
		{
		}

		@Override
		public void settingsChanged(TransportSettings settings)
		{
			sent.add(settings);
			current = settings;
		}
	}

	private static RouteStep ladder(int ticks, int change)
	{
		return new RouteStep(RouteStep.Kind.TRAVERSAL, "Ladder", "to 3210, 3200, 1", ticks, List.of(), "t-ladder", new int[0],
			Tiles.pack(3210, 3200, 0), Tiles.pack(3210, 3200, 1)).withTickChange(change);
	}

	private static RouteResult result(RouteStep... steps)
	{
		return RouteResults.ready(1, new RouteRequest(3220, 3200, 1), List.of(steps));
	}

	private static AbstractButton button(Component c, String text)
	{
		if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText()))
		{
			return (AbstractButton) c;
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				AbstractButton b = button(k, text);
				if (b != null)
				{
					return b;
				}
			}
		}
		return null;
	}

	@Test
	public void clicksShowAtOnceNeverBelowZeroAndAreSentAsOneChange() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			RouteTab t = new RouteTab(r, () -> r.current, new IconCache(IconProvider.NONE));
			// the same ladder twice in one route (up and later down again): both cards follow a click
			t.show(result(ladder(5, 0), ladder(5, 0)), Set.of());
			StepCard card = t.cards.get(0);
			StepCard other = t.cards.get(1);
			// only the total, right above - / +
			assertEquals("to 3210, 3200, 1", card.detailLine.getText());
			assertEquals("5 ticks", PanelDriver.ticksText(card));
			assertEquals(StepCard.TICK_HINT, card.getToolTipText());
			button(card, "+").doClick(0);
			assertEquals("6 ticks", PanelDriver.ticksText(card));
			assertEquals("6 ticks", PanelDriver.ticksText(other));
			assertEquals("to 3210, 3200, 1", card.detailLine.getText());
			for (int i = 0; i < 7; i++)
			{
				button(card, "-").doClick(0);
			}
			assertEquals("0 ticks", PanelDriver.ticksText(card));
			assertFalse("no fewer than 0 ticks", PanelDriver.minusButton(card).isEnabled());
			assertTrue(r.sent.isEmpty());
			assertFalse(t.waitingTicks.isEmpty());
			t.sendTickChanges(); // what the timer does after the last click
			assertEquals("one change for all the clicks", 1, r.sent.size());
			assertEquals(-5, r.sent.get(0).tickChange("t-ladder"));
			assertEquals("Ladder", r.sent.get(0).tickName("t-ladder"));
			t.sendTickChanges();
			assertEquals("nothing more to send", 1, r.sent.size());
			// back to +0: the change is removed
			for (int i = 0; i < 5; i++)
			{
				button(other, "+").doClick(0);
			}
			assertEquals("5 ticks", PanelDriver.ticksText(card));
			t.sendTickChanges();
			assertEquals(2, r.sent.size());
			assertTrue(r.sent.get(1).getTickChanges().isEmpty());
		});
	}

	/** A ride of network "Test ring" ({@code from} to {@code to}), 5 ticks of which {@code change} the network's change. */
	private static RouteStep ringRide(String from, String to, int change)
	{
		return new RouteStep(RouteStep.Kind.TRAVERSAL, "Test ring: " + to, "", 5, List.of(), "Test ring:" + from + ">" + to,
			new int[0], Tiles.pack(3210, 3200, 0), Tiles.pack(3230, 3200, 0))
			.withGroupTickChange(change, TransportSettings.networkKey("Test ring"));
	}

	@Test
	public void aNetworkCardsMinusAndPlusChangeTheWholeNetwork() throws Exception
	{
		// on network cards the - / + changes the whole network
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			RouteTab t = new RouteTab(r, () -> r.current, new IconCache(IconProvider.NONE));
			t.show(result(ringRide("a", "b", 0), ladder(5, 0), ringRide("b", "c", 0)), Set.of());
			StepCard first = t.cards.get(0);
			StepCard ladder = t.cards.get(1);
			StepCard second = t.cards.get(2);
			button(first, "+").doClick(0);
			button(first, "+").doClick(0);
			assertEquals("7 ticks", PanelDriver.ticksText(first));
			assertEquals("another ride of the same network follows", "7 ticks", PanelDriver.ticksText(second));
			assertEquals("the ladder does not", "5 ticks", PanelDriver.ticksText(ladder));
			t.sendTickChanges();
			assertEquals(1, r.sent.size());
			assertEquals(2, r.sent.get(0).networkTickChange("Test ring"));
			assertTrue("no change of the ride itself", r.sent.get(0).getTickChanges().isEmpty());
			// the card starts from the network's saved change
			r.current = r.sent.get(0);
			t.show(result(ringRide("a", "b", 2)), Set.of());
			assertEquals("5 ticks", PanelDriver.ticksText(t.cards.get(0)));
			button(t.cards.get(0), "-").doClick(0);
			button(t.cards.get(0), "-").doClick(0);
			t.sendTickChanges();
			assertEquals("back to +0: the change is removed", 0, r.sent.get(1).networkTickChange("Test ring"));
		});
	}

	@Test
	public void aCardShowsTheChangeItsRouteUsedAndTheNewestSetting() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			r.current = TransportSettings.defaults().withTickChange("t-ladder", -2, "Ladder");
			RouteTab t = new RouteTab(r, () -> r.current, new IconCache(IconProvider.NONE));
			// searched with the change: 5 data ticks - 2
			t.show(result(ladder(3, -2)), Set.of());
			assertEquals("3 ticks", PanelDriver.ticksText(t.cards.get(0)));
			// a route searched before the newest change (it is on its way): the card shows the newest change
			t.show(result(ladder(5, 0)), Set.of());
			assertEquals("3 ticks", PanelDriver.ticksText(t.cards.get(0)));
			button(t.cards.get(0), "+").doClick(0);
			assertEquals("4 ticks", PanelDriver.ticksText(t.cards.get(0)));
			t.sendTickChanges();
			assertEquals(-1, r.sent.get(0).tickChange("t-ladder"));
		});
	}

	@Test
	public void walksAndSailingStepsHaveNoControl() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			RouteTab t = new RouteTab(r, () -> r.current, new IconCache(IconProvider.NONE));
			RouteStep board = new RouteStep(RouteStep.Kind.BOAT, "Board your sloop", "", 10, List.of(), null, new int[0], -1);
			t.show(result(board), Set.of());
			StepCard c = t.cards.get(0);
			assertNull(PanelDriver.minusButton(c));
			assertNull(button(c, "+"));
			assertNull(PanelDriver.ticksText(c));
			assertEquals("10 ticks", c.detailLine.getText());
		});
	}

	/** Every wrapped text in the component (WrapLabel), in order. */
	/** The first component with this name, or null. */
	static Component named(Component c, String name)
	{
		if (name.equals(c.getName()))
		{
			return c;
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				Component f = named(k, name);
				if (f != null)
				{
					return f;
				}
			}
		}
		return null;
	}

	static List<String> texts(Component c)
	{
		List<String> out = new ArrayList<>();
		if (c instanceof WrapLabel)
		{
			out.add(((WrapLabel) c).getText());
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				out.addAll(texts(k));
			}
		}
		return out;
	}

	@Test
	public void quickClicksAreSentOnceAfterAShortPause() throws Exception
	{
		Recorder r = new Recorder();
		AtomicReference<RouteTab> tab = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			RouteTab t = new RouteTab(r, () -> r.current, new IconCache(IconProvider.NONE));
			t.show(result(ladder(5, 0)), Set.of());
			for (int i = 0; i < 3; i++)
			{
				button(t.cards.get(0), "+").doClick(0);
			}
			tab.set(t);
		});
		long until = System.currentTimeMillis() + 5_000;
		int[] sent = {0};
		while (System.currentTimeMillis() < until)
		{
			SwingUtilities.invokeAndWait(() -> sent[0] = r.sent.size());
			if (sent[0] > 0)
			{
				break;
			}
			Thread.sleep(50);
		}
		Thread.sleep(RouteTab.TICK_SEND_DELAY_MS + 200); // nothing more comes
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals(1, r.sent.size());
			assertEquals(3, r.sent.get(0).tickChange("t-ladder"));
			assertTrue(tab.get().waitingTicks.isEmpty());
		});
	}

	@Test
	public void theSettingsTabListsTheChangesToResetThem() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			SettingsTab s = new SettingsTab(r, new IconCache(IconProvider.NONE));
			// the changes are in "Transport ticks" (recently changed first), each with "Reset"
			s.show(TransportSettings.defaults().withTickChange("t-ladder", 2, "Ladder", 2000L).withTickChange("t-tele", -1, "Tele",
				1000L));
			assertTrue("before the map data: the names of the changes", texts(s).contains("Tele"));
			List<com.bestpath.core.data.TransportLine> lines = List.of(com.bestpath.core.data.TransportLine.builder("t-ladder", com.bestpath.core.data.TransportLine.Kind.TWO_WAY)
				.name("Ladder").category("CLIMB_BOTH").start(3210, 3200, 0).end(3210, 3200, 1).ticks(5).build());
			com.bestpath.core.data.TickGroups groups = com.bestpath.core.data.TickGroups.of(lines, com.bestpath.core.data.Tables.empty());
			s.showTickGroups(groups);
			s.showTransports(com.bestpath.route.TransportFinder.of(lines, com.bestpath.core.data.Tables.empty(), groups, WildernessZones.NONE));
			// 5 + the change 2 + the plane change: the total only, never the change
			String total = (7 + com.bestpath.core.data.PluginRouter.PLANE_CHANGE_PENALTY) + " ticks";
			assertTrue(texts(s).toString(), texts(s).contains("Ladder"));
			assertEquals(total, ((TickStepper) named(s, "ticks:t-ladder")).total.getText());
			assertTrue("an id the data does not have: its name", texts(s).contains("Tele"));
			AbstractButton reset = button(s, "Reset"); // the ladder's: changed last, listed first
			reset.doClick(0);
			s.transportTicks.sendChanges();
			assertEquals(1, r.sent.size());
			assertEquals(0, r.sent.get(0).tickChange("t-ladder"));
			assertEquals(-1, r.sent.get(0).tickChange("t-tele"));
		});
	}

	/** Lays a component and everything in it out at this width (no window: as the UI pictures do). */
	static void layout(Component c, int width)
	{
		c.setSize(width, c.getPreferredSize().height);
		for (int pass = 0; pass < 2; pass++)
		{
			layoutTree(c);
		}
		c.setSize(width, c.getPreferredSize().height);
		layoutTree(c);
	}

	private static void layoutTree(Component c)
	{
		if (c instanceof Container)
		{
			((Container) c).doLayout();
			for (Component k : ((Container) c).getComponents())
			{
				layoutTree(k);
			}
		}
	}

	private static java.awt.Rectangle in(Component c, Component root)
	{
		return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), root);
	}

	/**
	 * The total ticks go directly above the - / + controls, not beside them. At the sidebar's
	 * width (a card in the 242 px sidebar), the total sits right above - and +, over them, and Skip / Never use stay
	 * on the left, level with - / +; nothing is cut off.
	 */
	@Test
	public void theTotalSitsDirectlyAboveTheButtons() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			StepCard card = new StepCard(1, ladder(25, 0), new IconCache(IconProvider.NONE), s -> { }, s -> { },
				new StepCard.TickControl()
				{
					@Override
					public int change(RouteStep s)
					{
						return 0;
					}

					@Override
					public void changed(RouteStep s, int change)
					{
					}
				}, null);
			layout(card, Ui.CONTENT_WIDTH);
			AbstractButton minus = button(card, "-");
			AbstractButton plus = button(card, "+");
			TickStepper stepper = (TickStepper) SwingUtilities.getAncestorOfClass(TickStepper.class, minus);
			Component total = ((java.awt.Container) stepper.getComponent(0)).getComponent(0); // the total, in its click-to-type box
			assertEquals("25 ticks", ((javax.swing.JLabel) total).getText());
			java.awt.Rectangle t = in(total, card);
			java.awt.Rectangle m = in(minus, card);
			java.awt.Rectangle p = in(plus, card);
			java.awt.Rectangle skip = in(button(card, "Skip"), card);
			java.awt.Rectangle never = in(button(card, "Never use"), card);
			assertTrue("above: " + t + " " + m, t.y + t.height <= m.y && m.y - (t.y + t.height) <= 3);
			assertEquals("- and + side by side", m.y, p.y);
			assertTrue("over the buttons: " + t + " " + m + " " + p, t.x < p.x + p.width && t.x + t.width > m.x);
			int centre = (m.x + p.x + p.width) / 2;
			assertTrue("centred over them", Math.abs(t.x + t.width / 2 - centre) <= 2);
			assertTrue("Skip left of the total and - / +", skip.x + skip.width <= m.x && never.x + never.width <= m.x
				&& never.x + never.width <= t.x);
			assertTrue("Skip level with - / +", Math.abs((skip.y + skip.height / 2) - (m.y + m.height / 2)) <= 2);
			assertTrue("fits the card: " + p + " in " + card.getWidth(), p.x + p.width <= card.getWidth());
		});
	}
}

package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TickGroups;
import com.bestpath.core.data.TransportLine;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.TransportSettings;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.Test;

/**
 * The Settings tab's "Teleport and travel ticks" (category adjustments, as in the Data Tool): every teleport category and network with its icon, its total ticks above - / + (never a
 * breakdown); clicks show at once and are sent as one settings change after a short pause; "Your tick changes" lists
 * them with their totals and Reset.
 */
public class TickGroupSettingsTest
{
	/** Records the settings changes the Settings tab sends. */
	private static final class Recorder implements PanelActions
	{
		final List<TransportSettings> sent = new ArrayList<>();

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
		}
	}

	private static TransportLine line(String id, TransportLine.Kind kind, String network, String teleportCategory, int ticks)
	{
		return TransportLine.builder(id, kind).name(id).category("TRAVEL")
			.start(kind == TransportLine.Kind.TELEPORT ? 0 : 3200, 3200, 0).end(3210, 3210, 0).ticks(ticks)
			.network(network).teleportCategory(teleportCategory).build();
	}

	/**
	 * The spirit tree, quetzal, magic mushtree, charter ship, magic carpet, boat or ship, and soul wars portal networks
	 * use generic transport icons, like the gnome glider.
	 */
	@Test
	public void someNetworksHaveTheTransportIconAsTheGnomeGlider()
	{
		List<String> names = List.of("Spirit tree", "Quetzal", "Magic mushtree", "Charter ship", "Magic carpet", "Boat or ship",
			"Soul Wars portal", "Gnome glider");
		List<TransportLine> lines = new java.util.ArrayList<>();
		List<String> rows = new java.util.ArrayList<>();
		for (String n : names)
		{
			lines.add(line(n, TransportLine.Kind.NETWORK, n, "", 5));
			rows.add(n + "\t0\t\t" + n);
		}
		TickGroups g = TickGroups.of(lines, Tables.parse(List.of(), List.of(), rows, List.of(), List.of()));
		for (TickGroups.Group k : g.getGroups())
		{
			if (k.getKind() == TickGroups.Kind.NETWORK)
			{
				assertEquals(k.getName(), IconChooser.category(TransportSettings.Category.BOATS), IconChooser.tickGroup(k));
			}
		}
	}

	/** Two categories and two networks as the tables list them; rides of 5-20 ticks in the fairy ring network. */
	static TickGroups groups()
	{
		List<TransportLine> lines = List.of(
			line("s1", TransportLine.Kind.TELEPORT, "", "spell", 3),
			line("j1", TransportLine.Kind.TELEPORT, "", "jewellery (infinite)", 3),
			line("f1", TransportLine.Kind.NETWORK, "Fairy ring", "", 5),
			line("f2", TransportLine.Kind.NETWORK, "Fairy ring", "", 20),
			line("h1", TransportLine.Kind.ONE_WAY, "Player-owned house", "", 3));
		return TickGroups.of(lines, Tables.parse(List.of(), List.of(),
			List.of("Fairy ring\t0\t\trings", "Player-owned house\t10\t\thouse"),
			List.of("spell\t4\tspells", "jewellery (infinite)\t4\tjewellery"), List.of()));
	}

	@Test
	public void everyCategoryAndNetworkHasItsTotalAndIcon() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			SettingsTab s = new SettingsTab(r, new IconCache(IconProvider.NONE));
			TickGroupList list = s.tickGroups;
			assertTrue("before the map data has loaded", TickCardsTest.texts(list).contains(TickGroupList.LOADING));
			s.showTickGroups(groups());
			assertFalse(TickCardsTest.texts(list).contains(TickGroupList.LOADING));
			assertEquals(List.of("Spell", "Jewellery (infinite)", "Fairy ring", "Player-owned house"),
				TickCardsTest.texts(list));
			assertEquals("4 ticks", PanelDriver.totalText(list, "teleport:spell"));
			assertEquals("5-20 ticks", PanelDriver.totalText(list, "network:fairy ring"));
			assertEquals("3 + the network's 10", "13 ticks", PanelDriver.totalText(list, "network:player-owned house"));
			// each row has its own relevant picture
			List<TickGroups.Group> g = groups().getGroups();
			assertEquals(IconChooser.category(TransportSettings.Category.TELEPORT_SPELLS), IconChooser.tickGroup(g.get(0)));
			assertEquals("Amulet of eternal glory", IconKey.item(19707), IconChooser.tickGroup(g.get(1)));
			assertEquals("Dramen staff", IconKey.item(772), IconChooser.tickGroup(g.get(2)));
			assertEquals(IconChooser.category(TransportSettings.Category.HOUSE), IconChooser.tickGroup(g.get(3)));
		});
	}

	@Test
	public void clicksShowAtOnceNeverBelowZeroAndAreSentAsOneChange() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			SettingsTab s = new SettingsTab(r, new IconCache(IconProvider.NONE));
			s.showTickGroups(groups());
			TickGroupList list = s.tickGroups;
			TickStepper spell = PanelDriver.stepper(list, "teleport:spell");
			spell.minus.doClick(0);
			assertEquals("3 ticks", PanelDriver.totalText(list, "teleport:spell"));
			assertTrue(r.sent.isEmpty());
			assertFalse(list.waiting.isEmpty());
			for (int i = 0; i < 6; i++)
			{
				spell.minus.doClick(0);
			}
			assertEquals("0 ticks", PanelDriver.totalText(list, "teleport:spell"));
			assertFalse("no fewer than 0 ticks", spell.minus.isEnabled());
			TickStepper ring = PanelDriver.stepper(list, "network:fairy ring");
			for (int i = 0; i < 6; i++)
			{
				ring.minus.doClick(0);
			}
			assertEquals("the quickest rides stop at 0, the others go on", "0-14 ticks", PanelDriver.totalText(list, "network:fairy ring"));
			for (int i = 0; i < 30; i++)
			{
				ring.minus.doClick(0);
			}
			assertEquals("0 ticks", PanelDriver.totalText(list, "network:fairy ring"));
			assertFalse(ring.minus.isEnabled());
			ring.plus.doClick(0);
			assertEquals("0-1 ticks", PanelDriver.totalText(list, "network:fairy ring"));
			s.tickGroups.sendChanges(); // what the timer does after the last click
			assertEquals("one change for all the clicks", 1, r.sent.size());
			TransportSettings sent = r.sent.get(0);
			assertEquals(-4, sent.teleportCategoryTickChange("spell"));
			assertEquals(-19, sent.networkTickChange("Fairy ring"));
			assertTrue(list.waiting.isEmpty());
			// each changed type has Reset under its - / + (not listed above the search any more)
			assertTrue(PanelDriver.resetButton(list, "teleport:spell").isVisible());
			assertTrue(PanelDriver.resetButton(list, "network:fairy ring").isVisible());
			List<String> texts = TickCardsTest.texts(s);
			assertFalse(texts.toString(), texts.contains("Spell (all): 0 ticks"));
			PanelDriver.resetButton(list, "teleport:spell").doClick(0);
			assertFalse("hidden once back to the usual ticks", PanelDriver.resetButton(list, "teleport:spell").isVisible());
			s.tickGroups.sendChanges(); // sent after the pause, as a click
			assertEquals(2, r.sent.size());
			assertEquals(0, r.sent.get(1).teleportCategoryTickChange("spell"));
			assertEquals(-19, r.sent.get(1).networkTickChange("Fairy ring"));
			assertEquals("4 ticks", PanelDriver.totalText(list, "teleport:spell"));
			// back to the data's ticks with +: the change is removed
			for (int i = 0; i < 19; i++)
			{
				ring.plus.doClick(0);
			}
			s.tickGroups.sendChanges();
			assertEquals(0, r.sent.get(2).networkTickChange("Fairy ring"));
		});
	}

	@Test
	public void savedChangesShowAndWithoutDataTheListHasNamesOnly() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			SettingsTab s = new SettingsTab(r, new IconCache(IconProvider.NONE));
			s.show(TransportSettings.defaults().withGroupTickChange("teleport:spell", 3)
				.withGroupTickChange("network:fairy ring", -2));
			s.showTickGroups(groups());
			assertEquals("7 ticks", PanelDriver.totalText(s.tickGroups, "teleport:spell"));
			assertEquals("3-18 ticks", PanelDriver.totalText(s.tickGroups, "network:fairy ring"));
			assertTrue("Reset under a changed type's - / +", PanelDriver.resetButton(s.tickGroups, "teleport:spell").isVisible());
			assertNull(PanelDriver.totalText(s.tickGroups, "network:nope"));
			assertTrue(r.sent.isEmpty());
		});
	}

	@Test
	public void quickClicksAreSentOnceAfterAShortPause() throws Exception
	{
		Recorder r = new Recorder();
		AtomicReference<SettingsTab> tab = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsTab s = new SettingsTab(r, new IconCache(IconProvider.NONE));
			s.showTickGroups(groups());
			for (int i = 0; i < 3; i++)
			{
				PanelDriver.stepper(s.tickGroups, "teleport:spell").plus.doClick(0);
			}
			tab.set(s);
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
			assertEquals(3, r.sent.get(0).teleportCategoryTickChange("spell"));
			assertTrue(tab.get().tickGroups.waiting.isEmpty());
		});
	}

	private static AbstractButton find(Component c, String text)
	{
		if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText()))
		{
			return (AbstractButton) c;
		}
		if (c instanceof java.awt.Container)
		{
			for (Component k : ((java.awt.Container) c).getComponents())
			{
				AbstractButton b = find(k, text);
				if (b != null)
				{
					return b;
				}
			}
		}
		return null;
	}
}

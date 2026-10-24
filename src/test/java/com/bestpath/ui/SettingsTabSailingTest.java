package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.sea.BoatType;
import com.bestpath.player.BoatInfo;
import com.bestpath.player.PlayerBoats;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.TransportSettings;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The Settings tab's Sailing section: one row per boat, its box, the shipwright box, the notes without boats. */
public class SettingsTabSailingTest
{
	private final List<TransportSettings> changes = new ArrayList<>();

	private SettingsTab tab()
	{
		PanelActions record = new PanelActions()
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
			}

			@Override
			public void clearSkips()
			{
			}

			@Override
			public void settingsChanged(TransportSettings settings)
			{
				changes.add(settings);
			}
		};
		return new SettingsTab(record, new IconCache(IconProvider.NONE));
	}

	private static PlayerBoats boats()
	{
		return new PlayerBoats(PlayerBoats.Status.READ, List.of(), List.of(
			new BoatInfo(1, BoatType.SLOOP, "Sloop: Jolly Raven", "At Port Sarim", true, "Sloop, Teak hull"),
			new BoatInfo(3, BoatType.SKIFF, "Skiff: Merry Wave", "At an unknown place (200)", false,
				"Not at a dock the plugin knows: routes don't use it.")), null);
	}

	/** The checkboxes in the order shown (not the config panel's settings, ConfigMirror, at the top and bottom). */
	private static List<JCheckBox> boxes(Component c, List<JCheckBox> out)
	{
		if (c instanceof JCheckBox && (c.getName() == null || !c.getName().startsWith("config:")))
		{
			out.add((JCheckBox) c);
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				boxes(k, out);
			}
		}
		return out;
	}

	/** Texts of the wrapped labels, in order. */
	private static List<String> texts(Component c, List<String> out)
	{
		if (c instanceof WrapLabel)
		{
			out.add(((WrapLabel) c).getText());
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				texts(k, out);
			}
		}
		return out;
	}

	@Test
	public void oneRowPerBoatAndItsBoxSwitchesTheBoat() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsTab t = tab();
			int before = boxes(t, new ArrayList<>()).size();
			t.showBoats(boats());
			List<JCheckBox> all = boxes(t, new ArrayList<>());
			assertEquals(before + 2, all.size());
			List<String> text = texts(t, new ArrayList<>());
			int sloop = text.indexOf("Sloop: Jolly Raven");
			assertTrue(text.toString(), sloop >= 0);
			assertEquals("At Port Sarim", text.get(sloop + 1));
			assertTrue(text.contains("At an unknown place (200)"));

			// the sloop's box: ticked (every boat is allowed by default); the skiff at an unknown place: off and greyed
			JCheckBox sloopBox = all.get(all.size() - 4);
			JCheckBox skiffBox = all.get(all.size() - 3);
			JCheckBox wrights = all.get(all.size() - 2);
			JCheckBox keep = all.get(all.size() - 1);
			assertTrue(sloopBox.isSelected());
			assertTrue(sloopBox.isEnabled());
			assertFalse(skiffBox.isSelected());
			assertFalse(skiffBox.isEnabled());
			assertTrue(wrights.isSelected());
			// "Never leave my boat at sea", off by default
			assertFalse(keep.isSelected());

			sloopBox.doClick(0);
			assertEquals(1, changes.size());
			assertFalse(changes.get(0).isBoatAllowed(1));
			assertTrue(changes.get(0).isBoatAllowed(3));
			assertFalse(t.getSettings().isBoatAllowed(1));

			// the rows are rebuilt: the sloop's box now shows unticked
			all = boxes(t, new ArrayList<>());
			assertFalse(all.get(all.size() - 4).isSelected());

			all.get(all.size() - 2).doClick(0);
			assertFalse(changes.get(changes.size() - 1).isShipwrights());
			all.get(all.size() - 1).doClick(0);
			assertTrue(changes.get(changes.size() - 1).isKeepBoat());

			// settings shown from elsewhere (account switch) tick the boxes again
			t.show(TransportSettings.defaults());
			all = boxes(t, new ArrayList<>());
			assertTrue(all.get(all.size() - 4).isSelected());
			assertTrue(all.get(all.size() - 2).isSelected());
			assertFalse(all.get(all.size() - 1).isSelected());
		});
	}

	@Test
	public void aShortNoteWithoutBoats() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsTab t = tab();
			assertTrue(texts(t, new ArrayList<>()).contains(SettingsTab.noBoatsText(PlayerBoats.NOT_LOGGED_IN)));
			t.showBoats(new PlayerBoats(PlayerBoats.Status.READ, List.of(), List.of(), null));
			List<String> text = texts(t, new ArrayList<>());
			assertTrue(text.toString(), text.contains("You have no boats yet. Routes sail once you own one."));
			t.showBoats(PlayerBoats.NO_DATA);
			assertTrue(texts(t, new ArrayList<>()).contains(SettingsTab.noBoatsText(PlayerBoats.NO_DATA)));
			t.showBoats(new PlayerBoats(PlayerBoats.Status.READ, List.of(), boats().getRows(), "On someone else's boat."));
			assertTrue(texts(t, new ArrayList<>()).contains("On someone else's boat."));
		});
	}

	@Test
	public void boatPictures()
	{
		assertEquals(IconKey.sprite(IconChooser.SLOOP), IconChooser.boat(BoatType.SLOOP));
		assertEquals(IconKey.sprite(IconChooser.RAFT), IconChooser.boat(BoatType.RAFT));
		assertEquals(IconKey.sprite(IconChooser.SKIFF), IconChooser.boat(BoatType.SKIFF));
		assertNull(IconChooser.boat(null));
		// the sprites the cache's boat table names (table 166 column 41)
		assertEquals(7111, IconChooser.RAFT);
		assertEquals(7112, IconChooser.SKIFF);
		assertEquals(7113, IconChooser.SLOOP);
		assertTrue(IconLists.allSprites().containsKey(IconChooser.SLOOP));
	}
}

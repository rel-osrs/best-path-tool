package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestpath.player.HouseView;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.TransportSettings;
import java.awt.Component;
import java.awt.Container;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;

/**
 * The Settings tab's "House (POH)" section: where the house is, what the house scan
 * found, when it last looked, and a hint before the first look. Nothing to tick (scanning only).
 */
public class SettingsTabHouseTest
{
	private static SettingsTab tab()
	{
		PanelActions none = new PanelActions()
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
			}
		};
		return new SettingsTab(none, new IconCache(IconProvider.NONE));
	}

	/** Texts of the labels, in order. */
	private static List<String> texts(Component c, List<String> out)
	{
		if (c instanceof WrapLabel)
		{
			out.add(((WrapLabel) c).getText());
		}
		else if (c instanceof JLabel)
		{
			out.add(((JLabel) c).getText());
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

	private static int boxes(Component c)
	{
		int n = c instanceof JCheckBox ? 1 : 0;
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				n += boxes(k);
			}
		}
		return n;
	}

	@Test
	public void theHouseSection() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsTab t = tab();
			List<String> text = texts(t, new ArrayList<>());
			assertFalse(text.toString(), text.contains("Coming later"));
			assertTrue(text.contains("House (POH)"));
			assertTrue(text.contains("Log in to see your house."));
			int boxesBefore = boxes(t);

			t.showHouse(HouseView.of(null, Set.of(), 0));
			text = texts(t, new ArrayList<>());
			assertTrue(text.contains("No house found"));
			assertTrue(text.contains(SettingsTab.NO_HOUSE_NOTE));

			// a house not looked at yet: the hint
			t.showHouse(HouseView.of("Rimmington", Set.of(), 0));
			text = texts(t, new ArrayList<>());
			assertTrue(text.contains("Your house: Rimmington"));
			assertTrue(text.contains(HouseView.NOT_LOOKED_HINT));
			assertFalse(text.contains("Built:"));

			// looked at: what was found, when, and how to look again
			long at = 1_791_964_800_000L; // 2026-10-14 08:00 UTC
			t.showHouse(HouseView.of("Rimmington", Set.of("ornate jewellery box", "portal nexus: Varrock Teleport"), at));
			text = texts(t, new ArrayList<>());
			assertFalse(text.contains(HouseView.NOT_LOOKED_HINT));
			int built = text.indexOf("Built:");
			assertTrue(text.toString(), built > 0);
			assertEquals("Ornate jewellery box", text.get(built + 1));
			assertEquals("Portal nexus (1 destination)", text.get(built + 2));
			assertEquals("Varrock Teleport", text.get(built + 3));
			assertTrue(text.toString(), text.get(built + 4).startsWith("Last looked: "));
			assertTrue(text.contains(SettingsTab.HOUSE_AGAIN));

			// looked, nothing found
			t.showHouse(HouseView.of("Rimmington", Set.of(), at));
			text = texts(t, new ArrayList<>());
			assertTrue(text.contains(SettingsTab.HOUSE_NOTHING));
			assertEquals("nothing to tick (scanning only)", boxesBefore, boxes(t));
		});
	}

	@Test
	public void lastLookedText()
	{
		assertEquals("Last looked: 14 Oct 2026, 08:00", SettingsTab.lastLookedText(1_791_964_800_000L, ZoneOffset.UTC));
	}

	@Test
	public void housePictures()
	{
		HouseView v = HouseView.of("Rimmington", Set.of("mounted glory", "portal chamber: Varrock Teleport", "portal chamber: Icy basalt",
			"fairy ring", "ornate jewellery box", "lunar altar"), 1);
		List<IconKey> keys = new ArrayList<>();
		for (HouseView.Row r : v.rows())
		{
			keys.add(IconChooser.houseRow(r));
		}
		// box, glory, garden, altar, then the portals (by name: Icy basalt, Varrock Teleport)
		assertEquals(List.of(IconKey.sprite(IconChooser.HOUSE), IconKey.item(IconChooser.MOUNTED_GLORY), IconKey.sprite(IconChooser.TRANSPORT),
			IconKey.sprite(IconChooser.SPELLBOOK), IconKey.sprite(IconChooser.HOUSE), IconKey.sprite(IconChooser.SPELLS.get("Varrock Teleport"))), keys);
		assertTrue(IconLists.allItems().contains(IconChooser.MOUNTED_GLORY));
	}
}

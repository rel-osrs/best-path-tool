package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;

/**
 * Settings sections are collapsible lists with a dropdown arrow on the left side of the section title (not a
 * show / hide button); the content is not indented.
 */
public class SettingsFoldTest
{
	private static void all(Component c, List<Component> out)
	{
		out.add(c);
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				all(k, out);
			}
		}
	}

	private static JLabel heading(Component root, String text)
	{
		List<Component> cs = new ArrayList<>();
		all(root, cs);
		for (Component c : cs)
		{
			if (c instanceof JLabel && text.equals(((JLabel) c).getText()) && ((JLabel) c).getIcon() instanceof FoldArrow)
			{
				return (JLabel) c;
			}
		}
		return null;
	}

	private static void click(Component c)
	{
		MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false);
		for (java.awt.event.MouseListener l : c.getMouseListeners())
		{
			l.mouseClicked(e);
		}
	}

	@Test
	public void everySectionFoldsWithTheArrowOnItsLeftAndNoShowOrHide() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsTab s = new SettingsTab(new PanelActions()
			{
				@Override
				public void findRoute(com.bestpath.route.RouteRequest request)
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
				public void settingsChanged(com.bestpath.route.TransportSettings settings)
				{
				}
			}, new IconCache(IconProvider.NONE));
			for (String title : List.of("Routes and world map", "Graphics", "Transport types", "Avoid",
				"Never used (blacklist)", SettingsTab.PREFERRED_TITLE, "Transport ticks", "Bank", "House (POH)", "Sailing", SettingsTab.TICKS_TITLE, "Advanced"))
			{
				assertNotNull(title, heading(s, title));
			}
			List<Component> cs = new ArrayList<>();
			all(s, cs);
			for (Component c : cs)
			{
				// ("Show" in "Transport ticks" names its Show filter, not a fold)
				if (c instanceof JLabel && SwingUtilities.getAncestorOfClass(TransportTickList.class, c) == null)
				{
					String t = ((JLabel) c).getText();
					assertFalse("no Show / Hide words: " + t, "Show".equals(t) || "Hide".equals(t));
				}
			}

			JLabel bank = heading(s, "Bank");
			JLabel house = heading(s, "House (POH)");
			// all settings start collapsed
			assertFalse(FoldArrow.isOpen(bank));
			assertFalse(FoldArrow.isOpen(heading(s, "Transport types")));
			click(bank); // open it first
			assertTrue(FoldArrow.isOpen(bank));
			Container body = bank.getParent();
			int at = java.util.Arrays.asList(body.getComponents()).indexOf(bank);
			Component under = body.getComponent(at + 1);
			assertTrue(under.isVisible());
			click(bank);
			assertFalse(FoldArrow.isOpen(bank));
			assertFalse("the rows under it fold", under.isVisible());
			assertTrue("the next section stays", house.isVisible());
			assertTrue(bank.isVisible());
			click(bank);
			assertTrue(under.isVisible());

			// opening and folding the last plain section stops at the Advanced part (its own fold)
			JLabel sailing = heading(s, "Sailing");
			click(sailing);
			assertTrue(heading(s, SettingsTab.TICKS_TITLE).getParent().isVisible());
			assertEquals("it started folded: the click opens it", FoldArrow.of(true), sailing.getIcon());
		});
	}
}

package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.SlayerPreferred;
import com.bestpath.route.TransportSettings;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The Settings tab lists the preferred Slayer places. */
public class SettingsTabPreferredTest
{
	private final List<String> calls = new ArrayList<>();

	private SettingsTab tab()
	{
		return new SettingsTab(new PanelActions()
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

			@Override
			public void preferSlayerPlace(String task, String place, boolean on)
			{
				calls.add(task + " " + place + " " + on);
			}
		}, new IconCache(IconProvider.NONE));
	}

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

	private static List<String> texts(Component root)
	{
		List<String> out = new ArrayList<>();
		List<Component> cs = new ArrayList<>();
		all(root, cs);
		for (Component c : cs)
		{
			if (c instanceof WrapLabel)
			{
				out.add(((WrapLabel) c).getText());
			}
			else if (c instanceof JLabel)
			{
				out.add(((JLabel) c).getText());
			}
		}
		return out;
	}

	private static List<JButton> unmarkButtons(Component root)
	{
		List<JButton> out = new ArrayList<>();
		List<Component> cs = new ArrayList<>();
		all(root, cs);
		for (Component c : cs)
		{
			if (c instanceof JButton && "Unmark".equals(((JButton) c).getText()))
			{
				out.add((JButton) c);
			}
		}
		return out;
	}

	@Test
	public void eachPreferredPlaceIsListedWithUnmark() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsTab s = tab();
			assertTrue(texts(s).contains(SettingsTab.PREFERRED_HINT));
			assertTrue("an empty list says so", unmarkButtons(s).isEmpty());
			SlayerPreferred p = SlayerPreferred.NONE.with("Kalphites", "Kalphite Cave@5", true).with("Ankou", "Stronghold Slayer Cave@7", true);
			s.showSlayerPreferred(p);
			assertTrue(texts(s).contains("Ankou: Stronghold Slayer Cave"));
			assertTrue(texts(s).contains("Kalphites: Kalphite Cave"));
			List<JButton> unmark = unmarkButtons(s);
			assertEquals("by task", 2, unmark.size());
			unmark.get(1).doClick(); // Kalphites
			assertEquals(List.of("Kalphites Kalphite Cave@5 false"), calls);
			assertEquals("gone at once", SlayerPreferred.NONE.with("Ankou", "Stronghold Slayer Cave@7", true), s.preferred);
			assertEquals(1, unmarkButtons(s).size());
		});
	}
}

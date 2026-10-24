package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestpath.BestPathConfig;
import com.bestpath.overlay.PathStyle;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.TransportSettings;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import org.junit.Test;

/**
 * The config panel's settings in the Settings tab (the two mirror each other): a change there writes
 * the config value (only a new one), and {@link SettingsTab#configChanged} shows the config's values without writing.
 */
public class SettingsTabConfigTest
{
	/** RuneLite's config as the fake plugin keeps it. */
	private final Map<String, String> stored = new HashMap<>();
	/** "key=value" per write, in order. */
	private final List<String> written = new ArrayList<>();
	/** The colour the fake colour picker gives back. */
	private Color picked = Color.RED;
	private final List<String> pickerTitles = new ArrayList<>();

	private PanelActions actions()
	{
		return new PanelActions()
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
			public String config(String key)
			{
				return stored.get(key);
			}

			@Override
			public void setConfig(String key, String value)
			{
				written.add(key + "=" + value);
				stored.put(key, value);
			}

			@Override
			public void pickColour(Component owner, Color now, String title, java.util.function.Consumer<Color> chosen)
			{
				pickerTitles.add(title);
				chosen.accept(picked);
			}
		};
	}

	private SettingsTab tab()
	{
		return new SettingsTab(actions(), new IconCache(IconProvider.NONE));
	}

	private static void onSwing(Runnable r) throws Exception
	{
		SwingUtilities.invokeAndWait(r);
	}

	@Test
	public void changesWriteTheConfigValue() throws Exception
	{
		onSwing(() ->
		{
			ConfigMirror m = tab().mirror;
			assertTrue("building the tab writes nothing", written.isEmpty());

			JCheckBox sidebar = (JCheckBox) m.controls.get("showSidebar");
			assertTrue("nothing stored: the default", sidebar.isSelected());
			sidebar.doClick(0);
			assertEquals(List.of("showSidebar=false"), written);

			JSpinner reroute = (JSpinner) m.controls.get(BestPathConfig.REROUTE_KEY);
			assertEquals(10, reroute.getValue());
			reroute.setValue(12);
			reroute.setValue(12);
			assertEquals("rerouteDistance=12", written.get(1));
			assertEquals("the same value again: nothing written", 2, written.size());

			@SuppressWarnings("unchecked")
			JComboBox<Enum<?>> style = (JComboBox<Enum<?>>) m.controls.get("pathStyle");
			assertSame(PathStyle.TILES, style.getSelectedItem());
			style.setSelectedItem(PathStyle.LINE);
			assertEquals("pathStyle=LINE", written.get(2));

			ConfigMirror.Swatch glow = (ConfigMirror.Swatch) m.controls.get("glowColour");
			glow.doClick(0);
			assertEquals(List.of("Glow colour"), pickerTitles);
			assertEquals("glowColour=" + Color.RED.getRGB(), written.get(3));
			assertEquals(Color.RED, glow.getColour());
			glow.doClick(0);
			assertEquals("the same colour picked: nothing written", 4, written.size());

			JCheckBox dev = (JCheckBox) m.controls.get(BestPathConfig.DEVELOPER_KEY);
			dev.doClick(0);
			assertEquals("developerDetails=true", written.get(4));
		});
	}

	@Test
	public void configChangedShowsValuesWithoutWriting() throws Exception
	{
		onSwing(() ->
		{
			SettingsTab t = tab();
			ConfigMirror m = t.mirror;
			// changed in the config panel
			stored.put("minimalMode", "true");
			stored.put("textSize", "20");
			stored.put("pathStyle", "ARROWS");
			stored.put("routeColour", String.valueOf(Color.GREEN.getRGB()));
			t.configChanged();
			assertTrue(((JCheckBox) m.controls.get("minimalMode")).isSelected());
			assertEquals(20, ((JSpinner) m.controls.get("textSize")).getValue());
			assertSame(PathStyle.ARROWS, ((JComboBox<?>) m.controls.get("pathStyle")).getSelectedItem());
			assertEquals(Color.GREEN, ((ConfigMirror.Swatch) m.controls.get("routeColour")).getColour());
			assertTrue("shown, not written back", written.isEmpty());

			// a change from there on writes against the value shown
			((JSpinner) m.controls.get("textSize")).setValue(20);
			assertTrue(written.isEmpty());
			((JCheckBox) m.controls.get("minimalMode")).doClick(0);
			assertEquals(List.of("minimalMode=false"), written);
		});
	}

	@Test
	public void sectionsFoldAsTheConfigPanel() throws Exception
	{
		onSwing(() ->
		{
			ConfigMirror m = tab().mirror;
			// all settings start collapsed (the config panel's sections too)
			for (ConfigList.Section sec : ConfigList.SECTIONS)
			{
				assertFalse(sec.name, m.isOpen(sec));
			}
			m.toggle(ConfigList.ADVANCED);
			assertTrue(m.isOpen(ConfigList.ADVANCED));
			m.toggle(ConfigList.ROUTES);
			assertTrue(m.isOpen(ConfigList.ROUTES));
			m.toggle(ConfigList.ROUTES);
			assertFalse(m.isOpen(ConfigList.ROUTES));
			for (ConfigList.Item i : ConfigList.ITEMS)
			{
				assertNotNull(i.key, m.controls.get(i.key));
			}
		});
	}

	@Test
	public void theTabShowsThemFirstAndAdvancedLast() throws Exception
	{
		onSwing(() ->
		{
			List<String> names = new ArrayList<>();
			named(tab(), names);
			assertEquals("config:showSidebar", names.get(0));
			assertEquals("config:" + BestPathConfig.DEVELOPER_KEY, names.get(names.size() - 1));
			List<String> want = new ArrayList<>();
			for (ConfigList.Item i : ConfigList.ITEMS)
			{
				want.add("config:" + i.key);
			}
			names.removeIf(n -> !n.startsWith("config:"));
			assertEquals("the config panel's order", want, names);
		});
	}

	@Test
	public void thePanelPassesThemOn() throws Exception
	{
		onSwing(() ->
		{
			BestPathPanel panel = new BestPathPanel(actions(), IconProvider.NONE);
			PanelDriver.selectSettingsTab(panel);
			JCheckBox box = (JCheckBox) find(panel, "config:sceneMenu");
			assertNotNull(box);
			assertFalse(box.isSelected());
			stored.put("sceneMenu", "true");
			panel.configChanged();
			assertTrue(box.isSelected());
			assertTrue(written.isEmpty());
			box.doClick(0);
			assertEquals(List.of("sceneMenu=false"), written);
		});
	}

	/** The names of the named controls (tick boxes, number boxes, lists, colour squares) in the order shown. */
	private static void named(Component c, List<String> out)
	{
		if (c.getName() != null && (c instanceof JCheckBox || c instanceof JSpinner || c instanceof JComboBox
			|| c instanceof ConfigMirror.Swatch))
		{
			out.add(c.getName());
		}
		if (c instanceof Container && !(c instanceof JSpinner) && !(c instanceof JComboBox))
		{
			for (Component k : ((Container) c).getComponents())
			{
				named(k, out);
			}
		}
	}

	private static Component find(Component c, String name)
	{
		if (name.equals(c.getName()))
		{
			return c;
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				Component f = find(k, name);
				if (f != null)
				{
					return f;
				}
			}
		}
		return null;
	}
}

package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestpath.BestPathConfig;
import com.bestpath.overlay.PathStyle;
import com.bestpath.overlay.RouteColours;
import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.util.ColorUtil;
import org.junit.Test;

/**
 * The Settings tab's copy of the config panel ({@link ConfigList}) against BestPathConfig's own annotations (reflection:
 * tests only): every config item outside the transport sections is there, with the same name, description, section,
 * order, range and default. Fails when a config item is added without its line in ConfigList.
 */
public class ConfigListTest
{
	/** The config sections the Settings tab has natively (the account's transport settings). */
	private static final Set<String> SIDEBAR_OWN = Set.of(BestPathConfig.TYPES, BestPathConfig.AVOID, BestPathConfig.BANK,
		BestPathConfig.SAILING);

	/** The config panel's sections by key (BestPathConfig's String constants with @ConfigSection). */
	private static Map<String, ConfigSection> sections() throws IllegalAccessException
	{
		Map<String, ConfigSection> out = new LinkedHashMap<>();
		for (Field f : BestPathConfig.class.getDeclaredFields())
		{
			ConfigSection s = f.getAnnotation(ConfigSection.class);
			if (s != null)
			{
				out.put((String) f.get(null), s);
			}
		}
		return out;
	}

	/** Where an item stands in the config panel: top items and sections sorted together by position, then the item's. */
	private static int[] order(ConfigItem item, Map<String, ConfigSection> sections)
	{
		ConfigSection s = sections.get(item.section());
		return new int[]{s == null ? item.position() : s.position(), item.position()};
	}

	/** The config panel's items the sidebar mirrors, in config panel order. */
	/** The item with this key, or null. */
	private static ConfigList.Item byKey(String key)
	{
		for (ConfigList.Item i : ConfigList.ITEMS)
		{
			if (i.key.equals(key))
			{
				return i;
			}
		}
		return null;
	}

	private static List<Method> configItems() throws IllegalAccessException
	{
		Map<String, ConfigSection> sections = sections();
		List<Method> out = new ArrayList<>();
		for (Method m : BestPathConfig.class.getDeclaredMethods())
		{
			ConfigItem ci = m.getAnnotation(ConfigItem.class);
			if (ci != null && !ci.hidden() && !SIDEBAR_OWN.contains(ci.section()))
			{
				out.add(m);
			}
		}
		out.sort(Comparator.<Method>comparingInt(m -> order(m.getAnnotation(ConfigItem.class), sections)[0])
			.thenComparingInt(m -> order(m.getAnnotation(ConfigItem.class), sections)[1]));
		return out;
	}

	@Test
	public void everyConfigItemIsMirroredInOrder() throws Exception
	{
		List<String> want = new ArrayList<>();
		for (Method m : configItems())
		{
			want.add(m.getAnnotation(ConfigItem.class).keyName());
		}
		List<String> have = new ArrayList<>();
		for (ConfigList.Item i : ConfigList.ITEMS)
		{
			have.add(i.key);
		}
		assertEquals("a config item added or moved: add it to ConfigList (the Settings tab mirrors the config panel)", want,
			have);
	}

	@Test
	public void namesDescriptionsSectionsRangesAndDefaultsMatch() throws Exception
	{
		BestPathConfig defaults = new BestPathConfig()
		{
		};
		for (Method m : configItems())
		{
			ConfigItem ci = m.getAnnotation(ConfigItem.class);
			ConfigList.Item item = byKey(ci.keyName());
			assertNotNull(ci.keyName(), item);
			assertEquals(ci.keyName(), ci.name(), item.name);
			assertEquals(ci.keyName(), ci.description(), item.description);
			assertEquals(ci.keyName(), ci.section(), item.section == null ? "" : item.section.key);
			if (item.section != null)
			{
				assertTrue(ci.keyName(), ConfigList.SECTIONS.contains(item.section));
			}
			Class<?> type = m.getReturnType();
			ConfigList.Kind kind = type == boolean.class ? ConfigList.Kind.BOOLEAN : type == int.class ? ConfigList.Kind.INT
				: type == Color.class ? ConfigList.Kind.COLOUR : type.isEnum() ? ConfigList.Kind.ENUM : null;
			assertNotNull(ci.keyName() + ": a new kind of setting (" + type + "): give ConfigMirror a control for it", kind);
			assertEquals(ci.keyName(), kind, item.kind);
			if (kind == ConfigList.Kind.INT)
			{
				Range r = m.getAnnotation(Range.class);
				assertEquals(ci.keyName(), r == null ? 0 : r.min(), item.min);
				assertEquals(ci.keyName(), r == null ? Integer.MAX_VALUE : r.max(), item.max);
			}
			if (kind == ConfigList.Kind.ENUM)
			{
				assertEquals(ci.keyName(), Arrays.asList(type.getEnumConstants()), item.options);
			}
			assertEquals(ci.keyName() + " default", m.invoke(defaults), item.defaultValue);
		}
	}

	@Test
	public void sectionsMatch() throws Exception
	{
		Map<String, ConfigSection> sections = sections();
		List<String> want = new ArrayList<>();
		sections.entrySet().stream()
			.filter(e -> !SIDEBAR_OWN.contains(e.getKey()))
			.sorted(Comparator.comparingInt(e -> e.getValue().position()))
			.forEach(e -> want.add(e.getKey()));
		List<String> have = new ArrayList<>();
		for (ConfigList.Section s : ConfigList.SECTIONS)
		{
			have.add(s.key);
			ConfigSection a = sections.get(s.key);
			assertNotNull(s.key, a);
			assertEquals(s.key, a.name(), s.name);
			assertEquals(s.key, a.description(), s.description);
			assertEquals(s.key, a.closedByDefault(), s.closedByDefault);
		}
		assertEquals(want, have);
	}

	@Test
	public void valuesAreStoredAsRuneLiteStoresThem()
	{
		assertEquals("true", ConfigList.toConfig(true));
		assertEquals("false", ConfigList.toConfig(false));
		assertEquals("15", ConfigList.toConfig(15));
		assertEquals("ARROWS", ConfigList.toConfig(PathStyle.ARROWS));
		// ConfigManager.objectToString: String.valueOf(color.getRGB()), the ARGB as a signed number
		assertEquals("-16725761", ConfigList.toConfig(new Color(0, 200, 255)));
		assertEquals(String.valueOf(Color.WHITE.getRGB()), ConfigList.toConfig(Color.WHITE));
	}

	@Test
	public void valuesAreReadAsRuneLiteReadsThem()
	{
		ConfigList.Item sidebar = byKey("showSidebar");
		ConfigList.Item reroute = byKey(BestPathConfig.REROUTE_KEY);
		ConfigList.Item style = byKey("pathStyle");
		ConfigList.Item route = byKey("routeColour");
		assertNull(byKey("typeHouse")); // the sidebar's own transport settings: not mirrored

		// nothing stored: the default
		assertEquals(true, ConfigList.fromConfig(sidebar, null));
		assertEquals(10, ConfigList.fromConfig(reroute, null));
		assertSame(PathStyle.TILES, ConfigList.fromConfig(style, null));
		assertEquals(RouteColours.DEFAULT, ConfigList.fromConfig(route, null));

		assertEquals(false, ConfigList.fromConfig(sidebar, "false"));
		assertEquals("Boolean.parseBoolean, as RuneLite", false, ConfigList.fromConfig(sidebar, "yes"));
		assertEquals(15, ConfigList.fromConfig(reroute, "15"));
		assertEquals("kept in the config panel's range", 50, ConfigList.fromConfig(reroute, "99"));
		assertEquals(1, ConfigList.fromConfig(reroute, "0"));
		assertEquals("not a number: the default", 10, ConfigList.fromConfig(reroute, "ten"));
		assertSame(PathStyle.LINE, ConfigList.fromConfig(style, "LINE"));
		assertSame("unknown: the default", PathStyle.TILES, ConfigList.fromConfig(style, "Line"));

		// colours: ColorUtil.fromString (Integer.decode, alpha kept), both ways
		for (Color c : new Color[]{Color.RED, Color.WHITE, new Color(0, 200, 255), new Color(10, 20, 30, 40)})
		{
			String s = ConfigList.toConfig(c);
			assertEquals(c, ConfigList.fromConfig(route, s));
			assertEquals(ColorUtil.fromString(s), ConfigList.fromConfig(route, s));
		}
		assertEquals(ColorUtil.fromString("#FF0000"), ConfigList.fromConfig(route, "#FF0000"));
		assertEquals("not a colour: the default", RouteColours.DEFAULT, ConfigList.fromConfig(route, "blue"));
	}
}

package com.bestpath.route;

import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TickGroups;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which Settings tab category and "avoid" rules each exported transport
 * falls under. Only fields the transport data really has are used: the
 * data's category, the kind, the teleport category, the network, the
 * requirement line (with its network's) and the long-cooldown flag. Nothing is guessed:
 * a rule the data can't support ({@link TransportSettings.Avoid#KNOWN_BUGS})
 * is never applied and its checkbox is disabled.
 *
 * <p>Categories split the transports (each is in at most one; the first
 * that fits wins):
 * <ol>
 * <li>{@code HOUSE}: the "Player-owned house" network (rides out of the
 * house), and every transport that starts or lands on the house spot (house
 * portals, Teleport to House, house tablets, capes' house teleports, the
 * house's spirit tree / fairy ring stops);</li>
 * <li>{@code TELEPORT_SPELLS}: teleports in the teleport categories "spell",
 * "home teleport", "grouping teleport";</li>
 * <li>{@code TELEPORT_ITEMS}: teleports in the item categories (tablet,
 * scroll, jewellery, cape or diary item, other item; infinite and
 * consumable);</li>
 * <li>{@code BOATS}: category TRAVEL, or any network (boats, ships,
 * charter ships, fairy rings, spirit trees, gliders, minecarts, canoes,
 * balloons, quetzals, carpets, ...);</li>
 * <li>{@code STAIRS_LADDERS}: CLIMB_UP, CLIMB_DOWN, CLIMB_BOTH;</li>
 * <li>{@code SHORTCUTS}: SHORTCUT;</li>
 * <li>{@code DOORS}: PASS (gates, barriers and doors that need something;
 * ordinary doors are part of the walking map, not transports).</li>
 * </ol>
 * Everything else (ENTER, EXIT, CUSTOM, a teleport with no known category) is
 * in no category and always used.
 *
 * <p>Avoid rules: COST = the requirement line mentions coins or an item
 * "used up", or the teleport category is a "(consumable)" one; RUNES = the
 * requirement line needs runes ("rune air x1"); COOLDOWN = the long-cooldown
 * flag. Avoided transports are used only when there is no route without
 * them ({@link CoreRouteEngine}).
 *
 * <p>Immutable after construction; any thread.
 */
public final class TransportRules
{
	/** The network of rides out of the player's house. */
	public static final String HOUSE_NETWORK = "Player-owned house";

	private static final Set<String> SPELL_CATEGORIES = Set.of("spell", "home teleport", "grouping teleport");
	private static final Set<String> ITEM_CATEGORIES = Set.of("tablet", "scroll", "jewellery (infinite)",
		"jewellery (consumable)", "cape or diary item (infinite)", "cape or diary item (consumable)",
		"other item (infinite)", "other item (consumable)");
	/** A requirement part "coins 200" (at the start, or after ";", "(", "|" or "&"). */
	private static final Pattern COINS = Pattern.compile("(^|[;(|&])\\s*coins\\s+\\d");
	/** An item part with the "used up" option. */
	private static final Pattern USED_UP = Pattern.compile("\\bused up\\b");
	/** A requirement part "rune air x3". */
	private static final Pattern RUNE = Pattern.compile("(^|[;(|&])\\s*rune\\s+\\S");

	/** What each line is (worked out once). */
	private static final class Facts
	{
		final TransportSettings.Category category;
		final Set<TransportSettings.Avoid> avoid;

		Facts(TransportSettings.Category category, Set<TransportSettings.Avoid> avoid)
		{
			this.category = category;
			this.avoid = avoid;
		}
	}

	/** Keyed by the very line objects given to the constructor (identity, not equals). */
	private final Map<TransportLine, Facts> facts = new IdentityHashMap<>();
	/** Packed tiles where the house is (the start of the house network's rides). */
	private final Set<Integer> houseSpots = new HashSet<>();

	public TransportRules(List<TransportLine> lines, Tables tables)
	{
		for (TransportLine l : lines)
		{
			if (HOUSE_NETWORK.equalsIgnoreCase(l.getNetwork()) && l.hasStart())
			{
				houseSpots.add(Tiles.pack(l.getStartX(), l.getStartY(), l.getStartPlane()));
			}
		}
		for (TransportLine l : lines)
		{
			facts.put(l, new Facts(lineCategory(l), avoidRules(l, tables)));
		}
	}

	/** True if the transport data can support this rule (false: its checkbox is disabled). */
	public static boolean isSupported(TransportSettings.Avoid a)
	{
		return a != TransportSettings.Avoid.KNOWN_BUGS;
	}

	/** The Settings tab's tooltip: what the category covers in the transport data. */
	public static String describe(TransportSettings.Category c)
	{
		switch (c)
		{
			case TELEPORT_SPELLS:
				return "Teleport spells of every spellbook, home teleports and minigame (grouping) teleports";
			case TELEPORT_ITEMS:
				return "Teleport tablets, scrolls, jewellery, capes, diary items and other teleport items";
			case HOUSE:
				return "Everything that goes into or out of your house: house portals, Teleport to House, house "
					+ "tablets, and the rides out of the house (jewellery box, portals, nexus, obelisk, spirit tree, "
					+ "fairy ring)";
			case STAIRS_LADDERS:
				return "Stairs and ladders (up, down). Cave entrances, trapdoors and other ways in or out are always used";
			case SHORTCUTS:
				return "Shortcuts (agility shortcuts and other short cuts the data marks as shortcuts)";
			case DOORS:
				return "Gates, doors and barriers that need something (toll gates, guild doors, magic barriers). "
					+ "Ordinary doors are part of the walking map and are always opened";
			case BOATS:
			default:
				return "Boats, ships, charter ships, fairy rings, spirit trees, gliders, minecarts, canoes, balloons, "
					+ "quetzals, magic carpets and other rides";
		}
	}

	/** The Settings tab's tooltip: what the avoid rule covers in the transport data. */
	public static String describe(TransportSettings.Avoid a)
	{
		switch (a)
		{
			case COST:
				return "Transports that need coins, use up an item (tablets, scrolls...), or use charges (jewellery and "
					+ "other teleport items marked consumable)";
			case RUNES:
				return "Teleport spells that need runes";
			case COOLDOWN:
				return "Home teleports and minigame teleports (long cooldown)";
			case WILDERNESS:
				return "Routes stay out of the Wilderness: no walking in it and no transport into or out of it (Ferox Enclave"
					+ " is safe). Only when there is no other way, or you start or end there, does the route go in";
			case KNOWN_BUGS:
			default:
				return "Not available yet: the transport data has no known-bug mark";
		}
	}

	/**
	 * The Settings type a teleport category's teleports or a network's rides are in (the Settings tab shows that
	 * type's icon on the category's or network's tick row); null = in no type.
	 */
	public static TransportSettings.Category typeOf(TickGroups.Group g)
	{
		if (g.getKind() == TickGroups.Kind.NETWORK)
		{
			return HOUSE_NETWORK.equalsIgnoreCase(g.getName()) ? TransportSettings.Category.HOUSE : TransportSettings.Category.BOATS;
		}
		String tc = g.getName().trim().toLowerCase(Locale.ROOT);
		return SPELL_CATEGORIES.contains(tc) ? TransportSettings.Category.TELEPORT_SPELLS
			: ITEM_CATEGORIES.contains(tc) ? TransportSettings.Category.TELEPORT_ITEMS : null;
	}

	/** The transport's category (null = in no category: always used). */
	public TransportSettings.Category category(Transport t, TransportLine line)
	{
		if (t != null && (houseSpots.contains(Tiles.pack(t.getEndX(), t.getEndY(), t.getEndPlane()))
			|| t.hasStart() && houseSpots.contains(Tiles.pack(t.getStartX(), t.getStartY(), t.getStartPlane()))))
		{
			return TransportSettings.Category.HOUSE;
		}
		Facts f = line == null ? null : facts.get(line);
		return f == null ? null : f.category;
	}

	/** The avoid rules the transport falls under (empty if none). */
	public Set<TransportSettings.Avoid> avoidRules(TransportLine line)
	{
		Facts f = line == null ? null : facts.get(line);
		return f == null ? Set.of() : f.avoid;
	}

	/** True if the settings avoid this transport (only the supported rules count). */
	public boolean isAvoided(TransportSettings s, TransportLine line)
	{
		for (TransportSettings.Avoid a : avoidRules(line))
		{
			if (s.avoids(a) && isSupported(a))
			{
				return true;
			}
		}
		return false;
	}

	/** True if the settings avoid anything the data supports. */
	public static boolean avoidsAnything(TransportSettings s)
	{
		for (TransportSettings.Avoid a : TransportSettings.Avoid.values())
		{
			if (s.avoids(a) && isSupported(a))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The search's filter for these settings: switched-off categories are left out; with {@code withAvoid} the
	 * avoided transports too.
	 */
	public PluginRouter.TransportFilter filter(TransportSettings s, boolean withAvoid)
	{
		Set<TransportSettings.Category> off = EnumSet.noneOf(TransportSettings.Category.class);
		for (TransportSettings.Category c : TransportSettings.Category.values())
		{
			if (!s.isEnabled(c))
			{
				off.add(c);
			}
		}
		boolean avoid = withAvoid && avoidsAnything(s);
		if (off.isEmpty() && !avoid)
		{
			return PluginRouter.TransportFilter.NONE;
		}
		return (t, line) ->
		{
			TransportSettings.Category c = category(t, line);
			return (c != null && off.contains(c)) || (avoid && isAvoided(s, line));
		};
	}

	private TransportSettings.Category lineCategory(TransportLine l)
	{
		if (HOUSE_NETWORK.equalsIgnoreCase(l.getNetwork()))
		{
			return TransportSettings.Category.HOUSE;
		}
		if (l.getKind() == TransportLine.Kind.TELEPORT)
		{
			String tc = l.getTeleportCategory().trim().toLowerCase(Locale.ROOT);
			if (SPELL_CATEGORIES.contains(tc))
			{
				return TransportSettings.Category.TELEPORT_SPELLS;
			}
			if (ITEM_CATEGORIES.contains(tc))
			{
				return TransportSettings.Category.TELEPORT_ITEMS;
			}
			return null;
		}
		if ("TRAVEL".equals(l.getCategory()) || !l.getNetwork().isEmpty())
		{
			return TransportSettings.Category.BOATS;
		}
		switch (l.getCategory())
		{
			case "CLIMB_UP":
			case "CLIMB_DOWN":
			case "CLIMB_BOTH":
				return TransportSettings.Category.STAIRS_LADDERS;
			case "SHORTCUT":
				return TransportSettings.Category.SHORTCUTS;
			case "PASS":
				return TransportSettings.Category.DOORS;
			default:
				return null;
		}
	}

	private static Set<TransportSettings.Avoid> avoidRules(TransportLine l, Tables tables)
	{
		String req = (tables.networkRequirement(l.getNetwork(), l.getRequirements()) + " ; "
			+ tables.networkRequirement(l.getNetwork(), l.requirementsEndToStart())).toLowerCase(Locale.ROOT);
		Set<TransportSettings.Avoid> out = EnumSet.noneOf(TransportSettings.Avoid.class);
		String tc = l.getTeleportCategory().trim().toLowerCase(Locale.ROOT);
		if (COINS.matcher(req).find() || USED_UP.matcher(req).find() || tc.endsWith("(consumable)"))
		{
			out.add(TransportSettings.Avoid.COST);
		}
		if (RUNE.matcher(req).find())
		{
			out.add(TransportSettings.Avoid.RUNES);
		}
		if (l.isCooldown())
		{
			out.add(TransportSettings.Avoid.COOLDOWN);
		}
		return out.isEmpty() ? Set.of() : out;
	}
}

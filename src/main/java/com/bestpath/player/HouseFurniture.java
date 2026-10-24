package com.bestpath.player;

import com.bestpath.core.data.MenuText;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarbitID;

/**
 * Which built house furniture is which house thing ({@code house has <thing>} in the requirement lines), for the
 * house scan. Pure: the table is {@code house-objects.tsv} ({@link #RESOURCE}), generated from the game cache.
 *
 * <p>The portal nexus keeps one destination per slot in varbits {@code POH_NEXUS_TELE_1..45} (the game's script 2671
 * reads exactly these); a slot's value is a destination key of the table's {@code destination} rows (cache enum
 * 1377), 0 = empty.
 */
public final class HouseFurniture
{
	public static final String RESOURCE = "/com/bestpath/player/house-objects.tsv";
	/** The thing name the table gives a portal nexus (its destinations come from the slot varbits). */
	public static final String NEXUS = "portal nexus";
	/** The things a nexus destination becomes: "portal nexus: Varrock Teleport". */
	public static final String NEXUS_PREFIX = "portal nexus: ";

	/** The portal nexus slots, in the game's order (RuneLite gameval names). */
	public static final int[] NEXUS_SLOTS = {
		VarbitID.POH_NEXUS_TELE_1, VarbitID.POH_NEXUS_TELE_2, VarbitID.POH_NEXUS_TELE_3, VarbitID.POH_NEXUS_TELE_4,
		VarbitID.POH_NEXUS_TELE_5, VarbitID.POH_NEXUS_TELE_6, VarbitID.POH_NEXUS_TELE_7, VarbitID.POH_NEXUS_TELE_8,
		VarbitID.POH_NEXUS_TELE_9, VarbitID.POH_NEXUS_TELE_10, VarbitID.POH_NEXUS_TELE_11, VarbitID.POH_NEXUS_TELE_12,
		VarbitID.POH_NEXUS_TELE_13, VarbitID.POH_NEXUS_TELE_14, VarbitID.POH_NEXUS_TELE_15, VarbitID.POH_NEXUS_TELE_16,
		VarbitID.POH_NEXUS_TELE_17, VarbitID.POH_NEXUS_TELE_18, VarbitID.POH_NEXUS_TELE_19, VarbitID.POH_NEXUS_TELE_20,
		VarbitID.POH_NEXUS_TELE_21, VarbitID.POH_NEXUS_TELE_22, VarbitID.POH_NEXUS_TELE_23, VarbitID.POH_NEXUS_TELE_24,
		VarbitID.POH_NEXUS_TELE_25, VarbitID.POH_NEXUS_TELE_26, VarbitID.POH_NEXUS_TELE_27, VarbitID.POH_NEXUS_TELE_28,
		VarbitID.POH_NEXUS_TELE_29, VarbitID.POH_NEXUS_TELE_30, VarbitID.POH_NEXUS_TELE_31, VarbitID.POH_NEXUS_TELE_32,
		VarbitID.POH_NEXUS_TELE_33, VarbitID.POH_NEXUS_TELE_34, VarbitID.POH_NEXUS_TELE_35, VarbitID.POH_NEXUS_TELE_36,
		VarbitID.POH_NEXUS_TELE_37, VarbitID.POH_NEXUS_TELE_38, VarbitID.POH_NEXUS_TELE_39, VarbitID.POH_NEXUS_TELE_40,
		VarbitID.POH_NEXUS_TELE_41, VarbitID.POH_NEXUS_TELE_42, VarbitID.POH_NEXUS_TELE_43, VarbitID.POH_NEXUS_TELE_44,
		VarbitID.POH_NEXUS_TELE_45,
	};

	/**
	 * The map regions holding the house's room templates (the only regions in the game cache with thousands of
	 * "Build" spaces, x 29-32, y 110-111). Every player-owned house is an instance copied from these.
	 */
	public static final Set<Integer> HOUSE_TEMPLATE_REGIONS = Set.of(7534, 7535, 7790, 7791, 8046, 8047, 8302, 8303);

	/**
	 * The house in the transport data: one spot, 1857,7065,0, a tile no one walks on in the
	 * house-room template area. Teleport to House, the house tablet, the capes and the house portals land there; the
	 * jewellery box, portals, nexus, mounted items, obelisk, spirit tree and fairy ring leave from there. A player
	 * inside a house is routed from this spot ({@link #routeTile}).
	 */
	public static final int HOUSE_SPOT_X = 1857;
	public static final int HOUSE_SPOT_Y = 7065;
	public static final int HOUSE_SPOT_PLANE = 0;

	/**
	 * True for a tile of the house room templates: where RuneLite's {@code WorldPoint.fromLocalInstance} puts a
	 * player standing inside any player-owned house (an instance copied from those regions).
	 */
	public static boolean isHouseTemplateTile(int x, int y)
	{
		return x >= 0 && y >= 0 && HOUSE_TEMPLATE_REGIONS.contains((x >> 6) << 8 | (y >> 6));
	}

	/**
	 * The tile routes start from for a player on {x, y, plane} (the instance's template tile): inside a house, the
	 * house spot, because the house room tiles are not on the walking map (without it a player in the house would
	 * have no route, and the step after a teleport into the house would never be reached); anywhere else the tile
	 * itself.
	 */
	public static int[] routeTile(int x, int y, int plane)
	{
		return isHouseTemplateTile(x, y) ? new int[]{HOUSE_SPOT_X, HOUSE_SPOT_Y, HOUSE_SPOT_PLANE} : new int[]{x, y, plane};
	}

	/** Furniture object id -> thing name (tests read it). */
	final Map<Integer, String> things;
	private final Map<Integer, String> destinations;

	/** The nexus's Teleport Menu: slot value -> its line (text, the alternate's slot value). */
	private final Map<Integer, NexusLine> nexus;
	/** Alternate lines (Grand Exchange): slot value -> line (text, Magic level, unlock). */
	private final Map<Integer, NexusLine> alternates;
	/** Slots the Teleport Menu lists, by POH_NEXUS_ID ("other" = any other value). */
	private final Map<String, Integer> listed;
	/** The portal chamber's portal by the nexus text of its place: {option, portal name}. */
	private final Map<String, String[]> chamber;

	/** One line of the nexus's Teleport Menu as the house table describes it. */
	static final class NexusLine
	{
		final String text;
		/** A main line: its alternate's slot value, or -1. */
		final int alternate;
		/** An alternate: the base Magic level it needs (1 for a main line). */
		final int magic;
		/** An alternate: any one unlocks it, {0 varbit / 1 varp, id, value} (varbit = value, varp >= value). */
		final List<int[]> unlock;

		NexusLine(String text, int alternate, int magic, List<int[]> unlock)
		{
			this.text = text;
			this.alternate = alternate;
			this.magic = magic;
			this.unlock = unlock;
		}
	}

	public HouseFurniture(Map<Integer, String> things, Map<Integer, String> destinations)
	{
		this(things, destinations, Map.of(), Map.of(), Map.of(), Map.of());
	}

	HouseFurniture(Map<Integer, String> things, Map<Integer, String> destinations, Map<Integer, NexusLine> nexus,
		Map<Integer, NexusLine> alternates, Map<String, Integer> listed, Map<String, String[]> chamber)
	{
		this.things = Collections.unmodifiableMap(new HashMap<>(things));
		this.destinations = Collections.unmodifiableMap(new HashMap<>(destinations));
		this.nexus = Collections.unmodifiableMap(new HashMap<>(nexus));
		this.alternates = Collections.unmodifiableMap(new HashMap<>(alternates));
		this.listed = Collections.unmodifiableMap(new HashMap<>(listed));
		this.chamber = Collections.unmodifiableMap(new HashMap<>(chamber));
	}

	/** The table shipped with the plugin (empty if it is missing, so nothing is ever recognised). */
	public static HouseFurniture load()
	{
		try (InputStream in = HouseFurniture.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				return new HouseFurniture(Map.of(), Map.of());
			}
			return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
		}
		catch (IOException e)
		{
			return new HouseFurniture(Map.of(), Map.of());
		}
	}

	static HouseFurniture parse(BufferedReader r) throws IOException
	{
		Map<Integer, String> things = new HashMap<>();
		Map<Integer, String> destinations = new HashMap<>();
		Map<Integer, NexusLine> nexus = new HashMap<>();
		Map<Integer, NexusLine> alternates = new HashMap<>();
		Map<String, Integer> listed = new HashMap<>();
		Map<String, String[]> chamber = new HashMap<>();
		String line;
		while ((line = r.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] c = line.split("\t");
			if (c.length < 3)
			{
				continue;
			}
			try
			{
				switch (c[0])
				{
					case "object":
						things.put(Integer.parseInt(c[1].trim()), c[2].trim());
						break;
					case "destination":
						destinations.put(Integer.parseInt(c[1].trim()), c[2].trim());
						break;
					case "nexus":
						nexus.put(Integer.parseInt(c[1].trim()),
							new NexusLine(c[2].trim(), c.length > 3 ? Integer.parseInt(c[3].trim()) : -1, 1, List.of()));
						break;
					case "nexus-alternate":
						if (c.length > 4)
						{
							alternates.put(Integer.parseInt(c[1].trim()),
								new NexusLine(c[2].trim(), -1, Integer.parseInt(c[3].trim()), unlock(c[4])));
						}
						break;
					case "nexus-slots":
						listed.put(c[1].trim(), Integer.parseInt(c[2].trim()));
						break;
					case "chamber":
						if (c.length > 3)
						{
							chamber.put(c[1].trim(), new String[]{c[2].trim(), c[3].trim()});
						}
						break;
					default:
						break;
				}
			}
			catch (IllegalArgumentException e)
			{
				// skip a broken line
			}
		}
		return new HouseFurniture(things, destinations, nexus, alternates, listed, chamber);
	}

	/** "varbit 4520 = 1 | varp 4959 >= 50": any one of them. */
	static List<int[]> unlock(String text)
	{
		List<int[]> out = new ArrayList<>();
		for (String part : text.split("\\|"))
		{
			String[] w = part.trim().split("\\s+");
			if (w.length != 4 || !(w[0].equals("varbit") && w[2].equals("=") || w[0].equals("varp") && w[2].equals(">=")))
			{
				throw new IllegalArgumentException("unlock '" + text + "'");
			}
			out.add(new int[]{w[0].equals("varbit") ? 0 : 1, Integer.parseInt(w[1]), Integer.parseInt(w[3])});
		}
		return out;
	}

	/**
	 * The keyboard key of each line of the player's portal nexus Teleport Menu (shown as "Nexus: Senntisten (M)"), by
	 * the line's text ("Senntisten" -> "M"), keyed as the game's scripts 2674 / 2675 do: slot by slot (as
	 * many slots as the built nexus lists), a line per destination keyed 1-9 then A-Z, and an alternate's line (Grand
	 * Exchange) right under its main one when the player has its base Magic level and one of its unlocks (script
	 * 2664); an empty slot has no line. Lines after a slot value this table does not know get no key (their place is
	 * not known), nor lines after the 35th. Empty when the table has no nexus rows.
	 *
	 * @param slots   the nexus slot values in slot order ({@link #NEXUS_SLOTS})
	 * @param nexusId POH_NEXUS_ID (which nexus is built)
	 * @param magic   the base (real) Magic level
	 * @param varbit  a varbit's value
	 * @param varp    a varp's value
	 */
	public Map<String, String> nexusKeys(int[] slots, int nexusId, int magic, IntUnaryOperator varbit, IntUnaryOperator varp)
	{
		Map<String, String> out = new LinkedHashMap<>();
		Integer count = listed.containsKey(String.valueOf(nexusId)) ? listed.get(String.valueOf(nexusId)) : listed.get("other");
		if (count == null || slots == null)
		{
			return out;
		}
		int n = 1;
		for (int i = 0; i < Math.min(count, slots.length); i++)
		{
			if (slots[i] <= 0)
			{
				continue;
			}
			NexusLine main = nexus.get(slots[i]);
			if (main == null)
			{
				break; // a destination this table does not know: where the lines after it stand is not known
			}
			n = keyed(out, main.text, n);
			if (main.alternate >= 0)
			{
				NexusLine alt = alternates.get(main.alternate);
				if (alt == null)
				{
					break;
				}
				if (magic >= alt.magic && unlocked(alt.unlock, varbit, varp))
				{
					n = keyed(out, alt.text, n);
				}
			}
		}
		return out;
	}

	/**
	 * True when the table knows how many slots this POH_NEXUS_ID's list shows (false: {@link #nexusKeys} took the
	 * game script's default, the fewest slots).
	 */
	public boolean knowsNexusId(int nexusId)
	{
		return listed.containsKey(String.valueOf(nexusId));
	}

	/**
	 * True when a scan's keys are kept out in favour of the saved ones: the scan read a POH_NEXUS_ID the table
	 * doesn't know, so it listed only the default slots, and every line it found is saved with the same key, the
	 * saved list having more. Without this a large nexus could be saved as only its first few slots on some house
	 * visits, so the later lines' keys would come and go. A scan with a known POH_NEXUS_ID is always taken.
	 */
	public static boolean keepSaved(Map<String, String> scanned, boolean knownNexus, Map<String, String> saved)
	{
		if (knownNexus || saved.size() <= scanned.size())
		{
			return false;
		}
		for (Map.Entry<String, String> e : scanned.entrySet())
		{
			if (!e.getValue().equals(saved.get(e.getKey())))
			{
				return false;
			}
		}
		return true;
	}

	private static int keyed(Map<String, String> out, String text, int n)
	{
		// script 2675: "1".."9", then enum 1401 (A-Z) for 10..35, no key after that
		String key = n < 10 ? String.valueOf(n) : n < 36 ? String.valueOf((char) ('A' + n - 10)) : null;
		if (key != null)
		{
			out.putIfAbsent(text, key);
		}
		return n + 1;
	}

	private static boolean unlocked(List<int[]> tests, IntUnaryOperator varbit, IntUnaryOperator varp)
	{
		for (int[] t : tests)
		{
			if (t[0] == 0 ? varbit.applyAsInt(t[1]) == t[2] : varp.applyAsInt(t[1]) >= t[2])
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * What the player clicks to use the portal chamber's portal to the place the nexus's list calls {@code nexusText}
	 * ("Senntisten" -> "Enter Senntisten Portal"; a portal that toggles has its two places as options: "Varrock
	 * Portal", then "Grand Exchange"), or null when the table has none.
	 */
	public MenuText chamberMenu(String nexusText)
	{
		String[] c = nexusText == null ? null : chamber.get(nexusText);
		if (c == null)
		{
			return null;
		}
		return c[0].equals(nexusText) ? MenuText.named(c[0], c[1]) : MenuText.verb(c[0], c[1]);
	}

	/** The house thing this object id is, or null (not furniture the requirement lines name). */
	public String thing(int objectId)
	{
		return things.get(objectId);
	}

	/** True for any object id of the table (worth tracking while in a house). */
	public boolean isWatched(int objectId)
	{
		return things.containsKey(objectId);
	}

	/**
	 * What is built, from the furniture ids seen in the house and the nexus slot values (read only matters when a
	 * nexus is among the furniture). Things only, sorted; a nexus adds one {@code "portal nexus: <destination>"} per
	 * known destination in its slots (the bare nexus is no requirement name).
	 *
	 * @param slotValues the values of {@link #NEXUS_SLOTS} (may be null when no nexus is built)
	 */
	public Set<String> thingsOf(Collection<Integer> objectIds, int[] slotValues)
	{
		Set<String> out = new TreeSet<>();
		boolean nexus = false;
		for (int id : objectIds)
		{
			String t = things.get(id);
			if (t == null)
			{
				continue;
			}
			if (t.equals(NEXUS))
			{
				nexus = true;
			}
			else
			{
				out.add(t);
			}
		}
		if (nexus && slotValues != null)
		{
			for (int v : slotValues)
			{
				String d = v > 0 ? destinations.get(v) : null;
				if (d != null)
				{
					out.add(NEXUS_PREFIX + d);
				}
			}
		}
		return out;
	}

	/**
	 * True when an instance template chunk (RuneLite {@code WorldView#getInstanceTemplateChunks()} value) is copied
	 * from a house room template; -1 (no chunk) is not.
	 */
	public static boolean isHouseChunk(int chunkData)
	{
		if (chunkData == -1)
		{
			return false;
		}
		int x = (chunkData >> 14 & 0x3FF) * 8;
		int y = (chunkData >> 3 & 0x7FF) * 8;
		return HOUSE_TEMPLATE_REGIONS.contains((x >> 6) << 8 | (y >> 6));
	}
}

package com.bestpath.tool.menu;

import static com.bestpath.tool.menu.DestinationMatch.afterColon;
import static com.bestpath.tool.menu.DestinationMatch.best;
import static com.bestpath.tool.menu.DestinationMatch.score;
import static com.bestpath.tool.menu.MenuMatcher.unknown;

import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.MenuTextBuilder;
import com.bestpath.tool.edit.TransportRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The menu texts of rides out of the player's house ({@link MenuMatcher}): the jewellery box and the portal nexus by
 * their lists, mounted jewellery by its options.
 */
final class HouseMenus
{
	/**
	 * House furniture the data names by the record's name, by its name in the cache (furniture is never placed on the
	 * map): a mounted amulet of glory, digsite pendant or Xeric's talisman (each has an option per destination).
	 */
	static final Map<String, String> HOUSE_OBJECTS = Map.of(
		"house mounted glory", "Amulet of Glory",
		"house mounted digsite pendant", "Digsite Pendant",
		"house mounted xeric's talisman", "Xeric's Talisman");

	/** House options that are not destinations. */
	private static final Set<String> HOUSE_NON_PLACES = Set.of("remove", "configuration", "teleport menu", "upgrade", "admire",
		"take", "build");

	/** The house's jewellery boxes are named "Basic / Fancy / Ornate Jewellery Box"; the card says the common part. */
	static final String JEWELLERY_BOX = "Jewellery Box";
	static final String NEXUS = "Portal Nexus";
	/** The option of the jewellery box and the portal nexus that opens their list. */
	static final String TELEPORT_MENU = "Teleport Menu";

	/**
	 * Data names of jewellery box rides that the list words differently, so that no word of one is in the other
	 * (checked against the list of script 1685): the skills necklace's "Cooking Guild".
	 */
	static final Map<String, String> JEWELLERY_BOX_ALIASES = Map.of("Cooks' Guild", "Cooking Guild");

	private final MenuFacts facts;

	HouseMenus(MenuFacts facts)
	{
		this.facts = facts;
	}

	/**
	 * A ride out of the player's house: the jewellery box and the portal nexus by their lists (the list's own words,
	 * "Burthorpe", with the key); mounted jewellery by its options; other furniture is not known.
	 */
	MenuMatcher.Result house(TransportRecord r, List<String> doubts)
	{
		int colon = r.name.indexOf(": ");
		String kind = colon < 0 ? r.name : r.name.substring(0, colon);
		String dest = afterColon(r.name);
		String lower = kind.toLowerCase(Locale.ROOT);
		if (lower.equals("house jewellery box") && dest != null)
		{
			return jewelleryBox(r, dest, doubts);
		}
		if (lower.startsWith("house portal") && dest != null)
		{
			return nexus(dest, doubts);
		}
		String object = HOUSE_OBJECTS.get(lower);
		if (object == null || dest == null)
		{
			return unknown("house furniture (" + kind + "): which one the player built is not known (several kinds, or not in this"
				+ " table)", doubts);
		}
		List<String> options = new ArrayList<>();
		for (int id : facts.objectsByName.getOrDefault(object, List.of()))
		{
			MenuFacts.Thing t = facts.object(id);
			for (String op : t == null ? List.<String>of() : t.ops())
			{
				if (!HOUSE_NON_PLACES.contains(op.toLowerCase(Locale.ROOT)) && !options.contains(op))
				{
					options.add(op);
				}
			}
		}
		String op = best(dest, options, doubts);
		if (op == null)
		{
			return unknown("no option of the house's '" + object + "' names '" + dest + "' (" + String.join(", ", options) + ")", doubts);
		}
		return new MenuMatcher.Result(MenuText.named(op, object), null, null, null, doubts, "house object '" + object + "' options");
	}

	/**
	 * A jewellery box ride: "Teleport Menu" on the box, then the list entry naming the destination, with its key
	 * ("Jewellery Box -> Burthorpe (5)"). Only the entries of the box the ride needs (its requirement's lowest box:
	 * "Edgeville Monastery" is the fancy box's "Monastery", not the ornate box's "Edgeville").
	 */
	private MenuMatcher.Result jewelleryBox(TransportRecord r, String dest, List<String> doubts)
	{
		String req = r.requirements.toLowerCase(Locale.ROOT);
		int tier = req.contains("basic jewellery box") ? 1 : req.contains("fancy jewellery box") ? 2 : req.contains("ornate jewellery box") ? 3 : 0;
		if (tier == 0 || facts.jewelleryBox.isEmpty())
		{
			return unknown("jewellery box ride: " + (tier == 0 ? "no box in its requirement" : "the box's list is not in the cache facts"), doubts);
		}
		String why = boxClick(JEWELLERY_BOX);
		if (why != null)
		{
			return unknown(why, doubts);
		}
		List<String> names = new ArrayList<>();
		for (MenuFacts.ListEntry e : facts.jewelleryBox)
		{
			if (e.tier == tier)
			{
				names.add(e.name);
			}
		}
		String plain = dest.replaceAll("\\s*\\([^)]*\\)$", "");
		String name = best(JEWELLERY_BOX_ALIASES.getOrDefault(plain, plain), names, doubts);
		if (name == null)
		{
			return unknown("no entry of the jewellery box's list names '" + dest + "' (" + String.join(", ", names) + ")", doubts);
		}
		for (MenuFacts.ListEntry e : facts.jewelleryBox)
		{
			if (e.tier == tier && e.name.equals(name))
			{
				return new MenuMatcher.Result(MenuTextBuilder.choice(TELEPORT_MENU, JEWELLERY_BOX, name, e.key), null, null, null, doubts,
					"jewellery box list (script 1685), key " + e.key);
			}
		}
		return unknown("jewellery box entry '" + name + "' lost", doubts);
	}

	/**
	 * A house portal ride as the portal nexus's list says it ("Portal Nexus -> Senntisten"; no key: it follows the
	 * player's own list, the plugin adds it). The plugin says the portal chamber's portal instead when that is what
	 * the player's house has. The data's name is "&lt;destination&gt;[: &lt;where&gt;]"; an alternate (Grand
	 * Exchange) when the part after the destination names it.
	 */
	private MenuMatcher.Result nexus(String dest, List<String> doubts)
	{
		String why = boxClick(NEXUS);
		if (why != null)
		{
			return unknown(why, doubts);
		}
		String d = null;
		for (String k : facts.nexus.keySet())
		{
			if ((dest.equals(k) || dest.startsWith(k + ": ")) && (d == null || k.length() > d.length()))
			{
				d = k;
			}
		}
		if (d == null)
		{
			return unknown("'" + dest + "' is no destination of the portal nexus's list in the cache", doubts);
		}
		List<String> texts = facts.nexus.get(d);
		String where = dest.length() > d.length() ? dest.substring(d.length() + 2) : null;
		String text = texts.size() > 1 && where != null && score(where, texts.get(1)) > 0 ? texts.get(1) : texts.get(0);
		return new MenuMatcher.Result(MenuTextBuilder.choice(TELEPORT_MENU, NEXUS, text), null, null, null, doubts, "portal nexus list (enum 1377)");
	}

	/** Null when every house object whose name ends with {@code name} has "Teleport Menu" (and there is one); else why not. */
	private String boxClick(String name)
	{
		int found = 0;
		for (Map.Entry<String, List<Integer>> e : facts.objectsByName.entrySet())
		{
			if (!e.getKey().endsWith(name))
			{
				continue;
			}
			for (int id : e.getValue())
			{
				MenuFacts.Thing t = facts.object(id);
				if (t != null && t.ops().contains("Remove"))
				{
					found++;
					if (!t.ops().contains(TELEPORT_MENU))
					{
						return "the house's '" + e.getKey() + "' (object " + id + ") has no '" + TELEPORT_MENU + "' option";
					}
				}
			}
		}
		return found == 0 ? "no built '" + name + "' in the cache" : null;
	}
}

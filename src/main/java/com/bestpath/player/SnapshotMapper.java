package com.bestpath.player;

import com.bestpath.core.data.BoatTable;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Skill;
import com.bestpath.core.req.Spellbook;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.route.PlayerSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Turns the raw game values ({@link GameReading}) into core's immutable
 * player model ({@link PlayerState}) inside a {@link PlayerSnapshot}. Pure
 * (no client).
 */
public final class SnapshotMapper
{
	/** Rune pouch slots (varbits RUNE_POUCH_TYPE_1..6 / RUNE_POUCH_QUANTITY_1..6; the divine pouch has 4). */
	public static final int POUCH_SLOTS = 6;

	/**
	 * The rune pouches whose runes count as carried while one is in the inventory (gameval ItemID; the same four
	 * RuneLite's own rune pouch overlay checks).
	 */
	public static final Set<Integer> RUNE_POUCHES = Set.of(ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER,
		ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER);

	/**
	 * Varbit POH_HOUSE_LOCATION (2187) values to house places, named as the requirement data names them. From the
	 * game cache: enum 252 has keys 1, 2, 3, 4, 5, 6, 8, 9, 13 with the names Rimmington, Taverley, Pollnivneach,
	 * Rellekka, Brimhaven, Yanille, Hosidius, Prifddinas, Aldarin; the game's script 3809 reads the varbit and says
	 * "You must own a house" when it is 0. Any other value above 0 is a house at a place this table doesn't know (see
	 * {@link #housePlace(int, String)}).
	 */
	private static final String[] HOUSE_PLACES = new String[14];

	static
	{
		HOUSE_PLACES[1] = "Rimmington";
		HOUSE_PLACES[2] = "Taverley";
		HOUSE_PLACES[3] = "Pollnivneach";
		HOUSE_PLACES[4] = "Rellekka";
		HOUSE_PLACES[5] = "Brimhaven";
		HOUSE_PLACES[6] = "Yanille";
		HOUSE_PLACES[8] = "Hosidius";
		HOUSE_PLACES[9] = "Prifddinas";
		HOUSE_PLACES[13] = "Aldarin";
	}

	private SnapshotMapper()
	{
	}

	public static PlayerSnapshot toSnapshot(GameReading r)
	{
		PlayerState.Builder b = new PlayerState.Builder("Player");
		for (Skill s : Skill.values())
		{
			b.level(s, r.levels[s.ordinal()]);
		}
		b.members(r.members);
		boolean pouchCarried = false;
		for (Map.Entry<Integer, Integer> e : r.inventory.entrySet())
		{
			if (e.getKey() > 0 && e.getValue() > 0)
			{
				b.carry(e.getKey(), e.getValue());
				pouchCarried |= RUNE_POUCHES.contains(e.getKey());
			}
		}
		for (EquipSlot slot : EquipSlot.values())
		{
			int id = r.worn[slot.ordinal()];
			if (id > 0)
			{
				b.wear(slot, id);
			}
		}
		if (r.bank != null)
		{
			for (Map.Entry<Integer, Integer> e : r.bank.entrySet())
			{
				if (e.getKey() > 0 && e.getValue() > 0)
				{
					b.bank(e.getKey(), e.getValue());
				}
			}
		}
		r.varbits.forEach(b::varbit);
		r.varps.forEach(b::varp);
		r.quests.forEach(b::quest);
		b.spellbook(spellbook(r.spellbookValue));
		b.house(housePlace(r.houseValue, r.houseName));
		r.houseThings.forEach(b::houseThing); // the house scan (core counts them only while the player has a house)
		for (int i = 0; i < POUCH_SLOTS; i++)
		{
			if (r.pouchItems[i] > 0 && r.pouchCounts[i] > 0)
			{
				b.pouch(r.pouchItems[i], r.pouchCounts[i]);
			}
		}
		// the pouch's contents are known (varbits) wherever it is; not carried = treat as in the bank
		b.pouchInBank(!pouchCarried);
		// a question not answered yet does not stop a route (the step card asks it)
		r.answers.forEach(b::answer);
		b.askUnanswered(true);
		// detected, never asked (UnlockWatch): collection log items and grown spirit trees seen
		r.collectionLog.forEach(b::collectionLog);
		r.spiritTrees.forEach(b::spiritTree);
		b.canoeAxeStored(r.canoeAxeStored); // detected, never asked
		// home and minigame teleports: cooling down until their minutes after the last use have passed (the game's
		// own test, see Cooldown), "now" as CooldownWatch decides it
		long now = CooldownWatch.nowMinute(r.gameMinute, r.clockMillis);
		for (Cooldown c : Cooldown.values())
		{
			int last = r.cooldownLastUse[c.ordinal()];
			if (c.coolingDown(last, now))
			{
				b.coolingDown(c, c.readyAt(last));
			}
		}
		return new PlayerSnapshot(r.x, r.y, r.plane, b.build(), r.boats).withNexusKeys(r.nexusKeys);
	}

	/**
	 * The player's boats from the raw boat varbits ({@link BoatVars}) and what their values mean (the map data's
	 * {@link BoatTable}, from the game cache). A boat routes can use needs a known kind, parts whose values the table
	 * matches to a tier, and a dock, the player standing on it, or a capsized, lost or bottled status
	 * ({@link Boat#CAPSIZED}, {@link Boat#LOST}, {@link Boat#BOTTLED}: core decides when such a boat can be used).
	 * Anything else is listed in the Settings tab but not used (never guessed): an unknown place, an unknown kind or
	 * part.
	 *
	 * <p>The boat the player stands on: only the player's own (RuneLite's world entity owner), matched to a slot by
	 * kind and name ({@code SAILING_BOARDED_BOAT_TYPE} / {@code _NAME_1..3}). It becomes {@link Boat#AT_START}, so a
	 * route asked for at sea starts from the boat. When it is someone else's boat, or no single slot matches, routes
	 * get no boats while the player is on it (they can't tell which boat they would sail).
	 */
	public static PlayerBoats boats(BoatReading r, BoatTable table)
	{
		if (table == null || table.isEmpty())
		{
			return PlayerBoats.NO_DATA;
		}
		int aboardSlot = -1;
		String aboardNote = null;
		if (r.aboard)
		{
			if (!r.ownBoat)
			{
				aboardNote = "You are on a boat that isn't yours: routes from here don't sail.";
			}
			else
			{
				for (int s = 1; s <= BoatVars.SLOTS; s++)
				{
					int[] v = r.slots[s - 1];
					if (v[BoatVars.OWNED] == 1 && v[BoatVars.TYPE] == r.boardedType && v[BoatVars.NAME_1] == r.boardedName[0]
						&& v[BoatVars.NAME_2] == r.boardedName[1] && v[BoatVars.NAME_3] == r.boardedName[2])
					{
						aboardSlot = aboardSlot == -1 ? s : -2; // -2: more than one boat matches
					}
				}
				if (aboardSlot < 0)
				{
					aboardNote = aboardSlot == -2 ? "Two of your boats have this boat's kind and name: routes from here don't sail."
						: "This boat doesn't match any of your boats: routes from here don't sail.";
					aboardSlot = -1;
				}
			}
		}
		List<Boat> boats = new ArrayList<>();
		List<BoatInfo> rows = new ArrayList<>();
		for (int s = 1; s <= BoatVars.SLOTS; s++)
		{
			int[] v = r.slots[s - 1];
			if (v[BoatVars.OWNED] != 1)
			{
				continue;
			}
			BoatTable.Kind kind = table.kind(v[BoatVars.TYPE]);
			String name = table.boatName(v[BoatVars.NAME_1], v[BoatVars.NAME_2], v[BoatVars.NAME_3]);
			String kindName = kind == null ? "Boat" : kind.getName();
			String title = kind != null && kind.getType() == null || name.isEmpty() ? kindName : kindName + ": " + name;
			boolean onIt = s == aboardSlot;
			int port = v[BoatVars.PORT];
			String status = table.portStatus(port);
			String dock = table.dockName(port);
			String where = onIt ? "At sea, you are on it"
				: status != null ? status : dock != null ? "At " + dock : "At an unknown place (" + port + ")";
			BoatType type = kind == null ? null : kind.getType();
			String why = null;
			if (kind == null)
			{
				why = "An unknown kind of boat (" + v[BoatVars.TYPE] + "): routes don't use it.";
			}
			else if (type == null)
			{
				why = "A temporary boat: routes don't use it.";
			}
			int hull = kind == null ? -1 : kind.hull(v[BoatVars.HULL]);
			int sail = kind == null ? -1 : kind.sail(v[BoatVars.SAIL]);
			int helm = kind == null ? -1 : kind.helm(v[BoatVars.STEERING]);
			int keel = kind == null ? -1 : kind.hasKeels() ? kind.keel(v[BoatVars.KEEL]) : 0;
			if (why == null && (hull < 0 || sail < 0 || helm < 0 || keel < 0))
			{
				why = "Some of its parts are new to this plugin version: routes don't use it.";
			}
			int place = onIt ? Boat.AT_START : port;
			if (why == null && !onIt)
			{
				if (status != null)
				{
					// capsized / lost: through a shipwright; bottled: with its bottle in the inventory
					place = statusPlace(status);
					if (place == Boat.NOWHERE)
					{
						why = status + ": routes don't use it until it is back at a dock.";
					}
				}
				else if (dock == null)
				{
					why = "Not at a dock the plugin knows: routes don't use it.";
				}
			}
			if (why != null)
			{
				rows.add(new BoatInfo(s, type, title, where, false, why));
				continue;
			}
			boolean inoculation = false;
			for (int k = 0; k < BoatVars.HOTSPOTS; k++)
			{
				inoculation |= kind.inoculation(k, v[BoatVars.HOTSPOT_0 + k]);
			}
			Boat boat = new Boat(s, type, hull, sail, keel, helm, inoculation, kind.brazier(v[BoatVars.BRAZIER]),
				focus(v[BoatVars.FOCUS]), place).withName(name); // cards use the player's boat name
			boats.add(boat);
			rows.add(new BoatInfo(s, type, title, where, true, placeNote(boat) + boat.describe()));
		}
		if (r.aboard && aboardSlot < 0)
		{
			boats.clear(); // on a boat that is not (known to be) one of the player's: routes from the sea don't sail
		}
		return new PlayerBoats(PlayerBoats.Status.READ, boats, rows, aboardNote);
	}

	/**
	 * The place for a {@code _PORT} value that is no dock, by the game's label for it (the boat table's port records,
	 * game table 147: 254 "Capsized", 253 "Lost", 255 "Bottled"); {@link Boat#NOWHERE} for any other label
	 * (a status this plugin version doesn't know: the boat is not used).
	 */
	static int statusPlace(String status)
	{
		switch (status.trim().toLowerCase(Locale.ROOT))
		{
			case "capsized":
				return Boat.CAPSIZED;
			case "lost":
				return Boat.LOST;
			case "bottled":
				return Boat.BOTTLED;
			default:
				return Boat.NOWHERE;
		}
	}

	/** The start of a usable boat's mouse-over when it is at no dock: how routes can get it (else ""). */
	static String placeNote(Boat b)
	{
		if (b.needsShipwright())
		{
			return (b.getDockId() == Boat.CAPSIZED ? "Capsized" : "Lost") + ": routes use it only when a shipwright may bring it"
				+ " (Recover-boat). ";
		}
		if (b.isBottled())
		{
			return "Bottled: routes use it only while its full boat bottle is in your inventory (released at any dock). ";
		}
		return "";
	}

	/**
	 * {@code SAILING_BOAT_n_TELEPORT_FOCUS}: the game's script 8997 asks for a Teleport focus when the
	 * value is 0 and for a Greater teleport focus when it is below 2.
	 */
	public static Boat.Focus focus(int value)
	{
		return value <= 0 ? Boat.Focus.NONE : value == 1 ? Boat.Focus.TELEPORT : Boat.Focus.GREATER;
	}

	/**
	 * Varbit SPELLBOOK (4070): 0 standard, 1 ancient, 2 lunar, 3 arceuus (Quest Helper
	 * requirements/util/Spellbook.java). Unknown values read as standard.
	 */
	public static Spellbook spellbook(int value)
	{
		switch (value)
		{
			case 1:
				return Spellbook.ANCIENT;
			case 2:
				return Spellbook.LUNAR;
			case 3:
				return Spellbook.ARCEUUS;
			default:
				return Spellbook.STANDARD;
		}
	}

	/** The house place for a POH_HOUSE_LOCATION value, or null (no house, or a value not in the table). */
	public static String housePlace(int value)
	{
		return value > 0 && value < HOUSE_PLACES.length ? HOUSE_PLACES[value] : null;
	}

	/**
	 * The game's enum of house places (enum 252: POH_HOUSE_LOCATION value -> the place's name). A number:
	 * RuneLite's API names no enum for it.
	 */
	public static final int HOUSE_PLACE_ENUM = 252;
	/** A house at a place neither the table nor the game's enum names (a new place): only "house" is met. */
	public static final String UNKNOWN_HOUSE_PLACE = "an unknown place";

	/**
	 * The house place for a POH_HOUSE_LOCATION value: the table's name; else the name the game's own enum gives
	 * ({@code enumName}, read in game with {@code client.getEnum(252)}; null or empty when it has none); else, for any
	 * value above 0, {@link #UNKNOWN_HOUSE_PLACE}: the player owns a house (the game's script says so for every value
	 * but 0), so the house teleports into it and the rides out of it work; only "house at <place>" is not met. 0 or
	 * less: null, no house.
	 */
	public static String housePlace(int value, String enumName)
	{
		if (value <= 0)
		{
			return null;
		}
		String known = housePlace(value);
		if (known != null)
		{
			return known;
		}
		return enumName != null && !enumName.trim().isEmpty() ? enumName.trim() : UNKNOWN_HOUSE_PLACE;
	}

	/** RuneLite's quest state as ours. */
	public static QuestState questState(net.runelite.api.QuestState s)
	{
		if (s == null)
		{
			return QuestState.NOT_STARTED;
		}
		switch (s)
		{
			case FINISHED:
				return QuestState.DONE;
			case IN_PROGRESS:
				return QuestState.STARTED;
			case NOT_STARTED:
			default:
				return QuestState.NOT_STARTED;
		}
	}
}

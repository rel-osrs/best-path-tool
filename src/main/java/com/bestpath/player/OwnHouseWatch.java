package com.bestpath.player;

import java.util.Locale;
import net.runelite.api.gameval.ItemID;

/**
 * Decides whether the player is inside THEIR OWN house: only the own house is scanned. Pure state machine, fed by
 * the plugin on the client thread.
 *
 * <p>The game tells no one directly whose house an instance is. A friend's house looks the same and has the same
 * furniture ids, so scanning it would give routes the friend's teleports. Only signals that cannot come from a
 * friend's house count (anything else: not own, nothing scanned, nothing lost):
 * <ol>
 * <li><b>How the player came in.</b> A menu click that always leads into the player's own house, followed by the
 * next scene load being a house: the house portal's "Home" / "Build mode" (cache: the portals 15477-15481, 28822,
 * 30172, 34947, 55353 have Enter, Home, Build mode, Friend's house), the Teleport to House spell ("Cast"; its
 * other option "Outside" lands outside), the "Teleport to house" tablet (Break / Inside; cache item 8013), the
 * Construction cape's "Tele to POH" and worn "Teleport" -> "Home" (items 9789 / 9790) and the Max cape's
 * "Teleports" -> "Home" (only the inventory Max cape, item 13280, has that sub-option, see {@link #MAX_CAPE}).
 * "Enter" asks in a dialogue (own or a friend's house), so it is not used. Any other click (except walking) cancels
 * a pending entry, and an entry is forgotten after {@link #PENDING_TICKS} ticks, so an old click can never mark a
 * friend's house as own.</li>
 * <li><b>Building mode.</b> In a house with the player's own building-mode setting on (varbit POH_BUILDING_MODE)
 * and empty build spaces in the scene ("Build" options): guests never see build spaces, and nobody can visit a
 * house in building mode.</li>
 * </ol>
 * A house scene loaded again while in a house (building mode switched, for instance) keeps the answer: the game has
 * no way from one house straight into another one.
 */
public final class OwnHouseWatch
{
	/** How long a click that leads into the own house waits for the house to load (walking to the portal, casting). */
	public static final int PENDING_TICKS = 30;

	/**
	 * The Max cape as carried (item 13280, RuneLite {@code ItemID.SKILLCAPE_MAX}): its inventory option "Teleports" has
	 * the sub-option "Home" (with the guilds, the house portals' places...), which always goes into the own house.
	 * The worn Max cape (13342, {@code SKILLCAPE_MAX_WORN}) is not used: it shows "Home" twice under other names (worn
	 * option "Home", and "POH Portals" -> "Home"), a click says only "Home" on "Max cape", so the two can't be told
	 * apart, and the cache does not say where either goes (item options are handled by the game's server).
	 */
	public static final int MAX_CAPE = ItemID.SKILLCAPE_MAX;

	/** What a menu click means for entering a house. */
	public enum Entry
	{
		/** Always leads into the player's own house. */
		OWN_HOUSE,
		/** Anything else: cancels a pending own-house entry. */
		OTHER,
		/** Walking, cancelling the menu, examining: changes nothing. */
		IGNORED
	}

	private boolean pending;
	private int pendingTick;
	private boolean inHouse;
	private boolean own;

	/**
	 * The menu click's meaning, from its option, target (colour tags allowed) and the item clicked (the menu entry's
	 * item id; -1 when it is not an item). Only the exact clicks listed in the class comment lead into the own house
	 * (checked against every object, NPC, item and spell option in the game cache); anything else on the same things
	 * (the tablet or the cape in the bank: "Withdraw-1", ...) cancels.
	 */
	public static Entry classify(String option, String target, int itemId)
	{
		String o = clean(option);
		String t = clean(target);
		if (o.isEmpty() || o.equals("walk here") || o.equals("cancel") || o.equals("examine"))
		{
			return Entry.IGNORED;
		}
		if (o.equals("tele to poh") && (t.equals("construct. cape") || t.equals("construct. cape(t)")))
		{
			return Entry.OWN_HOUSE; // Construction cape (cache items 9789 / 9790, inventory and worn option)
		}
		if (o.equals("home") && (t.equals("construct. cape") || t.equals("construct. cape(t)")))
		{
			// the worn Construction cape's "Teleport" -> "Home" (its only "Home" option)
			return Entry.OWN_HOUSE;
		}
		if (t.equals("teleport to house"))
		{
			// the spell (Cast) and the tablet (Break, Inside); "Outside" lands outside the house (no house scene), and
			// Group, Use, Drop, the bank's Withdraw / Deposit... lead nowhere
			switch (o)
			{
				case "cast":
				case "break":
				case "inside":
					return Entry.OWN_HOUSE;
				default:
					return Entry.OTHER;
			}
		}
		if (t.equals("portal") && (o.equals("home") || o.equals("build mode")))
		{
			return Entry.OWN_HOUSE;
		}
		if (t.equals("max cape") && o.equals("home") && itemId == MAX_CAPE)
		{
			return Entry.OWN_HOUSE; // "Teleports" -> "Home" on the carried Max cape
		}
		return Entry.OTHER;
	}

	/** Menu text without colour tags, lower case, plain spaces. */
	static String clean(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replaceAll("<[^>]*>", "").replace(' ', ' ').trim().toLowerCase(Locale.ROOT);
	}

	/** A menu click at this game tick. */
	public void onMenuClick(Entry e, int tick)
	{
		if (e == Entry.OWN_HOUSE)
		{
			pending = true;
			pendingTick = tick;
		}
		else if (e == Entry.OTHER)
		{
			pending = false;
		}
	}

	/**
	 * A scene finished loading (game state LOGGED_IN).
	 *
	 * @param house the new scene is a house (an instance of house room templates)
	 */
	public void onSceneLoaded(boolean house, int tick)
	{
		boolean entry = pending && tick - pendingTick <= PENDING_TICKS;
		pending = false;
		if (!house)
		{
			inHouse = false;
			own = false;
			return;
		}
		own = entry || inHouse && own; // a reload inside the same house keeps the answer
		inHouse = true;
	}

	/** Building mode seen in this house (own setting on, and build spaces in the scene): it is the player's own. */
	public void onBuildModeSeen()
	{
		if (inHouse)
		{
			own = true;
		}
	}

	/** Logged out, world hop, connection lost: nothing is known any more. */
	public void reset()
	{
		pending = false;
		inHouse = false;
		own = false;
	}

	public boolean isInHouse()
	{
		return inHouse;
	}

	public boolean isInOwnHouse()
	{
		return inHouse && own;
	}
}

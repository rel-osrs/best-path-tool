package com.bestpath.tool.edit;

import java.util.Locale;

/**
 * One manual change. It takes effect the moment it is made: it is written as
 * one line of {@code data/manual/changes.jsonl} with who, when and which
 * cache, and is re-applied every time a cache is opened. Plain data,
 * serialised to JSON.
 */
public final class Edit
{
	public enum Type
	{
		/** Add ({@link #on}) or remove a wall on one side of a tile. */
		WALL,
		/** Make a tile blocked ({@link #on}) or walkable. */
		TILE,
		/** Mark a door/gate shut ({@link #on}) or open again. */
		DOOR,
		/** Hide ({@link #on}) or unhide the sub-region containing a tile. */
		HIDE,
		/** Add a transport, or replace one with the same id. */
		TRANSPORT,
		/** Delete the transport {@link #target}. */
		TRANSPORT_DELETE,
		/** "Not a transport": candidate {@link #target} is rejected ({@link #on}) or un-rejected. */
		REJECT,
		/** Undo the applied change {@link #target}. */
		REVERT,
		/** Leave the whole 64x64 region containing ({@link #x}, {@link #y}) out of the export ({@link #on}), or take it back. */
		IGNORE_REGION,
		/** Put candidate or transport {@link #target} on the "test in game" list ({@link #on}) with {@link #transport}, or take it off. */
		TEST_LATER,
		/** Keep ({@link #on}) the closed-off pocket containing a tile in the data (it is left out automatically otherwise), or leave it out again. */
		KEEP,
		/** Mark the rectangle ({@link #x}, {@link #y}) to ({@link #toX}, {@link #toY}) free-to-play land ({@link #on}) or members only, all planes. */
		FREE_LAND,
		/**
		 * Delete ({@link #on}) or restore the sea channel {@link #target} (its ends, {@code SeaChannels.key}); ({@link #x},
		 * {@link #y}) is a tile on it.
		 */
		SEA_CHANNEL,
		/**
		 * Remove ({@link #on}) or keep the "go to nearest" place kind of map icon {@link #target} (the icon id), with
		 * {@link #name} its new name (null: unchanged). The answer to a place kind card.
		 */
		PLACE_KIND,
		/**
		 * Sea charting task {@link #target} (its number) is done from a boat ({@link #on}) or on land: the answer to a
		 * Boat / Land card.
		 */
		CHARTING_SPOT,
		/**
		 * An answer to a game update card ({@code CacheCompare}): {@link #target} is the
		 * finding's key (new cache, what, about what, where), {@link #name} the answer (KEEP, CHECKED, DELETE, TAKE_BACK,
		 * REMOVE); a delete or take-back is a change of its own in the same undo step.
		 */
		CACHE_CHECK
	}

	/** Wall sides, as in {@link #side}. */
	public static final int NORTH = 0;
	public static final int EAST = 1;
	public static final int SOUTH = 2;
	public static final int WEST = 3;
	private static final String[] SIDES = {"north", "east", "south", "west"};

	public String id;
	public Type type;
	public String author;
	/** ISO time it was made. */
	public String time = "";
	/** Cache it was made on: OpenRS2 id and game build. */
	public int cacheId = -1;
	public int cacheBuild = -1;
	/** Optional note; usually blank. */
	public String note = "";

	public int x;
	public int y;
	public int plane;
	public int side;
	public boolean on;
	/** For DOOR: the door object id (a fact the change relied on). */
	public int objectId = -1;
	/** For TRANSPORT_DELETE / REJECT / REVERT: the id it acts on. */
	public String target;
	/** For TRANSPORT: the transport after the change. */
	public TransportRecord transport;
	/** For HIDE / KEEP: size of the sub-region when it was hidden (a fact). */
	public int factSize;
	/** For PLACE_KIND: the kind's new name (null: the name stays). */
	public String name;
	/** For FREE_LAND: the rectangle's other corner. */
	public int toX;
	public int toY;

	public String describe()
	{
		switch (type)
		{
			case WALL:
				return (on ? "Add" : "Remove") + " wall on the " + SIDES[side] + " side of " + where();
			case TILE:
				return "Make " + where() + (on ? " blocked" : " walkable");
			case DOOR:
				return (on ? "Mark door shut" : "Mark door open") + " (object " + objectId + ") at " + where();
			case HIDE:
				return (on ? "Hide" : "Unhide") + " the sub-region at " + where() + (factSize > 0 ? " (" + factSize + " tiles)" : "");
			case KEEP:
				return (on ? "Keep the closed-off pocket at " : "Leave out the closed-off pocket at ") + where() + (factSize > 0 ? " (" + factSize + " tiles)" : "");
			case TRANSPORT:
				return "Transport: " + transport.describe();
			case TRANSPORT_DELETE:
				return "Delete transport " + target;
			case REJECT:
				return (on ? "Not a transport: " : "Is a transport again: ") + target;
			case REVERT:
				return "Undo change " + target;
			case FREE_LAND:
				return String.format(Locale.ROOT, "Mark %d, %d to %d, %d %s (all planes)", Math.min(x, toX), Math.min(y, toY),
					Math.max(x, toX), Math.max(y, toY), on ? "free-to-play land" : "members only");
			case IGNORE_REGION:
				return (on ? "Ignore" : "Stop ignoring") + " region " + regionId() + " (all planes, left out of the export)";
			case TEST_LATER:
				return (on ? "Test in game later: " : "Off the test list: ") + (transport != null ? transport.describe() : target);
			case SEA_CHANNEL:
				return (on ? "Delete" : "Keep") + " the sea channel " + target.replace("-", " to ");
			case PLACE_KIND:
				return (on ? "Remove" : "Keep") + " the nearest place kind of map icon " + target
					+ (name != null ? ", named \"" + name + "\"" : "");
			case CHARTING_SPOT:
				return "Sea charting task " + target + " is done " + (on ? "from a boat" : "on land");
			case CACHE_CHECK:
				return "Game update card answered (" + name + "): " + target;
			default:
				return type.toString();
		}
	}

	public String where()
	{
		return String.format(Locale.ROOT, "%d, %d, plane %d", x, y, plane);
	}

	/** The 64x64 region containing (x, y). */
	public int regionId()
	{
		return (x >> 6) << 8 | (y >> 6);
	}
}

// Copyright (c) 2026, BestPath contributors. BSD-2-Clause (the repository's LICENSE): free to copy into your plugin.
package com.bestpath.api;

/**
 * The Best Path public API: the names other RuneLite plugins use to talk to Best Path (docs/API.md).
 * <p>
 * Plugin Hub plugins can't share classes, so the API is RuneLite's {@code PluginMessage} event: post
 * {@code new PluginMessage(BestPathApi.NAMESPACE, BestPathApi.PATH, data)} on the {@code EventBus}, and subscribe to
 * {@code PluginMessage} to hear Best Path's answers (namespace {@value #NAMESPACE}). The messages and keys are the
 * Shortest Path plugin's, so a plugin written for Shortest Path works with Best Path by changing the namespace. Best
 * Path's own additions are extra keys and messages, which Shortest Path callers just ignore.
 * <p>
 * Plain Java only (no RuneLite or Best Path classes), so another plugin's developer may copy this file into their own
 * plugin. Places in messages are RuneLite {@code WorldPoint}s, or whole numbers packed the way {@link #pack} does.
 * <p>
 * Versioning ({@link #API_VERSION}): new keys, messages and values keep the version (callers ignore what they don't
 * know); removing one, or changing what one means, raises it.
 */
public final class BestPathApi
{
	/** The {@code PluginMessage} namespace of every message to and from Best Path. */
	public static final String NAMESPACE = "bestpath";
	/** The version of this API, in every message Best Path sends ({@link #API_VERSION_KEY}). */
	public static final int API_VERSION = 1;

	// ---- Messages other plugins send ----

	/**
	 * Show a route to {@link #TARGET} (one place, or several: the nearest is taken). Optional: {@link #SOURCE},
	 * {@link #ID}, {@link #TITLE}, {@link #NOTES}. {@link #START} and {@link #CONFIG} are read for Shortest Path
	 * compatibility but not used: shown routes always start where the player stands and use the player's own settings.
	 * Best Path answers with {@link #STATUS} messages and, as the player follows the route, {@link #PROGRESS}.
	 */
	public static final String PATH = "path";
	/** Clear the route shown, only if this {@link #SOURCE} set it (never the player's own or another plugin's). */
	public static final String CLEAR = "clear";
	/**
	 * Find a route without showing it: {@link #ID}, {@link #TARGET}, optional {@link #START} (default: where the player
	 * stands). Answered by one {@link #RESULT} with the same {@link #ID}.
	 */
	public static final String QUERY = "query";
	/** Ask where the route shown goes: optional {@link #ID}. Answered by {@link #CURRENT_TARGET}. */
	public static final String GET_TARGET = "getTarget";
	/** Is Best Path there, and which API version: optional {@link #ID}. Answered by {@link #PONG}. */
	public static final String PING = "ping";

	// ---- Messages Best Path sends ----

	/**
	 * The answer to a {@link #QUERY}: {@link #ID}, {@link #REACHED}, {@link #START}, {@link #TARGET} (the place the
	 * route goes to), {@link #PATH_TILES}, {@link #TRANSPORTS}, {@link #CLOSEST}, {@link #COST}, {@link #REASON},
	 * {@link #STEPS}, {@link #API_VERSION_KEY}.
	 */
	public static final String RESULT = "result";
	/**
	 * The answer to {@link #GET_TARGET}: {@link #ID}, {@link #SET} (a route is shown), and when set {@link #START}
	 * (where it was searched from) and {@link #TARGET} (a list of every place it may go to).
	 */
	public static final String CURRENT_TARGET = "currentTarget";
	/** The answer to {@link #PING}: {@link #ID}, {@link #API_VERSION_KEY}. */
	public static final String PONG = "pong";
	/**
	 * What happened to a route a plugin asked for with {@link #PATH} (sent only about that plugin's route):
	 * {@link #ID}, {@link #SOURCE}, {@link #STATUS_KEY} (one of the STATUS_ values), {@link #MESSAGE}, and for
	 * {@link #STATUS_FOUND} the same route keys as {@link #RESULT}.
	 */
	public static final String STATUS = "status";
	/** The player moved on to another step of that plugin's route: {@link #ID}, {@link #SOURCE}, {@link #STEP_INDEX}. */
	public static final String PROGRESS = "progress";

	// ---- Keys ----

	/** Any value the caller chooses; sent back unchanged in the answers. */
	public static final String ID = "id";
	/** The calling plugin's name (text), shown with its route; {@link #CLEAR} clears only this source's route. */
	public static final String SOURCE = "source";
	/** A place, or a {@code Set} / {@code List} of places (see the class comment). */
	public static final String TARGET = "target";
	/** A place to search from ({@link #QUERY}); in answers, where the route starts. */
	public static final String START = "start";
	/** Shortest Path's per-request settings: accepted and ignored (Best Path uses the player's own settings). */
	public static final String CONFIG = "config";
	/** The route header's text (default: "Destination from " and the source). */
	public static final String TITLE = "title";
	/** A list of text lines shown under the route header. */
	public static final String NOTES = "notes";
	/** Boolean: the route reaches a target. */
	public static final String REACHED = "reached";
	/** The route's tiles in order, a {@code List} of places (key "path", as in Shortest Path). */
	public static final String PATH_TILES = "path";
	/**
	 * The transports used, in order: a {@code List} of {@code Map}s with {@link #ORIGIN}, {@link #DESTINATION},
	 * {@link #OBJECT_INFO} and {@link #DISPLAY_INFO}.
	 */
	public static final String TRANSPORTS = "transports";
	/** Where a transport is used. */
	public static final String ORIGIN = "origin";
	/** Where a transport lands. */
	public static final String DESTINATION = "destination";
	/** What is clicked, "Option Target objectId" (e.g. "Climb-up Ladder 16683"), or null when not an object. */
	public static final String OBJECT_INFO = "objectInfo";
	/** The step's text as Best Path shows it ("Climb up the ladder"). */
	public static final String DISPLAY_INFO = "displayInfo";
	/** The place nearest the target the search reached, or null when not known. */
	public static final String CLOSEST = "closest";
	/** The route's length in game ticks (whole number); -1 without a route. */
	public static final String COST = "cost";
	/** Why the search ended: one of the REASON_ values. */
	public static final String REASON = "reason";
	/**
	 * Best Path's steps: a {@code List} of {@code Map}s with {@link #STEP_KIND}, {@link #STEP_TITLE},
	 * {@link #STEP_TICKS} and {@link #STEP_END}.
	 */
	public static final String STEPS = "steps";
	/** A step's kind: WALK, TRAVERSAL, DOOR, TELEPORT, BOAT, SAIL, BANK, STASH or KILL. */
	public static final String STEP_KIND = "kind";
	/** A step's text ("Climb up the ladder"). */
	public static final String STEP_TITLE = "title";
	/** A step's game ticks. */
	public static final String STEP_TICKS = "ticks";
	/** Where the player is after a step, or null when not known. */
	public static final String STEP_END = "end";
	/** Boolean ({@link #CURRENT_TARGET}): a route is shown. */
	public static final String SET = "set";
	/** {@link #STATUS}'s state: one of the STATUS_ values. */
	public static final String STATUS_KEY = "status";
	/** Text for people, e.g. why there is no route. */
	public static final String MESSAGE = "message";
	/** The index in {@link #STEPS} of the step the player is on. */
	public static final String STEP_INDEX = "stepIndex";
	/** {@link #API_VERSION}, in every message Best Path sends. */
	public static final String API_VERSION_KEY = "apiVersion";

	// ---- REASON values (the first three as in Shortest Path) ----

	/** A route was found. */
	public static final String REASON_TARGET_REACHED = "TARGET_REACHED";
	/** No route with the transports the player can use and allows. */
	public static final String REASON_SEARCH_EXHAUSTED = "SEARCH_EXHAUSTED";
	/** The message was not understood (no target, a place that isn't one, too many places). */
	public static final String REASON_INVALID = "INVALID";
	/** The search failed. */
	public static final String REASON_ERROR = "ERROR";
	/** Best Path's map data is not loaded yet. */
	public static final String REASON_NO_DATA = "NO_DATA";
	/** The player is not logged in, so there is nothing to search from. */
	public static final String REASON_NOT_LOGGED_IN = "NOT_LOGGED_IN";
	/** Too many queries are waiting; ask again later. */
	public static final String REASON_BUSY = "BUSY";

	// ---- STATUS values ----

	/** The route was found and is shown (again after each new search, e.g. when the player left the route). */
	public static final String STATUS_FOUND = "FOUND";
	/** No route ({@link #MESSAGE} says why). */
	public static final String STATUS_NO_ROUTE = "NO_ROUTE";
	/** Best Path's map data is not loaded yet. */
	public static final String STATUS_NO_DATA = "NO_DATA";
	/** The search failed, or the player is not logged in ({@link #MESSAGE}). */
	public static final String STATUS_ERROR = "ERROR";
	/** The message was not understood ({@link #MESSAGE}); nothing changed. */
	public static final String STATUS_INVALID = "INVALID";
	/** The player arrived. */
	public static final String STATUS_ARRIVED = "ARRIVED";
	/** The route was cleared (by the player, or by this source's {@link #CLEAR}). */
	public static final String STATUS_CLEARED = "CLEARED";
	/** Another destination replaced the route (the player's own, or another plugin's). */
	public static final String STATUS_REPLACED = "REPLACED";
	/** The player turned off "Routes from other plugins": routes asked for with {@link #PATH} are not shown. */
	public static final String STATUS_DISABLED = "DISABLED";

	/** The most places one message may hold. */
	public static final int MAX_TARGETS = 5000;
	/** A packed place that is no place. */
	public static final int UNDEFINED = -1;

	private BestPathApi()
	{
	}

	/**
	 * A place packed into one whole number, the layout the Shortest Path plugin uses: x in bits 0-14, y in bits 15-29,
	 * plane in bits 30-31.
	 */
	public static int pack(int x, int y, int plane)
	{
		return (x & 0x7FFF) | (y & 0x7FFF) << 15 | (plane & 3) << 30;
	}

	/** The x of a {@link #pack packed} place. */
	public static int unpackX(int packed)
	{
		return packed & 0x7FFF;
	}

	/** The y of a {@link #pack packed} place. */
	public static int unpackY(int packed)
	{
		return packed >>> 15 & 0x7FFF;
	}

	/** The plane of a {@link #pack packed} place. */
	public static int unpackPlane(int packed)
	{
		return packed >>> 30 & 3;
	}
}

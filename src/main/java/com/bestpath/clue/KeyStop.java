package com.bestpath.clue;

import com.bestpath.core.data.ClueData;
import com.bestpath.core.req.PlayerState;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteStep;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * The "kill for the key" first stop of a key clue step. A key step ("Kill a MALE Barbarian ... to receive the key",
 * then search a chest) has, from the cache's clue-helper table, the tile of the NPC that drops the key and the key's
 * item ({@code clues.tsv} columns {@code keyNpc}, {@code keyItem}). While the player has no such key (not carried,
 * worn or in the bank), the clue route goes to that NPC first; once the key is carried the stop leaves the route. A
 * key in the bank: no stop (the bank stop for the step's needs gets it). Pure.
 */
public final class KeyStop
{
	/** The id of the kill card (its Skip button: skips the stop for this route): this plus the step's key. */
	public static final String STOP_PREFIX = "key-npc:";

	private KeyStop()
	{
	}

	/**
	 * The kill stop for the carried clue, or null (not a key step, no key NPC in the data, the player has the key, or
	 * nothing is known about the player).
	 *
	 * @param state    the player (items carried, worn, banked), or null
	 * @param itemName an item's name, or null when not known
	 */
	public static RouteRequest.Stop stop(DetectedClue clue, PlayerState state, IntFunction<String> itemName)
	{
		if (clue == null || clue.getKind() != DetectedClue.Kind.STEP || clue.getSteps().size() != 1 || state == null)
		{
			return null;
		}
		ClueData.Step s = clue.getSteps().get(0);
		int key = s.getKeyItem();
		if (s.getKeyNpc() == -1 || key <= 0 || state.carried(key) > 0 || state.banked(key) > 0)
		{
			return null;
		}
		String npc = s.getKeyNpcName().isEmpty() ? "the monster" : s.getKeyNpcName();
		String keyName = itemName == null ? null : itemName.apply(key);
		List<String> lines = new ArrayList<>();
		if (!s.getDescription().isEmpty())
		{
			lines.add(s.getDescription());
		}
		lines.add("Pick up the " + (keyName == null || keyName.isEmpty() ? "key" : keyName) + " it drops; then the route goes"
			+ " on to the clue.");
		return new RouteRequest.Stop(STOP_PREFIX + s.getKey(), s.getKeyNpc(), "Kill for the key: " + npc, lines,
			RouteStep.Kind.KILL);
	}
}

package com.bestpath.tool.clue;

import java.util.Collections;
import java.util.List;

/**
 * One clue step as the game cache describes it (one row of a clue-helper
 * table, with its target row resolved). Immutable. Written to and read from
 * {@code data/clues/clue-steps.tsv} by {@link ClueFile}.
 */
public final class ClueStep
{
	/** A place that matters for the step besides (or instead of) the target: a STASH unit, where a key drops. */
	public static final class Place
	{
		/** What the place is: "stash", "key-npc". */
		public final String role;
		/** Object or NPC ids there (may be empty). */
		public final List<Integer> ids;
		public final String name;
		public final int x;
		public final int y;
		public final int plane;

		public Place(String role, List<Integer> ids, String name, int x, int y, int plane)
		{
			this.role = role;
			this.ids = List.copyOf(ids);
			this.name = name;
			this.x = x;
			this.y = y;
			this.plane = plane;
		}
	}

	/** "cryptic-1233": type and the cache row id; stable while Jagex keeps the row. */
	public final String key;
	/** anagram, map, cipher, coordinate, cryptic, emote, fairyring, falo, hotcold, music, skillchallenge. */
	public final String type;
	/** beginner, easy, medium, hard, elite, master, quest (cache difficulty 0-6). */
	public final String tier;
	/** Clue items whose param 623 points at this row (empty: the tier has one shared item, or a device). */
	public final List<Integer> items;
	/** The clue text (anagram, cryptic, ...), the track for music clues; empty for maps and hot/cold. */
	public final String text;
	/** npc, loc, coord, key, kill or none. */
	public final String target;
	/** NPC or object ids of the target (kill: every NPC that counts). */
	public final List<Integer> targetIds;
	public final String targetName;
	/** Target tile; {@link #hasTile()} false when the cache gives none (kill steps). */
	public final int x;
	public final int y;
	public final int plane;
	/** The cache's description of the target ("The Apothecary in south-west Varrock."). */
	public final String description;
	public final List<Place> places;
	/** What the step needs, in words ("item 716 A Bullroarer", "Agility 60", "wear 1654 Gold ring"). */
	public final List<String> needs;
	/** Other facts: emote, fight, challenge question, puzzle box, fallback NPC. */
	public final List<String> notes;
	/** Where it came from: "cache 2735 table 8 row 1233 -> table 15 row 933". */
	public final String source;
	/** Things to look at: empty when nothing is in doubt. */
	public final List<String> checks;
	/**
	 * What the player does there, as the game's clue helper says it: dig, emote (with the emotes, "emote 20 Clap |
	 * 14 Spin"), music, talk, search, key, kill, none ({@link ClueExtractor#action}); "" in steps made by hand.
	 */
	public final String action;
	/** {@link #needs} as a requirement line (docs/DATA_FORMATS.md; item ids, worn outfit items), "" for nothing. */
	public final String requirement;
	/** The STASH unit's state: "varbit V ; tier ; place" (built when V = 1), "" without a STASH unit. */
	public final String stash;

	public ClueStep(String key, String type, String tier, List<Integer> items, String text, String target, List<Integer> targetIds,
		String targetName, int x, int y, int plane, String description, List<Place> places, List<String> needs, List<String> notes,
		String source, List<String> checks)
	{
		this(key, type, tier, items, text, target, targetIds, targetName, x, y, plane, description, places, needs, notes, source, checks,
			"", "", "");
	}

	public ClueStep(String key, String type, String tier, List<Integer> items, String text, String target, List<Integer> targetIds,
		String targetName, int x, int y, int plane, String description, List<Place> places, List<String> needs, List<String> notes,
		String source, List<String> checks, String action, String requirement, String stash)
	{
		this.action = action == null ? "" : action;
		this.requirement = requirement == null ? "" : requirement;
		this.stash = stash == null ? "" : stash;
		this.key = key;
		this.type = type;
		this.tier = tier;
		this.items = List.copyOf(items);
		this.text = text;
		this.target = target;
		this.targetIds = List.copyOf(targetIds);
		this.targetName = targetName;
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.description = description;
		this.places = Collections.unmodifiableList(List.copyOf(places));
		this.needs = List.copyOf(needs);
		this.notes = List.copyOf(notes);
		this.source = source;
		this.checks = List.copyOf(checks);
	}

	public boolean hasTile()
	{
		return x >= 0;
	}

	/** The STASH unit's form varbit (built when 1), or -1. */
	public int stashVarbit()
	{
		String[] p = stash.split(" ; ");
		if (p.length >= 1 && p[0].startsWith("varbit "))
		{
			try
			{
				return Integer.parseInt(p[0].substring(7).trim());
			}
			catch (NumberFormatException e)
			{
				return -1;
			}
		}
		return -1;
	}

	/** The STASH unit's place name as the game shows it ("Near the parrots in Ardougne Zoo"), or "". */
	public String stashPlace()
	{
		String[] p = stash.split(" ; ", 3);
		return p.length == 3 ? p[2].trim() : "";
	}
}

package com.bestpath.tool.clue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Jagex's clue-helper DB tables in the game cache. The cache stores only
 * numbers; the table and column names come from RuneLite's public gameval
 * names ({@code DBTableID.Cluehelper*}), see docs/CLUES.md.
 */
public final class ClueTables
{
	public static final int CLUETYPE = 3;
	public static final int ANAGRAM = 4;
	public static final int MAP = 5;
	public static final int CIPHER = 6;
	public static final int COORDINATE = 7;
	public static final int CRYPTIC = 8;
	public static final int EMOTE = 9;
	public static final int FAIRYRING = 10;
	public static final int FALO = 11;
	public static final int HOTCOLD = 12;
	public static final int MUSIC = 13;
	public static final int SKILLCHALLENGE = 14;
	public static final int TARGET_NPC = 15;
	public static final int TARGET_LOC = 16;
	public static final int TARGET_17 = 17; // not named in RuneLite's gameval
	public static final int TARGET_COORD = 18;
	public static final int TARGET_KEY = 19;
	public static final int TARGET_KILL = 20;
	public static final int REQ_OBJ = 21;
	public static final int REQ_OBJ_PARAM = 22;
	public static final int REQ_QUEST = 23;
	public static final int REQ_STAT = 24;
	public static final int CHALLENGE_QUESTION = 25;
	public static final int CHALLENGE_BOX = 26;
	public static final int COMBAT_ENCOUNTER = 27;
	public static final int OUTFIT = 28;

	/** Table id -> RuneLite gameval name (without the Cluehelper prefix). */
	public static final Map<Integer, String> NAMES;

	static
	{
		Map<Integer, String> m = new LinkedHashMap<>();
		m.put(CLUETYPE, "Cluetype");
		m.put(ANAGRAM, "ClueAnagram");
		m.put(MAP, "ClueMap");
		m.put(CIPHER, "ClueCipher");
		m.put(COORDINATE, "ClueCoordinate");
		m.put(CRYPTIC, "ClueCryptic");
		m.put(EMOTE, "ClueEmote");
		m.put(FAIRYRING, "ClueFairyring");
		m.put(FALO, "ClueFalobard");
		m.put(HOTCOLD, "ClueHotcold");
		m.put(MUSIC, "ClueMusic");
		m.put(SKILLCHALLENGE, "ClueSkillchallenge");
		m.put(TARGET_NPC, "TargetNpc");
		m.put(TARGET_LOC, "TargetLoc");
		m.put(TARGET_17, "Table17");
		m.put(TARGET_COORD, "TargetCoord");
		m.put(TARGET_KEY, "TargetKey");
		m.put(TARGET_KILL, "TargetKill");
		m.put(REQ_OBJ, "RequirementObj");
		m.put(REQ_OBJ_PARAM, "RequirementObjParamTrailItem");
		m.put(REQ_QUEST, "RequirementQuest");
		m.put(REQ_STAT, "RequirementStat");
		m.put(CHALLENGE_QUESTION, "ChallengeQuestion");
		m.put(CHALLENGE_BOX, "ChallengeBox");
		m.put(COMBAT_ENCOUNTER, "CombatEncounter");
		m.put(OUTFIT, "Outfit");
		NAMES = Collections.unmodifiableMap(m);
	}

	private ClueTables()
	{
	}
}

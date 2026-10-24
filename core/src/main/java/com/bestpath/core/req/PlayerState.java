package com.bestpath.core.req;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What a requirement is checked against: one player's levels, quests,
 * unlocks (varbits / varps), carried and worn items, bank, answers, the
 * collection log items and grown spirit trees the plugin has seen, whether an axe is stored at the canoe stations,
 * player-owned house (where it is, what is built in it) and which long-cooldown
 * teleports are cooling down ({@link Cooldown}). In the
 * plugin it is a snapshot of the logged-in player; it can also be a made-up
 * test player. Immutable; build with {@link Builder}.
 *
 * <p>Player answers: each is yes, no or not given yet. A test player's answers
 * are all known (not given = no). The plugin's player asks the ones not given
 * yet ({@link Builder#askUnanswered}): such an answer counts as yes until the
 * player says no, so "answer X" is met and "not answer X" is not, both resting
 * on the answer ({@link Check#getUnanswered()}); the step card asks it. The
 * Prifddinas respawn point is the other way: no until answered
 * ({@link #isNoUntilAnswered}).
 */
public final class PlayerState
{
	public static final int COINS = 995;

	final String name;
	private final int[] levels;
	final Map<String, QuestState> quests;
	final Map<Integer, Integer> varbits;
	final Map<Integer, Integer> varps;
	private final Map<Integer, Integer> carried;
	private final int[] worn;
	final Map<Integer, Integer> bank;
	private final boolean members;
	/** Lower-case answer -> yes (true) or no (false); not in it = not given yet. */
	final Map<String, Boolean> answers;
	private final boolean askUnanswered;
	final boolean allUnlocked;
	private final boolean allItems;
	private final String house;
	private final Set<String> houseThings;
	private final Spellbook spellbook;
	final Map<Integer, Integer> pouch;
	private final boolean pouchInBank;
	private final Map<Cooldown, Long> coolingDown;
	/** Item ids known to be in the collection log. */
	private final Set<Integer> collectionLog;
	/** Places (lower case) whose spirit tree is known to be grown. */
	private final Set<String> spiritTrees;
	/**
	 * An axe stored at the canoe stations (detected in game, never asked): yes, no, or null = not known (the plugin has
	 * not seen it yet; like an item maybe in a STASH unit, never assumed absent).
	 */
	private final Boolean canoeAxeStored;
	/** The most coins the player will pay for one transport; a fare above it counts as not met. -1: no limit. */
	private final int coinCap;

	private PlayerState(Builder b)
	{
		coinCap = -1; // a limit is set with withCoinCap
		name = b.name;
		levels = b.levels.clone();
		quests = Collections.unmodifiableMap(new HashMap<>(b.quests));
		varbits = Collections.unmodifiableMap(new HashMap<>(b.varbits));
		varps = Collections.unmodifiableMap(new HashMap<>(b.varps));
		carried = Collections.unmodifiableMap(new HashMap<>(b.carried));
		worn = b.worn.clone();
		bank = Collections.unmodifiableMap(new HashMap<>(b.bank));
		members = b.members;
		answers = Collections.unmodifiableMap(new HashMap<>(b.answers));
		askUnanswered = b.askUnanswered;
		allUnlocked = b.allUnlocked;
		allItems = b.allItems;
		house = b.house;
		houseThings = Collections.unmodifiableSet(new HashSet<>(b.houseThings));
		spellbook = b.spellbook;
		pouch = Collections.unmodifiableMap(new HashMap<>(b.pouch));
		pouchInBank = b.pouchInBank;
		coolingDown = b.coolingDown.isEmpty() ? Collections.emptyMap()
			: Collections.unmodifiableMap(new EnumMap<>(b.coolingDown));
		collectionLog = Collections.unmodifiableSet(new HashSet<>(b.collectionLog));
		spiritTrees = Collections.unmodifiableSet(new HashSet<>(b.spiritTrees));
		canoeAxeStored = b.canoeAxeStored;
	}

	/** The same player with another coin limit (-1: none); this one when it is already that. */
	public PlayerState withCoinCap(int cap)
	{
		int c = Math.max(-1, cap);
		return c == coinCap ? this : new PlayerState(this, c);
	}

	private PlayerState(PlayerState o, int coinCap)
	{
		this.coinCap = coinCap;
		name = o.name;
		levels = o.levels;
		quests = o.quests;
		varbits = o.varbits;
		varps = o.varps;
		carried = o.carried;
		worn = o.worn;
		bank = o.bank;
		members = o.members;
		answers = o.answers;
		askUnanswered = o.askUnanswered;
		allUnlocked = o.allUnlocked;
		allItems = o.allItems;
		house = o.house;
		houseThings = o.houseThings;
		spellbook = o.spellbook;
		pouch = o.pouch;
		pouchInBank = o.pouchInBank;
		coolingDown = o.coolingDown;
		collectionLog = o.collectionLog;
		spiritTrees = o.spiritTrees;
		canoeAxeStored = o.canoeAxeStored;
	}

	/** The most coins the player will pay for one transport; -1 for no limit. */
	public int getCoinCap()
	{
		return coinCap;
	}

	/**
	 * True when {@code o} has exactly the same contents (every field), so everything worked out from a player state is
	 * the same for both. This lets the transports a player can use be worked out again only when the state changed,
	 * not for every route. Every field must be compared here.
	 */
	public boolean sameAs(PlayerState o)
	{
		if (o == this)
		{
			return true;
		}
		return o != null && members == o.members && askUnanswered == o.askUnanswered && coinCap == o.coinCap
			&& allUnlocked == o.allUnlocked && allItems == o.allItems && pouchInBank == o.pouchInBank && spellbook == o.spellbook
			&& Objects.equals(canoeAxeStored, o.canoeAxeStored)
			&& Objects.equals(name, o.name) && Objects.equals(house, o.house)
			&& Arrays.equals(levels, o.levels) && Arrays.equals(worn, o.worn)
			&& quests.equals(o.quests) && varbits.equals(o.varbits) && varps.equals(o.varps) && carried.equals(o.carried)
			&& bank.equals(o.bank) && answers.equals(o.answers) && houseThings.equals(o.houseThings) && pouch.equals(o.pouch)
			&& coolingDown.equals(o.coolingDown) && collectionLog.equals(o.collectionLog) && spiritTrees.equals(o.spiritTrees);
	}

	public int level(Skill s)
	{
		return levels[s.ordinal()];
	}

	/**
	 * The combat level from the real levels (Wiki "Combat level" rev 15305725): base = (Defence + Hitpoints +
	 * floor(Prayer / 2)) / 4, plus 13/40 of the largest of Attack + Strength, floor(Ranged * 3/2) and
	 * floor(Magic * 3/2); rounded down.
	 */
	public int combatLevel()
	{
		double base = (level(Skill.DEFENCE) + level(Skill.HITPOINTS) + Math.floor(level(Skill.PRAYER) / 2.0)) / 4.0;
		double melee = level(Skill.ATTACK) + level(Skill.STRENGTH);
		double range = Math.floor(level(Skill.RANGED) * 1.5);
		double mage = Math.floor(level(Skill.MAGIC) * 1.5);
		return (int) Math.floor(base + 13.0 / 40.0 * Math.max(melee, Math.max(range, mage)));
	}

	public QuestState quest(String questName)
	{
		if (allUnlocked)
		{
			return QuestState.DONE;
		}
		return quests.getOrDefault(key(questName), QuestState.NOT_STARTED);
	}

	public int varbit(int id)
	{
		return varbits.getOrDefault(id, 0);
	}

	public int varp(int id)
	{
		return varps.getOrDefault(id, 0);
	}

	/** How many of this item are in the inventory or worn (and in the rune pouch, when the pouch is carried). */
	public int carried(int itemId)
	{
		int n = carried.getOrDefault(itemId, 0) + (pouchInBank ? 0 : pouch.getOrDefault(itemId, 0));
		for (int w : worn)
		{
			if (w == itemId)
			{
				n++;
			}
		}
		return n;
	}

	public Map<Integer, Integer> getInventory()
	{
		return carried;
	}

	/** The item worn in this slot, or -1. */
	public int worn(EquipSlot slot)
	{
		return worn[slot.ordinal()];
	}

	/** How many of this item are in the bank (and in the rune pouch, when the pouch is in the bank). */
	public int banked(int itemId)
	{
		return bank.getOrDefault(itemId, 0) + (pouchInBank ? pouch.getOrDefault(itemId, 0) : 0);
	}

	/** True if this item is worn in any slot. */
	public boolean wears(int itemId)
	{
		for (int w : worn)
		{
			if (w == itemId)
			{
				return true;
			}
		}
		return false;
	}

	public boolean isPouchInBank()
	{
		return pouchInBank;
	}

	public Spellbook getSpellbook()
	{
		return spellbook;
	}

	/** True if the player is on this spellbook; with everything unlocked, any spellbook counts. */
	public boolean onSpellbook(Spellbook s)
	{
		return allUnlocked || spellbook == s;
	}

	public boolean isMembers()
	{
		return members;
	}

	/**
	 * Questions that count as no until answered, for every player; every other question not answered yet counts as
	 * yes for the plugin's player. Lower case.
	 */
	private static final Set<String> NO_UNTIL_ANSWERED = Set.of(key("Respawn point set to Prifddinas"));

	/** True if this question counts as no until the player answers it ({@link #NO_UNTIL_ANSWERED}). */
	public static boolean isNoUntilAnswered(String answer)
	{
		return NO_UNTIL_ANSWERED.contains(key(answer));
	}

	/** The player's answer: yes, no, or null when not given yet (with everything unlocked: always yes). */
	public Boolean answer(String answer)
	{
		return allUnlocked ? Boolean.TRUE : answers.get(key(answer));
	}

	/** Every answer given, yes (true) or no (false), lower case. */
	public Map<String, Boolean> getAnswerMap()
	{
		return answers;
	}

	/**
	 * True if questions not answered yet count as yes until answered (the plugin's player: routes may use such a
	 * transport, and its step card asks); false if not answered means no (test players).
	 */
	public boolean isAskUnanswered()
	{
		return askUnanswered;
	}

	/** Every quest done, every unlock (varbit / varp) and player answer counts as met. */
	public boolean isAllUnlocked()
	{
		return allUnlocked;
	}

	/** Every item and any number of coins counts as carried. */
	public boolean isAllItems()
	{
		return allItems;
	}

	/** Where the player's house is (e.g. "Yanille"), or null for no house. */
	public String getHouse()
	{
		return house;
	}

	/** True if the house is at this place; with everything unlocked, any place counts. */
	public boolean houseAt(String place)
	{
		return allUnlocked || (house != null && house.equalsIgnoreCase(place.trim()));
	}

	public boolean hasHouse()
	{
		return allUnlocked || house != null;
	}

	/** True if this is built in the house (e.g. "ornate jewellery box"); with everything unlocked, all is built. */
	public boolean houseHas(String thing)
	{
		return allUnlocked || (house != null && houseThings.contains(key(thing)));
	}

	/** What is built in the house (lower-case names). */
	public Set<String> getHouseThings()
	{
		return houseThings;
	}

	/**
	 * True if the teleports with this cooldown can be used now (not cooling down). Decided when the state was made
	 * (the plugin: from the game's last-use minutes at the snapshot; a test player: ready unless set with
	 * {@link Builder#coolingDown}). Not affected by "everything unlocked".
	 */
	public boolean cooldownReady(Cooldown c)
	{
		return !coolingDown.containsKey(c);
	}

	/**
	 * The cooldowns cooling down now and the minute (since 1970 UTC) each is ready again (0 = not known, e.g. a test
	 * player); empty = all ready.
	 */
	public Map<Cooldown, Long> getCoolingDown()
	{
		return coolingDown;
	}

	/** True if this item is known to be in the collection log (with everything unlocked: always). */
	public boolean inCollectionLog(int itemId)
	{
		return allUnlocked || collectionLog.contains(itemId);
	}

	/** The item ids known to be in the collection log. */
	public Set<Integer> getCollectionLog()
	{
		return collectionLog;
	}

	/** True if the spirit tree at this place is known to be grown (with everything unlocked: always). */
	public boolean spiritTreeGrown(String place)
	{
		return allUnlocked || spiritTrees.contains(key(place));
	}

	/** The places (lower case) whose spirit tree is known to be grown. */
	public Set<String> getSpiritTrees()
	{
		return spiritTrees;
	}

	/**
	 * True if an axe is stored at the canoe stations or may be (not known yet; with everything unlocked: always). Only a
	 * known "no" is false.
	 */
	public boolean canoeAxeStored()
	{
		return allUnlocked || !Boolean.FALSE.equals(canoeAxeStored);
	}

	/** Whether an axe is stored at the canoe stations as last seen: yes, no, or null = not known yet. */
	public Boolean getCanoeAxeStored()
	{
		return canoeAxeStored;
	}

	static String key(String s)
	{
		return s.trim().toLowerCase(Locale.ROOT);
	}

	public static final class Builder
	{
		private final String name;
		private final int[] levels = new int[Skill.values().length];
		private final Map<String, QuestState> quests = new HashMap<>();
		private final Map<Integer, Integer> varbits = new HashMap<>();
		private final Map<Integer, Integer> varps = new HashMap<>();
		private final Map<Integer, Integer> carried = new HashMap<>();
		private final int[] worn = new int[EquipSlot.values().length];
		private final Map<Integer, Integer> bank = new HashMap<>();
		private boolean members;
		private final Map<String, Boolean> answers = new HashMap<>();
		private boolean askUnanswered;
		private boolean allUnlocked;
		boolean allItems;
		private String house;
		private final Set<String> houseThings = new HashSet<>();
		private Spellbook spellbook = Spellbook.STANDARD;
		private final Map<Integer, Integer> pouch = new HashMap<>();
		private boolean pouchInBank;
		private final Map<Cooldown, Long> coolingDown = new EnumMap<>(Cooldown.class);
		private final Set<Integer> collectionLog = new HashSet<>();
		private final Set<String> spiritTrees = new HashSet<>();
		private Boolean canoeAxeStored = Boolean.FALSE;

		public Builder(String name)
		{
			this.name = name;
			Arrays.fill(levels, 1);
			levels[Skill.HITPOINTS.ordinal()] = 10;
			Arrays.fill(worn, -1);
		}

		public Builder level(Skill s, int level)
		{
			levels[s.ordinal()] = level;
			return this;
		}

		public Builder quest(String questName, QuestState state)
		{
			quests.put(key(questName), state);
			return this;
		}

		public Builder varbit(int id, int value)
		{
			varbits.put(id, value);
			return this;
		}

		public Builder varp(int id, int value)
		{
			varps.put(id, value);
			return this;
		}

		/** Adds this many to the inventory. */
		public Builder carry(int itemId, int count)
		{
			carried.merge(itemId, count, Integer::sum);
			return this;
		}

		public Builder wear(EquipSlot slot, int itemId)
		{
			worn[slot.ordinal()] = itemId;
			return this;
		}

		/** Adds this many to the bank. */
		public Builder bank(int itemId, int count)
		{
			bank.merge(itemId, count, Integer::sum);
			return this;
		}

		public Builder members(boolean on)
		{
			members = on;
			return this;
		}

		/** The player's answer: yes (true) or no (false). */
		public Builder answer(String answer, boolean yes)
		{
			answers.put(key(answer), yes);
			return this;
		}

		/**
		 * Questions not answered yet count as yes until answered (the plugin's player): "answer X" is then met and
		 * "not answer X" not, both resting on the answer. Off (the default): not answered means no.
		 */
		public Builder askUnanswered(boolean on)
		{
			askUnanswered = on;
			return this;
		}

		public Builder allUnlocked(boolean on)
		{
			allUnlocked = on;
			return this;
		}

		/** Where the house is; null or blank = no house. */
		public Builder house(String place)
		{
			house = place == null || place.isBlank() ? null : place.trim();
			return this;
		}

		/** Something built in the house (a jewellery box, a portal...). */
		public Builder houseThing(String thing)
		{
			houseThings.add(key(thing));
			return this;
		}

		public Builder spellbook(Spellbook s)
		{
			spellbook = s;
			return this;
		}

		/** Adds runes to the rune pouch. */
		public Builder pouch(int itemId, int count)
		{
			pouch.merge(itemId, count, Integer::sum);
			return this;
		}

		/** The rune pouch is in the bank (its runes count as banked), not carried. */
		public Builder pouchInBank(boolean on)
		{
			pouchInBank = on;
			return this;
		}

		/**
		 * The teleports with this cooldown are cooling down, ready again in minute {@code readyAtMinute} (since 1970
		 * UTC; 0 = not known).
		 */
		public Builder coolingDown(Cooldown c, long readyAtMinute)
		{
			coolingDown.put(c, readyAtMinute);
			return this;
		}

		/** This item is in the collection log. */
		public Builder collectionLog(int itemId)
		{
			collectionLog.add(itemId);
			return this;
		}

		/** The spirit tree at this place is grown (travel with it). */
		public Builder spiritTree(String place)
		{
			spiritTrees.add(key(place));
			return this;
		}

		/**
		 * An axe is stored at the canoe stations (shared by every station): yes, no (the default, as for test players),
		 * or null = not known.
		 */
		public Builder canoeAxeStored(Boolean stored)
		{
			canoeAxeStored = stored;
			return this;
		}

		public PlayerState build()
		{
			return new PlayerState(this);
		}
	}
}

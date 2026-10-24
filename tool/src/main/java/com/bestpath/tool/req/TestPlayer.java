package com.bestpath.tool.req;

import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Skill;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A test player for the Data Tool's route check, as saved in
 * {@code data/manual/test-players.json}. Plain data (JSON); {@link #toState}
 * makes the {@link PlayerState} requirements are checked against.
 */
public final class TestPlayer
{
	public String name = "New player";
	public boolean members = true;
	/** Every quest done, every unlock, player answer and house thing met (any house place counts). */
	public boolean allUnlocked;
	/** Carries every item and any number of coins. */
	public boolean allItems;
	/** Skill name ("Agility") to real level; missing = 1 (Hitpoints 10). */
	public Map<String, Integer> levels = new LinkedHashMap<>();
	/** Quest name to "done" or "started"; missing = not started. */
	public Map<String, String> quests = new LinkedHashMap<>();
	public Map<String, Integer> varbits = new LinkedHashMap<>();
	public Map<String, Integer> varps = new LinkedHashMap<>();
	/** Item id to count. */
	public Map<String, Integer> inventory = new LinkedHashMap<>();
	/** Slot name ("head") to item id. */
	public Map<String, Integer> worn = new LinkedHashMap<>();
	/** Item id to count. */
	public Map<String, Integer> bank = new LinkedHashMap<>();
	public List<String> answers = new ArrayList<>();
	/** Where the player-owned house is (e.g. "Yanille"); null or empty = no house. */
	public String house;
	/** What is built in the house ("ornate jewellery box", "portal nexus: Varrock Teleport"...). */
	public List<String> houseThings = new ArrayList<>();
	/** standard, ancient, lunar or arceuus; null = standard. */
	public String spellbook;
	/** Runes in the rune pouch, item id to count (the plugin reads them from the game). */
	public Map<String, Integer> pouch = new LinkedHashMap<>();
	/** The rune pouch is in the bank, not carried. */
	public boolean pouchInBank;
	/**
	 * The home and minigame teleports were just used: both cooldowns are running, so their teleports are left out
	 * (core {@link Cooldown}). False (the default) = ready.
	 */
	public boolean cooldownsUsed;
	/** Built in (Maxed, Fresh account): shown but not saved or edited. */
	public transient boolean builtIn;

	public static TestPlayer maxed()
	{
		TestPlayer p = new TestPlayer();
		p.name = "Maxed";
		p.allUnlocked = true;
		p.allItems = true;
		for (Skill s : Skill.values())
		{
			p.levels.put(s.getLabel(), 99);
		}
		p.builtIn = true;
		return p;
	}

	public static TestPlayer fresh()
	{
		TestPlayer p = new TestPlayer();
		p.name = "Fresh account";
		p.builtIn = true;
		return p;
	}

	/** The player state requirements are checked against; throws IllegalArgumentException on a bad entry. */
	public PlayerState toState()
	{
		PlayerState.Builder b = PlayerStateBuilder.allItems(new PlayerState.Builder(name).members(members).allUnlocked(allUnlocked), allItems);
		levels.forEach((k, v) ->
		{
			Skill s = Skill.byName(k);
			if (s == null)
			{
				throw new IllegalArgumentException("unknown skill '" + k + "'");
			}
			b.level(s, v);
		});
		quests.forEach((k, v) -> b.quest(k, questState(v)));
		varbits.forEach((k, v) -> b.varbit(number(k), v));
		varps.forEach((k, v) -> b.varp(number(k), v));
		inventory.forEach((k, v) -> b.carry(number(k), v));
		worn.forEach((k, v) ->
		{
			EquipSlot s = EquipSlot.byName(k);
			if (s == null)
			{
				throw new IllegalArgumentException("unknown slot '" + k + "'");
			}
			b.wear(s, v);
		});
		bank.forEach((k, v) -> b.bank(number(k), v));
		answers.forEach(a -> b.answer(a, true));
		b.house(house);
		houseThings.forEach(b::houseThing);
		if (spellbook != null && !spellbook.isEmpty())
		{
			com.bestpath.core.req.Spellbook sb = com.bestpath.core.req.Spellbook.byName(spellbook);
			if (sb == null)
			{
				throw new IllegalArgumentException("unknown spellbook '" + spellbook + "'");
			}
			b.spellbook(sb);
		}
		pouch.forEach((k, v) -> b.pouch(number(k), v));
		b.pouchInBank(pouchInBank);
		if (cooldownsUsed)
		{
			for (Cooldown c : Cooldown.values())
			{
				b.coolingDown(c, 0); // when it is ready again is not known (nor needed) for a test player
			}
		}
		return b.build();
	}

	static QuestState questState(String s)
	{
		switch (s.trim().toLowerCase(Locale.ROOT))
		{
			case "done":
				return QuestState.DONE;
			case "started":
				return QuestState.STARTED;
			case "not started":
				return QuestState.NOT_STARTED;
			default:
				throw new IllegalArgumentException("quest state must be done, started or not started: '" + s + "'");
		}
	}

	private static int number(String s)
	{
		try
		{
			return Integer.parseInt(s.trim());
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException("not a number: '" + s + "'");
		}
	}

	public TestPlayer copy()
	{
		TestPlayer p = new TestPlayer();
		p.name = name;
		p.members = members;
		p.allUnlocked = allUnlocked;
		p.allItems = allItems;
		p.levels = new LinkedHashMap<>(levels);
		p.quests = new LinkedHashMap<>(quests);
		p.varbits = new LinkedHashMap<>(varbits);
		p.varps = new LinkedHashMap<>(varps);
		p.inventory = new LinkedHashMap<>(inventory);
		p.worn = new LinkedHashMap<>(worn);
		p.bank = new LinkedHashMap<>(bank);
		p.answers = new ArrayList<>(answers);
		p.house = house;
		p.houseThings = new ArrayList<>(houseThings);
		p.spellbook = spellbook;
		p.pouch = new LinkedHashMap<>(pouch);
		p.pouchInBank = pouchInBank;
		p.cooldownsUsed = cooldownsUsed;
		return p;
	}

	@Override
	public String toString()
	{
		return name;
	}
}

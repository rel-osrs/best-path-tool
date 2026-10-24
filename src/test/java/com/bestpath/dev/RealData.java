package com.bestpath.dev;

import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Skill;
import com.bestpath.core.req.Spellbook;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.data.MapDataLoader;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.RouteEngine;
import com.bestpath.route.RouteRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DEV ONLY: the UI preview with the REAL exported map data
 * ({@code gradlew uiPreview -PrealData}, {@code gradlew uiShot -PrealData}):
 * the plugin's own loader and engine, and the Data Tool's test players
 * (the built-in Maxed and Fresh account, plus your own players from
 * data/manual/test-players.json when that file exists), standing in Lumbridge.
 * Real in-game player state is not used here.
 */
final class RealData
{
	/** Where the preview's players stand (Lumbridge Castle courtyard), changeable in the window. */
	static final int[] START = {3222, 3218, 0};
	/** The sample destination: Varrock sewers by the manhole's ladder (the fake route's 3233, 9860 is not walkable). */
	static final RouteRequest DESTINATION = new RouteRequest(3236, 9862, 0);

	/** Al Kharid bank: from Lumbridge the route crosses the toll gate (players {@link #TOLL_COINS}, {@link #TOLL_QUEST}). */
	static final RouteRequest AL_KHARID_BANK = new RouteRequest(3269, 3167, 0);
	/** Varrock's square: from Lumbridge the {@link #BANK_PLAYER} route begins at the bank (runes for Varrock Teleport). */
	static final RouteRequest VARROCK_SQUARE = new RouteRequest(3213, 3424, 0);
	static final String TOLL_COINS = "Free-to-play, 1000 coins";
	/**
	 * The Data Tool export check's player with a bank (a route may begin at a bank): Magic 45, Agility
	 * 55, carries nothing; law, air, water, earth and fire runes, a million coins, a games necklace, a ring of dueling
	 * and an amulet of glory in the bank.
	 */
	static final String BANK_PLAYER = "Mid-level with a bank (runes, coins, jewellery in the bank)";
	static final String TOLL_QUEST = "Free-to-play, Prince Ali Rescue done";
	static final String MID_LEVEL = "Mid-level (Magic 45, Agility 55)";
	/** The mid-level player who has just used the home and minigame teleports (both cooling down). */
	static final String COOLDOWNS_USED = "Mid-level, home and minigame teleports used";
	/**
	 * Fake boats for the preview (the uiCheck sailing cases' boat, as a sloop): a teak sloop with oak sails moored
	 * at Port Sarim (dock 0), no teleport focus.
	 */
	static final List<Boat> TEST_BOATS = List.of(new Boat(1, BoatType.SLOOP, 2, 1, 0, 0, false, false, Boat.Focus.NONE, 0));

	private static RouteEngine engine;

	private RealData()
	{
	}

	/** The plugin's engine on the exported data (loaded once; "No map data yet" when there is none). */
	static synchronized RouteEngine engine()
	{
		if (engine == null)
		{
			long t0 = System.nanoTime();
			engine = MapDataLoader.engine();
			System.out.printf(Locale.ROOT, "Real map data: %s (%d ms)%n", engine.getClass().getSimpleName(),
				(System.nanoTime() - t0) / 1_000_000);
		}
		return engine;
	}

	/** Test players by name: Maxed, Fresh account, then your own ones (if the file exists). */
	static Map<String, PlayerSnapshot> players()
	{
		Map<String, PlayerSnapshot> m = new LinkedHashMap<>();
		m.put("Maxed (test player)", at(PlayerStateBuilder.maxed()));
		m.put("Fresh account (test player)", at(PlayerStateBuilder.fresh()));
		m.put(MID_LEVEL, at(new PlayerState.Builder("Mid-level").members(true)
			.level(Skill.MAGIC, 45).level(Skill.AGILITY, 55).build()));
		// home and minigame teleports only while ready; this one has just used both
		m.put(COOLDOWNS_USED, at(new PlayerState.Builder("Mid-level, cooldowns used").members(true)
			.level(Skill.MAGIC, 45).level(Skill.AGILITY, 55)
			.coolingDown(Cooldown.HOME, 0).coolingDown(Cooldown.MINIGAME, 0).build()));
		m.put("Free-to-play, Magic 30", at(new PlayerState.Builder("F2P").members(false).level(Skill.MAGIC, 30).build()));
		// the Al Kharid toll gate's two ways through: 10 coins, or Prince Ali Rescue done
		m.put(TOLL_COINS, at(new PlayerState.Builder("Toll coins").members(false).carry(995, 1000).build()));
		m.put(BANK_PLAYER, at(new PlayerState.Builder("Banker").members(true).level(Skill.MAGIC, 45).level(Skill.AGILITY, 55)
			.bank(563, 200).bank(556, 1000).bank(555, 500).bank(557, 500).bank(554, 500).bank(PlayerState.COINS, 1_000_000)
			.bank(3853, 1).bank(2552, 1).bank(1712, 1).build()));
		m.put(TOLL_QUEST, at(new PlayerState.Builder("Toll quest").members(false)
			.quest("Prince Ali Rescue", QuestState.DONE).build()));
		// fake boats (reading the player's boats from the game is part 2): routes may sail
		m.put("Maxed + test sloop at Port Sarim", at(PlayerStateBuilder.maxed(), TEST_BOATS));
		// the test sloop capsized (a shipwright brings it), or bottled with its bottle carried
		m.put("Maxed + test sloop capsized", at(PlayerStateBuilder.maxed(), List.of(new Boat(1, BoatType.SLOOP, 2, 1, 0, 0, false,
			false, Boat.Focus.NONE, Boat.CAPSIZED))));
		PlayerState.Builder bottle = PlayerStateBuilder.allItems(new PlayerState.Builder("Maxed + boat bottle").members(true).allUnlocked(true), true)
			.carry(Boat.BOTTLE_FULL, 1);
		for (Skill s : Skill.values())
		{
			bottle.level(s, 99);
		}
		m.put("Maxed + test sloop bottled, bottle carried", at(bottle.build(), List.of(new Boat(1, BoatType.SLOOP, 2, 1, 0, 0,
			false, false, Boat.Focus.NONE, Boat.BOTTLED))));
		File file = new File("data/manual/test-players.json");
		if (file.isFile())
		{
			try
			{
				for (Saved s : load(file))
				{
					m.put(s.name + " (test player)", at(s.toState()));
				}
			}
			catch (IOException | RuntimeException e)
			{
				System.out.println("Could not read " + file + ": " + e.getMessage());
			}
		}
		return m;
	}

	static PlayerSnapshot at(PlayerState s)
	{
		return at(s, List.of());
	}

	static PlayerSnapshot at(PlayerState s, List<Boat> boats)
	{
		return new PlayerSnapshot(START[0], START[1], START[2], s, boats);
	}

	/** A test player as the Data Tool saves it (data/manual/test-players.json; same fields as its TestPlayer). */
	static final class Saved
	{
		String name = "Test player";
		boolean members = true;
		boolean allUnlocked;
		boolean allItems;
		Map<String, Integer> levels = new LinkedHashMap<>();
		Map<String, String> quests = new LinkedHashMap<>();
		Map<String, Integer> varbits = new LinkedHashMap<>();
		Map<String, Integer> varps = new LinkedHashMap<>();
		Map<String, Integer> inventory = new LinkedHashMap<>();
		Map<String, Integer> worn = new LinkedHashMap<>();
		Map<String, Integer> bank = new LinkedHashMap<>();
		List<String> answers = new ArrayList<>();
		String house;
		List<String> houseThings = new ArrayList<>();
		String spellbook;
		Map<String, Integer> pouch = new LinkedHashMap<>();
		boolean pouchInBank;
		boolean cooldownsUsed;

		PlayerState toState()
		{
			PlayerState.Builder b = PlayerStateBuilder.allItems(new PlayerState.Builder(name).members(members).allUnlocked(allUnlocked), allItems);
			levels.forEach((k, v) -> b.level(require(Skill.byName(k), "skill " + k), v));
			quests.forEach((k, v) -> b.quest(k, "done".equalsIgnoreCase(v.trim()) ? QuestState.DONE
				: "started".equalsIgnoreCase(v.trim()) ? QuestState.STARTED : QuestState.NOT_STARTED));
			varbits.forEach((k, v) -> b.varbit(Integer.parseInt(k.trim()), v));
			varps.forEach((k, v) -> b.varp(Integer.parseInt(k.trim()), v));
			inventory.forEach((k, v) -> b.carry(Integer.parseInt(k.trim()), v));
			worn.forEach((k, v) -> b.wear(require(EquipSlot.byName(k), "slot " + k), v));
			bank.forEach((k, v) -> b.bank(Integer.parseInt(k.trim()), v));
			answers.forEach(a -> b.answer(a, true));
			b.house(house);
			houseThings.forEach(b::houseThing);
			if (spellbook != null && !spellbook.isEmpty())
			{
				b.spellbook(require(Spellbook.byName(spellbook), "spellbook " + spellbook));
			}
			pouch.forEach((k, v) -> b.pouch(Integer.parseInt(k.trim()), v));
			b.pouchInBank(pouchInBank);
			if (cooldownsUsed)
			{
				for (Cooldown c : Cooldown.values())
				{
					b.coolingDown(c, 0);
				}
			}
			return b.build();
		}

		private static <T> T require(T v, String what)
		{
			if (v == null)
			{
				throw new IllegalArgumentException("unknown " + what);
			}
			return v;
		}
	}

	private static List<Saved> load(File file) throws IOException
	{
		List<Saved> list = new Gson().fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8),
			new TypeToken<List<Saved>>()
			{
			}.getType());
		return list == null ? List.of() : list;
	}
}

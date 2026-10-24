package com.bestpath.tool.clue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The clue-table reading on a small made-up cache (not the real one). */
class ClueExtractorTest
{
	/** The game's packed tile. */
	private static int pack(int x, int y, int plane)
	{
		return plane << 28 | x << 14 | y;
	}

	private static Object[] v(Object... values)
	{
		return values;
	}

	/** A tiny cache: a few clue rows, their targets and requirements. */
	private static final class Fixture implements ClueExtractor.Source
	{
		final Map<Integer, Map<Integer, Object[][]>> tables = new HashMap<>();
		final Map<Integer, List<Integer>> items = new HashMap<>();

		void put(int table, int row, Object[]... cols)
		{
			tables.computeIfAbsent(table, k -> new TreeMap<>()).put(row, cols);
		}

		@Override
		public Map<Integer, Object[][]> rows(int table)
		{
			return tables.getOrDefault(table, new TreeMap<>());
		}

		@Override
		public Object[][] row(int rowId)
		{
			for (Map<Integer, Object[][]> t : tables.values())
			{
				if (t.containsKey(rowId))
				{
					return t.get(rowId);
				}
			}
			return null;
		}

		@Override
		public Map<Integer, List<Integer>> itemsByRow()
		{
			return items;
		}

		@Override
		public String npcName(int id)
		{
			return id == 50 ? "Hans" : id == 51 ? "Hans (later)" : id == 60 ? "Guard" : id == 70 ? "Ankou" : null;
		}

		@Override
		public String objectName(int id)
		{
			return id == 900 ? "Crate" : id == 901 ? "STASH (easy)" : id == 902 ? "Closed chest" : null;
		}

		@Override
		public String itemName(int id)
		{
			return id == 1 ? "Gold ring" : id == 2 ? "Ring of shadows" : id == 3 ? "Key (medium)" : id == 4 ? "Spade" : null;
		}

		@Override
		public Map<Integer, List<Integer>> itemsByParam(int param)
		{
			// trail item group 7: two god books
			return param == ClueExtractor.PARAM_TRAIL_GROUP ? Map.of(7, List.of(3839, 3841)) : Map.of();
		}

		@Override
		public int objectVarbit(int objectId)
		{
			return objectId == 901 ? 5213 : -1;
		}

		@Override
		public int[] varbit(int id)
		{
			return id == 5213 ? new int[]{1365, 17, 17} : null; // bit 17 of the easy STASH varp
		}

		@Override
		public Map<Integer, Integer> enumInts(int id)
		{
			return id == ClueExtractor.ENUM_STASH_BITS ? Map.of(901, 1 << 14 | 17) : Map.of(); // easy (1), bit 17
		}

		@Override
		public Map<Integer, String> enumStrings(int id)
		{
			if (id == ClueExtractor.ENUM_EMOTE_NAMES)
			{
				return Map.of(20, "Clap", 14, "Spin");
			}
			return id == ClueExtractor.ENUM_STASH_NAMES ? Map.of(901, "Near the tree") : Map.of();
		}
	}

	private static Fixture fixture()
	{
		Fixture f = new Fixture();
		// targets
		f.put(ClueTables.TARGET_NPC, 100, v(50), null, v(pack(3221, 3218, 0)), v("Hans, in Lumbridge Castle."));
		f.put(ClueTables.TARGET_NPC, 101, v(51), null, v(pack(3200, 3200, 1)), v("Hans, after the quest."));
		f.put(ClueTables.TARGET_COORD, 110, v(pack(3000, 3300, 0)), v("Dig by the tree."), v("Falador"));
		f.put(ClueTables.TARGET_KEY, 120, v(902), v(pack(2600, 3300, 1)), v(60), v(3), v(pack(2610, 3310, 0)), null, null,
			v("Kill a guard for the key."));
		f.put(ClueTables.TARGET_KILL, 130, v(70), null, v("Kill an Ankou."));
		// requirements and an outfit
		f.put(ClueTables.REQ_STAT, 200, v(16), v(60)); // stat 16 = Agility
		f.put(ClueTables.REQ_OBJ, 201, v("A spade"), v(4), null, v(2));
		f.put(ClueTables.OUTFIT, 210, v("Equip a gold ring."), null, null, null, null, null, null, null, null, null, v(1, 2), null);
		// a book in the left hand: item 3840 or any of trail item group 7
		f.put(ClueTables.OUTFIT, 211, v("Equip a god book."), null, null, null, null, null, v(3840), null, null, null, null, null,
			null, null, null, null, null, v(7), null, null, null, null, null, null);
		f.put(ClueTables.REQ_OBJ_PARAM, 202, v("Any God Book"), v(7));
		f.put(ClueTables.EMOTE, 1005, v(6), v(2), v("Clap then spin."), v(110), v(20, 14), null, v(211), null, null, null, v(202), v(1),
			null, null);
		// clue rows: id, difficulty, ...
		f.put(ClueTables.ANAGRAM, 1000, v(1), v(0), v("A Hns"), v(100, 101), null, v(1), null, null, null);
		f.put(ClueTables.CRYPTIC, 1001, v(2), v(2), v("Search the chest."), v(120), v(200), null, null, null, null, null, v(1), null, null);
		f.put(ClueTables.CRYPTIC, 1002, v(3), v(4), v("Ghostly bones."), v(130), null, null, null, null, null, null, v(1), null, null);
		// emote: id, diff, text, target, emote, outfitText, outfit, stashLoc, stashCoord, encounter, reqs, region, all, restricted
		f.put(ClueTables.EMOTE, 1003, v(4), v(1), v("Clap by the tree.<br>Equip a gold ring."), v(110), v(20), null, v(210), v(901),
			v(pack(3002, 3301, 0)), null, v(201), v(1), null, v(1));
		f.put(ClueTables.MAP, 1004, v(5), v(9), v(110), null, v(1), null, null);
		f.items.put(1001, List.of(2801, 2802));
		return f;
	}

	private static ClueStep step(List<ClueStep> all, String key)
	{
		return all.stream().filter(s -> s.key.equals(key)).findFirst().orElseThrow();
	}

	@Test
	void readsEveryClueRowWithItsTarget()
	{
		List<ClueStep> all = new ClueExtractor(fixture(), "cache 1").extract();
		assertEquals(6, all.size());
		ClueStep a = step(all, "anagram-1000");
		assertEquals("anagram", a.type);
		assertEquals("beginner", a.tier);
		assertEquals("npc", a.target);
		assertEquals("Hans", a.targetName);
		assertEquals(3221, a.x);
		assertEquals(3218, a.y);
		assertEquals(0, a.plane);
		assertEquals("cache 1 table 4 row 1000 -> table 15 row 100 / table 15 row 101", a.source);
		// the second target row is an alternative, not dropped
		assertEquals(1, a.places.size());
		assertEquals("alt-target", a.places.get(0).role);
		assertEquals(3200, a.places.get(0).x);
		assertEquals(1, a.places.get(0).plane);
		assertTrue(a.checks.isEmpty(), a.checks.toString());
	}

	@Test
	void keyStepGivesTheChestTheKeyAndWhereItDrops()
	{
		ClueStep c = step(new ClueExtractor(fixture(), "cache 1").extract(), "cryptic-1001");
		assertEquals("medium", c.tier);
		assertEquals("key", c.target);
		assertEquals("Closed chest", c.targetName);
		assertEquals(1, c.plane);
		assertEquals(List.of(2801, 2802), c.items);
		assertTrue(c.needs.contains("item 3 Key (medium) (the key)"), c.needs.toString());
		assertTrue(c.needs.contains("Agility 60"), c.needs.toString());
		ClueStep.Place p = c.places.get(0);
		assertEquals("key-npc", p.role);
		assertEquals(List.of(60), p.ids);
		assertEquals(2610, p.x);
	}

	@Test
	void killStepHasNoTileAndSaysSo()
	{
		ClueStep k = step(new ClueExtractor(fixture(), "cache 1").extract(), "cryptic-1002");
		assertEquals("elite", k.tier);
		assertEquals("kill", k.target);
		assertFalse(k.hasTile());
		assertEquals(List.of(70), k.targetIds);
		assertTrue(k.checks.get(0).startsWith("kill step"), k.checks.toString());
	}

	@Test
	void emoteStepHasStashOutfitAndRequirements()
	{
		ClueStep e = step(new ClueExtractor(fixture(), "cache 1").extract(), "emote-1003");
		assertEquals("easy", e.tier);
		assertEquals("Clap by the tree. Equip a gold ring.", e.text); // <br> becomes a space
		assertEquals("coord", e.target);
		assertEquals(3000, e.x);
		ClueStep.Place st = e.places.get(0);
		assertEquals("stash", st.role);
		assertEquals("STASH (easy)", st.name);
		assertEquals(3002, st.x);
		assertEquals(3301, st.y);
		// two items in one slot are alternatives
		assertTrue(e.needs.contains("wear one of 1 Gold ring / 2 Ring of shadows (ring)"), e.needs.toString());
		assertTrue(e.needs.contains("item 4 x2 (A spade)"), e.needs.toString());
		assertTrue(e.notes.contains("emote 20"), e.notes.toString());
		assertTrue(e.notes.contains("area: Falador"), e.notes.toString());
		assertTrue(e.checks.get(0).contains("restricted content"), e.checks.toString());
	}

	@Test
	void actionsAreTheGamesClueHelperWords()
	{
		List<ClueStep> all = new ClueExtractor(fixture(), "cache 1").extract();
		assertEquals("talk", step(all, "anagram-1000").action);
		assertEquals("key", step(all, "cryptic-1001").action);
		assertEquals("kill", step(all, "cryptic-1002").action);
		assertEquals("emote 20 Clap", step(all, "emote-1003").action);
		assertEquals("emote 20 Clap | 14 Spin", step(all, "emote-1005").action, "every emote, in order, with its name");
		assertEquals("dig", step(all, "map-1004").action, "a map to a tile is dug (script 6651)");
	}

	@Test
	void needsBecomeARequirementLine()
	{
		List<ClueStep> all = new ClueExtractor(fixture(), "cache 1").extract();
		// outfit slot alternatives worn, the item to bring with its count
		assertEquals("item 1/2 worn; item 4 x2", step(all, "emote-1003").requirement);
		// a slot's item and its trail item group are one choice; table 22's group is an item to bring
		assertEquals("item 3840/3839/3841 worn; item 3839/3841", step(all, "emote-1005").requirement);
		// the key and a skill level
		assertEquals("item 3; Agility 60", step(all, "cryptic-1001").requirement);
		assertEquals("", step(all, "cryptic-1002").requirement);
	}

	@Test
	void stashUnitStateFromItsVarbitAndTheGamesEnums()
	{
		List<ClueStep> all = new ClueExtractor(fixture(), "cache 1").extract();
		ClueStep e = step(all, "emote-1003");
		assertEquals("varbit 5213 ; easy ; Near the tree", e.stash);
		assertEquals(5213, e.stashVarbit());
		assertEquals("Near the tree", e.stashPlace());
		assertFalse(e.checks.stream().anyMatch(c -> c.startsWith("STASH")), e.checks.toString());
		assertEquals("", step(all, "emote-1005").stash, "no STASH unit");
	}

	@Test
	void stashVarbitThatIsNotTheEnumsBitIsFlagged()
	{
		Fixture f = fixture();
		ClueExtractor.Source wrong = new ClueExtractor.Source()
		{
			@Override
			public Map<Integer, Object[][]> rows(int table)
			{
				return f.rows(table);
			}

			@Override
			public Object[][] row(int rowId)
			{
				return f.row(rowId);
			}

			@Override
			public Map<Integer, List<Integer>> itemsByRow()
			{
				return f.itemsByRow();
			}

			@Override
			public String npcName(int id)
			{
				return f.npcName(id);
			}

			@Override
			public String objectName(int id)
			{
				return f.objectName(id);
			}

			@Override
			public String itemName(int id)
			{
				return f.itemName(id);
			}

			@Override
			public int objectVarbit(int objectId)
			{
				return f.objectVarbit(objectId);
			}

			@Override
			public int[] varbit(int id)
			{
				return new int[]{1366, 17, 17}; // the medium varp: not what enum 1368 says
			}

			@Override
			public Map<Integer, Integer> enumInts(int id)
			{
				return f.enumInts(id);
			}

			@Override
			public Map<Integer, String> enumStrings(int id)
			{
				return f.enumStrings(id);
			}
		};
		ClueStep e = step(new ClueExtractor(wrong, "cache 1").extract(), "emote-1003");
		assertTrue(e.checks.stream().anyMatch(c -> c.startsWith("STASH unit 901: its varbit 5213")), e.checks.toString());
	}

	@Test
	void unknownDifficultyIsFlaggedNotGuessed()
	{
		ClueStep m = step(new ClueExtractor(fixture(), "cache 1").extract(), "map-1004");
		assertEquals("difficulty 9", m.tier);
		assertTrue(m.checks.contains("unknown difficulty 9"), m.checks.toString());
	}

	@Test
	void unpacksTheGamesTiles()
	{
		int[] t = ClueExtractor.unpack(pack(3164, 9913, 2));
		assertEquals(3164, t[0]);
		assertEquals(9913, t[1]);
		assertEquals(2, t[2]);
		// a real value from the cache: Ranael in Al Kharid
		int[] r = ClueExtractor.unpack(54316123);
		assertEquals(3315, r[0]);
		assertEquals(3163, r[1]);
		assertEquals(0, r[2]);
	}
}

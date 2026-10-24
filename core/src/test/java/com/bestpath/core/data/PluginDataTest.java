package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.CollisionMapWriter;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.DoorMapBuilder;
import com.bestpath.core.path.DoorMapDiagnostics;
import com.bestpath.core.path.LineOfSight;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.Skill;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The plugin export: every part reads back what was written, the walls and doors give the
 * same answers as the raw flags they were made from, the per-player transport rules, and dropping the search's
 * caches leaves routes the same. Small hand-made worlds; the real data is checked by {@code exportPlugin} itself.
 */
class PluginDataTest
{
	/** Two regions side by side (x 3200-3327, y 3200-3263), planes 0 and 1, with some walls, blocked tiles and doors. */
	private static final class World
	{
		final Map<Long, Integer> flags = new HashMap<>();
		final List<int[]> doors = new ArrayList<>();

		World()
		{
			Random rnd = new Random(3);
			for (int i = 0; i < 400; i++)
			{
				int x = 3200 + rnd.nextInt(128);
				int y = 3200 + rnd.nextInt(64);
				int p = rnd.nextInt(2);
				int[] walls = {CollisionFlags.NORTH, CollisionFlags.EAST, CollisionFlags.SOUTH, CollisionFlags.WEST, CollisionFlags.BLOCKED};
				add(x, y, p, walls[rnd.nextInt(walls.length)]);
			}
			// a wall with a door in it, on the border between the two regions (x 3263 | 3264)
			for (int y = 3220; y < 3230; y++)
			{
				if (y != 3225)
				{
					add(3263, y, 0, CollisionFlags.EAST);
					add(3264, y, 0, CollisionFlags.WEST);
				}
			}
			doors.add(new int[]{3263, 3225, 0});
		}

		void add(int x, int y, int p, int f)
		{
			flags.merge(key(x, y, p), f, (a, b) -> a | b);
		}

		static long key(int x, int y, int p)
		{
			return (long) x << 20 | (long) y << 2 | p;
		}

		int get(int x, int y, int p)
		{
			if (x < 3200 || x >= 3328 || y < 3200 || y >= 3264 || p > 1)
			{
				return CollisionFlags.BLOCKED;
			}
			return flags.getOrDefault(key(x, y, p), 0);
		}

		MovementRules.Flags plane(int p, boolean doorsShut)
		{
			return (x, y) ->
			{
				int f = get(x, y, p);
				if (doorsShut && p == 0 && x == 3263 && y == 3225)
				{
					f |= CollisionFlags.EAST;
				}
				if (doorsShut && p == 0 && x == 3264 && y == 3225)
				{
					f |= CollisionFlags.WEST;
				}
				return f;
			};
		}

		CollisionMap map()
		{
			return CollisionMapBuilder.build(new MovementRules.Flags[]{plane(0, false), plane(1, false), plane(2, false), plane(3, false)},
				List.of(50 << 8 | 50, 51 << 8 | 50), 7, 9);
		}

		DoorMap doorMap()
		{
			MovementRules.Flags[] open = new MovementRules.Flags[4];
			MovementRules.Flags[] shut = new MovementRules.Flags[4];
			for (int p = 0; p < 4; p++)
			{
				open[p] = plane(p, false);
				shut[p] = plane(p, true);
			}
			return DoorMapBuilder.build(open, shut, doors);
		}
	}

	private static TransportLine line(String id, TransportLine.Kind kind, String name, int[] start, int[] end, int ticks,
		String network, String req, String back, String teleportCategory, Integer wilderness, boolean questOnly)
	{
		return TransportLine.builder(id, kind).name(name).category("CUSTOM").start(start[0], start[1], start[2])
			.end(end[0], end[1], end[2]).ticks(ticks).ticksEstimate(true).questOnly(questOnly).network(network)
			.requirements(req).requirementsBack(back).teleportCategory(teleportCategory).wildernessLimit(wilderness)
			.build();
	}

	@Test
	void transportLinesReadBackTheSame()
	{
		TransportLine a = TransportLine.builder("t-1", TransportLine.Kind.TWO_WAY).name("Ladder")
			.category("CLIMB_BOTH").start(3200, 3201, 0).end(3200, 3201, 1).ticks(3).ticksEstimate(true)
			.canFail(true).cooldown(true).network("Fairy ring").requirements("Agility 50; item 772")
			.requirementsBack("").wildernessLimit(20).object(16683, 3200, 3202, 0)
			.playerNote("No weapons or armour").build();
		TransportLine b = TransportLine.parse(TransportLineWriter.write(a));
		assertEquals(TransportLineWriter.write(a), TransportLineWriter.write(b));
		assertEquals("", b.requirementsBack); // set to "nothing" (not the same as there)
		assertEquals("", b.requirementsEndToStart());
		assertEquals(20, b.getWildernessLimit());
		assertEquals(16683, b.getObjectId());
		assertTrue(b.isCanFail() && b.isCooldown() && b.ticksEstimate && !b.isQuestOnly());

		TransportLine t = line("t-2", TransportLine.Kind.TELEPORT, "Tele\twith tab", new int[3], new int[]{3212, 3424, 0}, 5,
			"", "Magic 25", null, "spell", null, false);
		TransportLine u = TransportLine.parse(TransportLineWriter.write(t));
		assertNull(u.requirementsBack);
		assertEquals("Magic 25", u.requirementsEndToStart());
		assertNull(u.getWildernessLimit());
		assertEquals(-1, u.getObjectId());
		assertEquals("Tele with tab", u.getName());
		assertFalse(u.hasStart());
		assertThrows(IllegalArgumentException.class, () -> TransportLine.parse("too\tfew"));
	}

	@Test
	void wallBitsSeeWhatTheRawFlagsSee() throws IOException
	{
		World w = new World();
		LineOfSight.WallFlags raw = w::get;
		WallBits bits = WallBitsBuilder.fromFlags(raw, List.of((50 << 8 | 50) << 2, (51 << 8 | 50) << 2, (50 << 8 | 50) << 2 | 1,
			(51 << 8 | 50) << 2 | 1));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		WallBitsWriter.write(bits, out);
		WallBits back = WallBits.read(new ByteArrayInputStream(out.toByteArray()));
		assertArrayEquals(bits.keys.clone(), back.keys.clone());
		Random rnd = new Random(5);
		for (int i = 0; i < 20_000; i++)
		{
			int p = rnd.nextInt(2);
			int x0 = 3201 + rnd.nextInt(126);
			int y0 = 3201 + rnd.nextInt(62);
			int x1 = Math.max(3201, Math.min(3326, x0 + rnd.nextInt(21) - 10));
			int y1 = Math.max(3201, Math.min(3262, y0 + rnd.nextInt(21) - 10));
			assertEquals(LineOfSight.clear(raw, p, x0, y0, x1, y1), LineOfSight.clear(back, p, x0, y0, x1, y1),
				x0 + "," + y0 + " -> " + x1 + "," + y1 + " plane " + p);
		}
	}

	@Test
	void doorBitsReadBackAsTheSameSteps() throws IOException
	{
		World w = new World();
		DoorMap doors = w.doorMap();
		assertFalse(doors.isEmpty());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int n = DoorBitsWriter.write(doors, new int[]{(50 << 8 | 50) << 2, (51 << 8 | 50) << 2}, out);
		assertTrue(n > 0);
		DoorMap back = DoorBits.read(new ByteArrayInputStream(out.toByteArray()));
		for (int x = 3200; x < 3328; x++)
		{
			for (int y = 3200; y < 3264; y++)
			{
				assertEquals(doors.bits(x, y, 0), back.bits(x, y, 0), x + "," + y);
			}
		}
		assertEquals(DoorMapDiagnostics.cellKeys(doors), DoorMapDiagnostics.cellKeys(back));
		assertArrayEquals(doors.cellBits((51 << 8 | 50) << 2), back.cellBits((51 << 8 | 50) << 2));
		assertTrue(back.bits(3263, 3225, 0) != 0 && back.bits(3264, 3225, 0) != 0);
	}

	/** Every data file of a small export of the test world (no manifest yet). */
	private static Map<String, byte[]> dataFiles(World w, CollisionMap map, TransportLine t) throws IOException
	{
		Map<String, byte[]> files = new LinkedHashMap<>();
		ByteArrayOutputStream mv = new ByteArrayOutputStream();
		CollisionMapWriter.write(map, mv);
		files.put(PluginData.MOVEMENT, mv.toByteArray());
		ByteArrayOutputStream dr = new ByteArrayOutputStream();
		DoorBitsWriter.write(w.doorMap(), map.cellKeys(), dr);
		files.put(PluginData.DOORS, dr.toByteArray());
		ByteArrayOutputStream wl = new ByteArrayOutputStream();
		WallBitsWriter.write(WallBitsBuilder.fromFlags(w::get, List.of((50 << 8 | 50) << 2)), wl);
		files.put(PluginData.WALLS, wl.toByteArray());
		files.put(PluginData.TRANSPORTS, ("#comment\n" + TransportLine.HEADER + "\n" + TransportLineWriter.write(t) + "\n").getBytes(StandardCharsets.UTF_8));
		files.put(PluginData.ITEM_GROUPS, "# c\naxe\t1351,1349\tsrc\tnames\n".getBytes(StandardCharsets.UTF_8));
		files.put(PluginData.RUNES, "air\t556\t1381\tsrc\tnames\n".getBytes(StandardCharsets.UTF_8));
		files.put(PluginData.NETWORKS, "Fairy ring\t3\titem 772\tdesc\n".getBytes(StandardCharsets.UTF_8));
		files.put(PluginData.TELEPORT_CATEGORIES, "spell\t4\tdesc\n".getBytes(StandardCharsets.UTF_8));
		files.put(PluginData.FREE_LAND, "# c\n3210 3200-3220\n".getBytes(StandardCharsets.UTF_8));
		return files;
	}

	@Test
	void pluginDataReadsBackAndNoticesChanges() throws IOException
	{
		World w = new World();
		CollisionMap map = w.map();
		TransportLine t = line("t-1", TransportLine.Kind.TELEPORT, "Tele", new int[3], new int[]{3210, 3210, 0}, 5, "", "", null, "",
			null, false);
		Map<String, byte[]> files = dataFiles(w, map, t);
		files.put(PluginData.MANIFEST, PluginDataWriter.write(files, 7, 9, "2026-10-14T00:00:00Z", Map.of("transports", "1")));
		assertEquals(0, PluginData.read(name -> files.containsKey(name) ? new ByteArrayInputStream(files.get(name)) : null)
			.getClues().size(), "an export without clues.tsv reads as before, with no clue steps");

		PluginData d = PluginData.read(name -> files.containsKey(name) ? new ByteArrayInputStream(files.get(name)) : null);
		assertEquals(map.size(), d.getMap().size());
		assertEquals("7\t9", d.manifest.get("cache"));
		assertEquals("1", d.manifest.get("count transports"));
		assertEquals(PluginData.fingerprint(files), d.getFingerprint());
		assertEquals(1, d.getTransports().size());
		assertEquals(TransportLineWriter.write(t), TransportLineWriter.write(d.getTransports().get(0)));
		assertArrayEquals(new int[]{1351, 1349}, d.getTables().itemGroups().ids("Axe"));
		assertEquals(3, d.getTables().networkExtraTicks("fairy ring"));
		assertEquals(4, d.getTables().teleportCategoryTicks("Spell"));
		assertTrue(d.getFreeLand().isFree(3215, 3210));
		assertFalse(d.getFreeLand().isFree(3215, 3211));
		assertTrue(d.getDoors().bits(3263, 3225, 0) != 0);

		assertNull(PluginData.read(name -> null), "no manifest: no data");
		Map<String, byte[]> changed = new LinkedHashMap<>(files);
		byte[] tr = files.get(PluginData.TRANSPORTS).clone();
		tr[tr.length - 2] ^= 1;
		changed.put(PluginData.TRANSPORTS, tr);
		assertThrows(IOException.class, () -> PluginData.read(name -> changed.containsKey(name) ? new ByteArrayInputStream(changed.get(name)) : null));
		Map<String, byte[]> missing = new LinkedHashMap<>(files);
		missing.remove(PluginData.RUNES);
		assertThrows(IOException.class, () -> PluginData.read(name -> missing.containsKey(name) ? new ByteArrayInputStream(missing.get(name)) : null));
		Map<String, byte[]> other = new LinkedHashMap<>(files);
		other.put(PluginData.MANIFEST, new String(files.get(PluginData.MANIFEST), StandardCharsets.UTF_8)
			.replace("format\t" + PluginData.FORMAT, "format\tv9").getBytes(StandardCharsets.UTF_8));
		assertThrows(IOException.class, () -> PluginData.read(name -> other.containsKey(name) ? new ByteArrayInputStream(other.get(name)) : null));
	}

	@Test
	void clueFileReadsBackAndIsChecked() throws IOException
	{
		World w = new World();
		CollisionMap map = w.map();
		TransportLine t = line("t-1", TransportLine.Kind.TELEPORT, "Tele", new int[3], new int[]{3210, 3210, 0}, 5, "", "", null, "",
			null, false);
		Map<String, byte[]> files = dataFiles(w, map, t);
		String before = PluginData.fingerprint(files);
		ClueData clues = new ClueData(List.of(new ClueData.Step("cryptic-1233", "cryptic", "beginner", "npc", new int[0],
			new int[]{com.bestpath.core.path.Tiles.pack(3221, 3218, 0)}, -1, "Always walking around the castle grounds.",
			"Hans, in Lumbridge.", "", "", "", "", -1, "", -1, "", -1)));
		files.put(PluginData.CLUES, ClueDataWriter.write(clues, "test").getBytes(StandardCharsets.UTF_8));
		assertFalse(before.equals(PluginData.fingerprint(files)), "the fingerprint covers clues.tsv when it is there");
		files.put(PluginData.MANIFEST, PluginDataWriter.write(files, 7, 9, "2026-10-14T00:00:00Z", Map.of()));
		PluginData d = PluginData.read(name -> files.containsKey(name) ? new ByteArrayInputStream(files.get(name)) : null);
		assertEquals(1, d.getClues().size());
		assertEquals(ClueDataWriter.write(clues.getSteps().get(0)), ClueDataWriter.write(d.getClues().getSteps().get(0)));
		assertEquals(PluginData.fingerprint(files), d.getFingerprint());

		Map<String, byte[]> changed = new LinkedHashMap<>(files);
		byte[] cl = files.get(PluginData.CLUES).clone();
		cl[cl.length - 3] ^= 1;
		changed.put(PluginData.CLUES, cl);
		assertThrows(IOException.class, () -> PluginData.read(name -> changed.containsKey(name) ? new ByteArrayInputStream(changed.get(name)) : null));
		Map<String, byte[]> missing = new LinkedHashMap<>(files);
		missing.remove(PluginData.CLUES);
		assertThrows(IOException.class, () -> PluginData.read(name -> missing.containsKey(name) ? new ByteArrayInputStream(missing.get(name)) : null),
			"listed in the manifest but missing");
	}

	@Test
	void itemGroupDefaultsAreOptionalAndRead() throws IOException
	{
		World w = new World();
		CollisionMap map = w.map();
		TransportLine t = line("t-1", TransportLine.Kind.TELEPORT, "Tele", new int[3], new int[]{3210, 3210, 0}, 5, "", "", null, "",
			null, false);
		Map<String, byte[]> files = dataFiles(w, map, t);
		files.put(PluginData.MANIFEST, PluginDataWriter.write(files, 7, 9, "2026-10-14T00:00:00Z", Map.of()));
		PluginData without = PluginData.read(name -> files.containsKey(name) ? new ByteArrayInputStream(files.get(name)) : null);
		assertEquals(-1, without.getTables().itemGroupDefault("axe"), "an export without the file: no defaults");

		String before = PluginData.fingerprint(files);
		// the Data Tool's file as it is: group, item id, chosen by, time, item name
		files.put(PluginData.ITEM_GROUP_DEFAULTS, ("# defaults\naxe\t1349\tRel\t2026-10-13T07:23:17Z\tIron axe\nbad line\n"
			+ "broken\tx\n").getBytes(StandardCharsets.UTF_8));
		assertFalse(before.equals(PluginData.fingerprint(files)), "the fingerprint covers the defaults when they are there");
		files.put(PluginData.MANIFEST, PluginDataWriter.write(files, 7, 9, "2026-10-14T00:00:00Z", Map.of()));
		PluginData d = PluginData.read(name -> files.containsKey(name) ? new ByteArrayInputStream(files.get(name)) : null);
		assertEquals(1349, d.getTables().itemGroupDefault("Axe"), "any case");
		assertEquals(-1, d.getTables().itemGroupDefault("light source"));
		assertEquals(-1, d.getTables().itemGroupDefault(null));
		assertEquals(1, d.getTables().groupDefaults.size(), "bad lines are skipped");
		assertArrayEquals(new int[]{1351, 1349}, d.getTables().itemGroups().ids("axe"), "the group itself is unchanged");

		Map<String, byte[]> changed = new LinkedHashMap<>(files);
		byte[] df = files.get(PluginData.ITEM_GROUP_DEFAULTS).clone();
		df[df.length - 3] ^= 1;
		changed.put(PluginData.ITEM_GROUP_DEFAULTS, df);
		assertThrows(IOException.class, () -> PluginData.read(name -> changed.containsKey(name) ? new ByteArrayInputStream(changed.get(name)) : null));
	}

	@Test
	void boatTableIsOptionalAndRead() throws IOException
	{
		World w = new World();
		CollisionMap map = w.map();
		TransportLine t = line("t-1", TransportLine.Kind.TELEPORT, "Tele", new int[3], new int[]{3210, 3210, 0}, 5, "", "", null, "",
			null, false);
		Map<String, byte[]> files = dataFiles(w, map, t);
		files.put(PluginData.MANIFEST, PluginDataWriter.write(files, 7, 9, "2026-10-14T00:00:00Z", Map.of()));
		PluginData without = PluginData.read(name -> files.containsKey(name) ? new ByteArrayInputStream(files.get(name)) : null);
		assertTrue(without.getBoats().isEmpty(), "an export without boats.tsv: no boat can be read");

		String before = PluginData.fingerprint(files);
		files.put(PluginData.BOATS, BoatTableWriter.write(BoatTable.parse(BoatTableTest.SAMPLE), "boats").getBytes(StandardCharsets.UTF_8));
		assertFalse(before.equals(PluginData.fingerprint(files)), "the fingerprint covers the boat table when it is there");
		files.put(PluginData.MANIFEST, PluginDataWriter.write(files, 7, 9, "2026-10-14T00:00:00Z", Map.of()));
		PluginData d = PluginData.read(name -> files.containsKey(name) ? new ByteArrayInputStream(files.get(name)) : null);
		assertEquals(4, BoatTableDiagnostics.kinds(d.getBoats()).size());
		assertEquals("Port Sarim", d.getBoats().dockName(0));
		assertEquals(2, d.getBoats().kind(2).hull(2));
	}

	/** Tables: a fairy ring network (needs item 772, +3 ticks per ride), a spell category of 4 ticks. */
	private static Tables tables()
	{
		return Tables.parse(List.of(), List.of(), List.of("# networks", "Fairy ring\t3\titem 772\tdesc"),
			List.of("# categories", "spell\t4\tdesc"), List.of());
	}

	private static Map<String, Transport> byId(PlayerTransports.Result r)
	{
		Map<String, Transport> m = new LinkedHashMap<>();
		for (Transport t : r.getTransports())
		{
			m.put(t.getId(), t);
		}
		return m;
	}

	@Test
	void playerTransportsFollowTheToolsRules()
	{
		List<TransportLine> lines = List.of(
			line("ring-a", TransportLine.Kind.NETWORK, "Ring A", new int[]{3200, 3200, 0}, new int[]{3200, 3200, 0}, 2,
				"Fairy ring", "", null, "", null, false),
			line("ring-b", TransportLine.Kind.NETWORK, "Ring B", new int[]{3300, 3300, 0}, new int[]{3300, 3300, 0}, 2,
				"Fairy ring", "", null, "", null, false),
			line("ring-c", TransportLine.Kind.NETWORK, "Ring C", new int[]{3400, 3400, 0}, new int[]{3400, 3400, 0}, 2,
				"Fairy ring", "Agility 50", null, "", null, false),
			line("tele", TransportLine.Kind.TELEPORT, "Spell", new int[3], new int[]{3212, 3424, 0}, 9, "", "Magic 25", null,
				"spell", null, false),
			line("wildy", TransportLine.Kind.TELEPORT, "Glory", new int[3], new int[]{3087, 3496, 0}, 5, "", "", null, "", 30, false),
			line("split", TransportLine.Kind.TWO_WAY, "Rope", new int[]{3210, 3210, 0}, new int[]{3210, 3220, 0}, 4, "",
				"Agility 30", "", "", null, false),
			line("quest", TransportLine.Kind.ONE_WAY, "Quest boat", new int[]{3215, 3210, 0}, new int[]{3500, 3500, 0}, 10, "", "",
				null, "", null, true),
			line("bad", TransportLine.Kind.ONE_WAY, "Unreadable", new int[]{3216, 3210, 0}, new int[]{3216, 3211, 0}, 2, "",
				"this is not a requirement", null, "", null, false));
		PlayerTransports pt = new PlayerTransports(lines, tables());

		// Maxed: carries item 772, so every ring; rides cost own 2 + network 3; the spell costs its category's 4
		Map<String, Transport> max = byId(pt.build(PlayerStateBuilder.maxed(), 0, null));
		assertEquals(List.of("tele", "wildy", "split", "bad", "Fairy ring:Ring A>Ring B", "Fairy ring:Ring A>Ring C",
			"Fairy ring:Ring B>Ring A", "Fairy ring:Ring B>Ring C", "Fairy ring:Ring C>Ring A", "Fairy ring:Ring C>Ring B"),
			new ArrayList<>(max.keySet()));
		assertEquals(5, max.get("Fairy ring:Ring A>Ring B").getTicks());
		assertEquals(4, max.get("tele").getTicks());
		assertTrue(max.get("split").isTwoWay(), "both directions usable alike: one two-way transport");

		// a fresh account: no item 772 (no rides), no Magic 25, no Agility 30 one way (the way back needs nothing)
		Map<String, Transport> fresh = byId(pt.build(PlayerStateBuilder.fresh(), 0, null));
		assertEquals(List.of("wildy", "split:back", "bad"), new ArrayList<>(fresh.keySet()));
		Transport back = fresh.get("split:back");
		assertEquals(3210, back.getStartY() - 10);
		assertFalse(back.isTwoWay());

		// deep in the Wilderness: the level-30 teleport no longer works
		assertFalse(byId(pt.build(PlayerStateBuilder.maxed(), 31, null)).containsKey("wildy"));
		assertTrue(byId(pt.build(PlayerStateBuilder.maxed(), 30, null)).containsKey("wildy"));

		// free world: only transports from free land to free land
		FreeLand land = new FreeLand.Builder().set(3200, 3200, 3220, 3430, true).build();
		PlayerState f2p = new PlayerState.Builder("F2P").members(false).level(Skill.MAGIC, 30).build();
		assertEquals(List.of("tele", "split:back", "bad"), new ArrayList<>(byId(pt.build(f2p, 0, land)).keySet()));
	}

	@Test
	void droppingTheSearchCachesKeepsRoutesTheSame()
	{
		World w = new World();
		CollisionMap map = w.map();
		Pathfinder pf = new Pathfinder(map, CoarseGraph.build(map), null);
		List<Transport> tr = List.of(Transport.between("up", "Ladder", 3230, 3230, 0, 3230, 3230, 1, 2, true, true),
			Transport.teleport("tp", "Teleport", 3300, 3250, 0, 5, true));
		DoorMap doors = w.doorMap();
		Random rnd = new Random(11);
		List<Route> first = new ArrayList<>();
		List<PathQuery> qs = new ArrayList<>();
		for (int i = 0; i < 40; i++)
		{
			PathQuery q = new PathQuery(3200 + rnd.nextInt(128), 3200 + rnd.nextInt(64), rnd.nextInt(2), 3200 + rnd.nextInt(128),
				3200 + rnd.nextInt(64), rnd.nextInt(2), tr, 2, doors, 2);
			qs.add(q);
			first.add(pf.find(q));
		}
		assertTrue(pf.cacheBytes() > 0);
		for (int i = 0; i < qs.size(); i++)
		{
			pf.clearCaches();
			assertEquals(0, pf.cacheBytes());
			Route again = pf.find(qs.get(i));
			Route before = first.get(i);
			assertEquals(before.isFound(), again.isFound());
			assertEquals(before.getTicks(), again.getTicks());
			assertEquals(before.getSteps().size(), again.getSteps().size());
			for (int s = 0; s < before.getSteps().size(); s++)
			{
				assertTrue(Arrays.equals(before.getSteps().get(s).getTiles(), again.getSteps().get(s).getTiles()));
			}
		}
	}
}

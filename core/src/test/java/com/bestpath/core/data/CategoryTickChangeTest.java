package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.BruteForce;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The player's own change of a whole teleport category or network (the Settings tab's "Teleport and
 * travel ticks", the same list as the Data Tool's "Teleport and travel costs"): every transport of it changes, on its
 * own never below 0, and its own card change counts on top. The route search stays exact when a category is lowered:
 * its lower bounds are made per search from the transports' {@link Transport#cost(int)}. Checked against the
 * brute-force checker with one router (one search and its caches) kept across every query, and {@link TickGroups}
 * gives each transport the very ticks the routes use.
 */
class CategoryTickChangeTest
{
	private static final int X0 = 3200;
	private static final int Y0 = 3200;
	private static final int SIZE = 192;
	private static final PlayerState MEMBER = new PlayerState.Builder("Member").members(true).build();
	private static final String RING = "Test ring";
	private static final String BOATS = "Test boats";
	/** The groups a query lowers or raises: two teleport categories, two networks. */
	private static final String[] GROUPS = {"spell", "tablet", RING, BOATS};

	private static Tables tables()
	{
		// dear teleports (40 and 30 ticks), so lowering a category often makes its teleports the quickest way
		return Tables.parse(List.of(), List.of(),
			List.of("# network\textra ticks\trequirement\twhat", RING + "\t10\t\tfour stops", BOATS + "\t5\t\trides"),
			List.of("# category\tticks\twhat", "spell\t40\tspells", "tablet\t30\ttablets", "scroll\t4\tunused here"), List.of());
	}

	private static TransportLine line(String id, TransportLine.Kind kind, String network, String teleportCategory,
		int[] start, int[] end, int ticks, String requirements, String back)
	{
		return TransportLine.builder(id, kind).name(id)
			.category(kind == TransportLine.Kind.TELEPORT ? "TELEPORT" : "TRAVEL")
			.start(start[0], start[1], start[2]).end(end[0], end[1], end[2]).ticks(ticks).network(network)
			.requirements(requirements).requirementsBack(back).teleportCategory(teleportCategory).build();
	}

	private static int[] tile(Random rnd, int plane)
	{
		return new int[]{X0 + rnd.nextInt(SIZE), Y0 + rnd.nextInt(SIZE), plane};
	}

	/** Plane 0 with random walls and blocks, plane 1 a few "buildings" (as the core TickChangeTest's worlds). */
	private static CollisionMap randomMap(Random rnd)
	{
		Map<Long, Integer> f0 = new HashMap<>();
		Map<Long, Integer> f1 = new HashMap<>();
		for (int x = X0; x < X0 + SIZE; x++)
		{
			for (int y = Y0; y < Y0 + SIZE; y++)
			{
				if (((x - X0) / 24 + (y - Y0) / 24) % 3 != 0)
				{
					f1.put((long) x << 32 | y, CollisionFlags.NO_FLOOR);
				}
			}
		}
		for (Map<Long, Integer> f : List.of(f0, f1))
		{
			int walls = 150 + rnd.nextInt(300);
			for (int i = 0; i < walls; i++)
			{
				int x = X0 + rnd.nextInt(SIZE);
				int y = Y0 + rnd.nextInt(SIZE);
				int len = 1 + rnd.nextInt(20);
				boolean vertical = rnd.nextBoolean();
				for (int k = 0; k < len; k++)
				{
					if (vertical)
					{
						f.merge((long) x << 32 | (y + k), CollisionFlags.EAST, (p, q) -> p | q);
						f.merge((long) (x + 1) << 32 | (y + k), CollisionFlags.WEST, (p, q) -> p | q);
					}
					else
					{
						f.merge((long) (x + k) << 32 | y, CollisionFlags.NORTH, (p, q) -> p | q);
						f.merge((long) (x + k) << 32 | (y + 1), CollisionFlags.SOUTH, (p, q) -> p | q);
					}
				}
			}
			int blocks = 100 + rnd.nextInt(300);
			for (int i = 0; i < blocks; i++)
			{
				f.merge((long) (X0 + rnd.nextInt(SIZE)) << 32 | (Y0 + rnd.nextInt(SIZE)), CollisionFlags.OBJECT, (p, q) -> p | q);
			}
		}
		MovementRules.Flags w0 = (x, y) -> inside(x, y) ? f0.getOrDefault((long) x << 32 | y, 0) : CollisionFlags.BLOCKED;
		MovementRules.Flags w1 = (x, y) -> inside(x, y) ? f1.getOrDefault((long) x << 32 | y, 0) : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		List<Integer> regions = new ArrayList<>();
		for (int rx = 50; rx <= 52; rx++)
		{
			for (int ry = 50; ry <= 52; ry++)
			{
				regions.add(rx << 8 | ry);
			}
		}
		return CollisionMapBuilder.build(new MovementRules.Flags[]{w0, w1, none, none}, regions, 0, 0);
	}

	private static boolean inside(int x, int y)
	{
		return x >= X0 && x < X0 + SIZE && y >= Y0 && y < Y0 + SIZE;
	}

	/** Teleports in two categories, a four-stop network, rides of a second network, plain transports. */
	private static List<TransportLine> randomLines(Random rnd)
	{
		List<TransportLine> out = new ArrayList<>();
		for (int i = 0; i < 3 + rnd.nextInt(3); i++)
		{
			out.add(line("spell-" + i, TransportLine.Kind.TELEPORT, "", "spell", new int[3], tile(rnd, rnd.nextInt(5) == 0 ? 1 : 0),
				5, "", null));
			out.add(line("tablet-" + i, TransportLine.Kind.TELEPORT, "", "tablet", new int[3], tile(rnd, 0), 5, "", null));
		}
		for (int i = 0; i < 4; i++)
		{
			out.add(line("ring-" + i, TransportLine.Kind.NETWORK, RING, "", new int[3], tile(rnd, rnd.nextInt(6) == 0 ? 1 : 0),
				20 + rnd.nextInt(30), "", null));
		}
		for (int i = 0; i < 3 + rnd.nextInt(4); i++)
		{
			boolean twoWay = rnd.nextBoolean();
			// some two-way rides only work back (Agility 99 there): a transport "boat-n:back" of its own
			boolean backOnly = twoWay && rnd.nextInt(3) == 0;
			out.add(line("boat-" + i, twoWay ? TransportLine.Kind.TWO_WAY : TransportLine.Kind.ONE_WAY, BOATS, "", tile(rnd, 0),
				tile(rnd, rnd.nextInt(4) == 0 ? 1 : 0), 15 + rnd.nextInt(50), backOnly ? "Agility 99" : "", backOnly ? "" : null));
		}
		for (int i = 0; i < 4 + rnd.nextInt(8); i++)
		{
			int[] s = tile(rnd, rnd.nextInt(2));
			out.add(line("t-" + i, rnd.nextBoolean() ? TransportLine.Kind.TWO_WAY : TransportLine.Kind.ONE_WAY, "", "", s,
				new int[]{Math.min(X0 + SIZE - 1, s[0] + rnd.nextInt(5)), Math.min(Y0 + SIZE - 1, s[1] + rnd.nextInt(5)),
					rnd.nextInt(2)}, 10 + rnd.nextInt(60), "", null));
		}
		return out;
	}

	/** Leaves nothing out; the given changes per transport id, teleport category and network. */
	private static PluginRouter.TransportFilter changes(Map<String, Integer> own, Map<String, Integer> group)
	{
		return new PluginRouter.TransportFilter()
		{
			@Override
			public boolean excluded(Transport t, TransportLine line)
			{
				return false;
			}

			@Override
			public int tickChange(String transportId)
			{
				return own.getOrDefault(transportId, 0);
			}

			@Override
			public int teleportCategoryTickChange(String category)
			{
				return group.getOrDefault(category, 0);
			}

			@Override
			public int networkTickChange(String network)
			{
				return group.getOrDefault(network, 0);
			}
		};
	}

	private static PlayerTransports.TickChanges asTickChanges(Map<String, Integer> own, Map<String, Integer> group)
	{
		return new PlayerTransports.TickChanges()
		{
			@Override
			public int transport(String transportId)
			{
				return own.getOrDefault(transportId, 0);
			}

			@Override
			public int teleportCategory(String category)
			{
				return group.getOrDefault(category, 0);
			}

			@Override
			public int network(String network)
			{
				return group.getOrDefault(network, 0);
			}
		};
	}

	/** The transport is in this teleport category or network. */
	private static boolean inGroup(TransportLine line, String group)
	{
		return line != null && (group.equals(line.getTeleportCategory()) || group.equals(line.getNetwork()));
	}

	private static boolean usesGroup(PluginRouter.Answer a, String group)
	{
		for (Route.Step s : a.getRoute().getSteps())
		{
			if (s.getTransport() != null && inGroup(a.getTransports().lineOf(s.getTransport()), group))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The route, checked against the brute-force checker on the very transports it was searched with (to the tile the
	 * route stops at); the steps add up to the total; and {@link TickGroups} gives every transport its cost.
	 */
	private static PluginRouter.Answer route(PluginRouter r, TickGroups groups, int[] s, int[] d, Map<String, Integer> own,
		Map<String, Integer> group, String where)
	{
		PluginRouter.Answer a = PluginRouterDiagnostics.route(r, MEMBER, s[0], s[1], s[2], d[0], d[1], d[2], id -> false, changes(own, group));
		int[] stop = a.getStop();
		Route found = a.getRoute();
		if (stop == null)
		{
			assertTrue(!found.isFound(), where);
			return a;
		}
		PathQuery q = new PathQuery(s[0], s[1], s[2], stop[0], stop[1], stop[2], a.getTransports().getTransports(),
			PluginRouter.PLANE_CHANGE_PENALTY, DoorMap.none(), PluginRouter.DOOR_COST);
		Route brute = BruteForce.find(r.getData().getMap(), q, r.getData().getWalls());
		assertEquals(brute.isFound(), found.isFound(), "found? " + where);
		assertEquals(brute.getTicks(), found.getTicks(), "same as the brute-force checker: " + where);
		int sum = 0;
		for (Route.Step st : found.getSteps())
		{
			sum += st.getTicks();
		}
		assertEquals(found.getTicks(), sum, "the total is the steps' ticks: " + where);
		PlayerTransports.TickChanges c = asTickChanges(own, group);
		for (Transport t : a.getTransports().getTransports())
		{
			assertEquals(t.cost(PluginRouter.PLANE_CHANGE_PENALTY), groups.ticks(t.getId(), c),
				"TickGroups gives the ticks routes use: " + t + ", " + where);
		}
		return a;
	}

	/**
	 * Random worlds and queries, one router for all: the route with the data's ticks; a teleport category or network
	 * lowered (mostly so far that all its transports take 0 ticks, else part of the way), with a card change on one of
	 * its transports now and then; then raised. Same as the brute-force checker every time; a lowered group never makes
	 * a route dearer, and when it makes it quicker the route uses that group; a raised one never makes it quicker.
	 */
	@Test
	void aLoweredCategoryOrNetworkGivesTheBruteForceCheckersRoutes()
	{
		Random rnd = new Random(202610142);
		int queries = 0;
		int picked = 0;
		int found = 0;
		Tables tables = tables();
		for (int world = 0; world < 8; world++)
		{
			CollisionMap m = randomMap(rnd);
			List<TransportLine> lines = randomLines(rnd);
			PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), lines, tables,
				FreeLand.NONE);
			PluginRouter r = new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
			TickGroups groups = TickGroups.of(lines, tables);
			for (int i = 0; i < 25; i++)
			{
				int[] s = tile(rnd, rnd.nextInt(5) == 0 ? 1 : 0);
				int[] d = tile(rnd, rnd.nextInt(5) == 0 ? 1 : 0);
				String g = GROUPS[rnd.nextInt(GROUPS.length)];
				String where = String.format("world %d query %d: %s, from %d,%d,%d to %d,%d,%d", world, i, g, s[0], s[1], s[2],
					d[0], d[1], d[2]);
				PluginRouter.Answer before = route(r, groups, s, d, Map.of(), Map.of(), where + " (data)");
				found += before.getRoute().isFound() ? 1 : 0;

				int highest = 0;
				for (TickGroups.Group k : groups.getGroups())
				{
					if (k.getName().equals(g))
					{
						highest = k.highest(0);
					}
				}
				assertTrue(highest > 0, g);
				int down = rnd.nextInt(4) == 0 ? -1 - rnd.nextInt(highest) : -highest - rnd.nextInt(10);
				// now and then the player also changed one transport of the group on its card
				Map<String, Integer> own = new HashMap<>();
				if (rnd.nextInt(3) == 0)
				{
					for (Transport t : before.getTransports().getTransports())
					{
						if (inGroup(before.getTransports().lineOf(t), g))
						{
							own.put(t.getId(), rnd.nextInt(7) - 3);
							break;
						}
					}
				}
				PluginRouter.Answer lowered = route(r, groups, s, d, own, Map.of(g, down), where + " lowered by " + down);
				for (Transport t : lowered.getTransports().getTransports())
				{
					TransportLine l = lowered.getTransports().lineOf(t);
					if (inGroup(l, g) && !own.containsKey(t.getId()))
					{
						int data0 = t.withTickChange(0).cost(PluginRouter.PLANE_CHANGE_PENALTY);
						int penalty = data0 - t.getTicks();
						assertEquals(Math.max(0, t.getTicks() + down) + penalty, t.cost(PluginRouter.PLANE_CHANGE_PENALTY),
							"every transport of the group, never below 0 by the group's change: " + t + ", " + where);
					}
				}
				if (own.isEmpty() && lowered.getRoute().isFound())
				{
					if (before.getRoute().isFound())
					{
						assertTrue(lowered.getRoute().getTicks() <= before.getRoute().getTicks(), "never dearer " + where);
					}
					if (!before.getRoute().isFound() || lowered.getRoute().getTicks() < before.getRoute().getTicks())
					{
						assertTrue(usesGroup(lowered, g), "a quicker route uses the lowered group " + where);
						picked++;
					}
				}

				int up = 1 + rnd.nextInt(30);
				PluginRouter.Answer raised = route(r, groups, s, d, Map.of(), Map.of(g, up), where + " raised by " + up);
				if (raised.getRoute().isFound() && before.getRoute().isFound())
				{
					assertTrue(raised.getRoute().getTicks() >= before.getRoute().getTicks(), "never quicker when raised " + where);
				}
				queries++;
			}
			// the same router again without changes: nothing kept from the changed searches
			int[] s = {X0 + 5, Y0 + 5, 0};
			int[] d = {X0 + SIZE - 6, Y0 + SIZE - 6, 0};
			PluginRouter.Answer a = route(r, groups, s, d, Map.of(), Map.of(), "world " + world + " again");
			assertNotNull(a.getRoute());
		}
		assertEquals(200, queries);
		assertTrue(found >= 140, "most queries have a route: " + found);
		assertTrue(picked >= 40, "the lowered group was the quickest way often enough to test it: " + picked);
	}

	@Test
	void theGroupsAreTheTablesWithTheirTransportsTicks()
	{
		List<TransportLine> lines = List.of(
			line("s1", TransportLine.Kind.TELEPORT, "", "spell", new int[3], new int[]{3210, 3210, 0}, 5, "", null),
			line("s2", TransportLine.Kind.TELEPORT, "", "Spell", new int[3], new int[]{3211, 3210, 0}, 9, "", null),
			line("r1", TransportLine.Kind.NETWORK, RING, "", new int[3], new int[]{3220, 3220, 0}, 20, "", null),
			line("r2", TransportLine.Kind.NETWORK, RING, "", new int[3], new int[]{3230, 3220, 1}, 35, "", null),
			line("b1", TransportLine.Kind.TWO_WAY, BOATS, "", new int[]{3240, 3240, 0}, new int[]{3250, 3250, 0}, 8, "Agility 99", ""));
		TickGroups g = TickGroups.of(lines, tables());
		assertEquals(List.of("spell", "tablet", "scroll", RING, BOATS), names(g));
		TickGroups.Group spell = g.getGroups().get(0);
		assertEquals(TickGroups.Kind.TELEPORT_CATEGORY, spell.getKind());
		assertEquals(2, spell.getCount(), "both spells, any case");
		assertEquals(40, spell.lowest(0), "the category's ticks, not the teleports' own");
		assertEquals(40, spell.highest(0));
		assertEquals(36, spell.highest(-4));
		assertEquals(0, spell.lowest(-50), "never below 0");
		assertEquals(-40, spell.lowestChange());
		TickGroups.Group ring = g.getGroups().get(3);
		assertEquals(TickGroups.Kind.NETWORK, ring.getKind());
		assertEquals(30, ring.lowest(0), "20 + the network's 10");
		assertEquals(45, ring.highest(0));
		assertEquals(0, ring.lowest(-35));
		assertEquals(10, ring.highest(-35));
		assertEquals(-45, ring.lowestChange());
		assertEquals(0, g.getGroups().get(2).getCount(), "a category without teleports is listed too");

		// one transport's ticks as routes use them: data, group change (never below 0 on its own), own change, penalty
		PlayerTransports.TickChanges none = asTickChanges(Map.of(), Map.of());
		assertEquals(40, g.ticks("s1", none));
		assertEquals(37, g.ticks("s1", asTickChanges(Map.of(), Map.of("spell", -3))));
		assertEquals(0, g.ticks("s1", asTickChanges(Map.of(), Map.of("spell", -60))));
		assertEquals(2, g.ticks("s1", asTickChanges(Map.of("s1", 2), Map.of("spell", -60))), "the card's +2 counts from 0");
		assertEquals(45 + PluginRouter.PLANE_CHANGE_PENALTY, g.ticks(RING + ":r1>r2", none), "stop r2's ticks, plane 0 to 1: + penalty");
		assertEquals(30 + PluginRouter.PLANE_CHANGE_PENALTY, g.ticks(RING + ":r2>r1", none), "stop r1's ticks, plane 1 to 0");
		assertEquals(0 + PluginRouter.PLANE_CHANGE_PENALTY, g.ticks(RING + ":r1>r2", asTickChanges(Map.of(), Map.of(RING, -50))),
			"the group's change never takes off the plane-change penalty");
		assertEquals(0, g.ticks(RING + ":r1>r2", asTickChanges(Map.of(RING + ":r1>r2", -PluginRouter.PLANE_CHANGE_PENALTY), Map.of(RING, -50))),
			"the card's change can");
		assertEquals(13, g.ticks("b1:back", none), "the way back: the line's ticks + the network's 5");
		assertEquals(10, g.ticks("b1:back", asTickChanges(Map.of("b1:back", -1), Map.of(BOATS, -2))));
		assertEquals(-1, g.ticks("nope", none));
		assertEquals(-1, g.ticks(RING + ":r1>nope", none));

		// and the same as the transports the player's list is built with
		PlayerTransports pt = new PlayerTransports(lines, tables());
		PlayerTransports.TickChanges c = asTickChanges(Map.of("b1:back", 3), Map.of(RING, -25, "spell", -38, BOATS, -20));
		PlayerTransports.Result built = pt.build(MEMBER, 0, null, c);
		assertEquals(5, built.getTransports().size(), "two spells, two rides, the way back");
		for (Transport t : built.getTransports())
		{
			assertEquals(t.cost(PluginRouter.PLANE_CHANGE_PENALTY), g.ticks(t.getId(), c), t.toString());
		}
		assertEquals(2, find(built, "s2").cost(PluginRouter.PLANE_CHANGE_PENALTY));
		assertEquals(40, find(built, "s2").getTicks(), "the data's ticks are kept");
		assertEquals(5 + PluginRouter.PLANE_CHANGE_PENALTY, find(built, RING + ":r2>r1").cost(PluginRouter.PLANE_CHANGE_PENALTY), "r1: 30 - 25, plane change penalty");
		assertEquals(0 + 3, find(built, "b1:back").cost(PluginRouter.PLANE_CHANGE_PENALTY), "13 - 20: 0, then the card's +3");
		// no changes: the very same transports as without
		assertEquals(pt.build(MEMBER, 0, null).getTransports().size(), pt.build(MEMBER, 0, null, none).getTransports().size());
		for (Transport t : pt.build(MEMBER, 0, null, none).getTransports())
		{
			assertEquals(0, t.getTickChange(), t.toString());
		}
	}

	private static Transport find(PlayerTransports.Result r, String id)
	{
		for (Transport t : r.getTransports())
		{
			if (t.getId().equals(id))
			{
				return t;
			}
		}
		throw new AssertionError("no transport " + id);
	}

	private static List<String> names(TickGroups g)
	{
		List<String> out = new ArrayList<>();
		for (TickGroups.Group k : g.getGroups())
		{
			out.add(k.getName());
		}
		return out;
	}
}

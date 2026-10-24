package com.bestpath.core.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.graph.CoarseGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The player's own tick changes (- / + on a step card): a transport's cost is the data's cost
 * plus the change, never below 0, and the route search stays exact when costs go down. The coarse lower bounds are
 * made per search from the query's transports ({@link Transport#cost(int)}), so a lowered transport lowers them
 * too; these tests prove it against the brute-force checker, with one search object kept across the queries (its
 * caches hold walking distances only).
 */
class TickChangeTest
{
	private static final int X0 = 3200;
	private static final int Y0 = 3200;
	private static final int SIZE = 192;
	private static final int PENALTY = 2;

	@Test
	void theCostHasTheChangeAndNeverGoesBelowZero()
	{
		Transport ladder = Transport.between("l", "Ladder", 3201, 3201, 0, 3201, 3201, 1, 3, true, true);
		assertEquals(5, ladder.cost(PENALTY));
		assertEquals(0, ladder.getTickChange());
		assertSame(ladder, ladder.withTickChange(0));
		Transport less = ladder.withTickChange(-2);
		assertEquals(3, less.cost(PENALTY));
		assertEquals(3, less.getTicks(), "the data's ticks stay");
		assertEquals(-2, less.getTickChange());
		assertEquals(0, ladder.withTickChange(-5).cost(PENALTY), "the change can take off the plane-change penalty too");
		assertEquals(0, ladder.withTickChange(-50).cost(PENALTY), "never below 0");
		assertEquals(9, ladder.withTickChange(4).cost(PENALTY));
		assertEquals(5, less.withTickChange(0).cost(PENALTY), "back to the data's cost");
		Transport ring = ladder.withTickChange(1).usedFromBeside();
		assertEquals(6, ring.cost(PENALTY), "used from beside keeps the change");
		assertTrue(ring.isUsedFromBeside());
		assertTrue(ring.withTickChange(-1).isUsedFromBeside(), "the change keeps used from beside");
		Transport tele = Transport.teleport("t", "Tele", 3210, 3210, 0, 5, true);
		assertEquals(2, tele.withTickChange(-3).cost(PENALTY));
		assertEquals(0, tele.withTickChange(-7).cost(PENALTY));
	}

	/** Two planes: plane 0 with random walls and blocks, plane 1 a few "buildings". */
	private static final class World implements MovementRules.Flags
	{
		final Map<Long, Integer> flags = new HashMap<>();

		void set(int x, int y, int f)
		{
			flags.merge((long) x << 32 | y, f, (p, q) -> p | q);
		}

		@Override
		public int get(int x, int y)
		{
			boolean inside = x >= X0 && x < X0 + SIZE && y >= Y0 && y < Y0 + SIZE;
			return inside ? flags.getOrDefault((long) x << 32 | y, 0) : CollisionFlags.BLOCKED;
		}
	}

	private static CollisionMap randomMap(Random rnd)
	{
		World w0 = new World();
		World w1 = new World();
		for (int x = X0; x < X0 + SIZE; x++)
		{
			for (int y = Y0; y < Y0 + SIZE; y++)
			{
				if (((x - X0) / 24 + (y - Y0) / 24) % 3 != 0)
				{
					w1.set(x, y, CollisionFlags.NO_FLOOR);
				}
			}
		}
		for (World w : new World[]{w0, w1})
		{
			int walls = 150 + rnd.nextInt(400);
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
						w.set(x, y + k, CollisionFlags.EAST);
						w.set(x + 1, y + k, CollisionFlags.WEST);
					}
					else
					{
						w.set(x + k, y, CollisionFlags.NORTH);
						w.set(x + k, y + 1, CollisionFlags.SOUTH);
					}
				}
			}
			int blocks = 100 + rnd.nextInt(500);
			for (int i = 0; i < blocks; i++)
			{
				w.set(X0 + rnd.nextInt(SIZE), Y0 + rnd.nextInt(SIZE), CollisionFlags.OBJECT);
			}
		}
		List<Integer> regions = new ArrayList<>();
		for (int rx = 50; rx <= 52; rx++)
		{
			for (int ry = 50; ry <= 52; ry++)
			{
				regions.add(rx << 8 | ry);
			}
		}
		MovementRules.Flags empty = (x, y) -> CollisionFlags.BLOCKED;
		return CollisionMapBuilder.build(new MovementRules.Flags[]{w0, w1, empty, empty}, regions, 0, 0);
	}

	private static List<Transport> randomTransports(Random rnd)
	{
		List<Transport> tr = new ArrayList<>();
		int n = 6 + rnd.nextInt(20);
		for (int i = 0; i < n; i++)
		{
			int sp = rnd.nextInt(2);
			int ep = rnd.nextInt(10) < 6 ? 1 - sp : sp;
			int sx = X0 + rnd.nextInt(SIZE);
			int sy = Y0 + rnd.nextInt(SIZE);
			boolean far = rnd.nextInt(3) == 0;
			int ex = far ? X0 + rnd.nextInt(SIZE) : Math.min(X0 + SIZE - 1, Math.max(X0, sx + rnd.nextInt(9) - 4));
			int ey = far ? Y0 + rnd.nextInt(SIZE) : Math.min(Y0 + SIZE - 1, Math.max(Y0, sy + rnd.nextInt(9) - 4));
			if (rnd.nextInt(6) == 0)
			{
				tr.add(Transport.teleport("tp" + i, "Teleport", ex, ey, ep, 20 + rnd.nextInt(60), true));
			}
			else
			{
				// dear ones, so that lowering them to 0 often makes them the cheapest way
				tr.add(Transport.between("t" + i, "T", sx, sy, sp, ex, ey, ep, 10 + rnd.nextInt(80), rnd.nextBoolean(), true));
			}
		}
		return tr;
	}

	private static boolean uses(Route r, String id)
	{
		for (Route.Step s : r.getSteps())
		{
			if (s.getTransport() != null && s.getTransport().getId().equals(id))
			{
				return true;
			}
		}
		return false;
	}

	private static int stepTotal(Route r)
	{
		int n = 0;
		for (Route.Step s : r.getSteps())
		{
			n += s.getTicks();
		}
		return n;
	}

	/**
	 * Random worlds and queries: the route with the data's ticks, then with one transport lowered to 0 ticks
	 * (plane-change penalty included; mostly a dear one near the query's ends that the route never uses at its data
	 * cost, else any the route did not use), then raised. The search matches the brute-force checker
	 * every time; when the lowered transport makes the route cheaper, the route uses it; a lowered cost never makes
	 * a route dearer, a raised one never cheaper; the steps' ticks add up to the total.
	 */
	@Test
	void loweredTransportsArePickedWhenTrulyCheapestSameAsBruteForce()
	{
		Random rnd = new Random(20261014);
		int queries = 0;
		int picked = 0;
		int found = 0;
		for (int world = 0; world < 10; world++)
		{
			CollisionMap m = randomMap(rnd);
			Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null); // one search kept across every query (its caches)
			List<Transport> shared = randomTransports(rnd);
			for (int i = 0; i < 30; i++)
			{
				int sx = X0 + rnd.nextInt(SIZE);
				int sy = Y0 + rnd.nextInt(SIZE);
				int sp = rnd.nextInt(4) == 0 ? 1 : 0;
				int dx = X0 + rnd.nextInt(SIZE);
				int dy = Y0 + rnd.nextInt(SIZE);
				int dp = rnd.nextInt(4) == 0 ? 1 : 0;
				// plus one dear transport near this query's ends (a teleport, or from near the start to near the
				// destination), which the route never uses at its data cost; lowered, it may become the quickest way
				List<Transport> tr = new ArrayList<>(shared);
				int ex = clamp(dx + rnd.nextInt(13) - 6, X0);
				int ey = clamp(dy + rnd.nextInt(13) - 6, Y0);
				int ep = rnd.nextInt(4) == 0 ? 1 - dp : dp;
				tr.add(rnd.nextInt(3) == 0
					? Transport.teleport("x-tp", "Dear teleport", ex, ey, ep, 400 + rnd.nextInt(200), true)
					: Transport.between("x", "Dear transport", clamp(sx + rnd.nextInt(13) - 6, X0), clamp(sy + rnd.nextInt(13) - 6, Y0),
					rnd.nextInt(4) == 0 ? 1 - sp : sp, ex, ey, ep, 400 + rnd.nextInt(200), rnd.nextBoolean(), true));
				PathQuery base = new PathQuery(sx, sy, sp, dx, dy, dp, tr, PENALTY);
				Route before = pf.find(base);
				Route bruteBefore = BruteForce.find(m, base);
				assertEquals(bruteBefore.isFound(), before.isFound());
				assertEquals(bruteBefore.getTicks(), before.getTicks());
				found += before.isFound() ? 1 : 0;
				// mostly the dear one; else any transport the route did not use
				int k = tr.size() - 1;
				if (rnd.nextInt(4) == 0)
				{
					k = rnd.nextInt(tr.size());
					for (int tries = 0; tries < 5 && before.isFound() && uses(before, tr.get(k).getId()); tries++)
					{
						k = rnd.nextInt(tr.size());
					}
				}
				Transport t = tr.get(k);
				String where = String.format("world %d query %d: %s lowered, from %d,%d,%d to %d,%d,%d", world, i, t, sx, sy, sp,
					dx, dy, dp);

				List<Transport> lowered = new ArrayList<>(tr);
				lowered.set(k, t.withTickChange(-t.cost(PENALTY)));
				assertEquals(0, lowered.get(k).cost(PENALTY));
				PathQuery q = new PathQuery(sx, sy, sp, dx, dy, dp, lowered, PENALTY);
				Route after = pf.find(q);
				Route brute = BruteForce.find(m, q);
				assertEquals(brute.isFound(), after.isFound(), "found? " + where);
				assertEquals(brute.getTicks(), after.getTicks(), "ticks " + where);
				if (after.isFound())
				{
					assertEquals(after.getTicks(), stepTotal(after), "the steps add up to the total " + where);
					if (before.isFound())
					{
						assertTrue(after.getTicks() <= before.getTicks(), "never dearer " + where);
					}
					if (!before.isFound() || after.getTicks() < before.getTicks())
					{
						assertTrue(uses(after, t.getId()), "a cheaper route must use the lowered transport " + where);
						picked++;
					}
				}

				List<Transport> raised = new ArrayList<>(tr);
				raised.set(k, t.withTickChange(1 + rnd.nextInt(40)));
				PathQuery qr = new PathQuery(sx, sy, sp, dx, dy, dp, raised, PENALTY);
				Route up = pf.find(qr);
				Route bruteUp = BruteForce.find(m, qr);
				assertEquals(bruteUp.isFound(), up.isFound(), "found? raised, " + where);
				assertEquals(bruteUp.getTicks(), up.getTicks(), "ticks raised, " + where);
				if (up.isFound() && before.isFound())
				{
					assertTrue(up.getTicks() >= before.getTicks(), "never cheaper when raised " + where);
				}
				queries++;
			}
		}
		assertEquals(300, queries);
		assertTrue(found >= 150, "most queries have a route: " + found);
		assertTrue(picked >= 80, "the lowered transport was the quickest way often enough to test it: " + picked);
	}

	private static int clamp(int v, int lo)
	{
		return Math.max(lo, Math.min(lo + SIZE - 1, v));
	}
}

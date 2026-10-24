package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * Route-side fixes from a code review: an Error during a search or the data load, and arrival through a wall.
 */
public class PluginFixesRouteTest
{
	private static final PlayerSnapshot ME = new PlayerSnapshot(3200, 3200, 0, PlayerStateBuilder.fresh(), List.of());
	private static final RouteRequest A = new RouteRequest(3210, 3210, 0);

	private static RouteResult next(BlockingQueue<RouteResult> q) throws InterruptedException
	{
		RouteResult r = q.poll(5, TimeUnit.SECONDS);
		if (r == null)
		{
			throw new AssertionError("nothing published");
		}
		return r;
	}

	@Test
	public void anErrorDuringASearchIsAnsweredNotCalculatingForEver() throws Exception
	{
		BlockingQueue<RouteResult> published = new LinkedBlockingQueue<>();
		AtomicInteger calls = new AtomicInteger();
		RouteService service = new RouteService(job ->
		{
			if (calls.incrementAndGet() == 1)
			{
				throw new NoClassDefFoundError("com/example/Missing");
			}
			return RouteResults.ready(job.getGeneration(), job.getRequest(), List.of(
				new RouteStep(RouteStep.Kind.WALK, "Walk", "", 10, List.of(), null, new int[0], -1)));
		}, published::add);
		try
		{
			service.request(A, ME, TransportSettings.defaults());
			assertEquals(RouteStatus.CALCULATING, next(published).getStatus());
			RouteResult failed = next(published);
			assertEquals(RouteStatus.ERROR, failed.getStatus());
			assertEquals("Route search failed: NoClassDefFoundError", failed.getMessage());
			assertFalse(service.isSearching());

			// the worker still serves the next search
			service.request(A, ME, TransportSettings.defaults());
			next(published);
			assertEquals(RouteStatus.READY, next(published).getStatus());

			// a worker task that throws an Error doesn't stop the worker either
			service.runOnWorker(() ->
			{
				throw new AssertionError("boom");
			});
			service.request(A, ME, TransportSettings.defaults());
			next(published);
			assertEquals(RouteStatus.READY, next(published).getStatus());
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	public void anErrorWhileLoadingTheMapDataIsShownAndNotRetried()
	{
		AtomicInteger loads = new AtomicInteger();
		LoadingEngine e = new LoadingEngine(() ->
		{
			loads.incrementAndGet();
			throw new ExceptionInInitializerError("broken table");
		});
		e.load();
		RouteResult r = e.route(new RouteJob(3, A, ME, TransportSettings.defaults(), java.util.Set.of()));
		assertEquals(RouteStatus.NO_DATA, r.getStatus());
		assertTrue(r.getMessage(), r.getMessage().startsWith("The map data could not be loaded: "));
		assertTrue(r.getMessage(), r.getMessage().contains("broken table"));
		e.route(new RouteJob(4, A, ME, TransportSettings.defaults(), java.util.Set.of()));
		assertEquals(1, loads.get());
	}

	// ---------------------------------------------------------------------------------- arrival and walls

	/**
	 * A route that walks along the south side of a wall (between y 3209 and 3210, x 3203-3207), right past the
	 * destination 3205,3210 on the other side, then around the wall's east end and back west to it.
	 */
	private static RouteResult aroundTheWall(int[] arriveTiles)
	{
		RouteStep along = RouteProgressTest.walk(3200, 3209, 3212, 3209, 0);
		RouteStep up = RouteProgressTest.walk(3212, 3209, 3212, 3213, 0);
		RouteStep back = RouteProgressTest.walk(3212, 3213, 3205, 3210, 0);
		return RouteResults.ready(5, new RouteRequest(3205, 3210, 0), List.of(along, up, back), -1, List.of(), arriveTiles);
	}

	/** The tiles next to {@code dest} from which one legal step (walls, no corner cutting) reaches it. */
	private static int[] arriveTiles(CollisionMap map, int dest)
	{
		int x = Tiles.x(dest);
		int y = Tiles.y(dest);
		int p = Tiles.plane(dest);
		int[] out = new int[8];
		int n = 0;
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if ((dx != 0 || dy != 0) && map.canStep(x - dx, y - dy, p, dx, dy))
				{
					out[n++] = Tiles.pack(x - dx, y - dy, p);
				}
			}
		}
		return Arrays.copyOf(out, n);
	}

	/** The tiles one legal step from 3205,3210 with that wall: the north side and along the row. */
	private static final int[] LEGAL = {Tiles.pack(3204, 3210, 0), Tiles.pack(3206, 3210, 0), Tiles.pack(3204, 3211, 0),
		Tiles.pack(3205, 3211, 0), Tiles.pack(3206, 3211, 0)};

	@Test
	public void standingNextToTheDestinationThroughAWallIsNotArriving()
	{
		RouteProgress p = new RouteProgress(aroundTheWall(LEGAL));
		assertTrue(p.update(3205, 3209, 0, null) != RouteProgress.Event.ARRIVED); // just south of the wall
		assertTrue(p.update(3206, 3209, 0, null) != RouteProgress.Event.ARRIVED);
		assertEquals(RouteProgress.Event.ARRIVED, new RouteProgress(aroundTheWall(LEGAL)).update(3206, 3211, 0, null));
		assertEquals(RouteProgress.Event.ARRIVED, new RouteProgress(aroundTheWall(LEGAL)).update(3205, 3210, 0, null));
	}

	@Test
	public void withoutKnownStepsTheOneTileRadiusCountsOnlyOnTheLastStep()
	{
		RouteProgress p = new RouteProgress(aroundTheWall(null));
		assertTrue(p.update(3205, 3209, 0, null) != RouteProgress.Event.ARRIVED); // first step, through the wall: not yet
		RouteProgress q = new RouteProgress(aroundTheWall(null));
		q.update(3208, 3213, 0, null); // on the last walk
		assertEquals(2, q.getStep());
		assertEquals(RouteProgress.Event.ARRIVED, q.update(3206, 3211, 0, null));
	}

	@Test
	public void arriveTilesComeFromTheMovementData()
	{
		// open region, but a wall on the south side of row 3206 for x 3203-3207 (no step between y 3205 and 3206 there)
		MovementRules.Flags flags = (x, y) ->
		{
			if (x < 3200 || x >= 3264 || y < 3200 || y >= 3264)
			{
				return CollisionFlags.BLOCKED;
			}
			if (y == 3206 && x >= 3203 && x <= 3207)
			{
				return CollisionFlags.SOUTH;
			}
			if (y == 3205 && x >= 3203 && x <= 3207)
			{
				return CollisionFlags.NORTH;
			}
			return 0;
		};
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{flags, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		int[] t = arriveTiles(m, Tiles.pack(3205, 3206, 0));
		java.util.Set<Integer> got = new java.util.HashSet<>();
		for (int v : t)
		{
			got.add(v);
		}
		assertEquals(java.util.Set.of(Tiles.pack(3204, 3206, 0), Tiles.pack(3206, 3206, 0), Tiles.pack(3204, 3207, 0),
			Tiles.pack(3205, 3207, 0), Tiles.pack(3206, 3207, 0)), got);
		// open ground: all eight neighbours
		assertEquals(8, arriveTiles(m, Tiles.pack(3230, 3230, 0)).length);
		// off the map: none
		assertEquals(0, arriveTiles(m, Tiles.pack(3300, 3300, 0)).length);
	}

	/** Open ground 3200-3263, a wall on the south side of row 3206 for x 3203-3207, a solid tile (a table) at 3230,3230. */
	private static CollisionMap wallAndTable()
	{
		MovementRules.Flags flags = (x, y) ->
		{
			if (x < 3200 || x >= 3264 || y < 3200 || y >= 3264 || (x == 3230 && y == 3230))
			{
				return CollisionFlags.BLOCKED;
			}
			if (y == 3206 && x >= 3203 && x <= 3207)
			{
				return CollisionFlags.SOUTH;
			}
			if (y == 3205 && x >= 3203 && x <= 3207)
			{
				return CollisionFlags.NORTH;
			}
			return 0;
		};
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		return CollisionMapBuilder.build(new MovementRules.Flags[]{flags, none, none, none}, List.of(50 << 8 | 50), 0, 0);
	}

	@Test
	public void theArriveAreaCountsStepsWalkedNotTilesApart()
	{
		ArriveArea a = ArriveArea.walk(wallAndTable(), Tiles.pack(3205, 3206, 0), -1, ArriveArea.MAX_STEPS);
		assertEquals(0, a.steps(Tiles.pack(3205, 3206, 0)));
		assertEquals(1, a.steps(Tiles.pack(3204, 3207, 0)));
		assertEquals(2, a.steps(Tiles.pack(3205, 3208, 0)));
		assertEquals(2, a.steps(Tiles.pack(3207, 3206, 0)));
		// one tile south, through the wall: the way round its end
		assertTrue(a.steps(Tiles.pack(3205, 3205, 0)) > 2);
		// further than the most the setting allows, or another plane: not in it
		assertEquals(11, a.steps(Tiles.pack(3216, 3206, 0)));
		assertEquals(-1, a.steps(Tiles.pack(3226, 3206, 0)));
		assertEquals(-1, a.steps(Tiles.pack(3205, 3207, 1)));
	}

	@Test
	public void aDestinationNoOneCanStandOnIsArrivedAtBesideTheRoutesEnd()
	{
		// the bug: a table has no tile one legal step from it, so only the exact last tile counted
		CollisionMap m = wallAndTable();
		int table = Tiles.pack(3230, 3230, 0);
		assertEquals(0, arriveTiles(m, table).length);
		RouteStep walk = RouteProgressTest.walk(3220, 3230, 3229, 3230, 0);
		ArriveArea a = ArriveArea.walk(m, table, walk.getEndTile(), ArriveArea.MAX_STEPS);
		RouteResult r = RouteResult.ready(5, new RouteRequest(3230, 3230, 0), List.of(walk), table, List.of(), a);
		RouteProgress p = new RouteProgress(r);
		p.setLimits(RouteProgress.DEFAULT_STRAY_DISTANCE, 2);
		assertEquals(RouteProgress.Event.STEP_CHANGED, p.update(3226, 3230, 0, null)); // 3 steps from the end: not yet
		assertEquals(RouteProgress.Event.ARRIVED, p.update(3229, 3232, 0, null)); // round the table, 2 steps from the end
		RouteProgress exact = new RouteProgress(r);
		exact.setLimits(RouteProgress.DEFAULT_STRAY_DISTANCE, 0);
		assertTrue(exact.update(3229, 3231, 0, null) != RouteProgress.Event.ARRIVED);
		assertEquals(RouteProgress.Event.ARRIVED, exact.update(3229, 3230, 0, null));
	}
}

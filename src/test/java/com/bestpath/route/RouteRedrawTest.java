package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.GamePath;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The walk ahead redrawn from the player's tile ({@link RouteRedraw}) with the real {@link RouteService} and
 * {@link RouteFollower} on open ground: a click on the line is drawn as the game walks it, nothing is published when
 * nothing differs, the route itself and the sidebar are never touched, progress follows the drawn line, and an
 * answer for a route no longer shown is dropped.
 */
public class RouteRedrawTest
{
	private static final MovementRules.Flags OPEN = (x, y) -> x >= 3200 && x < 3392 && y >= 3200 && y < 3392
		? 0 : CollisionFlags.BLOCKED;
	private static final MovementRules.Flags NONE = (x, y) -> CollisionFlags.BLOCKED;
	private static final CollisionMap MAP;

	static
	{
		List<Integer> regions = new ArrayList<>();
		for (int rx = 50; rx <= 52; rx++)
		{
			for (int ry = 50; ry <= 52; ry++)
			{
				regions.add(rx << 8 | ry);
			}
		}
		MAP = CollisionMapBuilder.build(new MovementRules.Flags[]{OPEN, NONE, NONE, NONE}, regions, 0, 0);
	}

	private final RouteFollowerTest.ManualExecutor worker = new RouteFollowerTest.ManualExecutor();
	private final List<RouteResult> published = new ArrayList<>();
	private final List<Progress> stepChanges = new ArrayList<>();
	private final List<DrawPath> redrawn = new ArrayList<>();
	private final List<RouteJob> jobs = new ArrayList<>();
	/** The route's own walk: north-east first, then east (not the game's shape). */
	private int[] walk = steps(3220, 3220, "NE NE NE NE NE NE NE NE NE NE E E E E E E E E E E E E E E E E E E E E");
	private final RouteEngine engine = new RouteEngine()
	{
		private final GamePath game = new GamePath(MAP, DoorMap.none());

		@Override
		public RouteResult route(RouteJob job)
		{
			jobs.add(job);
			return RouteResults.ready(job.getGeneration(), job.getRequest(),
				List.of(new RouteStep(RouteStep.Kind.WALK, "Walk", "", walk.length - 1, List.of(), null, walk, -1)));
		}

		@Override
		public GamePath gamePath()
		{
			return game;
		}
	};
	private final RouteService service = new RouteService(engine, published::add, worker);
	private final RouteFollower follower = new RouteFollower(service, TransportSettings::defaults,
		() -> com.bestpath.player.RequirementInputs.NONE, stepChanges::add);
	private int tick;

	{
		follower.setRedrawListener((r, d) -> redrawn.add(d));
		// short walks: arriving only next to the end, so the redraws near it are seen (arrival: RouteProgressTest)
		follower.setLimits(RouteProgress.DEFAULT_STRAY_DISTANCE, 1);
	}

	/** A walk from (x, y) by these steps ("N", "NE", "E"...). */
	private static int[] steps(int x, int y, String steps)
	{
		String[] s = steps.split(" ");
		int[] out = new int[s.length + 1];
		out[0] = Tiles.pack(x, y, 0);
		for (int i = 0; i < s.length; i++)
		{
			x += s[i].contains("E") ? 1 : s[i].contains("W") ? -1 : 0;
			y += s[i].contains("N") ? 1 : s[i].contains("S") ? -1 : 0;
			out[i + 1] = Tiles.pack(x, y, 0);
		}
		return out;
	}

	private RouteResult start()
	{
		int end = walk[walk.length - 1];
		service.request(new RouteRequest(Tiles.x(end), Tiles.y(end), 0),
			new PlayerSnapshot(Tiles.x(walk[0]), Tiles.y(walk[0]), 0, PlayerStateBuilder.maxed(), List.of()), TransportSettings.defaults());
		worker.runAll();
		assertEquals(RouteStatus.READY, service.getCurrent().getStatus());
		return service.getCurrent();
	}

	/** One game tick with the player on this tile, the game walking them to {@code flag} (-1 none); then the worker. */
	private void at(int tile, int flag)
	{
		follower.onTick(++tick, Tiles.x(tile), Tiles.y(tile), 0, flag, () -> null);
		worker.runAll();
	}

	private static String runs(int[] t)
	{
		StringBuilder b = new StringBuilder();
		int run = 0;
		for (int i = 1; i < t.length; i++)
		{
			run++;
			int dx = Tiles.x(t[i]) - Tiles.x(t[i - 1]);
			int dy = Tiles.y(t[i]) - Tiles.y(t[i - 1]);
			boolean last = i == t.length - 1;
			if (last || Tiles.x(t[i + 1]) - Tiles.x(t[i]) != dx || Tiles.y(t[i + 1]) - Tiles.y(t[i]) != dy)
			{
				b.append(b.length() == 0 ? "" : " ").append(run).append('x')
					.append(dy > 0 ? "N" : dy < 0 ? "S" : "").append(dx > 0 ? "E" : dx < 0 ? "W" : "");
				run = 0;
			}
		}
		return b.toString();
	}

	@Test
	public void aClickOnTheLineIsDrawnAsTheGameWalksItAndNothingElseChanges()
	{
		RouteResult r = start();
		int sidebar = published.size();
		// the player clicks the line's 16th tile (6 east of the north-east run's end)
		at(walk[0], walk[16]);
		DrawPath d = follower.getDrawn();
		assertTrue(d.isFor(r));
		assertEquals(1, d.getVersion());
		int[] line = d.tiles(0);
		assertEquals("6xE 10xNE 14xE", runs(line));
		assertEquals(walk[16], line[16]);
		assertEquals(1, redrawn.size()); // the world map was told
		// the route itself is untouched: same result, same steps and tiles, no new publish for the sidebar
		assertSame(r, service.getCurrent());
		assertEquals(sidebar, published.size());
		assertEquals(1, stepChanges.size());
		assertTrue(Arrays.equals(walk, stepTiles(r)));
		// walking that click: nothing changes on the way
		for (int i = 1; i < 16; i++)
		{
			at(line[i], walk[16]);
			assertSame(d, follower.getDrawn());
			assertEquals(i, follower.getProgress().getTileIndex());
		}
		assertEquals(1, redrawn.size());
		assertEquals(1, jobs.size()); // following the redrawn line is not "off the route"
	}

	@Test
	public void standingStillRedrawsOnceThenNothing()
	{
		RouteResult r = start();
		at(walk[0], -1);
		DrawPath d = follower.getDrawn();
		assertEquals(1, d.getVersion());
		// the game's path from here to the walk's end: east first, the diagonal last
		assertEquals("20xE 10xNE", runs(d.tiles(0)));
		at(walk[0], -1);
		at(walk[0], -1);
		assertSame(d, follower.getDrawn());
		assertEquals(1, redrawn.size());
		// walking along it far from the route's own tiles (10 tiles off at most) is still on the route
		int[] line = d.tiles(0);
		for (int i = 1; i < 25; i++)
		{
			at(line[i], line[25]);
			assertSame(d, follower.getDrawn());
			assertEquals(i, follower.getProgress().getTileIndex());
		}
		assertEquals(1, jobs.size());
		assertSame(r, service.getCurrent());
	}

	@Test
	public void aLineAlreadyInTheGamesShapeIsNeverRepublished()
	{
		walk = steps(3220, 3220, "E E E E E E E E E E E E E E E E E E E E NE NE NE NE NE NE NE NE NE NE");
		start();
		at(walk[0], -1);
		DrawPath first = follower.getDrawn();
		at(walk[0], walk[12]);
		for (int i = 1; i <= 12; i++)
		{
			at(walk[i], i < 12 ? walk[12] : -1);
		}
		assertSame(first, follower.getDrawn());
		assertEquals(0, first.getVersion());
		assertTrue(redrawn.isEmpty());
	}

	@Test
	public void anAnswerForARouteNoLongerShownIsDropped()
	{
		RouteResult r = start();
		follower.onTick(++tick, Tiles.x(walk[0]), Tiles.y(walk[0]), 0, -1, () -> null);
		// a new route is asked for before the worker gets to the redraw
		service.request(new RouteRequest(3300, 3300, 0), new PlayerSnapshot(3220, 3220, 0, PlayerStateBuilder.maxed(), List.of()),
			TransportSettings.defaults());
		worker.runAll();
		RouteResult next = service.getCurrent();
		assertNotEquals(r.getGeneration(), next.getGeneration());
		assertTrue(redrawn.isEmpty());
		assertFalse(follower.getDrawn().isFor(r) && follower.getDrawn().getVersion() > 0);
	}

	private static int[] stepTiles(RouteResult r)
	{
		RouteStep s = r.getSteps().get(0);
		int[] t = new int[s.tileCount()];
		for (int i = 0; i < t.length; i++)
		{
			t[i] = s.tile(i);
		}
		return t;
	}
}

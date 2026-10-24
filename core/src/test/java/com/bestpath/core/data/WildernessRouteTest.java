package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Wilderness on the plugin's route path: "avoid the Wilderness unless necessary" searches with
 * its tiles and transports off first and only with them when that finds nothing; a teleport the player is too deep for
 * is cast at its line ("path the player to the appropriate level").
 */
class WildernessRouteTest
{
	private static final PlayerState MEMBER = new PlayerState.Builder("Member").members(true).build();

	private static TransportLine line(String id, TransportLine.Kind kind, int[] start, int[] end, int ticks, Integer limit)
	{
		return TransportLine.builder(id, kind).name(id)
			.category(kind == TransportLine.Kind.TELEPORT ? "TELEPORT" : "CLIMB_UP")
			.start(start[0], start[1], start[2]).end(end[0], end[1], end[2]).ticks(ticks).wildernessLimit(limit)
			.build();
	}

	/** One open region (3200-3263, 3200-3263), these transports and this Wilderness. */
	private static PluginRouter router(WildernessZones zones, TransportLine... lines)
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		PluginData data = PluginDataBuilder.withWilderness(PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(lines),
			Tables.empty(), FreeLand.NONE), zones);
		return new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
	}

	/** A Wilderness strip x 3230-3235 from y 3200 up to {@code top} (levels 1-20). */
	private static WildernessZones strip(int top)
	{
		WildernessZonesBuilder b = new WildernessZonesBuilder(3200, 3200, 64, 64);
		for (int y = 3200; y <= top; y++)
		{
			for (int x = 3230; x <= 3235; x++)
			{
				b.set(x, y, WildernessZones.TO_20);
			}
		}
		return b.build();
	}

	private static PluginRouter.TransportFilter avoiding(boolean avoid)
	{
		return new PluginRouter.TransportFilter()
		{
			@Override
			public boolean excluded(Transport t, TransportLine line)
			{
				return false;
			}

			@Override
			public boolean avoidWilderness()
			{
				return avoid;
			}
		};
	}

	private static Route route(PluginRouter r, int sx, int sy, int dx, int dy, boolean avoid)
	{
		return PluginRouterDiagnostics.route(r, MEMBER, sx, sy, 0, dx, dy, 0, id -> false, avoiding(avoid)).getRoute();
	}

	private static boolean entersWilderness(Route r, WildernessZones w)
	{
		for (Route.Step s : r.getSteps())
		{
			for (int t : s.getTiles())
			{
				if (w.isWilderness(Tiles.x(t), Tiles.y(t)))
				{
					return true;
				}
			}
		}
		return false;
	}

	@Test
	void avoidingGoesRoundTheWilderness()
	{
		WildernessZones w = strip(3250);
		PluginRouter r = router(w);
		Route through = route(r, 3210, 3220, 3255, 3220, false);
		assertTrue(through.isFound());
		assertEquals(45, through.getTicks(), "straight through the strip");
		assertTrue(entersWilderness(through, w));
		Route round = route(r, 3210, 3220, 3255, 3220, true);
		assertTrue(round.isFound());
		assertFalse(entersWilderness(round, w), "round the strip's top");
		assertTrue(round.getTicks() > 45, "longer: " + round.getTicks());
	}

	@Test
	void onlyWhenNecessaryIntoTheWilderness()
	{
		// the strip from bottom to top: no way round, so the second search goes through it
		WildernessZones wall = strip(3263);
		PluginRouter r = router(wall);
		Route through = route(r, 3210, 3220, 3255, 3220, true);
		assertTrue(through.isFound());
		assertEquals(45, through.getTicks());
		assertTrue(entersWilderness(through, wall));
		// a destination in the Wilderness: one search, as usual
		WildernessZones w = strip(3250);
		Route in = route(router(w), 3210, 3220, 3232, 3220, true);
		assertTrue(in.isFound());
		assertEquals(22, in.getTicks());
	}

	@Test
	void avoidingLeavesTransportsIntoTheWildernessOut()
	{
		WildernessZones w = strip(3250);
		// a cheap teleport into the strip, next to the destination
		PluginRouter r = router(w, line("t-wild", TransportLine.Kind.TELEPORT, new int[3], new int[]{3235, 3240, 0}, 1, null));
		Route used = route(r, 3210, 3220, 3240, 3240, false);
		assertEquals("t-wild", used.getSteps().get(0).getTransport().getId());
		Route kept = route(r, 3210, 3220, 3240, 3240, true);
		assertTrue(kept.isFound());
		assertFalse(entersWilderness(kept, w));
		for (Route.Step s : kept.getSteps())
		{
			assertTrue(s.getTransport() == null || !"t-wild".equals(s.getTransport().getId()));
		}
	}

	/** Levels 21-30 above y 3240, levels 1-20 from y 3220 to 3239, outside below. */
	private static WildernessZones lines()
	{
		WildernessZonesBuilder b = new WildernessZonesBuilder(3200, 3200, 64, 64);
		for (int y = 3220; y < 3264; y++)
		{
			for (int x = 3200; x < 3264; x++)
			{
				b.set(x, y, y >= 3240 ? WildernessZones.TO_30 : WildernessZones.TO_20);
			}
		}
		return b.build();
	}

	/** The teleport step of a route and where it was used from (its first tile), or null. */
	private static Route.Step teleport(Route r, String id)
	{
		for (Route.Step s : r.getSteps())
		{
			if (s.getKind() == Route.Step.Kind.TELEPORT && s.getTransport() != null && id.equals(s.getTransport().getId()))
			{
				return s;
			}
		}
		return null;
	}

	@Test
	void aTeleportTooDeepForIsUsedFromWhereTheSearchGetsShallowEnough()
	{
		WildernessZones w = lines();
		PluginRouter r = router(w,
			line("t-20", TransportLine.Kind.TELEPORT, new int[3], new int[]{3260, 3205, 0}, 10, 20),
			line("t-0", TransportLine.Kind.TELEPORT, new int[3], new int[]{3205, 3205, 0}, 10, 0));
		// from levels 21-30: the normal search walks south to the level-20 line (y 3239, 16 tiles), teleports, one step on
		Route r20 = route(r, 3210, 3255, 3260, 3206, false);
		assertTrue(r20.isFound());
		assertEquals(16 + 10 + 1, r20.getTicks());
		Route.Step tele = teleport(r20, "t-20");
		assertNotNull(tele, r20.getSteps().toString());
		assertEquals(2, tele.getTiles().length, "used from a tile, not from the start");
		assertEquals(3239, Tiles.y(tele.getTiles()[0]));
		assertEquals(Route.Step.Kind.WALK, r20.getSteps().get(0).getKind());
		// a teleport that does not work in the Wilderness at all: out of it first (y 3219)
		Route r0 = route(r, 3210, 3225, 3205, 3206, false);
		assertTrue(r0.isFound());
		assertEquals(3219, Tiles.y(teleport(r0, "t-0").getTiles()[0]));
		// from outside the Wilderness: from the start, as before
		Route out = route(r, 3210, 3210, 3260, 3206, false);
		assertEquals(1, teleport(out, "t-20").getTiles().length);
		// the line's own tile works: level 20 is enough for a level-20 teleport
		assertEquals(10 + 1, route(r, 3210, 3239, 3260, 3206, false).getTicks());
	}

	@Test
	void aLeverOutOfTheWildernessThenTheTeleport()
	{
		// nearby transports that take you out of the Wilderness (like the lever) are considered too
		WildernessZones w = lines();
		PluginRouter r = router(w,
			line("t-20", TransportLine.Kind.TELEPORT, new int[3], new int[]{3205, 3205, 0}, 10, 20),
			line("t-lever", TransportLine.Kind.ONE_WAY, new int[]{3210, 3261, 0}, new int[]{3250, 3210, 0}, 5, null));
		// walking to the line: 23 + 10 + 1 = 34; the lever: 1 + 5, the teleport from its landing (outside) 10, one step: 17
		Route lever = route(r, 3210, 3262, 3205, 3206, false);
		assertTrue(lever.isFound());
		assertEquals(1 + 5 + 10 + 1, lever.getTicks());
		assertEquals("t-lever", lever.getSteps().get(1).getTransport().getId());
		Route.Step tele = teleport(lever, "t-20");
		assertNotNull(tele, lever.getSteps().toString());
		assertEquals(Tiles.pack(3250, 3210, 0), tele.getTiles()[0], "teleported where the lever landed");
	}
}

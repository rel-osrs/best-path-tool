package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.ClueData;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataBuilder;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.SeaBits;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaGridBuilder;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * The boat card of a route started on the player's boat. "Park my boat" (this route
 * only), "Ocean only path" (until the player is off the boat; only for a destination near a dock or open sea).
 * World: two islands with sea between (x 3260-3339), docks West (3258, 3250; a shipwright) and East (3341, 3250); a
 * teleport to 3250, 3250 on the West island.
 */
public class BoatChoiceTest
{
	private static boolean isSea(int x, int y)
	{
		return x >= 3260 && x <= 3339 && y >= 3200 && y < 3392;
	}

	private static Dock dock(int id, String name, int lx, int bx, int standX)
	{
		List<int[]> stand = new ArrayList<>();
		stand.add(new int[]{standX, 3250, 0});
		return new Dock(id, name, 1, null, Dock.Kind.GANGPLANK, lx, 3250, 0, 1, bx, 3250, 2, stand);
	}

	private static CoreRouteEngine engine()
	{
		MovementRules.Flags walk = (x, y) -> x < 3200 || y < 3200 || x >= 3392 || y >= 3392 || isSea(x, y) ? CollisionFlags.BLOCKED : 0;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		List<Integer> regions = new ArrayList<>();
		for (int rx = 50; rx <= 52; rx++)
		{
			for (int ry = 50; ry <= 52; ry++)
			{
				regions.add(rx << 8 | ry);
			}
		}
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{walk, none, none, none}, regions, 0, 0);
		SeaMap.Builder b = new SeaMap.Builder();
		for (int x = 3260; x <= 3339; x++)
		{
			for (int y = 3200; y < 3392; y++)
			{
				b.set(x, y, 131);
			}
		}
		SeaMap sea = b.build();
		MovementRules.Flags f = (x, y) -> (x == 3261 || x == 3262 || x == 3337 || x == 3338) && (y == 3250 || y == 3251)
			? CollisionFlags.OBJECT : 0;
		SeaGrid grid = SeaGridBuilder.build(sea, SeaPieces.build(sea, f), f);
		List<Dock> docks = List.of(dock(0, "West", 3258, 3261, 3257).withShipwright("Shipwright Test"), dock(1, "East", 3341, 3337, 3342));
		TransportLine tele = TransportLine.builder("tele-west", TransportLine.Kind.TELEPORT).name("Teleport west")
			.category("TELEPORT").end(3250, 3250, 0).ticks(10).build();
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(tele), Tables.empty(),
			new FreeLand.Builder().build(), ClueData.EMPTY, new SeaBits.Data(grid, docks, List.of()));
		return new CoreRouteEngine(new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET));
	}

	/** The player on their raft far north at sea. */
	private static final PlayerSnapshot ABOARD = new PlayerSnapshot(3300, 3390, 0, PlayerStateBuilder.maxed(),
		List.of(new Boat(1, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, Boat.AT_START)));

	private static RouteResult route(CoreRouteEngine e, PlayerSnapshot p, int x, int y, BoatMode mode)
	{
		return e.route(new RouteJob(1, new RouteRequest(x, y, 0), p, TransportSettings.defaults(), Set.of(), mode));
	}

	private static String kinds(RouteResult r)
	{
		StringBuilder sb = new StringBuilder();
		for (RouteStep s : r.getSteps())
		{
			sb.append(sb.length() == 0 ? "" : " ").append(s.getKind());
		}
		return sb.toString();
	}

	@Test
	public void aRouteThatLeavesTheBoatAtSeaOffersBothChoices()
	{
		RouteResult r = route(engine(), ABOARD, 3250, 3250, BoatMode.NORMAL);
		assertEquals(RouteStatus.READY, r.getStatus());
		assertEquals(RouteStep.Kind.TELEPORT, r.getSteps().get(0).getKind());
		BoatCard c = r.getBoatCard();
		assertNotNull(c);
		assertEquals(BoatMode.NORMAL, c.getMode());
		assertEquals("Boat 1: Raft", c.getBoat());
		assertEquals(BoatType.RAFT, c.getType());
		assertTrue("West's dock is 8 tiles away", c.isOceanOnlyNear());
		assertEquals("This route leaves your boat at sea", c.title());
		// far inland (45 tiles from the sea): "Ocean only path" greyed out
		RouteResult inland = route(engine(), ABOARD, 3205, 3250, BoatMode.NORMAL);
		assertEquals(BoatMode.NORMAL, inland.getBoatCard().getMode());
		assertFalse(inland.getBoatCard().isOceanOnlyNear());
	}

	@Test
	public void parkMyBoatSailsToADockWithAShipwrightFirst()
	{
		RouteResult r = route(engine(), ABOARD, 3250, 3250, BoatMode.PARK);
		assertEquals(RouteStatus.READY, r.getStatus());
		assertEquals("SAIL BOAT WALK", kinds(r));
		assertTrue(r.getSteps().get(1).getTitle(), r.getSteps().get(1).getTitle().startsWith("Leave"));
		assertEquals(BoatMode.PARK, r.getBoatCard().getMode());
		assertEquals("Parking your boat first", r.getBoatCard().title());
	}

	@Test
	public void oceanOnlyStaysOnTheBoat()
	{
		RouteResult r = route(engine(), ABOARD, 3250, 3250, BoatMode.OCEAN_ONLY);
		assertEquals(RouteStatus.READY, r.getStatus());
		assertEquals("SAIL", kinds(r));
		RouteStep last = r.getSteps().get(0);
		assertTrue(last.getTitle(), last.getTitle().contains("West"));
		int end = last.getEndTile();
		assertTrue(Tiles.x(end) >= 3260 && Tiles.x(end) <= 3265);
		assertTrue(last.getNotes().toString(), last.getNotes().get(last.getNotes().size() - 1).startsWith("Ocean only: this stops"));
		assertEquals(BoatMode.OCEAN_ONLY, r.getBoatCard().getMode());
		assertNull(r.getBoatCard().note);
		// a destination far inland: ignored (only a place nearby where a boat can get counts), the normal route, the
		// card says why and keeps the way to turn it off
		RouteResult inland = route(engine(), ABOARD, 3205, 3250, BoatMode.OCEAN_ONLY);
		assertEquals(RouteStep.Kind.TELEPORT, inland.getSteps().get(0).getKind());
		assertEquals(BoatMode.OCEAN_ONLY, inland.getBoatCard().getMode());
		assertEquals("The destination isn't within " + Sailing.OCEAN_ONLY_RANGE + " tiles of a dock or open sea, so this is the normal route.",
			inland.getBoatCard().note);
		assertEquals(inland.getBoatCard().note, inland.getBoatCard().text());
	}

	@Test
	public void noCardOffTheBoatOrWhenTheRouteSailsIt()
	{
		PlayerSnapshot onLand = new PlayerSnapshot(3230, 3250, 0, PlayerStateBuilder.maxed(),
			List.of(new Boat(1, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, 0)));
		assertNull(route(engine(), onLand, 3370, 3250, BoatMode.NORMAL).getBoatCard());
		// a mode left over is ignored off the boat
		assertNull(route(engine(), onLand, 3370, 3250, BoatMode.OCEAN_ONLY).getBoatCard());
		// the quickest route sails the boat itself (to open sea nearby): nothing to choose
		RouteResult sea = route(engine(), ABOARD, 3300, 3370, BoatMode.NORMAL);
		assertEquals("SAIL", kinds(sea));
		assertNull(sea.getBoatCard());
	}

	/** The route service keeps the choice: "Park my boat" for one route, ocean only until off the boat. */
	@Test
	public void theServiceKeepsTheChoice() throws Exception
	{
		BlockingQueue<RouteJob> jobs = new LinkedBlockingQueue<>();
		RouteService service = new RouteService(job ->
		{
			jobs.add(job);
			return RouteResults.ready(job.getGeneration(), job.getRequest(), List.of());
		}, r -> { });
		try
		{
			RouteRequest a = new RouteRequest(3250, 3250, 0);
			RouteRequest b = new RouteRequest(3260, 3260, 0);
			service.request(a, ABOARD, TransportSettings.defaults());
			assertEquals(BoatMode.NORMAL, jobs.poll(5, TimeUnit.SECONDS).getBoatMode());
			service.setBoatMode(BoatMode.PARK);
			service.reroute(ABOARD, TransportSettings.defaults());
			assertEquals(BoatMode.PARK, jobs.poll(5, TimeUnit.SECONDS).getBoatMode());
			service.refresh(ABOARD, TransportSettings.defaults());
			assertEquals("kept while following", BoatMode.PARK, jobs.poll(5, TimeUnit.SECONDS).getBoatMode());
			// a new destination: "just this route"
			service.request(b, ABOARD, TransportSettings.defaults());
			assertEquals(BoatMode.NORMAL, jobs.poll(5, TimeUnit.SECONDS).getBoatMode());
			// ocean only stays on for the next destination while on the boat...
			service.setBoatMode(BoatMode.OCEAN_ONLY);
			service.reroute(ABOARD, TransportSettings.defaults());
			assertEquals(BoatMode.OCEAN_ONLY, jobs.poll(5, TimeUnit.SECONDS).getBoatMode());
			service.request(a, ABOARD, TransportSettings.defaults());
			assertEquals(BoatMode.OCEAN_ONLY, jobs.poll(5, TimeUnit.SECONDS).getBoatMode());
			// ... until the player is off it ("Teleporting away removes the card")
			PlayerSnapshot off = new PlayerSnapshot(3250, 3250, 0, PlayerStateBuilder.maxed(),
				List.of(new Boat(1, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, Boat.LOST)));
			service.refresh(off, TransportSettings.defaults());
			assertEquals(BoatMode.NORMAL, jobs.poll(5, TimeUnit.SECONDS).getBoatMode());
			assertEquals(BoatMode.NORMAL, service.boatMode);
		}
		finally
		{
			service.shutdown();
		}
	}
}

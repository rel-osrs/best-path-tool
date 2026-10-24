package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.MenuTextBuilder;
import com.bestpath.core.data.NoRouteReasons;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataBuilder;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.req.PlayerState;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Step card titles are the game's menu text (the exact text the minimenu click would be, e.g. "construction cape ->
 * rimmington"): known texts the way the transport is used (two-way: up or down; a network ride:
 * the boarding stop's click with the landing stop's choice), on route cards and on "no route" cards; unknown ones keep
 * the old title (the transport's name, a teleport's with "Teleport: ").
 *
 * <p>Map: one region, planes 0 and 1, x 3200..3263, y 3200..3239 open on both planes, y 3240.. solid; on plane 0 a wall
 * at x 3240 (only the gap at y 3210, which needs Agility 99, crosses it).
 */
public class CardTitlesTest
{
	private static final PlayerState MEMBER = new PlayerState.Builder("Member").members(true).build();

	private static boolean open(int x, int y, int plane)
	{
		return x >= 3200 && x < 3264 && y >= 3200 && y < 3240 && (plane == 1 || x != 3240);
	}

	private static TransportLine line(String id, TransportLine.Kind kind, String name, String category, int[] start, int[] end,
		String network, String req, MenuText menu, MenuText back)
	{
		return TransportLine.builder(id, kind).name(name).category(category).start(start[0], start[1], start[2])
			.end(end[0], end[1], end[2]).ticks(2).network(network).requirements(req).menu(menu, back).build();
	}

	private static CoreRouteEngine engine(TransportLine... lines)
	{
		MovementRules.Flags p0 = (x, y) -> open(x, y, 0) ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags p1 = (x, y) -> open(x, y, 1) ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{p0, p1, none, none}, List.of(50 << 8 | 50), 0, 0);
		Tables tables = Tables.parse(List.of(), List.of(), List.of("Spirit tree\t4\t\ttree"), List.of(), List.of());
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(lines), tables,
			FreeLand.NONE);
		return new CoreRouteEngine(new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET));
	}

	private static RouteResult route(CoreRouteEngine e, int sx, int sy, int sp, int dx, int dy, int dp)
	{
		return e.route(new RouteJob(1, new RouteRequest(dx, dy, dp), new PlayerSnapshot(sx, sy, sp, MEMBER, List.of()),
			TransportSettings.defaults(), Set.of())).explained();
	}

	/** The title of the route's step with this transport id (a ride's id starts with the network's name). */
	private static String title(RouteResult r, String idStart)
	{
		assertEquals(RouteStatus.READY, r.getStatus());
		for (RouteStep s : r.getSteps())
		{
			if (s.getTransportId() != null && s.getTransportId().startsWith(idStart))
			{
				return s.getTitle();
			}
		}
		throw new AssertionError("no step " + idStart + " in " + r.getSteps());
	}

	private static final int[] NOWHERE = new int[3];

	private static TransportLine cape(MenuText menu)
	{
		return line("t-cape", TransportLine.Kind.TELEPORT, "Construction cape: Rimmington house portal", "TELEPORT", NOWHERE,
			new int[]{3259, 3235, 0}, "", "", menu, null);
	}

	private static TransportLine ladder(MenuText up, MenuText down)
	{
		return line("t-ladder", TransportLine.Kind.TWO_WAY, "Ladder", "CLIMB_BOTH", new int[]{3201, 3200, 0}, new int[]{3201, 3200, 1},
			"", "", up, down);
	}

	@Test
	public void aTeleportSaysItsItemAndTheChoice()
	{
		CoreRouteEngine e = engine(cape(MenuTextBuilder.choice("Teleport", "Construct. cape", "Rimmington")));
		assertEquals("Construct. cape -> Rimmington", title(route(e, 3200, 3200, 0, 3259, 3235, 0), "t-cape"));
		// a worn option that names the place
		CoreRouteEngine glory = engine(cape(MenuText.named("Edgeville", "Amulet of glory")));
		assertEquals("Amulet of glory -> Edgeville", title(route(glory, 3200, 3200, 0, 3259, 3235, 0), "t-cape"));
		// a spell: the game's menu line
		CoreRouteEngine spell = engine(cape(MenuText.verb("Cast", "Varrock Teleport")));
		assertEquals("Cast Varrock Teleport", title(route(spell, 3200, 3200, 0, 3259, 3235, 0), "t-cape"));
	}

	@Test
	public void unknownMenuTextKeepsTheOldTitle()
	{
		// a teleport: "Teleport: " unless its name says it already
		CoreRouteEngine e = engine(cape(null));
		assertEquals("Teleport: Construction cape: Rimmington house portal", title(route(e, 3200, 3200, 0, 3259, 3235, 0), "t-cape"));
		CoreRouteEngine l = engine(ladder(null, null));
		assertEquals("Ladder", title(route(l, 3200, 3200, 0, 3201, 3201, 1), "t-ladder"));
		// only one way known: the other way is the old title
		CoreRouteEngine half = engine(ladder(MenuText.verb("Climb-up", "Ladder"), null));
		assertEquals("Climb-up Ladder", title(route(half, 3200, 3200, 0, 3201, 3201, 1), "t-ladder"));
		assertEquals("Ladder", title(route(half, 3200, 3201, 1, 3201, 3201, 0), "t-ladder"));
	}

	@Test
	public void aTwoWayTransportSaysTheWayItIsUsed()
	{
		CoreRouteEngine e = engine(ladder(MenuText.verb("Climb-up", "Ladder"), MenuText.verb("Climb-down", "Ladder")));
		assertEquals("Climb-up Ladder", title(route(e, 3200, 3200, 0, 3201, 3201, 1), "t-ladder"));
		assertEquals("Climb-down Ladder", title(route(e, 3200, 3201, 1, 3201, 3201, 0), "t-ladder"));
	}

	private static TransportLine stop(String id, String place, int x, int y, String req, MenuText menu)
	{
		return line(id, TransportLine.Kind.NETWORK, "Spirit tree: " + place, "TRAVEL", new int[]{x, y, 0}, new int[]{x, y, 0},
			"Spirit tree", req, menu, null);
	}

	@Test
	public void aNetworkRideSaysTheBoardingClickAndTheLandingChoice()
	{
		CoreRouteEngine e = engine(
			stop("s-a", "Gnome Village", 3201, 3230, "", MenuTextBuilder.choice("Travel", "Spirit tree", "Tree Gnome Village")),
			stop("s-b", "Khazard", 3262, 3230, "", MenuTextBuilder.choice("Travel", "Spirit tree", "Battlefield of Khazard")));
		assertEquals("Spirit tree -> Battlefield of Khazard", title(route(e, 3201, 3229, 0, 3262, 3231, 0), "Spirit tree"));
		assertEquals("Spirit tree -> Tree Gnome Village", title(route(e, 3262, 3231, 0, 3201, 3229, 0), "Spirit tree"));
		// the boarding stop's click not known (only the choice that leads to it): the old title
		CoreRouteEngine half = engine(
			stop("s-a", "Gnome Village", 3201, 3230, "", MenuTextBuilder.choiceOnly("Tree Gnome Village")),
			stop("s-b", "Khazard", 3262, 3230, "", MenuTextBuilder.choice("Travel", "Spirit tree", "Battlefield of Khazard")));
		assertEquals("Spirit tree: Khazard", title(route(half, 3201, 3229, 0, 3262, 3231, 0), "Spirit tree"));
	}

	private static NoRouteWhy.Blocker blocker(CoreRouteEngine e, int sx, int sy, int dx, int dy)
	{
		RouteResult r = route(e, sx, sy, 0, dx, dy, 0);
		assertEquals(RouteStatus.NO_ROUTE, r.getStatus());
		NoRouteWhy why = r.getNoRouteWhy();
		assertNotNull(why);
		assertEquals(NoRouteReasons.Kind.BLOCKED, why.kind);
		assertEquals(1, why.blockers.size());
		return why.blockers.get(0);
	}

	@Test
	public void noRouteCardsUseTheMenuTextTheWayTheRouteWouldGo()
	{
		TransportLine gap = line("t-gap", TransportLine.Kind.TWO_WAY, "Gap", "SHORTCUT", new int[]{3239, 3210, 0},
			new int[]{3241, 3210, 0}, "", "Agility 99", MenuText.verb("Squeeze-through", "Gap"), MenuText.verb("Climb-over", "Gap"));
		CoreRouteEngine e = engine(gap);
		assertEquals("Squeeze-through Gap", blocker(e, 3220, 3210, 3250, 3210).getStep().getTitle());
		assertEquals("Climb-over Gap", blocker(e, 3250, 3210, 3220, 3210).getStep().getTitle());
		// not known: the name, as before
		CoreRouteEngine plain = engine(line("t-gap", TransportLine.Kind.TWO_WAY, "Gap", "SHORTCUT", new int[]{3239, 3210, 0},
			new int[]{3241, 3210, 0}, "", "Agility 99", null, null));
		assertEquals("Gap", blocker(plain, 3220, 3210, 3250, 3210).getStep().getTitle());
	}

	@Test
	public void aBlockedRideSaysTheBoardingClickAndTheLandingChoice()
	{
		CoreRouteEngine e = engine(
			stop("s-a", "Gnome Village", 3210, 3230, "", MenuTextBuilder.choice("Travel", "Spirit tree", "Tree Gnome Village")),
			stop("s-b", "Khazard", 3250, 3230, "Farming 99", MenuTextBuilder.choice("Travel", "Spirit tree", "Battlefield of Khazard")));
		NoRouteWhy.Blocker b = blocker(e, 3210, 3229, 3250, 3231);
		assertEquals("Spirit tree -> Battlefield of Khazard", b.getStep().getTitle());
	}

	@Test
	public void theArrowIsTheOneTheRuneScapeFontsHave()
	{
		// RuneLite's RuneScape fonts have no U+2192 (Java borrows it from a system font): the cards write "->"
		assertEquals("->", CoreRouteEngine.ARROW);
		assertTrue(StepTitles.transportTitle(null, MenuTextBuilder.choice("Teleport", "Construct. cape", "Rimmington"), true)
			.equals("Construct. cape -> Rimmington"));
		assertEquals("Teleport: Transport", StepTitles.transportTitle(null, null, true));
	}
}

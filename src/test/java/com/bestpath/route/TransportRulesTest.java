package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataBuilder;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WallBits;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.route.TransportSettings.Avoid;
import com.bestpath.route.TransportSettings.Category;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The Settings tab's categories and "avoid" rules on made-up transport lines shaped like the real export
 * (docs/PLUGIN_UI.md, "Settings tab: what each choice uses"), and their effect on routes.
 */
public class TransportRulesTest
{
	private static final int[] HOUSE = {1857, 7065, 0};
	private static final int[] NONE = {0, 0, 0};

	private static List<TransportLine> lines;
	private static TransportRules rules;
	private static PluginRouter router;

	private static TransportLine line(String id, TransportLine.Kind kind, String category, int[] start, int[] end, int ticks,
		boolean cooldown, String network, String req, String teleportCategory)
	{
		return TransportLine.builder(id, kind).name(id).category(category).start(start[0], start[1], start[2])
			.end(end[0], end[1], end[2]).ticks(ticks).cooldown(cooldown).network(network).requirements(req)
			.teleportCategory(teleportCategory).build();
	}

	@BeforeClass
	public static void data()
	{
		lines = List.of(
			line("spell", TransportLine.Kind.TELEPORT, "TELEPORT", NONE, new int[]{3259, 3259, 0}, 4, false, "",
				"Magic 25; rune law x1; rune air x3", "spell"),
			line("home", TransportLine.Kind.TELEPORT, "TELEPORT", NONE, new int[]{3240, 3259, 0}, 24, true, "", "",
				"home teleport"),
			line("minigame", TransportLine.Kind.TELEPORT, "TELEPORT", NONE, new int[]{3240, 3250, 0}, 24, true, "", "",
				"grouping teleport"),
			line("tablet", TransportLine.Kind.TELEPORT, "TELEPORT", NONE, new int[]{3250, 3200, 0}, 4, false, "",
				"item 8007 used up", "tablet"),
			line("glory", TransportLine.Kind.TELEPORT, "TELEPORT", NONE, new int[]{3250, 3210, 0}, 4, false, "", "item 1712",
				"jewellery (consumable)"),
			line("eternal", TransportLine.Kind.TELEPORT, "TELEPORT", NONE, new int[]{3250, 3220, 0}, 4, false, "", "item 19707",
				"jewellery (infinite)"),
			line("odd", TransportLine.Kind.TELEPORT, "TELEPORT", NONE, new int[]{3250, 3230, 0}, 4, false, "", "", ""),
			line("ladder", TransportLine.Kind.TWO_WAY, "CLIMB_UP", new int[]{3202, 3202, 0}, new int[]{3203, 3202, 0}, 2, false,
				"", "", ""),
			line("rocks", TransportLine.Kind.TWO_WAY, "SHORTCUT", new int[]{3204, 3204, 0}, new int[]{3206, 3204, 0}, 4, false,
				"", "Agility 10", ""),
			line("toll gate", TransportLine.Kind.TWO_WAY, "PASS", new int[]{3210, 3210, 0}, new int[]{3210, 3210, 1}, 2, false,
				"", "one of (coins 10 | quest Prince Ali Rescue)", ""),
			line("cave", TransportLine.Kind.TWO_WAY, "ENTER", new int[]{3220, 3220, 0}, new int[]{3221, 3220, 0}, 2, false,
				"", "", ""),
			line("custom", TransportLine.Kind.TWO_WAY, "CUSTOM", new int[]{3222, 3222, 0}, new int[]{3223, 3222, 0}, 2, false,
				"", "", ""),
			line("charter", TransportLine.Kind.ONE_WAY, "TRAVEL", new int[]{3230, 3230, 0}, new int[]{3231, 3231, 0}, 10, false,
				"Charter ship", "members; coins 1000", ""),
			line("ring A", TransportLine.Kind.NETWORK, "TRAVEL", NONE, new int[]{3205, 3205, 0}, 0, false, "Fairy ring", "", ""),
			line("ring B", TransportLine.Kind.NETWORK, "TRAVEL", NONE, new int[]{3255, 3255, 0}, 0, false, "Fairy ring", "", ""),
			line("soul portal", TransportLine.Kind.ONE_WAY, "ENTER", new int[]{3240, 3240, 0}, new int[]{3241, 3241, 0}, 2, false,
				"Soul Wars portal", "members", ""),
			line("house ride", TransportLine.Kind.ONE_WAY, "TRAVEL", HOUSE, new int[]{3245, 3245, 0}, 10, false,
				"Player-owned house", "members; house", ""),
			line("house portal", TransportLine.Kind.TWO_WAY, "TRAVEL", new int[]{3246, 3246, 0}, HOUSE, 2, false, "",
				"members; house at Rimmington", ""),
			line("tele to house", TransportLine.Kind.TELEPORT, "TELEPORT", NONE, HOUSE, 4, false, "",
				"Magic 40; rune law x1; house", "spell"));
		Tables tables = Tables.parse(List.of(), List.of(), List.of("Charter ship\t0\tmembers", "Fairy ring\t0\tmembers",
			"Player-owned house\t10\tmembers; house"), List.of("spell\t4", "home teleport\t24", "tablet\t4"), List.of());
		rules = new TransportRules(lines, tables);

		// plane 0 and plane 1 open; plane 1 is reached only through the toll gate
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, open, none, none}, List.of(50 << 8 | 50), 0, 0);
		List<TransportLine> routed = new ArrayList<>();
		for (TransportLine l : lines)
		{
			if (l.getId().equals("spell") || l.getId().equals("toll gate"))
			{
				routed.add(l);
			}
		}
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), routed, tables,
			FreeLand.NONE);
		router = new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
	}

	private static TransportLine byId(String id)
	{
		for (TransportLine l : lines)
		{
			if (l.getId().equals(id))
			{
				return l;
			}
		}
		throw new AssertionError(id);
	}

	private static Transport transportOf(TransportLine l)
	{
		return l.hasStart()
			? Transport.between(l.getId(), l.getName(), l.getStartX(), l.getStartY(), l.getStartPlane(), l.getEndX(), l.getEndY(),
			l.getEndPlane(), l.getTicks(), l.getKind() == TransportLine.Kind.TWO_WAY, true)
			: Transport.teleport(l.getId(), l.getName(), l.getEndX(), l.getEndY(), l.getEndPlane(), l.getTicks(), true);
	}

	private static Category category(String id)
	{
		TransportLine l = byId(id);
		return rules.category(transportOf(l), l);
	}

	@Test
	public void everyTransportIsInTheCategoryTheTableSays()
	{
		assertEquals(Category.TELEPORT_SPELLS, category("spell"));
		assertEquals(Category.TELEPORT_SPELLS, category("home"));
		assertEquals(Category.TELEPORT_SPELLS, category("minigame"));
		assertEquals(Category.TELEPORT_ITEMS, category("tablet"));
		assertEquals(Category.TELEPORT_ITEMS, category("glory"));
		assertEquals(Category.TELEPORT_ITEMS, category("eternal"));
		assertNull(category("odd")); // a teleport with no known category: always used
		assertEquals(Category.STAIRS_LADDERS, category("ladder"));
		assertEquals(Category.SHORTCUTS, category("rocks"));
		assertEquals(Category.DOORS, category("toll gate"));
		assertNull(category("cave"));
		assertNull(category("custom"));
		assertEquals(Category.BOATS, category("charter"));
		assertEquals(Category.BOATS, category("ring B"));
		assertEquals(Category.BOATS, category("soul portal")); // ENTER, but part of a network
		assertEquals(Category.HOUSE, category("house ride"));
		assertEquals(Category.HOUSE, category("house portal")); // TRAVEL, but it lands in the house
		assertEquals(Category.HOUSE, category("tele to house")); // a spell, but it lands in the house
		// a fairy ring ride from the house's ring (lineOf = the stop it goes to) is a house transport too
		Transport fromHouse = Transport.between("Fairy ring:house>B", "ring B", HOUSE[0], HOUSE[1], HOUSE[2], 3255, 3255, 0, 0,
			false, true);
		assertEquals(Category.HOUSE, rules.category(fromHouse, byId("ring B")));
		assertNull(rules.category(null, null));
	}

	@Test
	public void avoidRulesComeFromRequirementsTeleportCategoriesAndTheCooldownFlag()
	{
		assertEquals(Set.of(Avoid.RUNES), rules.avoidRules(byId("spell")));
		assertEquals(Set.of(Avoid.COOLDOWN), rules.avoidRules(byId("home")));
		assertEquals(Set.of(Avoid.COST), rules.avoidRules(byId("tablet"))); // item used up
		assertEquals(Set.of(Avoid.COST), rules.avoidRules(byId("glory"))); // consumable (charges)
		assertEquals(Set.of(), rules.avoidRules(byId("eternal")));
		assertEquals(Set.of(Avoid.COST), rules.avoidRules(byId("toll gate"))); // coins (even if a quest makes it free)
		assertEquals(Set.of(Avoid.COST), rules.avoidRules(byId("charter")));
		assertEquals(Set.of(), rules.avoidRules(byId("ring A")));
		assertEquals(Set.of(), rules.avoidRules(byId("ladder")));
		assertEquals(Set.of(Avoid.RUNES), rules.avoidRules(byId("tele to house")));
		// known bugs: no such mark in the data, so the rule is never applied and its box is disabled
		assertFalse(TransportRules.isSupported(Avoid.KNOWN_BUGS));
		TransportSettings bugs = TransportSettings.defaults().withAvoid(Avoid.KNOWN_BUGS, true);
		assertFalse(TransportRules.avoidsAnything(bugs));
		for (Category c : Category.values())
		{
			assertFalse(TransportRules.describe(c).isEmpty());
		}
	}

	private static RouteResult route(int dx, int dy, int dp, TransportSettings s)
	{
		PlayerSnapshot me = new PlayerSnapshot(3200, 3200, 0, PlayerStateBuilder.maxed(), List.of());
		return new CoreRouteEngine(router).route(new RouteJob(1, new RouteRequest(dx, dy, dp), me, s, Set.of()));
	}

	private static List<String> transportIds(RouteResult r)
	{
		List<String> ids = new ArrayList<>();
		for (RouteStep s : r.getSteps())
		{
			if (s.getTransportId() != null)
			{
				ids.add(s.getTransportId());
			}
		}
		return ids;
	}

	@Test
	public void aSwitchedOffCategoryIsNeverUsed()
	{
		assertEquals(List.of("spell"), transportIds(route(3260, 3260, 0, TransportSettings.defaults())));
		TransportSettings noSpells = TransportSettings.defaults().withCategory(Category.TELEPORT_SPELLS, false);
		RouteResult walk = route(3260, 3260, 0, noSpells);
		assertEquals(RouteStatus.READY, walk.getStatus());
		assertEquals(List.of(), transportIds(walk));
		assertEquals(60, walk.getTotalTicks());

		// other categories off: the spell is still used
		TransportSettings noBoats = TransportSettings.defaults().withCategory(Category.BOATS, false);
		assertEquals(List.of("spell"), transportIds(route(3260, 3260, 0, noBoats)));

		// gates off: plane 1 can't be reached at all
		TransportSettings noGates = TransportSettings.defaults().withCategory(Category.DOORS, false);
		assertEquals(RouteStatus.NO_ROUTE, route(3215, 3215, 1, noGates).getStatus());
	}

	@Test
	public void anAvoidedTransportIsUsedOnlyWhenNothingElseWorks()
	{
		// runes avoided: walking is possible, so the spell is not used
		TransportSettings noRunes = TransportSettings.defaults().withAvoid(Avoid.RUNES, true);
		RouteResult walk = route(3260, 3260, 0, noRunes);
		assertEquals(List.of(), transportIds(walk));
		assertTrue(walk.getNotes().isEmpty());

		// costs avoided: the toll gate is the only way up, so it is used, and the route says so
		TransportSettings noCost = TransportSettings.defaults().withAvoid(Avoid.COST, true);
		RouteResult up = route(3215, 3215, 1, noCost);
		assertEquals(RouteStatus.READY, up.getStatus());
		assertEquals(List.of("toll gate"), transportIds(up));
		assertEquals(List.of(CoreRouteEngine.AVOID_NOTE), up.getNotes());
		boolean marked = false;
		for (RouteStep s : up.getSteps())
		{
			marked |= "toll gate".equals(s.getTransportId()) && s.getNotes().contains(CoreRouteEngine.AVOIDED_STEP_NOTE);
		}
		assertTrue(marked);

		// a switched-off category is never used, even when nothing else works
		TransportSettings both = noCost.withCategory(Category.DOORS, false);
		assertEquals(RouteStatus.NO_ROUTE, route(3215, 3215, 1, both).getStatus());
	}

	@Test
	public void aSettingsChangeSearchesAgainLikeAnAvailabilityChange() throws Exception
	{
		BlockingQueue<RouteResult> published = new LinkedBlockingQueue<>();
		RouteService service = new RouteService(new CoreRouteEngine(router), published::add);
		try
		{
			PlayerSnapshot me = new PlayerSnapshot(3200, 3200, 0, PlayerStateBuilder.maxed(), List.of());
			service.request(new RouteRequest(3260, 3260, 0), me, TransportSettings.defaults());
			assertEquals(RouteStatus.CALCULATING, published.poll(5, TimeUnit.SECONDS).getStatus());
			RouteResult first = published.poll(5, TimeUnit.SECONDS);
			assertEquals(List.of("spell"), transportIds(first));

			// what the plugin does when a Settings box changes: a quiet search again with the new settings
			service.refresh(me, TransportSettings.defaults().withCategory(Category.TELEPORT_SPELLS, false));
			RouteResult second = published.poll(5, TimeUnit.SECONDS);
			assertEquals(RouteStatus.READY, second.getStatus()); // no "Calculating..." in between
			assertEquals(List.of(), transportIds(second));
			assertTrue(second.getGeneration() > first.getGeneration());
		}
		finally
		{
			service.shutdown();
		}
	}
}

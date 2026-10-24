package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
import com.bestpath.core.data.TickGroups;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.req.PlayerState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The player's own change of a whole teleport category or network (the Settings tab's "Teleport and
 * travel ticks", like the Data Tool's "Teleport and travel costs"): saved per account in the settings and used by the
 * plugin's routes on top of the shipped values, with each transport's own card change counting on top of it.
 */
public class CategoryTickChangesTest
{
	private static PluginRouter router;
	private static TickGroups groups;

	private static TransportLine line(String id, TransportLine.Kind kind, String network, String teleportCategory, int[] start,
		int[] end, int ticks)
	{
		return TransportLine.builder(id, kind).name(id).category("TRAVEL").start(start[0], start[1], start[2])
			.end(end[0], end[1], end[2]).ticks(ticks).network(network).teleportCategory(teleportCategory).build();
	}

	/** One open region (3200-3263, 3200-3263): a spell teleport (category 70 ticks) and two ring stops (60 + 4). */
	@BeforeClass
	public static void map()
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		List<TransportLine> lines = List.of(
			line("t-spell", TransportLine.Kind.TELEPORT, "", "spell", new int[3], new int[]{3259, 3259, 0}, 5),
			line("r-a", TransportLine.Kind.NETWORK, "Test ring", "", new int[3], new int[]{3201, 3230, 0}, 60),
			line("r-b", TransportLine.Kind.NETWORK, "Test ring", "", new int[3], new int[]{3262, 3230, 0}, 60));
		Tables tables = Tables.parse(List.of(), List.of(), List.of("Test ring\t4\t\tring"), List.of("spell\t70\tspells"), List.of());
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), lines, tables,
			FreeLand.NONE);
		router = new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
		groups = TickGroups.of(lines, tables);
	}

	private static RouteResult route(int sx, int sy, int dx, int dy, TransportSettings s)
	{
		CoreRouteEngine e = new CoreRouteEngine(router);
		PlayerSnapshot me = new PlayerSnapshot(sx, sy, 0, new PlayerState.Builder("Member").members(true).build(), List.of());
		return e.route(new RouteJob(3, new RouteRequest(dx, dy, 0), me, s, Set.of()));
	}

	private static RouteStep step(RouteResult r, String id)
	{
		for (RouteStep s : r.getSteps())
		{
			if (id.equals(s.getTransportId()))
			{
				return s;
			}
		}
		return null;
	}

	@Test
	public void groupChangesAreKeptByKeyAndSavedAndReadBack()
	{
		String spell = TransportSettings.teleportCategoryKey("Spell ");
		assertEquals("teleport:spell", spell);
		assertEquals("network:fairy ring", TransportSettings.networkKey("Fairy ring"));
		TransportSettings s = TransportSettings.defaults().withGroupTickChange(spell, -3)
			.withGroupTickChange(TransportSettings.networkKey("Fairy ring"), 2).withTickChange("t-x", 1, "X");
		assertEquals(-3, s.teleportCategoryTickChange("spell"));
		assertEquals(-3, s.teleportCategoryTickChange("SPELL"));
		assertEquals(2, s.networkTickChange("fairy ring"));
		assertEquals(0, s.networkTickChange("Spirit tree"));
		assertTrue(s.hasTickChanges());
		assertNotEquals(TransportSettings.defaults(), s);
		String text = s.encode();
		assertTrue(text, text.contains("groupticks=teleport%3Aspell -3\n"));
		assertTrue(text, text.contains("groupticks=network%3Afairy+ring 2\n"));
		TransportSettings read = TransportSettings.decode(text);
		assertEquals(s, read);
		assertEquals(s.encode(), read.encode());
		// other settings keep them; back at 0 they are removed
		TransportSettings more = s.withCategory(TransportSettings.Category.SHORTCUTS, false).withBlacklisted("t-y", "Y")
			.withAvoid(TransportSettings.Avoid.RUNES, true).withBoatAllowed(2, false).withShipwrights(false)
			.withTickChange("t-x", 0, null);
		assertEquals(-3, more.teleportCategoryTickChange("spell"));
		// "Never leave my boat at sea": off by default, saved as keepboat=on, kept by other changes
		assertFalse(TransportSettings.defaults().isKeepBoat());
		TransportSettings kept = more.withKeepBoat(true);
		assertTrue(kept.encode().contains("keepboat=on\n"));
		assertEquals(kept, TransportSettings.decode(kept.encode()));
		assertTrue(kept.withShipwrights(true).withBankTicks(20).isKeepBoat());
		assertEquals(more, kept.withKeepBoat(false));
		TransportSettings back = s.withGroupTickChange(spell, 0).withGroupTickChange("network:fairy ring", 0)
			.withTickChange("t-x", 0, null);
		assertEquals(TransportSettings.defaults(), back);
		assertEquals(TransportSettings.defaults().encode(), back.encode());
		// broken lines are skipped
		TransportSettings odd = TransportSettings.decode("v1\ngroupticks=teleport%3Aspell\ngroupticks=x 3\n"
			+ "groupticks=network%3Aa 0\ngroupticks=network%3Ab 5\n");
		assertEquals(Map.of("network:b", 5), odd.groupTicks);
	}

	@Test
	public void aLoweredCategoryIsUsedByRoutesAndTheCardShowsItsTotal()
	{
		RouteResult data = route(3200, 3200, 3260, 3260, TransportSettings.defaults());
		assertEquals("walking 60 tiles beats the 70-tick spell", 60, data.getTotalTicks());
		assertNull(step(data, "t-spell"));
		TransportSettings lowered = TransportSettings.defaults().withGroupTickChange(TransportSettings.teleportCategoryKey("spell"),
			-66);
		RouteResult r = route(3200, 3200, 3260, 3260, lowered);
		RouteStep spell = step(r, "t-spell");
		assertEquals(4, spell.getTicks());
		assertEquals("a category change is not the card's own", 0, spell.getTickChange());
		assertEquals(4 + 1, r.getTotalTicks());
		// the card's own change counts on top: the card's ticks without it are the category's
		TransportSettings both = lowered.withTickChange("t-spell", 2, "Spell");
		RouteStep s2 = step(route(3200, 3200, 3260, 3260, both), "t-spell");
		assertEquals(6, s2.getTicks());
		assertEquals(2, s2.getTickChange());
		assertEquals(4, s2.getTicks() - s2.getTickChange());
		// a category change and a card change that add up to 0: the card still knows its own part
		TransportSettings even = TransportSettings.defaults().withGroupTickChange("teleport:spell", -3)
			.withTickChange("t-spell", 3, "Spell");
		assertEquals(70, groups.ticks("t-spell", even.tickChanges()));
		// never below 0 by the category, and the card's change counts from there
		TransportSettings deep = TransportSettings.defaults().withGroupTickChange("teleport:spell", -90)
			.withTickChange("t-spell", 1, "Spell");
		RouteStep s3 = step(route(3200, 3200, 3260, 3260, deep), "t-spell");
		assertEquals(1, s3.getTicks());
		assertEquals(1, s3.getTickChange());
		assertEquals(s3.getTicks(), groups.ticks("t-spell", deep.tickChanges()));
	}

	@Test
	public void aNetworkChangeChangesEveryRide()
	{
		RouteResult data = route(3202, 3230, 3261, 3230, TransportSettings.defaults());
		assertEquals("walking 59 tiles beats the 64-tick ride", 59, data.getTotalTicks());
		TransportSettings lowered = TransportSettings.defaults().withGroupTickChange(TransportSettings.networkKey("Test ring"), -60);
		RouteResult r = route(3202, 3230, 3261, 3230, lowered);
		RouteStep ride = step(r, "Test ring:r-a>r-b");
		assertEquals(4, ride.getTicks());
		// a network card's - / + is the network's change
		assertEquals(-60, ride.getTickChange());
		assertEquals(TransportSettings.networkKey("Test ring"), ride.getTickKey());
		assertEquals(ride.getTicks(), groups.ticks("Test ring:r-a>r-b", lowered.tickChanges()));
		assertTrue(r.getTotalTicks() < 59);
		// raised: walking again
		RouteResult raised = route(3202, 3230, 3261, 3230, TransportSettings.defaults()
			.withGroupTickChange(TransportSettings.networkKey("test ring"), 5));
		assertEquals(59, raised.getTotalTicks());
	}
}

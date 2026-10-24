package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The player's own tick changes (- / + on a step card): saved per account in the settings, and
 * used by the plugin's routes (ticks, totals, which transport is used).
 */
public class TickChangesTest
{
	private static PluginRouter router;

	private static TransportLine line(String id, TransportLine.Kind kind, String name, String category, int[] start, int[] end,
		int ticks, String requirements, String back)
	{
		return TransportLine.builder(id, kind).name(name).category(category).start(start[0], start[1], start[2])
			.end(end[0], end[1], end[2]).ticks(ticks).requirements(requirements).requirementsBack(back).build();
	}

	/** One open region (3200-3263, 3200-3263, planes 0 and 1); a teleport, and a ladder only a mage may climb up. */
	@BeforeClass
	public static void map()
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, open, none, none}, List.of(50 << 8 | 50), 0, 0);
		List<TransportLine> lines = List.of(
			line("t-tele", TransportLine.Kind.TELEPORT, "Test teleport", "TRAVEL", new int[3], new int[]{3259, 3259, 0}, 5, "",
				null),
			line("t-ladder", TransportLine.Kind.TWO_WAY, "Ladder", "CLIMB_BOTH", new int[]{3201, 3200, 0},
				new int[]{3201, 3200, 1}, 3, "Magic 99", ""));
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), lines,
			Tables.empty(), FreeLand.NONE);
		router = new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
	}

	private static RouteResult route(int sx, int sy, int sp, int dx, int dy, int dp, TransportSettings s)
	{
		CoreRouteEngine e = new CoreRouteEngine(router);
		PlayerSnapshot me = new PlayerSnapshot(sx, sy, sp, PlayerStateBuilder.fresh(), List.of());
		return e.route(new RouteJob(3, new RouteRequest(dx, dy, dp), me, s, Set.of()));
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
	public void changesAreKeptPerTransportAndZeroRemovesThem()
	{
		TransportSettings s = TransportSettings.defaults().withTickChange("t-tele", 2, "Test teleport");
		assertEquals(2, s.tickChange("t-tele"));
		assertEquals(0, s.tickChange("t-other"));
		assertEquals(0, s.tickChange(null));
		assertEquals(Map.of("t-tele", 2), s.getTickChanges());
		assertEquals("Test teleport", s.tickName("t-tele"));
		assertNotEquals(TransportSettings.defaults(), s);
		// a split two-way transport's way back shares the transport's change (one tick value for both ways)
		TransportSettings l = s.withTickChange("t-ladder:back", -1, "Ladder");
		assertEquals(-1, l.tickChange("t-ladder"));
		assertEquals(-1, l.tickChange("t-ladder:back"));
		assertEquals("t-ladder", TransportSettings.tickKey("t-ladder:back"));
		assertEquals("Boats:A>B", TransportSettings.tickKey("Boats:A>B"));
		// back at +0 the change is removed
		TransportSettings back = l.withTickChange("t-tele", 0, null).withTickChange("t-ladder", 0, null);
		assertTrue(back.getTickChanges().isEmpty());
		assertEquals(TransportSettings.defaults(), back);
		assertEquals(TransportSettings.defaults().encode(), back.encode());
		// other settings keep the changes
		TransportSettings more = s.withCategory(TransportSettings.Category.SHORTCUTS, false).withBlacklisted("t-x", "X")
			.withAvoid(TransportSettings.Avoid.RUNES, true).withBoatAllowed(2, false).withShipwrights(false)
			.withoutBlacklisted("t-x");
		assertEquals(2, more.tickChange("t-tele"));
	}

	@Test
	public void changesAreSavedAndReadBack()
	{
		TransportSettings s = TransportSettings.defaults().withBlacklisted("t-x", "X")
			.withTickChange("Fairy rings:Zanaris>AJR", -3, "Fairy ring: AJR (Kandarin)")
			.withTickChange("t-tele", 12, "Test teleport");
		String text = s.encode();
		assertTrue(text, text.contains("ticks=Fairy+rings%3AZanaris%3EAJR -3 Fairy+ring%3A+AJR+%28Kandarin%29 "));
		TransportSettings read = TransportSettings.decode(text);
		assertEquals(s, read);
		assertEquals(-3, read.tickChange("Fairy rings:Zanaris>AJR"));
		assertEquals("Fairy ring: AJR (Kandarin)", read.tickName("Fairy rings:Zanaris>AJR"));
		assertEquals(List.of("Fairy rings:Zanaris>AJR", "t-tele"), List.copyOf(read.getTickChanges().keySet()));
		// broken or zero lines are skipped, the rest is kept
		TransportSettings odd = TransportSettings.decode("v1\nticks=a\nticks=b x name\nticks=c 0 C\nticks=d 4\nticks=e -2 E\n");
		assertEquals(Map.of("d", 4, "e", -2), odd.getTickChanges());
		assertEquals("d", odd.tickName("d"));
	}

	/** Each change keeps when it was made (to sort by how recently it was changed). */
	@Test
	public void whenEachChangeWasMadeIsSavedAndAnOlderSaveStillReads()
	{
		TransportSettings s = TransportSettings.defaults().withTickChange("a", 2, "A", 1000L).withTickChange("b", -1, "B", 2000L);
		assertEquals(2000L, s.tickTime("b"));
		String text = s.encode();
		assertTrue(text, text.contains("ticks=a 2 A 1000\n"));
		TransportSettings read = TransportSettings.decode(text);
		assertEquals(1000L, read.tickTime("a"));
		assertEquals(2000L, read.tickTime("b"));
		// changed again: the new time; reset: gone
		assertEquals(3000L, read.withTickChange("a", 3, "A", 3000L).tickTime("a"));
		assertEquals(0L, read.withTickChange("a", 0, "A", 3000L).tickTime("a"));
		// saved before times were kept: no time, the change itself read as before
		TransportSettings old = TransportSettings.decode("v1\nticks=a 2 A\n");
		assertEquals(2, old.tickChange("a"));
		assertEquals(0L, old.tickTime("a"));
		// a broken time keeps the change
		assertEquals(2, TransportSettings.decode("v1\nticks=a 2 A soon\n").tickChange("a"));
	}

	@Test
	public void routesUseTheChangedTicksAndTheTotalFollows()
	{
		RouteResult data = route(3200, 3200, 0, 3260, 3260, 0, TransportSettings.defaults());
		assertEquals(5 + 1, data.getTotalTicks());
		assertEquals(5, step(data, "t-tele").getTicks());
		assertEquals(0, step(data, "t-tele").getTickChange());

		RouteResult less = route(3200, 3200, 0, 3260, 3260, 0, TransportSettings.defaults().withTickChange("t-tele", -3, "T"));
		assertEquals(2 + 1, less.getTotalTicks());
		assertEquals(2, step(less, "t-tele").getTicks());
		assertEquals(-3, step(less, "t-tele").getTickChange());

		RouteResult zero = route(3200, 3200, 0, 3260, 3260, 0, TransportSettings.defaults().withTickChange("t-tele", -9, "T"));
		assertEquals("never below 0", 0 + 1, zero.getTotalTicks());
		assertEquals(0, step(zero, "t-tele").getTicks());
		assertEquals("the change the route really used", -5, step(zero, "t-tele").getTickChange());

		RouteResult dear = route(3200, 3200, 0, 3260, 3260, 0, TransportSettings.defaults().withTickChange("t-tele", 60, "T"));
		assertEquals("walking is quicker now", 60, dear.getTotalTicks());
		assertEquals(null, step(dear, "t-tele"));
		assertFalse(dear.getSteps().isEmpty());
	}

	@Test
	public void aWayBackUsesTheTransportsChange()
	{
		// the way up needs Magic 99: the fresh player only has the way down, "t-ladder:back"
		RouteResult data = route(3201, 3201, 1, 3201, 3201, 0, TransportSettings.defaults());
		RouteStep down = step(data, "t-ladder:back");
		assertEquals(3 + PluginRouter.PLANE_CHANGE_PENALTY, down.getTicks());
		assertEquals(1 + 3 + PluginRouter.PLANE_CHANGE_PENALTY + 1, data.getTotalTicks());
		RouteResult less = route(3201, 3201, 1, 3201, 3201, 0, TransportSettings.defaults().withTickChange("t-ladder:back", -4,
			"Ladder"));
		assertEquals(3 - 4 + PluginRouter.PLANE_CHANGE_PENALTY, step(less, "t-ladder:back").getTicks());
		assertEquals(-4, step(less, "t-ladder:back").getTickChange());
		assertEquals(1 + (3 - 4 + PluginRouter.PLANE_CHANGE_PENALTY) + 1, less.getTotalTicks());
	}
}

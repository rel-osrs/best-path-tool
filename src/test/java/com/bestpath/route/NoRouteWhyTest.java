package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.NoRouteReasons;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataBuilder;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.Skill;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * "No route" explains itself in the plugin's engine: each kind of reason in plain words, and a card
 * for each thing the way needs that the player can't use, with its marks and why. Map (as core's NoRouteReasonsTest):
 * one region, plane 0; y 3240..3263 solid; y 3200..3239 four areas with walls between at x 3216, 3232, 3248:
 * A 3200-3215 (the player), B 3217-3231, C 3233-3247, D 3249-3263 (no way in); free-to-play land A and B.
 */
public class NoRouteWhyTest
{
	private static boolean open(int x, int y)
	{
		return x >= 3200 && x < 3264 && y >= 3200 && y < 3240 && x != 3216 && x != 3232 && x != 3248;
	}

	private static CoreRouteEngine engine(TransportLine... lines)
	{
		MovementRules.Flags plane0 = (x, y) -> open(x, y) ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{plane0, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		FreeLand free = new FreeLand.Builder().set(3200, 3200, 3231, 3263, true).build();
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(lines),
			Tables.empty(), free);
		return new CoreRouteEngine(new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET));
	}

	/** A -> B. */
	private static TransportLine gate(String category, String req)
	{
		return TransportLine.builder("gate1", TransportLine.Kind.ONE_WAY).name("Gate").category(category)
			.start(3215, 3210, 0).end(3217, 3210, 0).ticks(2).requirements(req).build();
	}

	private static final PlayerState FRESH = PlayerStateBuilder.fresh();

	private static RouteJob job(int dx, int dy, PlayerState who, TransportSettings s, Set<String> skipped)
	{
		return new RouteJob(3, new RouteRequest(dx, dy, 0), new PlayerSnapshot(3205, 3205, 0, who, List.of()), s, skipped);
	}

	private static NoRouteWhy why(CoreRouteEngine e, RouteJob job)
	{
		RouteResult r = e.route(job).explained();
		assertEquals(RouteStatus.NO_ROUTE, r.getStatus());
		return r.getNoRouteWhy();
	}

	private static NoRouteWhy why(CoreRouteEngine e, int dx, int dy, PlayerState who)
	{
		return why(e, job(dx, dy, who, TransportSettings.defaults(), Set.of()));
	}

	/** The switched-off Settings category whose tick box the card shows, or null. */
	private static TransportSettings.Category turnOn(NoRouteWhy.Blocker b)
	{
		for (NoRouteWhy.Toggle t : b.getToggles())
		{
			if (t.getWhat() == NoRouteWhy.Toggle.What.TYPE)
			{
				return t.getCategory();
			}
		}
		return null;
	}

	@Test
	public void membersLandNowhereToStandAndNoKnownWay()
	{
		CoreRouteEngine e = engine();
		PlayerState free = new PlayerState.Builder("Free").members(false).build();
		NoRouteWhy members = why(e, 3240, 3220, free);
		assertEquals(NoRouteReasons.Kind.MEMBERS_LAND, members.kind);
		assertEquals("This is members-only land, and you're on a free-to-play world.", members.text());
		assertTrue(members.blockers.isEmpty());

		NoRouteWhy solid = why(e, 3210, 3257, FRESH);
		assertEquals("Nobody can stand there (water, a wall...).", solid.getHeadline());

		NoRouteWhy cutOff = why(e, 3259, 3220, FRESH);
		assertEquals(NoRouteReasons.Kind.NO_KNOWN_WAY, cutOff.kind);
		assertEquals("There's no known way there in our map data.", cutOff.text());
	}

	@Test
	public void aRequirementNotMetHasItsX()
	{
		CoreRouteEngine e = engine(gate("SHORTCUT", "Agility 30"));
		NoRouteWhy w = why(e, 3228, 3210, FRESH);
		assertEquals(NoRouteReasons.Kind.BLOCKED, w.kind);
		assertEquals("The way there needs 1 thing you can't use yet:", w.getHeadline());
		assertEquals(1, w.blockers.size());
		NoRouteWhy.Blocker b = w.blockers.get(0);
		assertEquals("Gate", b.getStep().getTitle());
		assertEquals(RouteStep.Kind.TRAVERSAL, b.getStep().getKind());
		StepInfo.Need n = b.getStep().getInfo().getNeeds().get(0);
		assertEquals(Skill.AGILITY, n.getSkill());
		assertEquals(NeedStatus.MISSING, n.getStatus());
		assertTrue(b.getMissing().isEmpty());
		assertTrue("the X says it", b.getReasons().isEmpty());
		assertNull(b.getRestoreId());
		// no coordinates anywhere
		assertTrue(w.text(), !w.text().matches(".*\\d{4}.*"));
	}

	@Test
	public void ofSeveralChoicesTheNearestIsMarked()
	{
		CoreRouteEngine e = engine(gate("SHORTCUT", "one of (Agility 70; quest Dragon Slayer | Agility 30)"));
		List<StepInfo.Need> needs = why(e, 3228, 3210, FRESH).blockers.get(0).getStep().getInfo().getNeeds();
		assertEquals(1, needs.size());
		assertEquals("Agility 30", needs.get(0).getText());
		assertEquals(NeedStatus.MISSING, needs.get(0).getStatus());
	}

	@Test
	public void aNeedWithoutAPictureIsAnXLine()
	{
		CoreRouteEngine e = engine(gate("SHORTCUT", "members"));
		PlayerState free = new PlayerState.Builder("Free").members(false).build();
		NoRouteWhy.Blocker b = why(e, 3228, 3210, free).blockers.get(0);
		assertEquals(List.of("A members world"), b.getMissing());
	}

	@Test
	public void varsAreWordsOnTheCardAndNumbersInTheDeveloperDetails()
	{
		// no varbit / varp text unless developer details are on; a quest's own var is "partial
		// progress through" it (varp 328 is Regicide's in quest-vars.tsv), any other var an unlock (two say it once)
		CoreRouteEngine e = engine(gate("SHORTCUT", "varp 328 >= 15; varbit 4897 > 0; varbit 4153 = 1"));
		NoRouteWhy.Blocker b = why(e, 3228, 3210, FRESH).blockers.get(0);
		assertEquals(List.of("Partial progress through Regicide", VarNotes.LOCKED), b.getMissing());
		assertEquals(List.of(), b.getReasons());
		assertEquals(List.of("[no] varp 328 >= 15 (is 0)", "[no] varbit 4897 > 0 (is 0)", "[no] varbit 4153 = 1 (is 0)"),
			b.getStep().getInfo().getCheckLines());
	}

	@Test
	public void turnedOffSkippedNeverUsed()
	{
		CoreRouteEngine e = engine(gate("PASS", ""));
		TransportSettings doorsOff = TransportSettings.defaults().withCategory(TransportSettings.Category.DOORS, false);
		NoRouteWhy.Blocker off = why(e, job(3228, 3210, FRESH, doorsOff, Set.of())).blockers.get(0);
		assertEquals(List.of("Turned off in Settings: Gates and barriers that need something."), off.getReasons());
		assertEquals(TransportSettings.Category.DOORS, turnOn(off)); // the type's tick box on its card
		assertEquals(List.of(NoRouteWhy.Toggle.type(TransportSettings.Category.DOORS)), off.getToggles());
		NoRouteWhy.Toggle box = off.getToggles().get(0);
		assertEquals("Gates and barriers that need something", box.getLabel());
		assertTrue(!box.isOn(doorsOff) && box.isOn(TransportSettings.defaults()));
		assertEquals(TransportSettings.defaults(), box.set(doorsOff, true));
		assertEquals(doorsOff, box.set(TransportSettings.defaults(), false));

		TransportSettings never = TransportSettings.defaults().withBlacklisted("gate1", "Gate");
		NoRouteWhy.Blocker black = why(e, job(3228, 3210, FRESH, never, Set.of())).blockers.get(0);
		assertEquals(List.of("You chose never to use this."), black.getReasons());
		assertEquals("gate1", black.getRestoreId());
		assertNull(turnOn(black));

		NoRouteWhy.Blocker skipped = why(e, job(3228, 3210, FRESH, TransportSettings.defaults(), Set.of("gate1"))).blockers.get(0);
		assertEquals(List.of("You skipped this for this route (Undo above allows it again)."), skipped.getReasons());
		assertNull(skipped.getRestoreId());
		assertTrue(skipped.isSkipped());
	}

	@Test
	public void aSkippedTransportGivesTheNextWay()
	{
		// "Skip" on a "no route" card: the way without it, when there is one
		TransportLine other = TransportLine.builder("gate2", TransportLine.Kind.ONE_WAY).name("Other gate")
			.category("SHORTCUT").start(3215, 3220, 0).end(3217, 3220, 0).ticks(2).requirements("Agility 60")
			.build();
		CoreRouteEngine e = engine(gate("SHORTCUT", "Agility 30"), other);
		assertEquals("Gate", why(e, 3228, 3210, FRESH).blockers.get(0).getStep().getTitle());
		NoRouteWhy next = why(e, job(3228, 3210, FRESH, TransportSettings.defaults(), Set.of("gate1")));
		assertEquals(NoRouteReasons.Kind.BLOCKED, next.kind);
		assertEquals(1, next.blockers.size());
		NoRouteWhy.Blocker b = next.blockers.get(0);
		assertEquals("Other gate", b.getStep().getTitle());
		assertTrue(!b.isSkipped() && b.getReasons().isEmpty());
		assertEquals("its Skip skips the card's own key", "gate2", b.getSkipId());
		// both skipped: no way without them, so the old cards, each saying it was skipped
		NoRouteWhy both = why(e, job(3228, 3210, FRESH, TransportSettings.defaults(), Set.of("gate1", "gate2")));
		assertEquals(NoRouteReasons.Kind.BLOCKED, both.kind);
		assertTrue(both.blockers.get(0).isSkipped());
	}

	@Test
	public void coolingDownSaysWhenItIsReady()
	{
		TransportLine home = TransportLine.builder("home", TransportLine.Kind.TELEPORT).name("Home Teleport")
			.category("TELEPORT").end(3228, 3220, 0).ticks(5).cooldown(true).teleportCategory("home teleport")
			.build();
		CoreRouteEngine e = engine(home);
		PlayerState used = new PlayerState.Builder("Used").members(true).coolingDown(Cooldown.HOME, 1_000L).build();
		RouteJob job = job(3228, 3220, used, TransportSettings.defaults(), Set.of());
		assertEquals(RouteStatus.NO_ROUTE, e.route(job).getStatus());
		NoRouteWhy w = e.whyNoRoute(job, PluginRouter.TransportFilter.NONE, 990L);
		NoRouteWhy.Blocker b = w.blockers.get(0);
		assertEquals(RouteStep.Kind.TELEPORT, b.getStep().getKind());
		assertEquals("Home Teleport", b.getStep().getTitle());
		assertEquals(List.of("Cooling down: ready again in about 10 min."), b.getReasons());
		for (StepInfo.Need n : b.getStep().getInfo().getNeeds())
		{
			assertTrue("no \"Ready\" on a cooling-down card", n.getType() != StepInfo.Need.Type.COOLDOWN);
		}
	}

	@Test
	public void theWhyIsWorkedOutAfterTheNoRouteIsShown()
	{
		// "no route" is shown first ("finding out why"), the why worked out after it (the route
		// service adds its time to "Calculated in")
		RouteResult r = engine(gate("SHORTCUT", "Agility 30")).route(job(3228, 3210, FRESH, TransportSettings.defaults(), Set.of()))
			.withSearchMillis(12);
		assertTrue(r.isFindingWhy());
		r = r.explained();
		assertEquals(12, r.getSearchMillis());
		assertEquals("kept with the time", NoRouteReasons.Kind.BLOCKED, r.getNoRouteWhy().kind);
	}
}

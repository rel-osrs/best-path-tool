package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.player.RequirementInputs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Home and minigame teleports while following a route (used only while ready), with the real
 * {@link RouteService} and {@link RouteFollower} and a fake engine that uses the home teleport only when the player's
 * snapshot says it is ready: the route is searched again once, quietly, when it turns ready; casting the route's own
 * home teleport is no reason to search again; using it elsewhere while the route still needs it later is.
 */
public class CooldownRerouteTest
{
	private static final RouteRequest DEST = new RouteRequest(3220, 3200, 0);
	private static final int START_X = 3100;
	private static final int START_Y = 3200;
	/** Where the home teleport lands. */
	private static final int HOME_X = 3222;
	private static final int HOME_Y = 3218;
	private static final TransportLine HOME = TransportLine.builder("home", TransportLine.Kind.TELEPORT)
		.name("Lumbridge Home Teleport").category("TELEPORT").end(HOME_X, HOME_Y, 0).ticks(124).cooldown(true)
		.requirements("spellbook standard").teleportCategory("home teleport").wildernessLimit(20).build();

	private final RouteFollowerTest.ManualExecutor worker = new RouteFollowerTest.ManualExecutor();
	private final List<RouteJob> jobs = new ArrayList<>();
	private final RequirementInputs inputs = RequirementInputs.NONE.withCooldowns(Set.of(Cooldown.HOME));
	private final RouteService service = new RouteService(this::engine, r -> { }, worker);
	private final RouteFollower follower = new RouteFollower(service, TransportSettings::defaults, () -> inputs, p -> { });
	/** The route has a ladder before the home teleport (see {@link #engine}). */
	private boolean ladderFirst;
	private PlayerSnapshot now;
	private int snapshotsTaken;

	private static PlayerSnapshot player(int x, int y, boolean homeReady)
	{
		PlayerState.Builder b = new PlayerState.Builder("p").members(true);
		if (!homeReady)
		{
			b.coolingDown(Cooldown.HOME, 29_000_030L);
		}
		return new PlayerSnapshot(x, y, 0, b.build(), List.of());
	}

	private static RouteStep homeTeleport()
	{
		return new RouteStep(RouteStep.Kind.TELEPORT, "Lumbridge Home Teleport", "", 124, List.of(), "home", new int[0], -1,
			Tiles.pack(HOME_X, HOME_Y, 0)).withInfo(StepInfo.of(HOME, false, null, null));
	}

	/** Fake engine: the home teleport and a walk when it is ready, else a long walk east. */
	private RouteResult engine(RouteJob job)
	{
		jobs.add(job);
		PlayerSnapshot p = job.getPlayer();
		List<RouteStep> steps = new ArrayList<>();
		if (p.getState().cooldownReady(Cooldown.HOME))
		{
			if (ladderFirst)
			{
				steps.add(RouteProgressTest.walk(p.getX(), p.getY(), p.getX() + 3, p.getY(), 0));
				steps.add(RouteProgressTest.transport("ladder", p.getX() + 3, p.getY(), 0, p.getX() + 3, p.getY(), 1, 2));
			}
			steps.add(homeTeleport());
			steps.add(RouteProgressTest.walk(HOME_X, HOME_Y, DEST.getX(), DEST.getY(), 0));
		}
		else
		{
			steps.add(RouteProgressTest.walk(p.getX(), p.getY(), DEST.getX(), DEST.getY(), 0));
		}
		return RouteResults.ready(job.getGeneration(), job.getRequest(), steps);
	}

	private PlayerSnapshot takeSnapshot()
	{
		snapshotsTaken++;
		return now;
	}

	private void tick(int t, int x, int y)
	{
		now = new PlayerSnapshot(x, y, 0, now.getState(), List.of());
		follower.onTick(t, x, y, 0, -1, this::takeSnapshot);
	}

	private void start(boolean homeReady)
	{
		now = player(START_X, START_Y, homeReady);
		service.request(DEST, now, TransportSettings.defaults());
		worker.runAll();
		assertEquals(RouteStatus.READY, service.getCurrent().getStatus());
	}

	@Test
	public void readyAgainSearchesOnceQuietly()
	{
		start(false);
		assertNull("cooling down: the route walks", service.getCurrent().getSteps().get(0).getInfo().getCooldown());
		tick(1, START_X, START_Y);

		// the home teleport turns ready (the plugin's CooldownWatch says so): one quiet search
		now = player(START_X, START_Y, true);
		follower.inputsChanged(2);
		for (int t = 2; t <= 2 + RouteFollower.QUIET_TICKS; t++)
		{
			tick(t, START_X + 1, START_Y);
		}
		worker.runAll();
		assertEquals(2, jobs.size());
		RouteStep first = service.getCurrent().getSteps().get(0);
		assertEquals(Cooldown.HOME, first.getInfo().getCooldown());
		assertEquals("Ready", first.getInfo().getNeeds().get(0).getText());

		// a minute later nothing changed: no search
		follower.inputsChanged(10);
		for (int t = 10; t <= 10 + RouteFollower.QUIET_TICKS; t++)
		{
			tick(t, START_X + 1, START_Y);
		}
		worker.runAll();
		assertEquals(2, jobs.size());
	}

	@Test
	public void castingTheRoutesOwnHomeTeleportDoesNotSearchAgain()
	{
		start(true);
		assertEquals(Cooldown.HOME, service.getCurrent().getSteps().get(0).getInfo().getCooldown());
		tick(1, START_X, START_Y);
		assertEquals(0, follower.getProgress().getStep());

		// the player casts it: its cooldown starts while the player still stands there (casting takes ~20 ticks)
		now = player(START_X, START_Y, false);
		follower.inputsChanged(2);
		for (int t = 2; t <= 12; t++)
		{
			tick(t, START_X, START_Y);
		}
		worker.runAll();
		assertTrue("the change was looked at", snapshotsTaken >= 1);
		assertEquals("no new search", 1, jobs.size());

		// landed in Lumbridge: the route goes on with its walk
		tick(13, HOME_X, HOME_Y);
		assertEquals(1, follower.getProgress().getStep());
		assertEquals(1, jobs.size());
	}

	@Test
	public void usingItElsewhereWhileTheRouteStillNeedsItSearchesAgain()
	{
		ladderFirst = true;
		start(true);
		List<RouteStep> steps = service.getCurrent().getSteps();
		assertEquals(Set.of(Cooldown.HOME), RouteFollower.cooldownsAhead(steps, 0));
		assertEquals("the next transport is the home teleport: the player is doing it", Set.of(),
			RouteFollower.cooldownsAhead(steps, 2));
		// a bank visit is not a transport the player moves by: the home teleport right after it is the one being cast
		RouteStep bank = new RouteStep(RouteStep.Kind.BANK, "Bank", "", 30, List.of(), "bank", new int[0], -1);
		assertEquals(Set.of(), RouteFollower.cooldownsAhead(List.of(bank, homeTeleport()), 0));
		tick(1, START_X, START_Y);
		assertEquals(0, follower.getProgress().getStep());

		// the home teleport is used before the ladder: the route's later home teleport can't be done any more
		now = player(START_X, START_Y, false);
		follower.inputsChanged(2);
		for (int t = 2; t <= 2 + RouteFollower.QUIET_TICKS; t++)
		{
			tick(t, START_X, START_Y);
		}
		worker.runAll();
		assertEquals(2, jobs.size());
		assertNull(service.getCurrent().getSteps().get(0).getInfo().getCooldown());
	}
}

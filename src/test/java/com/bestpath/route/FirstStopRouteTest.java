package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.bestpath.core.data.ClueData;
import com.bestpath.core.data.ClueDataDiagnostics;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.data.MapDataLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.Test;

/**
 * A route with a first stop (put the items taken out of a STASH unit back before the next clue step): the stop's card between the two parts, a STASH-only route, a skipped stop, an unreachable stop; then once on
 * the real exported map data.
 */
public class FirstStopRouteTest
{
	private static final PlayerSnapshot ME = new PlayerSnapshot(3200, 3200, 0, PlayerStateBuilder.fresh(), List.of());
	private static final int STASH_TILE = Tiles.pack(3210, 3210, 0);
	private static final RouteRequest.Stop STOP = new RouteRequest.Stop("stash-return:5229", STASH_TILE,
		"Put the items back in the STASH unit (Somewhere)", List.of("Items: Iron med helm"));
	private static final RouteRequest CLUE = RouteRequest.places("clue STEP:medium:x,", "Emote clue (medium)", List.of("A note"),
		new int[]{Tiles.pack(3300, 3300, 0)});

	/** A made-up search: one walk from the player straight to the request's first place; records what it was asked. */
	private static final class Fake implements Function<RouteJob, RouteResult>
	{
		final List<RouteJob> asked = new ArrayList<>();
		Set<Integer> unreachable = Set.of();

		@Override
		public RouteResult apply(RouteJob job)
		{
			asked.add(job);
			RouteRequest r = job.getRequest();
			int to = r.getTargets()[0];
			if (unreachable.contains(to))
			{
				return RouteResult.status(job.getGeneration(), r, RouteStatus.NO_ROUTE, "No route");
			}
			PlayerSnapshot p = job.getPlayer();
			int from = Tiles.pack(p.getX(), p.getY(), p.getPlane());
			// stops next to the place (as for a blocked STASH unit)
			int end = Tiles.pack(Tiles.x(to) - 1, Tiles.y(to), Tiles.plane(to));
			RouteStep walk = new RouteStep(RouteStep.Kind.WALK, "Walk", "", 10, List.of(), null, new int[]{from, end}, -1);
			return RouteResults.ready(job.getGeneration(), r, List.of(walk), to, List.of("searched " + Tiles.x(to)));
		}
	}

	private static RouteJob job(RouteRequest r, Set<String> skipped)
	{
		return new RouteJob(7, r, ME, TransportSettings.defaults(), skipped);
	}

	@Test
	public void stopCardBetweenTheTwoParts()
	{
		Fake one = new Fake();
		RouteRequest r = CLUE.withFirstStop(STOP);
		RouteResult res = FirstStopRoute.route(job(r, Set.of()), one);
		assertEquals(RouteStatus.READY, res.getStatus());
		assertSame("the result names the whole request", r, res.getRequest());
		List<RouteStep> s = res.getSteps();
		assertEquals(3, s.size());
		RouteStep stop = s.get(1);
		assertEquals(RouteStep.Kind.STASH, stop.getKind());
		assertEquals(STOP.getTitle(), stop.getTitle());
		assertEquals(STOP.getLines(), stop.getNotes());
		assertEquals("Skip on the card skips the stop", "stash-return:5229", stop.getTransportId());
		int besideStash = Tiles.pack(3209, 3210, 0);
		assertEquals(besideStash, stop.getEndTile());
		assertEquals(besideStash, stop.getMarkerTile());
		// part 2 starts where part 1 ended, without the stop, with the clue's needs
		assertEquals(2, one.asked.size());
		assertNull(one.asked.get(0).getRequest().getFirstStop());
		assertEquals(STASH_TILE, one.asked.get(0).getRequest().getTargets()[0]);
		RouteJob second = one.asked.get(1);
		assertEquals(3209, second.getPlayer().getX());
		assertNull(second.getRequest().getFirstStop());
		assertEquals(CLUE, second.getRequest());
		assertEquals(Tiles.pack(3300, 3300, 0), res.getDestination());
		assertEquals("the clue part's notes", List.of("searched 3300"), res.getNotes());
		assertEquals(20, res.getTotalTicks());
	}

	@Test
	public void stashOnlyRouteEndsWithTheCard()
	{
		Fake one = new Fake();
		RouteRequest r = RouteRequest.places("stash stash-return:5229", "Put the items back in the STASH unit", List.of(),
			new int[]{STASH_TILE}).withFirstStop(STOP);
		assertTrue(r.isStopOnly());
		RouteResult res = FirstStopRoute.route(job(r, Set.of()), one);
		assertEquals(1, one.asked.size());
		assertEquals(2, res.getSteps().size());
		assertEquals(RouteStep.Kind.STASH, res.getSteps().get(1).getKind());
		assertEquals(STASH_TILE, res.getDestination());
	}

	@Test
	public void skippedStopGoesStraightThere()
	{
		Fake one = new Fake();
		RouteResult res = FirstStopRoute.route(job(CLUE.withFirstStop(STOP), Set.of(STOP.getId())), one);
		assertEquals(1, one.asked.size());
		assertEquals(1, res.getSteps().size());
		assertEquals(Tiles.pack(3300, 3300, 0), one.asked.get(0).getRequest().getTargets()[0]);
	}

	@Test
	public void unreachableStopGoesStraightOnWithANote()
	{
		Fake one = new Fake();
		one.unreachable = Set.of(STASH_TILE);
		RouteResult res = FirstStopRoute.route(job(CLUE.withFirstStop(STOP), Set.of()), one);
		assertEquals(RouteStatus.READY, res.getStatus());
		assertEquals(1, res.getSteps().size());
		assertEquals(FirstStopRoute.UNREACHABLE_NOTE, res.getNotes().get(0));
		// a STASH-only route to an unreachable unit: no route
		RouteRequest only = RouteRequest.places("stash x", "Put back", List.of(), new int[]{STASH_TILE}).withFirstStop(STOP);
		assertEquals(RouteStatus.NO_ROUTE, FirstStopRoute.route(job(only, Set.of()), one).getStatus());
	}

	private static final int NPC_TILE = Tiles.pack(3250, 3250, 0);
	private static final RouteRequest.Stop KILL = new RouteRequest.Stop("key-npc:cryptic-2279", NPC_TILE,
		"Kill for the key: Barbarian", List.of("Pick up the key"), RouteStep.Kind.KILL);

	/** Put the STASH items back, then kill for the key, then the clue's place. */
	@Test
	public void twoStopsInTurn()
	{
		Fake one = new Fake();
		RouteRequest r = CLUE.withFirstStops(java.util.Arrays.asList(STOP, null, KILL));
		assertEquals(List.of(STOP, KILL), r.getFirstStops());
		assertTrue(!r.isStopOnly());
		RouteResult res = FirstStopRoute.route(job(r, Set.of()), one);
		assertEquals(RouteStatus.READY, res.getStatus());
		List<RouteStep> s = res.getSteps();
		assertEquals(5, s.size());
		assertEquals(RouteStep.Kind.STASH, s.get(1).getKind());
		assertEquals(RouteStep.Kind.KILL, s.get(3).getKind());
		assertEquals("Kill for the key: Barbarian", s.get(3).getTitle());
		assertEquals("Skip on the kill card skips that stop", KILL.getId(), s.get(3).getTransportId());
		assertEquals(3, one.asked.size());
		assertEquals("part 2 starts beside the STASH unit", 3209, one.asked.get(1).getPlayer().getX());
		assertEquals(NPC_TILE, one.asked.get(1).getRequest().getTargets()[0]);
		assertEquals("part 3 starts beside the NPC", 3249, one.asked.get(2).getPlayer().getX());
		assertEquals(CLUE, one.asked.get(2).getRequest());
		assertEquals(Tiles.pack(3300, 3300, 0), res.getDestination());
		assertEquals(30, res.getTotalTicks());
	}

	@Test
	public void killStopSkippedOrOutOfReach()
	{
		Fake one = new Fake();
		RouteResult skipped = FirstStopRoute.route(job(CLUE.withFirstStop(KILL), Set.of(KILL.getId())), one);
		assertEquals(1, skipped.getSteps().size());
		assertEquals(List.of("searched 3300"), skipped.getNotes());
		one.unreachable = Set.of(NPC_TILE);
		RouteResult far = FirstStopRoute.route(job(CLUE.withFirstStops(List.of(STOP, KILL)), Set.of()), one);
		assertEquals(RouteStatus.READY, far.getStatus());
		assertEquals("the STASH stop stays, the kill stop is left out", 3, far.getSteps().size());
		assertEquals(RouteStep.Kind.STASH, far.getSteps().get(1).getKind());
		assertEquals(FirstStopRoute.KILL_UNREACHABLE_NOTE, far.getNotes().get(0));
	}

	@Test
	public void stopKinds()
	{
		assertEquals(RouteStep.Kind.STASH, STOP.getKind());
		assertTrue(!STOP.equals(new RouteRequest.Stop(STOP.getId(), STOP.getTile(), STOP.getTitle(), STOP.getLines(),
			RouteStep.Kind.KILL)));
		assertTrue(RouteStep.Kind.KILL.isStop() && RouteStep.Kind.STASH.isStop() && !RouteStep.Kind.BANK.isStop());
		try
		{
			new RouteRequest.Stop("x", NPC_TILE, "t", List.of(), RouteStep.Kind.WALK);
			throw new AssertionError("a walk is no stop");
		}
		catch (IllegalArgumentException expected)
		{
			// fine
		}
	}

	@Test
	public void requestKeepsItsStopWithNewNeedsButNotInEquals()
	{
		RouteRequest r = CLUE.withFirstStop(STOP);
		assertEquals(STOP, r.withNeeds(DestinationNeeds.NONE).getFirstStop());
		assertEquals("same destination with or without the stop (skips are kept)", CLUE, r);
		assertNull(r.withoutFirstStop().getFirstStop());
		assertSame(CLUE, CLUE.withoutFirstStop());
		assertTrue(!CLUE.withFirstStop(STOP).isStopOnly());
	}

	/** On the real exported map: Lumbridge -> a real STASH unit -> Varrock square, as three parts with the card. */
	@Test
	public void realMap()
	{
		RouteEngine e = MapDataLoader.engine();
		assumeTrue("no exported map data", e instanceof CoreRouteEngine);
		ClueData clues = ((CoreRouteEngine) e).getRouter().getData().getClues();
		ClueData.Step draynor = null;
		for (ClueData.Step s : clues.getSteps())
		{
			// a STASH unit near Draynor Village / Lumbridge
			if (s.getStash() != -1 && s.getStashVarbit() > 0 && Tiles.plane(s.getStash()) == 0
				&& Math.abs(Tiles.x(s.getStash()) - 3100) < 40 && Math.abs(Tiles.y(s.getStash()) - 3250) < 40)
			{
				draynor = s;
				break;
			}
		}
		assumeTrue("no STASH unit near Draynor in the data", draynor != null);
		RouteRequest.Stop stop = new RouteRequest.Stop("stash-return:" + draynor.getStashVarbit(), draynor.getStash(),
			"Put the items back in the STASH unit (" + draynor.getStashPlace() + ")", List.of("Items: test"));
		RouteRequest r = new RouteRequest(3213, 3428, 0).withFirstStop(stop);
		PlayerSnapshot me = new PlayerSnapshot(3222, 3218, 0, PlayerStateBuilder.fresh(), List.of());
		RouteResult res = e.route(new RouteJob(1, r, me, TransportSettings.defaults(), Set.of()));
		assertEquals(res.getMessage(), RouteStatus.READY, res.getStatus());
		RouteStep card = null;
		int at = -1;
		for (int i = 0; i < res.getSteps().size(); i++)
		{
			if (res.getSteps().get(i).getKind() == RouteStep.Kind.STASH)
			{
				card = res.getSteps().get(i);
				at = i;
			}
		}
		assertNotNull("the STASH card is in the route", card);
		assertTrue("walks before and after it", at > 0 && at < res.getSteps().size() - 1);
		int end = card.getEndTile();
		assertTrue("the card is next to the unit",
			Math.max(Math.abs(Tiles.x(end) - Tiles.x(draynor.getStash())), Math.abs(Tiles.y(end) - Tiles.y(draynor.getStash()))) <= 3);
		assertEquals(Tiles.pack(3213, 3428, 0), res.getDestination());
	}

	/**
	 * On the real exported map: the Exam Centre key clue (cryptic-2279) without its key goes
	 * to the barbarians first (the cache's key-NPC tile), then to the chest.
	 */
	@Test
	public void realMapKeyClueGoesToTheNpcFirst()
	{
		RouteEngine e = MapDataLoader.engine();
		assumeTrue("no exported map data", e instanceof CoreRouteEngine);
		ClueData.Step step = ClueDataDiagnostics.byKey(((CoreRouteEngine) e).getRouter().getData().getClues(), "cryptic-2279");
		assumeTrue("an export with the key-NPC columns", step != null && step.getKeyNpc() != -1);
		assertEquals(Tiles.pack(3082, 3419, 0), step.getKeyNpc());
		assertEquals(7297, step.getKeyItem());
		com.bestpath.clue.DetectedClue d = com.bestpath.clue.DetectedClueTestAccess.step(step);
		RouteRequest.Stop kill = com.bestpath.clue.KeyStop.stop(d, PlayerStateBuilder.fresh(), id -> "Key (medium)");
		assertNotNull(kill);
		PlayerSnapshot me = new PlayerSnapshot(3222, 3218, 0, PlayerStateBuilder.fresh(), List.of());
		RouteResult res = e.route(new RouteJob(1, d.request().withFirstStop(kill), me, TransportSettings.defaults(), Set.of()));
		assertEquals(res.getMessage(), RouteStatus.READY, res.getStatus());
		int at = -1;
		for (int i = 0; i < res.getSteps().size(); i++)
		{
			if (res.getSteps().get(i).getKind() == RouteStep.Kind.KILL)
			{
				at = i;
			}
		}
		assertTrue("the kill card comes before the walk to the chest", at > 0 && at < res.getSteps().size() - 1);
		int end = res.getSteps().get(at).getEndTile();
		assertTrue("the kill card is at the barbarians",
			Math.max(Math.abs(Tiles.x(end) - 3082), Math.abs(Tiles.y(end) - 3419)) <= 3);
		assertEquals(Tiles.pack(3353, 3332, 0), res.getDestination());
	}
}

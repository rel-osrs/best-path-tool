package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.api.BestPathApi;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * Which plugin a route belongs to, and what that plugin is told (docs/API.md): found, no route, replaced, cleared,
 * arrived, the step the player is on. Part one by hand; part two with the real route service and route follower.
 */
public class ApiRoutesTest
{
	private final List<ApiRoutes.Out> sent = new ArrayList<>();
	private final ApiRoutes api = new ApiRoutes(sent::add);

	private static ApiRequest msg(String source, Object id, int x, int y)
	{
		Map<String, Object> m = new HashMap<>();
		m.put("source", source);
		m.put("id", id);
		m.put("target", new WorldPoint(x, y, 0));
		return ApiRequest.read(m);
	}

	private static RouteResult ready(long gen, RouteRequest r)
	{
		return RouteResults.ready(gen, r, List.of(RouteProgressTest.walk(3200, 3200, r.getX(), r.getY(), 0)));
	}

	/** The last message's status (or name for other messages) and id. */
	private String last()
	{
		ApiRoutes.Out o = sent.get(sent.size() - 1);
		Object state = o.getData().get(BestPathApi.STATUS_KEY);
		return (BestPathApi.STATUS.equals(o.getName()) ? state : o.getName()) + " " + o.getData().get("id");
	}

	/** Asks for the route as the plugin does: path, then the route service shows CALCULATING. */
	private RouteRequest ask(ApiRequest a, RouteResult shown, long gen)
	{
		RouteRequest r = api.path(a, true, shown);
		assertNotNull(r);
		api.published(RouteResult.status(gen, r, RouteStatus.CALCULATING, ""), -1); // the service publishes at once...
		api.started(RouteResult.status(gen, r, RouteStatus.CALCULATING, ""), -1); // ...then the bridge says it started
		return r;
	}

	@Test
	public void foundOnceThenAgainOnlyForANewSearch()
	{
		RouteRequest r = ask(msg("A", 1, 3210, 3200), RouteResult.idle(0), 1);
		assertTrue(sent.isEmpty()); // calculating: nothing yet
		RouteResult found = ready(1, r);
		api.published(found, Tiles.pack(3200, 3200, 0));
		assertEquals("FOUND 1", last());
		assertEquals(10, sent.get(0).getData().get("cost"));
		assertEquals("A", sent.get(0).getData().get("source"));
		api.published(found.withSearchMillis(3), -1); // the same generation (cards made again): not told twice
		assertEquals(1, sent.size());
		api.published(ready(2, r), -1); // searched again (the player left the route)
		assertEquals(2, sent.size());
		assertEquals("FOUND 1", last());
		assertNotNull(api.owner);
	}

	@Test
	public void theStepThePlayerIsOnIsToldWhenItChanges()
	{
		RouteRequest r = ask(msg("A", 1, 3210, 3200), RouteResult.idle(0), 1);
		RouteResult found = ready(1, r);
		api.published(found, -1);
		api.progress(new Progress(1, 0, 0), found);
		api.progress(new Progress(1, 0, 5), found); // same step, further along: not told
		assertEquals(2, sent.size());
		assertEquals(BestPathApi.PROGRESS, sent.get(1).getName());
		assertEquals(0, sent.get(1).getData().get(BestPathApi.STEP_INDEX));
		api.progress(Progress.NONE, found);
		api.progress(new Progress(9, 1, 0), found); // another route's progress
		assertEquals(2, sent.size());
	}

	@Test
	public void anotherDestinationReplacesItAndTheOwnerIsToldOnce()
	{
		RouteRequest r = ask(msg("A", 1, 3210, 3200), RouteResult.idle(0), 1);
		api.published(ready(1, r), -1);
		api.published(RouteResult.status(2, new RouteRequest(3300, 3300, 0), RouteStatus.CALCULATING, ""), -1);
		assertEquals("REPLACED 1", last());
		assertNull(api.owner);
		int n = sent.size();
		api.published(ready(2, new RouteRequest(3300, 3300, 0)), -1); // the player's route: none of A's business
		api.published(RouteResult.idle(3), -1);
		assertEquals(n, sent.size());
	}

	@Test
	public void aNewPathFromAnotherPluginReplacesTheFirst()
	{
		RouteRequest r = ask(msg("A", 1, 3210, 3200), RouteResult.idle(0), 1);
		RouteResult shown = ready(1, r);
		api.published(shown, -1);
		ask(msg("B", "b", 3220, 3200), shown, 2);
		assertEquals("REPLACED 1", last());
		api.published(ready(2, msg("B", "b", 3220, 3200).toRoute()), -1);
		assertEquals("FOUND b", last());
		assertEquals("B", sent.get(sent.size() - 1).getData().get("source"));
	}

	@Test
	public void theSameRouteAgainIsNotSearchedAgain()
	{
		RouteRequest r = ask(msg("A", 1, 3210, 3200), RouteResult.idle(0), 1);
		RouteResult shown = ready(1, r);
		api.published(shown, -1);
		assertNull(api.path(msg("A", 2, 3210, 3200), true, shown));
		assertEquals(1, sent.size());
		api.published(ready(2, r), -1);
		assertEquals("FOUND 2", last()); // the newest id from now on
	}

	@Test
	public void clearedByThePlayerOrByItsOwnSourceOnly()
	{
		RouteRequest r = ask(msg("A", 1, 3210, 3200), RouteResult.idle(0), 1);
		RouteResult shown = ready(1, r);
		api.published(shown, -1);
		assertFalse(api.clear("B", shown));
		assertFalse(api.clear(ApiRequest.NO_SOURCE, shown));
		assertTrue(api.clear("A", shown));
		assertFalse(api.clear("A", ready(2, new RouteRequest(1, 1, 0)))); // not the route shown
		api.published(RouteResult.idle(2), -1); // the plugin cleared it
		assertEquals("CLEARED 1", last());
		assertNull(api.owner);
		assertFalse(api.clear("A", RouteResult.idle(2)));
	}

	@Test
	public void badMessagesAndTheSwitchOffAreAnsweredAndChangeNothing()
	{
		assertNull(api.path(ApiRequest.read(Map.of("target", "Varrock", "id", 4)), true, RouteResult.idle(0)));
		assertEquals("INVALID 4", last());
		assertEquals("Not a place: String", sent.get(0).getData().get("message"));
		assertNull(api.path(ApiRequest.read(Map.of("id", 5)), true, RouteResult.idle(0)));
		assertEquals("INVALID 5", last());
		assertNull(api.path(msg("A", 6, 3210, 3200), false, RouteResult.idle(0)));
		assertEquals("DISABLED 6", last());
		assertNull(api.owner);
	}

	@Test
	public void anOlderSearchFinishingMeanwhileIsNotTheNewRoute()
	{
		RouteRequest mine = new RouteRequest(3300, 3300, 0);
		RouteRequest r = api.path(msg("A", 1, 3210, 3200), true, RouteResult.status(4, mine, RouteStatus.CALCULATING, ""));
		api.published(ready(4, mine), -1); // the player's search ends before ours starts
		assertTrue(sent.isEmpty());
		assertNotNull(api.owner);
		api.started(RouteResult.status(5, r, RouteStatus.CALCULATING, ""), -1);
		api.published(ready(5, r), -1);
		assertEquals("FOUND 1", last());
	}

	@Test
	public void notLoggedInIsAnErrorAtOnce()
	{
		RouteRequest r = api.path(msg("A", 1, 3210, 3200), true, RouteResult.idle(0));
		RouteResult failed = RouteResult.status(1, r, RouteStatus.ERROR, "Log in to find a route.");
		api.published(failed, -1); // published inside the request, before started
		api.started(failed, -1);
		assertEquals("ERROR 1", last());
		assertEquals("Log in to find a route.", sent.get(0).getData().get("message"));
		assertEquals(1, sent.size());
	}

	@Test
	public void noRouteIsToldOnceItsWhyIsWorkedOut()
	{
		RouteRequest r = ask(msg("A", 1, 3210, 3200), RouteResult.idle(0), 1);
		RouteResult finding = RouteResult.noRouteFindingWhy(1, r, "No route.", List.of(), () -> null);
		api.published(finding, -1);
		assertTrue(sent.isEmpty());
		api.published(finding.explained(), -1);
		assertEquals("NO_ROUTE 1", last());
		assertEquals(1, sent.size());
	}

	// ---- with the real route service and follower ----

	private final RouteFollowerTest.ManualExecutor worker = new RouteFollowerTest.ManualExecutor();
	private RouteService service;

	private RouteResult engine(RouteJob job)
	{
		PlayerSnapshot p = job.getPlayer();
		RouteRequest r = job.getRequest();
		return RouteResults.ready(job.getGeneration(), r, List.of(RouteProgressTest.walk(p.getX(), p.getY(), r.getX(), r.getY(), 0)));
	}

	private RouteFollower follow()
	{
		service = new RouteService(this::engine, r -> api.published(r, -1), worker);
		RouteFollower f = new RouteFollower(service, TransportSettings::defaults, () -> com.bestpath.player.RequirementInputs.NONE,
			p -> api.progress(p, service.getCurrent()));
		f.setArrivalListener(api::arrived);
		return f;
	}

	private void request(ApiRequest a, PlayerSnapshot me)
	{
		RouteRequest r = api.path(a, true, service.getCurrent());
		service.request(r, me, TransportSettings.defaults());
		api.started(service.getCurrent(), -1);
		worker.runAll();
	}

	@Test
	public void followedToTheEndItIsToldArrivedOnce()
	{
		RouteFollower f = follow();
		PlayerSnapshot me = new PlayerSnapshot(3200, 3200, 0, PlayerStateBuilder.fresh(), List.of());
		request(msg("A", 1, 3220, 3200), me);
		assertEquals("FOUND 1", last());
		f.onTick(1, 3200, 3200, 0, -1, () -> me);
		assertEquals("progress 1", last());
		f.onTick(2, 3219, 3200, 0, -1, () -> me);
		assertEquals(RouteStatus.ARRIVED, service.getCurrent().getStatus());
		assertEquals("ARRIVED 1", last());
		assertEquals(3, sent.size()); // FOUND, progress, ARRIVED: the route's end is not told again
		assertNull(api.owner);
	}

	@Test
	public void withClearOnArrivalOffArrivedIsStillTold()
	{
		RouteFollower f = follow();
		f.setClearOnArrival(false);
		PlayerSnapshot me = new PlayerSnapshot(3200, 3200, 0, PlayerStateBuilder.fresh(), List.of());
		request(msg("A", 1, 3220, 3200), me);
		f.onTick(1, 3200, 3200, 0, -1, () -> me);
		f.onTick(2, 3219, 3200, 0, -1, () -> me);
		f.onTick(3, 3219, 3200, 0, -1, () -> me);
		assertEquals(RouteStatus.READY, service.getCurrent().getStatus());
		assertEquals("ARRIVED 1", last());
		assertEquals(3, sent.size());
		service.clear(); // the player clears it afterwards
		assertEquals("CLEARED 1", last());
	}

	@Test
	public void queryAnswersWithoutChangingTheRouteShown()
	{
		follow();
		PlayerSnapshot me = new PlayerSnapshot(3200, 3200, 0, PlayerStateBuilder.fresh(), List.of());
		request(msg("A", 1, 3220, 3200), me);
		RouteResult shown = service.getCurrent();
		List<RouteResult> answers = new ArrayList<>();
		service.query(msg("A", 2, 3205, 3200).toRoute(), me.at(3201, 3200, 0), TransportSettings.defaults(), answers::add);
		worker.runAll();
		assertEquals(1, answers.size());
		assertEquals(4, answers.get(0).getTotalTicks()); // from the start given: 3201 -> 3205
		assertEquals(shown, service.getCurrent());
		assertEquals(1, sent.size()); // A was told nothing about the query
	}

	@Test
	public void aQueryWhoseSearchFailsIsAnsweredError()
	{
		service = new RouteService(job ->
		{
			throw new IllegalStateException("broken");
		}, r -> { }, worker);
		List<RouteResult> answers = new ArrayList<>();
		service.query(new RouteRequest(1, 1, 0), new PlayerSnapshot(0, 0, 0, PlayerStateBuilder.fresh(), List.of()), TransportSettings.defaults(),
			answers::add);
		worker.runAll();
		assertEquals(RouteStatus.ERROR, answers.get(0).getStatus());
	}
}

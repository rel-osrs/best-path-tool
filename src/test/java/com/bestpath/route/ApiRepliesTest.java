package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.api.BestPathApi;
import com.bestpath.core.path.Tiles;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** The data of Best Path's answers (docs/API.md): Shortest Path's keys, plus Best Path's own. */
public class ApiRepliesTest
{
	/** The keys of a Shortest Path {@code result}, which a caller written for it reads. */
	private static final Set<String> SHORTEST_PATH_RESULT = Set.of("id", "reached", "start", "target", "path", "transports",
		"closest", "cost", "reason");

	private static final RouteRequest DEST = RouteRequest.places("api: test", "Test", List.of(),
		new int[]{Tiles.pack(3210, 3201, 1)});

	/** Walk 3200,3200 -> 3202,3200; climb the ladder at 3203,3200 to 3203,3201 plane 1; walk to 3210,3201. */
	private static RouteResult route()
	{
		RouteStep walk = new RouteStep(RouteStep.Kind.WALK, "Walk", "", 2, List.of(), null,
			new int[]{Tiles.pack(3200, 3200, 0), Tiles.pack(3201, 3200, 0), Tiles.pack(3202, 3200, 0)}, -1);
		RouteStep ladder = new RouteStep(RouteStep.Kind.TRAVERSAL, "Climb up the ladder", "", 3, List.of(), "t-1",
			new int[0], Tiles.pack(3203, 3200, 0), Tiles.pack(3203, 3201, 1))
			.withClick(new ClickHint("Climb-up", "Ladder", null, 16683, Tiles.pack(3203, 3200, 0), new int[0], false, -1));
		RouteStep walk2 = new RouteStep(RouteStep.Kind.WALK, "Walk", "", 7, List.of(), null,
			new int[]{Tiles.pack(3203, 3201, 1), Tiles.pack(3210, 3201, 1)}, -1);
		return RouteResults.ready(5, DEST, List.of(walk, ladder, walk2));
	}

	@Test
	public void aFoundRouteHasShortestPathsKeysAndBestPathsSteps()
	{
		Map<String, Object> m = ApiReplies.result("q1", route(), Tiles.pack(3200, 3200, 0));
		assertTrue(m.keySet().containsAll(SHORTEST_PATH_RESULT));
		assertEquals("q1", m.get("id"));
		assertEquals(true, m.get("reached"));
		assertEquals(BestPathApi.REASON_TARGET_REACHED, m.get("reason"));
		assertEquals(12, m.get("cost"));
		assertEquals(new WorldPoint(3200, 3200, 0), m.get("start"));
		assertEquals(new WorldPoint(3210, 3201, 1), m.get("target"));
		assertEquals(BestPathApi.API_VERSION, m.get(BestPathApi.API_VERSION_KEY));
		assertEquals(List.of(new WorldPoint(3200, 3200, 0), new WorldPoint(3201, 3200, 0), new WorldPoint(3202, 3200, 0),
			new WorldPoint(3203, 3200, 0), new WorldPoint(3203, 3201, 1), new WorldPoint(3210, 3201, 1)), m.get("path"));

		List<?> transports = (List<?>) m.get("transports");
		assertEquals(1, transports.size());
		Map<?, ?> t = (Map<?, ?>) transports.get(0);
		assertEquals(new WorldPoint(3203, 3200, 0), t.get("origin"));
		assertEquals(new WorldPoint(3203, 3201, 1), t.get("destination"));
		assertEquals("Climb-up Ladder 16683", t.get("objectInfo"));
		assertEquals("Climb up the ladder", t.get("displayInfo"));

		List<?> steps = (List<?>) m.get("steps");
		assertEquals(3, steps.size());
		Map<?, ?> s = (Map<?, ?>) steps.get(1);
		assertEquals("TRAVERSAL", s.get("kind"));
		assertEquals("Climb up the ladder", s.get("title"));
		assertEquals(3, s.get("ticks"));
		assertEquals(new WorldPoint(3203, 3201, 1), s.get("end"));
	}

	@Test
	public void noRouteAndOtherFailuresAreNotReached()
	{
		Map<String, Object> m = ApiReplies.result(1, RouteResults.noRoute(5, DEST, "No route.", List.of(), null),
			Tiles.pack(3200, 3200, 0));
		assertTrue(m.keySet().containsAll(SHORTEST_PATH_RESULT));
		assertEquals(false, m.get("reached"));
		assertEquals(BestPathApi.REASON_SEARCH_EXHAUSTED, m.get("reason"));
		assertEquals(List.of(), m.get("path"));
		assertEquals(-1, m.get("cost"));
		assertEquals("No route.", m.get("message"));
		assertEquals(BestPathApi.REASON_NO_DATA,
			ApiReplies.result(1, RouteResult.status(5, DEST, RouteStatus.NO_DATA, "Loading"), -1).get("reason"));
		assertEquals(BestPathApi.REASON_ERROR,
			ApiReplies.result(1, RouteResult.status(5, DEST, RouteStatus.ERROR, "Oops"), -1).get("reason"));
		Map<String, Object> bad = ApiReplies.failure(null, BestPathApi.REASON_INVALID, -1, "Not a place: String");
		assertTrue(bad.keySet().containsAll(SHORTEST_PATH_RESULT));
		assertNull(bad.get("start"));
	}

	@Test
	public void aStepWithoutAnObjectHasNoObjectInfo()
	{
		assertNull(ApiReplies.objectInfo(ClickHint.NONE));
		assertNull(ApiReplies.objectInfo(new ClickHint("Cast", "Varrock Teleport", null, -1, -1, new int[0], true, -1)));
	}

	@Test
	public void currentTargetListsEveryPlace()
	{
		RouteRequest two = RouteRequest.places("k", "T", List.of(), new int[]{Tiles.pack(1, 2, 0), Tiles.pack(3, 4, 1)});
		Map<String, Object> m = ApiReplies.currentTarget("x", two, Tiles.pack(3200, 3200, 0));
		assertEquals(true, m.get("set"));
		assertEquals(new WorldPoint(3200, 3200, 0), m.get("start"));
		assertEquals(List.of(new WorldPoint(1, 2, 0), new WorldPoint(3, 4, 1)), m.get("target"));
		Map<String, Object> none = ApiReplies.currentTarget("x", null, -1);
		assertEquals(false, none.get("set"));
		assertFalse(none.containsKey("target"));
	}

	@Test
	public void statusFoundCarriesTheRouteOthersDoNot()
	{
		Map<String, Object> found = ApiReplies.status(3, "My Plugin", BestPathApi.STATUS_FOUND, route(), -1);
		assertEquals("FOUND", found.get("status"));
		assertEquals("My Plugin", found.get("source"));
		assertEquals(12, found.get("cost"));
		Map<String, Object> replaced = ApiReplies.status(3, "My Plugin", BestPathApi.STATUS_REPLACED, route(), -1);
		assertFalse(replaced.containsKey("cost"));
	}
}

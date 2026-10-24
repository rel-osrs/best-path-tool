package com.bestpath.route;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.api.BestPathApi;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** Reading the public API's {@code path} / {@code query} messages (docs/API.md), the way Shortest Path's are written. */
public class ApiRequestTest
{
	/** True for a route asked for through the API (by any source). */
	private static boolean isApiRoute(RouteRequest r)
	{
		return r != null && r.getKey() != null && r.getKey().startsWith(ApiRequest.KEY_PREFIX);
	}

	private static Map<String, Object> data(Object... keysAndValues)
	{
		Map<String, Object> m = new HashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2)
		{
			m.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return m;
	}

	@Test
	public void oneWorldPointWithoutASource()
	{
		ApiRequest a = ApiRequest.read(data("target", new WorldPoint(3222, 3218, 0)));
		assertTrue(a.isValid());
		assertTrue(a.hasTargets());
		assertArrayEquals(new int[]{Tiles.pack(3222, 3218, 0)}, a.targets);
		assertEquals(ApiRequest.NO_SOURCE, a.getSource());
		assertNull(a.getId());
		assertEquals(-1, a.getStart());
		RouteRequest r = a.toRoute();
		assertEquals("Destination from another plugin", r.getTitle());
		assertEquals(List.of("Set by another plugin: 3222, 3218, 0."), r.getNotes());
		assertTrue(isApiRoute(r));
		assertFalse(isApiRoute(new RouteRequest(3222, 3218, 0)));
	}

	@Test
	public void aSetOfPointsAndShortestPathPackedNumbersIsTheNearestOf()
	{
		Set<Object> targets = new LinkedHashSet<>();
		targets.add(new WorldPoint(3222, 3218, 0));
		targets.add(BestPathApi.pack(3093, 3493, 2)); // plane 2: a negative number in this layout
		targets.add(BestPathApi.pack(3222, 3218, 0)); // the same place twice: once
		ApiRequest a = ApiRequest.read(data("target", targets, "source", "My Plugin", "id", 7));
		assertTrue(a.isValid());
		assertArrayEquals(new int[]{Tiles.pack(3222, 3218, 0), Tiles.pack(3093, 3493, 2)}, a.targets);
		assertEquals(7, a.getId());
		RouteRequest r = a.toRoute();
		assertEquals("Destination from My Plugin", r.getTitle());
		assertEquals(List.of("Set by My Plugin: the nearest of 2 places."), r.getNotes());
		assertEquals(2, r.targetCount());
	}

	@Test
	public void packedPlacesRoundTrip()
	{
		for (int plane = 0; plane <= 3; plane++)
		{
			int p = BestPathApi.pack(12345, 9876, plane);
			assertEquals(12345, BestPathApi.unpackX(p));
			assertEquals(9876, BestPathApi.unpackY(p));
			assertEquals(plane, BestPathApi.unpackPlane(p));
		}
		// Shortest Path's layout, written out: x bits 0-14, y bits 15-29, plane bits 30-31
		assertEquals(3222 | 3218 << 15 | 1 << 30, BestPathApi.pack(3222, 3218, 1));
	}

	@Test
	public void titleAndNotesAreShownAndCut()
	{
		List<Object> notes = new ArrayList<>(List.of("one", "two", 3, "  ", "four", "five", "six", "seven"));
		String longTitle = "x".repeat(300);
		ApiRequest a = ApiRequest.read(data("target", new WorldPoint(3222, 3218, 0), "title", longTitle, "notes", notes));
		RouteRequest r = a.toRoute();
		assertEquals(ApiRequest.MAX_TEXT, r.getTitle().length());
		assertTrue(r.getTitle().endsWith("..."));
		assertEquals(List.of("one", "two", "four", "five", "six"), r.getNotes());
		assertEquals(List.of("just one"),
			ApiRequest.read(data("target", new WorldPoint(1, 1, 0), "notes", "just one")).toRoute().getNotes());
	}

	@Test
	public void anythingThatIsNotAPlaceMakesTheMessageInvalid()
	{
		assertFalse(ApiRequest.read(data("target", "Varrock")).isValid());
		assertFalse(ApiRequest.read(data("target", BestPathApi.UNDEFINED)).isValid());
		assertFalse(ApiRequest.read(data("target", Set.of(new WorldPoint(3222, 3218, 0), "Varrock"))).isValid());
		assertFalse(ApiRequest.read(data("target", List.of(List.of(new WorldPoint(3222, 3218, 0))))).isValid());
		assertFalse(ApiRequest.read(data("target", new WorldPoint(20000, 3218, 0))).isValid());
		assertFalse(ApiRequest.read(data("target", new WorldPoint(3222, 3218, 4))).isValid());
		List<WorldPoint> many = new ArrayList<>();
		for (int i = 0; i <= BestPathApi.MAX_TARGETS; i++)
		{
			many.add(new WorldPoint(1000 + i % 1000, 1000 + i / 1000, 0));
		}
		ApiRequest tooMany = ApiRequest.read(data("target", many));
		assertFalse(tooMany.isValid());
		assertEquals("More than " + BestPathApi.MAX_TARGETS + " places", tooMany.getProblem());
		assertFalse(ApiRequest.read(data("target", new WorldPoint(3222, 3218, 0), "start", Set.of(new WorldPoint(1, 1, 0))))
			.isValid());
	}

	@Test
	public void noTargetIsReadButHasNothingToGoTo()
	{
		assertTrue(ApiRequest.read(null).isValid());
		assertFalse(ApiRequest.read(null).hasTargets());
		assertFalse(ApiRequest.read(data("target", Set.of())).hasTargets());
	}

	@Test
	public void startIsReadAndShortestPathsConfigIsIgnored()
	{
		ApiRequest a = ApiRequest.read(data("target", new WorldPoint(3222, 3218, 0), "start", new WorldPoint(3200, 3200, 1),
			"config", Map.of("useFairyRings", false)));
		assertTrue(a.isValid());
		assertEquals(Tiles.pack(3200, 3200, 1), a.getStart());
		assertEquals(Tiles.pack(3201, 3200, 0), ApiRequest.read(data("start", BestPathApi.pack(3201, 3200, 0))).getStart());
	}

	@Test
	public void theSameSourceAndPlacesAreTheSameRoute()
	{
		RouteRequest one = ApiRequest.read(data("target", new WorldPoint(3222, 3218, 0), "source", "A", "id", 1)).toRoute();
		RouteRequest again = ApiRequest.read(data("target", new WorldPoint(3222, 3218, 0), "source", "A", "id", 2)).toRoute();
		RouteRequest other = ApiRequest.read(data("target", new WorldPoint(3222, 3218, 0), "source", "B")).toRoute();
		assertEquals(one, again);
		assertFalse(one.equals(other));
	}
}

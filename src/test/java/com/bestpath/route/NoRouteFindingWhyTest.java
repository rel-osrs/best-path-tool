package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.NoRouteReasons;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;

/**
 * A setting disables the no-route explanations, and the card says "no route, finding out why" while the explanation is
 * calculated. A "no route" is published at once, still finding out why,
 * then again with the why; with explanations off the why is never worked out.
 */
public class NoRouteFindingWhyTest
{
	private static final PlayerSnapshot ME = new PlayerSnapshot(3200, 3200, 0, PlayerStateBuilder.fresh(), List.of());
	private static final RouteRequest A = new RouteRequest(3210, 3210, 0);
	private static final NoRouteWhy WHY = NoRouteWhy.ofWays(NoRouteReasons.Kind.BLOCKED, "You need a key", List.of(), List.of());

	private final BlockingQueue<RouteResult> published = new LinkedBlockingQueue<>();
	private final AtomicInteger explained = new AtomicInteger();
	private RouteService service;

	@After
	public void stop()
	{
		if (service != null)
		{
			service.shutdown();
		}
	}

	private RouteResult noRoute(RouteJob job)
	{
		return RouteResult.noRouteFindingWhy(job.getGeneration(), job.getRequest(), "No route to the destination.",
			List.of(), () ->
			{
				explained.incrementAndGet();
				return WHY;
			});
	}

	private RouteResult next() throws InterruptedException
	{
		RouteResult r = published.poll(5, TimeUnit.SECONDS);
		if (r == null)
		{
			throw new AssertionError("nothing published");
		}
		return r;
	}

	@Test
	public void noRouteIsShownFirstThenWhy() throws Exception
	{
		service = new RouteService(this::noRoute, published::add);
		service.request(A, ME, TransportSettings.defaults());
		assertEquals(RouteStatus.CALCULATING, next().getStatus());
		RouteResult first = next();
		assertEquals(RouteStatus.NO_ROUTE, first.getStatus());
		assertTrue(first.isFindingWhy());
		assertNull(first.getNoRouteWhy());
		RouteResult second = next();
		assertEquals(first.getGeneration(), second.getGeneration());
		assertFalse(second.isFindingWhy());
		assertSame(WHY, second.getNoRouteWhy());
		// "Calculated in": the search and the explanation together
		assertTrue(second.getSearchMillis() >= first.getSearchMillis());
		assertSame(second, service.getCurrent());
		assertEquals(1, explained.get());
	}

	@Test
	public void switchedOffNothingIsWorkedOut() throws Exception
	{
		service = new RouteService(this::noRoute, published::add);
		service.setExplainNoRoute(false);
		service.request(A, ME, TransportSettings.defaults());
		assertEquals(RouteStatus.CALCULATING, next().getStatus());
		RouteResult r = next();
		assertEquals(RouteStatus.NO_ROUTE, r.getStatus());
		assertFalse(r.isFindingWhy());
		assertNull(r.getNoRouteWhy());
		assertEquals("No route to the destination.", r.getMessage());
		assertNull("published once", published.poll(300, TimeUnit.MILLISECONDS));
		assertEquals(0, explained.get());
	}

	@Test
	public void aFailedExplanationKeepsTheEngineLine() throws Exception
	{
		service = new RouteService(job -> RouteResult.noRouteFindingWhy(job.getGeneration(), job.getRequest(), "No route.",
			List.of(), () ->
			{
				throw new IllegalStateException("boom");
			}), published::add);
		service.request(A, ME, TransportSettings.defaults());
		next();
		assertTrue(next().isFindingWhy());
		RouteResult r = next();
		assertFalse(r.isFindingWhy());
		assertNull(r.getNoRouteWhy());
		assertEquals("No route.", r.getMessage());
	}
}

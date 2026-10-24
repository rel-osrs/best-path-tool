package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

public class RouteServiceTest
{
	private static final PlayerSnapshot ME = new PlayerSnapshot(3200, 3200, 0, PlayerStateBuilder.fresh(), List.of());
	private static final RouteRequest A = new RouteRequest(3210, 3210, 0);
	private static final RouteRequest B = new RouteRequest(3220, 3220, 0);

	private final BlockingQueue<RouteResult> published = new LinkedBlockingQueue<>();
	private final BlockingQueue<RouteJob> jobs = new LinkedBlockingQueue<>();
	private RouteService service;

	@After
	public void stop()
	{
		if (service != null)
		{
			service.shutdown();
		}
	}

	/** An engine that records its jobs and answers with one walk step. */
	private RouteResult answer(RouteJob job)
	{
		jobs.add(job);
		RouteStep s = new RouteStep(RouteStep.Kind.WALK, "Walk", "", 10, List.of(), null, new int[0], -1);
		return RouteResults.ready(job.getGeneration(), job.getRequest(), List.of(s));
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
	public void cardsAreMadeAgainForTheItemsNowWithoutASearch() throws Exception
	{
		// item requirement cards update to show what the player has on them
		int torch = 20720;
		com.bestpath.core.req.Requirement req = com.bestpath.core.req.RequirementText.parse("item light source",
			com.bestpath.core.req.RequirementText.ItemGroups.of(java.util.Map.of("light source", new int[]{33, torch})));
		com.bestpath.core.data.TransportLine cave = com.bestpath.core.data.TransportLine.builder("t-2", com.bestpath.core.data.TransportLine.Kind.ONE_WAY)
			.name("Crypt Entrance").category("ENTER").start(3200, 3200, 0).end(3200, 9600, 0).ticks(4)
			.requirements("item light source").build();
		service = new RouteService(job ->
		{
			jobs.add(job);
			RouteStep s = new RouteStep(RouteStep.Kind.TRAVERSAL, "Crypt Entrance", "", 4, List.of(), "t-2", new int[0], -1)
				.withInfo(StepInfo.of(cave, false, req, null, job.getPlayer().getState(), null));
			return RouteResults.ready(job.getGeneration(), job.getRequest(), List.of(s));
		}, published::add);
		PlayerState candle = new PlayerState.Builder("p").members(true).carry(33, 1).build();
		PlayerState bruma = new PlayerState.Builder("p").members(true).carry(torch, 1).build();
		service.request(A, new PlayerSnapshot(3200, 3200, 0, candle, List.of()), TransportSettings.defaults());
		next();
		RouteResult ready = next();
		assertEquals(33, ready.getSteps().get(0).getInfo().getNeeds().get(0).getItemId());

		service.updateCards(new PlayerSnapshot(3200, 3200, 0, bruma, List.of()));
		RouteResult fresh = next();
		assertEquals(ready.getGeneration(), fresh.getGeneration());
		assertEquals(torch, fresh.getSteps().get(0).getInfo().getNeeds().get(0).getItemId());
		assertEquals(fresh, service.getCurrent());
		assertEquals(1, jobs.size()); // no search
		assertEquals(candle, service.getLastPlayer().getState()); // later snapshots are still compared with the search's

		// nothing on the cards changes: nothing published
		service.updateCards(new PlayerSnapshot(3200, 3200, 0, bruma, List.of()));
		assertNull(published.poll(300, TimeUnit.MILLISECONDS));
	}

	@Test
	public void publishesCalculatingThenTheAnswer() throws Exception
	{
		service = new RouteService(this::answer, published::add);
		assertEquals(RouteStatus.IDLE, service.getCurrent().getStatus());
		service.request(A, ME, TransportSettings.defaults());
		RouteResult calc = next();
		assertEquals(RouteStatus.CALCULATING, calc.getStatus());
		assertEquals(A, calc.getRequest());
		RouteResult ready = next();
		assertEquals(RouteStatus.READY, ready.getStatus());
		assertEquals(calc.getGeneration(), ready.getGeneration());
		assertEquals(10, ready.getTotalTicks());
		assertEquals(ready, service.getCurrent());
		assertTrue(service.hasDestination());
	}

	@Test
	public void runsOnTheWorkerNotTheCallingThread() throws Exception
	{
		Thread caller = Thread.currentThread();
		BlockingQueue<Thread> engineThread = new LinkedBlockingQueue<>();
		service = new RouteService(job ->
		{
			engineThread.add(Thread.currentThread());
			return answer(job);
		}, published::add);
		service.request(A, ME, TransportSettings.defaults());
		Thread t = engineThread.poll(5, TimeUnit.SECONDS);
		assertNotEquals(caller, t);
		assertEquals("BestPath route worker", t.getName());
		assertTrue(t.isDaemon());
	}

	@Test
	public void anOlderSearchNeverOverwritesANewerOne() throws Exception
	{
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch started = new CountDownLatch(1);
		service = new RouteService(job ->
		{
			if (job.getRequest().equals(A))
			{
				started.countDown();
				try
				{
					release.await(5, TimeUnit.SECONDS);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			}
			return answer(job);
		}, published::add);
		service.request(A, ME, TransportSettings.defaults());
		assertEquals(RouteStatus.CALCULATING, next().getStatus());
		assertTrue("A is running now", started.await(5, TimeUnit.SECONDS));
		service.request(B, ME, TransportSettings.defaults());
		RouteResult calcB = next();
		assertEquals(B, calcB.getRequest());
		release.countDown();
		RouteResult r = next();
		assertEquals(B, r.getRequest());
		assertEquals(RouteStatus.READY, r.getStatus());
		assertNull("A's late answer must be dropped", published.poll(300, TimeUnit.MILLISECONDS));
		assertEquals(B, service.getCurrent().getRequest());
	}

	@Test
	public void clearDropsTheDestinationAndARunningSearch() throws Exception
	{
		CountDownLatch release = new CountDownLatch(1);
		service = new RouteService(job ->
		{
			try
			{
				release.await(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			return answer(job);
		}, published::add);
		service.request(A, ME, TransportSettings.defaults());
		next();
		service.clear();
		assertEquals(RouteStatus.IDLE, next().getStatus());
		release.countDown();
		assertNull(published.poll(300, TimeUnit.MILLISECONDS));
		assertFalse(service.hasDestination());
		assertEquals(RouteStatus.IDLE, service.getCurrent().getStatus());
	}

	@Test
	public void skipsLastForThisDestinationOnly() throws Exception
	{
		service = new RouteService(this::answer, published::add);
		service.skipForThisRoute("t-0"); // no destination yet: ignored
		assertTrue(service.getSkipped().isEmpty());

		service.request(A, ME, TransportSettings.defaults());
		assertTrue(jobs.poll(5, TimeUnit.SECONDS).getSessionExclusions().isEmpty());
		service.skipForThisRoute("t-1");
		service.reroute(ME, TransportSettings.defaults().withBlacklisted("t-9", "Gate"));
		RouteJob again = jobs.poll(5, TimeUnit.SECONDS);
		assertEquals(A, again.getRequest());
		assertEquals(Set.of("t-1"), again.getSessionExclusions());
		assertTrue(again.isExcluded("t-1"));
		assertTrue("blacklist counts too", again.isExcluded("t-9"));
		assertFalse(again.isExcluded("t-2"));

		service.request(A, ME, TransportSettings.defaults());
		assertEquals("same destination keeps skips", Set.of("t-1"), jobs.poll(5, TimeUnit.SECONDS).getSessionExclusions());
		service.request(B, ME, TransportSettings.defaults());
		assertTrue("new destination forgets skips", jobs.poll(5, TimeUnit.SECONDS).getSessionExclusions().isEmpty());

		service.skipForThisRoute("t-3");
		service.clearSkips();
		assertTrue(service.getSkipped().isEmpty());
	}

	@Test
	public void engineFailureBecomesAnErrorResult() throws Exception
	{
		service = new RouteService(job ->
		{
			throw new IllegalStateException("boom");
		}, published::add);
		service.request(A, ME, TransportSettings.defaults());
		next();
		RouteResult r = next();
		assertEquals(RouteStatus.ERROR, r.getStatus());
		assertTrue(r.getMessage().contains("IllegalStateException"));
	}

	@Test
	public void failKeepsTheDestinationAndRequestsAfterShutdownDoNothing() throws Exception
	{
		service = new RouteService(this::answer, published::add);
		service.fail(A, "Log in to find a route.");
		RouteResult r = next();
		assertEquals(RouteStatus.ERROR, r.getStatus());
		assertEquals(A, r.getRequest());
		assertTrue(service.hasDestination());
		service.shutdown();
		service.request(B, ME, TransportSettings.defaults()); // must not throw
		assertEquals(RouteStatus.CALCULATING, next().getStatus());
		assertNull(published.poll(300, TimeUnit.MILLISECONDS));
	}

	@Test
	public void noMapDataEngineSaysSo()
	{
		RouteJob job = new RouteJob(5, A, ME, TransportSettings.defaults(), Set.of());
		RouteResult r = new NoMapDataEngine().route(job);
		assertEquals(RouteStatus.NO_DATA, r.getStatus());
		assertEquals(5, r.getGeneration());
		assertEquals(A, r.getRequest());
	}
}

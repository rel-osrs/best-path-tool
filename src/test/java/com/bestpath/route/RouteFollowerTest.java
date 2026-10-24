package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.RequirementText;
import com.bestpath.core.req.Skill;
import com.bestpath.player.RequirementInputs;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * The route follower with the real {@link RouteService}: progress, quiet
 * rerouting when the player strays or availability changes, and arrival.
 * Searches run when the test says so ({@link ManualExecutor}).
 */
public class RouteFollowerTest
{
	private static final RouteRequest DEST = new RouteRequest(3220, 3200, 0);

	private final ManualExecutor worker = new ManualExecutor();
	private final List<RouteJob> jobs = new ArrayList<>();
	private final List<Progress> stepChanges = new ArrayList<>();
	private RequirementInputs inputs = RequirementInputs.NONE;
	private final RouteService service = new RouteService(this::engine, r -> { }, worker);
	private final RouteFollower follower = new RouteFollower(service, TransportSettings::defaults, () -> inputs,
		stepChanges::add);
	/** What the fake client's snapshot gives. */
	private PlayerSnapshot now = snap(3200, 3200, 50);
	private int snapshotsTaken;

	private static PlayerSnapshot snap(int x, int y, int agility)
	{
		return new PlayerSnapshot(x, y, 0, new PlayerState.Builder("p").members(true).level(Skill.AGILITY, agility)
			.build(), List.of());
	}

	/** Fake engine: walks straight east from the player to the destination. */
	private RouteResult engine(RouteJob job)
	{
		jobs.add(job);
		PlayerSnapshot p = job.getPlayer();
		RouteRequest r = job.getRequest();
		return RouteResults.ready(job.getGeneration(), r,
			List.of(RouteProgressTest.walk(p.getX(), p.getY(), r.getX(), r.getY(), 0)));
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

	private void start()
	{
		service.request(DEST, now, TransportSettings.defaults());
		worker.runAll();
		assertEquals(RouteStatus.READY, service.getCurrent().getStatus());
	}

	/** The "put items back" route (one stop only) is not dropped on arrival: the plugin drops it. */
	@Test
	public void aRouteToItsOneStopStaysOnArrival()
	{
		RouteRequest.Stop stop = new RouteRequest.Stop("stash-1", Tiles.pack(3220, 3200, 0), "Put the items back in the STASH unit",
			List.of());
		RouteRequest put = RouteRequest.places("stash 1", "Put the items back in the STASH unit", List.of(),
			new int[]{stop.getTile()}).withFirstStop(stop);
		assertTrue(put.isStopOnly());
		service.request(put, now, TransportSettings.defaults());
		worker.runAll();
		tick(1, 3219, 3200);
		tick(2, 3220, 3200);
		assertEquals(RouteStatus.READY, service.getCurrent().getStatus());
		assertTrue(service.hasDestination());
		service.clear();
		assertFalse(service.hasDestination());
	}

	@Test
	public void followsTheRouteAndClearsItOnArrival()
	{
		start();
		tick(1, 3200, 3200);
		assertEquals(0, follower.getProgress().getStep());
		assertEquals(1, stepChanges.size()); // the sidebar was told once: step 0 of this route
		tick(2, 3205, 3200);
		assertEquals(5, follower.getProgress().getTileIndex());
		assertEquals(1, stepChanges.size()); // same step: the sidebar is not told again
		tick(3, 3219, 3200);
		assertEquals(RouteStatus.ARRIVED, service.getCurrent().getStatus());
		assertFalse(service.hasDestination());
		assertEquals(Progress.NONE, follower.getProgress());
		assertEquals(1, jobs.size());
		assertEquals(0, snapshotsTaken); // following needs no snapshot
	}

	/** With the clear-route-on-arrival toggle off, the arrived route stays shown. */
	@Test
	public void withClearOnArrivalOffTheRouteStaysAtItsEnd()
	{
		follower.setClearOnArrival(false);
		start();
		tick(1, 3200, 3200);
		tick(2, 3219, 3200);
		assertEquals(RouteStatus.READY, service.getCurrent().getStatus());
		assertTrue(service.hasDestination());
		assertEquals(1, jobs.size()); // no new search at the end either
		follower.setClearOnArrival(true);
		tick(3, 3219, 3200);
		assertFalse(service.hasDestination());
	}

	@Test
	public void straysThenSearchesQuietlyFromTheNewTile()
	{
		start();
		RouteResult first = service.getCurrent();
		tick(1, 3205, 3200);
		tick(2, 3205, 3220);
		tick(3, 3205, 3220);
		tick(4, 3205, 3220); // third tick off the route
		assertEquals(1, snapshotsTaken);
		assertTrue(service.isSearching());
		assertTrue(service.getCurrent() == first); // the old route stays shown meanwhile (no "Calculating...")
		tick(5, 3205, 3220);
		tick(6, 3205, 3220);
		tick(7, 3205, 3220); // strayed again, but a search is already running: no second one
		assertEquals(1, snapshotsTaken);
		worker.runAll();
		assertEquals(2, jobs.size());
		assertEquals(3220, jobs.get(1).getPlayer().getY());
		RouteResult second = service.getCurrent();
		assertEquals(RouteStatus.READY, second.getStatus());
		assertFalse(service.isSearching());
		tick(8, 3205, 3220);
		assertTrue(follower.getProgress().isFor(second));
		assertEquals(0, follower.getProgress().getStep());
	}

	@Test
	public void searchesAgainOnlyWhenWhatChangedMatters()
	{
		inputs = RequirementInputs.collect(List.of(RequirementText.parse("Agility 51",
			RequirementText.ItemGroups.NONE)));
		start();
		tick(1, 3200, 3200);
		follower.inputsChanged(1); // e.g. a varbit or the inventory changed, nothing that matters
		tick(2, 3201, 3200);
		assertEquals(0, snapshotsTaken); // waiting for the burst to settle
		tick(3, 3202, 3200);
		assertEquals(1, snapshotsTaken);
		worker.runAll();
		assertEquals(1, jobs.size()); // same Agility: no new search

		now = snap(3203, 3200, 51); // levelled up: the shortcut needing Agility 51 opens
		follower.inputsChanged(4);
		tick(4, 3203, 3200);
		tick(5, 3204, 3200);
		tick(6, 3204, 3200);
		worker.runAll();
		assertEquals(2, jobs.size());
		assertEquals(51, jobs.get(1).getPlayer().getState().level(Skill.AGILITY));
	}

	@Test
	public void endlessSmallChangesAreCheckedAtMostEveryFewTicks()
	{
		start();
		for (int t = 1; t <= 30; t++)
		{
			follower.inputsChanged(t);
			tick(t, 3200 + t % 2, 3200);
		}
		// one check per MAX_WAIT_TICKS at most
		assertTrue("checks " + snapshotsTaken, snapshotsTaken <= 30 / RouteFollower.MAX_WAIT_TICKS + 1);
		assertTrue(snapshotsTaken >= 2);
	}

	@Test
	public void noSearchWithoutARouteOrWithoutData()
	{
		follower.inputsChanged(1);
		tick(1, 3200, 3200);
		tick(5, 3200, 3200);
		assertEquals(0, snapshotsTaken);
		assertTrue(jobs.isEmpty());

		RouteService noData = new RouteService(job -> RouteResult.status(job.getGeneration(), job.getRequest(),
			RouteStatus.NO_DATA, "none"), r -> { }, worker);
		RouteFollower f = new RouteFollower(noData, TransportSettings::defaults, () -> inputs, p -> { });
		noData.request(DEST, now, TransportSettings.defaults());
		worker.runAll();
		f.inputsChanged(1);
		f.onTick(5, 3200, 3200, 0, -1, this::takeSnapshot);
		assertEquals(0, snapshotsTaken);
		assertEquals(Progress.NONE, f.getProgress());
	}

	@Test
	public void clearingTheRouteDropsTheProgress()
	{
		start();
		tick(1, 3203, 3200);
		assertEquals(0, follower.getProgress().getStep());
		service.clear();
		tick(2, 3204, 3200);
		assertEquals(Progress.NONE, follower.getProgress());
		assertEquals(Progress.NONE, stepChanges.get(stepChanges.size() - 1));
		assertNull(service.getLastPlayer());
	}

	/** Runs queued tasks only when told (a search "in flight" until then). */
	static final class ManualExecutor extends AbstractExecutorService
	{
		private final Deque<Runnable> queue = new ArrayDeque<>();
		private boolean shut;

		void runAll()
		{
			while (!queue.isEmpty())
			{
				queue.poll().run();
			}
		}

		@Override
		public void execute(Runnable command)
		{
			queue.add(command);
		}

		@Override
		public void shutdown()
		{
			shut = true;
		}

		@Override
		public List<Runnable> shutdownNow()
		{
			shut = true;
			return new ArrayList<>(queue);
		}

		@Override
		public boolean isShutdown()
		{
			return shut;
		}

		@Override
		public boolean isTerminated()
		{
			return shut;
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit)
		{
			return true;
		}
	}
}

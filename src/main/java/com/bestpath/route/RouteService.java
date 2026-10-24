package com.bestpath.route;

import com.bestpath.core.path.GamePath;
import com.bestpath.core.sea.Boat;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs route searches off the client thread:
 * callers hand in an immutable {@link PlayerSnapshot} built on the client
 * thread; one worker thread searches; the immutable {@link RouteResult} is
 * published through an {@link AtomicReference} that overlays read, and the
 * listener is told (it must hop to Swing with SwingUtilities.invokeLater).
 *
 * <p>Every request gets a new generation number; a result of an older
 * request is dropped, so a slow search never overwrites a newer one. Pure
 * Java (no RuneLite classes).
 */
@Slf4j
public final class RouteService
{
	private final RouteEngine engine;
	private final Consumer<RouteResult> listener;
	private final ExecutorService worker;
	private final AtomicLong generation = new AtomicLong();
	private final AtomicReference<RouteResult> current = new AtomicReference<>(RouteResult.idle(0));
	private final Object lock = new Object();

	/** Guarded by {@link #lock}. */
	private RouteRequest lastRequest;
	/** The player snapshot the latest search was asked with (to diff later snapshots against). Guarded by {@link #lock}. */
	private PlayerSnapshot lastPlayer;
	/** Session exclusions: "skip this transport for this route". Guarded by {@link #lock}. */
	private final Set<String> skipped = new LinkedHashSet<>();
	/**
	 * How routes treat the boat the player is on (the boat card's "Park my boat" and "Ocean only path"). Guarded by
	 * {@link #lock} (tests read it).
	 */
	BoatMode boatMode = BoatMode.NORMAL;
	/**
	 * Whether a "no route" is explained (the config's "No-route explanations"). Off: the engine's own line only,
	 * nothing worked out.
	 */
	private volatile boolean explainNoRoute = true;

	/**
	 * @param listener told about every published result, on the calling or the worker thread
	 */
	public RouteService(RouteEngine engine, Consumer<RouteResult> listener)
	{
		this(engine, listener, Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "BestPath route worker");
			t.setDaemon(true);
			// below the client thread: a long search must not make the game stutter
			t.setPriority(Thread.NORM_PRIORITY - 1);
			return t;
		}));
	}

	/** For tests: a chosen executor (normally one thread). */
	public RouteService(RouteEngine engine, Consumer<RouteResult> listener, ExecutorService worker)
	{
		this.engine = engine;
		this.listener = listener;
		this.worker = worker;
	}

	/** Whether "no route" answers are explained (on by default). Any thread; the next "no route" follows it. */
	public void setExplainNoRoute(boolean on)
	{
		explainNoRoute = on;
	}

	/** The latest published result (never null). Safe from any thread; overlays read this. */
	public RouteResult getCurrent()
	{
		return current.get();
	}

	/**
	 * Asks for a route. Publishes CALCULATING at once, then the answer from the
	 * worker. A new destination forgets the previous route's skipped transports.
	 */
	public void request(RouteRequest request, PlayerSnapshot player, TransportSettings settings)
	{
		RouteJob job;
		synchronized (lock)
		{
			if (!request.equals(lastRequest))
			{
				skipped.clear();
				// "Park my boat" is for this route only; "Ocean only path" stays on
				if (boatMode == BoatMode.PARK)
				{
					boatMode = BoatMode.NORMAL;
				}
			}
			offTheBoat(player);
			lastRequest = request;
			lastPlayer = player;
			long gen = generation.incrementAndGet();
			job = new RouteJob(gen, request, player, settings, skipped, boatMode);
			publish(RouteResult.status(gen, request, RouteStatus.CALCULATING, "Calculating..."));
		}
		submit(job);
	}

	/**
	 * Searches again to the same destination from a newer snapshot WITHOUT showing "Calculating...": the old
	 * route stays on screen until the new one replaces it. Used when the player strayed off the route or an
	 * availability input changed. Keeps this route's skipped transports. No-op without a destination.
	 */
	public void refresh(PlayerSnapshot player, TransportSettings settings)
	{
		RouteJob job;
		synchronized (lock)
		{
			if (lastRequest == null)
			{
				return;
			}
			lastPlayer = player;
			offTheBoat(player);
			job = new RouteJob(generation.incrementAndGet(), lastRequest, player, settings, skipped, boatMode);
		}
		submit(job);
	}

	/**
	 * The player's items changed but the route stays (nothing it can use changed): its step cards are made again on the
	 * worker for the items the player has now, and published under the same generation. Later snapshots are still
	 * compared with the one the route was searched with. No-op without a ready route, or when no card would look
	 * different.
	 */
	public void updateCards(PlayerSnapshot player)
	{
		if (player == null)
		{
			return;
		}
		long gen = generation.get();
		try
		{
			worker.execute(() ->
			{
				RouteResult shown = current.get();
				if (shown.getGeneration() != gen || shown.getStatus() != RouteStatus.READY)
				{
					return;
				}
				RouteResult fresh;
				try
				{
					fresh = shown.withPlayer(player.getState());
				}
				catch (RuntimeException e)
				{
					log.warn("Updating the step cards failed", e);
					return; // the cards stay as they were
				}
				synchronized (lock)
				{
					if (fresh != shown && current.get() == shown)
					{
						publish(fresh);
					}
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			// shut down: nothing to do
		}
	}

	/**
	 * Called with {@link #lock} held: once the player is off the boat (teleported away, left it at a dock), every boat
	 * mode ends.
	 */
	private void offTheBoat(PlayerSnapshot player)
	{
		if (boatMode != BoatMode.NORMAL && !onBoat(player))
		{
			boatMode = BoatMode.NORMAL;
		}
	}

	/** True when the snapshot has the player on one of their boats ({@link Boat#AT_START}). */
	public static boolean onBoat(PlayerSnapshot player)
	{
		if (player == null)
		{
			return false;
		}
		for (Boat b : player.getBoats())
		{
			if (b.getDockId() == Boat.AT_START)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The boat card's choice: "Park my boat" for this route, "Ocean only path" until the player is off the boat, or back
	 * to the normal route. Then reroute.
	 */
	public void setBoatMode(BoatMode mode)
	{
		synchronized (lock)
		{
			boatMode = mode == null ? BoatMode.NORMAL : mode;
		}
	}

	private void submit(RouteJob job)
	{
		try
		{
			worker.execute(() -> run(job));
		}
		catch (RejectedExecutionException e)
		{
			// shut down: nothing to do
		}
	}

	/** The snapshot the latest search was asked with, or null (none, or the destination was dropped). */
	public PlayerSnapshot getLastPlayer()
	{
		synchronized (lock)
		{
			return lastPlayer;
		}
	}

	/** True while a search is running whose answer is not published yet. */
	public boolean isSearching()
	{
		RouteResult c = current.get();
		return c.getStatus() == RouteStatus.CALCULATING || c.getGeneration() != generation.get();
	}

	/** The player reached the destination: drops it (like {@link #clear}) and says so. */
	public void arrive()
	{
		synchronized (lock)
		{
			RouteRequest r = lastRequest;
			lastRequest = null;
			lastPlayer = null;
			skipped.clear();
			boatMode = boatMode == BoatMode.PARK ? BoatMode.NORMAL : boatMode;
			publish(RouteResult.status(generation.incrementAndGet(), null, RouteStatus.ARRIVED,
				r == null ? "You have arrived."
					: r.hasTitle() ? "You have arrived (" + r.getTitle() + ")." : "You have arrived."));
		}
	}

	/** Searches again to the same destination (after a skip or a settings change); no-op without one. */
	public void reroute(PlayerSnapshot player, TransportSettings settings)
	{
		RouteRequest r;
		synchronized (lock)
		{
			r = lastRequest;
		}
		if (r != null)
		{
			request(r, player, settings);
		}
	}

	/** Shows a problem instead of a route (e.g. "Log in first"); keeps the destination. */
	public void fail(RouteRequest request, String message)
	{
		synchronized (lock)
		{
			lastRequest = request;
			publish(RouteResult.status(generation.incrementAndGet(), request, RouteStatus.ERROR, message));
		}
	}

	/** Session exclusion: this transport is not used again until the destination changes. Then reroute. */
	public void skipForThisRoute(String transportId)
	{
		synchronized (lock)
		{
			if (lastRequest != null && transportId != null)
			{
				skipped.add(transportId);
			}
		}
	}

	/** Forgets this route's skipped transports. Then reroute. */
	public void clearSkips()
	{
		synchronized (lock)
		{
			skipped.clear();
		}
	}

	public Set<String> getSkipped()
	{
		synchronized (lock)
		{
			return Collections.unmodifiableSet(new LinkedHashSet<>(skipped));
		}
	}

	/** True while a destination is set. */
	public boolean hasDestination()
	{
		synchronized (lock)
		{
			return lastRequest != null;
		}
	}

	/** Drops the destination; a search still running is ignored when it ends. */
	public void clear()
	{
		synchronized (lock)
		{
			lastRequest = null;
			lastPlayer = null;
			skipped.clear();
			boatMode = boatMode == BoatMode.PARK ? BoatMode.NORMAL : boatMode;
			publish(RouteResult.idle(generation.incrementAndGet()));
		}
	}

	/**
	 * Runs a task on the route worker before any search asked for later (e.g.
	 * loading the map data at plugin start, off the client thread).
	 */
	public void runOnWorker(Runnable task)
	{
		try
		{
			worker.execute(() ->
			{
				try
				{
					task.run();
				}
				catch (Throwable e)
				{
					// an Error too: the worker must keep serving searches
					log.warn("Route worker task failed", e);
					rethrowIfFatal(e);
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			// shut down: nothing to do
		}
	}

	/**
	 * Route worker thread only: one search that changes nothing here, not the current route (the Functions tab's Slayer
	 * travel times, {@link SlayerTimes}).
	 */
	RouteResult searchQuietly(RouteJob job)
	{
		return engine.route(job);
	}

	/**
	 * Searches on the worker, after any search asked for before, without changing the route shown (the public API's
	 * {@code query}, docs/API.md). The answer goes to {@code answer} on the worker: always one, ERROR when the search
	 * failed; none after {@link #shutdown}. No session exclusions or boat modes: those belong to the route shown.
	 */
	public void query(RouteRequest request, PlayerSnapshot player, TransportSettings settings, Consumer<RouteResult> answer)
	{
		runOnWorker(() ->
		{
			RouteResult r = null;
			try
			{
				r = engine.route(new RouteJob(0, request, player, settings, Set.of()));
			}
			catch (RuntimeException e)
			{
				log.warn("Query search failed", e);
				r = RouteResult.status(0, request, RouteStatus.ERROR, "Route search failed: " + e.getClass().getSimpleName());
			}
			finally
			{
				// always answered, even when an Error ends the search: the caller counts the queries still out
				answer.accept(r != null ? r : RouteResult.status(0, request, RouteStatus.ERROR, "Route search failed"));
			}
		});
	}

	/** Route worker thread only: the engine's game pathing for redrawing the walk ahead, or null (no map yet). */
	GamePath gamePath()
	{
		return engine.gamePath();
	}

	/** Stops the worker without waiting: the plugin's shutDown() must never block. */
	public void shutdown()
	{
		worker.shutdownNow();
	}

	private void run(RouteJob job)
	{
		if (job.getGeneration() != generation.get())
		{
			return; // a newer request came in before this one started
		}
		RouteResult r;
		Throwable fatal = null;
		long start = System.nanoTime();
		try
		{
			// the sidebar shows how long the search took, by the travel time
			r = engine.route(job).withSearchMillis((System.nanoTime() - start) / 1_000_000);
		}
		catch (Throwable e)
		{
			// an Error too (OutOfMemoryError, StackOverflowError, NoClassDefFoundError...): without an answer for
			// this generation the sidebar would say "Calculating..." for ever
			log.warn("Route search failed", e);
			r = RouteResult.status(job.getGeneration(), job.getRequest(), RouteStatus.ERROR,
				"Route search failed: " + e.getClass().getSimpleName());
			fatal = e;
		}
		if (r.isFindingWhy() && !explainNoRoute)
		{
			r = r.withoutWhy();
		}
		synchronized (lock)
		{
			publish(r);
		}
		if (fatal != null)
		{
			rethrowIfFatal(fatal);
		}
		if (r.isFindingWhy())
		{
			explain(r);
		}
	}

	/**
	 * Works out why there is no route and publishes the result again ("No route, finding out why..." is shown
	 * meanwhile). "Calculated in" counts the search and the explanation together. Skipped when a newer request came in;
	 * a failure shows the engine's own line.
	 */
	private void explain(RouteResult r)
	{
		if (r.getGeneration() != generation.get())
		{
			return;
		}
		RouteResult done;
		Throwable fatal = null;
		long start = System.nanoTime();
		try
		{
			done = r.explained();
		}
		catch (Throwable e)
		{
			log.warn("Explaining no route failed", e);
			done = r.withoutWhy();
			fatal = e;
		}
		if (r.getSearchMillis() >= 0)
		{
			done = done.withSearchMillis(r.getSearchMillis() + (System.nanoTime() - start) / 1_000_000);
		}
		synchronized (lock)
		{
			publish(done);
		}
		if (fatal != null)
		{
			rethrowIfFatal(fatal);
		}
	}

	/** The JVM itself is in trouble (out of memory...): let it through after the answer was published. */
	private static void rethrowIfFatal(Throwable e)
	{
		if (e instanceof VirtualMachineError)
		{
			throw (VirtualMachineError) e;
		}
	}

	/** Called with {@link #lock} held. Drops results of older requests. */
	private void publish(RouteResult r)
	{
		if (r.getGeneration() != generation.get())
		{
			return;
		}
		current.set(r);
		listener.accept(r);
	}
}

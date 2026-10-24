package com.bestpath.route;

import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.Cooldown;
import com.bestpath.player.AvailabilityDiff;
import com.bestpath.player.Debouncer;
import com.bestpath.player.RequirementInputs;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the published route in step with the player, once per game tick
 * (client thread): tracks the current step ({@link RouteProgress}), searches
 * again quietly when the player strays or when an availability input that
 * matters changed (after a burst of events settles), and drops the route on
 * arrival. The searches themselves run on the {@link RouteService} worker.
 * It also asks for the walk ahead to be redrawn from the player's tile
 * ({@link RouteRedraw}, on the same worker) and follows the walks as drawn.
 *
 * <p>Pure Java (no RuneLite classes): the plugin hands in the player's tile
 * and a way to take a snapshot (only called when one is needed), so this can
 * be tested without the game.
 */
@Slf4j
public final class RouteFollower
{
	/** Inputs changed: act once nothing new came for this many ticks... */
	public static final int QUIET_TICKS = 2;
	/** ...or at the latest this many ticks after the first change. */
	public static final int MAX_WAIT_TICKS = 10;

	private final RouteService service;
	private final Supplier<TransportSettings> settings;
	private final Supplier<RequirementInputs> inputs;
	private final Consumer<Progress> stepListener;
	private final AtomicReference<Progress> progress = new AtomicReference<>(Progress.NONE);
	private final Debouncer changes = new Debouncer(QUIET_TICKS, MAX_WAIT_TICKS);
	private final RouteRedraw redraw;
	/** Client thread only. */
	private RouteProgress tracker;
	/** The player's re-route distance and arrival range ({@link RouteProgress#setLimits}); set from any thread. */
	private volatile int strayDistance = RouteProgress.DEFAULT_STRAY_DISTANCE;
	private volatile int arriveSteps = RouteProgress.DEFAULT_ARRIVE_STEPS;
	private volatile boolean clearOnArrival = true;
	/** Told once per route when the player arrives (client thread; the public API's ARRIVED); null for none. */
	private volatile Consumer<RouteResult> arrivalListener;
	/** The generation the arrival listener was last told about (client thread only). */
	private long arrivalTold = -1;

	/**
	 * @param stepListener told when the current step (or the route followed) changes; it must hop to Swing
	 */
	public RouteFollower(RouteService service, Supplier<TransportSettings> settings,
		Supplier<RequirementInputs> inputs, Consumer<Progress> stepListener)
	{
		this.service = service;
		this.settings = settings;
		this.inputs = inputs;
		this.stepListener = stepListener;
		this.redraw = new RouteRedraw(service);
	}

	/** The walk lines as drawn now (never null; check {@link DrawPath#isFor}). Any thread; overlays read this. */
	public DrawPath getDrawn()
	{
		return redraw.current();
	}

	/** Told on the route worker when a walk line was redrawn (the world map rebuilds its lines there). */
	public void setRedrawListener(BiConsumer<RouteResult, DrawPath> listener)
	{
		redraw.setListener(listener);
	}

	/** The latest progress (never null). Safe from any thread; overlays read this. */
	public Progress getProgress()
	{
		return progress.get();
	}

	/**
	 * The player's settings (RuneLite config panel): search again when further than
	 * {@code strayDistance} tiles off the route; arrived within {@code arriveSteps} legal steps of its end. Used from
	 * the next tick on.
	 */
	public void setLimits(int strayDistance, int arriveSteps)
	{
		this.strayDistance = strayDistance;
		this.arriveSteps = arriveSteps;
	}

	/**
	 * The "Clear route on arrival" setting: off, an arrived route stays shown until it is cleared or replaced. Used
	 * from the next tick on.
	 */
	public void setClearOnArrival(boolean clear)
	{
		this.clearOnArrival = clear;
	}

	/**
	 * Told on the client thread, once per route, when the player arrives, before the route is cleared (or while it stays
	 * shown with "Clear route on arrival" off). Not for a route to its one first stop, which stays until the plugin
	 * drops it.
	 */
	public void setArrivalListener(Consumer<RouteResult> listener)
	{
		this.arrivalListener = listener;
	}

	/** An event that may change availability arrived (inventory, bank, varbit, level...). */
	public void inputsChanged(int tick)
	{
		changes.mark(tick);
	}

	/**
	 * One game tick with the player on this real-world tile, also knowing where the game is walking the player: its
	 * destination flag (packed tile, -1 = none: standing still).
	 *
	 * @param snapshot takes a player snapshot (client thread); called only when a search is needed; may give null
	 */
	public void onTick(int tick, int x, int y, int plane, int destination, Supplier<PlayerSnapshot> snapshot)
	{
		RouteResult r = service.getCurrent();
		if (r.getStatus() == RouteStatus.READY && !r.getSteps().isEmpty() && r.getRequest() != null)
		{
			// the same generation with cards made again for the player's items (RouteService.updateCards) keeps its progress
			if (tracker == null || tracker.getRoute().getGeneration() != r.getGeneration())
			{
				tracker = new RouteProgress(r);
				PlayerSnapshot from = service.getLastPlayer(); // where this route was searched from
				tracker.setOrigin(from == null ? -1 : Tiles.pack(from.getX(), from.getY(), from.getPlane()));
			}
			tracker.setLimits(strayDistance, arriveSteps);
			switch (tracker.update(x, y, plane, redraw.forResult(r)))
			{
				case ARRIVED:
					Consumer<RouteResult> al = arrivalListener;
					if (al != null && arrivalTold != r.getGeneration() && !r.getRequest().isStopOnly())
					{
						arrivalTold = r.getGeneration();
						al.accept(r);
					}
					if (!clearOnArrival)
					{
						break; // the player's setting: the route stays until cleared or replaced
					}
					if (r.getRequest().isStopOnly())
					{
						// a route to its one stop (e.g. putting STASH items back) stays on its card even when the
						// player is close by, until the plugin drops it (the items are back, or "Skip", or Clear)
						break;
					}
					log.debug("Arrived at {}", r.getRequest());
					tracker = null;
					publish(Progress.NONE);
					service.arrive();
					return;
				case STRAYED:
					if (!service.isSearching())
					{
						log.debug("Off the route at {}, {}, {}: searching again", x, y, plane);
						search(snapshot);
					}
					break;
				default:
					break;
			}
			publish(tracker.progress());
			redraw.playerAt(r, tracker.getStep(), tracker.getTileIndex(), Tiles.pack(x, y, plane), destination);
		}
		else if (tracker != null)
		{
			tracker = null;
			publish(Progress.NONE);
		}

		if (changes.ready(tick))
		{
			checkInputs(r, snapshot);
		}
	}

	/** Searches again if what changed matters (debounced). */
	private void checkInputs(RouteResult r, Supplier<PlayerSnapshot> snapshot)
	{
		if (!service.hasDestination()
			|| (r.getStatus() != RouteStatus.READY && r.getStatus() != RouteStatus.NO_ROUTE))
		{
			changes.clear(); // nothing to keep up to date
			return;
		}
		if (service.isSearching())
		{
			return; // try again next tick
		}
		changes.clear();
		PlayerSnapshot before = service.getLastPlayer();
		PlayerSnapshot now = snapshot.get();
		if (now == null)
		{
			return;
		}
		Set<Cooldown> ahead = tracker != null && tracker.getRoute().getGeneration() == r.getGeneration()
			? cooldownsAhead(r.getSteps(), tracker.getStep())
			: Set.of();
		if (before == null || AvailabilityDiff.needsReroute(before, now, inputs.get(), ahead))
		{
			log.debug("Availability changed: {}", before == null ? "no earlier snapshot"
				: AvailabilityDiff.changes(before.getState(), now.getState(), inputs.get()));
			service.refresh(now, settings.get());
		}
		else if (destinationNeedsChanged(r.getRequest(), before, now))
		{
			log.debug("The destination's needed items moved between the bank and the inventory: searching again");
			service.refresh(now, settings.get());
		}
		else if (r.getStatus() == RouteStatus.READY)
		{
			// the same route: its cards show the items on the player now
			service.updateCards(now);
		}
	}

	/**
	 * The home / minigame cooldowns the route still needs from step {@code current} on, leaving out the next transport
	 * step when it is such a teleport: the player who just used it is doing that step (casting it takes 19-25
	 * ticks), so its cooldown starting is no reason to search again. A cooldown teleport the player used anywhere else
	 * while the route still needs it later is a reason: the search uses it only while it is ready.
	 */
	static Set<Cooldown> cooldownsAhead(List<RouteStep> steps, int current)
	{
		Set<Cooldown> out = EnumSet.noneOf(Cooldown.class);
		int next = -1;
		for (int i = Math.max(0, current); i < steps.size(); i++)
		{
			RouteStep s = steps.get(i);
			if (next < 0 && s.getKind().isTravel())
			{
				next = i;
				continue;
			}
			Cooldown c = s.getInfo().getCooldown();
			if (c != null)
			{
				out.add(c);
			}
		}
		return out;
	}

	/**
	 * True if the items the destination needs that are only in the bank are not the same any more (taken out, put
	 * in): the route then starts at a bank, or no longer does (the bank-first rule of {@link DestinationNeeds}).
	 */
	static boolean destinationNeedsChanged(RouteRequest request, PlayerSnapshot before, PlayerSnapshot now)
	{
		if (request == null || request.getNeeds().isEmpty())
		{
			return false;
		}
		DestinationNeeds n = request.getNeeds();
		return !n.fromBank(before.getState()).equals(n.fromBank(now.getState()));
	}

	private void search(Supplier<PlayerSnapshot> snapshot)
	{
		PlayerSnapshot now = snapshot.get();
		if (now != null)
		{
			changes.clear(); // this search uses the newest state anyway
			service.refresh(now, settings.get());
		}
	}

	private void publish(Progress p)
	{
		Progress old = progress.getAndSet(p);
		if (old.getStep() != p.getStep() || old.getGeneration() != p.getGeneration())
		{
			stepListener.accept(p);
		}
	}
}

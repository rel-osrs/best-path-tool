package com.bestpath.route;

import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A route with first stops ({@link RouteRequest.Stop}), e.g. putting the items taken out of a STASH unit back before
 * the next clue step, or killing the NPC that drops a key clue's key before going to the chest. Searched in parts with
 * the engine's own one-destination search. Each part goes from where the last one ended to the next stop, and the
 * stop's card follows (no ticks: the search adds none for putting the items in or for the fight); the last part goes
 * on to the request's places, with the request's needs (so a bank stop for them comes after the stops). A request
 * whose only place is its one stop ({@link RouteRequest#isStopOnly()}) ends with the stop's card.
 *
 * <p>A stop skipped (its card's "Skip"): the route leaves it out. A stop that can't be reached: left out, with a header
 * note saying so. Worker thread; pure apart from the search it is handed.
 */
public final class FirstStopRoute
{
	/** The header note when the STASH unit can't be reached and the route goes straight on. */
	public static final String UNREACHABLE_NOTE = "The STASH unit can't be reached with the transports allowed:"
		+ " this route goes straight on.";
	/** The header note when the NPC to kill for the key can't be reached and the route goes straight on. */
	public static final String KILL_UNREACHABLE_NOTE = "The place to kill for the key can't be reached with the transports"
		+ " allowed: this route goes straight on.";

	private FirstStopRoute()
	{
	}

	/**
	 * @param job the request has first stops
	 * @param one the engine's search for a request without first stops
	 */
	public static RouteResult route(RouteJob job, Function<RouteJob, RouteResult> one)
	{
		RouteRequest r = job.getRequest();
		RouteRequest plain = r.withoutFirstStop();
		PlayerSnapshot p = job.getPlayer();
		PlayerSnapshot from = p;
		int at = Tiles.pack(p.getX(), p.getY(), p.getPlane());
		List<RouteStep> steps = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		RouteResult first = null;
		RouteResult last = null;
		for (RouteRequest.Stop stop : r.getFirstStops())
		{
			if (job.isExcluded(stop.getId()))
			{
				continue;
			}
			int st = stop.getTile();
			RouteResult toStop = one.apply(job.with(new RouteRequest(Tiles.x(st), Tiles.y(st), Tiles.plane(st)), from));
			if (toStop.getStatus() != RouteStatus.READY)
			{
				if (r.isStopOnly())
				{
					return toStop.withRequest(r, List.of());
				}
				notes.add(stop.getKind() == RouteStep.Kind.KILL ? KILL_UNREACHABLE_NOTE : UNREACHABLE_NOTE);
				continue;
			}
			at = endOf(toStop, at);
			steps.addAll(toStop.getSteps());
			steps.add(new RouteStep(stop.getKind(), stop.getTitle(),
				"at " + Tiles.x(at) + ", " + Tiles.y(at) + ", " + Tiles.plane(at), 0, stop.getLines(), stop.getId(),
				new int[0], at, at));
			first = first == null ? toStop : first;
			last = toStop;
			from = new PlayerSnapshot(Tiles.x(at), Tiles.y(at), Tiles.plane(at), p.getState(), p.getBoats())
				.withNexusKeys(p.getNexusKeys());
		}
		if (last == null)
		{
			// every stop skipped or out of reach: straight to the places
			return one.apply(job.with(plain, p)).withRequest(r, notes);
		}
		if (r.isStopOnly())
		{
			return RouteResult.ready(job.getGeneration(), r, steps, last.getDestination(), last.getNotes(), last.arrive())
				.withBoatCard(first.getBoatCard());
		}
		RouteResult on = one.apply(job.with(plain, from));
		if (on.getStatus() != RouteStatus.READY)
		{
			return on.withRequest(r, notes);
		}
		steps.addAll(on.getSteps());
		notes.addAll(on.getNotes());
		// the boat card is the first part's: that part starts where the player is
		return RouteResult.ready(job.getGeneration(), r, steps, on.getDestination(), notes, on.arrive())
			.withBoatCard(first.getBoatCard());
	}

	/** Where a part left the player: its last step's end tile, else where it started (already there). */
	static int endOf(RouteResult part, int startTile)
	{
		List<RouteStep> s = part.getSteps();
		for (int i = s.size() - 1; i >= 0; i--)
		{
			if (s.get(i).getEndTile() != -1)
			{
				return s.get(i).getEndTile();
			}
		}
		return startTile;
	}
}

package com.bestpath.route;

/**
 * How a route treats the boat the player is on, chosen on the route's boat card ({@link BoatCard}). Kept by the
 * {@link RouteService} beside the route's skipped transports.
 */
public enum BoatMode
{
	/** The quickest route: it may teleport away and leave the boat at sea (unless Settings say never). */
	NORMAL,
	/**
	 * "Park my boat", for this route only: the route first sails the boat to a dock with a shipwright, then goes on any
	 * way, as the Settings choice "Never leave my boat at sea" does for every route. A new destination goes back to
	 * {@link #NORMAL}.
	 */
	PARK,
	/**
	 * "Ocean only path" (e.g. courier tasks): the player stays on the boat to a dock or open sea near the destination.
	 * Stays on for new destinations while the player is on the boat (the player can turn it off); off once the player
	 * is off the boat.
	 */
	OCEAN_ONLY
}

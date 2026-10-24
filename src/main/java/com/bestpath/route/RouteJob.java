package com.bestpath.route;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Everything one search needs, copied before it is handed to the worker. Immutable. */
public final class RouteJob
{
	private final long generation;
	private final RouteRequest request;
	private final PlayerSnapshot player;
	private final TransportSettings settings;
	private final Set<String> sessionExclusions;
	private final BoatMode boatMode;

	public RouteJob(long generation, RouteRequest request, PlayerSnapshot player, TransportSettings settings,
		Set<String> sessionExclusions)
	{
		this(generation, request, player, settings, sessionExclusions, BoatMode.NORMAL);
	}

	public RouteJob(long generation, RouteRequest request, PlayerSnapshot player, TransportSettings settings,
		Set<String> sessionExclusions, BoatMode boatMode)
	{
		this.boatMode = boatMode == null ? BoatMode.NORMAL : boatMode;
		this.generation = generation;
		this.request = request;
		this.player = player;
		this.settings = settings;
		this.sessionExclusions = Collections.unmodifiableSet(new LinkedHashSet<>(sessionExclusions));
	}

	public long getGeneration()
	{
		return generation;
	}

	public RouteRequest getRequest()
	{
		return request;
	}

	public PlayerSnapshot getPlayer()
	{
		return player;
	}

	public TransportSettings getSettings()
	{
		return settings;
	}

	/** Transports skipped "for this route" (session exclusions). */
	public Set<String> getSessionExclusions()
	{
		return sessionExclusions;
	}

	/** The same job (generation, settings, skips) for another request from another player snapshot. */
	public RouteJob with(RouteRequest otherRequest, PlayerSnapshot otherPlayer)
	{
		return new RouteJob(generation, otherRequest, otherPlayer, settings, sessionExclusions, boatMode);
	}

	/** How this route treats the boat the player is on ("Park my boat", "Ocean only path"). */
	public BoatMode getBoatMode()
	{
		return boatMode;
	}

	/** True if the transport may not be used: skipped for this route or on the persistent blacklist. */
	public boolean isExcluded(String transportId)
	{
		return sessionExclusions.contains(transportId) || settings.isBlacklisted(transportId);
	}
}

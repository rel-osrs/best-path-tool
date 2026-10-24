package com.bestpath.tool.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Transport candidates, their suggestions and cache-table destinations for one cache. Immutable after building. */
public final class TransportData
{
	private final List<TransportCandidate> candidates;
	/** Every candidate found in the cache, decided ones included. */
	private final List<TransportCandidate> all;
	private final Map<TransportCandidate, List<Suggestion>> suggestions;
	private final List<CacheTableTeleports.Destination> destinations;
	private final Map<Integer, List<TransportCandidate>> byCell = new HashMap<>();

	private final Map<String, com.bestpath.tool.edit.TransportRecord> confirmed;

	/**
	 * {@code confirmed}: transports created or confirmed in the tool. Rejected
	 * candidates, and those that already have a confirmed transport, are left out.
	 */
	public TransportData(List<TransportCandidate> all, Map<TransportCandidate, List<Suggestion>> suggestions,
		List<CacheTableTeleports.Destination> destinations, Map<String, com.bestpath.tool.edit.TransportRecord> confirmed,
		java.util.Set<String> rejected)
	{
		java.util.Set<String> confirmedObjects = new java.util.HashSet<>();
		for (com.bestpath.tool.edit.TransportRecord r : confirmed.values())
		{
			if (r.objectId >= 0)
			{
				confirmedObjects.add(r.objectId + "@" + r.objectX + "," + r.objectY + "," + r.objectPlane);
			}
		}
		List<TransportCandidate> candidates = new ArrayList<>();
		for (TransportCandidate c : all)
		{
			if (!rejected.contains(c.id()) && !confirmedObjects.contains(c.id()))
			{
				candidates.add(c);
			}
		}
		this.confirmed = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(confirmed));
		this.candidates = Collections.unmodifiableList(candidates);
		this.all = Collections.unmodifiableList(new ArrayList<>(all));
		this.suggestions = Collections.unmodifiableMap(suggestions);
		// teleport suggestions already confirmed or rejected are left out too
		java.util.Set<String> decided = new java.util.HashSet<>(rejected);
		for (com.bestpath.tool.edit.TransportRecord r : confirmed.values())
		{
			if (r.suggestionId != null)
			{
				decided.add(r.suggestionId);
			}
		}
		List<CacheTableTeleports.Destination> open = new ArrayList<>();
		for (CacheTableTeleports.Destination d : destinations)
		{
			if (!decided.contains(d.id()))
			{
				open.add(d);
			}
		}
		this.destinations = Collections.unmodifiableList(open);
		for (TransportCandidate c : candidates)
		{
			byCell.computeIfAbsent(cell(c.getX(), c.getY(), c.getPlane()), k -> new ArrayList<>()).add(c);
		}
	}

	private static int cell(int x, int y, int plane)
	{
		return ((x >> 6) << 8 | (y >> 6)) << 2 | plane;
	}

	/** Transports created or confirmed in the tool, by id. */
	public Map<String, com.bestpath.tool.edit.TransportRecord> getConfirmed()
	{
		return confirmed;
	}

	public List<TransportCandidate> getCandidates()
	{
		return candidates;
	}

	/** Every candidate found in the cache, including confirmed and rejected ones. */
	public List<TransportCandidate> getAllCandidates()
	{
		return all;
	}

	/** Suggestions for a candidate, best first (possibly empty). */
	public List<Suggestion> suggestionsFor(TransportCandidate c)
	{
		return suggestions.getOrDefault(c, Collections.emptyList());
	}

	public List<CacheTableTeleports.Destination> getDestinations()
	{
		return destinations;
	}

	/** Candidates whose footprint is within {@code radius} tiles of a tile, on that plane. */
	public List<TransportCandidate> near(int x, int y, int plane, int radius)
	{
		List<TransportCandidate> out = new ArrayList<>();
		for (int cx = (x - radius - 8) >> 6; cx <= (x + radius) >> 6; cx++)
		{
			for (int cy = (y - radius - 8) >> 6; cy <= (y + radius) >> 6; cy++)
			{
				for (TransportCandidate c : byCell.getOrDefault((cx << 8 | cy) << 2 | plane, Collections.emptyList()))
				{
					if (c.distanceTo(x, y) <= radius)
					{
						out.add(c);
					}
				}
			}
		}
		return out;
	}

	/** Candidates in a rectangle of tiles on a plane (for drawing). */
	public List<TransportCandidate> inArea(int x0, int y0, int x1, int y1, int plane)
	{
		List<TransportCandidate> out = new ArrayList<>();
		for (int cx = (x0 - 8) >> 6; cx <= x1 >> 6; cx++)
		{
			for (int cy = (y0 - 8) >> 6; cy <= y1 >> 6; cy++)
			{
				for (TransportCandidate c : byCell.getOrDefault((cx << 8 | cy) << 2 | plane, Collections.emptyList()))
				{
					if (c.getX() + c.getSizeX() > x0 && c.getX() <= x1 && c.getY() + c.getSizeY() > y0 && c.getY() <= y1)
					{
						out.add(c);
					}
				}
			}
		}
		return out;
	}
}

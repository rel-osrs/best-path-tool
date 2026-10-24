package com.bestpath.core.path;

import com.bestpath.core.graph.CoarseGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The coarse stage's "special" nodes for one query, next to the entrance sides every query shares: the
 * destination, the player, each transport's start and end, and per solid transport end used from beside it
 * (see {@link Beside}) one node per component beside it standing for its tiles there. Special {@code s} is
 * coarse node {@code sides + s}.
 */
final class CoarseNodes
{
	/** What a special node is ({@link #role}). */
	static final int ROLE_DEST = 0;
	static final int ROLE_PLAYER = 1;
	static final int ROLE_T_START = 2;
	static final int ROLE_T_END = 3;
	/**
	 * The tiles beside a solid transport end (see {@link Beside}) in one component, as one node (ref = the solid
	 * tile): the end is boarded from there (no extra ticks; the corners count too, so it stays a lower bound) and,
	 * when a transport lands on it, stepped off to there (1 tick).
	 */
	static final int ROLE_BESIDE = 4;

	/** The query's transports ({@link #ref} of a transport's start or end is its index here). */
	final List<Transport> transports;
	/** How many special nodes there are. */
	final int count;
	/** Per special: its tile (-1: a teleport's start, which has none), role, reference and component (-1: none). */
	final int[] tile;
	final int[] role;
	final int[] ref;
	final int[] comp;
	/** Per special: its tiles when it is several ({@link #ROLE_BESIDE}), else null (its one tile is {@link #tile}). */
	final int[][] set;
	/** Per transport-end special: its number among the known end tiles of its component ({@link EndWalks}). */
	final int[] end;
	/** Per special with a {@link #set}: the numbers of its tiles among those known end tiles. */
	final int[][] endSet;
	/** The specials by component, and the specials on tiles no one can walk on, by tile. */
	final ByKey specialsIn;
	final ByKey specialsAt;
	/** Per solid tile, the nodes beside it it is boarded from (relaxed from the specials on that tile). */
	final Map<Integer, List<Integer>> boardNodes = new HashMap<>();
	/** The solid ends that transports used from beside them land on (stepped off to the tiles beside). */
	final Set<Integer> leaveEnds = new LinkedHashSet<>();

	/**
	 * The special nodes of query {@code q}; {@code ends} numbers the transport ends of each component (the walks
	 * between them are kept there).
	 */
	CoarseNodes(PathQuery q, CoarseGraph graph, Beside beside, EndWalks ends)
	{
		List<Transport> tr = q.getTransports();
		transports = tr;
		// specials: 0 = destination, 1 = player, then per transport its start and end, then per solid end of a
		// transport used from beside it and per component beside it, one node for its tiles there (ROLE_BESIDE)
		Set<Integer> boardEnds = new LinkedHashSet<>();
		List<int[]> besideSets = new ArrayList<>();
		List<Integer> besideEnd = new ArrayList<>();
		besideSets(graph, beside, tr, besideSets, besideEnd, boardEnds, leaveEnds);
		int nSpecial = 2 + 2 * tr.size() + besideSets.size();
		count = nSpecial;
		int[] spTile = new int[nSpecial];
		int[] spRole = new int[nSpecial];
		int[] spRef = new int[nSpecial];
		int[] spComp = new int[nSpecial];
		int[][] spSet = new int[nSpecial][];
		tile = spTile;
		role = spRole;
		ref = spRef;
		comp = spComp;
		set = spSet;
		spTile[0] = Tiles.pack(q.getDestX(), q.getDestY(), q.getDestPlane());
		spRole[0] = ROLE_DEST;
		spTile[1] = Tiles.pack(q.getStartX(), q.getStartY(), q.getStartPlane());
		spRole[1] = ROLE_PLAYER;
		for (int i = 0; i < tr.size(); i++)
		{
			Transport t = tr.get(i);
			spTile[2 + 2 * i] = t.hasStart() ? Tiles.pack(t.getStartX(), t.getStartY(), t.getStartPlane()) : -1;
			spRole[2 + 2 * i] = ROLE_T_START;
			spRef[2 + 2 * i] = i;
			spTile[3 + 2 * i] = Tiles.pack(t.getEndX(), t.getEndY(), t.getEndPlane());
			spRole[3 + 2 * i] = ROLE_T_END;
			spRef[3 + 2 * i] = i;
		}
		for (int i = 0; i < besideSets.size(); i++)
		{
			int sp = 2 + 2 * tr.size() + i;
			int end = besideEnd.get(i);
			spSet[sp] = besideSets.get(i);
			spTile[sp] = spSet[sp][0];
			spRole[sp] = ROLE_BESIDE;
			spRef[sp] = end;
			if (boardEnds.contains(end) && beside.boardTiles(end).length > 0)
			{
				boardNodes.computeIfAbsent(end, k -> new ArrayList<>()).add(sp);
			}
		}
		ByKey.Builder inB = new ByKey.Builder(nSpecial);
		ByKey.Builder atB = new ByKey.Builder(16);
		for (int s = 0; s < nSpecial; s++)
		{
			int t = spTile[s];
			spComp[s] = t < 0 ? -1 : graph.componentAt(Tiles.x(t), Tiles.y(t), Tiles.plane(t));
			if (spComp[s] >= 0)
			{
				inB.add(spComp[s], s);
			}
			else if (t >= 0)
			{
				// a tile no one can walk on (e.g. a transport landing): only nodes on the very same tile connect
				atB.add(t, s);
			}
		}
		specialsIn = inB.build();
		specialsAt = atB.build();
		// the transport ends' numbers among the known end tiles of their components (walks between them are kept)
		end = new int[nSpecial];
		endSet = new int[nSpecial][];
		for (int s = 2; s < nSpecial; s++)
		{
			if (spComp[s] < 0)
			{
				continue;
			}
			if (spSet[s] == null)
			{
				end[s] = ends.number(spComp[s], spTile[s]);
			}
			else
			{
				int[] tiles = spSet[s];
				endSet[s] = new int[tiles.length];
				for (int i = 0; i < tiles.length; i++)
				{
					endSet[s][i] = ends.number(spComp[s], tiles[i]);
				}
			}
		}
	}

	/**
	 * The tiles beside the solid ends of transports used from beside them, one set per solid end and component beside
	 * it ({@code sets}, with their solid end in {@code setEnd}); {@code boardEnds} and
	 * {@code leaveEnds} get the solid ends they are boarded at and landed on.
	 */
	static void besideSets(CoarseGraph graph, Beside beside, List<Transport> tr, List<int[]> sets, List<Integer> setEnd,
		Set<Integer> boardEnds, Set<Integer> leaveEnds)
	{
		for (Transport t : tr)
		{
			if (t.isUsedFromBeside())
			{
				int st = Tiles.pack(t.getStartX(), t.getStartY(), t.getStartPlane());
				int en = Tiles.pack(t.getEndX(), t.getEndY(), t.getEndPlane());
				boardEnds.add(st);
				leaveEnds.add(en);
				if (t.isTwoWay())
				{
					boardEnds.add(en);
					leaveEnds.add(st);
				}
			}
		}
		Set<Integer> solidEnds = new LinkedHashSet<>(boardEnds);
		solidEnds.addAll(leaveEnds);
		for (int end : solidEnds)
		{
			Map<Integer, List<Integer>> byComp = new LinkedHashMap<>();
			for (int n : beside.leaveTiles(end))
			{
				byComp.computeIfAbsent(graph.componentAt(Tiles.x(n), Tiles.y(n), Tiles.plane(n)), k -> new ArrayList<>()).add(n);
			}
			for (List<Integer> set : byComp.values())
			{
				sets.add(set.stream().mapToInt(Integer::intValue).toArray());
				setEnd.add(end);
			}
		}
	}
}

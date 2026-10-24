package com.bestpath.core.path;

import com.bestpath.core.graph.CoarseGraph;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * The pathfinder's coarse stage: a backward search from the destination over the coarse graph's nodes (the
 * entrance sides every query shares and the query's own special nodes, {@link CoarseNodes}). It gives every
 * component a lower bound of the ticks still needed to reach the destination ({@link CoarseBounds}), and from
 * those, when the tile search first enters a component, a bound for each of its tiles ({@link #field}). The
 * bounds never overestimate, so the tile search they aim still finds the true shortest route.
 * One query at a time: the work arrays are kept between queries.
 */
final class CoarseSearch
{
	/** "Not reached" / "no way": larger than any real number of ticks, and safe to add a few ticks to. */
	static final int INF = Integer.MAX_VALUE / 4;
	/** Once the player node is settled, the coarse search goes on max(this, 25 % of its bound) more ticks. */
	static final int EXTRA_MIN = 30;

	private final CoarseGraph graph;
	private final EntranceSides sides;
	private final ComponentWalks walks;
	private final CellWalks cells;
	private final Beside beside;
	/** The map's cell keys (the fast search's numbering). */
	private final int[] cellKeys;
	/** Per cell index (the fast search's numbering): its first component id. */
	private final int[] baseByIndex;
	/** Per cell index (the fast search's numbering): the coarse graph's index of the same cell. */
	private final int[] graphIndex;

	/** Per-query work of the coarse stage, kept between queries (one query at a time). */
	private int[] coarseDist = new int[0];
	private boolean[] coarseDone = new boolean[0];
	private final LongHeap coarseHeap = new LongHeap();
	private final LongHeap fieldHeap = new LongHeap();
	/** The walks from the query's destination and player tiles, and which tiles they are from (-1: none yet). */
	private final short[] destWalk = new short[4096];
	private final short[] playerWalk = new short[4096];
	private final int[] walkedFor = {-1, -1};

	CoarseSearch(CoarseGraph graph, int[] cellKeys, EntranceSides sides, ComponentWalks walks, CellWalks cells, Beside beside)
	{
		this.graph = graph;
		this.cellKeys = cellKeys;
		this.sides = sides;
		this.walks = walks;
		this.cells = cells;
		this.beside = beside;
		baseByIndex = new int[cellKeys.length];
		graphIndex = new int[baseByIndex.length];
		for (int i = 0; i < cellKeys.length; i++)
		{
			baseByIndex[i] = graph.cellComponentBase(cellKeys[i] >> 2, cellKeys[i] & 3);
			graphIndex[i] = graph.cellIndex(cellKeys[i] >> 2, cellKeys[i] & 3);
		}
	}

	/**
	 * The coarse backward search. Each node has two states: 0 = the way on
	 * from here starts with a crossing, a transport (or this is the
	 * destination); 1 = it starts with a walk inside the component. A walk
	 * may only follow a crossing or a transport, never another walk: a real
	 * path walks once through a component between two crossings, so this
	 * loses nothing, and it stops the search from sliding along a cell edge
	 * from one entrance piece to the next for free.
	 * <p>
	 * {@code extraUnit} > 0: the stopping margin leaves out whole units (see {@link Pathfinder#find(PathQuery, int, int)}).
	 * The search also stops past {@code cap} ticks, and with {@code always} it gives its bounds even when nothing leads
	 * from the query's start (else null).
	 */
	CoarseBounds bounds(PathQuery q, int extraUnit, int cap, boolean always)
	{
		int sides = this.sides.count();
		walks.keepEndsWithinLimit();
		CoarseNodes sp = new CoarseNodes(q, graph, beside, walks.ends());
		List<Transport> tr = sp.transports;
		int penalty = q.getPlaneChangePenalty();
		int[] spTile = sp.tile;
		int[] spRole = sp.role;
		int[] spRef = sp.ref;
		int[] spComp = sp.comp;
		walkedFor[0] = -1;
		walkedFor[1] = -1;

		int nodes = sides + sp.count;
		clearWork(nodes);
		int[] dist = coarseDist;
		boolean[] done = coarseDone;
		int[] lbComp = new int[graph.componentCount()];
		Arrays.fill(lbComp, INF);
		LongHeap heap = coarseHeap;
		heap.clear();
		int destNode = sides;
		dist[2 * destNode] = 0;
		heap.push(2L * destNode);
		int lbPlayer = INF; // the virtual player node
		int lbTele = INF; // the least over the teleports (their part of lbPlayer)
		int stopAt = INF;
		int unsettled = INF;
		while (!heap.isEmpty())
		{
			long k = heap.pop();
			int d = (int) (k >>> 32);
			int ns = (int) k;
			if (done[ns] || d != dist[ns])
			{
				continue;
			}
			if (d > stopAt || d > cap)
			{
				unsettled = d;
				break;
			}
			done[ns] = true;
			int n = ns >> 1;
			boolean walked = (ns & 1) == 1;
			boolean isSide = n < sides;
			int s = n - sides;
			int c = isSide ? this.sides.sideComp[n] : spComp[s];
			if (c >= 0 && d < lbComp[c])
			{
				lbComp[c] = d;
			}
			if (c >= 0 && !walked)
			{
				walkInside(sp, n, s, c, d, isSide);
			}
			if (isSide)
			{
				relax(heap, dist, 2 * (n ^ 1), d, 1); // one step across the cell border
				continue;
			}
			if (c < 0 && spTile[s] >= 0)
			{
				leaveSolidTile(sp, s, d);
			}
			switch (spRole[s])
			{
				case CoarseNodes.ROLE_PLAYER:
					lbPlayer = Math.min(lbPlayer, d);
					break;
				case CoarseNodes.ROLE_T_END:
				{
					// backwards over the transport: from its end to its start (or to the player node, for a teleport)
					Transport t = tr.get(spRef[s]);
					if (t.hasStart())
					{
						relax(heap, dist, 2 * (sides + 2 + 2 * spRef[s]), d, t.cost(penalty));
					}
					else
					{
						lbPlayer = Math.min(lbPlayer, d + t.cost(penalty));
						lbTele = Math.min(lbTele, d + t.cost(penalty));
					}
					break;
				}
				case CoarseNodes.ROLE_T_START:
				{
					Transport t = tr.get(spRef[s]);
					if (t.isTwoWay())
					{
						relax(heap, dist, 2 * (sides + 3 + 2 * spRef[s]), d, t.cost(penalty));
					}
					break;
				}
				case CoarseNodes.ROLE_BESIDE:
					stepOffBackwards(sp, s, d);
					break;
				default:
					break;
			}
			if (lbPlayer < INF && stopAt == INF && (heap.isEmpty() || (int) (heap.peek() >>> 32) >= lbPlayer))
			{
				// the player node is settled: a little more, then stop
				stopAt = lbPlayer + Math.max(EXTRA_MIN, (extraUnit > 0 ? lbPlayer % extraUnit : lbPlayer) / 4);
			}
		}
		if (lbPlayer == INF && !always)
		{
			return null; // nothing leads from the player to the destination
		}
		return result(sp, nodes, lbComp, unsettled, lbPlayer, lbTele);
	}

	/** Makes the work arrays ready for {@code nodes} nodes (index = node * 2 + state; kept between queries). */
	private void clearWork(int nodes)
	{
		if (coarseDist.length < 2 * nodes)
		{
			coarseDist = new int[2 * nodes];
			coarseDone = new boolean[2 * nodes];
		}
		Arrays.fill(coarseDist, 0, 2 * nodes, INF);
		Arrays.fill(coarseDone, 0, 2 * nodes, false);
	}

	/**
	 * From special {@code s} (the tiles beside a solid end), settled at {@code d} ticks: when transports land on that
	 * solid tile, backwards over the step off it, to everything on that tile.
	 */
	private void stepOffBackwards(CoarseNodes sp, int s, int d)
	{
		int sides = this.sides.count();
		int[] spRef = sp.ref;
		ByKey specialsAt = sp.specialsAt;
		if (sp.leaveEnds.contains(spRef[s]))
		{
			// backwards over the step off a solid landing tile: to everything on that tile
			for (int e = specialsAt.first(spRef[s]); e >= 0 && e < specialsAt.size() && specialsAt.key(e) == spRef[s]; e++)
			{
				relax(coarseHeap, coarseDist, 2 * (sides + specialsAt.value(e)), d, Beside.STEP_OFF);
			}
		}
	}

	/**
	 * From node {@code n} (special {@code s} when it is no entrance side) of component {@code c}, settled at {@code d}
	 * ticks in state 0: a walk inside the component to its other nodes, arriving there in state 1.
	 */
	private void walkInside(CoarseNodes sp, int n, int s, int c, int d, boolean isSide)
	{
		int sides = this.sides.count();
		int[] spTile = sp.tile;
		int[][] spSet = sp.set;
		int[] spEnd = sp.end;
		int[][] spEndSet = sp.endSet;
		ByKey specialsIn = sp.specialsIn;
		int[] dist = coarseDist;
		boolean[] done = coarseDone;
		LongHeap heap = coarseHeap;
		int[] cs = this.sides.compSides[c];
		int[] matrix = isSide ? walks.matrix(c) : null;
		int row = isSide ? indexOf(cs, n) : -1;
		int[] fromN = isSide ? null : nodeSideDists(sp, s, c);
		for (int j = 0; j < cs.length; j++)
		{
			int m = cs[j];
			if (m == n || done[2 * m + 1])
			{
				continue;
			}
			int w = isSide ? matrix[row * cs.length + j] : fromN[j];
			relax(heap, dist, 2 * m + 1, d, w);
		}
		int first = specialsIn.first(c);
		if (first >= 0)
		{
			// walking is symmetric (a step and its way back are one stored bit), so this node's own distances
			// to the others serve: between transport ends they are kept per component; from the player and the
			// destination, one walk per query (no whole 8 KB walk table is kept per tile)
			short[] ends0 = isSide || s < 2 || specialsIn.count(first) < 2 ? null : endRow(c, s, sp);
			for (int e = first; e < specialsIn.size() && specialsIn.key(e) == c; e++)
			{
				int o = specialsIn.value(e);
				int m = sides + o;
				if (m == n || done[2 * m + 1])
				{
					continue;
				}
				int w;
				if (isSide)
				{
					w = nodeSideDists(sp, o, c)[row];
				}
				else if (s < 2)
				{
					short[] from = queryWalk(s, spTile);
					w = spSet[o] == null ? at(from, spTile[o]) : ComponentWalks.minOver(from, spSet[o]);
				}
				else if (o < 2)
				{
					short[] to = queryWalk(o, spTile);
					w = spSet[s] == null ? at(to, spTile[s]) : ComponentWalks.minOver(to, spSet[s]);
				}
				else
				{
					w = spSet[o] == null ? rowAt(ends0, spEnd[o]) : rowMin(ends0, spEndSet[o]);
				}
				relax(heap, dist, 2 * m + 1, d, w);
			}
		}
	}

	/**
	 * From special {@code s} on a tile no one can walk on, settled at {@code d} ticks: to the other nodes on the very
	 * same tile, and to the nodes beside it that a transport here is boarded from.
	 */
	private void leaveSolidTile(CoarseNodes sp, int s, int d)
	{
		int sides = this.sides.count();
		int[] spTile = sp.tile;
		ByKey specialsAt = sp.specialsAt;
		int[] dist = coarseDist;
		LongHeap heap = coarseHeap;
		for (int e = specialsAt.first(spTile[s]); e >= 0 && e < specialsAt.size() && specialsAt.key(e) == spTile[s]; e++)
		{
			relax(heap, dist, 2 * (sides + specialsAt.value(e)), d, 0);
		}
		if (!sp.boardNodes.isEmpty())
		{
			// a transport here is used from the tiles beside this solid tile (no extra ticks)
			for (int o : sp.boardNodes.getOrDefault(spTile[s], List.of()))
			{
				relax(heap, dist, 2 * (sides + o), d, 0);
			}
		}
	}

	/** The bounds the finished search gives (see {@link CoarseBounds}). */
	private CoarseBounds result(CoarseNodes sp, int nodes, int[] lbComp, int unsettled, int lbPlayer, int lbTele)
	{
		int sides = this.sides.count();
		int[] dist = coarseDist;
		boolean[] done = coarseDone;
		ByKey specialsAt = sp.specialsAt;
		CoarseBounds b = new CoarseBounds();
		b.lbComp = lbComp;
		b.unsettled = unsettled; // INF if everything reachable was settled: the rest cannot be on any route
		b.lbPlayer = lbPlayer;
		b.lbTele = Math.min(lbTele, unsettled); // a teleport landing not settled: at least the stopping bound
		b.dist0 = new int[nodes];
		for (int i = 0; i < nodes; i++)
		{
			// only settled values are final; anything not settled is at least the stopping bound
			b.dist0[i] = done[2 * i] ? dist[2 * i] : unsettled;
		}
		b.spTile = sp.tile;
		b.spSet = sp.set;
		b.specialsIn = sp.specialsIn;
		if (!sp.leaveEnds.isEmpty())
		{
			b.solidBound = new HashMap<>();
			for (int t : sp.leaveEnds)
			{
				int best = INF;
				int e0 = specialsAt.first(t);
				for (int e = e0; e >= 0 && e < specialsAt.size() && specialsAt.key(e) == t; e++)
				{
					best = Math.min(best, b.dist0[sides + specialsAt.value(e)]);
				}
				if (e0 >= 0) // INF: no way on from there at all
				{
					b.solidBound.put(t, best);
				}
			}
		}
		b.fieldCache = new int[graph.componentCount()][];
		return b;
	}

	/**
	 * The tile search's bound from these coarse bounds: per tile, the walk inside its component to one of its nodes
	 * plus the coarse bound from there ({@link #field}); as the cheaper bound, one number per component.
	 */
	FastSearch.Bound tileBound(CoarseBounds b)
	{
		return new FastSearch.Bound()
		{
			@Override
			public int h(int ci, int l)
			{
				// the tile's own bound: walk inside its component to one of its nodes, then the coarse bound from there
				int label = graphIndex[ci] < 0 ? 0 : graph.localLabel(graphIndex[ci], l);
				if (label == 0)
				{
					if (!b.solidBound.isEmpty())
					{
						// a solid tile stepped off after landing (a fairy ring's middle): its coarse bound
						Integer v = b.solidBound.get(Tiles.fromCell(cellKeys[ci], l));
						if (v != null)
						{
							return v;
						}
					}
					return 0;
				}
				int c = baseByIndex[ci] + label - 1;
				int[] f = b.fieldCache[c];
				if (f == null)
				{
					f = field(b, c);
					b.fieldCache[c] = f;
				}
				return f[l];
			}

			@Override
			public int coarse(int ci, int l)
			{
				// one number for the whole component: the least settled coarse value of its nodes (every
				// tile's own bound is a walk to one of them plus at least that), or the stopping bound.
				// Always the same for a tile within one query: the search checks entries against it.
				int label = graphIndex[ci] < 0 ? 0 : graph.localLabel(graphIndex[ci], l);
				if (label == 0)
				{
					return 0;
				}
				int c = baseByIndex[ci] + label - 1;
				return b.lbComp[c] == INF ? b.unsettled : b.lbComp[c];
			}
		};
	}

	/** The coarse bounds' one number per component ({@link FastSearch.Bound#coarse}; no per-tile bound). */
	FastSearch.Bound componentBound(CoarseBounds b)
	{
		return new FastSearch.Bound()
		{
			@Override
			public int h(int ci, int l)
			{
				return 0;
			}

			@Override
			public int coarse(int ci, int l)
			{
				int label = graphIndex[ci] < 0 ? 0 : graph.localLabel(graphIndex[ci], l);
				if (label == 0)
				{
					return 0;
				}
				int c = baseByIndex[ci] + label - 1;
				return b.lbComp[c] == INF ? b.unsettled : b.lbComp[c];
			}
		};
	}

	/**
	 * The bound for every tile of a component: the shortest walk inside the
	 * cell to one of its nodes, plus that node's state-0 bound (the way on
	 * after the walk starts with a crossing or transport). Much sharper than
	 * one number per component. Made once per query per component.
	 */
	int[] field(CoarseBounds b, int c)
	{
		int[] f = new int[4096];
		Arrays.fill(f, INF);
		int cell = graph.componentCell(c);
		byte[] bits = cells.bits(cell);
		// the cell's labels, unpacked once per query (several components of one cell share them)
		short[] labels = b.labelCache.computeIfAbsent(cell, k -> graph.cellLabels(k >> 2, k & 3));
		int label = c - graph.cellComponentBase(cell >> 2, cell & 3) + 1;
		LongHeap heap = fieldHeap;
		heap.clear();
		for (int m : sides.compSides[c])
		{
			int v = b.dist0[m];
			if (v >= INF)
			{
				continue;
			}
			for (int t : sides.sideTiles[m])
			{
				int l = Tiles.local(t);
				if (v < f[l])
				{
					f[l] = v;
					heap.push((long) v << 32 | l);
				}
			}
		}
		for (int e = b.specialsIn.first(c); e >= 0 && e < b.specialsIn.size() && b.specialsIn.key(e) == c; e++)
		{
			int o = b.specialsIn.value(e);
			int v = b.dist0[sides.count() + o];
			int[] set = b.spSet[o];
			for (int i = 0, n = set != null ? set.length : 1; i < n; i++)
			{
				int l = Tiles.local(set != null ? set[i] : b.spTile[o]);
				if (v < f[l])
				{
					f[l] = v;
					heap.push((long) v << 32 | l);
				}
			}
		}
		while (!heap.isEmpty())
		{
			long k = heap.pop();
			int v = (int) (k >>> 32);
			int l = (int) k;
			if (v != f[l])
			{
				continue;
			}
			int lx = l >> 6;
			int ly = l & 63;
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					int nx = lx + dx;
					int ny = ly + dy;
					if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx > 63 || ny > 63)
					{
						continue;
					}
					int n = nx << 6 | ny;
					if (labels[n] == label && v + 1 < f[n] && CoarseGraph.canStepInCell(bits, lx, ly, dx, dy))
					{
						f[n] = v + 1;
						heap.push((long) (v + 1) << 32 | n);
					}
				}
			}
		}
		// tiles nothing reached: no known way on from them
		for (int i = 0; i < 4096; i++)
		{
			if (f[i] >= INF)
			{
				f[i] = b.unsettled;
			}
		}
		return f;
	}

	/** About how many bytes the kept work arrays take. */
	long bytes()
	{
		return coarseDist.length == 0 ? 0 : 5L * coarseDist.length + 32;
	}

	/** Forgets the kept work arrays (made again by the next query). */
	void clear()
	{
		coarseDist = new int[0];
		coarseDone = new boolean[0];
	}

	private static void relax(LongHeap heap, int[] dist, int m, int d, int w)
	{
		if (w >= INF)
		{
			return;
		}
		int nd = d + w;
		if (nd < dist[m])
		{
			dist[m] = nd;
			heap.push((long) nd << 32 | m);
		}
	}

	private static int indexOf(int[] a, int v)
	{
		for (int i = 0; i < a.length; i++)
		{
			if (a[i] == v)
			{
				return i;
			}
		}
		return -1;
	}

	private static int at(short[] dist, int tile)
	{
		int v = dist[Tiles.local(tile)];
		return v < 0 ? INF : v;
	}

	private static int rowAt(short[] row, int k)
	{
		int v = row[k];
		return v < 0 ? INF : v;
	}

	private static int rowMin(short[] row, int[] ks)
	{
		int best = INF;
		for (int k : ks)
		{
			int v = row[k];
			if (v >= 0 && v < best)
			{
				best = v;
			}
		}
		return best;
	}

	/** Special {@code s}'s walking distances to its component's entrance sides (a tile, or the tiles beside a solid end). */
	private int[] nodeSideDists(CoarseNodes sp, int s, int c)
	{
		if (sp.set[s] == null)
		{
			return walks.sideDists(sp.tile[s], c);
		}
		return walks.besideSideDists(sp.ref[s], sp.set[s], c);
	}

	/** The walk from the query's destination (special 0) or player (special 1), made once per query. */
	private short[] queryWalk(int s, int[] spTile)
	{
		short[] w = s == 0 ? destWalk : playerWalk;
		if (walkedFor[s] != spTile[s])
		{
			cells.walkFrom(spTile[s], w);
			walkedFor[s] = spTile[s];
		}
		return w;
	}

	/**
	 * The kept walk from transport end special {@code s} (a tile, or the tiles beside a solid end) to its component's
	 * known ends.
	 */
	private short[] endRow(int c, int s, CoarseNodes sp)
	{
		EndWalks ends = walks.ends();
		return sp.set[s] == null ? ends.row(c, sp.end[s], sp.tile[s], null, 0)
			: ends.row(c, -1, 0, sp.set[s], (long) sp.ref[s] << 32 | sp.set[s][0]);
	}
}

package com.bestpath.core.path;

import com.bestpath.core.collision.CollisionMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pathfinder's tile search, built for speed. It follows the same rules and
 * finds the same ticks as the plain tile search the tests check it against
 * (which knows no closed tiles or Wilderness limits). What makes it fast:
 * <ul>
 * <li>cells are found by a direct table lookup, not a search;</li>
 * <li>each tile has one byte saying which of the 8 steps are possible
 * (worked out once per cell);</li>
 * <li>ticks and arrival directions live in flat per-cell arrays that are
 * kept between searches (a stamp says which search filled them);</li>
 * <li>the queue is a bucket queue (ticks are small whole numbers);</li>
 * <li>a per-cell bit set says which tiles have transports.</li>
 * </ul>
 * Exact A*: tiles are re-opened when reached cheaper, so the bound only has
 * to be admissible. One search at a time; each search's own state is a {@link Run}.
 */
final class FastSearch
{
	/** Lower bound of the ticks left from a tile (cell index, local index) to the destination. */
	interface Bound
	{
		int h(int cellIndex, int local);

		/**
		 * A cheaper bound, never more than {@link #h} (default: {@link #h} itself). Teleport landings are
		 * queued with it and get their {@link #h} only if they come out of the queue: most never do.
		 */
		default int coarse(int cellIndex, int local)
		{
			return h(cellIndex, local);
		}
	}

	/** Directions: north, north-east, east, south-east, south, south-west, west, north-west. */
	private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
	private static final int[] DY = {1, 1, 0, -1, -1, -1, 0, 1};
	private static final byte BY_TRANSPORT = 8;
	private static final byte START = 9;
	/** Bounds this large mean "cannot reach the destination from here". */
	static final int NEVER = Integer.MAX_VALUE / 8;
	/** Costs are kept as ticks << SHIFT plus the number of diagonal steps (capped at DIAG_MASK). */
	private static final int SHIFT = 12;
	static final int DIAG_MASK = (1 << SHIFT) - 1;
	/** What the many-targets check says about a tile just taken from the queue ({@link Run#checkTargets}). */
	private static final int GO_ON = 0;
	private static final int STOP = 1;
	private static final int SKIP = 2;

	private final CollisionMap map;
	/** Cell key (regionId << 2 | plane) to cell index, or -1. */
	private final int[] index = new int[1 << 18];
	private final int[] keys;
	/** Per cell index: step bits (N=1, E=2, NE=4, NW=8), and per tile a byte of possible steps (bit d = direction d). */
	private final byte[][] bits;
	private final byte[][] dirs;
	/** Per cell index: ticks and arrival direction of the search that last touched it (see {@link #stamp}). */
	private final int[][] gs;
	private final byte[][] prevs;
	private final int[] stamp;
	private int search;
	/** The queue, kept between searches (one search at a time). */
	private BucketQueue buckets = new BucketQueue();

	/** {@link #zoneOf}: the limits it was made for, and per cell index its zone (-1 mixed, -2 not asked). */
	private PathQuery.Limits limitsFor;
	private int[] limitCells;

	private int zoneOf(PathQuery.Limits limits, int ci, int l)
	{
		if (limits != limitsFor)
		{
			limitsFor = limits;
			limitCells = new int[keys.length];
			Arrays.fill(limitCells, -2);
		}
		int key = keys[ci];
		int x0 = (key >> 10) << 6;
		int y0 = ((key >> 2) & 0xFF) << 6;
		int z = limitCells[ci];
		if (z == -2)
		{
			z = limits.cellZone(x0, y0);
			limitCells[ci] = z;
		}
		return z >= 0 ? z : limits.zone(x0 + (l >> 6), y0 + (l & 63));
	}

	/** {@link #closing}: the closed tiles it was made for, and per cell index 0 = not asked, else 1 + {@link PathQuery.Closed#cell}. */
	private PathQuery.Closed closedFor;
	private byte[] closedCells;

	/**
	 * The bound with closed tiles out of reach (such as the avoided Wilderness): every place that queues a tile
	 * (a step, a transport's landing, a teleport's) skips one whose bound says {@link #NEVER}.
	 */
	private Bound closing(Bound inner, PathQuery.Closed closed)
	{
		if (closed != closedFor)
		{
			closedFor = closed;
			closedCells = new byte[keys.length];
		}
		return new Bound()
		{
			@Override
			public int h(int ci, int l)
			{
				return shut(closed, ci, l) ? NEVER : inner.h(ci, l);
			}

			@Override
			public int coarse(int ci, int l)
			{
				return shut(closed, ci, l) ? NEVER : inner.coarse(ci, l);
			}
		};
	}

	private boolean shut(PathQuery.Closed closed, int ci, int l)
	{
		int c = closedCells[ci];
		int key = keys[ci];
		int x0 = (key >> 10) << 6;
		int y0 = ((key >> 2) & 0xFF) << 6;
		if (c == 0)
		{
			c = 1 + closed.cell(x0, y0);
			closedCells[ci] = (byte) c;
		}
		return c == 1 + PathQuery.Closed.SHUT || c == 1 + PathQuery.Closed.PART && closed.tile(x0 + (l >> 6), y0 + (l & 63));
	}

	FastSearch(CollisionMap map)
	{
		this.map = map;
		keys = map.cellKeys();
		Arrays.fill(index, -1);
		for (int i = 0; i < keys.length; i++)
		{
			index[keys[i]] = i;
		}
		bits = new byte[keys.length][];
		dirs = new byte[keys.length][];
		gs = new int[keys.length][];
		prevs = new byte[keys.length][];
		stamp = new int[keys.length];
		doorCache = new byte[keys.length][];
	}

	/** Per cell index: door bits of each tile for {@link #doorsFor} (made when first needed). */
	private byte[][] doorCache;
	private DoorMap doorsFor;

	private byte[] doorBits(int ci, DoorMap doors)
	{
		byte[] b = doorCache[ci];
		if (b == null)
		{
			b = new byte[4096];
			int key = keys[ci];
			int bx = (key >> 10) << 6;
			int by = ((key >> 2) & 0xFF) << 6;
			for (int l = 0; l < 4096; l++)
			{
				b[l] = (byte) doors.bits(bx + (l >> 6), by + (l & 63), key & 3);
			}
			doorCache[ci] = b;
		}
		return b;
	}

	int cellIndex(int x, int y, int plane)
	{
		if (x < 0 || y < 0 || x >= 16384 || y >= 16384)
		{
			return -1;
		}
		return index[((x >> 6) << 8 | (y >> 6)) << 2 | plane];
	}

	private byte[] bits(int ci)
	{
		byte[] b = bits[ci];
		if (b == null)
		{
			b = map.cellStepBits(keys[ci] >> 2, keys[ci] & 3);
			bits[ci] = b != null ? b : new byte[4096];
			b = bits[ci];
		}
		return b;
	}

	/** Step bits of a tile, 0 outside the map. */
	private int nib(int x, int y, int p)
	{
		int ci = cellIndex(x, y, p);
		return ci < 0 ? 0 : bits(ci)[(x & 63) << 6 | (y & 63)];
	}

	/** The possible steps of every tile of a cell, one byte each (made once). */
	private byte[] dirs(int ci)
	{
		byte[] d = dirs[ci];
		if (d != null)
		{
			return d;
		}
		d = new byte[4096];
		int key = keys[ci];
		int p = key & 3;
		int bx = (key >> 10) << 6;
		int by = ((key >> 2) & 0xFF) << 6;
		byte[] own = bits(ci);
		for (int l = 0; l < 4096; l++)
		{
			int x = bx + (l >> 6);
			int y = by + (l & 63);
			int m = 0;
			int o = own[l];
			if ((o & CollisionMap.N) != 0)
			{
				m |= 1;
			}
			if ((o & CollisionMap.NE) != 0)
			{
				m |= 2;
			}
			if ((o & CollisionMap.E) != 0)
			{
				m |= 4;
			}
			if ((nib(x + 1, y - 1, p) & CollisionMap.NW) != 0)
			{
				m |= 8; // south-east = the reverse of a north-west step from (x+1, y-1)
			}
			if ((nib(x, y - 1, p) & CollisionMap.N) != 0)
			{
				m |= 16; // south
			}
			if ((nib(x - 1, y - 1, p) & CollisionMap.NE) != 0)
			{
				m |= 32; // south-west
			}
			if ((nib(x - 1, y, p) & CollisionMap.E) != 0)
			{
				m |= 64; // west
			}
			if ((o & CollisionMap.NW) != 0)
			{
				m |= 128;
			}
			d[l] = (byte) m;
		}
		dirs[ci] = d;
		return d;
	}

	/**
	 * About how many bytes the per-cell arrays kept between searches take
	 * (step bits, possible steps, ticks, arrivals, door bits): 4 KB, 4 KB,
	 * 16 KB, 4 KB and 4 KB per cell entered; the queue's buckets and the transport indexes.
	 */
	long cacheBytes()
	{
		long n = buckets.bytes();
		n += (transportIndex == null ? 0 : transportIndex.bytes()) + (indexBefore == null ? 0 : indexBefore.bytes());
		for (int ci = 0; ci < keys.length; ci++)
		{
			n += bits[ci] != null ? 4112 : 0;
			n += dirs[ci] != null ? 4112 : 0;
			n += gs[ci] != null ? 16400 + 4112 : 0; // ticks and arrival directions go together
			n += doorCache[ci] != null ? 4112 : 0;
		}
		return n;
	}

	/**
	 * Drops every per-cell array kept between searches. They hold nothing a
	 * search depends on (the next search works them out again or clears them
	 * before use), so routes stay the same; only the next searches are a
	 * little slower. Call between searches only.
	 */
	void clearCaches()
	{
		buckets = new BucketQueue();
		transportIndex = null;
		indexBefore = null;
		Arrays.fill(bits, null);
		Arrays.fill(dirs, null);
		Arrays.fill(gs, null);
		Arrays.fill(prevs, null);
		Arrays.fill(doorCache, null);
		Arrays.fill(stamp, 0);
		search = 0;
		doorsFor = null;
	}

	/** This search's ticks array for a cell (cleared the first time this search touches it). */
	private int[] g(int ci)
	{
		if (stamp[ci] != search)
		{
			int[] a = gs[ci];
			if (a == null)
			{
				a = new int[4096];
				gs[ci] = a;
				prevs[ci] = new byte[4096];
			}
			Arrays.fill(a, Integer.MAX_VALUE);
			stamp[ci] = search;
		}
		return gs[ci];
	}

	/**
	 * The search; {@code beside} (null: none) says where transports used from beside their ends are boarded
	 * and stepped off (see {@link Beside}); many targets at once when {@code targets} is set (null: one route).
	 */
	Route search(PathQuery q, Bound bound, Beside beside, SearchTargets targets)
	{
		return search(q, bound, beside, targets, Integer.MAX_VALUE);
	}

	/**
	 * As {@link #search(PathQuery, Bound, Beside, SearchTargets)}, looking only for a route of at most {@code limit}
	 * ticks (none longer is found: the search stops once every route left is longer, by its bound).
	 */
	Route search(PathQuery q, Bound bound, Beside beside, SearchTargets targets, int limit)
	{
		long t0 = System.nanoTime();
		search++;
		return new Run(q, bound, beside, targets, limit, t0).search();
	}

	/**
	 * One search ({@link #search(PathQuery, Bound, Beside, SearchTargets, int)}): the query's settings and the search's
	 * own bookkeeping (how each tile was reached, the tiles queued with the cheap bound only), dropped when it ends.
	 * The ticks and arrival directions themselves live in the per-cell arrays kept between searches.
	 */
	private final class Run
	{
		private final PathQuery q;
		private final SearchTargets targets;
		private final int limit;
		private final long t0;
		private final DoorMap doorsQ;
		private final int doorStep;
		private final Bound bound;
		private final List<Transport> transports;
		private final int penalty;
		/** Transports by the tile they are used from {index, reversed}, and a bit per tile that has any. */
		private final Map<Integer, List<int[]>> from;
		private final long[][] hasFrom;
		/** Transports used from beside their ends: the solid tiles they land on, with the steps off them. */
		private final Map<Integer, Integer> stepOff;
		/** Landing tile -> the transport that got there (index * 2, plus 1 when used backwards). */
		private final Map<Integer, Integer> via = new HashMap<>();
		/** Landing tile -> the tile a transport was used from, when that is beside its end (not the end itself). */
		private final Map<Integer, Integer> usedAt = new HashMap<>();
		/** Tiles queued with the cheap bound only (teleport landings), as cellIndex << 12 | local. */
		private final Set<Integer> lazy = new HashSet<>();
		private final BucketQueue open;
		/** The destination's cell index and local index (-1: many targets, no single destination), and its tile. */
		private final int destCi;
		private final int destL;
		private final int destTile;
		/** The start's cell index (-1: off the walking map) and tile. */
		private final int sci;
		private final int startTile;
		/**
		 * Tiles outside the walking map (open sea: no cell at all) can be a transport's end or start: a
		 * destination there is finished by the best transport into it {g, use, from tile, g at from tile}.
		 */
		private final int[] offDest = {Integer.MAX_VALUE, -1, 0, 0};
		/**
		 * Teleports the start is too deep in the Wilderness for: offered later, from every tile the search reaches
		 * that is shallow enough for them (waiting[zone]: the teleports that work up to that zone; null: none), and
		 * per zone the least ticks they were offered from so far.
		 */
		private final PathQuery.Limits limitsQ;
		private List<Integer>[] waiting;
		private int[] offeredAt;
		private int expanded;

		Run(PathQuery q, Bound bound, Beside beside, SearchTargets targets, int limit, long t0)
		{
			this.q = q;
			this.targets = targets;
			this.limit = limit;
			this.t0 = t0;
			doorsQ = q.getDoors();
			doorStep = q.getDoorCost() << SHIFT;
			if (doorsQ != doorsFor)
			{
				Arrays.fill(doorCache, null);
				doorsFor = doorsQ;
			}
			if (q.getClosed() != null)
			{
				bound = closing(bound, q.getClosed()); // a closed tile: "no way on from there", so it is never queued
			}
			this.bound = bound;
			transports = q.getTransports();
			penalty = q.getPlaneChangePenalty();
			TransportIndex ix = index(transports, beside);
			from = ix.from;
			hasFrom = ix.hasFrom;
			stepOff = ix.stepOff;
			open = buckets;
			open.reset();
			int dx0 = q.getDestX();
			int dy0 = q.getDestY();
			int dp0 = q.getDestPlane();
			// many targets: no single destination (the targets are checked as tiles come off the queue)
			destCi = targets != null ? -1 : cellIndex(dx0, dy0, dp0);
			destL = (dx0 & 63) << 6 | (dy0 & 63);
			sci = cellIndex(q.getStartX(), q.getStartY(), q.getStartPlane());
			startTile = Tiles.pack(q.getStartX(), q.getStartY(), q.getStartPlane());
			destTile = targets != null ? -1 : Tiles.pack(dx0, dy0, dp0);
			limitsQ = q.getLimits();
		}

		Route search()
		{
			if (targets != null)
			{
				markTargets();
			}
			queueStart();
			queueTeleports();
			while (!open.isEmpty())
			{
				long k = open.pop();
				int ci = (int) ((k >>> 12) & 0x3FFF);
				int l = (int) (k & 4095);
				int g = gs[ci][l];
				if (!upToDate(k, ci, l, g))
				{
					continue;
				}
				if (offDest[1] >= 0 && open.lastF() >= offDest[0] >> SHIFT)
				{
					break; // nothing left can beat the transport into the destination off the walking map
				}
				if (open.lastF() > limit)
				{
					break; // every route left is longer than the limit (its bound says so)
				}
				expanded++;
				if (targets != null)
				{
					int t = checkTargets(ci, l, g);
					if (t == STOP)
					{
						break;
					}
					if (t == SKIP)
					{
						continue;
					}
				}
				if (ci == destCi && l == destL)
				{
					return build(Tiles.fromCell(keys[ci], l), g >> SHIFT, via, usedAt, transports, penalty, expanded, System.nanoTime() - t0,
						q.getDoors(), q.getDoorCost());
				}
				if (waiting != null)
				{
					offerLimited(ci, l, g);
				}
				expandSteps(ci, l, g);
				expandTransports(ci, l, g);
			}
			return finish();
		}

		/** Per cell index, a bit per target tile. */
		private void markTargets()
		{
			targets.bits = new long[keys.length][];
			for (int t : targets.indexOf.keySet())
			{
				int tci = cellIndex(Tiles.x(t), Tiles.y(t), Tiles.plane(t));
				if (tci >= 0)
				{
					if (targets.bits[tci] == null)
					{
						targets.bits[tci] = new long[64];
					}
					int tl = Tiles.local(t);
					targets.bits[tci][tl >> 6] |= 1L << (tl & 63);
				}
			}
		}

		/** Queues the start tile; for a start off the walking map, the landings of the transports from that very tile. */
		private void queueStart()
		{
			if (sci >= 0)
			{
				int l = (q.getStartX() & 63) << 6 | (q.getStartY() & 63);
				g(sci)[l] = 0;
				prevs[sci][l] = START;
				open.push(bound.h(sci, l), 0, sci, l);
			}
			else
			{
				queueFromOffMap();
			}
		}

		/** The player is off the walking map (on a boat at sea): only transports from that very tile lead on. */
		private void queueFromOffMap()
		{
			for (int i = 0; i < transports.size(); i++)
			{
				Transport tr = transports.get(i);
				for (int dir = 0; dir < 2 && tr.hasStart(); dir++)
				{
					if (dir == 1 && !tr.isTwoWay())
					{
						continue;
					}
					int fromT = dir == 0 ? Tiles.pack(tr.getStartX(), tr.getStartY(), tr.getStartPlane())
						: Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane());
					if (fromT != startTile)
					{
						continue;
					}
					int toT = dir == 0 ? Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane())
						: Tiles.pack(tr.getStartX(), tr.getStartY(), tr.getStartPlane());
					int tg = tr.cost(penalty) << SHIFT;
					int nci = cellIndex(Tiles.x(toT), Tiles.y(toT), Tiles.plane(toT));
					if (nci < 0)
					{
						if (toT == destTile && tg < offDest[0])
						{
							offDest[0] = tg;
							offDest[1] = i * 2 + dir;
							offDest[2] = startTile;
							offDest[3] = 0;
						}
						continue;
					}
					int nl = Tiles.local(toT);
					int h = bound.h(nci, nl);
					int[] ga = g(nci);
					if (h < NEVER && tg < ga[nl])
					{
						ga[nl] = tg;
						prevs[nci][nl] = BY_TRANSPORT;
						if (!lazy.isEmpty())
						{
							lazy.remove(nci << 12 | nl);
						}
						via.put(toT, i * 2 + dir);
						open.push((tg >> SHIFT) + h, tg, nci, nl);
					}
				}
			}
		}

		/**
		 * Queues each teleport's landing with the cheap bound; the teleports the start is too deep in the Wilderness
		 * for wait instead ({@link #waiting}, offered later by {@link #offerLimited}).
		 */
		private void queueTeleports()
		{
			int startZone = limitsQ == null ? 0 : limitsQ.zone(q.getStartX(), q.getStartY());
			for (int i = 0; i < transports.size(); i++)
			{
				Transport tr = transports.get(i);
				if (tr.hasStart())
				{
					continue;
				}
				if (startZone > 0)
				{
					int deepest = limitsQ.deepest(tr);
					if (deepest < startZone)
					{
						if (waiting == null)
						{
							@SuppressWarnings("unchecked")
							List<Integer>[] w = new List[startZone];
							waiting = w;
							offeredAt = new int[startZone];
							Arrays.fill(offeredAt, Integer.MAX_VALUE);
						}
						if (waiting[deepest] == null)
						{
							waiting[deepest] = new ArrayList<>();
						}
						waiting[deepest].add(i);
						continue;
					}
				}
				int ci = cellIndex(tr.getEndX(), tr.getEndY(), tr.getEndPlane());
				if (ci < 0)
				{
					continue;
				}
				int l = (tr.getEndX() & 63) << 6 | (tr.getEndY() & 63);
				int c = tr.cost(penalty);
				int gc = c << SHIFT;
				int[] ga = g(ci);
				if (gc < ga[l])
				{
					int h = bound.coarse(ci, l);
					if (h >= NEVER)
					{
						continue; // no way on from there
					}
					ga[l] = gc;
					prevs[ci][l] = BY_TRANSPORT;
					via.put(Tiles.fromCell(keys[ci], l), i * 2);
					lazy.add(ci << 12 | l);
					open.push(c + h, gc, ci, l);
				}
			}
		}

		/**
		 * False when the entry just taken from the queue ({@code k}, for tile {@code ci}, {@code l} now at {@code g})
		 * is to be skipped: an older, worse entry, or a tile queued with the cheap bound whose real bound is higher
		 * (it is queued again with that) or says there is no way on.
		 */
		private boolean upToDate(long k, int ci, int l, int g)
		{
			if ((g & DIAG_MASK) != (int) (k >>> 26))
			{
				return false; // an older, worse entry
			}
			if (!lazy.isEmpty() && lazy.contains(ci << 12 | l))
			{
				// queued with the cheap bound: work out the real one now, and queue again if it is higher
				if ((g >> SHIFT) + bound.coarse(ci, l) != open.lastF())
				{
					return false; // an older, worse entry
				}
				lazy.remove(ci << 12 | l);
				int h = bound.h(ci, l);
				if (h >= NEVER)
				{
					return false; // no way on from there
				}
				if ((g >> SHIFT) + h != open.lastF())
				{
					open.push((g >> SHIFT) + h, g, ci, l);
					return false;
				}
			}
			else if ((g >> SHIFT) + bound.h(ci, l) != open.lastF())
			{
				return false; // an older, worse entry
			}
			return true;
		}

		/**
		 * Many targets: notes a target tile reached (its ticks are final, as the bound is zero) and says whether the
		 * search stops ({@link #STOP}), skips going on from this tile ({@link #SKIP}) or goes on ({@link #GO_ON}).
		 */
		private int checkTargets(int ci, int l, int g)
		{
			if (targets.done(open.lastF()))
			{
				return STOP; // nothing left within the limit can help (a zero bound: f is the ticks)
			}
			long[] tb = targets.bits[ci];
			if (tb != null && (tb[l >> 6] & (1L << (l & 63))) != 0)
			{
				int i = targets.indexOf.get(Tiles.fromCell(keys[ci], l));
				if (targets.ticks[i] < 0)
				{
					targets.reached(i, g >> SHIFT); // final (zero bound); the search goes on to the others
					if (targets.found == targets.ticks.length)
					{
						return STOP;
					}
				}
			}
			if (targets.on != null && (long) (g >> SHIFT) + targets.onAdd + targets.on.coarse(ci, l) >= targets.best)
			{
				return SKIP; // nothing on from here can lead to a target that helps
			}
			return GO_ON;
		}

		/**
		 * The teleports waiting for a shallow enough tile, offered from this one when it is shallow enough
		 * for them and reached in fewer ticks than any tile they were offered from before: each landing as a transport used
		 * from this tile (the route walks or rides here, then teleports).
		 */
		private void offerLimited(int ci, int l, int g)
		{
			List<Integer>[] waiting = this.waiting;
			int z = zoneOf(limitsQ, ci, l);
			int hereTile = Tiles.fromCell(keys[ci], l);
			for (int w = z; w < waiting.length; w++)
			{
				if (waiting[w] == null || g >= offeredAt[w])
				{
					continue;
				}
				offeredAt[w] = g;
				for (int i : waiting[w])
				{
					Transport tr = transports.get(i);
					int nci = cellIndex(tr.getEndX(), tr.getEndY(), tr.getEndPlane());
					if (nci < 0)
					{
						continue;
					}
					int nl = (tr.getEndX() & 63) << 6 | (tr.getEndY() & 63);
					int tg = g + (tr.cost(penalty) << SHIFT);
					int[] ga = g(nci);
					if (tg < ga[nl])
					{
						int h = bound.coarse(nci, nl);
						if (h >= NEVER)
						{
							continue;
						}
						ga[nl] = tg;
						prevs[nci][nl] = BY_TRANSPORT;
						int landing = Tiles.fromCell(keys[nci], nl);
						via.put(landing, i * 2);
						usedAt.put(landing, hereTile);
						lazy.add(nci << 12 | nl);
						open.push((tg >> SHIFT) + h, tg, nci, nl);
					}
				}
			}
		}

		/** Queues the tiles one step from this one (or, for a solid tile landed on, the steps off it). */
		private void expandSteps(int ci, int l, int g)
		{
			Bound bound = this.bound;
			BucketQueue open = this.open;
			Set<Integer> lazy = this.lazy;
			int m = dirs(ci)[l] & 0xFF;
			boolean steppingOff = false;
			if (m == 0 && !stepOff.isEmpty())
			{
				// a solid tile a transport used from beside landed on: the player steps off it
				Integer off = stepOff.get(Tiles.fromCell(keys[ci], l));
				if (off != null)
				{
					m = off;
					steppingOff = true;
				}
			}
			int doorHere = doorStep == 0 ? 0 : doorBits(ci, doorsQ)[l] & 0xFF;
			int lx = l >> 6;
			int ly = l & 63;
			// cost = ticks << SHIFT plus the number of diagonal steps: among equally short routes the one
			// with the fewest diagonal steps wins (straighter, and stable when the start moves a little)
			int straight = g + (1 << SHIFT);
			int diagonal = (g & DIAG_MASK) < DIAG_MASK ? straight + 1 : straight;
			for (int d = 0; d < 8; d++)
			{
				if ((m & (1 << d)) == 0)
				{
					continue;
				}
				int ng = (d & 1) == 1 ? diagonal : straight; // odd directions are the diagonals
				if ((doorHere & (1 << d)) != 0)
				{
					ng += doorStep; // through a door: a little extra
				}
				int nlx = lx + DX[d];
				int nly = ly + DY[d];
				int nci;
				int nl;
				if (nlx >= 0 && nly >= 0 && nlx < 64 && nly < 64)
				{
					nci = ci;
					nl = nlx << 6 | nly;
				}
				else
				{
					int key = keys[ci];
					int x = ((key >> 10) << 6) + nlx;
					int y = (((key >> 2) & 0xFF) << 6) + nly;
					nci = cellIndex(x, y, key & 3);
					if (nci < 0)
					{
						continue;
					}
					nl = (x & 63) << 6 | (y & 63);
				}
				int[] ga = g(nci);
				if (ng < ga[nl])
				{
					if (steppingOff)
					{
						// off a ring's middle: queued with the cheap bound, as teleport landings (most never come out)
						int h = bound.coarse(nci, nl);
						if (h >= NEVER)
						{
							continue;
						}
						ga[nl] = ng;
						prevs[nci][nl] = (byte) d;
						lazy.add(nci << 12 | nl);
						open.push((ng >> SHIFT) + h, ng, nci, nl);
						continue;
					}
					int h = bound.h(nci, nl);
					if (h >= NEVER)
					{
						continue; // no way on from there
					}
					ga[nl] = ng;
					prevs[nci][nl] = (byte) d;
					if (!lazy.isEmpty())
					{
						lazy.remove(nci << 12 | nl);
					}
					open.push((ng >> SHIFT) + h, ng, nci, nl);
				}
			}
		}

		/** Queues the landings of the transports used from this tile. */
		private void expandTransports(int ci, int l, int g)
		{
			long[] here = hasFrom[ci];
			if (here == null || (here[l >> 6] & (1L << (l & 63))) == 0)
			{
				return;
			}
			int hereTile = Tiles.fromCell(keys[ci], l);
			for (int[] u : from.get(hereTile))
			{
				Transport tr = transports.get(u[0]);
				int ex = u[1] == 0 ? tr.getEndX() : tr.getStartX();
				int ey = u[1] == 0 ? tr.getEndY() : tr.getStartY();
				int ep = u[1] == 0 ? tr.getEndPlane() : tr.getStartPlane();
				int nci = cellIndex(ex, ey, ep);
				int tg = g + (tr.cost(penalty) << SHIFT);
				if (nci < 0)
				{
					if (Tiles.pack(ex, ey, ep) == destTile && tg < offDest[0])
					{
						offDest[0] = tg;
						offDest[1] = u[0] * 2 + u[1];
						offDest[2] = hereTile;
						offDest[3] = g;
					}
					continue;
				}
				int nl = (ex & 63) << 6 | (ey & 63);
				int[] ga = g(nci);
				if (tg < ga[nl])
				{
					int h = bound.h(nci, nl);
					if (h >= NEVER)
					{
						continue;
					}
					ga[nl] = tg;
					prevs[nci][nl] = BY_TRANSPORT;
					if (!lazy.isEmpty())
					{
						lazy.remove(nci << 12 | nl);
					}
					int landing = Tiles.pack(ex, ey, ep);
					via.put(landing, u[0] * 2 + u[1]);
					if (hereTile != (u[1] == 0 ? Tiles.pack(tr.getStartX(), tr.getStartY(), tr.getStartPlane())
						: Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane())))
					{
						usedAt.put(landing, hereTile); // used from beside its end
					}
					else if (!usedAt.isEmpty())
					{
						usedAt.remove(landing);
					}
					open.push((tg >> SHIFT) + h, tg, nci, nl);
				}
			}
		}

		/**
		 * The queue is done without reaching the destination's tile: the ways to the targets that can help, the route
		 * that ends with a transport into a destination off the walking map, or no route.
		 */
		private Route finish()
		{
			if (targets != null && targets.routes != null)
			{
				// the way to each target that can help, and to the nearest ones (final: a zero bound settles each tile once)
				for (Map.Entry<Integer, Integer> e : targets.indexOf.entrySet())
				{
					int i = e.getValue();
					int t = targets.ticks[i];
					if (t >= 0 && (targets.helps(i, t) || t == targets.firstTicks))
					{
						targets.routes[i] = build(e.getKey(), t, via, usedAt, transports, penalty, expanded, 0, q.getDoors(), q.getDoorCost());
					}
				}
			}
			if (offDest[1] >= 0)
			{
				// the route to the transport's start, then the transport into the destination
				Transport tr = transports.get(offDest[1] / 2);
				boolean back = (offDest[1] & 1) == 1;
				int usedFrom = offDest[2];
				List<Route.Step> steps = new ArrayList<>();
				if (!(sci < 0 && usedFrom == startTile))
				{
					steps.addAll(build(usedFrom, offDest[3] >> SHIFT, via, usedAt, transports, penalty, expanded, 0, q.getDoors(), q.getDoorCost()).getSteps());
				}
				int useTile = back ? Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane()) : Tiles.pack(tr.getStartX(), tr.getStartY(), tr.getStartPlane());
				if (tr.isUsedFromBeside())
				{
					useTile = usedFrom; // perhaps beside its end
				}
				steps.add(new Route.Step(Route.Step.Kind.TRANSPORT, new int[]{useTile, destTile}, tr, tr.cost(penalty)));
				return new Route(true, offDest[0] >> SHIFT, steps, expanded, System.nanoTime() - t0);
			}
			return Route.notFound(expanded, System.nanoTime() - t0);
		}
	}

	/**
	 * The last two transport indexes: one route searches several times with the same transports, so the index is kept
	 * instead of made again for each search.
	 */
	private TransportIndex transportIndex;
	private TransportIndex indexBefore;

	/** The index for these transports (and these ends used from beside), made again only when they differ. */
	private TransportIndex index(List<Transport> transports, Beside beside)
	{
		if (transportIndex != null && transportIndex.isFor(transports, beside))
		{
			return transportIndex;
		}
		TransportIndex ix = indexBefore != null && indexBefore.isFor(transports, beside) ? indexBefore
			: new TransportIndex(this, keys.length, transports, beside);
		indexBefore = transportIndex;
		transportIndex = ix;
		return ix;
	}

	/** Walks back from the destination; groups the tiles into walks and transports. */
	private Route build(int dest, int total, Map<Integer, Integer> via, Map<Integer, Integer> usedAt, List<Transport> transports,
		int penalty, int expanded, long nanos, DoorMap doorsQ, int doorCostQ)
	{
		List<Route.Step> reversed = new ArrayList<>();
		List<Integer> walk = new ArrayList<>();
		int t = dest;
		while (true)
		{
			walk.add(t);
			int ci = cellIndex(Tiles.x(t), Tiles.y(t), Tiles.plane(t));
			byte how = prevs[ci][Tiles.local(t)];
			if (how == START)
			{
				break;
			}
			if (how == BY_TRANSPORT)
			{
				flushWalk(walk, reversed, doorsQ, doorCostQ);
				int v = via.get(t);
				Transport tr = transports.get(v / 2);
				boolean back = (v & 1) == 1;
				int[] tiles;
				int startTile;
				if (tr.hasStart())
				{
					startTile = back ? Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane())
						: Tiles.pack(tr.getStartX(), tr.getStartY(), tr.getStartPlane());
					if (!usedAt.isEmpty())
					{
						startTile = usedAt.getOrDefault(t, startTile); // used from beside its end
					}
					tiles = new int[]{startTile, t};
				}
				else
				{
					// a teleport: from the start, or (start too deep in the Wilderness) from a tile shallow enough
					Integer from = usedAt.isEmpty() ? null : usedAt.get(t);
					startTile = from == null ? 0 : from;
					tiles = from == null ? new int[]{t} : new int[]{from, t};
				}
				reversed.add(new Route.Step(tr.hasStart() ? Route.Step.Kind.TRANSPORT : Route.Step.Kind.TELEPORT,
					tiles, tr, tr.cost(penalty)));
				if (!tr.hasStart() && tiles.length == 1
					|| cellIndex(Tiles.x(startTile), Tiles.y(startTile), Tiles.plane(startTile)) < 0)
				{
					break; // a teleport from the start, or a transport from the player's tile off the walking map
				}
				t = startTile;
				continue;
			}
			t = Tiles.pack(Tiles.x(t) - DX[how], Tiles.y(t) - DY[how], Tiles.plane(t));
		}
		flushWalk(walk, reversed, doorsQ, doorCostQ);
		List<Route.Step> steps = new ArrayList<>();
		for (int i = reversed.size() - 1; i >= 0; i--)
		{
			steps.add(reversed.get(i));
		}
		return new Route(true, total, steps, expanded, nanos);
	}

	private static void flushWalk(List<Integer> walk, List<Route.Step> reversed, DoorMap doorsQ, int doorCostQ)
	{
		if (walk.size() > 1)
		{
			int[] tiles = new int[walk.size()];
			for (int i = 0; i < tiles.length; i++)
			{
				tiles[i] = walk.get(tiles.length - 1 - i);
			}
			reversed.add(new Route.Step(Route.Step.Kind.WALK, tiles, null, WalkCost.cost(tiles, doorsQ, doorCostQ)));
		}
		walk.clear();
	}
}

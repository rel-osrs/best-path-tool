package com.bestpath.core.path;

import com.bestpath.core.collision.CollisionMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The tile-level search: walking one tile per tick in 8 directions (no
 * corner cutting, from {@link CollisionMap}), plus transports. It starts from
 * a virtual "player" node that leads to the player's tile (0 ticks) and to
 * every teleport's landing tile (its ticks), and ends at the destination.
 *
 * <p>With a zero heuristic this is plain Dijkstra: the brute-force checker.
 * With an admissible heuristic (never more than the true remaining ticks)
 * it is A*; tiles are re-opened when a cheaper way to them is found, so
 * the heuristic does not have to be consistent and the result stays exact.
 */
final class TileSearch
{
	/** Lower bound of the ticks left from a tile to the destination. */
	interface Heuristic
	{
		int h(int tile);
	}

	static final Heuristic ZERO = t -> 0;

	private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
	private static final int[] DY = {1, 1, 0, -1, -1, -1, 0, 1};
	private static final byte ARRIVED_BY_TRANSPORT = 8;
	private static final byte START = 9;

	private TileSearch()
	{
	}

	static Route search(CollisionMap map, PathQuery q, Heuristic heur)
	{
		return search(map, q, heur, null);
	}

	/** As above; {@code beside} (null: none) for transports used from beside their ends ({@link Beside}). */
	static Route search(CollisionMap map, PathQuery q, Heuristic heur, Beside beside)
	{
		long t0 = System.nanoTime();
		List<Transport> transports = q.getTransports();
		int penalty = q.getPlaneChangePenalty();
		int doorCost = q.getDoorCost();
		// transports by the tile they are used from: {index, reversed (0/1)}
		Map<Integer, List<int[]>> from = new HashMap<>();
		// solid landing tiles of transports used from beside their ends, with the directions to step off them
		Map<Integer, Integer> stepOff = new HashMap<>();
		for (int i = 0; i < transports.size(); i++)
		{
			Transport tr = transports.get(i);
			if (!tr.hasStart())
			{
				continue;
			}
			int st = Tiles.pack(tr.getStartX(), tr.getStartY(), tr.getStartPlane());
			int en = Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane());
			boolean b = beside != null && tr.isUsedFromBeside();
			from.computeIfAbsent(st, k -> new ArrayList<>()).add(new int[]{i, 0});
			if (b)
			{
				besideUses(from, stepOff, beside, st, en, new int[]{i, 0});
			}
			if (tr.isTwoWay())
			{
				from.computeIfAbsent(en, k -> new ArrayList<>()).add(new int[]{i, 1});
				if (b)
				{
					besideUses(from, stepOff, beside, en, st, new int[]{i, 1});
				}
			}
		}
		Cells cells = new Cells();
		Map<Integer, Integer> via = new HashMap<>();
		// landing tile -> the tile a transport was used from, when beside its end
		Map<Integer, Integer> usedAt = new HashMap<>();
		LongHeap open = new LongHeap();
		int dest = Tiles.pack(q.getDestX(), q.getDestY(), q.getDestPlane());

		int start = Tiles.pack(q.getStartX(), q.getStartY(), q.getStartPlane());
		cells.set(start, 0, START);
		open.push(key(heur.h(start), start));
		for (int i = 0; i < transports.size(); i++)
		{
			Transport tr = transports.get(i);
			if (tr.hasStart())
			{
				continue;
			}
			int land = Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane());
			int g = tr.cost(penalty);
			if (g < cells.g(land))
			{
				cells.set(land, g, ARRIVED_BY_TRANSPORT);
				via.put(land, i * 2);
				open.push(key(g + heur.h(land), land));
			}
		}

		int expanded = 0;
		while (!open.isEmpty())
		{
			long k = open.pop();
			int f = (int) (k >>> 32);
			int t = (int) k;
			int g = cells.g(t);
			if (g + heur.h(t) != f)
			{
				continue; // an older, worse entry for this tile
			}
			expanded++;
			if (t == dest)
			{
				return build(t, g, cells, via, usedAt, transports, penalty, expanded, System.nanoTime() - t0, q.getDoors(), q.getDoorCost());
			}
			int x = Tiles.x(t);
			int y = Tiles.y(t);
			int p = Tiles.plane(t);
			int off = stepOff.isEmpty() ? 0 : stepOff.getOrDefault(t, 0);
			for (int d = 0; d < 8; d++)
			{
				if (!map.canStep(x, y, p, DX[d], DY[d]) && (off & 1 << d) == 0)
				{
					continue;
				}
				int n = Tiles.pack(x + DX[d], y + DY[d], p);
				int ng = g + 1 + (doorCost > 0 && (q.getDoors().bits(x, y, p) & (1 << d)) != 0 ? doorCost : 0);
				if (ng < cells.g(n))
				{
					cells.set(n, ng, (byte) d);
					open.push(key(ng + heur.h(n), n));
				}
			}
			List<int[]> uses = from.get(t);
			if (uses != null)
			{
				for (int[] u : uses)
				{
					Transport tr = transports.get(u[0]);
					int n = u[1] == 0 ? Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane())
						: Tiles.pack(tr.getStartX(), tr.getStartY(), tr.getStartPlane());
					int ng = g + tr.cost(penalty);
					if (ng < cells.g(n))
					{
						cells.set(n, ng, ARRIVED_BY_TRANSPORT);
						via.put(n, u[0] * 2 + u[1]);
						int nominal = u[1] == 0 ? Tiles.pack(tr.getStartX(), tr.getStartY(), tr.getStartPlane())
							: Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane());
						if (t != nominal)
						{
							usedAt.put(n, t);
						}
						else
						{
							usedAt.remove(n);
						}
						open.push(key(ng + heur.h(n), n));
					}
				}
			}
		}
		return Route.notFound(expanded, System.nanoTime() - t0);
	}

	/** A transport used from beside its end {@code used}: from the tiles beside it; a solid {@code other} is stepped off. */
	private static void besideUses(Map<Integer, List<int[]>> from, Map<Integer, Integer> stepOff, Beside beside, int used,
		int other, int[] use)
	{
		for (int n : beside.boardTiles(used))
		{
			from.computeIfAbsent(n, k -> new ArrayList<>()).add(use);
		}
		int off = beside.leaveMask(other);
		if (off != 0)
		{
			stepOff.put(other, off);
		}
	}

	private static long key(int f, int tile)
	{
		return (long) f << 32 | (tile & 0xFFFFFFFFL);
	}

	/** Walks back from the destination and groups the tiles into walks and transports. */
	private static Route build(int dest, int total, Cells cells, Map<Integer, Integer> via, Map<Integer, Integer> usedAt,
		List<Transport> transports,
		int penalty, int expanded, long nanos, DoorMap doorsQ, int doorCostQ)
	{
		List<Route.Step> reversed = new ArrayList<>();
		List<Integer> walk = new ArrayList<>();
		int t = dest;
		while (true)
		{
			walk.add(t);
			byte how = cells.prev(t);
			if (how == START)
			{
				break;
			}
			if (how == ARRIVED_BY_TRANSPORT)
			{
				flushWalk(walk, reversed, doorsQ, doorCostQ);
				int v = via.get(t);
				Transport tr = transports.get(v / 2);
				boolean back = (v & 1) == 1;
				int[] tiles;
				int startTile = 0;
				if (tr.hasStart())
				{
					startTile = back ? Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane())
						: Tiles.pack(tr.getStartX(), tr.getStartY(), tr.getStartPlane());
					startTile = usedAt.getOrDefault(t, startTile); // used from beside its end
					tiles = new int[]{startTile, t};
				}
				else
				{
					tiles = new int[]{t};
				}
				reversed.add(new Route.Step(tr.hasStart() ? Route.Step.Kind.TRANSPORT : Route.Step.Kind.TELEPORT,
					tiles, tr, tr.cost(penalty)));
				if (!tr.hasStart())
				{
					break; // a teleport is where the route begins
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

	/** {@code walk} holds tiles from the end backwards; a walk of one tile is no walk. */
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

	/** Best ticks and how each tile was reached, stored per cell (allocated when first touched). */
	private static final class Cells
	{
		private final Map<Integer, int[]> g = new HashMap<>();
		private final Map<Integer, byte[]> prev = new HashMap<>();
		private int lastCell = -1;
		private int[] lastG;
		private byte[] lastPrev;

		private void load(int cell, boolean create)
		{
			if (cell == lastCell)
			{
				return;
			}
			int[] gs = g.get(cell);
			if (gs == null)
			{
				if (!create)
				{
					lastCell = -1;
					lastG = null;
					lastPrev = null;
					return;
				}
				gs = new int[4096];
				Arrays.fill(gs, Integer.MAX_VALUE);
				g.put(cell, gs);
				prev.put(cell, new byte[4096]);
			}
			lastCell = cell;
			lastG = gs;
			lastPrev = prev.get(cell);
		}

		int g(int tile)
		{
			load(Tiles.cell(tile), false);
			return lastG == null ? Integer.MAX_VALUE : lastG[Tiles.local(tile)];
		}

		byte prev(int tile)
		{
			load(Tiles.cell(tile), false);
			return lastPrev[Tiles.local(tile)];
		}

		void set(int tile, int gv, byte how)
		{
			load(Tiles.cell(tile), true);
			lastG[Tiles.local(tile)] = gv;
			lastPrev[Tiles.local(tile)] = how;
		}
	}
}

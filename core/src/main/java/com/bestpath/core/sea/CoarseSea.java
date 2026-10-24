package com.bestpath.core.sea;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Rough sailing distances that never cut across land, for ordering many stops without a route search (sea charting).
 * The main ocean is cut into square cells ({@link #CELL} tiles); inside each cell every patch of water joined by boat
 * steps is one node, so water on two sides of a strip of land in one cell stays apart. Nodes of next-door cells are
 * joined where a boat can step from one to the other, each join as long as the straight line between the two patches'
 * middles. A distance is the shortest way through these joins, never less than the straight line. Immutable.
 *
 * <p>Memory (ocean pathing is rarely used): not kept. It is built for one plan on the route worker and dropped with it: about 0.4 MB while it lives (11,438
 * nodes at 16 tiles a cell), a third of a second to build. Building keeps only two columns of cells' tiles at a time.
 *
 * <p>Cell size (trial over the six oceans' charting tasks, scored on true sailing distances): 16 tiles
 * gave orders closer to the best known than 32 tiles or straight lines, and the fewest legs where land is in the way.
 */
public final class CoarseSea
{
	/** Cell size in tiles (a power of two). */
	public static final int CELL = 16;
	/** A point further than this from the main ocean has no node: its distances are straight lines. */
	public static final int MAX_SNAP = 40;
	/** The opposite of each direction of {@link SeaGrid#DX}. */
	private static final int[] BACK = {1, 0, 3, 2, 7, 6, 5, 4};

	private final SeaGrid grid;
	/** This map's cell size and its power of two. */
	private final int size;
	private final int shift;
	/** The cells with water (key cx << 16 | cy), ascending; cell i's nodes are cellFirst[i] .. cellFirst[i + 1] - 1. */
	private final int[] cellKeys;
	private final int[] cellFirst;
	/** Per node: the cell's local index (lx * size + ly) of its first tile in scan order, and its middle. */
	private final short[] anchor;
	private final float[] midX;
	private final float[] midY;
	/** Joins, as a list per node: start index into {@link #to} / {@link #len}. */
	private final int[] edgeStart;
	private final int[] to;
	private final float[] len;

	private CoarseSea(SeaGrid grid, int size, int[] cellKeys, int[] cellFirst, short[] anchor, float[] midX, float[] midY,
		int[] edgeStart, int[] to, float[] len)
	{
		this.grid = grid;
		this.size = size;
		this.shift = Integer.numberOfTrailingZeros(size);
		this.cellKeys = cellKeys;
		this.cellFirst = cellFirst;
		this.anchor = anchor;
		this.midX = midX;
		this.midY = midY;
		this.edgeStart = edgeStart;
		this.to = to;
		this.len = len;
	}

	/** Builds the cells' patches and their joins from the main ocean ({@link #CELL} tiles a cell). */
	public static CoarseSea build(SeaGrid grid)
	{
		return build(grid, CELL);
	}

	/** As {@link #build(SeaGrid)} with another cell size (a power of two, 4 to 64). */
	public static CoarseSea build(SeaGrid grid, int cellSize)
	{
		if (Integer.bitCount(cellSize) != 1 || cellSize < 4 || cellSize > 64)
		{
			throw new IllegalArgumentException("cell size must be a power of two from 4 to 64");
		}
		final int sh = Integer.numberOfTrailingZeros(cellSize);
		final int dim = 1 << (14 - sh); // cells across the map (tiles are below 1 << 14)
		final int area = cellSize * cellSize;
		BitSet present = new BitSet(dim * dim);
		grid.forEachTile((x, y) -> present.set((x >> sh) * dim + (y >> sh)));
		int cells = present.cardinality();
		int[] cellKeys = new int[cells];
		int[] cellFirst = new int[cells + 1];

		short[] anchor = new short[4096];
		float[] midX = new float[4096];
		float[] midY = new float[4096];
		long[] edges = new long[16384]; // a join: lower node << 32 | higher node
		int edgeCount = 0;
		int nodes = 0;
		// each cell's tiles labelled with their node + 1 (0: no water), for this column of cells and the one before
		Map<Integer, int[]> column = new HashMap<>();
		Map<Integer, int[]> before = new HashMap<>();
		int columnX = -2;
		int[] stack = new int[area];
		int ci = 0;
		for (int idx = present.nextSetBit(0); idx >= 0; idx = present.nextSetBit(idx + 1))
		{
			int cx = idx / dim;
			int cy = idx % dim;
			if (cx != columnX)
			{
				before = cx == columnX + 1 ? column : new HashMap<>();
				column = new HashMap<>();
				columnX = cx;
			}
			cellKeys[ci] = cellKey(cx, cy);
			cellFirst[ci] = nodes;
			ci++;
			int x0 = cx << sh;
			int y0 = cy << sh;
			int[] lab = new int[area];
			for (int l = 0; l < area; l++)
			{
				if (lab[l] != 0 || !grid.isOcean(x0 + l / cellSize, y0 + l % cellSize))
				{
					continue;
				}
				// a new patch: every tile a boat reaches from here without leaving the cell
				if (nodes == anchor.length)
				{
					anchor = Arrays.copyOf(anchor, nodes * 2);
					midX = Arrays.copyOf(midX, nodes * 2);
					midY = Arrays.copyOf(midY, nodes * 2);
				}
				double sx = 0;
				double sy = 0;
				int tiles = 0;
				int top = 0;
				stack[top++] = l;
				lab[l] = nodes + 1;
				while (top > 0)
				{
					int c = stack[--top];
					int x = x0 + c / cellSize;
					int y = y0 + c % cellSize;
					sx += x;
					sy += y;
					tiles++;
					for (int d = 0; d < 8; d++)
					{
						int nx = x + SeaGrid.DX[d];
						int ny = y + SeaGrid.DY[d];
						if (nx >> sh != cx || ny >> sh != cy || !grid.canMove(x, y, d))
						{
							continue;
						}
						int nl = (nx - x0) * cellSize + (ny - y0);
						if (lab[nl] == 0)
						{
							lab[nl] = nodes + 1;
							stack[top++] = nl;
						}
					}
				}
				anchor[nodes] = (short) l;
				midX[nodes] = (float) (sx / tiles);
				midY[nodes] = (float) (sy / tiles);
				nodes++;
			}
			column.put(cy, lab);

			// joins with the cells done before this one: the column to the west and the cell to the south (the cells
			// to the east and north join this one when their turn comes)
			int cellEdges = edgeCount;
			for (int l = 0; l < area; l++)
			{
				int lx = l / cellSize;
				int ly = l % cellSize;
				if (lab[l] == 0 || lx != 0 && ly != 0)
				{
					continue; // only the west column and the bottom row step into those cells
				}
				int x = x0 + lx;
				int y = y0 + ly;
				for (int d = 0; d < 8; d++)
				{
					int nx = x + SeaGrid.DX[d];
					int ny = y + SeaGrid.DY[d];
					int ncx = nx >> sh;
					int ncy = ny >> sh;
					int[] other = ncx == cx - 1 ? before.get(ncy) : ncx == cx && ncy == cy - 1 ? column.get(ncy) : null;
					if (other == null)
					{
						continue;
					}
					int b = other[(nx - (ncx << sh)) * cellSize + (ny - (ncy << sh))] - 1;
					if (b < 0 || !grid.canMove(x, y, d) && !grid.canMove(nx, ny, BACK[d]))
					{
						continue;
					}
					int a = lab[l] - 1;
					long e = (long) Math.min(a, b) << 32 | Math.max(a, b);
					// each join once: a cell has only a few, so they are only looked for among this cell's
					boolean seen = false;
					for (int k = cellEdges; k < edgeCount && !seen; k++)
					{
						seen = edges[k] == e;
					}
					if (seen)
					{
						continue;
					}
					if (edgeCount == edges.length)
					{
						edges = Arrays.copyOf(edges, edgeCount * 2);
					}
					edges[edgeCount++] = e;
				}
			}
		}
		cellFirst[cells] = nodes;

		// each join once, then as a list per node both ways
		Arrays.sort(edges, 0, edgeCount);
		int unique = 0;
		for (int i = 0; i < edgeCount; i++)
		{
			if (i == 0 || edges[i] != edges[i - 1])
			{
				edges[unique++] = edges[i];
			}
		}
		int[] edgeStart = new int[nodes + 1];
		for (int i = 0; i < unique; i++)
		{
			edgeStart[(int) (edges[i] >>> 32) + 1]++;
			edgeStart[(int) edges[i] + 1]++;
		}
		for (int i = 0; i < nodes; i++)
		{
			edgeStart[i + 1] += edgeStart[i];
		}
		int[] fill = Arrays.copyOf(edgeStart, nodes);
		int[] to = new int[unique * 2];
		float[] len = new float[unique * 2];
		for (int i = 0; i < unique; i++)
		{
			int a = (int) (edges[i] >>> 32);
			int b = (int) edges[i];
			float l = (float) Math.hypot(midX[a] - midX[b], midY[a] - midY[b]);
			to[fill[a]] = b;
			len[fill[a]++] = l;
			to[fill[b]] = a;
			len[fill[b]++] = l;
		}
		return new CoarseSea(grid, cellSize, cellKeys, cellFirst, Arrays.copyOf(anchor, nodes), Arrays.copyOf(midX, nodes),
			Arrays.copyOf(midY, nodes), edgeStart, to, len);
	}

	private static int cellKey(int cx, int cy)
	{
		return cx << 16 | cy;
	}

	/**
	 * The node of the main-ocean water nearest a point (within {@link #MAX_SNAP} tiles), or -1 for none. Plane is not
	 * looked at: the main ocean is on plane 0, so a point elsewhere should be asked for as its nearest water.
	 */
	public int nodeNear(int x, int y)
	{
		int[] w = nearestWater(x, y);
		return w == null ? -1 : nodeOf(w[0], w[1]);
	}

	/** The nearest main-ocean tile within {@link #MAX_SNAP} (closest by straight line in the first ring with any), or null. */
	public int[] nearestWater(int x, int y)
	{
		for (int r = 0; r <= MAX_SNAP; r++)
		{
			int[] best = null;
			double bd = Double.MAX_VALUE;
			for (int dx = -r; dx <= r; dx++)
			{
				for (int dy = -r; dy <= r; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) == r && grid.isOcean(x + dx, y + dy))
					{
						double dd = Math.hypot(dx, dy);
						if (dd < bd)
						{
							bd = dd;
							best = new int[]{x + dx, y + dy};
						}
					}
				}
			}
			if (best != null)
			{
				return best;
			}
		}
		return null;
	}

	/** The node of a main-ocean tile, or -1 when it isn't main ocean. */
	public int nodeOf(int x, int y)
	{
		if (!grid.isOcean(x, y))
		{
			return -1;
		}
		int cx = x >> shift;
		int cy = y >> shift;
		int cell = Arrays.binarySearch(cellKeys, cellKey(cx, cy));
		if (cell < 0)
		{
			return -1;
		}
		// the tile's patch: its lowest local index is the patch's anchor
		int x0 = cx << shift;
		int y0 = cy << shift;
		boolean[] seen = new boolean[size * size];
		int[] stack = new int[size * size];
		int top = 0;
		int start = (x - x0) * size + (y - y0);
		stack[top++] = start;
		seen[start] = true;
		int low = start;
		while (top > 0)
		{
			int c = stack[--top];
			low = Math.min(low, c);
			int tx = x0 + c / size;
			int ty = y0 + c % size;
			for (int d = 0; d < 8; d++)
			{
				int nx = tx + SeaGrid.DX[d];
				int ny = ty + SeaGrid.DY[d];
				if (nx >> shift != cx || ny >> shift != cy || !grid.canMove(tx, ty, d))
				{
					continue;
				}
				int nl = (nx - x0) * size + (ny - y0);
				if (!seen[nl])
				{
					seen[nl] = true;
					stack[top++] = nl;
				}
			}
		}
		for (int n = cellFirst[cell]; n < cellFirst[cell + 1]; n++)
		{
			if (anchor[n] == low)
			{
				return n;
			}
		}
		return -1;
	}

	public double midX(int node)
	{
		return midX[node];
	}

	public double midY(int node)
	{
		return midY[node];
	}

	/**
	 * Shortest ways through the joins from one node to every node (tiles; infinite: not joined). The array is new each
	 * time (about 90 KB): read what is needed from it and let it go.
	 */
	public double[] distancesFrom(int node)
	{
		int nodes = anchor.length;
		double[] dist = new double[nodes];
		Arrays.fill(dist, Double.POSITIVE_INFINITY);
		// a binary heap of nodes by distance, with each node's place in it
		int[] heap = new int[nodes];
		int[] at = new int[nodes];
		Arrays.fill(at, -1);
		int size = 0;
		dist[node] = 0;
		heap[size] = node;
		at[node] = size++;
		while (size > 0)
		{
			int u = heap[0];
			at[u] = -2; // settled
			size--;
			if (size > 0)
			{
				heap[0] = heap[size];
				at[heap[0]] = 0;
				siftDown(heap, at, dist, size, 0);
			}
			for (int k = edgeStart[u]; k < edgeStart[u + 1]; k++)
			{
				int v = to[k];
				if (at[v] == -2)
				{
					continue;
				}
				double nd = dist[u] + len[k];
				if (nd < dist[v])
				{
					dist[v] = nd;
					if (at[v] < 0)
					{
						heap[size] = v;
						at[v] = size++;
					}
					siftUp(heap, at, dist, at[v]);
				}
			}
		}
		return dist;
	}

	private static void siftUp(int[] heap, int[] at, double[] dist, int i)
	{
		int v = heap[i];
		while (i > 0)
		{
			int parent = (i - 1) >> 1;
			if (dist[heap[parent]] <= dist[v])
			{
				break;
			}
			heap[i] = heap[parent];
			at[heap[i]] = i;
			i = parent;
		}
		heap[i] = v;
		at[v] = i;
	}

	private static void siftDown(int[] heap, int[] at, double[] dist, int size, int i)
	{
		int v = heap[i];
		while (true)
		{
			int c = 2 * i + 1;
			if (c >= size)
			{
				break;
			}
			if (c + 1 < size && dist[heap[c + 1]] < dist[heap[c]])
			{
				c++;
			}
			if (dist[heap[c]] >= dist[v])
			{
				break;
			}
			heap[i] = heap[c];
			at[heap[i]] = i;
			i = c;
		}
		heap[i] = v;
		at[v] = i;
	}
}

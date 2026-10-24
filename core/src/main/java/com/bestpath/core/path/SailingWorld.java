package com.bestpath.core.path;

import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaChannels;
import com.bestpath.core.sea.SeaGraph;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaRouter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The sea as sailing routes see it, shared by every route: the docks sailing can use (each with its joins into the
 * sea graph, the land tile the player stands on and the tile the player walks to for its shipwright), the sea graph
 * with its search, the graph's pieces (water a boat can sail between without a boat-only transport), and where a boat
 * can be. Also the ways a player can get onto each boat ({@link #boardings}).
 */
final class SailingWorld
{
	final SeaGrid grid;
	final CoarseGraph graph;
	final SeaGraph seaGraph;
	final SeaRouter router;
	final List<Dock> docks = new ArrayList<>();
	/** Per dock: its joins into the sea graph, and the land tile the player stands on. */
	final List<List<SeaGraph.Join>> dockJoins = new ArrayList<>();
	final List<int[]> land = new ArrayList<>();
	/** Per dock: the tile the player walks to for its shipwright ({@link #standTile}), or null (none, or not known). */
	final List<int[]> wrightStand = new ArrayList<>();
	/** Every dock given, also those sailing can't use (to tell whether a new world may keep this one's sea graph). */
	private final List<Dock> allDocks;
	/**
	 * Per sea-graph waypoint: its piece of the graph (waypoints joined by links, whatever the boat; null until first
	 * needed, {@link #piecesOf}). A boat never sails from one piece to another but through a boat-only transport.
	 */
	private int[] wpPiece;
	/** Per dock (as {@link #docks}): the graph pieces its joins reach, ascending (null until first needed). */
	private int[][] dockPieces;
	/** The joins of single tiles (boat-only transports' ends), kept between routes. */
	private final Map<Integer, List<SeaGraph.Join>> tileJoins = new HashMap<>();

	/**
	 * @param docks    all docks; those off the main ocean, without water the sea graph joins, or without a land tile on
	 *                 the walking map are left out
	 * @param channels the sea graph's channels ({@link SeaChannels}); null: found here
	 * @param graph    the walking map's components (to pick each dock's best land tile)
	 * @param previous its sea graph is kept when the sea and the buoys are the same (null: none)
	 */
	SailingWorld(SeaGrid grid, List<Dock> docks, List<int[]> channels, CoarseGraph graph, SailingWorld previous)
	{
		boolean same = previous != null && previous.grid.sameAs(grid) && sameBuoys(previous.allDocks, docks)
			&& (channels == null || sameChannels(previous.seaGraph.channels(), channels));
		this.grid = same ? previous.grid : grid;
		this.graph = graph;
		this.allDocks = List.copyOf(docks);
		this.seaGraph = same ? previous.seaGraph : withChannels(SeaGraph.build(this.grid, docks), channels);
		this.router = same ? previous.router : new SeaRouter(seaGraph);
		this.wpPiece = same ? previous.wpPiece : null;
		boolean[] pockets = null;
		for (Dock d : docks)
		{
			List<int[]> ring = new ArrayList<>();
			for (int x = d.getBuoyX() - 1; x <= d.getBuoyX() + d.getBuoySize(); x++)
			{
				for (int y = d.getBuoyY() - 1; y <= d.getBuoyY() + d.getBuoySize(); y++)
				{
					if (this.grid.isOcean(x, y))
					{
						ring.add(new int[]{x, y});
					}
				}
			}
			int[] best = null;
			int bestSize = -1;
			for (int[] t : d.getLandTiles())
			{
				int c = graph.componentAt(t[0], t[1], t[2]);
				if (c >= 0 && graph.componentSize(c) > bestSize)
				{
					best = t;
					bestSize = graph.componentSize(c);
				}
			}
			List<SeaGraph.Join> joins = ring.isEmpty() ? List.of() : seaGraph.join(ring.toArray(new int[0][]));
			if (joins.isEmpty() || best == null)
			{
				continue;
			}
			this.docks.add(d);
			dockJoins.add(joins);
			land.add(best.clone());
			if (pockets == null && d.getShipwrightTile() != null)
			{
				pockets = graph.pockets();
			}
			wrightStand.add(standTile(d, best, graph, pockets));
		}
	}

	/**
	 * Where the player walks to for a dock's shipwright, or null (no shipwright, or its place not known: the boat is
	 * then brought at the dock's land tile). The shipwright's place is its spawn tile; NPCs wander a little and the
	 * player talks to them from a tile next to them, so the target is the tile west, east, south or north of it that
	 * can be walked on (has a component on the walking map, not a closed-off pocket) and is nearest the dock's land
	 * tile {@code dockLand} (Chebyshev, then straight-line distance, then in that order); none of those: the spawn tile
	 * itself if it can be walked on; else null. Depends only on the walking map, so every caller picks the same tile.
	 */
	static int[] standTile(Dock d, int[] dockLand, CoarseGraph graph, boolean[] pockets)
	{
		int[] w = d.getShipwrightTile();
		if (w == null)
		{
			return null;
		}
		int[][] next = {{w[0] - 1, w[1]}, {w[0] + 1, w[1]}, {w[0], w[1] - 1}, {w[0], w[1] + 1}};
		int[] best = null;
		long bestKey = Long.MAX_VALUE;
		for (int[] n : next)
		{
			if (!standable(graph, pockets, n[0], n[1], w[2]))
			{
				continue;
			}
			long dx = n[0] - dockLand[0];
			long dy = n[1] - dockLand[1];
			long key = Math.max(Math.abs(dx), Math.abs(dy)) << 32 | (dx * dx + dy * dy);
			if (key < bestKey)
			{
				best = new int[]{n[0], n[1], w[2]};
				bestKey = key;
			}
		}
		if (best == null && standable(graph, pockets, w[0], w[1], w[2]))
		{
			best = w;
		}
		return best;
	}

	private static boolean standable(CoarseGraph graph, boolean[] pockets, int x, int y, int plane)
	{
		int c = graph.componentAt(x, y, plane);
		return c >= 0 && (pockets == null || c >= pockets.length || !pockets[c]);
	}

	/** The graph with its channels: the given ones, or (null) found now. */
	private static SeaGraph withChannels(SeaGraph g, List<int[]> channels)
	{
		return g.withChannels(channels != null ? channels : SeaChannels.find(g));
	}

	/** Whether two channel lists are equal, in order (if channels were removed, the graph is made again). */
	private static boolean sameChannels(List<int[]> a, List<int[]> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		for (int i = 0; i < a.size(); i++)
		{
			if (!Arrays.equals(a.get(i), b.get(i)))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean sameBuoys(List<Dock> a, List<Dock> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		for (int i = 0; i < a.size(); i++)
		{
			Dock x = a.get(i);
			Dock y = b.get(i);
			if (x.getId() != y.getId() || x.getBuoyX() != y.getBuoyX() || x.getBuoyY() != y.getBuoyY() || x.getBuoySize() != y.getBuoySize())
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether a route starting here starts on a boat: the tile is open sea (main ocean), or an unwalkable tile with
	 * main ocean within {@link Sailing#NEAR_TILES} (the boat entity's tile may lie on a rock or the shore beside the water).
	 * Not for destinations: a boat ends only at a dock or on open sea ({@link #isOpenSea}).
	 */
	boolean atSea(int x, int y, int plane)
	{
		return plane == 0 && (grid.isOcean(x, y) || graph.componentAt(x, y, 0) < 0 && nearOcean(x, y) != null);
	}

	/**
	 * Whether the tile is open sea (the main ocean): besides a dock, the only place a boat may end.
	 */
	boolean isOpenSea(int x, int y, int plane)
	{
		return plane == 0 && grid.isOcean(x, y);
	}

	/**
	 * For a blocked spot out in the open ocean (a rock with no land in sight), which should still get a route: the
	 * nearest main-ocean tile (straight distance, within {@link Sailing#NEAR_TILES} Chebyshev; ties: lower x, then lower y)
	 * that one of {@code boats} can be at, by the sea search's own data: the boat fits there (its hard clearance), its
	 * parts allow the tile's sea area, and the tile joins the sea graph. Null when none (no boats, or no such water
	 * near). {x, y}.
	 */
	int[] openWaterNear(int x, int y, int plane, List<Boat> boats)
	{
		if (plane != 0 || boats.isEmpty())
		{
			return null;
		}
		List<int[]> fits = new ArrayList<>();
		for (int dx = -Sailing.NEAR_TILES; dx <= Sailing.NEAR_TILES; dx++)
		{
			for (int dy = -Sailing.NEAR_TILES; dy <= Sailing.NEAR_TILES; dy++)
			{
				int tx = x + dx;
				int ty = y + dy;
				if (!grid.isOcean(tx, ty))
				{
					continue;
				}
				int clear = grid.clearanceAt(tx, ty);
				int area = grid.areaOrdinalAt(tx, ty);
				for (Boat b : boats)
				{
					if (clear >= b.getType().hardClearance() && (area < 0 || (b.areaMask() & 1 << area) != 0))
					{
						fits.add(new int[]{tx, ty, dx * dx + dy * dy});
						break;
					}
				}
			}
		}
		fits.sort((a, b) -> a[2] != b[2] ? Integer.compare(a[2], b[2]) : a[0] != b[0] ? Integer.compare(a[0], b[0])
			: Integer.compare(a[1], b[1]));
		for (int[] t : fits)
		{
			if (!seaGraph.join(new int[][]{{t[0], t[1]}}).isEmpty())
			{
				return new int[]{t[0], t[1]};
			}
		}
		return null;
	}

	/** The ocean tiles nearest the point (the point itself if it is ocean), within {@link Sailing#NEAR_TILES}; null if none. */
	private int[][] nearOcean(int x, int y)
	{
		if (grid.isOcean(x, y))
		{
			return new int[][]{{x, y}};
		}
		for (int r = 1; r <= Sailing.NEAR_TILES; r++)
		{
			List<int[]> ring = new ArrayList<>();
			for (int dx = -r; dx <= r; dx++)
			{
				for (int dy = -r; dy <= r; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) == r && grid.isOcean(x + dx, y + dy))
					{
						ring.add(new int[]{x + dx, y + dy});
					}
				}
			}
			if (!ring.isEmpty())
			{
				return ring.toArray(new int[0][]);
			}
		}
		return null;
	}

	/**
	 * The main-ocean tile nearest the point (within {@link Sailing#NEAR_TILES}) where the
	 * boat fits (its hard clearance), or null; the point itself when it fits. For a start at sea.
	 */
	int[] nearestFit(int x, int y, Boat b)
	{
		return fitWithin(x, y, b, Sailing.NEAR_TILES, Double.MAX_VALUE);
	}

	/**
	 * Where the boat stops for a destination on open sea (a boat never ends beside a rock or the coast): the tile
	 * itself when the boat fits there (its hard clearance), else the nearest tile where it fits and its hull still
	 * covers the destination (straight distance at most half the boat's width, whatever its heading: raft 0, skiff 1,
	 * sloop 1.5); null when there is none (the boat can't be there).
	 */
	int[] destinationFit(int x, int y, Boat b)
	{
		double half = b.getType().getWidth() / 2.0;
		return fitWithin(x, y, b, (int) Math.floor(half), half);
	}

	/** The tile nearest the point (Chebyshev rings up to {@code reach}, straight distance at most {@code within}) where the boat fits. */
	private int[] fitWithin(int x, int y, Boat b, int reach, double within)
	{
		int need = b.getType().hardClearance();
		int[] best = null;
		double bestD = Double.MAX_VALUE;
		for (int r = 0; r <= reach && best == null; r++)
		{
			for (int dx = -r; dx <= r; dx++)
			{
				for (int dy = -r; dy <= r; dy++)
				{
					double d = Math.hypot(dx, dy);
					if (Math.max(Math.abs(dx), Math.abs(dy)) == r && d <= within && grid.clearanceAt(x + dx, y + dy) >= need && d < bestD)
					{
						best = new int[]{x + dx, y + dy};
						bestD = d;
					}
				}
			}
		}
		return best;
	}

	/**
	 * The boat the player is on when the route starts at sea: the one marked at sea, else the first (lowest slot)
	 * that could be there (not capsized, lost or bottled); null if none.
	 */
	static Boat aboard(List<Boat> boats)
	{
		Boat first = null;
		for (Boat b : boats)
		{
			if (b.getDockId() == Boat.AT_START)
			{
				return b;
			}
			if (b.needsShipwright() || b.isBottled())
			{
				continue;
			}
			if (first == null || b.getSlot() < first.getSlot())
			{
				first = b;
			}
		}
		return first;
	}

	int dockIndexOf(int dockId)
	{
		for (int k = 0; k < docks.size(); k++)
		{
			if (docks.get(k).getId() == dockId)
			{
				return k;
			}
		}
		return -1;
	}

	/** Whether two ascending lists of graph pieces have one in common. */
	static boolean shares(int[] a, int[] b)
	{
		int i = 0;
		int j = 0;
		while (i < a.length && j < b.length)
		{
			if (a[i] == b[j])
			{
				return true;
			}
			if (a[i] < b[j])
			{
				i++;
			}
			else
			{
				j++;
			}
		}
		return false;
	}

	/**
	 * The graph pieces these joins reach, ascending. The pieces (waypoints joined by links, whatever the boat) are
	 * worked out once, on first use.
	 */
	int[] piecesOf(List<SeaGraph.Join> joins)
	{
		if (wpPiece == null)
		{
			wpPiece = graphPieces(seaGraph);
		}
		int[] out = new int[joins.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = wpPiece[joins.get(i).waypoint];
		}
		return Arrays.stream(out).sorted().distinct().toArray();
	}

	/** Per waypoint its piece of the graph: waypoints joined by links (in either direction) share one. */
	static int[] graphPieces(SeaGraph g)
	{
		int n = g.waypointCount();
		int[] parent = new int[n];
		for (int w = 0; w < n; w++)
		{
			parent[w] = w;
		}
		for (int w = 0; w < n; w++)
		{
			for (int d = 0; d < SeaGraph.HEADINGS; d++)
			{
				if (g.hasLink(w, d))
				{
					int a = root(parent, w);
					int b = root(parent, g.linkTarget(w, d));
					if (a != b)
					{
						parent[Math.max(a, b)] = Math.min(a, b);
					}
				}
			}
		}
		// and by channels: the crooked water the links miss
		for (int c = 0; c < g.channelCount(); c++)
		{
			int a = root(parent, g.channelFrom(c));
			int b = root(parent, g.channelTo(c));
			if (a != b)
			{
				parent[Math.max(a, b)] = Math.min(a, b);
			}
		}
		for (int w = 0; w < n; w++)
		{
			parent[w] = root(parent, w);
		}
		return parent;
	}

	private static int root(int[] parent, int w)
	{
		while (parent[w] != w)
		{
			parent[w] = parent[parent[w]];
			w = parent[w];
		}
		return w;
	}

	/** Per dock its graph pieces ({@link #piecesOf} of its joins). */
	int[] dockPieces(int k)
	{
		if (dockPieces == null)
		{
			dockPieces = new int[docks.size()][];
			for (int i = 0; i < dockPieces.length; i++)
			{
				dockPieces[i] = piecesOf(dockJoins.get(i));
			}
		}
		return dockPieces[k];
	}

	/**
	 * The boat-only transports of {@code boats} the sea search can use: both ends on plane 0, on the boats' water
	 * ({@link SeaGrid}) and joining the sea graph. A two-way one counts both ways.
	 */
	List<SeaLink> links(List<Transport> boats, int planeChangePenalty)
	{
		List<SeaLink> out = new ArrayList<>();
		for (Transport t : boats)
		{
			if (!t.isBoatOnly() || !t.hasStart() || t.getStartPlane() != 0 || t.getEndPlane() != 0)
			{
				continue;
			}
			addLink(out, t, t.getStartX(), t.getStartY(), t.getEndX(), t.getEndY(), planeChangePenalty);
			if (t.isTwoWay())
			{
				addLink(out, t, t.getEndX(), t.getEndY(), t.getStartX(), t.getStartY(), planeChangePenalty);
			}
		}
		return out;
	}

	private void addLink(List<SeaLink> out, Transport t, int sx, int sy, int ex, int ey, int penalty)
	{
		if (!grid.isOcean(sx, sy) || !grid.isOcean(ex, ey))
		{
			return;
		}
		List<SeaGraph.Join> in = joinsAt(sx, sy);
		List<SeaGraph.Join> out2 = joinsAt(ex, ey);
		if (!in.isEmpty() && !out2.isEmpty())
		{
			out.add(new SeaLink(t, sx, sy, ex, ey, t.cost(penalty) * 100, in, out2, piecesOf(in), piecesOf(out2)));
		}
	}

	private List<SeaGraph.Join> joinsAt(int x, int y)
	{
		return tileJoins.computeIfAbsent(x << 14 | y, k -> seaGraph.join(new int[][]{{x, y}}));
	}

	static List<SeaLink> linksFor(List<SeaLink> all, Boat b)
	{
		if (all.isEmpty())
		{
			return all;
		}
		List<SeaLink> out = new ArrayList<>(all.size());
		for (SeaLink l : all)
		{
			if (l.t.fits(b.getType()))
			{
				out.add(l);
			}
		}
		return out;
	}

	/**
	 * The least a sea leg from dock k to dock j costs ({@link SailingBounds#leastSail} between the water next to their buoys, the
	 * rings sea legs start and end on), {@link SailingBounds#FAR} when no boat can sail there.
	 */
	long dockSail(int k, int j, double perTile, List<SeaLink> ls)
	{
		return SailingBounds.leastSail(SailingBounds.buoyBox(docks.get(k)), dockPieces(k), SailingBounds.buoyBox(docks.get(j)), dockPieces(j),
			perTile, ls);
	}

	/** The boat the player is on at {@code q}'s start (a start at sea), or null. */
	Boat aboardAt(PathQuery q, Sailing.Options o)
	{
		return atSea(q.getStartX(), q.getStartY(), q.getStartPlane()) ? aboard(o.boats) : null;
	}

	/** {@code q} with a greater teleport focus's teleport to its boat's dock for each such boat (not the one aboard). */
	PathQuery withFocus(PathQuery q, Sailing.Options o, Boat aboard)
	{
		List<Transport> focus = focusTeleports(o, aboard);
		if (focus.isEmpty())
		{
			return q;
		}
		List<Transport> all = new ArrayList<>(q.getTransports());
		all.addAll(focus);
		return SailingSearch.with(q, all, q.getDestX(), q.getDestY(), q.getDestPlane());
	}

	/** A greater teleport focus's teleport to its boat's dock, for each such boat but {@code aboard} (null: none). */
	List<Transport> focusTeleports(Sailing.Options o, Boat aboard)
	{
		List<Transport> out = new ArrayList<>();
		for (Boat b : o.boats)
		{
			int home = b.getDockId() >= 0 && b != aboard ? dockIndexOf(b.getDockId()) : -1;
			if (b.getFocus() == Boat.Focus.GREATER && home >= 0)
			{
				int[] t = land.get(home);
				out.add(Transport.teleport("sail-teleport:" + b.getSlot(), "Greater teleport focus: to " + b.label() + " at "
					+ docks.get(home).getName(), t[0], t[1], t[2], Sailing.TELEPORT_TICKS, true));
			}
		}
		return out;
	}

	/**
	 * Where an "ocean only" route may end for this destination, in the order they are tried: a destination on open sea,
	 * the boat over it; else every dock the player may use whose land tile is within {@link Sailing#OCEAN_ONLY_RANGE} tiles,
	 * nearest first (the boat stops there, moored); else the open-sea tile nearest the destination within that range
	 * where the boat fits. No search. Empty when none.
	 */
	List<OceanEnd> oceanEnds(int x, int y, int plane, Boat b, Sailing.Options o)
	{
		List<OceanEnd> out = new ArrayList<>();
		if (isOpenSea(x, y, plane))
		{
			int[] t = destinationFit(x, y, b);
			if (t != null)
			{
				List<SeaGraph.Join> j = seaGraph.join(new int[][]{t});
				if (!j.isEmpty())
				{
					out.add(new OceanEnd(-1, j, piecesOf(j), "the destination"));
					return out;
				}
			}
		}
		List<Integer> near = new ArrayList<>();
		for (int k = 0; k < docks.size(); k++)
		{
			int[] t = land.get(k);
			if (usable(docks.get(k), o) && SailingBounds.cheb(t[0], t[1], x, y) <= Sailing.OCEAN_ONLY_RANGE)
			{
				near.add(k);
			}
		}
		near.sort((a, c) -> Integer.compare(SailingBounds.cheb(land.get(a)[0], land.get(a)[1], x, y),
			SailingBounds.cheb(land.get(c)[0], land.get(c)[1], x, y)));
		for (int k : near)
		{
			out.add(new OceanEnd(k, dockJoins.get(k), dockPieces(k), docks.get(k).getName()));
		}
		int[] sea = openSeaNear(x, y, b);
		if (sea != null)
		{
			List<SeaGraph.Join> j = seaGraph.join(new int[][]{sea});
			out.add(new OceanEnd(-1, j, piecesOf(j), "open sea near the destination"));
		}
		return out;
	}

	/**
	 * The main-ocean tile nearest the point (straight distance, within {@link Sailing#OCEAN_ONLY_RANGE} Chebyshev; ties: lower x,
	 * then lower y) where boat {@code b} fits (its hard clearance), its parts allow the tile's sea area and the sea graph
	 * joins it; null when none.
	 */
	private int[] openSeaNear(int x, int y, Boat b)
	{
		List<int[]> fits = new ArrayList<>();
		for (int dx = -Sailing.OCEAN_ONLY_RANGE; dx <= Sailing.OCEAN_ONLY_RANGE; dx++)
		{
			for (int dy = -Sailing.OCEAN_ONLY_RANGE; dy <= Sailing.OCEAN_ONLY_RANGE; dy++)
			{
				int tx = x + dx;
				int ty = y + dy;
				int area = grid.isOcean(tx, ty) ? grid.areaOrdinalAt(tx, ty) : 0;
				if (grid.isOcean(tx, ty) && grid.clearanceAt(tx, ty) >= b.getType().hardClearance()
					&& (area < 0 || (b.areaMask() & 1 << area) != 0))
				{
					fits.add(new int[]{tx, ty, dx * dx + dy * dy});
				}
			}
		}
		fits.sort((a, c) -> a[2] != c[2] ? Integer.compare(a[2], c[2]) : a[0] != c[0] ? Integer.compare(a[0], c[0])
			: Integer.compare(a[1], c[1]));
		for (int[] t : fits)
		{
			if (!seaGraph.join(new int[][]{{t[0], t[1]}}).isEmpty())
			{
				return new int[]{t[0], t[1]};
			}
		}
		return null;
	}

	/** One place an ocean-only route may end: a dock (its index) or open sea (-1), its sea graph joins and pieces. */
	static final class OceanEnd
	{
		final int dock;
		final List<SeaGraph.Join> joins;
		final int[] pieces;
		final String name;

		OceanEnd(int dock, List<SeaGraph.Join> joins, int[] pieces, String name)
		{
			this.dock = dock;
			this.joins = joins;
			this.pieces = pieces;
			this.name = name;
		}
	}

	/** The player may board or leave a boat here: the dock's Sailing level, and its quest finished. */
	static boolean usable(Dock d, Sailing.Options o)
	{
		return d.getLevel() <= o.sailingLevel && (o.player == null || d.getQuest() == null
			|| d.questRequirement().check(o.player).isMet());
	}

	/**
	 * Where the player can board this boat, and what bringing it there costs. {@code bottles}: the bottled boats'
	 * bottles are in the inventory ({@link Sailing#bottlesInHand}).
	 */
	List<SeaBoarding> boardings(Boat b, boolean isAboard, boolean bottles, Sailing.Options o)
	{
		List<SeaBoarding> out = new ArrayList<>();
		if (isAboard)
		{
			out.add(new SeaBoarding(-1, 0, null, null));
			// a route whose first step is not sailing this boat leaves it at sea, so it is lost and recoverable: by a
			// shipwright only, as any lost boat (no teleport focus: the game's lists leave lost boats out); never when
			// the player keeps the boat (a setting)
			if (!o.keepBoat)
			{
				wrightBoardings(b, "lost", -1, o, out);
			}
			return out;
		}
		if (b.getDockId() == Boat.AT_START)
		{
			return out; // at sea somewhere else than the start
		}
		String bring = "sail-bring:" + b.getSlot();
		if (b.isBottled())
		{
			// only with its bottle in the inventory; released at any dock, like boarding, no coins
			if (bottles)
			{
				for (int k = 0; k < docks.size(); k++)
				{
					Dock d = docks.get(k);
					if (usable(d, o))
					{
						out.add(new SeaBoarding(k, o.boardTicks, "Release " + b.label() + " from its bottle at " + d.getName()
							+ " (Unbottle-boat)", "sail-release:" + b.getSlot()));
					}
				}
			}
			return out;
		}
		int home = b.getDockId() >= 0 ? dockIndexOf(b.getDockId()) : -1;
		if (home >= 0)
		{
			out.add(new SeaBoarding(home, 0, null, null));
		}
		// capsized, lost or without a teleport focus: a shipwright only, as a normal retrieval
		if (b.needsShipwright() || b.getFocus() == Boat.Focus.NONE)
		{
			wrightBoardings(b, b.placeWord(), home, o, out);
			return out;
		}
		for (int k = 0; k < docks.size(); k++)
		{
			Dock d = docks.get(k);
			if (k != home && usable(d, o))
			{
				out.add(new SeaBoarding(k, Sailing.TELEPORT_TICKS, b.getFocus().getLabel() + ": " + b.label() + " brought to " + d.getName(), bring));
			}
		}
		return out;
	}

	/**
	 * A shipwright bringing the boat to each usable dock that has one but {@code skip} (its own dock; -1 none), when
	 * shipwrights are allowed: {@link Sailing#RECOVER_TICKS} more, the boat's retrieval coins; {@code word} ("capsized",
	 * "lost"; null for a boat at a dock) says why in the step's words.
	 */
	private void wrightBoardings(Boat b, String word, int skip, Sailing.Options o, List<SeaBoarding> out)
	{
		if (!o.shipwrights)
		{
			return;
		}
		for (int k = 0; k < docks.size(); k++)
		{
			Dock d = docks.get(k);
			if (k != skip && usable(d, o) && d.getShipwright() != null)
			{
				out.add(new SeaBoarding(k, Sailing.RECOVER_TICKS, d.getShipwright() + " brings " + b.label() + " to " + d.getName()
					+ " (Recover-boat, " + String.format(Locale.ROOT, "%,d", b.getType().getRetrievalCost()) + " coins"
					+ (word != null ? "; it is " + word : "") + ")", "sail-bring:" + b.getSlot(), true));
			}
		}
	}
}

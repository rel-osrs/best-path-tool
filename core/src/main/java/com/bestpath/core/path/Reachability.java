package com.bestpath.core.path;

import com.bestpath.core.graph.CoarseGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The quick "can it be reached at all" checks, ignoring costs. Places are components (walking inside one reaches
 * all of it) and the tiles outside any component that transports touch; two places are linked when their
 * components touch through an entrance or a transport leads from one to the other. A walk over these links says
 * exactly which places the player can reach, or which places lead to the destination, and so proves "no route"
 * without exploring half the world. One query at a time.
 */
final class Reachability
{
	/** No early stop for {@link #reached}. */
	static final int ALL = Integer.MIN_VALUE;

	private final CoarseGraph graph;
	private final Beside beside;
	/** Per component: the components it touches through an entrance. */
	private final int[][] neighbours;

	Reachability(CoarseGraph graph, Beside beside, EntranceSides sides)
	{
		this.graph = graph;
		this.beside = beside;
		this.neighbours = sides.neighbours;
	}

	/**
	 * Quick check, ignoring costs: can the destination be reached at all from
	 * the player's tile and the teleport landings, through components that
	 * touch and transports? Walking inside a component reaches all of it, so
	 * this is exact; it saves exploring half the world for a route that
	 * cannot exist. Tiles outside any component (unwalkable transport ends)
	 * count as their own little places.
	 */
	boolean reachable(PathQuery q)
	{
		return reached(q, place(q.getDestX(), q.getDestY(), q.getDestPlane())).contains(place(q.getDestX(), q.getDestY(), q.getDestPlane()));
	}

	/**
	 * Every place reachable from the player node; stops early once {@code dest} is reached ({@link #ALL}: explore all).
	 * The answer is good until the next call (its marks are used again).
	 */
	Reach reached(PathQuery q, int dest)
	{
		Links links = links(q);
		Reach seen = forward.begin(links);
		int[] todo = links.todo;
		int n = 0;
		for (int end : links.landings)
		{
			if (seen.add(end))
			{
				todo[n++] = links.index(end);
			}
		}
		int me = place(q.getStartX(), q.getStartY(), q.getStartPlane());
		if (seen.add(me))
		{
			int i = links.index(me);
			if (i >= 0)
			{
				todo[n++] = i;
			}
			else if (me == dest)
			{
				return seen; // a place no transport touches: only itself
			}
		}
		int destIndex = dest == ALL ? -1 : links.index(dest);
		while (n > 0)
		{
			int i = todo[--n];
			if (i == destIndex)
			{
				return seen;
			}
			if (i < links.components)
			{
				for (int m : neighbours[i])
				{
					if (seen.addIndex(m))
					{
						todo[n++] = m;
					}
				}
			}
			for (int k = links.head[i]; k < links.head[i + 1]; k++)
			{
				int m = links.to[k];
				if (seen.addIndex(m))
				{
					todo[n++] = m;
				}
			}
		}
		return seen;
	}

	/**
	 * The places ({@link #place}) the query's destination can be reached from at all, walking and with the query's
	 * transports that have a start (costs ignored); the teleports' landing places are {@code links.landings}. The
	 * answer is good until the next call.
	 */
	Reach leading(PathQuery q)
	{
		Links links = links(q);
		links.backwards();
		Reach seen = backward.begin(links);
		int[] todo = links.todo;
		int n = 0;
		int dest = place(q.getDestX(), q.getDestY(), q.getDestPlane());
		seen.add(dest);
		int d = links.index(dest);
		if (d >= 0)
		{
			todo[n++] = d;
		}
		while (n > 0)
		{
			int i = todo[--n];
			if (i < links.components)
			{
				for (int m : neighbours[i])
				{
					if (seen.addIndex(m))
					{
						todo[n++] = m;
					}
				}
			}
			for (int k = links.headBack[i]; k < links.headBack[i + 1]; k++)
			{
				int m = links.toBack[k];
				if (seen.addIndex(m))
				{
					todo[n++] = m;
				}
			}
		}
		return seen;
	}

	/**
	 * The links of the last two transport lists, made once per list rather than once per check. Two, because a route
	 * asks in turn with what the player carries and with the bank's items too.
	 */
	private Links links;
	private Links linksBefore;
	private Reach forward = new Reach();
	private Reach backward = new Reach();

	/** The query's transports as links between places, made again only when the transports differ from the last ones. */
	private Links links(PathQuery q)
	{
		List<Transport> tr = q.getTransports();
		if (links != null && links.isFor(tr))
		{
			return links;
		}
		Links l = linksBefore != null && linksBefore.isFor(tr) ? linksBefore : new Links(tr);
		linksBefore = links;
		links = l;
		return l;
	}

	/**
	 * The query's transports as links between places for {@link #reached} and {@link #leading}, in plain arrays rather
	 * than boxed maps and sets, to keep each route's short-lived memory low. Places are numbered: the components first
	 * (their own ids), then every other place a transport touches (a tile no one can walk on, the place beside a solid
	 * end).
	 */
	final class Links
	{
		/** The transports these links are for (compared one by one: a query copies its list). */
		private final Transport[] of;
		final int components;
		/** Other places ({@link #place} below 0, {@link #besidePlace}) by place id: their numbers from {@link #components} on. */
		private final Map<Integer, Integer> others = new HashMap<>();
		private final List<Integer> otherIds = new ArrayList<>();
		/** The teleports' landing places (place ids, usable from anywhere). */
		final int[] landings;
		/** Links by the place they leave from: {@code to[head[i] .. head[i + 1])}. */
		final int[] head;
		final int[] to;
		/** The same links backwards (made when first needed). */
		int[] headBack;
		int[] toBack;
		/** A stack as big as the places, for the walks. */
		final int[] todo;

		Links(List<Transport> list)
		{
			of = list.toArray(new Transport[0]);
			components = graph.componentCount();
			IntPairs pairs = new IntPairs();
			List<Integer> lands = new ArrayList<>();
			// solid tiles already linked to the tiles beside them (boarding, stepping off)
			Set<Integer> boardLinked = new HashSet<>();
			Set<Integer> leaveLinked = new HashSet<>();
			for (Transport t : list)
			{
				int end = place(t.getEndX(), t.getEndY(), t.getEndPlane());
				if (!t.hasStart())
				{
					lands.add(end);
					number(end);
					continue;
				}
				int start = place(t.getStartX(), t.getStartY(), t.getStartPlane());
				pairs.add(number(start), number(end));
				if (t.isTwoWay())
				{
					pairs.add(number(end), number(start));
				}
				if (t.isUsedFromBeside())
				{
					// boarded from beside a solid end, stepped off a solid landing tile (exactly as the tile search)
					int st = Tiles.pack(t.getStartX(), t.getStartY(), t.getStartPlane());
					int en = Tiles.pack(t.getEndX(), t.getEndY(), t.getEndPlane());
					besideLinks(pairs, boardLinked, leaveLinked, st, en, end);
					if (t.isTwoWay())
					{
						besideLinks(pairs, boardLinked, leaveLinked, en, st, start);
					}
				}
			}
			landings = lands.stream().mapToInt(Integer::intValue).toArray();
			int size = components + otherIds.size();
			head = new int[size + 1];
			to = new int[pairs.size];
			pairs.into(head, to);
			todo = new int[size];
		}

		/**
		 * A transport used from beside its solid end {@code used} leads from the tiles beside it (through one "beside"
		 * place per solid tile) to {@code otherPlace}; a solid {@code other} is stepped off to the tiles beside it.
		 * Each solid tile is linked once.
		 */
		private void besideLinks(IntPairs pairs, Set<Integer> boardLinked, Set<Integer> leaveLinked, int used,
			int other, int otherPlace)
		{
			int[] board = beside.boardTiles(used);
			if (board.length > 0)
			{
				int bp = number(besidePlace(used));
				pairs.add(bp, number(otherPlace));
				if (boardLinked.add(used))
				{
					for (int n : board)
					{
						pairs.add(number(place(Tiles.x(n), Tiles.y(n), Tiles.plane(n))), bp);
					}
				}
			}
			if (leaveLinked.add(other))
			{
				for (int n : beside.leaveTiles(other))
				{
					pairs.add(number(otherPlace), number(place(Tiles.x(n), Tiles.y(n), Tiles.plane(n))));
				}
			}
		}

		/** A place's number, given one when it has none yet. */
		private int number(int placeId)
		{
			if (placeId >= 0)
			{
				return placeId;
			}
			Integer i = others.get(placeId);
			if (i == null)
			{
				i = components + otherIds.size();
				others.put(placeId, i);
				otherIds.add(placeId);
			}
			return i;
		}

		/** A place's number, or -1 when no transport touches it (and it is no component). */
		int index(int placeId)
		{
			if (placeId >= 0)
			{
				return placeId;
			}
			Integer i = others.get(placeId);
			return i == null ? -1 : i;
		}

		int size()
		{
			return todo.length;
		}

		/** True when {@code list} has the same transports, in the same order. */
		boolean isFor(List<Transport> list)
		{
			if (list.size() != of.length)
			{
				return false;
			}
			int i = 0;
			for (Transport t : list)
			{
				if (t != of[i++])
				{
					return false;
				}
			}
			return true;
		}

		void backwards()
		{
			if (headBack != null)
			{
				return;
			}
			IntPairs back = new IntPairs();
			for (int i = 0; i + 1 < head.length; i++)
			{
				for (int k = head[i]; k < head[i + 1]; k++)
				{
					back.add(to[k], i);
				}
			}
			headBack = new int[head.length];
			toBack = new int[back.size];
			back.into(headBack, toBack);
		}

		long bytes()
		{
			long n = 16 + 4L * (head.length + to.length + todo.length + landings.length) + 8L * of.length;
			n += headBack == null ? 0 : 4L * (headBack.length + toBack.length);
			return n + 64L * otherIds.size();
		}
	}

	/** Pairs of numbers (from, to) in two growing arrays, turned into links by the place they leave from. */
	private static final class IntPairs
	{
		int[] a = new int[256];
		int[] b = new int[256];
		int size;

		void add(int x, int y)
		{
			if (size == a.length)
			{
				a = Arrays.copyOf(a, size * 2);
				b = Arrays.copyOf(b, size * 2);
			}
			a[size] = x;
			b[size++] = y;
		}

		/** Counting sort by "from", keeping the order pairs were added in. */
		void into(int[] head, int[] to)
		{
			for (int i = 0; i < size; i++)
			{
				head[a[i] + 1]++;
			}
			for (int i = 1; i < head.length; i++)
			{
				head[i] += head[i - 1];
			}
			int[] fill = Arrays.copyOf(head, head.length);
			for (int i = 0; i < size; i++)
			{
				to[fill[a[i]]++] = b[i];
			}
		}
	}

	/**
	 * The places one walk reached ({@link #reached}, {@link #leading}): a mark per place number, cleared by a new mark
	 * value instead of a new table; a place no transport touches can be in it only as the walk's own start.
	 */
	static final class Reach
	{
		Links links;
		private int[] marks = new int[0];
		private int mark;
		private boolean hasLone;
		private int lone;

		Reach begin(Links l)
		{
			links = l;
			if (marks.length < l.size())
			{
				marks = new int[l.size()];
				mark = 0;
			}
			if (++mark == Integer.MAX_VALUE)
			{
				Arrays.fill(marks, 0);
				mark = 1;
			}
			hasLone = false;
			return this;
		}

		/** Adds a place (by id); false when it was in already. */
		boolean add(int placeId)
		{
			int i = links.index(placeId);
			if (i < 0)
			{
				if (hasLone && lone == placeId)
				{
					return false;
				}
				hasLone = true;
				lone = placeId;
				return true;
			}
			return addIndex(i);
		}

		boolean addIndex(int i)
		{
			if (marks[i] == mark)
			{
				return false;
			}
			marks[i] = mark;
			return true;
		}

		boolean contains(int placeId)
		{
			int i = links.index(placeId);
			return i < 0 ? hasLone && lone == placeId : marks[i] == mark;
		}

		long bytes()
		{
			return marks.length == 0 ? 0 : 16 + 4L * marks.length;
		}
	}

	/**
	 * The {@link #reached} place "beside the solid tile, where its transports used from beside are boarded":
	 * below every tile's own place ({@code -1 - tile}, tiles are below 2^30), never a component or {@link #ALL}.
	 */
	private static int besidePlace(int tile)
	{
		return Integer.MIN_VALUE + 1 + tile;
	}

	/** A tile's place for {@link #reachable}: its component, or (negative) the tile itself if it has none. */
	int place(int x, int y, int plane)
	{
		int c = graph.componentAt(x, y, plane);
		return c >= 0 ? c : -1 - Tiles.pack(x, y, plane);
	}

	/** About how many bytes the kept links and marks take. */
	long bytes()
	{
		return (links == null ? 0 : links.bytes()) + (linksBefore == null ? 0 : linksBefore.bytes()) + forward.bytes() + backward.bytes();
	}

	/** Forgets the kept links and marks (made again when next needed). */
	void clear()
	{
		links = null;
		linksBefore = null;
		forward = new Reach();
		backward = new Reach();
	}
}

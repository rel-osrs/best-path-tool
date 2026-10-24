package com.bestpath.core.sea;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.MovementRules;
import java.util.Arrays;

/**
 * The sea split into pieces a boat cannot leave by sailing: sea tiles joined
 * by steps the movement data allows (boats cannot cross blocked tiles such as
 * rocks and the white border lines). Steps between sea
 * areas count (a boat sails into a restricted area from open water); whether
 * the boat may enter one is decided later, from its parts.
 *
 * <p>The biggest piece is the main ocean. Blocked sea tiles belong to no
 * piece. Immutable once built.
 */
public final class SeaPieces
{
	private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** Region ids with sea tiles, ascending (as in the sea map). */
	private final int[] keys;
	/** Per region: piece + 1 per tile (0 = none), index localX << 6 | localY. */
	private final short[][] tiles;
	private final int[] sizes;
	private final int main;

	private SeaPieces(int[] keys, short[][] tiles, int[] sizes)
	{
		this.keys = keys;
		this.tiles = tiles;
		this.sizes = sizes;
		int best = -1;
		for (int i = 0; i < sizes.length; i++)
		{
			if (best < 0 || sizes[i] > sizes[best])
			{
				best = i;
			}
		}
		this.main = best;
	}

	/**
	 * Splits the sea along the steps plane 0 of the movement data allows.
	 * Orthogonal steps are enough: a diagonal step needs both orthogonal ones.
	 */
	public static SeaPieces build(SeaMap sea, MovementRules.Flags plane0)
	{
		int[] keys = sea.regionIds();
		short[][] tiles = new short[keys.length][];
		int[] sizes = new int[64];
		int count = 0;
		int[] queue = new int[4096];
		for (int k = 0; k < keys.length; k++)
		{
			int bx = (keys[k] >> 8) << 6;
			int by = (keys[k] & 0xFF) << 6;
			for (int i = 0; i < 4096; i++)
			{
				int x = bx + (i >> 6);
				int y = by + (i & 63);
				if (piece(keys, tiles, x, y) >= 0 || !sea.isSea(x, y, 0) || !open(plane0, x, y))
				{
					continue;
				}
				if (count == Short.MAX_VALUE - 1)
				{
					throw new IllegalStateException("too many sea pieces");
				}
				// flood the new piece
				int id = count++;
				set(keys, tiles, x, y, id);
				int size = 1;
				int head = 0;
				int tail = 0;
				queue[tail++] = x << 16 | y;
				while (head < tail)
				{
					int cx = queue[head] >>> 16;
					int cy = queue[head++] & 0xFFFF;
					for (int[] d : STEPS)
					{
						int nx = cx + d[0];
						int ny = cy + d[1];
						if (!sea.isSea(nx, ny, 0) || piece(keys, tiles, nx, ny) >= 0
							|| !MovementRules.canStep(plane0, cx, cy, d[0], d[1]))
						{
							continue;
						}
						set(keys, tiles, nx, ny, id);
						size++;
						if (tail == queue.length)
						{
							// reuse the used front before growing
							System.arraycopy(queue, head, queue, 0, tail - head);
							tail -= head;
							head = 0;
							if (tail == queue.length)
							{
								queue = Arrays.copyOf(queue, queue.length * 2);
							}
						}
						queue[tail++] = nx << 16 | ny;
					}
				}
				if (id == sizes.length)
				{
					sizes = Arrays.copyOf(sizes, sizes.length * 2);
				}
				sizes[id] = size;
			}
		}
		return new SeaPieces(keys, tiles, Arrays.copyOf(sizes, count));
	}

	private static boolean open(MovementRules.Flags f, int x, int y)
	{
		return (f.get(x, y) & CollisionFlags.BLOCKED) == 0;
	}

	private static int piece(int[] keys, short[][] tiles, int x, int y)
	{
		if (x < 0 || y < 0)
		{
			return -1;
		}
		int k = Arrays.binarySearch(keys, (x >> 6) << 8 | (y >> 6));
		if (k < 0 || tiles[k] == null)
		{
			return -1;
		}
		return tiles[k][(x & 63) << 6 | (y & 63)] - 1;
	}

	private static void set(int[] keys, short[][] tiles, int x, int y, int id)
	{
		int k = Arrays.binarySearch(keys, (x >> 6) << 8 | (y >> 6));
		if (tiles[k] == null)
		{
			tiles[k] = new short[4096];
		}
		tiles[k][(x & 63) << 6 | (y & 63)] = (short) (id + 1);
	}

	/** The piece of a sea tile, or -1 (not sea, blocked, or not plane 0). */
	public int pieceAt(int x, int y, int plane)
	{
		return plane != 0 ? -1 : piece(keys, tiles, x, y);
	}

	/** Number of tiles in a piece. */
	public int size(int piece)
	{
		return sizes[piece];
	}

	public int count()
	{
		return sizes.length;
	}

	/** The biggest piece (the main ocean), or -1 when there is no sea. */
	public int mainPiece()
	{
		return main;
	}
}

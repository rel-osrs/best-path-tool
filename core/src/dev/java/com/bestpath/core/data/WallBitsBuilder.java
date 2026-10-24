package com.bestpath.core.data;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.path.LineOfSight;
import java.util.Map;
import java.util.TreeMap;

/**
 * Makes {@link WallBits} from raw tile flags, when the data is made.
 */
public final class WallBitsBuilder
{
	private WallBitsBuilder()
	{
	}

	/** The wall bitmaps of the cells given (cell keys regionId &lt;&lt; 2 | plane). */
	public static WallBits fromFlags(LineOfSight.WallFlags flags, Iterable<Integer> cellKeys)
	{
		TreeMap<Integer, long[]> m = new TreeMap<>();
		for (int key : cellKeys)
		{
			int bx = (key >> 10) << 6;
			int by = ((key >> 2) & 0xFF) << 6;
			int p = key & 3;
			long[] bm = new long[WallBits.LONGS_PER_CELL];
			boolean any = false;
			for (int lx = 0; lx < 64; lx++)
			{
				for (int ly = 0; ly < 64; ly++)
				{
					int x = bx + lx;
					int y = by + ly;
					int f = flags.get(x, y, p);
					boolean north = (f & CollisionFlags.NORTH) != 0 || (flags.get(x, y + 1, p) & CollisionFlags.SOUTH) != 0;
					boolean east = (f & CollisionFlags.EAST) != 0 || (flags.get(x + 1, y, p) & CollisionFlags.WEST) != 0;
					int v = (north ? 1 : 0) | (east ? 2 : 0);
					if (v != 0)
					{
						int bit = (lx << 6 | ly) * 2;
						bm[bit >> 6] |= (long) v << (bit & 63);
						any = true;
					}
				}
			}
			if (any)
			{
				m.put(key, bm);
			}
		}
		int[] k = new int[m.size()];
		long[][] c = new long[m.size()][];
		int i = 0;
		for (Map.Entry<Integer, long[]> e : m.entrySet())
		{
			k[i] = e.getKey();
			c[i] = e.getValue();
			i++;
		}
		return new WallBits(k, c);
	}
}

package com.bestpath.core.sea;

import com.bestpath.core.collision.MovementRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes {@link SeaGrid}s from the sea map, its pieces and the movement data, when the data is made (the plugin reads
 * its grid from the sea file).
 */
public final class SeaGridBuilder
{
	private SeaGridBuilder()
	{
	}

	/**
	 * @param plane0 plane 0 of the movement data with the sea still open
	 *               (before it is closed to walking)
	 */
	public static SeaGrid build(SeaMap sea, SeaPieces pieces, MovementRules.Flags plane0)
	{
		return build(sea, pieces, plane0, new int[0][]);
	}

	/**
	 * As above, with the water the boat-only transports end in (e.g. the Wyrmscraig cave, where a boat sails into a
	 * cave pool cut off from the main ocean): each piece one of {@code joinedAt} ({x, y}, plane 0) lies in is part of
	 * the grid too. "Main ocean" in {@link SeaGrid}'s notes then means the water boats can use: the main
	 * ocean and those pieces.
	 */
	public static SeaGrid build(SeaMap sea, SeaPieces pieces, MovementRules.Flags plane0, int[][] joinedAt)
	{
		int main = pieces.mainPiece();
		Set<Integer> kept = new HashSet<>();
		if (main >= 0)
		{
			kept.add(main);
		}
		for (int[] t : joinedAt)
		{
			int p = pieces.pieceAt(t[0], t[1], 0);
			if (p >= 0)
			{
				kept.add(p);
			}
		}
		List<Integer> keys = new ArrayList<>();
		List<long[]> masks = new ArrayList<>();
		List<Byte> uniform = new ArrayList<>();
		List<byte[]> nibbles = new ArrayList<>();
		if (main >= 0)
		{
			for (int key : sea.regionIds())
			{
				int bx = (key >> 8) << 6;
				int by = (key & 0xFF) << 6;
				long[] m = new long[64];
				byte[] areas = new byte[2048];
				int first = -1;
				boolean same = true;
				boolean any = false;
				for (int l = 0; l < 4096; l++)
				{
					int x = bx + (l >> 6);
					int y = by + (l & 63);
					if (!kept.contains(pieces.pieceAt(x, y, 0)))
					{
						continue;
					}
					any = true;
					m[l >> 6] |= 1L << (l & 63);
					int a = SeaMap.areaOf(sea.texture(x, y, 0)).ordinal();
					areas[l >> 1] |= (byte) ((l & 1) == 0 ? a : a << 4);
					if (first < 0)
					{
						first = a;
					}
					same &= a == first;
				}
				if (any)
				{
					keys.add(key);
					masks.add(m);
					uniform.add(same ? (byte) first : (byte) -1);
					nibbles.add(same ? null : areas);
				}
			}
		}
		int[] k = keys.stream().mapToInt(Integer::intValue).toArray();
		byte[] u = new byte[k.length];
		for (int i = 0; i < u.length; i++)
		{
			u[i] = uniform.get(i);
		}
		long[][] m = SeaGrid.shareFull(masks.toArray(new long[0][]));
		SeaGrid rule = new SeaGrid(k, m, u, nibbles.toArray(new byte[0][]), new int[0], new byte[0]);
		// the steps the movement data allows (walls between ocean tiles): only where they differ from the rule
		List<int[]> odd = new ArrayList<>();
		// every main-ocean tile, in tile-number order
		for (int s = 0; s < rule.keys.length; s++)
		{
			int bx = (rule.keys[s] >> 8) << 6;
			int by = (rule.keys[s] & 0xFF) << 6;
			for (int col = 0; col < 64; col++)
			{
				long w = rule.mask[s][col];
				while (w != 0)
				{
					int x = bx + col;
					int y = by + Long.numberOfTrailingZeros(w);
					w &= w - 1;
					int ruleMoves = SeaGridDiagnostics.moves(rule, x, y);
					int moves = 0;
					for (int d = 0; d < 8; d++)
					{
						if ((ruleMoves & 1 << d) != 0 && MovementRules.canStep(plane0, x, y, SeaGrid.DX[d], SeaGrid.DY[d]))
						{
							moves |= 1 << d;
						}
					}
					if (moves != ruleMoves)
					{
						odd.add(new int[]{x << 14 | y, moves});
					}
				}
			}
		}
		if (odd.isEmpty())
		{
			return rule;
		}
		odd.sort((a, b) -> Integer.compare(a[0], b[0]));
		int[] ot = new int[odd.size()];
		byte[] om = new byte[odd.size()];
		for (int i = 0; i < ot.length; i++)
		{
			ot[i] = odd.get(i)[0];
			om[i] = (byte) odd.get(i)[1];
		}
		return new SeaGrid(k, m, u, rule.areaNibbles, ot, om);
	}
}

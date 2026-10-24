package com.bestpath.core.data;

import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaChannels;
import com.bestpath.core.sea.SeaGrid;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * The sea file ({@code sea.bin}, optional): the main ocean as a tile mask with
 * its sea areas and the few tiles whose boat steps are not the mask's rule,
 * the docks, and the sea graph's channels. Clearance and the sea graph are not
 * stored: the plugin works them out when it loads ({@link SeaGrid#of}, then the
 * sea graph).
 *
 * <p>File (gzip, big-endian): "BPSE", int version (0 to 2), int board ticks
 * (boarding and leaving a boat, each; must equal {@link Dock#BOARD_TICKS}),
 * int region count, per region (ascending id): int region id, byte 1 = all 4,096
 * tiles main ocean or 0 = 64 longs follow (column per local x, bit = local y),
 * byte area ({@link com.bestpath.core.sea.SeaArea} ordinal for the whole region,
 * or 255 = 2,048 bytes follow: 4 bits per local tile, index localX &lt;&lt; 6 |
 * localY, even index in the low bits); int odd tile count, per tile: int x &lt;&lt;
 * 14 | y, byte steps (bit per direction: W, E, S, N, SW, SE, NW, NE); int dock
 * count, per dock: int id, UTF name, int Sailing level, UTF quest ("" = none),
 * byte kind (0 gangplank, 1 mooring point), ints land x, y, plane, size, buoy
 * x, y, size, int stand-on tile count with ints x, y, plane each, UTF
 * shipwright ("" = none); from version 1, after a shipwright's name: byte 1
 * and ints x, y, plane where the shipwright stands (its Wiki tile), or byte 0
 * (not known). Version 0 files have no shipwright places: their shipwrights
 * bring the boat to the dock's land tile. Version 2 ends with the sea graph's
 * channels ({@link SeaChannels}): int count, per channel int point count and
 * the points (int x &lt;&lt; 14 | y). Older files have none.
 */
public final class SeaBits
{
	static final int MAGIC = 0x42505345; // "BPSE"
	static final int VERSION = 2;
	/** Shipwright places, no channels. */
	private static final int VERSION_1 = 1;
	/** The first version: no shipwright places. */
	private static final int VERSION_0 = 0;
	static final int PER_TILE = 255;

	private SeaBits()
	{
	}

	/** What the file holds. */
	public static final class Data
	{
		private final SeaGrid grid;
		private final List<Dock> docks;
		private final List<int[]> channels;

		public Data(SeaGrid grid, List<Dock> docks, List<int[]> channels)
		{
			this.grid = grid;
			this.docks = Collections.unmodifiableList(new ArrayList<>(docks));
			this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
		}

		/**
		 * The sea graph's channels (points packed x &lt;&lt; 14 | y), found when the data was generated; empty in files
		 * older than version 2. The plugin never finds them itself (that floods the whole sea, too slow).
		 */
		public List<int[]> getChannels()
		{
			return channels;
		}

		/** The main ocean (clearance worked out on reading). */
		public SeaGrid getGrid()
		{
			return grid;
		}

		/** Every dock (duplicates dropped, shipwrights set). */
		public List<Dock> getDocks()
		{
			return docks;
		}
	}

	public static Data read(InputStream in) throws IOException
	{
		DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)));
		if (d.readInt() != MAGIC)
		{
			throw new IOException("not a BestPath sea file");
		}
		int version = d.readInt();
		if (version != VERSION && version != VERSION_1 && version != VERSION_0)
		{
			throw new IOException("unsupported sea file version " + version);
		}
		int board = d.readInt();
		if (board != Dock.BOARD_TICKS)
		{
			throw new IOException("sea file has " + board + " ticks to board a boat; this version uses " + Dock.BOARD_TICKS);
		}
		int n = d.readInt();
		int[] keys = new int[n];
		long[][] masks = new long[n][];
		byte[] uniform = new byte[n];
		byte[][] areas = new byte[n][];
		for (int s = 0; s < n; s++)
		{
			keys[s] = d.readInt();
			long[] m = new long[64];
			if (d.readUnsignedByte() == 1)
			{
				Arrays.fill(m, -1L);
			}
			else
			{
				for (int i = 0; i < 64; i++)
				{
					m[i] = d.readLong();
				}
			}
			masks[s] = m;
			int area = d.readUnsignedByte();
			if (area == PER_TILE)
			{
				uniform[s] = -1;
				areas[s] = new byte[2048];
				d.readFully(areas[s]);
			}
			else
			{
				uniform[s] = (byte) area;
			}
		}
		int no = d.readInt();
		int[] odd = new int[no];
		byte[] moves = new byte[no];
		for (int i = 0; i < no; i++)
		{
			odd[i] = d.readInt();
			moves[i] = d.readByte();
		}
		SeaGrid grid;
		try
		{
			grid = SeaGrid.of(keys, masks, uniform, areas, odd, moves);
		}
		catch (IllegalArgumentException e)
		{
			throw new IOException("sea file: " + e.getMessage(), e);
		}
		int nd = d.readInt();
		List<Dock> docks = new ArrayList<>(nd);
		for (int i = 0; i < nd; i++)
		{
			int id = d.readInt();
			String name = d.readUTF();
			int level = d.readInt();
			String quest = d.readUTF();
			int kind = d.readUnsignedByte();
			if (kind >= Dock.Kind.values().length)
			{
				throw new IOException("sea file: unknown dock kind " + kind);
			}
			int lx = d.readInt();
			int ly = d.readInt();
			int lp = d.readInt();
			int ls = d.readInt();
			int bx = d.readInt();
			int by = d.readInt();
			int bs = d.readInt();
			int nt = d.readInt();
			List<int[]> tiles = new ArrayList<>(nt);
			for (int t = 0; t < nt; t++)
			{
				tiles.add(new int[]{d.readInt(), d.readInt(), d.readInt()});
			}
			String wright = d.readUTF();
			Dock dock = new Dock(id, name, level, quest.isEmpty() ? null : quest, Dock.Kind.values()[kind], lx, ly, lp, ls, bx, by, bs, tiles);
			if (wright.isEmpty())
			{
				docks.add(dock);
			}
			else if (version >= 1 && d.readUnsignedByte() == 1)
			{
				docks.add(dock.withShipwright(wright, d.readInt(), d.readInt(), d.readInt()));
			}
			else
			{
				docks.add(dock.withShipwright(wright));
			}
		}
		List<int[]> channels = new ArrayList<>();
		if (version >= 2)
		{
			int nc = d.readInt();
			for (int i = 0; i < nc; i++)
			{
				int np = d.readInt();
				if (np < 2 || np > 1 << 16)
				{
					throw new IOException("sea file: a channel of " + np + " points");
				}
				int[] c = new int[np];
				for (int k = 0; k < np; k++)
				{
					c[k] = d.readInt();
				}
				channels.add(c);
			}
		}
		if (d.read() != -1)
		{
			throw new IOException("sea file has data after its end");
		}
		return new Data(grid, docks, channels);
	}
}

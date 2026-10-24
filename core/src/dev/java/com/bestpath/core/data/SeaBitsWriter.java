package com.bestpath.core.data;

import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.DockDiagnostics;
import com.bestpath.core.sea.SeaChannels;
import com.bestpath.core.sea.SeaGraph;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaGridDiagnostics;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Writes the sea file ({@code sea.bin}) that {@link SeaBits#read} reads (used when the data is made).
 */
public final class SeaBitsWriter
{
	private SeaBitsWriter()
	{
	}

	/** Writes the sea with its channels found now ({@link SeaChannels#find}). */
	public static void write(SeaGrid grid, List<Dock> docks, OutputStream out) throws IOException
	{
		write(grid, docks, SeaChannels.find(SeaGraph.build(grid, docks)), out, SeaBits.VERSION);
	}

	/** As above with channels already found. */
	public static void write(SeaGrid grid, List<Dock> docks, List<int[]> channels, OutputStream out) throws IOException
	{
		write(grid, docks, channels, out, SeaBits.VERSION);
	}

	/** As {@link #write(SeaGrid, List, OutputStream)} in an older version, without channels (checks old files load). */
	static void write(SeaGrid grid, List<Dock> docks, OutputStream out, int version) throws IOException
	{
		write(grid, docks, List.of(), out, version);
	}

	private static void write(SeaGrid grid, List<Dock> docks, List<int[]> channels, OutputStream out, int version) throws IOException
	{
		ByteArrayOutputStream raw = new ByteArrayOutputStream(1 << 16);
		DataOutputStream d = new DataOutputStream(raw);
		d.writeInt(SeaBits.MAGIC);
		d.writeInt(version);
		d.writeInt(Dock.BOARD_TICKS);
		int[] keys = grid.regionIds();
		d.writeInt(keys.length);
		long[] full = new long[64];
		Arrays.fill(full, -1L);
		for (int s = 0; s < keys.length; s++)
		{
			d.writeInt(keys[s]);
			long[] m = grid.regionMask(s);
			boolean all = Arrays.equals(m, full);
			d.writeByte(all ? 1 : 0);
			if (!all)
			{
				for (long w : m)
				{
					d.writeLong(w);
				}
			}
			int area = SeaGridDiagnostics.regionUniformArea(grid, s);
			d.writeByte(area >= 0 ? area : SeaBits.PER_TILE);
			if (area < 0)
			{
				d.write(SeaGridDiagnostics.regionAreas(grid, s));
			}
		}
		int[] odd = SeaGridDiagnostics.oddTiles(grid);
		byte[] moves = SeaGridDiagnostics.oddMoves(grid);
		d.writeInt(odd.length);
		for (int i = 0; i < odd.length; i++)
		{
			d.writeInt(odd[i]);
			d.writeByte(moves[i]);
		}
		d.writeInt(docks.size());
		for (Dock k : docks)
		{
			d.writeInt(k.getId());
			d.writeUTF(k.getName());
			d.writeInt(k.getLevel());
			d.writeUTF(k.getQuest() == null ? "" : k.getQuest());
			d.writeByte(DockDiagnostics.kind(k).ordinal());
			d.writeInt(DockDiagnostics.landX(k));
			d.writeInt(DockDiagnostics.landY(k));
			d.writeInt(DockDiagnostics.landPlane(k));
			d.writeInt(DockDiagnostics.landSize(k));
			d.writeInt(k.getBuoyX());
			d.writeInt(k.getBuoyY());
			d.writeInt(k.getBuoySize());
			d.writeInt(k.getLandTiles().size());
			for (int[] t : k.getLandTiles())
			{
				d.writeInt(t[0]);
				d.writeInt(t[1]);
				d.writeInt(t[2]);
			}
			d.writeUTF(k.getShipwright() == null ? "" : k.getShipwright());
			if (version >= 1 && k.getShipwright() != null && !k.getShipwright().isEmpty()) // as the reader decides
			{
				int[] w = k.getShipwrightTile();
				d.writeByte(w == null ? 0 : 1);
				if (w != null)
				{
					d.writeInt(w[0]);
					d.writeInt(w[1]);
					d.writeInt(w[2]);
				}
			}
		}
		if (version >= 2)
		{
			d.writeInt(channels.size());
			for (int[] c : channels)
			{
				d.writeInt(c.length);
				for (int p : c)
				{
					d.writeInt(p);
				}
			}
		}
		d.flush();
		try (GZIPOutputStream gz = new GZIPOutputStream(out))
		{
			raw.writeTo(gz);
		}
	}
}

package com.bestpath.core.data;

import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Tiles;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * The door file ({@code doors.bin}): which steps go through a door or gate, as a
 * sparse list of tiles with their north half of the door bits (N, NE, E, SE;
 * the other half is the same steps backwards, see
 * {@link DoorMap#fromNorthHalf}).
 *
 * <p>File (gzip): "BPDR", int version 0, int count, then per tile (ascending
 * by cell key, then local index): int cell key (regionId &lt;&lt; 2 | plane),
 * short local index (localX &lt;&lt; 6 | localY), byte bits.
 */
public final class DoorBits
{
	static final int MAGIC = 0x42504452; // "BPDR"
	static final int VERSION = 0;

	private DoorBits()
	{
	}

	public static DoorMap read(InputStream in) throws IOException
	{
		DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)));
		if (d.readInt() != MAGIC)
		{
			throw new IOException("not a BestPath door file");
		}
		int version = d.readInt();
		if (version != VERSION)
		{
			throw new IOException("unsupported door file version " + version);
		}
		int n = d.readInt();
		int[] tiles = new int[n];
		byte[] bits = new byte[n];
		for (int i = 0; i < n; i++)
		{
			int key = d.readInt();
			int l = d.readShort() & 0xFFF;
			bits[i] = d.readByte();
			int x = ((key >> 10) << 6) + (l >> 6);
			int y = (((key >> 2) & 0xFF) << 6) + (l & 63);
			tiles[i] = Tiles.pack(x, y, key & 3);
		}
		return DoorMap.fromNorthHalf(tiles, bits);
	}
}

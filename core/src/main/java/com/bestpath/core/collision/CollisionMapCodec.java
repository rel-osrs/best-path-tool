package com.bestpath.core.collision;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

/**
 * Reads {@link CollisionMap} files (format version 1). Layout,
 * gzip-compressed:
 *
 * <pre>
 * "BPCM"  magic (4 bytes)
 * int     format version (1)
 * int     cache id (OpenRS2), int cache build
 * int     count of region-planes
 * count x { int key (regionId << 2 | plane); 256 x long step bits }
 * int     CRC32 of all bytes above
 * </pre>
 */
public final class CollisionMapCodec
{
	public static final int FORMAT_VERSION = 1;
	static final int MAGIC = 0x4250434D; // "BPCM"

	private CollisionMapCodec()
	{
	}

	public static CollisionMap read(InputStream in) throws IOException
	{
		byte[] all;
		try (GZIPInputStream gz = new GZIPInputStream(in))
		{
			all = gz.readAllBytes();
		}
		if (all.length < 24)
		{
			throw new IOException("collision file too short");
		}
		CRC32 crc = new CRC32();
		crc.update(all, 0, all.length - 4);
		DataInputStream d = new DataInputStream(new ByteArrayInputStream(all));
		if (d.readInt() != MAGIC)
		{
			throw new IOException("not a BestPath collision file");
		}
		int version = d.readInt();
		if (version != FORMAT_VERSION)
		{
			throw new IOException("unsupported collision format version " + version);
		}
		int cacheId = d.readInt();
		int build = d.readInt();
		int n = d.readInt();
		int[] keys = new int[n];
		long[][] blocks = new long[n][CollisionMap.LONGS_PER_BLOCK];
		for (int i = 0; i < n; i++)
		{
			keys[i] = d.readInt();
			if (i > 0 && keys[i] <= keys[i - 1])
			{
				throw new IOException("collision file keys out of order");
			}
			for (int j = 0; j < CollisionMap.LONGS_PER_BLOCK; j++)
			{
				blocks[i][j] = d.readLong();
			}
		}
		if (d.readInt() != (int) crc.getValue())
		{
			throw new IOException("collision file checksum mismatch");
		}
		return new CollisionMap(keys, blocks, cacheId, build);
	}
}

package com.bestpath.core.collision;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;

/**
 * Writes {@link CollisionMap} files, in the format {@link CollisionMapCodec} reads (used when the data is made).
 */
public final class CollisionMapWriter
{
	private CollisionMapWriter()
	{
	}

	public static void write(CollisionMap map, OutputStream out) throws IOException
	{
		ByteArrayOutputStream raw = new ByteArrayOutputStream(map.size() * 2052 + 32);
		DataOutputStream d = new DataOutputStream(raw);
		d.writeInt(CollisionMapCodec.MAGIC);
		d.writeInt(CollisionMapCodec.FORMAT_VERSION);
		d.writeInt(map.cacheId);
		d.writeInt(map.cacheBuild);
		d.writeInt(map.size());
		int[] keys = map.keys;
		long[][] blocks = map.blocks;
		for (int i = 0; i < keys.length; i++)
		{
			d.writeInt(keys[i]);
			for (long v : blocks[i])
			{
				d.writeLong(v);
			}
		}
		d.flush();
		CRC32 crc = new CRC32();
		crc.update(raw.toByteArray());
		d.writeInt((int) crc.getValue());
		try (GZIPOutputStream gz = new GZIPOutputStream(out))
		{
			raw.writeTo(gz);
		}
	}
}

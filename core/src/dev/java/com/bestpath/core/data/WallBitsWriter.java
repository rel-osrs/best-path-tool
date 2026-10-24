package com.bestpath.core.data;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Writes the wall file ({@code walls.bin}) that {@link WallBits#read} reads (used when the data is made).
 */
public final class WallBitsWriter
{
	private WallBitsWriter()
	{
	}

	public static void write(WallBits walls, OutputStream out) throws IOException
	{
		int[] keys = walls.keys;
		ByteArrayOutputStream raw = new ByteArrayOutputStream(12 + keys.length * (4 + 8 * WallBits.LONGS_PER_CELL));
		DataOutputStream d = new DataOutputStream(raw);
		d.writeInt(WallBits.MAGIC);
		d.writeInt(WallBits.VERSION);
		d.writeInt(keys.length);
		for (int i = 0; i < keys.length; i++)
		{
			d.writeInt(keys[i]);
			for (long v : walls.cells[i])
			{
				d.writeLong(v);
			}
		}
		d.flush();
		try (GZIPOutputStream gz = new GZIPOutputStream(out))
		{
			raw.writeTo(gz);
		}
	}
}

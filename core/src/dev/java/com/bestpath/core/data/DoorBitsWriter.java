package com.bestpath.core.data;

import com.bestpath.core.path.DoorMap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Writes the door file ({@code doors.bin}) that {@link DoorBits#read} reads (used when the data is made).
 */
public final class DoorBitsWriter
{
	private DoorBitsWriter()
	{
	}

	/** Writes the north half of {@code doors} for the cells given (ascending keys); returns the number of tiles written. */
	public static int write(DoorMap doors, int[] cellKeys, OutputStream out) throws IOException
	{
		List<int[]> list = new ArrayList<>();
		for (int key : cellKeys)
		{
			byte[] cb = doors.cellBits(key);
			if (cb == null)
			{
				continue;
			}
			for (int l = 0; l < 4096; l++)
			{
				if ((cb[l] & 0x0F) != 0)
				{
					list.add(new int[]{key, l, cb[l] & 0x0F});
				}
			}
		}
		ByteArrayOutputStream raw = new ByteArrayOutputStream(12 + list.size() * 7);
		DataOutputStream d = new DataOutputStream(raw);
		d.writeInt(DoorBits.MAGIC);
		d.writeInt(DoorBits.VERSION);
		d.writeInt(list.size());
		for (int[] t : list)
		{
			d.writeInt(t[0]);
			d.writeShort(t[1]);
			d.writeByte(t[2]);
		}
		d.flush();
		try (GZIPOutputStream gz = new GZIPOutputStream(out))
		{
			raw.writeTo(gz);
		}
		return list.size();
	}
}

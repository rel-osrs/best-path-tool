package com.bestpath.core.path;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Writes the walking tables file that {@link WalkTables#read} reads (used when the data is made).
 */
public final class WalkTablesWriter
{
	private WalkTablesWriter()
	{
	}

	public static void write(WalkTables t, OutputStream out) throws IOException
	{
		GZIPOutputStream gz = new GZIPOutputStream(out);
		DataOutputStream d = new DataOutputStream(new BufferedOutputStream(gz));
		d.writeInt(WalkTables.MAGIC);
		d.writeInt(WalkTables.VERSION);
		d.writeLong(t.graphKey);
		d.writeInt(t.mKeys.length);
		writeInts(d, t.mKeys);
		writeInts(d, t.mK);
		d.write(t.mSym);
		writeInts(d, t.mOff);
		writeShorts(d, t.mPool);
		d.writeInt(t.sKeys.length);
		writeInts(d, t.sKeys);
		writeInts(d, t.sOff);
		writeShorts(d, t.sPool);
		d.writeInt(t.bKeys.length);
		writeLongs(d, t.bKeys);
		writeInts(d, t.bOff);
		writeShorts(d, t.bPool);
		d.writeInt(t.eKeys.length);
		writeInts(d, t.eKeys);
		writeInts(d, t.eTileOff);
		d.writeInt(t.eTiles.length);
		writeInts(d, t.eTiles);
		writeInts(d, t.eRowOff);
		writeShorts(d, t.eRows);
		d.writeInt(t.esKeys.length);
		writeLongs(d, t.esKeys);
		writeInts(d, t.esComp);
		writeInts(d, t.esOff);
		writeShorts(d, t.esPool);
		d.flush();
		gz.finish();
	}

	private static void writeInts(DataOutputStream d, int[] v) throws IOException
	{
		for (int x : v)
		{
			d.writeInt(x);
		}
	}

	private static void writeLongs(DataOutputStream d, long[] v) throws IOException
	{
		for (long x : v)
		{
			d.writeLong(x);
		}
	}

	private static void writeShorts(DataOutputStream d, short[] v) throws IOException
	{
		d.writeInt(v.length);
		for (short x : v)
		{
			d.writeShort(x);
		}
	}
}

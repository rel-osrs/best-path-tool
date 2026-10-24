package com.bestpath.core.sea;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Makes the sea graph's channel list the data keeps ({@link SeaChannels}): the channels found, without those deleted
 * by hand, which are recorded by a key.
 */
public final class SeaChannelsBuilder
{
	private SeaChannelsBuilder()
	{
	}

	/**
	 * A channel's name, used to record hand-deleted channels: its two ends, the lower packed tile first, "x,y-x,y".
	 * The same whichever way round its points run, and the same every time the channels are found again from the same
	 * sea.
	 */
	public static String key(int[] points)
	{
		int a = points[0];
		int b = points[points.length - 1];
		int lo = Math.min(a, b);
		int hi = Math.max(a, b);
		return (lo >>> 14) + "," + (lo & 0x3FFF) + "-" + (hi >>> 14) + "," + (hi & 0x3FFF);
	}

	/** The channels without the hand-deleted ones (by {@link #key}). */
	public static List<int[]> without(List<int[]> channels, Set<String> deleted)
	{
		if (deleted == null || deleted.isEmpty())
		{
			return channels;
		}
		List<int[]> out = new ArrayList<>(channels.size());
		for (int[] c : channels)
		{
			if (!deleted.contains(key(c)))
			{
				out.add(c);
			}
		}
		return out;
	}
}

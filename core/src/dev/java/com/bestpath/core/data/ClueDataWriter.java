package com.bestpath.core.data;

import com.bestpath.core.path.Tiles;

/**
 * Writes the clue steps file ({@code clues.tsv}) that {@link ClueData#parse} reads (used when the data is made).
 */
public final class ClueDataWriter
{
	private ClueDataWriter()
	{
	}

	/** The file's text: a comment, the header, one line per step. */
	public static String write(ClueData data, String comment)
	{
		StringBuilder sb = new StringBuilder();
		for (String c : comment.split("\n"))
		{
			sb.append("# ").append(c).append('\n');
		}
		sb.append(ClueData.HEADER).append('\n');
		for (ClueData.Step s : data.getSteps())
		{
			sb.append(write(s)).append('\n');
		}
		return sb.toString();
	}

	/** One step's line of the file. */
	public static String write(ClueData.Step s)
	{
		StringBuilder it = new StringBuilder();
		for (int i = 0; i < s.items.length; i++)
		{
			it.append(i == 0 ? "" : ",").append(s.items[i]);
		}
		StringBuilder tg = new StringBuilder();
		for (int i = 0; i < s.targets.length; i++)
		{
			tg.append(i == 0 ? "" : ";").append(tile(s.targets[i]));
		}
		return String.join("\t", s.key, s.type, s.tier, s.target, it, tg, s.stash == -1 ? "" : tile(s.stash),
			TransportLine.clean(s.text), TransportLine.clean(s.description), TransportLine.clean(s.wiki), TransportLine.clean(s.needs),
			TransportLine.clean(s.note), TransportLine.clean(s.action), s.stashVarbit == -1 ? "" : String.valueOf(s.stashVarbit),
			TransportLine.clean(s.stashPlace), s.keyNpc == -1 ? "" : tile(s.keyNpc), TransportLine.clean(s.keyNpcName),
			s.keyItem == -1 ? "" : String.valueOf(s.keyItem));
	}

	/** "x,y,plane" of a packed tile. */
	private static String tile(int packed)
	{
		return Tiles.x(packed) + "," + Tiles.y(packed) + "," + Tiles.plane(packed);
	}
}

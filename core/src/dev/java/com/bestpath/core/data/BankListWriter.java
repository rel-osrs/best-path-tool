package com.bestpath.core.data;

import com.bestpath.core.path.Tiles;

/**
 * Writes the banks file ({@value BankList#FILE}) that {@link BankList#parse} reads (used when the data is made).
 */
public final class BankListWriter
{
	private BankListWriter()
	{
	}

	/** The text form: {@code comment} (each line written as a # line), the header, one line per bank. */
	public static String write(BankList list, String comment)
	{
		StringBuilder sb = new StringBuilder();
		if (comment != null)
		{
			for (String c : comment.split("\n"))
			{
				sb.append("# ").append(c).append('\n');
			}
		}
		sb.append(BankList.HEADER).append('\n');
		for (BankList.Bank b : list.banks)
		{
			sb.append(line(b)).append('\n');
		}
		return sb.toString();
	}

	private static String line(BankList.Bank b)
	{
		StringBuilder sb = new StringBuilder();
		for (int t : b.use)
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(Tiles.x(t)).append(',').append(Tiles.y(t)).append(',').append(Tiles.plane(t));
		}
		return clean(b.name) + '\t' + b.kind + '\t' + clean(b.needs) + '\t' + sb;
	}

	private static String clean(String s)
	{
		return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}
}

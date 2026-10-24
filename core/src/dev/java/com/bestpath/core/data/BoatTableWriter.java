package com.bestpath.core.data;

import java.util.Set;

/**
 * Writes the boat varbit file ({@value PluginData#BOATS}) that {@link BoatTable#parse} reads (used when the data is
 * made).
 */
public final class BoatTableWriter
{
	private BoatTableWriter()
	{
	}

	/** The text form, read back by {@link BoatTable#parse}; {@code header} lines become {@code #} comments. */
	public static String write(BoatTable table, String header)
	{
		StringBuilder sb = new StringBuilder();
		for (String h : header.split("\n"))
		{
			sb.append("# ").append(h).append('\n');
		}
		sb.append("# type <id> <kind RAFT|SKIFF|SLOOP|-> <name> <hull tiers> <sail tiers> <keel tiers> <helm tiers> <brazier values>"
			+ " <inoculation hotspot:value>\n");
		sb.append("# port <value> <label> | name <part 1-3> <word for 0> <word for 1> ... | dock <id> <name>\n");
		for (BoatTable.Kind k : table.kinds.values())
		{
			sb.append(line(k)).append('\n');
		}
		table.ports.forEach((v, label) -> sb.append("port\t").append(v).append('\t').append(TransportLine.clean(label)).append('\n'));
		for (int p = 0; p < table.nameWords.size(); p++)
		{
			sb.append("name\t").append(p + 1);
			for (String w : table.nameWords.get(p))
			{
				sb.append('\t').append(TransportLine.clean(w));
			}
			sb.append('\n');
		}
		table.docks.forEach((id, name) -> sb.append("dock\t").append(id).append('\t').append(TransportLine.clean(name)).append('\n'));
		return sb.toString();
	}

	private static String line(BoatTable.Kind k)
	{
		StringBuilder ino = new StringBuilder();
		k.inoculation.forEach((h, vs) ->
		{
			for (int v : vs)
			{
				ino.append(ino.length() == 0 ? "" : ",").append(h).append(':').append(v);
			}
		});
		return "type\t" + k.id + '\t' + (k.type == null ? "-" : k.type.name()) + '\t' + TransportLine.clean(k.name) + '\t'
			+ list(k.hulls) + '\t' + list(k.sails) + '\t' + list(k.keels) + '\t' + list(k.helms) + '\t'
			+ (k.braziers.isEmpty() ? "-" : join(k.braziers)) + '\t' + (ino.length() == 0 ? "-" : ino);
	}

	private static String list(int[] a)
	{
		if (a.length == 0)
		{
			return "-";
		}
		StringBuilder sb = new StringBuilder();
		for (int v : a)
		{
			sb.append(sb.length() == 0 ? "" : ",").append(v);
		}
		return sb.toString();
	}

	private static String join(Set<Integer> s)
	{
		StringBuilder sb = new StringBuilder();
		for (int v : s)
		{
			sb.append(sb.length() == 0 ? "" : ",").append(v);
		}
		return sb.toString();
	}
}

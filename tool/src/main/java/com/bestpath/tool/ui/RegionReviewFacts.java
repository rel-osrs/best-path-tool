package com.bestpath.tool.ui;

import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.geo.Regions;
import com.bestpath.tool.transport.CacheTableTeleports;
import com.bestpath.tool.transport.TransportCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "Found here" lines of the Region review tab: what the tool itself found in a region, per category (a help for
 * checking, not a verdict).
 */
final class RegionReviewFacts
{
	private RegionReviewFacts()
	{
	}

	/** Undecided candidates (per plane), confirmed transports, teleport landings and test-list entries in the region. */
	static String transports(EditorContext ctx, int region)
	{
		Map<String, TransportRecord> testLater = ctx.effectiveTestLater();
		int open = 0;
		int[] perPlane = new int[Regions.PLANES];
		for (TransportCandidate c : ctx.transports.getCandidates())
		{
			if (in(region, c.getX(), c.getY()) && !ctx.decided(c, testLater))
			{
				open++;
				perPlane[c.getPlane()]++;
			}
		}
		int confirmed = 0;
		int pending = 0;
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			TransportRecord r = t.record;
			if (!t.deleted && ((r.hasStart() && in(region, r.startX, r.startY)) || in(region, r.endX, r.endY)))
			{
				confirmed++;
				pending += t.pending ? 1 : 0;
			}
		}
		int teleports = 0;
		for (CacheTableTeleports.Destination d : ctx.transports.getDestinations())
		{
			teleports += in(region, d.x, d.y) ? 1 : 0;
		}
		int test = 0;
		for (TransportRecord r : testLater.values())
		{
			test += r != null && ((r.hasStart() && in(region, r.startX, r.startY)) || in(region, r.endX, r.endY)) ? 1 : 0;
		}
		return String.format(Locale.ROOT, "Found here: %d suggested, not decided%s · %d confirmed starting or ending here%s"
				+ " · %d unconfirmed teleport destinations · %d on the test-in-game list",
			open, open > 0 ? " (" + planes(perPlane) + ")" : "", confirmed, pending > 0 ? " (" + pending + " pending)" : "", teleports, test);
	}

	/** The region's transport objects that change with a varbit or varp (the first six by name). */
	static String varbits(EditorContext ctx, int region)
	{
		List<String> names = new ArrayList<>();
		int count = 0;
		for (TransportCandidate c : ctx.transports.getAllCandidates())
		{
			if (in(region, c.getX(), c.getY()) && (c.getVarbit() >= 0 || c.getVarp() >= 0))
			{
				count++;
				if (names.size() < 6)
				{
					names.add(String.format(Locale.ROOT, "%s (%s %d, plane %d)", c.getName(),
						c.getVarbit() >= 0 ? "varbit" : "varp", c.getVarbit() >= 0 ? c.getVarbit() : c.getVarp(), c.getPlane()));
				}
			}
		}
		return "Found here: " + count + " transport objects that change with a varbit/varp"
			+ (names.isEmpty() ? "" : ": " + String.join(", ", names) + (count > names.size() ? ", ..." : ""))
			+ " (only transport objects are checked; other objects are not listed)";
	}

	static String items()
	{
		return "Nothing found automatically: the data has no item requirements yet. Use the notes.";
	}

	/** Per plane: the number of sub-regions, and how many are pockets or hidden. */
	static String subRegions(EditorContext ctx, int region)
	{
		boolean[] hidden = ctx.effectiveHidden();
		boolean[] pocketFlags = ctx.effectivePockets();
		StringBuilder sb = new StringBuilder("Found here: ");
		boolean any = false;
		for (int p = 0; p < Regions.PLANES; p++)
		{
			int base = ctx.graph.cellComponentBase(region, p);
			int n = maxLabel(ctx.graph.cellLabels(region, p));
			if (base < 0 || n == 0)
			{
				continue;
			}
			int pockets = 0;
			int hid = 0;
			for (int i = 0; i < n; i++)
			{
				int comp = base + i;
				pockets += pocketFlags != null && comp < pocketFlags.length && pocketFlags[comp] ? 1 : 0;
				hid += hidden != null && comp < hidden.length && hidden[comp] ? 1 : 0;
			}
			sb.append(any ? " · " : "").append("plane ").append(p).append(": ").append(n);
			if (pockets > 0 || hid > 0)
			{
				sb.append(" (").append(pockets).append(" pockets, ").append(hid).append(" hidden)");
			}
			any = true;
		}
		if (!any)
		{
			sb.append("no walkable sub-regions on any plane");
		}
		return sb.toString();
	}

	/** The highest sub-region label of a region's plane (its number of sub-regions); 0 for none. */
	static int maxLabel(short[] labels)
	{
		int max = 0;
		if (labels != null)
		{
			for (short l : labels)
			{
				max = Math.max(max, l);
			}
		}
		return max;
	}

	private static boolean in(int region, int x, int y)
	{
		return Regions.idOfTile(x, y) == region;
	}

	private static String planes(int[] perPlane)
	{
		List<String> parts = new ArrayList<>();
		for (int p = 0; p < perPlane.length; p++)
		{
			if (perPlane[p] > 0)
			{
				parts.add("plane " + p + ": " + perPlane[p]);
			}
		}
		return String.join(", ", parts);
	}
}

package com.bestpath.tool.ui;

import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.geo.Regions;
import com.bestpath.tool.transport.CacheTableTeleports;
import com.bestpath.tool.transport.Suggestion;
import com.bestpath.tool.transport.TransportCandidate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the {@link RemoteLink} reports about regions: the facts file of one region (its sub-regions, candidates,
 * transports, teleport landings, test list), the walking groups, and the Region review list.
 */
final class RemoteLinkFacts
{
	private RemoteLinkFacts()
	{
	}

	/** The Region review list as the tab shows it by default: no ignored regions, none with nowhere to walk. */
	static List<Integer> reviewList(EditorContext ctx, Set<Integer> regionIds)
	{
		Set<Integer> ignored = ctx.effectiveIgnoredRegions();
		List<Integer> out = new ArrayList<>();
		for (int id : new TreeSet<>(regionIds))
		{
			if (ignored.contains(id) || ctx.graph == null)
			{
				continue;
			}
			boolean walk = false;
			for (int p = 0; p < Regions.PLANES && !walk; p++)
			{
				short[] labels = ctx.graph.cellComponentBase(id, p) >= 0 ? ctx.graph.cellLabels(id, p) : null;
				for (int k = 0; labels != null && k < labels.length && !walk; k++)
				{
					walk = labels[k] > 0;
				}
			}
			if (walk)
			{
				out.add(id);
			}
		}
		return out;
	}

	/** Per component, a representative of its group (joined by walking entrances or confirmed transports). */
	static int[] groups(EditorContext ctx)
	{
		int n = ctx.graph.componentCount();
		int[] parent = new int[n];
		for (int c = 0; c < n; c++)
		{
			parent[c] = c;
		}
		for (int e = 0; e < ctx.graph.entranceCount(); e++)
		{
			union(parent, ctx.graph.entranceFrom(e), ctx.graph.entranceTo(e));
		}
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			TransportRecord r = t.record;
			if (!t.deleted && r.hasStart())
			{
				int a = ctx.graph.componentAt(r.startX, r.startY, r.startPlane);
				int b = ctx.graph.componentAt(r.endX, r.endY, r.endPlane);
				if (a >= 0 && b >= 0)
				{
					union(parent, a, b);
				}
			}
		}
		for (int c = 0; c < n; c++)
		{
			parent[c] = find(parent, c);
		}
		return parent;
	}

	/** Groups reached by any candidate (its standing tile or suggested ends), transport end, teleport landing or test-list entry. */
	private static Set<Integer> touchedGroups(EditorContext ctx, int[] group)
	{
		Set<Integer> out = new HashSet<>();
		java.util.function.Consumer<int[]> add = t ->
		{
			int c = t == null ? -1 : ctx.graph.componentAt(t[0], t[1], t[2]);
			if (c >= 0)
			{
				out.add(group[c]);
			}
		};
		for (TransportCandidate c : ctx.transports.getAllCandidates())
		{
			add.accept(ctx.standingTile(c, c.getX(), c.getY()));
			for (Suggestion s : ctx.transports.suggestionsFor(c))
			{
				add.accept(new int[]{s.getDestX(), s.getDestY(), s.getDestPlane()});
				add.accept(s.getStart());
			}
		}
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			if (!t.deleted)
			{
				add.accept(t.record.hasStart() ? new int[]{t.record.startX, t.record.startY, t.record.startPlane} : null);
				add.accept(new int[]{t.record.endX, t.record.endY, t.record.endPlane});
			}
		}
		for (CacheTableTeleports.Destination d : ctx.transports.getDestinations())
		{
			add.accept(new int[]{d.x, d.y, d.plane});
		}
		for (TransportRecord r : ctx.effectiveTestLater().values())
		{
			if (r != null)
			{
				add.accept(r.hasStart() ? new int[]{r.startX, r.startY, r.startPlane} : null);
				add.accept(new int[]{r.endX, r.endY, r.endPlane});
			}
		}
		return out;
	}

	private static int find(int[] parent, int c)
	{
		while (parent[c] != c)
		{
			parent[c] = parent[parent[c]];
			c = parent[c];
		}
		return c;
	}

	private static void union(int[] parent, int a, int b)
	{
		parent[find(parent, a)] = find(parent, b);
	}

	/** Everything the tool knows about a region's transports, as plain text. */
	static String facts(EditorContext ctx, int region)
	{
		if (ctx.transports == null || ctx.graph == null)
		{
			return "no data (open a cache first)\n";
		}
		StringBuilder sb = new StringBuilder();
		int bx = Regions.baseX(region);
		int by = Regions.baseY(region);
		sb.append(String.format(Locale.ROOT, "Region %d tiles %d-%d, %d-%d%s%n", region, bx, bx + 63, by, by + 63,
			ctx.effectiveIgnoredRegions().contains(region) ? " IGNORED" : ""));
		subRegions(sb, ctx, region);
		Map<String, TransportRecord> testLater = ctx.effectiveTestLater();
		candidates(sb, ctx, region, testLater);
		transports(sb, ctx, region);
		sb.append("Unconfirmed teleport destinations here:\n");
		for (CacheTableTeleports.Destination d : ctx.transports.getDestinations())
		{
			if (Regions.idOfTile(d.x, d.y) == region)
			{
				sb.append(String.format(Locale.ROOT, "  %s at %d,%d,%d network=%s source=%s%n", d.label(), d.x, d.y, d.plane, d.network, d.source));
			}
		}
		sb.append("Test-in-game list here:\n");
		for (Map.Entry<String, TransportRecord> e : testLater.entrySet())
		{
			TransportRecord r = e.getValue();
			if (r != null && ((r.hasStart() && Regions.idOfTile(r.startX, r.startY) == region) || Regions.idOfTile(r.endX, r.endY) == region))
			{
				sb.append("  ").append(e.getKey()).append(' ').append(r.name).append(' ').append(r.describe()).append('\n');
			}
		}
		return sb.toString();
	}

	/** The region's sub-regions per plane: size, a tile, its walking group, pocket / hidden / touched. */
	private static void subRegions(StringBuilder sb, EditorContext ctx, int region)
	{
		int bx = Regions.baseX(region);
		int by = Regions.baseY(region);
		boolean[] hidden = ctx.effectiveHidden();
		boolean[] pockets = ctx.effectivePockets();
		int[] group = groups(ctx);
		long[] groupTiles = new long[group.length];
		for (int c = 0; c < group.length; c++)
		{
			groupTiles[group[c]] += ctx.graph.componentSize(c);
		}
		Set<Integer> touched = touchedGroups(ctx, group);
		sb.append("(group = sub-regions joined by walking or a confirmed transport; tiles = the group's size;"
			+ " TOUCHED = a candidate, transport, teleport landing or test-list entry reaches the group)\n");
		for (int p = 0; p < Regions.PLANES; p++)
		{
			int base = ctx.graph.cellComponentBase(region, p);
			short[] labels = ctx.graph.cellLabels(region, p);
			if (base < 0 || labels == null)
			{
				continue;
			}
			int n = 0;
			for (short l : labels)
			{
				n = Math.max(n, l);
			}
			int[] size = new int[n + 1];
			int[][] first = new int[n + 1][];
			for (int t = 0; t < labels.length; t++)
			{
				int l = labels[t];
				if (l > 0)
				{
					size[l]++;
					if (first[l] == null)
					{
						first[l] = new int[]{bx + (t >> 6), by + (t & 63)}; // index localX << 6 | localY
					}
				}
			}
			for (int l = 1; l <= n; l++)
			{
				int comp = base + l - 1;
				sb.append(String.format(Locale.ROOT, "  sub-region plane %d #%d: %d tiles, e.g. %d,%d, group %d (%,d tiles)%s%s", p, l, size[l],
					first[l] == null ? -1 : first[l][0], first[l] == null ? -1 : first[l][1], group[comp], groupTiles[group[comp]],
					pockets != null && comp < pockets.length && pockets[comp] ? " POCKET" : "",
					hidden != null && comp < hidden.length && hidden[comp] ? " HIDDEN" : "")
					+ (touched.contains(group[comp]) ? " TOUCHED" : "") + "\n");
			}
		}
	}

	/** The region's transport candidates, whether each is decided, and their best three suggestions. */
	private static void candidates(StringBuilder sb, EditorContext ctx, int region, Map<String, TransportRecord> testLater)
	{
		sb.append("Candidates (objects that look like transports):\n");
		Set<TransportCandidate> open = new HashSet<>(ctx.transports.getCandidates());
		for (TransportCandidate c : ctx.transports.getAllCandidates())
		{
			if (Regions.idOfTile(c.getX(), c.getY()) != region)
			{
				continue;
			}
			String state = !open.contains(c) ? "already decided (confirmed or rejected)"
				: testLater.containsKey(c.id()) ? "on test list" : ctx.decided(c, testLater) ? "decided, not built" : "UNDECIDED";
			sb.append(String.format(Locale.ROOT, "  %s %d '%s' at %d,%d,%d size %dx%d %s options=%s%s%s [%s]%n", c.getCategory(), c.getObjectId(),
				c.getName(), c.getX(), c.getY(), c.getPlane(), c.getSizeX(), c.getSizeY(), "shape" + c.getShapeType(), c.getOptions(),
				c.getVarbit() >= 0 ? " varbit " + c.getVarbit() : "", c.getVarp() >= 0 ? " varp " + c.getVarp() : "", state));
			List<Suggestion> sug = ctx.transports.suggestionsFor(c);
			for (int k = 0; k < Math.min(3, sug.size()); k++)
			{
				Suggestion s = sug.get(k);
				int[] st = s.getStart();
				sb.append(String.format(Locale.ROOT, "      suggest %s -> %d,%d,%d%s partner=%s score=%d evidence=%s%n", s.getRule(),
					s.getDestX(), s.getDestY(), s.getDestPlane(), st == null ? "" : " from " + st[0] + "," + st[1] + "," + st[2],
					s.getPartner(), s.getScore(), s.getEvidence()));
			}
		}
	}

	/** The confirmed transports that start or end in the region. */
	private static void transports(StringBuilder sb, EditorContext ctx, int region)
	{
		sb.append("Confirmed transports starting or ending here:\n");
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			TransportRecord r = t.record;
			if (!t.deleted && ((r.hasStart() && Regions.idOfTile(r.startX, r.startY) == region) || Regions.idOfTile(r.endX, r.endY) == region))
			{
				sb.append(String.format(Locale.ROOT, "  %s %s [%s, %s] %s obj=%d@%d,%d,%d src=%s net=%s%s%n", r.id, r.name, r.category, r.kind,
					r.describe(), r.objectId, r.objectX, r.objectY, r.objectPlane, r.source, r.network, (t.pending ? " (pending)" : "") + r.requirementNote()));
			}
		}
	}
}

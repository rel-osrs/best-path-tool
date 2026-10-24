package com.bestpath.tool.ui;

import static com.bestpath.tool.ui.RemoteLink.num;

import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.req.CacheNames;
import com.bestpath.tool.req.ItemGroupFile;
import com.bestpath.tool.req.Networks;
import com.bestpath.tool.transport.CacheTableTeleports;
import com.bestpath.tool.transport.TransportCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@link RemoteLink} commands about transports: showing, confirming, rejecting and making them, changing a
 * copy ({@code amend}), deleting, the test list, the networks file, and what the cache says about an object.
 */
final class RemoteLinkTransports
{
	private RemoteLinkTransports()
	{
	}

	/** Runs a transport command; its reply, or null when {@code w[0]} is not one. */
	static String run(EditorContext ctx, String[] w, String line)
	{
		switch (w[0])
		{
			case "record":
				return record(ctx, w);
			case "candidate":
				return candidate(ctx, w);
			case "reject":
				return reject(ctx, w);
			case "confirm":
				return confirm(ctx, w);
			case "objlink":
				return objectLink(ctx, w, line);
			case "transport":
				return transport(ctx, line);
			case "options":
				return options(ctx, w);
			case "stand":
				return stand(ctx, w);
			case "facing":
				return facing(ctx, w);
			case "amend":
				return amend(ctx, w, line);
			case "network":
				return network(line);
			case "suggestions":
				return suggestions(ctx, line);
			case "delete":
				return delete(ctx, w);
			case "testlater":
				return testLater(ctx, w);
			default:
				return null;
		}
	}

	/** record &lt;transport id&gt;: selects the transport and shows it. */
	private static String record(EditorContext ctx, String[] w)
	{
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			if (t.record.id.equals(w[1]) && !t.deleted)
			{
				ctx.showRecord(t.record);
				return "ok";
			}
		}
		return "no such transport";
	}

	/** candidate objectId x y p: selects the candidate (decided or not) and shows it. */
	private static String candidate(EditorContext ctx, String[] w)
	{
		if (ctx.transports == null)
		{
			return "no data";
		}
		for (TransportCandidate c : ctx.transports.getAllCandidates())
		{
			if (at(c, w, 1))
			{
				ctx.showCandidate(c);
				return "ok";
			}
		}
		return "no such candidate";
	}

	/** reject objectId x y p: like Delete on that undecided candidate. */
	private static String reject(EditorContext ctx, String[] w)
	{
		if (ctx.transports == null)
		{
			return "no data";
		}
		for (TransportCandidate c : ctx.transports.getCandidates())
		{
			if (at(c, w, 1))
			{
				ctx.select(ctx.candidateSelection(c), null, false);
				ctx.deleteSelected();
				return "rejected";
			}
		}
		return "no undecided candidate there";
	}

	/** True when the candidate is the object {@code w[k]} at {@code w[k + 1..k + 3]} (x, y, plane). */
	private static boolean at(TransportCandidate c, String[] w, int k)
	{
		return c.getObjectId() == num(w[k]) && c.getX() == num(w[k + 1]) && c.getY() == num(w[k + 2]) && c.getPlane() == num(w[k + 3]);
	}

	/** confirm objectId x y p: like selecting the candidate and pressing C: its best suggestion becomes a transport. */
	private static String confirm(EditorContext ctx, String[] w)
	{
		TransportCandidate c = undecided(ctx, num(w[1]), num(w[2]), num(w[3]), num(w[4]));
		if (c == null)
		{
			return "no undecided candidate there";
		}
		ctx.select(ctx.candidateSelection(c), null, false);
		ctx.confirmSelected();
		EditorContext.Selection s = ctx.getSelection();
		return s == null || s.record == null ? "no suggestion to confirm" : ends(ctx, s.record);
	}

	/**
	 * objlink objectId x y p objectId x y p [| source]: a transport from one object to the tile next to another (e.g. a
	 * cave entrance and its exit); the exit is rejected as its own suggestion
	 */
	private static String objectLink(EditorContext ctx, String[] w, String line)
	{
		TransportCandidate a = undecided(ctx, num(w[1]), num(w[2]), num(w[3]), num(w[4]));
		TransportCandidate b = undecided(ctx, num(w[5]), num(w[6]), num(w[7]), num(w[8]));
		if (a == null || b == null)
		{
			return "candidate not found: " + (a == null ? "first" : "second");
		}
		int[] end = ctx.standingTile(b, b.getX(), b.getY());
		int[] start = ctx.standingTile(a, a.getX(), a.getY());
		if (end == null || start == null)
		{
			return "no usable tile next to " + (end == null ? "second" : "first");
		}
		String source = line.contains("|") ? line.substring(line.indexOf('|') + 1).trim() : "manual: object link";
		TransportRecord r = ctx.recordFromCandidate(a, start, end[0], end[1], end[2], source);
		ctx.pendTransport(r);
		ctx.select(ctx.candidateSelection(b), null, false);
		ctx.deleteSelected();
		return ends(ctx, r);
	}

	/** transport &lt;TWO_WAY|ONE_WAY&gt; &lt;category&gt; &lt;objectId|-1&gt; ox oy op sx sy sp ex ey ep ticks | name | source */
	private static String transport(EditorContext ctx, String line)
	{
		String[] parts = line.split("\\|");
		String[] h = parts[0].trim().split("\\s+");
		TransportRecord r = new TransportRecord();
		r.id = com.bestpath.tool.edit.EditStore.newId("t");
		r.kind = TransportRecord.Kind.valueOf(h[1]);
		r.category = h[2];
		r.objectId = num(h[3]);
		r.objectX = num(h[4]);
		r.objectY = num(h[5]);
		r.objectPlane = num(h[6]);
		r.startX = num(h[7]);
		r.startY = num(h[8]);
		r.startPlane = num(h[9]);
		r.endX = num(h[10]);
		r.endY = num(h[11]);
		r.endPlane = num(h[12]);
		r.ticks = num(h[13]);
		r.ticksEstimate = true;
		r.name = parts.length > 1 ? parts[1].trim() : "Custom transport";
		r.source = parts.length > 2 ? parts[2].trim() : "manual";
		ctx.pendTransport(r);
		return ends(ctx, r);
	}

	/** options objectId x y p [objectId x y p ...]: the cache's menu options of each object (decided or not), e.g. for the Shortest Path export */
	private static String options(EditorContext ctx, String[] w)
	{
		if (ctx.transports == null)
		{
			return "no data";
		}
		StringBuilder sb = new StringBuilder();
		for (int k = 1; k + 3 < w.length; k += 4)
		{
			String found = "?";
			for (TransportCandidate c : ctx.transports.getAllCandidates())
			{
				if (at(c, w, k))
				{
					found = String.join(",", c.getOptions()).replace(' ', '_');
					break;
				}
			}
			sb.append(found.isEmpty() ? "?" : found).append(' ');
		}
		return sb.toString().trim();
	}

	/** stand objectId x y p towardX towardY: where a transport at this object (decided or not) would start, and whether that tile is usable */
	private static String stand(EditorContext ctx, String[] w)
	{
		for (TransportCandidate c : ctx.transports.getAllCandidates())
		{
			if (at(c, w, 1))
			{
				int[] s = ctx.standingTile(c, num(w[5]), num(w[6]));
				return s == null ? "none" : s[0] + "," + s[1] + "," + s[2] + " usable=" + ctx.usable(s[0], s[1], s[2], false);
			}
		}
		return "no such candidate";
	}

	/** facing objectId x y p: the tiles beside the object (no wall between) a player can use it from */
	private static String facing(EditorContext ctx, String[] w)
	{
		for (TransportCandidate c : ctx.transports.getAllCandidates())
		{
			if (at(c, w, 1))
			{
				StringBuilder sb = new StringBuilder();
				for (int[] t : ctx.facingTiles(c))
				{
					sb.append(t[0]).append(',').append(t[1]).append(',').append(t[2])
						.append(ctx.usable(t[0], t[1], t[2], false) ? "" : "(pocket/hidden)").append(' ');
				}
				return sb.length() == 0 ? "none" : sb.toString().trim();
			}
		}
		return "no such candidate";
	}

	/**
	 * amend &lt;transport id&gt; ticks &lt;n&gt; | start x y p | end x y p | source &lt;text&gt; | name &lt;text&gt; | object &lt;id x y p or none&gt; |
	 * require &lt;line or none&gt; | requireback &lt;line, none or same&gt; | reqsource &lt;text&gt; | questonly on/off | canfail on/off |
	 * tpcategory &lt;name|none&gt; | wilderness &lt;n|none&gt; | cooldown on/off | playernote &lt;text|none&gt; | network &lt;name|none&gt; |
	 * suggestion &lt;id|none&gt;: a changed copy of the transport, built in at the next data build
	 */
	private static String amend(EditorContext ctx, String[] w, String line)
	{
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			if (!t.record.id.equals(w[1]) || t.deleted)
			{
				continue;
			}
			TransportRecord r = t.record.copy();
			StringBuilder warning = new StringBuilder();
			String error = amendField(ctx, r, w, line, warning);
			if (error != null)
			{
				return error;
			}
			ctx.pendTransport(r);
			return ends(ctx, r) + warning;
		}
		return "no such transport";
	}

	/** Changes field {@code w[2]} of the copy {@code r}; an error reply, or null when it was changed. */
	private static String amendField(EditorContext ctx, TransportRecord r, String[] w, String line, StringBuilder warning)
	{
		switch (w[2])
		{
			case "ticks":
				r.ticks = num(w[3]);
				r.ticksEstimate = false;
				return null;
			case "start":
				r.startX = num(w[3]);
				r.startY = num(w[4]);
				r.startPlane = num(w[5]);
				return null;
			case "end":
				r.endX = num(w[3]);
				r.endY = num(w[4]);
				r.endPlane = num(w[5]);
				return null;
			case "source":
				r.source = line.split("\\s+", 4)[3];
				return null;
			case "name":
				r.name = line.trim().split("\\s+", 4)[3].trim();
				return null;
			case "object":
				amendObject(r, w);
				return null;
			case "require":
			case "requireback":
				return amendRequirement(r, w, line, warning);
			case "reqsource":
				r.requirementsSource = line.trim().split("\\s+", 4).length > 3 ? line.trim().split("\\s+", 4)[3] : "";
				return null;
			case "questonly":
				r.questOnly = w[3].equals("on");
				return null;
			case "canfail":
				r.canFail = w[3].equals("on");
				return null;
			case "tpcategory":
				return amendTeleportCategory(r, line);
			case "wilderness":
				// wilderness <deepest level, 0 = not in the Wilderness> | wilderness none
				r.wildernessLimit = w[3].equals("none") ? null : Integer.valueOf(num(w[3]));
				return null;
			case "cooldown":
				r.cooldown = w[3].equals("on");
				return null;
			case "playernote":
			{
				// playernote <text the plugin shows the player on this step> | playernote none
				String text = fieldText(line);
				r.playerNote = text.equals("none") ? "" : text;
				return null;
			}
			case "network":
				return amendNetwork(r, line);
			case "suggestion":
				return amendSuggestion(ctx, r, line);
			default:
				return "unknown field";
		}
	}

	/** The text after "amend &lt;id&gt; &lt;field&gt;", or "". */
	private static String fieldText(String line)
	{
		return line.trim().split("\\s+", 4).length > 3 ? line.trim().split("\\s+", 4)[3].trim() : "";
	}

	/** object &lt;id&gt; x y p | object none: the object the transport is used at. */
	private static void amendObject(TransportRecord r, String[] w)
	{
		if (w[3].equals("none"))
		{
			r.objectId = -1;
			r.objectX = 0;
			r.objectY = 0;
			r.objectPlane = 0;
			return;
		}
		r.objectId = num(w[3]);
		r.objectX = num(w[4]);
		r.objectY = num(w[5]);
		r.objectPlane = num(w[6]);
	}

	/** require &lt;line&gt; | requireback &lt;line&gt;|same; "none" = nothing needed. */
	private static String amendRequirement(TransportRecord r, String[] w, String line, StringBuilder warning)
	{
		String[] parts = line.trim().split("\\s+", 4);
		String text = parts.length > 3 ? parts[3].trim() : "";
		if (w[2].equals("requireback") && text.equals("same"))
		{
			r.requirementsBack = null;
			return null;
		}
		if (w[2].equals("requireback") && r.kind != TransportRecord.Kind.TWO_WAY)
		{
			return "requireback: not a two-way transport";
		}
		try
		{
			String parsed = parseRequirement(text, warning);
			if (w[2].equals("require"))
			{
				r.requirements = parsed;
			}
			else
			{
				r.requirementsBack = parsed;
			}
		}
		catch (RequirementText.ParseException e)
		{
			return "bad requirement: " + e.getMessage();
		}
		return null;
	}

	/** A requirement line ("none" = nothing needed) in its tidy written form; each unknown name adds a warning. */
	private static String parseRequirement(String text, StringBuilder warning) throws RequirementText.ParseException
	{
		Requirement req = ItemGroupFile.parse(CacheNames.tidyQuestNames(text.equals("none") ? "" : text));
		String parsed = req.toText();
		for (String q : CacheNames.unknownNames(req))
		{
			warning.append(" WARNING unknown ").append(q);
		}
		return parsed;
	}

	/** tpcategory &lt;category name&gt; | tpcategory none */
	private static String amendTeleportCategory(TransportRecord r, String line)
	{
		String cat = fieldText(line);
		if (!cat.equals("none") && com.bestpath.tool.req.TeleportCategories.get(cat) == null)
		{
			return "unknown teleport category '" + cat + "'";
		}
		r.teleportCategory = cat.equals("none") ? "" : com.bestpath.tool.req.TeleportCategories.get(cat).name;
		return null;
	}

	/** network &lt;name in data/manual/networks.tsv&gt; | network none */
	private static String amendNetwork(TransportRecord r, String line)
	{
		String net = fieldText(line);
		if (!net.equals("none") && Networks.get(net) == null)
		{
			return "unknown network '" + net + "' (add it with: network <name> | <extra ticks> | <requirement> | <description>)";
		}
		r.network = net.equals("none") ? "" : Networks.get(net).name;
		return null;
	}

	/**
	 * suggestion &lt;suggested teleport / network stop id, e.g. teleport:Fairy ring:AJR&gt; | suggestion none: the pink dot
	 * this transport decides
	 */
	private static String amendSuggestion(EditorContext ctx, TransportRecord r, String line)
	{
		String id = fieldText(line);
		if (id.equals("none"))
		{
			r.suggestionId = null;
			return null;
		}
		boolean known = false;
		for (CacheTableTeleports.Destination d : ctx.transports.getDestinations())
		{
			known |= d.id().equals(id);
		}
		if (!known)
		{
			return "no suggested teleport or network stop '" + id + "'";
		}
		r.suggestionId = id;
		return null;
	}

	/** network &lt;name&gt; | &lt;extra ticks or keep&gt; | &lt;requirement for every ride, or none&gt; | &lt;description&gt;: adds or changes a line of data/manual/networks.tsv */
	private static String network(String line)
	{
		String[] parts = splitOutsideBrackets(line);
		if (parts.length < 4 || parts[0].trim().isEmpty())
		{
			return "network <name> | <extra ticks or keep> | <requirement or none> | <description>";
		}
		String name = parts[0].trim();
		Networks.Network old = Networks.get(name);
		int extra;
		if (parts[1].trim().equals("keep"))
		{
			extra = old == null ? 0 : old.extraTicks;
		}
		else
		{
			extra = num(parts[1].trim());
		}
		String text = parts[2].trim();
		StringBuilder warning = new StringBuilder();
		String parsed;
		try
		{
			parsed = parseRequirement(text, warning);
		}
		catch (RequirementText.ParseException e)
		{
			return "bad requirement: " + e.getMessage();
		}
		String description = String.join("|", java.util.Arrays.copyOfRange(parts, 3, parts.length)).trim();
		try
		{
			Networks.put(new Networks.Network(old == null ? name : old.name, extra, parsed, description));
		}
		catch (java.io.IOException e)
		{
			return "could not save: " + e.getMessage();
		}
		Networks.Network now = Networks.get(name);
		return "network " + now.name + " extra=" + now.extraTicks + " needs: " + now.requirement + warning;
	}

	/** The parts of the line after its first word, split at the | outside brackets: "one of (A | B)" stays in one part. */
	private static String[] splitOutsideBrackets(String line)
	{
		List<String> split = new ArrayList<>();
		int depth = 0;
		int from = line.indexOf(' ') + 1;
		for (int k = from; k <= line.length(); k++)
		{
			char ch = k < line.length() ? line.charAt(k) : '|';
			depth += ch == '(' ? 1 : ch == ')' ? -1 : 0;
			if (ch == '|' && (depth <= 0 || k == line.length()))
			{
				split.add(line.substring(from, k));
				from = k + 1;
			}
		}
		return split.toArray(new String[0]);
	}

	/** suggestions [text]: the suggested teleports and network stops (pink dots) whose id contains the text, with their tiles */
	private static String suggestions(EditorContext ctx, String line)
	{
		String text = line.trim().split("\\s+", 2).length > 1 ? line.trim().split("\\s+", 2)[1].trim().toLowerCase(Locale.ROOT) : "";
		StringBuilder sb = new StringBuilder();
		for (CacheTableTeleports.Destination d : ctx.transports.getDestinations())
		{
			if (d.id().toLowerCase(Locale.ROOT).contains(text))
			{
				sb.append(String.format(Locale.ROOT, "%n  %s at %d,%d,%d %s (%s)", d.id(), d.x, d.y, d.plane,
					d.isNetwork ? "network stop" : "teleport", d.source));
			}
		}
		return sb.length() == 0 ? "none" : sb.toString();
	}

	/** delete &lt;transport id&gt;: like selecting the transport and pressing Delete (one not built in yet is taken back) */
	private static String delete(EditorContext ctx, String[] w)
	{
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			if (t.record.id.equals(w[1]) && !t.deleted)
			{
				ctx.select(EditorContext.Selection.ofRecord(t.record, t.pending), null, false);
				ctx.deleteSelected();
				return "deleted";
			}
		}
		return "no such transport";
	}

	/**
	 * testlater &lt;transport id&gt;: like selecting a built transport and pressing "Test in game later" (it stays
	 * built and goes on the test list), e.g. for an approval card answered NO
	 */
	private static String testLater(EditorContext ctx, String[] w)
	{
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			if (t.record.id.equals(w[1]) && !t.deleted)
			{
				if (t.pending)
				{
					return "not built in yet: try again once the data has caught up";
				}
				ctx.select(EditorContext.Selection.ofRecord(t.record, false), null, false);
				ctx.testLaterSelected();
				return "on the test list";
			}
		}
		return "no such transport";
	}

	private static TransportCandidate undecided(EditorContext ctx, int objectId, int x, int y, int plane)
	{
		if (ctx.transports == null)
		{
			return null;
		}
		for (TransportCandidate c : ctx.transports.getCandidates())
		{
			if (c.getObjectId() == objectId && c.getX() == x && c.getY() == y && c.getPlane() == plane)
			{
				return c;
			}
		}
		return null;
	}

	/** A transport and whether a player can stand on both ends. */
	private static String ends(EditorContext ctx, TransportRecord r)
	{
		return r.id + " " + r.describe() + " startUsable=" + (!r.hasStart() || ctx.usable(r.startX, r.startY, r.startPlane, false))
			+ " endUsable=" + ctx.usable(r.endX, r.endY, r.endPlane, false) + r.requirementNote();
	}
}

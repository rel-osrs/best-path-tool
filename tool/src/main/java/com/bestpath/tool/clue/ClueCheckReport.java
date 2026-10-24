package com.bestpath.tool.clue;

import static com.bestpath.tool.clue.ClueCheckCli.key;

import com.bestpath.tool.clue.ClueCheckCli.Location;
import com.bestpath.tool.clue.ClueCheckCli.Outcome;
import com.bestpath.tool.clue.ClueCheckCli.Player;
import com.bestpath.tool.clue.ClueCheckCli.Start;
import com.bestpath.tool.clue.ClueCheckCli.TileResult;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The clue check's output ({@link ClueCheckCli}): {@code report.tsv} with one line per location, and the summary
 * with the steps reached per type and the locations not reached, by likely cause.
 */
final class ClueCheckReport
{
	private ClueCheckReport()
	{
	}

	/** Cause prefix for a location reached only through a suggestion (for a person to confirm; the data is unchanged). */
	static final String WITH_SUGGESTION = "reached only with a suggestion: ";

	static void writeReport(File f, List<Location> locs, Map<Long, TileResult> tiles, List<Player> players, List<Start> starts)
		throws java.io.IOException
	{
		StringBuilder sb = new StringBuilder("key\ttype\ttier\trole\tkind\tname\tx\ty\tplane\tspot\tnearestStandable\tnearestDistance\tstandablePlane"
			+ "\tfootprint\tsuggestion");
		for (Player p : players)
		{
			for (Start s : starts)
			{
				sb.append('\t').append(p.name).append(" from ").append(s.name).append(" (ticks/steps/stop)");
			}
		}
		sb.append("\tbestStop\tsuggestionBestStop\tcause\n");
		for (Location l : locs)
		{
			TileResult tr = tiles.get(key(l.x, l.y, l.plane));
			if (tr == null || tr.outcomes == null)
			{
				continue;
			}
			appendReportLine(sb, l, tr, tiles);
		}
		Files.writeString(f.toPath(), sb.toString(), StandardCharsets.UTF_8);
	}

	/** One location's line of the report. */
	private static void appendReportLine(StringBuilder sb, Location l, TileResult tr, Map<Long, TileResult> tiles)
	{
		sb.append(l.step.key).append('\t').append(l.step.type).append('\t').append(l.step.tier).append('\t').append(l.role).append('\t')
			.append(l.kind).append('\t').append(l.label.replace('\t', ' ')).append('\t').append(l.x).append('\t').append(l.y).append('\t')
			.append(l.plane).append('\t').append(tr.spot.name().toLowerCase(Locale.ROOT)).append('\t')
			.append(tr.nearest == null ? "-" : tr.nearest[0] + "," + tr.nearest[1] + "," + tr.nearest[2]).append('\t')
			.append(tr.nearest == null ? "-" : String.valueOf(tr.nearest[3])).append('\t')
			.append(tr.otherPlane < 0 ? "-" : String.valueOf(tr.otherPlane)).append('\t')
			.append(l.footprint == null ? "-" : l.footprint[0] + "," + l.footprint[1] + "-" + l.footprint[2] + "," + l.footprint[3]).append('\t')
			.append(l.suggestion == null ? "-" : l.suggestion.x + "," + l.suggestion.y + "," + l.suggestion.plane);
		for (Outcome[] row : tr.outcomes)
		{
			for (Outcome o : row)
			{
				sb.append('\t').append(o == null ? "-" : !o.found ? "none" : o.ticks + "/" + o.steps + "/" + l.distance(o) + (o.sailed ? "/sail" : ""));
			}
		}
		int best = bestStop(l, tr, 0);
		int sugg = suggestionBestStop(l, tiles, 0);
		String cause = cause(l, tr, tiles, 0);
		sb.append('\t').append(best < 0 ? "-" : String.valueOf(best)).append('\t').append(sugg < 0 ? "-" : String.valueOf(sugg)).append('\t')
			.append(cause == null ? "ok" : cause).append('\n');
	}

	/** The closest any start got for player {@code pi} to the location (to its object's footprint if it has one), or -1 without a route. */
	static int bestStop(Location l, TileResult tr, int pi)
	{
		int best = -1;
		for (Outcome o : tr.outcomes[pi])
		{
			int d = l.distance(o);
			if (d >= 0 && (best < 0 || d < best))
			{
				best = d;
			}
		}
		return best;
	}

	/** As {@link #bestStop}, for the location's suggested tile; -1 without a suggestion or a route there. */
	static int suggestionBestStop(Location l, Map<Long, TileResult> tiles, int pi)
	{
		if (l.suggestion == null)
		{
			return -1;
		}
		TileResult st = tiles.get(key(l.suggestion.x, l.suggestion.y, l.suggestion.plane));
		if (st == null || st.outcomes == null)
		{
			return -1;
		}
		Location at = new Location(l.step, l.role, l.kind, l.label, l.suggestion.x, l.suggestion.y, l.suggestion.plane);
		return bestStop(at, st, pi);
	}

	/**
	 * The likely cause for player {@code pi}, null when fine. A location reached
	 * only beside its blocked dig/emote tile, or only on the suggested plane, gets
	 * a cause starting with {@link #WITH_SUGGESTION}.
	 */
	static String cause(Location l, TileResult tr, Map<Long, TileResult> tiles, int pi)
	{
		int best = bestStop(l, tr, pi);
		String cause = ClueCheck.cause(l.kind, tr.spot, tr.nearest == null ? -1 : tr.nearest[3], best);
		if (cause == null)
		{
			return null;
		}
		if (l.suggestion != null && ClueCheck.closeEnough(l.kind, suggestionBestStop(l, tiles, pi)))
		{
			return WITH_SUGGESTION + l.suggestion.reason;
		}
		return cause;
	}

	static String summary(List<ClueStep> steps, List<Location> locs, Map<Long, TileResult> tiles, List<Player> players, List<Start> starts,
		double secs, int searches, int threads, int cacheId)
	{
		// per step: reached when its main place is close enough for the player (kill steps: any area)
		Map<String, List<String>> problems = new TreeMap<>();
		Map<String, List<String>> suggested = new TreeMap<>();
		Map<String, Boolean[]> stepOk = stepResults(locs, tiles, problems, suggested);
		Map<String, int[]> perType = countPerType(steps, stepOk);
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT, "Clue check on cache %d: %,d steps, %,d locations (%,d distinct tiles), %,d searches in %.0f s on %d threads.%n%n",
			cacheId, steps.size(), locs.size(), tiles.size(), searches, secs, threads));
		sb.append("Starts: ");
		for (Start s : starts)
		{
			sb.append(s.name).append(" ").append(s.x).append(",").append(s.y).append(",").append(s.plane).append(s.free ? "" : " (members only)").append("; ");
		}
		sb.append("\nPlayers: ");
		for (Player p : players)
		{
			sb.append(p.name).append(" (").append(p.transports.size()).append(" transports); ");
		}
		sb.append("\nA step counts as reached when, from at least one start, the route ends on its tile (dig/emote spots), within 1 tile")
			.append(" (objects, STASH units: of the object's whole footprint) or within 2 tiles (NPCs) of its target, or of any alternative")
			.append(" target / kill area (one tile per group of Wiki spawns). A dig/emote spot whose own tile is blocked on our map counts from")
			.append(" the tile next to it. data/clues/clue-overrides.tsv is applied (retired steps dropped, fixed tiles,")
			.append(" task-only and unreachable kill areas left out, kill-area planes and moved Wiki layers). The Maxed player also sails (three maxed boats at Port Sarim;")
			.append(" fresh players walk). \"With suggestions\" also counts a tile with no floor reached on the only other plane that")
			.append(" has floor there (to confirm).\n\n");
		appendTable(sb, perType);
		sb.append("Locations the maxed player can't reach (or only from too far), by likely cause:\n\n");
		appendGroups(sb, problems);
		sb.append("\nLocations the maxed player reaches only with a suggestion (to confirm; the clue data is unchanged):\n\n");
		appendGroups(sb, suggested);
		return sb.toString();
	}

	/**
	 * Per step key: {maxed, fresh, f2p, has a place, maxed with suggestions} reached; and each location the maxed player
	 * does not reach into {@code problems} (by cause) or {@code suggested} (by the suggestion's reason).
	 */
	private static Map<String, Boolean[]> stepResults(List<Location> locs, Map<Long, TileResult> tiles, Map<String, List<String>> problems,
		Map<String, List<String>> suggested)
	{
		Map<String, Boolean[]> stepOk = new LinkedHashMap<>(); // maxed, fresh, f2p, has a place, maxed with suggestions
		for (Location l : locs)
		{
			TileResult tr = tiles.get(key(l.x, l.y, l.plane));
			if (tr == null || tr.outcomes == null)
			{
				continue;
			}
			boolean main = l.role.equals("target") || l.role.equals("kill-area") || l.role.equals("alt-target");
			Boolean[] ok = stepOk.computeIfAbsent(l.step.key, k -> new Boolean[]{false, false, false, false, false});
			String cause = cause(l, tr, tiles, 0);
			if (main)
			{
				ok[3] = true;
				for (int pi = 0; pi < 3; pi++)
				{
					if (ClueCheck.reached(l.kind, tr.spot, bestStop(l, tr, pi)))
					{
						ok[pi] = true;
					}
				}
				if (ok[0] || cause != null && cause.startsWith(WITH_SUGGESTION))
				{
					ok[4] = true;
				}
			}
			if (cause != null)
			{
				addProblem(l, tr, cause, problems, suggested);
			}
		}
		return stepOk;
	}

	/** A location the maxed player does not reach, under its cause. */
	private static void addProblem(Location l, TileResult tr, String cause, Map<String, List<String>> problems,
		Map<String, List<String>> suggested)
	{
		String near = tr.nearest == null || tr.nearest[3] == 0 ? ""
			: "; nearest standable " + tr.nearest[0] + "," + tr.nearest[1] + "," + tr.nearest[2] + " (" + tr.nearest[3] + " away)";
		String other = tr.otherPlane < 0 ? "" : "; plane " + tr.otherPlane + " is standable at this x,y";
		String line = String.format(Locale.ROOT, "%s (%s %s, %s) %s at %d,%d,%d%s%s", l.step.key, l.step.tier, l.role, l.kind, l.label, l.x, l.y,
			l.plane, near, other);
		if (cause.startsWith(WITH_SUGGESTION))
		{
			suggested.computeIfAbsent(cause.substring(WITH_SUGGESTION.length()), k -> new ArrayList<>()).add(line);
		}
		else
		{
			problems.computeIfAbsent(cause, k -> new ArrayList<>()).add(line);
		}
	}

	/** Per step type: steps, with a place, maxed ok, maxed ok with suggestions, fresh ok, f2p-eligible, f2p ok. */
	private static Map<String, int[]> countPerType(List<ClueStep> steps, Map<String, Boolean[]> stepOk)
	{
		Map<String, int[]> perType = new TreeMap<>();
		for (ClueStep s : steps)
		{
			int[] c = perType.computeIfAbsent(s.type, k -> new int[7]);
			Boolean[] ok = stepOk.get(s.key);
			c[0]++;
			if (ok == null || !ok[3])
			{
				continue;
			}
			c[1]++;
			c[2] += ok[0] ? 1 : 0;
			c[3] += ok[4] ? 1 : 0;
			c[4] += ok[1] ? 1 : 0;
			if (s.tier.equals("beginner"))
			{
				c[5]++;
				c[6] += ok[2] ? 1 : 0;
			}
		}
		return perType;
	}

	/** The steps-reached table, a row per type and the totals. */
	private static void appendTable(StringBuilder sb, Map<String, int[]> perType)
	{
		sb.append("| Type | Steps | With a tile | Reached (maxed) | Reached (maxed, with suggestions) | Reached (fresh members) | Beginner steps | Reached (fresh F2P) |\n");
		sb.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
		int[] tot = new int[7];
		for (Map.Entry<String, int[]> e : perType.entrySet())
		{
			int[] c = e.getValue();
			sb.append("| ").append(e.getKey());
			for (int i = 0; i < 7; i++)
			{
				sb.append(" | ").append(c[i]);
				tot[i] += c[i];
			}
			sb.append(" |\n");
		}
		sb.append("| **all**");
		for (int v : tot)
		{
			sb.append(" | **").append(v).append("**");
		}
		sb.append(" |\n\n");
	}

	private static void appendGroups(StringBuilder sb, Map<String, List<String>> groups)
	{
		if (groups.isEmpty())
		{
			sb.append("- none\n");
		}
		for (Map.Entry<String, List<String>> e : groups.entrySet())
		{
			sb.append("- **").append(e.getKey()).append("** (").append(e.getValue().size()).append(")\n");
			for (String s : e.getValue())
			{
				sb.append("  - ").append(s).append('\n');
			}
		}
	}
}

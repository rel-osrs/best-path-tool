package com.bestpath.tool.clue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@code data/clues/clue-steps.tsv}: one line per clue step, tab-separated,
 * {@code #} lines are comments, the first other line names the columns.
 * Empty cells are {@code -}. List cells separate their entries with
 * {@code " ; "} (a {@code ;} inside an entry is written as {@code ,}).
 * A place is {@code role:ids:name@x,y,plane}. See data/clues/README.md.
 */
public final class ClueFile
{
	public static final String[] COLUMNS = {"key", "type", "tier", "items", "text", "target", "targetIds", "targetName", "x", "y", "plane",
		"description", "places", "needs", "notes", "source", "checks", "action", "requirement", "stash"};
	static final String SEP = " ; ";

	private ClueFile()
	{
	}

	public static void write(File file, List<ClueStep> steps, List<String> header) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		for (String h : header)
		{
			sb.append("# ").append(h).append('\n');
		}
		sb.append(String.join("\t", COLUMNS)).append('\n');
		for (ClueStep s : steps)
		{
			sb.append(line(s)).append('\n');
		}
		file.getAbsoluteFile().getParentFile().mkdirs();
		Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
	}

	static String line(ClueStep s)
	{
		List<String> places = new ArrayList<>();
		for (ClueStep.Place p : s.places)
		{
			places.add(p.role + ":" + ClueExtractor.joinInts(p.ids) + ":" + p.name.replace(":", " ").replace("@", " ") + "@" + p.x + "," + p.y + "," + p.plane);
		}
		String[] cells = {s.key, s.type, s.tier, ClueExtractor.joinInts(s.items), s.text, s.target, ClueExtractor.joinInts(s.targetIds),
			s.targetName, s.hasTile() ? String.valueOf(s.x) : "", s.hasTile() ? String.valueOf(s.y) : "", s.hasTile() ? String.valueOf(s.plane) : "",
			s.description, list(places), list(s.needs), list(s.notes), s.source, list(s.checks), s.action, s.requirement, s.stash};
		for (int i = 0; i < cells.length; i++)
		{
			String c = cells[i] == null ? "" : cells[i].replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
			cells[i] = c.isEmpty() ? "-" : c;
		}
		return String.join("\t", cells);
	}

	private static String list(List<String> v)
	{
		List<String> out = new ArrayList<>();
		for (String s : v)
		{
			out.add(s.replace(";", ","));
		}
		return String.join(SEP, out);
	}

	public static List<ClueStep> read(File file) throws IOException
	{
		return parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
	}

	static List<ClueStep> parse(List<String> lines)
	{
		List<ClueStep> out = new ArrayList<>();
		boolean header = true;
		int n = 0;
		for (String line : lines)
		{
			n++;
			if (line.startsWith("#") || line.isBlank())
			{
				continue;
			}
			if (header)
			{
				header = false;
				if (!line.equals(String.join("\t", COLUMNS)))
				{
					throw new IllegalArgumentException("line " + n + ": unexpected columns: " + line);
				}
				continue;
			}
			String[] c = line.split("\t", -1);
			if (c.length != COLUMNS.length)
			{
				throw new IllegalArgumentException("line " + n + ": " + c.length + " cells, expected " + COLUMNS.length);
			}
			for (int i = 0; i < c.length; i++)
			{
				c[i] = c[i].equals("-") ? "" : c[i];
			}
			List<ClueStep.Place> places = new ArrayList<>();
			for (String p : split(c[12]))
			{
				int at = p.lastIndexOf('@');
				String[] head = p.substring(0, at).split(":", 3);
				int[] t = ints(p.substring(at + 1));
				places.add(new ClueStep.Place(head[0], intList(head[1]), head[2], t[0], t[1], t[2]));
			}
			boolean tile = !c[8].isEmpty();
			out.add(new ClueStep(c[0], c[1], c[2], intList(c[3]), c[4], c[5], intList(c[6]), c[7],
				tile ? Integer.parseInt(c[8]) : -1, tile ? Integer.parseInt(c[9]) : -1, tile ? Integer.parseInt(c[10]) : -1,
				c[11], places, split(c[13]), split(c[14]), c[15], split(c[16]), c[17], c[18], c[19]));
		}
		return out;
	}

	private static List<String> split(String cell)
	{
		return cell.isEmpty() ? Collections.emptyList() : Arrays.asList(cell.split(" ; ", -1));
	}

	static List<Integer> intList(String cell)
	{
		List<Integer> out = new ArrayList<>();
		if (!cell.isEmpty())
		{
			for (String s : cell.split(","))
			{
				out.add(Integer.parseInt(s.trim()));
			}
		}
		return out;
	}

	private static int[] ints(String s)
	{
		String[] p = s.split(",");
		return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())};
	}
}

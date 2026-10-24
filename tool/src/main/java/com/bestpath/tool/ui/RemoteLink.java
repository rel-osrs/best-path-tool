package com.bestpath.tool.ui;

import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.geo.Regions;
import com.bestpath.tool.review.RegionReview;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;

/**
 * Lets another program (a script) drive the open tool. It writes commands, one per line, to
 * {@code cache/remote-link/command.txt}; twice a second the tool runs them on the Swing thread, deletes the file and
 * appends each line with its reply to {@code result.txt} next to it. A developer aid; the folder is git-ignored.
 * Changes made through it (transports, tiles, doors, answers) are ordinary changes, with "Remote link" as their author.
 * <p>
 * The commands about what the window shows are here: {@code goto x y plane [scale]}, {@code area x0 y0 x1 y1 plane},
 * {@code tile x y plane} (like a click with the pointer), {@code region <id>} and {@code show <region>} (the Region
 * review tab), {@code review <region> <CATEGORY> <STATUS|-> [note, \n for new lines]}, {@code human <region> on|off},
 * {@code facts <region>} (writes facts-&lt;region&gt;.txt), {@code regions} (writes regions.txt),
 * {@code ask <id> HIDE|REJECT|NONE x y plane [objectId] | <text>} (a card on the map, see {@link QuestionCards}),
 * {@code unask <id>} and {@code say [text]} (a message in the status bar; no text clears it). The transport commands
 * are in {@link RemoteLinkTransports}, the walking commands (tiles, walls, doors, free-to-play land) in
 * {@link RemoteLinkMapEdits}; each command's method gives its syntax.
 */
final class RemoteLink
{
	/** What the link needs from the main window. */
	interface Window
	{
		EditorContext ctx();

		RegionReview review();

		QuestionCards questions();

		/** The cache's region ids. */
		java.util.Set<Integer> regionIds();

		void jump(int x, int y, int plane, double scale);

		void showArea(int x0, int y0, int x1, int y1, int plane);

		void clickTile(int x, int y, int plane);

		void showReviewRegion(int regionId);

		void say(String text);

		void toFrontNow();
	}

	static final String AUTHOR = "Remote link";

	private final File dir;
	private final File command;
	private final Window window;
	private final Timer timer;
	/** The region the map was last moved to by {@link #follow}. */
	private int shown = -1;

	RemoteLink(File workspace, Window window)
	{
		this.dir = new File(workspace, "cache/remote-link");
		this.command = new File(dir, "command.txt");
		this.window = window;
		// a file check twice a second is nothing next to the map painting
		this.timer = new Timer(500, e -> poll());
	}

	void start()
	{
		timer.start();
	}

	private void poll()
	{
		if (!command.isFile())
		{
			return;
		}
		List<String> lines;
		try
		{
			lines = Files.readAllLines(command.toPath(), StandardCharsets.UTF_8);
			Files.delete(command.toPath());
		}
		catch (IOException ex)
		{
			return; // still being written; next time
		}
		StringBuilder out = new StringBuilder();
		for (String line : lines)
		{
			if (line.isBlank())
			{
				continue;
			}
			String result;
			try
			{
				result = run(line.trim());
			}
			catch (Exception ex)
			{
				result = "ERROR " + ex;
			}
			out.append(line.trim()).append(" -> ").append(result).append('\n');
		}
		try
		{
			Files.writeString(new File(dir, "result.txt").toPath(), out.toString(), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException ignored)
		{
			// nothing to report to
		}
	}

	/** Runs one command line; its reply. */
	private String run(String line) throws IOException
	{
		String[] w = line.split("\\s+");
		EditorContext ctx = window.ctx();
		String result = viewCommand(ctx, w, line);
		if (result == null)
		{
			result = RemoteLinkTransports.run(ctx, w, line);
		}
		if (result == null)
		{
			result = RemoteLinkMapEdits.run(ctx, w, line);
		}
		return result == null ? "unknown command" : result;
	}

	/** The commands about what the window shows: the map, the Region review tab, cards, the status bar; null for others. */
	private String viewCommand(EditorContext ctx, String[] w, String line) throws IOException
	{
		switch (w[0])
		{
			case "goto":
				window.jump(num(w[1]), num(w[2]), num(w[3]), w.length > 4 ? Double.parseDouble(w[4]) : 8);
				return "ok";
			case "area":
				window.showArea(num(w[1]), num(w[2]), num(w[3]), num(w[4]), num(w[5]));
				return "ok";
			case "tile":
				window.clickTile(num(w[1]), num(w[2]), num(w[3]));
				return "ok, selected " + (ctx.getSelection() == null ? "nothing" : ctx.getSelection().name());
			case "region":
				window.showReviewRegion(num(w[1]));
				return "ok";
			case "show":
				follow(num(w[1]));
				return "ok";
			case "review":
				return review(line);
			case "human":
				window.review().setHumanReviewed(num(w[1]), w[2].equals("on"), AUTHOR);
				return "ok";
			case "facts":
				File f = new File(dir, "facts-" + w[1] + ".txt");
				Files.writeString(f.toPath(), RemoteLinkFacts.facts(ctx, num(w[1])), StandardCharsets.UTF_8);
				return "written " + f.getName();
			case "regions":
				return regions(ctx);
			case "ask":
				return ask(line);
			case "unask":
				// unask <card id>: take an open card back without an answer
				return window.questions().withdraw(w[1]) ? "withdrawn (" + window.questions().count() + " open)" : "not open";
			case "say":
				window.say(line.length() > 4 ? line.substring(4).trim() : "");
				window.toFrontNow();
				return "ok";
			default:
				return null;
		}
	}

	/** review &lt;region&gt; &lt;CATEGORY&gt; &lt;STATUS|-&gt; [note]: sets a check ("-" keeps the status), then reads it back. */
	private String review(String line) throws IOException
	{
		String[] parts = line.split("\\s+", 5);
		follow(num(parts[1]));
		RegionReview.Status status = parts[3].equals("-") ? null : RegionReview.Status.valueOf(parts[3]);
		// "(empty)" clears the note (lines are trimmed, so nothing after the status means "keep the note")
		String note = parts.length > 4 ? (parts[4].equals("(empty)") ? "" : parts[4].replace("\\n", "\n")) : null;
		RegionReview.Category cat = RegionReview.Category.valueOf(parts[2]);
		window.review().set(num(parts[1]), cat, status, note, AUTHOR);
		// read it back, so a check the review tab saved over at once shows as NOT SAVED
		RegionReview.Check back = window.review().get(num(parts[1]), cat);
		return (status == null || back.status == status) && (note == null || back.note.equals(note)) ? "ok" : "NOT SAVED";
	}

	/** regions: writes regions.txt, the Region review list as the tab shows it by default. */
	private String regions(EditorContext ctx) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		int n = 0;
		for (int id : RemoteLinkFacts.reviewList(ctx, window.regionIds()))
		{
			sb.append(id).append('\n');
			n++;
		}
		Files.writeString(new File(dir, "regions.txt").toPath(), sb.toString(), StandardCharsets.UTF_8);
		return "written regions.txt (" + n + " regions)";
	}

	/**
	 * ask &lt;id&gt; &lt;ACTION&gt; x y plane [objectId] | &lt;text&gt;: a card on the map; with
	 * {@code || <button> ;; <button> ;; type: <hint>} after the text it has its own answer buttons and a typing box.
	 */
	private String ask(String line)
	{
		int bar = line.indexOf('|');
		String[] head = line.substring(0, bar).trim().split("\\s+");
		QuestionCards.Question q = new QuestionCards.Question();
		q.id = head[1];
		q.action = QuestionCards.Action.valueOf(head[2]);
		q.x = num(head[3]);
		q.y = num(head[4]);
		q.plane = num(head[5]);
		q.objectId = head.length > 6 ? num(head[6]) : -1;
		// ... | <text> || <button> ;; <button> ;; type: <hint>: own answer buttons and a typing box
		String rest = line.substring(bar + 1);
		int own = rest.indexOf("||");
		q.text = (own < 0 ? rest : rest.substring(0, own)).trim().replace("\\n", "\n");
		if (own >= 0)
		{
			q.choices = new ArrayList<>();
			for (String c : rest.substring(own + 2).split(";;"))
			{
				c = c.trim();
				if (c.startsWith("type:"))
				{
					q.typeHint = c.substring(5).trim();
				}
				else if (!c.isEmpty())
				{
					q.choices.add(c);
				}
			}
		}
		window.questions().ask(q);
		window.toFrontNow();
		return "asked (" + window.questions().count() + " open)";
	}

	/** Moves the map to a region and picks it in the Region review tab, when the script starts on a new region. */
	private void follow(int region)
	{
		if (region == shown)
		{
			return;
		}
		shown = region;
		int bx = Regions.baseX(region);
		int by = Regions.baseY(region);
		window.showArea(bx, by, bx + 63, by + 63, window.ctx().views().isEmpty() ? 0 : window.ctx().views().get(0).getPlane());
		window.showReviewRegion(region);
	}

	/** A number of a command (commas allowed, e.g. "3,222"). */
	static int num(String s)
	{
		return Integer.parseInt(s.replace(",", ""));
	}
}

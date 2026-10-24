package com.bestpath.tool.ui;

import static com.bestpath.tool.ui.UiCheck.get;
import static com.bestpath.tool.ui.UiCheck.removeNewEdits;
import static com.bestpath.tool.ui.UiCheck.restoreFile;
import static com.bestpath.tool.ui.UiCheck.run;
import static com.bestpath.tool.ui.UiCheck.snap;
import static com.bestpath.tool.ui.UiCheck.snapRoot;
import static com.bestpath.tool.ui.UiCheck.waitBuilt;
import static com.bestpath.tool.ui.UiCheckControls.pressButton;

import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.review.RegionReview;
import com.bestpath.tool.update.CompareFile;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * DEVELOPER AID: the {@link UiCheck} modes that press buttons which save something (a change, an answer, a group,
 * a review); each puts the files it touched back afterwards. Also the windows they open (game update comparison,
 * item groups).
 */
final class UiCheckEditModes
{
	private UiCheckEditModes()
	{
	}

	/** A change takes effect at once, the data rebuilds by itself, Ctrl+Z takes it back; the change file is put back afterwards. */
	static void undo(MainWindow window) throws Exception
	{
		EditorContext c0 = get(window::devContext);
		File file = c0.edits.getFile();
		byte[] before = file.isFile() ? Files.readAllBytes(file.toPath()) : null;
		try
		{
			int x = 3222;
			int y = 3220;
			boolean blockedBefore = get(() -> (window.devContext().grid.get(x, y, 0) & com.bestpath.core.collision.CollisionFlags.BLOCKED) != 0);
			run(() ->
			{
				Edit e = new Edit();
				e.type = Edit.Type.TILE;
				e.x = x;
				e.y = y;
				e.on = !blockedBefore;
				window.devContext().edits.add(e);
				window.devMainMap().setPlane(0);
				window.devMainMap().goTo(x, y, 16);
			});
			System.out.println("after the change: unbuilt " + get(() -> window.devContext().edits.getUnbuilt().size()));
			snap("undo-1-changed");
			waitBuilt();
			boolean blockedNow = get(() -> (window.devContext().grid.get(x, y, 0) & com.bestpath.core.collision.CollisionFlags.BLOCKED) != 0);
			System.out.println("rebuilt: tile blocked " + blockedBefore + " -> " + blockedNow);
			snap("undo-2-rebuilt");
			run(() -> window.devContext().edits.undo());
			waitBuilt();
			boolean blockedAfter = get(() -> (window.devContext().grid.get(x, y, 0) & com.bestpath.core.collision.CollisionFlags.BLOCKED) != 0);
			System.out.println("after undo and rebuild: tile blocked " + blockedAfter + " (expected " + blockedBefore + ")");
			snap("undo-3-undone");
		}
		finally
		{
			restoreFile(file, before);
		}
	}

	/**
	 * The sea channel cards made after the data build, the first one shown; then Keep pressed on it (its change line
	 * is taken out again afterwards, so its card comes back at the next build).
	 */
	static void channelCards(MainWindow window) throws Exception
	{
		Thread.sleep(2000);
		snap("channel-cards-first");
		File changes = get(() -> window.devContext().edits.getFile());
		int linesBefore = Files.readAllLines(changes.toPath()).size();
		try
		{
			run(() -> System.out.println("pressed: " + pressButton(window.devMainMap(), "Keep")));
			Thread.sleep(2500);
			snap("channel-cards-after-keep");
			System.out.println("changes not built yet: " + get(() -> window.devContext().edits.getUnbuilt().size()));
		}
		finally
		{
			removeNewEdits(changes, linesBefore, e -> e.type == Edit.Type.SEA_CHANNEL && "Card: kept (approved)".equals(e.note));
		}
	}

	/**
	 * The "go to nearest" place kind cards made after the data build; Keep pressed on the first (its change line is
	 * taken out again afterwards, so its card comes back at the next build).
	 */
	static void placeCards(MainWindow window) throws Exception
	{
		Thread.sleep(2000);
		snap("place-cards-first");
		System.out.println("open cards: " + get(() -> window.devQuestionCards().count()));
		File changes = get(() -> window.devContext().edits.getFile());
		int linesBefore = Files.readAllLines(changes.toPath()).size();
		try
		{
			run(() -> System.out.println("pressed: " + pressButton(window.devMainMap(), "Keep")));
			Thread.sleep(2500);
			snap("place-cards-after-keep");
			System.out.println("open cards after Keep: " + get(() -> window.devQuestionCards().count()));
			System.out.println("place kind answers: " + get(() -> com.bestpath.tool.edit.ManualState.placeKinds(
				window.devContext().edits.active()).decided));
		}
		finally
		{
			removeNewEdits(changes, linesBefore, e -> e.type == Edit.Type.PLACE_KIND && e.note != null && e.note.startsWith("Card: kept"));
		}
	}

	/** The Boat / Land cards of the sea charting tasks off the open sea, after the data build (nothing pressed). */
	static void chartCards(MainWindow window) throws Exception
	{
		Thread.sleep(2000);
		snap("chart-cards-first");
		System.out.println("open cards: " + get(() -> window.devQuestionCards().count()) + ", sea charting: "
			+ get(() -> window.devQuestionCards().count(QuestionCards.Action.CHARTING_SPOT)));
	}

	/**
	 * "Preview the plugin's data" in the compare window, for the cache numbered in the second argument (default
	 * 2740); Move is not pressed (it would change data/ and open the new cache).
	 */
	static void cacheMove(MainWindow window, String[] args) throws Exception
	{
		int newId = args.length > 1 ? Integer.parseInt(args[1]) : 2740;
		CacheCompareDialog d = get(window::openCacheCompare);
		run(() -> d.devChoose(newId));
		System.out.println("Move before the preview: " + (get(d::devCanMove) ? "can be pressed" : "greyed out"));
		run(d::devPreview);
		long c0 = System.currentTimeMillis();
		Thread.sleep(1000);
		while (get(d::devRunning) && System.currentTimeMillis() - c0 < 900_000)
		{
			Thread.sleep(1000);
		}
		Thread.sleep(1000);
		System.out.println("preview: " + get(d::devStatus));
		System.out.println("Move after the preview: " + (get(d::devCanMove) ? "can be pressed" : "greyed out"));
		snapRoot(d.getRootPane(), "move-preview");
		run(d::dispose);
	}

	/**
	 * "Compare with a new cache": the real comparison with the cache numbered in the second argument (default 2740)
	 * in its window; then two TEST cards held in memory only (never written to a file), Still right pressed on the
	 * first, and its CACHE_CHECK line taken out of the change file again.
	 */
	static void cacheCompare(MainWindow window, String[] args) throws Exception
	{
		int newId = args.length > 1 ? Integer.parseInt(args[1]) : 2740;
		compareWith(window, newId);
		CompareFile f = testComparison();
		File changes = get(() -> window.devContext().edits.getFile());
		int linesBefore = Files.readAllLines(changes.toPath()).size();
		try
		{
			run(() -> window.devCacheChecks(f));
			Thread.sleep(2500);
			snap("compare-cards");
			System.out.println("game update cards: " + get(() -> window.devQuestionCards().count(QuestionCards.Action.CACHE_CHECK)));
			run(() -> System.out.println("pressed: " + pressButton(window.devMainMap(), "Still right")));
			Thread.sleep(1500);
			run(() -> window.devCacheChecks(f));
			Thread.sleep(1500);
			snap("compare-cards-after-still-right");
			System.out.println("game update cards after Still right: "
				+ get(() -> window.devQuestionCards().count(QuestionCards.Action.CACHE_CHECK)) + ", answered: "
				+ get(() -> com.bestpath.tool.edit.ManualState.cacheChecks(window.devContext().edits.active())));
		}
		finally
		{
			removeNewEdits(changes, linesBefore, e -> e.type == Edit.Type.CACHE_CHECK && e.target != null && e.target.startsWith("9999/"));
			run(() -> window.devCacheChecks(null));
		}
	}

	/** The real comparison with cache {@code newId} in its window, then a picture of its report. */
	private static void compareWith(MainWindow window, int newId) throws Exception
	{
		CacheCompareDialog d = get(window::openCacheCompare);
		run(() -> d.devChoose(newId));
		run(d::devStart);
		long c0 = System.currentTimeMillis();
		Thread.sleep(1000);
		while (get(d::devRunning) && System.currentTimeMillis() - c0 < 300_000)
		{
			Thread.sleep(500);
		}
		Thread.sleep(1000);
		System.out.println("comparison: " + get(d::devStatus));
		snapRoot(d.getRootPane(), "compare-report");
		run(d::dispose);
	}

	/** A made-up comparison #2735 -> #9999 with two TEST findings (ground under a transport, a reviewed region). */
	private static CompareFile testComparison()
	{
		CompareFile f = new CompareFile();
		f.from = 2735;
		f.to = 9999;
		f.made = java.time.Instant.now().toString();
		f.report = "docs/reports/test.md";
		CompareFile.Item ground = new CompareFile.Item();
		ground.key = "9999/GROUND_CHANGED/t-test/3208,3210,0";
		ground.kind = "GROUND_CHANGED";
		ground.look = true;
		ground.title = "Transport ends on ground the game changed";
		ground.text = "(TEST CARD) \"Staircase\" (t-test): its end at 3208,3210,0: open -> blocked by an object";
		ground.x = 3208;
		ground.y = 3210;
		ground.ref = "t-test";
		CompareFile.Item region = new CompareFile.Item();
		region.key = "9999/REVIEWED_REGION_CHANGED/12850/3230,3220,0";
		region.kind = "REVIEWED_REGION_CHANGED";
		region.look = true;
		region.title = "Reviewed regions where walking changed";
		region.text = "(TEST CARD) Region 12850 (3200,3200): walls changed; objects +1 -0";
		region.details = java.util.Arrays.asList("2 tiles with other walls or blocking:", "  3230,3220,0: open -> walls north",
			"  3230,3221,0: open -> walls south", "Objects:", "  added \"Fence\" (1234) at 3230,3220,0");
		region.x = 3230;
		region.y = 3220;
		region.ref = "12850";
		region.tiles = new long[]{com.bestpath.tool.update.CacheFacts.tile(3230, 3220, 0),
			com.bestpath.tool.update.CacheFacts.tile(3230, 3221, 0)};
		f.items.add(ground);
		f.items.add(region);
		return f;
	}

	/** Makes, fills, empties and deletes a test group in the Item groups window; both files are put back afterwards. */
	static void itemGroupsEdit(MainWindow window) throws Exception
	{
		File groupsFile = com.bestpath.tool.req.ItemGroupFile.FILE;
		File defaultsFile = com.bestpath.tool.req.ItemGroupDefaults.FILE;
		byte[] groupsBefore = Files.readAllBytes(groupsFile.toPath());
		byte[] defaultsBefore = defaultsFile.isFile() ? Files.readAllBytes(defaultsFile.toPath()) : null;
		try
		{
			ItemGroupsDialog d = get(window::openItemGroups);
			run(() -> d.setLocation(-4000, 0));
			System.out.println("uses of light source: " + get(() -> d.devUses("light source")).size());
			System.out.println("uses of ghostspeak: " + get(() -> d.devUses("ghostspeak")));
			run(() -> d.devMakeGroup("bad x2 name"));
			System.out.println("bad name: " + get(d::devStatus));
			run(() -> d.devMakeGroup("light source"));
			System.out.println("taken name: " + get(d::devStatus));
			run(() -> d.devMakeGroup("Test  Group"));
			System.out.println("new: " + get(d::devStatus));
			run(() -> d.devAdd("1351, 1349 1353"));
			waitLoaded(d);
			System.out.println("add: " + get(d::devStatus));
			snapRoot(d.getRootPane(), "test-group-added");
			run(() -> d.devAdd("65000"));
			waitLoaded(d);
			System.out.println("add unknown: " + get(d::devStatus));
			run(() -> d.devAdd("abc"));
			System.out.println("add text: " + get(d::devStatus));
			run(() -> d.devRemove("test group", 1349));
			System.out.println("remove: " + get(d::devStatus));
			for (String line : Files.readAllLines(groupsFile.toPath()))
			{
				if (line.startsWith("test group"))
				{
					System.out.println("file line: " + line);
				}
			}
			snapRoot(d.getRootPane(), "test-group-removed");
			run(d::devDeleteShown);
			System.out.println("delete: " + get(d::devStatus) + " / groups now " + com.bestpath.tool.req.ItemGroupFile.groups().size());
		}
		finally
		{
			Files.write(groupsFile.toPath(), groupsBefore);
			if (defaultsBefore != null)
			{
				Files.write(defaultsFile.toPath(), defaultsBefore);
			}
		}
	}

	/** Waits until the Item groups window has looked up the items it was given. */
	private static void waitLoaded(ItemGroupsDialog d) throws Exception
	{
		Thread.sleep(300);
		while (get(d::devLoading))
		{
			Thread.sleep(200);
		}
		Thread.sleep(300);
	}

	/** The Item groups window, one picture per group named after "itemgroups" (all groups if none). */
	static void itemGroups(MainWindow window, String[] args) throws Exception
	{
		ItemGroupsDialog d = get(window::openItemGroups);
		run(() -> d.setLocation(-4000, 0));
		List<String> names = new java.util.ArrayList<>();
		for (int i = 1; i < args.length; i++)
		{
			names.add(args[i].replace('_', ' '));
		}
		if (names.isEmpty())
		{
			names.addAll(com.bestpath.tool.req.ItemGroupFile.groups().keySet());
		}
		for (String g : names)
		{
			run(() -> d.devGroupList().setSelectedValue(g, true));
			Thread.sleep(300);
			long t1 = System.currentTimeMillis();
			while (get(d::devLoading) && System.currentTimeMillis() - t1 < 60_000)
			{
				Thread.sleep(200);
			}
			Thread.sleep(300);
			snapRoot(d.getRootPane(), "group-" + g.replace(' ', '-'));
		}
	}

	/** cards &lt;file of "ask" lines&gt;: the cards as the remote link shows them, then taken back (questions.json put back). */
	static void cards(File workspace, String askFile) throws Exception
	{
		File questions = new File(workspace, "cache/remote-link/questions.json");
		byte[] before = questions.isFile() ? Files.readAllBytes(questions.toPath()) : null;
		List<String> asks = Files.readAllLines(new File(askFile).toPath());
		File command = new File(workspace, "cache/remote-link/command.txt");
		try
		{
			Files.write(command.toPath(), asks);
			Thread.sleep(4000);
			snap("cards");
		}
		finally
		{
			restoreFile(questions, before);
		}
	}

	/** The Region review tab with a few made-up checks around Lumbridge; the review file is put back afterwards. */
	static void review(MainWindow window) throws Exception
	{
		RegionReview review = get(window::devRegionReview);
		File file = review.getFile();
		byte[] before = file.isFile() ? Files.readAllBytes(file.toPath()) : null;
		try
		{
			run(() ->
			{
				setTestChecks(review);
				window.devSelectTab(4);
				window.devMainMap().setPlane(0);
				window.devMainMap().goTo(3200, 3200, 2.2);
				window.devReviewPanel().refresh();
				window.devReviewPanel().pickRegion(12850);
			});
			Thread.sleep(2500);
			snap("review");
		}
		finally
		{
			restoreFile(file, before);
		}
	}

	/** Made-up checks for three regions around Lumbridge: one part done, one complete, one with only items checked. */
	private static void setTestChecks(RegionReview review)
	{
		try
		{
			review.set(12850, RegionReview.Category.TRANSPORTS, RegionReview.Status.COMPLETE, "Castle stairs checked.", "uicheck");
			review.set(12850, RegionReview.Category.VARBITS, RegionReview.Status.NEEDS_WORK, "Cellar trapdoor changes with a varbit.", "uicheck");
			for (RegionReview.Category c : RegionReview.Category.values())
			{
				review.set(12849, c, RegionReview.Status.COMPLETE, null, "uicheck");
			}
			review.set(12594, RegionReview.Category.ITEMS, RegionReview.Status.COMPLETE, null, "uicheck");
		}
		catch (java.io.IOException ex)
		{
			throw new java.io.UncheckedIOException(ex);
		}
	}
}

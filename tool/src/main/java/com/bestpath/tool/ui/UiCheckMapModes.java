package com.bestpath.tool.ui;

import static com.bestpath.tool.ui.UiCheck.get;
import static com.bestpath.tool.ui.UiCheck.run;
import static com.bestpath.tool.ui.UiCheck.showView;
import static com.bestpath.tool.ui.UiCheck.snap;
import static com.bestpath.tool.ui.UiCheck.snapViews;
import static com.bestpath.tool.ui.UiCheck.waitBuilt;
import static com.bestpath.tool.ui.UiCheckControls.clickBox;
import static com.bestpath.tool.ui.UiCheckControls.clickFirstRow;
import static com.bestpath.tool.ui.UiCheckControls.mouseClick;
import static com.bestpath.tool.ui.UiCheckControls.pickCombo;

import com.bestpath.core.sea.SeaGraph;
import com.bestpath.tool.edit.Edit;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** DEVELOPER AID: the {@link UiCheck} modes that look at map layers (sea, channels, docks, the 3D map, one view). */
final class UiCheckMapModes
{
	private UiCheckMapModes()
	{
	}

	/** The "Sea (sailing)" layer: whole map, a dock, an area border line, and zoomed in with the walls layer. */
	static void sea(MainWindow window) throws Exception
	{
		run(() -> clickBox(window, "Sea (sailing)"));
		double[][] views = {{2500, 3150, 0.3, 0}, {2900, 3150, 1.2, 0}, {2930, 4080, 3, 0}, {3048, 3190, 8, 0}};
		String[] names = {"sea-world", "sea-karamja", "sea-north-line", "sea-port-sarim"};
		snapViews(views, names, 3000);
		run(() -> clickBox(window, "Walls & blocked tiles"));
		Thread.sleep(2000);
		snap("sea-port-sarim-walls");
	}

	/**
	 * The sea channels (Sea layer) with one in the Inspector; a mouse click selects a channel; Delete and Ctrl+Z on
	 * it; then the confirmed transports zoomed out (dots) and listed in the bottom panel ("Your transports (all)"),
	 * to check that no transport is hidden.
	 */
	static void channels(MainWindow window) throws Exception
	{
		run(() -> clickBox(window, "Sea (sailing)"));
		SeaGraph sg = get(() -> window.devContext().sailing.getSeaGraph());
		System.out.println("channels: " + sg.channelCount());
		int best = channelNearYnysdail(sg);
		int[] pts = sg.channelPoints(best);
		int mid = pts[pts.length / 2];
		System.out.println("Ynysdail channel " + (best + 1) + ": " + pts.length + " points, middle " + (mid >>> 14) + "," + (mid & 0x3FFF));
		double[][] views = {{2500, 3150, 0.3, 0}, {2200, 3470, 1.6, 0}, {mid >>> 14, mid & 0x3FFF, 6, 0}};
		String[] names = {"channels-world", "channels-ynysdail", "channels-ynysdail-inspector"};
		snapViews(views, names, 3000);
		clickChannel(window, mid);
		deleteAndUndoChannel(window, pts);
		showTransportList(window);
	}

	/** The channel whose middle point is nearest Ynysdail (2165, 3454). */
	private static int channelNearYnysdail(SeaGraph sg)
	{
		int best = -1;
		int bestD = Integer.MAX_VALUE;
		for (int c = 0; c < sg.channelCount(); c++)
		{
			int[] pts = sg.channelPoints(c);
			int mid = pts[pts.length / 2];
			int d = Math.abs((mid >>> 14) - 2165) + Math.abs((mid & 0x3FFF) - 3454);
			if (d < bestD)
			{
				bestD = d;
				best = c;
			}
		}
		return best;
	}

	/** A real mouse click on the channel's middle, from a view a little away from it (so the click, not goTo, selects it). */
	private static void clickChannel(MainWindow window, int mid) throws Exception
	{
		run(() -> window.devMainMap().showTile((mid >>> 14) + 6, (mid & 0x3FFF) + 4, 6));
		Thread.sleep(1500);
		run(() -> mouseClick(window.devMainMap(), (mid >>> 14) + 0.5, (mid & 0x3FFF) + 0.5));
		Thread.sleep(2000);
		snap("channels-clicked");
	}

	/** Delete (as the Del key does), the data built again without the channel, then Ctrl+Z; the change file is put back afterwards. */
	private static void deleteAndUndoChannel(MainWindow window, int[] pts) throws Exception
	{
		File changes = get(() -> window.devContext().edits.getFile());
		int linesBefore = changes.isFile() ? Files.readAllLines(changes.toPath()).size() : 0;
		String channelKey = com.bestpath.core.sea.SeaChannelsBuilder.key(pts);
		try
		{
			run(() -> window.devContext().deleteSelected());
			Thread.sleep(500);
			snap("channels-deleted-at-once");
			waitBuilt();
			Thread.sleep(1500);
			System.out.println("after the delete and a build: channels " + get(() -> window.devContext().sailing.getSeaGraph().channelCount()));
			snap("channels-deleted-built");
			run(() -> window.devContext().edits.undo());
			waitBuilt();
			Thread.sleep(1500);
			System.out.println("after undo and a build: channels " + get(() -> window.devContext().sailing.getSeaGraph().channelCount()));
			snap("channels-undone");
		}
		finally
		{
			removeChannelDelete(changes, linesBefore, channelKey);
		}
	}

	/** Takes only this run's own lines out (the delete and its undo): other changes made meanwhile stay. */
	private static void removeChannelDelete(File changes, int linesBefore, String channelKey) throws Exception
	{
		List<String> lines = new ArrayList<>(Files.readAllLines(changes.toPath()));
		String mine = null;
		for (int i = lines.size() - 1; i >= linesBefore; i--)
		{
			Edit e = UiCheck.edit(lines.get(i));
			if (e != null && e.type == Edit.Type.SEA_CHANNEL && channelKey.equals(e.target))
			{
				mine = e.id;
			}
		}
		for (int i = lines.size() - 1; i >= linesBefore; i--)
		{
			Edit e = UiCheck.edit(lines.get(i));
			if (e != null && mine != null && (mine.equals(e.id) || e.type == Edit.Type.REVERT && mine.equals(e.target)))
			{
				lines.remove(i);
			}
		}
		UiCheck.writeChanges(changes, lines, linesBefore);
	}

	/** The confirmed transports zoomed out and listed in the bottom panel, then a row of the list clicked. */
	private static void showTransportList(MainWindow window) throws Exception
	{
		run(() ->
		{
			clickBox(window, "Sea (sailing)");
			clickBox(window, "Confirmed transports");
			window.devMainMap().goTo(3222, 3218, 0.6);
			pickCombo(window, "Your transports (all)");
		});
		Thread.sleep(3000);
		snap("transports-zoomed-out-and-listed");
		// clicking a row of the list: the transport is selected and the map goes to it
		run(() -> clickFirstRow(window));
		Thread.sleep(3000);
		snap("transports-list-row-clicked");
	}

	/**
	 * view &lt;x&gt; &lt;y&gt; &lt;plane&gt; &lt;scale&gt; [layer name ...]: one picture of a place with the named layers ticked
	 * (layer names with spaces joined by underscores, e.g. Sea_(sailing))
	 */
	static void view(MainWindow window, String[] args) throws Exception
	{
		for (int k = 5; k < args.length; k++)
		{
			String layer = args[k].replace('_', ' ');
			run(() -> clickBox(window, layer));
		}
		int vx = Integer.parseInt(args[1]);
		int vy = Integer.parseInt(args[2]);
		int vp = Integer.parseInt(args[3]);
		double vs = Double.parseDouble(args[4]);
		run(() ->
		{
			window.devMainMap().setPlane(vp);
			window.devMainMap().goTo(vx, vy, vs);
		});
		Thread.sleep(3500);
		snap("view-" + vx + "-" + vy + "-" + vp);
	}

	/** The "Docks (sailing)" layer over the sea layer: whole map, a gangplank dock, a mooring point, a dock off the main ocean. */
	static void docks(MainWindow window) throws Exception
	{
		run(() -> clickBox(window, "Sea (sailing)"));
		run(() -> clickBox(window, "Docks (sailing)"));
		double[][] views = {{2500, 3150, 0.3, 0}, {2700, 3100, 0.8, 0}, {3050, 3190, 8, 0}, {2283, 2823, 8, 0}, {2580, 8606, 6, 0}};
		String[] names = {"docks-world", "docks-labels", "docks-port-sarim", "docks-isle-of-souls", "docks-wyrmscraig-cavern"};
		snapViews(views, names, 3000);
	}

	/** The 3D map layer around Lumbridge at a few zooms and on plane 1 (draws into the real 3D library). */
	static void models(MainWindow window) throws Exception
	{
		run(() -> clickBox(window, "3D map"));
		long m0 = System.currentTimeMillis();
		String phase;
		while (!("DRAWING".equals(phase = get(window::devModelPhase)) || "READY".equals(phase))
			&& System.currentTimeMillis() - m0 < 240_000)
		{
			Thread.sleep(500);
		}
		System.out.printf(Locale.ROOT, "3D map %s after %.1f s%n", phase, (System.currentTimeMillis() - m0) / 1000.0);
		double[][] views = {{3222, 3218, 16, 0}, {3222, 3218, 4, 0}, {3222, 3218, 1, 0}, {3212, 3220, 12, 1}, {3212, 3425, 6, 0}, {2364, 2597, 0.4657, 0}, {2364, 2597, 0.2, 0}};
		String[] names = {"models-16", "models-4", "models-1", "models-plane1", "models-varrock", "models-far", "models-farther"};
		for (int i = 0; i < views.length; i++)
		{
			showView(views[i]);
			Thread.sleep(8000); // visible regions are drawn first
			snap(names[i]);
			System.out.println("  status: " + get(window::devStatus));
		}
		// the 3D map on its own (world map off), zoomed in and out
		run(() -> clickBox(window, "World map"));
		double[][] only = {{3222, 3218, 16, 0}, {2364, 2597, 0.4657, 0}};
		for (int i = 0; i < only.length; i++)
		{
			showView(only[i]);
			Thread.sleep(8000);
			snap("models-only-" + i);
		}
		run(() -> clickBox(window, "World map"));
		run(() -> clickBox(window, "3D map")); // off: stops drawing and saves what is done
	}
}

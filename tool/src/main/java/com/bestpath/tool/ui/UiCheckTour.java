package com.bestpath.tool.ui;

import static com.bestpath.tool.ui.UiCheck.get;
import static com.bestpath.tool.ui.UiCheck.snap;
import static com.bestpath.tool.ui.UiCheck.snapRoot;
import static com.bestpath.tool.ui.UiCheck.waitForRoute;
import static com.bestpath.tool.ui.UiCheckControls.whereFields;
import static com.bestpath.tool.ui.UiCheckControls.whereRow;

import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.transport.CacheTableTeleports;
import com.bestpath.tool.transport.TransportCandidate;
import java.io.File;
import java.util.List;

/**
 * DEVELOPER AID: what {@link UiCheck} shows when no mode is named: a candidate, a suggested teleport and two
 * transports selected, the Networks tab, a few routes, and (with {@code -PuiCheckExport}) "Update plugin data".
 */
final class UiCheckTour
{
	private UiCheckTour()
	{
	}

	static void run(MainWindow window, File workspace) throws Exception
	{
		candidate(window);
		teleport(window);
		transportNeeds(window);
		whereCheck(window);
		networks(window);
		routes(window);
		// "Update plugin data" only with -PuiCheckExport (a real export: about a minute)
		if (Boolean.getBoolean("uicheck.export"))
		{
			pluginExport(window, workspace);
		}
	}

	/** A candidate near Lumbridge castle, selected (decision bar filled in, both views). */
	private static void candidate(MainWindow window) throws Exception
	{
		UiCheck.run(() ->
		{
			EditorContext ctx = window.devContext();
			List<TransportCandidate> near = ctx.transports.near(3206, 3228, 0, 12);
			if (!near.isEmpty())
			{
				window.devForViews(v -> v.setShowTransports(true));
				ctx.showCandidate(near.get(0));
			}
		});
		Thread.sleep(3000);
		snap("candidate-selected");
	}

	/** A suggested teleport (pink dot) selected. */
	private static void teleport(MainWindow window) throws Exception
	{
		UiCheck.run(() ->
		{
			EditorContext ctx = window.devContext();
			ctx.teleportsShown = true;
			window.devForViews(v -> v.setShowTeleports(true));
			for (CacheTableTeleports.Destination d : ctx.transports.getDestinations())
			{
				if (d.source.startsWith("Wiki") && d.name.contains("Varrock"))
				{
					window.devMainMap().setPlane(d.plane);
					window.devMainMap().goTo(d.x, d.y, 6);
					ctx.select(ctx.teleportSelection(d), window.devMainMap(), false);
					break;
				}
			}
		});
		Thread.sleep(3000);
		snap("teleport-selected");
	}

	/** A transport with requirements selected (the Needs box: in words, and its line to edit). */
	private static void transportNeeds(MainWindow window) throws Exception
	{
		UiCheck.run(() -> selectTransport(window, "Bamboo Gate"));
		Thread.sleep(2000);
		snap("transport-needs");
	}

	/**
	 * The Where row, which moves a transport's ends (also to another plane): Drakan's medallion's Slepe teleport
	 * selected, plane 1 typed for its end (not moved: the check only).
	 */
	private static void whereCheck(MainWindow window) throws Exception
	{
		UiCheck.run(() -> selectTransport(window, "Drakan's medallion: Slepe"));
		Thread.sleep(1500);
		System.out.println("where, Drakan's medallion: Slepe: " + get(() -> whereRow(window.getRootPane())));
		UiCheck.run(() -> whereFields(window.getRootPane())[5].setText("1"));
		Thread.sleep(500);
		System.out.println("where, plane 1 typed: " + get(() -> whereRow(window.getRootPane())));
		snap("transport-where");
		UiCheck.run(() -> whereFields(window.getRootPane())[5].setText("0"));
	}

	/** Selects the first transport with this name and moves the main map to its end. */
	private static void selectTransport(MainWindow window, String name)
	{
		EditorContext ctx = window.devContext();
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			if (!t.deleted && name.equals(t.record.name))
			{
				window.devMainMap().setPlane(t.record.endPlane);
				window.devMainMap().goTo(t.record.endX, t.record.endY, 6);
				ctx.select(EditorContext.Selection.ofRecord(t.record, t.pending), window.devMainMap(), false);
				break;
			}
		}
	}

	/** The Networks tab, one network shown (its stops and rides on the right). */
	private static void networks(MainWindow window) throws Exception
	{
		UiCheck.run(() ->
		{
			window.devSelectTab(5);
			window.devNetworksPanel().devShow("Canoe");
		});
		Thread.sleep(1000);
		System.out.println("networks, Bamboo Gate selected: " + get(() -> window.devNetworksPanel().devButtons()));
		UiCheck.run(() ->
		{
			EditorContext ctx = window.devContext();
			for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
			{
				if (!t.deleted && "Canoe".equals(t.record.network))
				{
					ctx.showRecord(t.record);
					break;
				}
			}
		});
		Thread.sleep(1000);
		System.out.println("networks, a canoe ride selected: " + get(() -> window.devNetworksPanel().devButtons()));
		snap("networks");
		UiCheck.run(() -> window.devSelectTab(0));
	}

	/** A long route, a close-up walk, and two routes to blocked destinations. */
	private static void routes(MainWindow window) throws Exception
	{
		// a route: Lumbridge castle to the Grand Exchange, Route tool and tab
		UiCheck.run(() ->
		{
			window.devContext().select(null, null, false);
			window.devSelectTool('r');
			window.devMainMap().setPlane(0);
			window.devMainMap().goTo(3195, 3350, 1.5);
			window.devRoute().setStart(3222, 3218, 0);
			window.devRoute().setEnd(3165, 3480, 0);
		});
		waitForRoute();
		Thread.sleep(3000);
		System.out.println("route: " + get(() -> window.devRoute().getStatus()));
		snap("route");

		// close-up of a short walk on open ground: the game's shape (straight first, then diagonal)
		UiCheck.run(() ->
		{
			window.devMainMap().goTo(3312, 3148, 22);
			window.devRoute().setStart(3304, 3146, 0);
			window.devRoute().setEnd(3320, 3151, 0);
		});
		waitForRoute();
		Thread.sleep(3000);
		System.out.println("close-up route: " + get(() -> window.devRoute().getStatus()));
		snap("route-closeup");

		blockedDestination(window);
		dungeonDestination(window);
	}

	/** A destination on a blocked tile (inside the castle wall area): the route stops nearby, in sight. */
	private static void blockedDestination(MainWindow window) throws Exception
	{
		int[] blocked = get(() ->
		{
			EditorContext ctx = window.devContext();
			for (int r = 1; r < 12; r++)
			{
				for (int x = 3212 - r; x <= 3212 + r; x++)
				{
					for (int y = 3226 - r; y <= 3226 + r; y++)
					{
						if (ctx.graph.componentAt(x, y, 0) < 0 && ctx.graph.componentAt(x + 1, y, 0) >= 0)
						{
							return new int[]{x, y};
						}
					}
				}
			}
			return null;
		});
		if (blocked != null)
		{
			int[] b = blocked;
			UiCheck.run(() ->
			{
				window.devMainMap().goTo(b[0], b[1], 18);
				window.devRoute().setStart(3222, 3218, 0);
				window.devRoute().setEnd(b[0], b[1], 0);
			});
			waitForRoute();
			Thread.sleep(3000);
			System.out.println("blocked destination " + b[0] + "," + b[1] + ": " + get(() -> window.devRoute().getStatus()));
			snap("route-blocked-destination");
		}
	}

	/** Above the main map (Edgeville dungeon): a blocked destination; the stop tile stays in its region. */
	private static void dungeonDestination(MainWindow window) throws Exception
	{
		int[] dungeon = get(() ->
		{
			EditorContext ctx = window.devContext();
			for (int x = 3090; x <= 3110; x++)
			{
				for (int y = 9870; y <= 9900; y++)
				{
					if (ctx.graph.componentAt(x, y, 0) < 0 && ctx.graph.componentAt(x, y - 1, 0) >= 0)
					{
						return new int[]{x, y};
					}
				}
			}
			return null;
		});
		if (dungeon != null)
		{
			int[] d = dungeon;
			UiCheck.run(() ->
			{
				window.devMainMap().goTo(d[0], d[1], 14);
				window.devRoute().setStart(d[0], d[1] - 1, 0);
				window.devRoute().setEnd(d[0], d[1], 0);
			});
			waitForRoute();
			Thread.sleep(3000);
			System.out.println("dungeon destination " + d[0] + "," + d[1] + ": " + get(() -> window.devRoute().getStatus()));
			snap("route-dungeon-blocked");
		}
	}

	/** A real "Update plugin data" run into a copy of the plugin's data folder (the plugin itself is not changed), pictured before and after. */
	private static void pluginExport(MainWindow window, File workspace) throws Exception
	{
		File copy = new File(workspace, "cache/ui-check-plugin-data");
		copy.mkdirs();
		File[] old = copy.listFiles();
		for (File f : old == null ? new File[0] : old)
		{
			f.delete();
		}
		File real = new File(workspace, "src/main/resources/com/bestpath/data");
		for (File f : real.listFiles())
		{
			java.nio.file.Files.copy(f.toPath(), new File(copy, f.getName()).toPath());
		}
		PluginExportDialog[] d = new PluginExportDialog[1];
		UiCheck.run(() -> d[0] = window.openPluginExport(copy));
		Thread.sleep(500);
		snapRoot(d[0].getRootPane(), "plugin-export-start");
		UiCheck.run(() -> d[0].devStart());
		long exportStart = System.currentTimeMillis();
		Thread.sleep(2000);
		while (get(() -> d[0].devRunning()) && System.currentTimeMillis() - exportStart < 900_000)
		{
			Thread.sleep(1000);
		}
		Thread.sleep(500);
		System.out.println("plugin export: " + get(() -> d[0].devStatus()));
		String text = get(() -> d[0].devLog());
		int at = text.indexOf("---- What changes");
		System.out.println(at >= 0 ? text.substring(at) : text.substring(Math.max(0, text.length() - 3000)));
		snapRoot(d[0].getRootPane(), "plugin-export-done");
	}
}

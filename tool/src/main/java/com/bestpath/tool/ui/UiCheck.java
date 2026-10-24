package com.bestpath.tool.ui;

import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.edit.Edit;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * DEVELOPER AID: opens the real tool window (off screen), runs a few
 * scripted steps and saves a picture of the window after each one, so the
 * user interface can be checked without anyone looking at the screen.
 * The first argument names a mode (see {@link #runMode}); without one the
 * tour of {@link UiCheckTour} runs. Uses its own temporary settings; modes
 * that save changes put the data files back afterwards. Pictures go to
 * {@code cache/ui-check/}. {@code gradlew -p tool uiCheck}
 */
public final class UiCheck
{
	private static MainWindow window;
	private static File outDir;
	private static int shot;

	private UiCheck()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File workspace = ToolSettings.findWorkspace();
		ToolSettings settings = checkSettings(workspace);
		outDir = new File(workspace, "cache/ui-check");
		outDir.mkdirs();
		for (File f : outDir.listFiles())
		{
			f.delete();
		}
		openWindow(settings, workspace);
		waitForData();
		snap("opened");
		if (args.length > 0 && args[0].equals("sailing"))
		{
			UiCheckRoutes.setUpSailing(window, args);
			args = UiCheckRoutes.asRoutes(args);
		}
		else if (args.length > 0 && args[0].equals("examples"))
		{
			args = UiCheckRoutes.exampleArgs(workspace);
		}
		if (!runMode(args, workspace))
		{
			UiCheckTour.run(window, workspace);
			System.out.println("pictures in " + outDir.getAbsolutePath());
		}
		System.exit(0); // no shutdown(): the tool's own settings are not saved
	}

	/** Temporary settings: the cache the plugin data was made from, Lumbridge in view, the window not maximised. */
	private static ToolSettings checkSettings(File workspace) throws Exception
	{
		File cacheDir = com.bestpath.tool.cache.DataCache.folder(workspace); // the cache the plugin's data was made from
		File tmp = File.createTempFile("bestpath-uicheck", ".properties");
		tmp.deleteOnExit();
		ToolSettings settings = new ToolSettings(tmp);
		settings.set("cache.last", cacheDir.getAbsolutePath());
		settings.set("view.x", "3222.5");
		settings.set("view.y", "3218.5");
		settings.set("view.scale", "6");
		settings.set("window.maximized", "false");
		return settings;
	}

	private static void openWindow(ToolSettings settings, File workspace) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			}
			catch (Exception ignored)
			{
				// default look and feel is fine
			}
			window = new MainWindow(settings, workspace);
			window.setExtendedState(java.awt.Frame.NORMAL); // never maximise on the real screen
			window.setSize(1920, 1040); // a maximised window on a 1920x1080 screen, minus the taskbar
			window.setLocation(-4000, 0); // off screen: nothing flashes on the user's desktop
			window.setVisible(true);
		});
	}

	/** Waits until the data is built (at most 3 minutes, else a picture and exit code 1), then 3 more seconds. */
	private static void waitForData() throws Exception
	{
		long t0 = System.currentTimeMillis();
		while (get(() -> window.devContext().pathfinder) == null)
		{
			if (System.currentTimeMillis() - t0 > 180_000)
			{
				snap("timeout");
				System.out.println("Data was not ready after 3 minutes; status: " + get(window::devStatus));
				System.exit(1);
			}
			Thread.sleep(500);
		}
		System.out.printf(Locale.ROOT, "data ready after %.1f s: %s%n", (System.currentTimeMillis() - t0) / 1000.0, get(window::devStatus));
		Thread.sleep(3000);
	}

	/** Runs the mode named by the first argument; false when there is none (the tour runs instead). */
	private static boolean runMode(String[] args, File workspace) throws Exception
	{
		switch (args.length > 0 ? args[0] : "")
		{
			case "undo":
				UiCheckEditModes.undo(window);
				return true;
			case "sea":
				UiCheckMapModes.sea(window);
				return true;
			case "channels":
				UiCheckMapModes.channels(window);
				return true;
			case "view":
				UiCheckMapModes.view(window, args);
				return true;
			case "channelcards":
				UiCheckEditModes.channelCards(window);
				return true;
			case "placecards":
				UiCheckEditModes.placeCards(window);
				return true;
			case "cachemove":
				UiCheckEditModes.cacheMove(window, args);
				return true;
			case "cachecompare":
				UiCheckEditModes.cacheCompare(window, args);
				return true;
			case "chartcards":
				UiCheckEditModes.chartCards(window);
				return true;
			case "docks":
				UiCheckMapModes.docks(window);
				return true;
			case "models":
				UiCheckMapModes.models(window);
				return true;
			case "itemgroupsedit":
				UiCheckEditModes.itemGroupsEdit(window);
				return true;
			case "itemgroups":
				UiCheckEditModes.itemGroups(window, args);
				return true;
			case "cards":
				if (args.length < 2)
				{
					return false;
				}
				UiCheckEditModes.cards(workspace, args[1]);
				return true;
			case "review":
				UiCheckEditModes.review(window);
				return true;
			case "routes":
				UiCheckRoutes.routes(window, args);
				return true;
			default:
				return false;
		}
	}

	// --- steps shared by the modes ---

	/** Waits until the data has taken in every change (the background rebuild is done). */
	static void waitBuilt() throws Exception
	{
		long t0 = System.currentTimeMillis();
		Thread.sleep(500);
		while (get(() -> window.devContext().edits.needsBuild()) && System.currentTimeMillis() - t0 < 120_000)
		{
			Thread.sleep(250);
		}
		System.out.printf(Locale.ROOT, "  data rebuilt after %.1f s%n", (System.currentTimeMillis() - t0) / 1000.0);
		Thread.sleep(1000);
	}

	/** Waits until the route search is done (at most a minute). */
	static void waitForRoute() throws Exception
	{
		long t0 = System.currentTimeMillis();
		while (get(() -> window.devRoute().isBusy()) && System.currentTimeMillis() - t0 < 60_000)
		{
			Thread.sleep(200);
		}
	}

	/** Moves the main map to a view {x, y, scale, plane}. */
	static void showView(double[] v) throws Exception
	{
		run(() ->
		{
			window.devMainMap().setPlane((int) v[3]);
			window.devMainMap().goTo((int) v[0], (int) v[1], v[2]);
		});
	}

	/** One picture per view ({x, y, scale, plane}), each after a pause of {@code wait} ms. */
	static void snapViews(double[][] views, String[] names, long wait) throws Exception
	{
		for (int i = 0; i < views.length; i++)
		{
			showView(views[i]);
			Thread.sleep(wait);
			snap(names[i]);
		}
	}

	static void run(Runnable r) throws Exception
	{
		SwingUtilities.invokeAndWait(r);
	}

	static <T> T get(Supplier<T> s) throws Exception
	{
		Object[] box = new Object[1];
		SwingUtilities.invokeAndWait(() -> box[0] = s.get());
		@SuppressWarnings("unchecked")
		T t = (T) box[0];
		return t;
	}

	/** Paints the whole window into a picture file. */
	static void snap(String name) throws Exception
	{
		snapRoot(window.getRootPane(), name);
	}

	static void snapRoot(JRootPane root, String name) throws Exception
	{
		File f = new File(outDir, String.format(Locale.ROOT, "%02d-%s.png", ++shot, name));
		SwingUtilities.invokeAndWait(() ->
		{
			BufferedImage img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = img.createGraphics();
			root.printAll(g);
			g.dispose();
			try
			{
				ImageIO.write(img, "png", f);
			}
			catch (java.io.IOException e)
			{
				throw new RuntimeException(e);
			}
		});
		System.out.println("saved " + f.getName());
	}

	// --- putting saved files back ---

	/** Puts a file back as it was ({@code before} null: it was not there). */
	static void restoreFile(File file, byte[] before) throws Exception
	{
		if (before == null)
		{
			file.delete();
		}
		else
		{
			Files.write(file.toPath(), before);
		}
	}

	/** A change file line as a change. */
	static Edit edit(String line)
	{
		return new com.google.gson.Gson().fromJson(line, Edit.class);
	}

	/** Takes out of the change file the lines after the first {@code linesBefore} whose change {@code mine} picks. */
	static void removeNewEdits(File changes, int linesBefore, Predicate<Edit> mine) throws Exception
	{
		List<String> lines = new ArrayList<>(Files.readAllLines(changes.toPath()));
		for (int i = lines.size() - 1; i >= linesBefore; i--)
		{
			Edit e = edit(lines.get(i));
			if (e != null && mine.test(e))
			{
				lines.remove(i);
			}
		}
		writeChanges(changes, lines, linesBefore);
	}

	/** Writes the change file's lines back and says how many there are now. */
	static void writeChanges(File changes, List<String> lines, int linesBefore) throws Exception
	{
		Files.write(changes.toPath(), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
		System.out.println("change file: " + lines.size() + " lines (was " + linesBefore + ")");
	}
}

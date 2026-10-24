package com.bestpath.tool.map;

import com.bestpath.tool.cache.LoadedCache;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Supplies map pictures per (region, plane) from the saved {@link MapLibrary}
 * and keeps it up to date for the open cache.
 *
 * <p>Two background threads:
 * <ul>
 * <li><b>map-draw</b>: prepares the renderer, fingerprints every region,
 * and draws only pictures whose region changed (visible ones first);</li>
 * <li><b>map-load</b>: reads saved pictures from disk when you zoom in, so
 * zooming never waits behind drawing.</li>
 * </ul>
 * Saved pictures are shown immediately on open, even before checking finishes.
 * One store per picture {@link Style}, each with its own library.
 */
public final class MapTileStore
{
	public static final int THUMB = 32;
	/** Smallest kept version of a picture; below it the 32 px thumbnail is used. */
	private static final int SMALLEST = 64;
	private static final int SAVE_EVERY = 100;

	/** Kind of map picture a store draws. */
	public enum Style
	{
		/** The world-map look (RuneLite's map drawer), 4 px per tile. */
		WORLD_MAP(256, 520L << 20, "Map pictures"),
		/** Ground and every object's 3D model seen from straight above, 16 px per tile. */
		MODELS(1024, 400L << 20, "3D map");

		/** Full picture size in pixels (64 tiles). */
		final int full;
		/** Memory for pictures kept in memory (bytes). */
		final long budget;
		final String name;

		Style(int full, long budget, String name)
		{
			this.full = full;
			this.budget = budget;
			this.name = name;
		}

		public String getName()
		{
			return name;
		}
	}

	public enum Phase
	{
		PREPARING("Preparing the map renderer"),
		CHECKING("Checking which regions changed"),
		DRAWING("Drawing map pictures"),
		READY("Map pictures up to date"),
		FAILED("Map pictures failed");

		private final String text;

		Phase(String text)
		{
			this.text = text;
		}

		public String getText()
		{
			return text;
		}
	}

	private final LoadedCache cache;
	private final MapLibrary library;
	private final Runnable repaint;
	private final Style style;

	/**
	 * Pictures in memory, per region-plane and size: key {@code key * 8 + level},
	 * level 0 = full size, each next level half as big, down to 64 px. Least
	 * recently used ones are dropped beyond the style's memory budget, so big
	 * versions go first when zoomed out.
	 */
	private final LinkedHashMap<Long, BufferedImage> full = new LinkedHashMap<>(256, 0.75f, true);
	private long fullBytes;
	private final Map<Long, BufferedImage> thumbImages = new ConcurrentHashMap<>();

	private final Object drawLock = new Object();
	private final Deque<Long> urgentDraw = new ArrayDeque<>();
	private final Deque<Long> backgroundDraw = new ArrayDeque<>();
	private final Set<Long> pendingDraw = new HashSet<>();

	private final Object loadLock = new Object();
	private final Deque<Long> loadQueue = new ArrayDeque<>();
	private final Set<Long> pendingLoad = new HashSet<>();

	private volatile Phase phase = Phase.PREPARING;
	private volatile boolean checked;
	private volatile boolean stopped;
	private volatile String lastError;
	private volatile int regionsReused;
	private volatile int regionsChanged;
	private volatile int toDraw;
	private volatile int drawn;
	private volatile double avgMs;
	/** Pictures drawn since the library was last saved (draw thread only). */
	private int sinceSave;

	private final Thread drawThread;
	private final Thread loadThread;

	public MapTileStore(LoadedCache cache, MapLibrary library, Runnable repaint)
	{
		this(cache, library, repaint, Style.WORLD_MAP);
	}

	public MapTileStore(LoadedCache cache, MapLibrary library, Runnable repaint, Style style)
	{
		this.cache = cache;
		this.library = library;
		this.repaint = repaint;
		this.style = style;
		String suffix = style == Style.WORLD_MAP ? "" : "-" + style.name().toLowerCase(java.util.Locale.ROOT);
		drawThread = new Thread(this::drawLoop, "map-draw" + suffix);
		drawThread.setDaemon(true);
		// the 3D drawing is slower and only fills in an optional picture style; keep the tool responsive
		drawThread.setPriority(style == Style.WORLD_MAP ? Thread.NORM_PRIORITY : Thread.MIN_PRIORITY);
		loadThread = new Thread(this::loadLoop, "map-load" + suffix);
		loadThread.setDaemon(true);
		drawThread.start();
		loadThread.start();
	}

	static long key(int regionId, int plane)
	{
		return ((long) regionId << 2) | plane;
	}

	public static final BufferedImage EMPTY = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

	/**
	 * Picture for drawing a region-plane {@code pixelsPerRegion} wide. Returns
	 * {@link #EMPTY} if there is nothing to draw, or null if not drawn yet.
	 */
	public BufferedImage get(int regionId, int plane, double pixelsPerRegion)
	{
		long k = key(regionId, plane);
		MapLibrary.Entry e = library.get(regionId);
		boolean upToDate = checked && e != null && e.isDone(plane);
		if (e != null && e.isEmpty(plane))
		{
			return EMPTY;
		}
		BufferedImage thumb = thumb(k);
		if (pixelsPerRegion <= THUMB)
		{
			if (checked && !upToDate)
			{
				requestDraw(k);
			}
			else if (thumb == null)
			{
				if (e != null && (e.pictureMask >> plane & 1) != 0 && library.savedPicture(regionId, plane) != null)
				{
					requestLoad(k); // saved picture without a thumbnail yet: rebuild it
				}
				else
				{
					requestDraw(k);
				}
			}
			return thumb;
		}
		// smallest version at least as big as the screen size, so a picture is
		// never shrunk by more than half when drawn (no shimmering)
		int level = 0;
		while ((style.full >> (level + 1)) >= SMALLEST && (style.full >> (level + 1)) >= pixelsPerRegion)
		{
			level++;
		}
		BufferedImage img;
		synchronized (full)
		{
			img = full.get(k * 8 + level);
		}
		if (img != null)
		{
			if (checked && !upToDate)
			{
				requestDraw(k);
			}
			return img;
		}
		if (e != null && (e.pictureMask >> plane & 1) != 0 && library.savedPicture(regionId, plane) != null)
		{
			requestLoad(k);
		}
		else
		{
			requestDraw(k);
		}
		return thumb; // blurry stand-in until the full picture arrives
	}

	private BufferedImage thumb(long k)
	{
		BufferedImage t = thumbImages.get(k);
		if (t == null)
		{
			t = library.thumb(k);
			if (t != null)
			{
				thumbImages.put(k, t);
			}
		}
		return t;
	}

	private void requestDraw(long k)
	{
		synchronized (drawLock)
		{
			if (pendingDraw.add(k))
			{
				urgentDraw.addFirst(k);
				drawLock.notifyAll();
			}
		}
	}

	private void requestLoad(long k)
	{
		synchronized (loadLock)
		{
			if (pendingLoad.add(k))
			{
				loadQueue.addFirst(k);
				loadLock.notifyAll();
			}
		}
	}

	private void drawLoop()
	{
		if (!prepareAndCheck())
		{
			return;
		}
		sinceSave = 0;
		while (!stopped)
		{
			Long k;
			try
			{
				k = nextToDraw();
			}
			catch (InterruptedException ex)
			{
				return;
			}
			if (k == null)
			{
				break;
			}
			drawQueued(k);
		}
		saveQuietly();
	}

	/** Prepares the renderer and queues every picture that must be drawn; false (phase FAILED) on an error. */
	private boolean prepareAndCheck()
	{
		try
		{
			phase = Phase.PREPARING;
			repaintLater();
			cache.prepareRenderer();
			phase = Phase.CHECKING;
			repaintLater();
			planBackgroundWork(style == Style.WORLD_MAP ? cache.regionFingerprints() : cache.modelFingerprints());
			checked = true;
			phase = toDraw > 0 ? Phase.DRAWING : Phase.READY;
			repaintLater();
			return true;
		}
		catch (IOException | RuntimeException e)
		{
			lastError = e.getMessage();
			phase = Phase.FAILED;
			repaintLater();
			return false;
		}
	}

	/**
	 * Waits for the next picture to draw (urgent ones first) and returns its key, or null once stopped. While the
	 * queues are empty after drawing, the phase becomes READY and the library is saved.
	 */
	private Long nextToDraw() throws InterruptedException
	{
		synchronized (drawLock)
		{
			while (urgentDraw.isEmpty() && backgroundDraw.isEmpty() && !stopped)
			{
				if (phase == Phase.DRAWING)
				{
					phase = Phase.READY;
					saveQuietly();
					sinceSave = 0;
					repaintLater();
				}
				drawLock.wait();
			}
			if (stopped)
			{
				return null;
			}
			long k = !urgentDraw.isEmpty() ? urgentDraw.pollFirst() : backgroundDraw.pollFirst();
			pendingDraw.add(k);
			return k;
		}
	}

	/** Draws one queued picture (unless it is up to date by now), timing it and saving the library now and then. */
	private void drawQueued(long k)
	{
		int regionId = (int) (k >> 2);
		int plane = (int) (k & 3);
		MapLibrary.Entry e = library.get(regionId);
		if (e != null && e.isDone(plane))
		{
			synchronized (drawLock)
			{
				pendingDraw.remove(k);
			}
			return; // drawn meanwhile, or up to date
		}
		if (phase != Phase.DRAWING)
		{
			phase = Phase.DRAWING;
		}
		long t0 = System.nanoTime();
		try
		{
			draw(regionId, plane);
		}
		catch (IOException | RuntimeException ex)
		{
			lastError = "region " + regionId + " plane " + plane + ": " + ex.getMessage();
			library.markEmpty(regionId, plane);
		}
		avgMs = drawn == 0 ? (System.nanoTime() - t0) / 1e6 : avgMs * 0.98 + (System.nanoTime() - t0) / 1e6 * 0.02;
		drawn++;
		synchronized (drawLock)
		{
			pendingDraw.remove(k);
		}
		if (++sinceSave >= SAVE_EVERY)
		{
			saveQuietly();
			sinceSave = 0;
		}
		repaintLater();
	}

	/** Compares fingerprints with the library and queues every picture that must be (re)drawn. */
	private void planBackgroundWork(Map<Integer, Long> fingerprints)
	{
		List<Long> work = new ArrayList<>();
		int reused = 0;
		int changed = 0;
		for (Map.Entry<Integer, Long> f : fingerprints.entrySet())
		{
			int id = f.getKey();
			MapLibrary.Entry e = library.get(id);
			if (e != null && e.fingerprint == f.getValue())
			{
				reused++;
			}
			else
			{
				changed++;
				e = library.reset(id, f.getValue());
			}
			for (int p = 0; p < 4; p++)
			{
				if (!e.isDone(p))
				{
					work.add(key(id, p));
				}
			}
		}
		synchronized (drawLock)
		{
			backgroundDraw.addAll(work);
			drawLock.notifyAll();
		}
		regionsReused = reused;
		regionsChanged = changed;
		toDraw = work.size();
	}

	private void draw(int regionId, int plane) throws IOException
	{
		long k = key(regionId, plane);
		if (!cache.planeHasContent(regionId, plane))
		{
			library.markEmpty(regionId, plane);
			thumbImages.remove(k);
			return;
		}
		BufferedImage img = style == Style.WORLD_MAP
			? cache.renderRegion(regionId, plane)
			: cache.renderRegionModels(regionId, plane, style.full / 64);
		if (img == null || isBlank(img))
		{
			library.markEmpty(regionId, plane);
			thumbImages.remove(k);
			return;
		}
		library.savePicture(regionId, plane, img, style == Style.MODELS);
		BufferedImage[] levels = mipmaps(img);
		BufferedImage t = scale(levels[levels.length - 1], THUMB);
		library.markPicture(regionId, plane, t);
		thumbImages.put(k, t);
		keep(k, levels);
	}

	/** Puts every size of a picture in memory, dropping the least recently used beyond the budget. */
	private void keep(long k, BufferedImage[] levels)
	{
		synchronized (full)
		{
			for (int i = 0; i < levels.length; i++)
			{
				BufferedImage old = full.put(k * 8 + i, levels[i]);
				fullBytes += bytes(levels[i]) - (old == null ? 0 : bytes(old));
			}
			java.util.Iterator<Map.Entry<Long, BufferedImage>> it = full.entrySet().iterator();
			while (fullBytes > style.budget && it.hasNext())
			{
				fullBytes -= bytes(it.next().getValue());
				it.remove();
			}
		}
	}

	private static long bytes(BufferedImage img)
	{
		return 4L * img.getWidth() * img.getHeight();
	}

	private void loadLoop()
	{
		while (!stopped)
		{
			long k;
			synchronized (loadLock)
			{
				while (loadQueue.isEmpty() && !stopped)
				{
					try
					{
						loadLock.wait();
					}
					catch (InterruptedException e)
					{
						return;
					}
				}
				if (stopped)
				{
					return;
				}
				k = loadQueue.pollFirst();
			}
			File png = library.savedPicture((int) (k >> 2), (int) (k & 3));
			try
			{
				BufferedImage img = png != null ? ImageIO.read(png) : null;
				if (img != null)
				{
					BufferedImage[] levels = mipmaps(img);
					keep(k, levels);
					if (!thumbImages.containsKey(k) && library.thumb(k) == null)
					{
						BufferedImage t = scale(levels[levels.length - 1], THUMB);
						library.markPicture((int) (k >> 2), (int) (k & 3), t);
						thumbImages.put(k, t);
					}
				}
			}
			catch (IOException e)
			{
				lastError = "reading " + (png == null ? "?" : png.getName()) + ": " + e.getMessage();
			}
			synchronized (loadLock)
			{
				pendingLoad.remove(k);
			}
			repaintLater();
		}
	}

	private void saveQuietly()
	{
		try
		{
			library.save();
		}
		catch (IOException e)
		{
			lastError = "saving the map library: " + e.getMessage();
		}
	}

	private void repaintLater()
	{
		SwingUtilities.invokeLater(repaint);
	}

	/** The picture at full size and every half size down to 64 px, each made by halving the previous one. */
	static BufferedImage[] mipmaps(BufferedImage img)
	{
		List<BufferedImage> out = new ArrayList<>();
		out.add(toArgb(img));
		for (int size = img.getWidth() / 2; size >= SMALLEST; size /= 2)
		{
			out.add(scale(out.get(out.size() - 1), size));
		}
		return out.toArray(new BufferedImage[0]);
	}

	/** Saved JPEGs load as RGB; keep everything ARGB so drawing is uniform. */
	private static BufferedImage toArgb(BufferedImage img)
	{
		if (img.getType() == BufferedImage.TYPE_INT_ARGB)
		{
			return img;
		}
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();
		return out;
	}

	static boolean isBlank(BufferedImage img)
	{
		for (int y = 0; y < img.getHeight(); y += 2)
		{
			for (int x = 0; x < img.getWidth(); x += 2)
			{
				if ((img.getRGB(x, y) >>> 24) != 0)
				{
					return false;
				}
			}
		}
		return true;
	}

	private static BufferedImage scale(BufferedImage src, int size)
	{
		BufferedImage dst = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, size, size, null);
		g.dispose();
		return dst;
	}

	public Style getStyle()
	{
		return style;
	}

	public Phase getPhase()
	{
		return phase;
	}

	public int getRegionsReused()
	{
		return regionsReused;
	}

	public int getRegionsChanged()
	{
		return regionsChanged;
	}

	/** Pictures queued for drawing when checking finished (0 until then). */
	public int getToDraw()
	{
		return toDraw;
	}

	public int getDrawn()
	{
		return Math.min(drawn, Math.max(toDraw, drawn));
	}

	/** Rough seconds left, from the recent average drawing time. */
	public long getSecondsLeft()
	{
		int left = Math.max(0, toDraw - drawn);
		return Math.round(left * avgMs / 1000);
	}

	public String getLastError()
	{
		return lastError;
	}

	public void stop()
	{
		stopped = true;
		synchronized (drawLock)
		{
			drawLock.notifyAll();
		}
		synchronized (loadLock)
		{
			loadLock.notifyAll();
		}
	}

	/** Waits for the threads to finish (the draw thread saves the library on exit). */
	public void join() throws InterruptedException
	{
		drawThread.join(15_000);
		loadThread.join(2_000);
	}
}

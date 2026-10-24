package com.bestpath.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sidebar's pictures: each one is asked for once from the
 * {@link IconProvider} and kept, with its scaled copies, so cards made again
 * (every new route) reuse them. Swing thread only. Small: at most a few
 * hundred icons of up to 36x32 pixels.
 */
final class IconCache
{
	private static final class Entry
	{
		BufferedImage image;
		/** Waiting for the picture (it is drawn later); null once it is here. */
		List<Runnable> waiting = new ArrayList<>();
		final Map<Long, BufferedImage> scaled = new HashMap<>();
	}

	/** At most this many things wait for one picture (the newest cards). */
	static final int MAX_WAITING = 64;

	private final IconProvider provider;
	private final Map<IconKey, Entry> entries = new HashMap<>();

	IconCache(IconProvider provider)
	{
		this.provider = provider == null ? IconProvider.NONE : provider;
	}

	/**
	 * The picture fitted into {@code w} x {@code h} (see {@link #fit}), or null while it is not here yet; then
	 * {@code onLoad} runs (Swing thread) once when it arrives.
	 */
	BufferedImage get(IconKey key, int w, int h, Runnable onLoad)
	{
		Entry e = entries.get(key);
		if (e == null)
		{
			Entry fresh = new Entry();
			e = fresh;
			entries.put(key, fresh);
			provider.load(key, img ->
			{
				if (img == null || fresh.image != null)
				{
					return;
				}
				fresh.image = img;
				List<Runnable> w8 = fresh.waiting;
				fresh.waiting = null;
				for (Runnable r : w8)
				{
					r.run();
				}
			});
		}
		if (e.image == null)
		{
			if (onLoad != null && e.waiting != null)
			{
				if (e.waiting.size() >= MAX_WAITING)
				{
					e.waiting.remove(0); // a picture that never comes must not keep old cards forever
				}
				e.waiting.add(onLoad);
			}
			return null;
		}
		long size = (long) w << 32 | h;
		BufferedImage s = e.scaled.get(size);
		if (s == null)
		{
			s = fit(e.image, w, h);
			e.scaled.put(size, s);
		}
		return s;
	}

	/** Item names asked for (the bank card's box): null value = asked, not here yet (or none). */
	private final Map<Integer, String> names = new HashMap<>();
	/** Waiting for an item's name, by item. */
	private final Map<Integer, List<Runnable>> namesWaiting = new HashMap<>();

	/**
	 * An item's name (from the {@link IconProvider}), or null while it is not here (or there is none); then
	 * {@code onLoad} runs (Swing thread) once when it arrives. Asked once per item and kept.
	 */
	String itemName(int itemId, Runnable onLoad)
	{
		if (names.containsKey(itemId))
		{
			String n = names.get(itemId);
			List<Runnable> w = namesWaiting.get(itemId);
			if (n == null && onLoad != null && w != null && w.size() < MAX_WAITING)
			{
				w.add(onLoad);
			}
			return n;
		}
		names.put(itemId, null);
		List<Runnable> waiting = new ArrayList<>();
		if (onLoad != null)
		{
			waiting.add(onLoad);
		}
		namesWaiting.put(itemId, waiting);
		provider.itemName(itemId, n ->
		{
			if (n == null || n.isEmpty() || "null".equals(n) || names.get(itemId) != null)
			{
				namesWaiting.remove(itemId); // none: the bank box keeps the route data's name
				return;
			}
			names.put(itemId, n);
			List<Runnable> w = namesWaiting.remove(itemId);
			if (w != null)
			{
				for (Runnable r : w)
				{
					r.run();
				}
			}
		});
		return names.get(itemId); // a provider that answers at once
	}

	/**
	 * The picture made to fit {@code w} x {@code h}, keeping its shape: a bigger one is scaled down smoothly, a small
	 * one (at most half the box) is scaled up by a whole factor, pixel for pixel, so it stays sharp; others keep their
	 * size.
	 */
	static BufferedImage fit(BufferedImage img, int w, int h)
	{
		int iw = img.getWidth();
		int ih = img.getHeight();
		double scale;
		Object interpolation;
		if (iw > w || ih > h)
		{
			scale = Math.min((double) w / iw, (double) h / ih);
			interpolation = RenderingHints.VALUE_INTERPOLATION_BICUBIC;
		}
		else if (iw * 2 <= w && ih * 2 <= h)
		{
			scale = Math.min(w / iw, h / ih);
			interpolation = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
		}
		else
		{
			scale = 1;
			interpolation = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
		}
		int nw = Math.max(1, (int) Math.round(iw * scale));
		int nh = Math.max(1, (int) Math.round(ih * scale));
		BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(img, 0, 0, nw, nh, null);
		g.dispose();
		return out;
	}
}

package com.bestpath.overlay;

import java.awt.BasicStroke;
import java.awt.Stroke;

/**
 * The pens the path is drawn with in the game world, for the "Path thickness" setting. At the default thickness 3:
 * tile squares 1 (the current step's 2), the walk line 3 (the current step's 4), the boat's course 3, each line with
 * a dark edge 2 wider. Arrow heads grow with thicker lines but never shrink below their size at 3. Made once per
 * thickness.
 */
public final class PathStrokes
{
	public static final int MIN = 1;
	public static final int MAX = 10;
	/** The default thickness. */
	public static final int USUAL = 3;
	private static final PathStrokes[] MADE = new PathStrokes[MAX + 1];

	static
	{
		for (int w = MIN; w <= MAX; w++)
		{
			MADE[w] = new PathStrokes(w);
		}
	}

	final int width;
	final Stroke thin;
	final Stroke thick;
	final Stroke walk;
	final Stroke walkOutline;
	final Stroke walkNow;
	final Stroke walkNowOutline;
	final Stroke course;
	final Stroke courseOutline;
	/** Arrow heads' size against the usual (1 up to thickness 3). */
	final double arrowScale;

	private PathStrokes(int w)
	{
		width = w;
		thin = new BasicStroke(Math.max(1, w - 2));
		thick = new BasicStroke(Math.max(1, w - 1));
		walk = round(w);
		walkOutline = round(w + 2);
		walkNow = round(w + 1);
		walkNowOutline = round(w + 3);
		course = round(w);
		courseOutline = round(w + 2);
		arrowScale = Math.max(1, w / (double) USUAL);
	}

	private static Stroke round(float w)
	{
		return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	}

	/** The pens for this thickness (clamped to {@value #MIN}-{@value #MAX}). */
	public static PathStrokes of(int thickness)
	{
		return MADE[Math.max(MIN, Math.min(MAX, thickness))];
	}
}

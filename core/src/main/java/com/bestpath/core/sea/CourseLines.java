package com.bestpath.core.sea;

/**
 * Plain geometry for drawing a boat's course as arrows in the overlays: cutting a leg to a box, and one arrow head per
 * leg at the leg's end, where the boat turns. Numbers only (no drawing classes); nothing is allocated.
 */
public final class CourseLines
{
	private CourseLines()
	{
	}

	/**
	 * The part of the line from (x0, y0) to (x1, y1) inside the box [minX, maxX] x [minY, maxY] (Liang-Barsky): its
	 * start and end as fractions of the line into {@code t} (0 = (x0, y0), 1 = (x1, y1)). False when no part is inside
	 * (a line that only touches the box at one point counts as outside).
	 */
	public static boolean clip(double x0, double y0, double x1, double y1, double minX, double minY, double maxX,
		double maxY, double[] t)
	{
		double dx = x1 - x0;
		double dy = y1 - y0;
		t[0] = 0;
		t[1] = 1;
		return edge(-dx, x0 - minX, t) && edge(dx, maxX - x0, t) && edge(-dy, y0 - minY, t) && edge(dy, maxY - y0, t)
			&& t[0] < t[1];
	}

	/** One edge of {@link #clip}: narrows t[0] .. t[1]; false when the line is parallel to the edge and outside it. */
	private static boolean edge(double p, double q, double[] t)
	{
		if (p == 0)
		{
			return q >= 0;
		}
		double r = q / p;
		if (p < 0)
		{
			t[0] = Math.max(t[0], r);
		}
		else
		{
			t[1] = Math.min(t[1], r);
		}
		return true;
	}

	/**
	 * An arrow head: its tip at (tipX, tipY), pointing the way from (fromX, fromY) to the tip, {@code length} long and
	 * {@code 2 * halfWidth} wide at its base: {tipX, tipY, base corner 1 x, y, base corner 2 x, y} into {@code out}.
	 * False (nothing written) when the two points are the same.
	 */
	public static boolean head(double fromX, double fromY, double tipX, double tipY, double length, double halfWidth,
		double[] out)
	{
		double dx = tipX - fromX;
		double dy = tipY - fromY;
		double n = Math.hypot(dx, dy);
		if (n < 1e-6)
		{
			return false;
		}
		dx /= n;
		dy /= n;
		double bx = tipX - dx * length;
		double by = tipY - dy * length;
		out[0] = tipX;
		out[1] = tipY;
		out[2] = bx - dy * halfWidth;
		out[3] = by + dx * halfWidth;
		out[4] = bx + dy * halfWidth;
		out[5] = by - dx * halfWidth;
		return true;
	}

	/**
	 * The one arrow head of a leg drawn from (ax, ay) to (bx, by) on screen: its tip on the leg's end (the turn, or the
	 * course's end), pointing the leg's way; into {@code out} as {@link #head}. False when the end is outside the box
	 * [minX, maxX] x [minY, maxY] (the next visible turn draws its own head) or the leg is shorter than the head.
	 * (ax, ay) may be any earlier point of the leg; its distance to the end is the length checked.
	 */
	public static boolean endHead(double ax, double ay, double bx, double by, double minX, double minY, double maxX,
		double maxY, double length, double halfWidth, double[] out)
	{
		if (bx < minX || bx > maxX || by < minY || by > maxY || Math.hypot(bx - ax, by - ay) < length)
		{
			return false;
		}
		return head(ax, ay, bx, by, length, halfWidth, out);
	}
}

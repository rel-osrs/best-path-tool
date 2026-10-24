package com.bestpath.overlay;

import com.bestpath.core.sea.CourseLines;
import com.bestpath.route.RouteStep;
import java.awt.geom.Path2D;

/**
 * The geometry of a boat's course drawn as arrows. Each straight leg of a sail step (corner to corner,
 * {@link RouteStep#cornerX(int)}: the boat's exact headings, 22.5 degrees apart) is one straight line with one arrow
 * head at the leg's end, where the boat turns, pointing the way the boat goes; a leg whose end does not show has no
 * head. Plain Java2D with no RuneLite classes, shared by the scene, minimap and world map overlays. Nothing here
 * allocates.
 */
public final class CourseArrows
{
	private CourseArrows()
	{
	}

	/**
	 * Adds a filled arrow head to {@code p}: its tip at (tipX, tipY), pointing the way from (fromX, fromY) to the tip,
	 * {@code length} long and {@code 2 * halfWidth} wide at its base. Nothing when the two points are the same.
	 */
	public static void head(Path2D.Float p, double fromX, double fromY, double tipX, double tipY, double length, double halfWidth)
	{
		double dx = tipX - fromX;
		double dy = tipY - fromY;
		double n = Math.hypot(dx, dy);
		if (n < 1e-6)
		{
			return;
		}
		dx /= n;
		dy /= n;
		double bx = tipX - dx * length;
		double by = tipY - dy * length;
		p.moveTo((float) tipX, (float) tipY);
		p.lineTo((float) (bx - dy * halfWidth), (float) (by + dx * halfWidth));
		p.lineTo((float) (bx + dy * halfWidth), (float) (by - dx * halfWidth));
		p.closePath();
	}

	/**
	 * Adds the one arrow head of a leg drawn on screen from (ax, ay) to (bx, by): its tip on the leg's end (the turn,
	 * or the course's end), pointing the leg's way. Returns false (nothing added) when the end is outside the box
	 * (what shows: the map's area, the canvas) or the leg is shorter than the head ({@link CourseLines#endHead}).
	 * {@code buf}: at least 6 doubles, reused.
	 */
	public static boolean endHead(Path2D.Float p, double ax, double ay, double bx, double by, double minX, double minY,
		double maxX, double maxY, double length, double halfWidth, double[] buf)
	{
		if (!CourseLines.endHead(ax, ay, bx, by, minX, minY, maxX, maxY, length, halfWidth, buf))
		{
			return false;
		}
		p.moveTo((float) buf[0], (float) buf[1]);
		p.lineTo((float) buf[2], (float) buf[3]);
		p.lineTo((float) buf[4], (float) buf[5]);
		p.closePath();
		return true;
	}

	/**
	 * Where on the exact course the player at tile index {@code k} of the step is: the point that far along its leg
	 * (the tiles are the legs sampled evenly), x and y in world tiles into {@code out}. The step must have corners.
	 */
	public static void pointAt(RouteStep s, double k, double[] out)
	{
		int n = s.cornerCount();
		if (k <= s.corner(0))
		{
			out[0] = s.cornerX(0);
			out[1] = s.cornerY(0);
			return;
		}
		for (int c = 0; c + 1 < n; c++)
		{
			int a = s.corner(c);
			int b = s.corner(c + 1);
			if (k <= b && b > a)
			{
				double f = (k - a) / (b - a);
				out[0] = s.cornerX(c) + (s.cornerX(c + 1) - s.cornerX(c)) * f;
				out[1] = s.cornerY(c) + (s.cornerY(c + 1) - s.cornerY(c)) * f;
				return;
			}
		}
		out[0] = s.cornerX(n - 1);
		out[1] = s.cornerY(n - 1);
	}
}

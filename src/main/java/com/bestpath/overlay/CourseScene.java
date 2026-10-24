package com.bestpath.overlay;

import com.bestpath.core.sea.CourseLines;
import com.bestpath.route.RouteStep;
import java.awt.geom.Path2D;
import java.util.Arrays;

/**
 * A sail step's course in one loaded scene: each leg cut to the scene's tiles and sampled about a tile apart along its
 * exact line, as local coordinates (128 per tile, a tile's middle at + 64). The scene and minimap overlays project
 * these samples each frame and draw between neighbours that land on the canvas, so every part of a leg that shows is
 * drawn even when its start is off screen or outside the scene. Built only when the route or the scene's base changes
 * (never per frame). Each leg's one arrow head sits on the leg's end, where the boat turns; a leg whose end is outside
 * the scene or off screen has none (the next turn that shows has its own). Pure (no RuneLite classes) and immutable;
 * {@link Frame} holds what a frame reuses.
 */
public final class CourseScene
{
	public static final CourseScene EMPTY = new CourseScene(new int[0], new int[0], new float[0], new int[1], new boolean[0]);

	/** Local x / y of each sample. */
	private final int[] lx;
	private final int[] ly;
	/**
	 * Each sample's place along the step's tiles (a tile index, fractional), to leave out what is behind the player
	 * (tests read it).
	 */
	final float[] at;
	/** Per leg: its first sample; the last entry is the sample count (leg c is [start[c], start[c + 1])). */
	private final int[] start;
	/**
	 * Per leg: its last sample is the leg's real end (the turn or the course's end lies in the scene): only then can it
	 * have an arrow head (tests read it).
	 */
	final boolean[] endIn;

	private CourseScene(int[] lx, int[] ly, float[] at, int[] start, boolean[] endIn)
	{
		this.lx = lx;
		this.ly = ly;
		this.at = at;
		this.start = start;
		this.endIn = endIn;
	}

	/**
	 * The step's course in the scene whose south-west tile is (baseX, baseY), sizeX x sizeY tiles: each leg's part over
	 * the scene's tiles (their middles, so every sample is a local point inside the scene), sampled at most a tile
	 * apart (both ends of the part included). A leg outside the scene has no samples.
	 */
	public static CourseScene of(RouteStep s, int baseX, int baseY, int sizeX, int sizeY)
	{
		int legs = s.cornerCount() - 1;
		if (legs < 1 || sizeX < 2 || sizeY < 2)
		{
			return EMPTY;
		}
		int[] start = new int[legs + 1];
		boolean[] endIn = new boolean[legs];
		int[] lx = new int[64];
		int[] ly = new int[64];
		float[] at = new float[64];
		int n = 0;
		double[] t = new double[2];
		for (int c = 0; c < legs; c++)
		{
			start[c] = n;
			double x0 = s.cornerX(c) - baseX;
			double y0 = s.cornerY(c) - baseY;
			double x1 = s.cornerX(c + 1) - baseX;
			double y1 = s.cornerY(c + 1) - baseY;
			if (!CourseLines.clip(x0, y0, x1, y1, 0, 0, sizeX - 1, sizeY - 1, t))
			{
				continue;
			}
			endIn[c] = t[1] >= 1 - 1e-9;
			double len = Math.hypot(x1 - x0, y1 - y0) * (t[1] - t[0]);
			int m = Math.max(1, (int) Math.ceil(len - 1e-9));
			int a = s.corner(c);
			int b = s.corner(c + 1);
			for (int i = 0; i <= m; i++)
			{
				double f = t[0] + (t[1] - t[0]) * i / m;
				if (n == lx.length)
				{
					lx = Arrays.copyOf(lx, n * 2);
					ly = Arrays.copyOf(ly, n * 2);
					at = Arrays.copyOf(at, n * 2);
				}
				lx[n] = (int) Math.round((x0 + (x1 - x0) * f) * 128 + 64);
				ly[n] = (int) Math.round((y0 + (y1 - y0) * f) * 128 + 64);
				at[n] = (float) (a + (b - a) * f);
				n++;
			}
		}
		start[legs] = n;
		return new CourseScene(Arrays.copyOf(lx, n), Arrays.copyOf(ly, n), Arrays.copyOf(at, n), start, endIn);
	}

	/** Legs (the step's corners less one; 0 for {@link #EMPTY}). */
	public int legCount()
	{
		return start.length - 1;
	}

	/** Leg c's first sample. */
	public int legFrom(int c)
	{
		return start[c];
	}

	/** Leg c's samples end here (exclusive); equal to {@link #legFrom} when the leg is outside the scene. */
	public int legTo(int c)
	{
		return start[c + 1];
	}

	public int sampleCount()
	{
		return lx.length;
	}

	public int localX(int i)
	{
		return lx[i];
	}

	public int localY(int i)
	{
		return ly[i];
	}

	/** Where a sample lands on the screen (the canvas or the minimap). */
	public interface SampleProjector
	{
		/** Sample i's screen x, y into {@code out}; false when it does not land on the screen (behind the camera...). */
		boolean project(int i, int[] out);
	}

	/** What one frame works with, reused frame after frame (grown when a course has more samples). */
	public static final class Frame
	{
		private int[] sx = new int[64];
		private int[] sy = new int[64];
		private boolean[] ok = new boolean[64];
		private final int[] xy = new int[2];
		private final double[] head = new double[6];

		/** Makes room for a scene's samples (call when a scene is made, not per frame). */
		public void fit(CourseScene cs)
		{
			if (cs.sampleCount() > ok.length)
			{
				sx = Arrays.copyOf(sx, cs.sampleCount());
				sy = Arrays.copyOf(sy, cs.sampleCount());
				ok = Arrays.copyOf(ok, cs.sampleCount());
			}
		}
	}

	/**
	 * Adds the course from tile index {@code k} on (the player's place; what is behind him is left out). The samples
	 * are projected; each run of neighbouring samples that all land on the screen is one straight line from its first
	 * to its last (a straight leg stays straight on the flat sea in the scene's perspective and on the minimap, and one
	 * line has no rounding kinks), so every part of a leg that shows is drawn, wherever its start is. Each leg ahead
	 * gets one arrow head with its tip on the leg's end (the turn) when that end is in the scene and on the screen
	 * ({@link #endHead}). Lines go to {@code line}, heads to {@code heads}; returns the heads added. {@code f} must
	 * {@link Frame#fit} this scene.
	 */
	public int draw(float k, SampleProjector p, Frame f, Path2D.Float line, Path2D.Float heads, double headLength,
		double headHalfWidth)
	{
		int count = 0;
		for (int c = 0; c < legCount(); c++)
		{
			int from = legFrom(c);
			int to = legTo(c);
			while (from < to && at[from] < k)
			{
				from++; // behind the player
			}
			for (int i = from; i < to; i++)
			{
				f.ok[i] = p.project(i, f.xy);
				f.sx[i] = f.xy[0];
				f.sy[i] = f.xy[1];
			}
			int runStart = -1;
			for (int i = from; i <= to; i++)
			{
				if (i < to && f.ok[i])
				{
					runStart = runStart < 0 ? i : runStart;
					continue;
				}
				if (runStart >= 0 && i - 1 > runStart)
				{
					line.moveTo(f.sx[runStart], f.sy[runStart]);
					line.lineTo(f.sx[i - 1], f.sy[i - 1]);
				}
				runStart = -1;
			}
			if (from < to && endIn[c] && endHead(c, from, p, f, heads, headLength, headHalfWidth))
			{
				count++;
			}
		}
		return count;
	}

	/**
	 * Leg c's arrow head, its tip on the leg's last sample (the turn; must be on the screen). The head points from an
	 * earlier sample of the leg on the screen, walking back along the unbroken run of samples on the screen until one is
	 * at least a head's length away; samples behind the player (before {@code from}) are projected now if the walk
	 * reaches them, so the leg the boat is on keeps its head up to the turn. No head when the run is shorter than the
	 * head (a short leg, or most of it off screen). At most the samples of one leg are projected; nothing allocated.
	 */
	private boolean endHead(int c, int from, SampleProjector p, Frame f, Path2D.Float heads, double headLength,
		double headHalfWidth)
	{
		int tip = legTo(c) - 1;
		if (!f.ok[tip])
		{
			return false;
		}
		int first = legFrom(c);
		double min2 = headLength * headLength;
		for (int j = tip - 1; j >= first; j--)
		{
			if (j < from)
			{
				f.ok[j] = p.project(j, f.xy); // behind the player: only for the head's direction
				f.sx[j] = f.xy[0];
				f.sy[j] = f.xy[1];
			}
			if (!f.ok[j])
			{
				return false;
			}
			double dx = f.sx[tip] - f.sx[j];
			double dy = f.sy[tip] - f.sy[j];
			if (dx * dx + dy * dy >= min2)
			{
				return CourseArrows.endHead(heads, f.sx[j], f.sy[j], f.sx[tip], f.sy[tip], -1e9, -1e9, 1e9, 1e9,
					headLength, headHalfWidth, f.head);
			}
		}
		return false;
	}
}

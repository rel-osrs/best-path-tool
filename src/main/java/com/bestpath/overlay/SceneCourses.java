package com.bestpath.overlay;

import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStep;
import java.awt.geom.Path2D;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/**
 * The published route's sail steps in the loaded scene, for the scene and minimap overlays: each step's
 * {@link CourseScene} and its samples as local points, made again only when the route or the scene's base or size
 * changes; plus what a frame reuses. A frame projects the samples and draws a line between neighbours that both land
 * on the screen, so every part of a leg that shows is drawn wherever its start is, with one arrow head on the leg's
 * end, the turn, when that shows ({@link CourseScene#draw}). One instance per overlay, used on the client thread only
 * (render()).
 */
final class SceneCourses implements CourseScene.SampleProjector
{
	/** Where a local point lands on the screen (the canvas or the minimap), or null. */
	interface Projector
	{
		Point project(LocalPoint lp);
	}

	private RouteResult route;
	private int wvId;
	private int baseX;
	private int baseY;
	private int sizeX;
	private int sizeY;
	private CourseScene[] scenes = new CourseScene[0];
	private LocalPoint[][] points = new LocalPoint[0][];
	private final CourseScene.Frame frame = new CourseScene.Frame();
	/** While drawing: the step's local points and the projector (no allocation per frame). */
	private LocalPoint[] drawing;
	private Projector projector;

	/** Step {@code step}'s course in this scene (made now if the route or the scene changed). */
	CourseScene scene(RouteResult r, WorldView wv, int step)
	{
		if (r != route || wv.getId() != wvId || wv.getBaseX() != baseX || wv.getBaseY() != baseY || wv.getSizeX() != sizeX
			|| wv.getSizeY() != sizeY)
		{
			route = r;
			wvId = wv.getId();
			baseX = wv.getBaseX();
			baseY = wv.getBaseY();
			sizeX = wv.getSizeX();
			sizeY = wv.getSizeY();
			scenes = new CourseScene[r.getSteps().size()];
			points = new LocalPoint[scenes.length][];
		}
		if (scenes[step] == null)
		{
			RouteStep s = r.getSteps().get(step);
			CourseScene cs = CourseScene.of(s, baseX, baseY, sizeX, sizeY);
			LocalPoint[] lp = new LocalPoint[cs.sampleCount()];
			for (int i = 0; i < lp.length; i++)
			{
				lp[i] = new LocalPoint(cs.localX(i), cs.localY(i), wv);
			}
			scenes[step] = cs;
			points[step] = lp;
			frame.fit(cs);
		}
		return scenes[step];
	}

	/**
	 * Adds step {@code step}'s course from tile index {@code k} on to {@code line} and its arrow heads (one per leg) to
	 * {@code heads}. Call {@link #scene} for the step first, in the same frame.
	 */
	void draw(int step, float k, Projector p, Path2D.Float line, Path2D.Float heads, double headLength,
		double headHalfWidth)
	{
		drawing = points[step];
		projector = p;
		scenes[step].draw(k, this, frame, line, heads, headLength, headHalfWidth);
		drawing = null;
		projector = null;
	}

	@Override
	public boolean project(int i, int[] out)
	{
		Point p = projector.project(drawing[i]);
		if (p == null)
		{
			return false;
		}
		out[0] = p.getX();
		out[1] = p.getY();
		return true;
	}
}

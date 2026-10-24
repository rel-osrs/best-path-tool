package com.bestpath.overlay;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.DrawPath;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStep;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the published route as dots on the minimap (transport tiles larger;
 * the current step brighter; steps already done left out). Walks as
 * {@link DrawPath} has them. Reads only the precomputed, immutable
 * {@link RouteResult}, {@link DrawPath} and {@link Progress}. A boat's course
 * is straight lines per leg in the boat's exact headings, each with one small
 * arrow head ({@link CourseArrows}), instead of dots; every part of a leg on
 * the minimap is drawn wherever its start is ({@link SceneCourses}).
 */
public class RouteMinimapOverlay extends Overlay
{

	private static final Stroke COURSE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private final Client client;
	private final Supplier<RouteResult> route;
	private final Supplier<Progress> progress;
	private final Supplier<DrawPath> drawn;
	private final Path2D.Float heads = new Path2D.Float();
	private final Path2D.Float course = new Path2D.Float();
	private final SceneCourses courses = new SceneCourses();
	private final SceneCourses.Projector toMinimap;
	/** The route's colours, from the one colour the player picks. */
	private final Supplier<RouteColours> colours;

	public RouteMinimapOverlay(Client client, Supplier<RouteResult> route, Supplier<Progress> progress,
		Supplier<DrawPath> drawn, Supplier<RouteColours> colours)
	{
		this.colours = colours;
		this.client = client;
		this.route = route;
		this.progress = progress;
		this.drawn = drawn;
		this.toMinimap = lp -> Perspective.localToMinimap(client, lp);
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		RouteResult r = route.get();
		if (r.segmentCount() == 0 && r.markerCount() == 0)
		{
			return null;
		}
		WorldView wv = client.getTopLevelWorldView();
		int plane = wv.getPlane();
		Progress p = progress.get();
		int current = p.isFor(r) ? p.getStep() : -1;
		DrawPath d = drawn.get();
		boolean redrawn = d.isFor(r);
		RouteColours c = colours.get();
		for (int s = 0; s < r.segmentCount(); s++)
		{
			int step = r.segmentStep(s);
			if (step < current)
			{
				continue;
			}
			boolean now = step == current;
			g.setColor(now ? c.minimapCurrent : c.minimapPath);
			RouteStep st = r.getSteps().get(step);
			if (st.getKind() == RouteStep.Kind.SAIL && st.cornerCount() >= 2)
			{
				drawCourse(g, r, wv, plane, step, now ? p.getTileIndex() : 0);
				continue;
			}
			boolean own = !redrawn || !d.hasTiles(step);
			int n = own ? r.segmentLength(s) : d.tileCount(step);
			for (int i = now ? p.getTileIndex() : 0; i < n; i++)
			{
				dot(g, wv, plane, own ? r.segmentTile(s, i) : d.tile(step, i), 2);
			}
		}
		for (int i = 0; i < r.markerCount(); i++)
		{
			int step = r.markerStep(i);
			if (step >= current)
			{
				g.setColor(step == current ? c.minimapCurrent : c.minimapMarker);
				dot(g, wv, plane, r.marker(i), 4);
			}
		}
		return null;
	}

	/** A sail step's course from tile index {@code k} on: each leg's exact line on the minimap, one arrow head. */
	private void drawCourse(Graphics2D g, RouteResult r, WorldView wv, int plane, int step, int k)
	{
		if (Tiles.plane(r.getSteps().get(step).tile(0)) != plane)
		{
			return;
		}
		courses.scene(r, wv, step);
		course.reset();
		heads.reset();
		courses.draw(step, k, toMinimap, course, heads, 6, 3.5);
		Stroke old = g.getStroke();
		g.setStroke(COURSE);
		g.draw(course);
		g.fill(heads);
		g.setStroke(old);
	}

	private void dot(Graphics2D g, WorldView wv, int plane, int tile, int size)
	{
		if (Tiles.plane(tile) != plane)
		{
			return;
		}
		LocalPoint lp = LocalPoint.fromWorld(wv, Tiles.x(tile), Tiles.y(tile));
		if (lp == null)
		{
			return;
		}
		Point p = Perspective.localToMinimap(client, lp);
		if (p != null)
		{
			g.fillRect(p.getX() - size / 2, p.getY() - size / 2, size, size);
		}
	}
}

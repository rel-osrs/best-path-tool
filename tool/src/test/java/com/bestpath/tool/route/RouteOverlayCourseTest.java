package com.bestpath.tool.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.SeaGraph;
import com.bestpath.core.sea.SeaGraphDiagnostics;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaGridBuilder;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import com.bestpath.core.sea.SeaRouter;
import com.bestpath.core.sea.SeaRouterDiagnostics;
import com.bestpath.tool.map.MapViewport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The Route tab draws a sea course as arrows: straight legs in the boat's exact headings, one arrow head per leg, at
 * the leg's end where the boat turns. Also saves a picture of it (no game cache needed: a hand-made sea with an
 * island) to tool/build/route-arrows/route-tab-course.png for looking at.
 */
class RouteOverlayCourseTest
{
	@Test
	void aCourseIsDrawnLegByLegWithOneArrowHeadEach() throws Exception
	{
		// a sea 96 x 64 from 3200, 3200 with an island in the middle; a raft from the south-west to the north-east
		SeaMap.Builder b = new SeaMap.Builder();
		for (int x = 3200; x < 3296; x++)
		{
			for (int y = 3200; y < 3264; y++)
			{
				boolean island = x >= 3236 && x < 3256 && y >= 3218 && y < 3240;
				if (!island)
				{
					b.set(x, y, 131);
				}
			}
		}
		SeaMap sea = b.build();
		MovementRules.Flags none = (x, y) -> 0;
		SeaGrid grid = SeaGridBuilder.build(sea, SeaPieces.build(sea, none), none);
		SeaGraph g = SeaGraph.build(grid, List.of());
		Boat raft = new Boat(1, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, 0);
		SeaRouter.Course c = SeaRouterDiagnostics.search(new SeaRouter(g), new SeaRouter.Rules(raft, 50),
			List.of(new SeaRouter.Start(g.join(new int[][]{{3210, 3206}}), 0)), List.of(g.join(new int[][]{{3286, 3256}})),
			Integer.MAX_VALUE / 2).course(0);
		assertTrue(c.headings.length >= 2, "the island is in the way: legs " + c.headings.length);

		int w = 900;
		int h = 640;
		MapViewport vp = new MapViewport(3248, 3232, 9);
		vp.setSize(w, h);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D gr = img.createGraphics();
		gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		gr.setColor(new Color(30, 52, 38));
		gr.fillRect(0, 0, w, h);
		gr.setColor(new Color(28, 50, 92));
		for (int x = 3200; x < 3296; x++)
		{
			for (int y = 3200; y < 3264; y++)
			{
				if (grid.isOcean(x, y))
				{
					gr.fillRect((int) Math.floor(vp.screenX(x)), (int) Math.floor(vp.screenY(y + 1)), 10, 10);
				}
			}
		}
		// the course's tiles faintly (what the old drawing showed), then the legs as arrows
		gr.setColor(new Color(255, 255, 255, 50));
		for (int t : c.tiles)
		{
			gr.fillRect((int) Math.floor(vp.screenX(t >>> 14)), (int) Math.floor(vp.screenY((t & 0x3FFF) + 1)), 9, 9);
		}
		gr.setClip(0, 0, w, h);
		int heads = RouteOverlay.drawCourse(gr, vp, c.points, gr.getClipBounds());
		assertEquals(c.headings.length, heads);
		gr.setColor(Color.WHITE);
		StringBuilder legs = new StringBuilder();
		for (int i = 0; i < c.headings.length; i++)
		{
			legs.append(i == 0 ? "" : ", ").append(SeaGraphDiagnostics.compassDegrees(c.headings[i])).append("°");
		}
		gr.drawString("Route tab: a raft's course round an island, " + c.headings.length + " legs (compass " + legs
			+ "), one arrow head each, at its end", 10, 20);
		gr.dispose();
		File dir = new File("build/route-arrows");
		dir.mkdirs();
		ImageIO.write(img, "png", new File(dir, "route-tab-course.png"));

		// part of the course off the map: only the legs that show get a head
		BufferedImage small = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
		Graphics2D gs = small.createGraphics();
		MapViewport far = new MapViewport(3500, 3500, 9);
		far.setSize(200, 200);
		gs.setClip(0, 0, 200, 200);
		assertEquals(0, RouteOverlay.drawCourse(gs, far, c.points, gs.getClipBounds()));
		// only the course's end shows: just the last leg's head; only its start shows: none (the legs end elsewhere)
		int n = c.points.length / 2;
		int ex = (int) vp.screenX(c.points[2 * n - 2] + 0.5);
		int ey = (int) vp.screenY(c.points[2 * n - 1] + 0.5);
		assertEquals(1, RouteOverlay.drawCourse(gs, vp, c.points, new java.awt.Rectangle(ex - 6, ey - 6, 12, 12)));
		int bx = (int) vp.screenX(c.points[0] + 0.5);
		int by = (int) vp.screenY(c.points[1] + 0.5);
		assertEquals(0, RouteOverlay.drawCourse(gs, vp, c.points, new java.awt.Rectangle(bx - 6, by - 6, 12, 12)));
		gs.dispose();
	}
}

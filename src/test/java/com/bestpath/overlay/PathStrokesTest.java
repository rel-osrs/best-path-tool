package com.bestpath.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import java.awt.BasicStroke;
import java.awt.Stroke;
import org.junit.Test;

/** The path line and glow thickness settings. */
public class PathStrokesTest
{
	private static float width(Stroke s)
	{
		return ((BasicStroke) s).getLineWidth();
	}

	@Test
	public void theUsualThicknessDrawsAsBefore()
	{
		PathStrokes p = PathStrokes.of(PathStrokes.USUAL);
		assertEquals(1f, width(p.thin), 0f);
		assertEquals(2f, width(p.thick), 0f);
		assertEquals(3f, width(p.walk), 0f);
		assertEquals(1.0, p.arrowScale, 0.0);
	}

	@Test
	public void thickerAndThinnerLines()
	{
		assertEquals(1f, width(PathStrokes.of(1).thin), 0f);
		assertEquals(1f, width(PathStrokes.of(1).thick), 0f);
		assertEquals(1f, width(PathStrokes.of(1).walk), 0f);
		assertEquals(1.0, PathStrokes.of(1).arrowScale, 0.0); // arrow heads never smaller than before
		assertEquals(8f, width(PathStrokes.of(10).thin), 0f);
		assertEquals(10f, width(PathStrokes.of(10).walk), 0f);
		assertEquals(2.0, PathStrokes.of(6).arrowScale, 0.0);
	}

	@Test
	public void outOfRangeIsClamped()
	{
		assertSame(PathStrokes.of(1), PathStrokes.of(0));
		assertSame(PathStrokes.of(10), PathStrokes.of(99));
	}
}

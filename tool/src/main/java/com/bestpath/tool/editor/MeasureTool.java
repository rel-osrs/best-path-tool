package com.bestpath.tool.editor;

import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.util.Locale;

/**
 * Drag between two tiles to measure: tiles apart, and the walking ticks if
 * nothing were in the way (8-direction walking, 1 tile per tick).
 */
public final class MeasureTool extends EditorTool
{
	private int[] a;
	private int[] b;

	public MeasureTool(EditorContext ctx)
	{
		super(ctx, "Measure", 'm', "Drag between two tiles to measure the distance (ticks if walking with nothing in the way).");
	}

	@Override
	public boolean pressed(MapView view, double wx, double wy, MouseEvent e)
	{
		a = new int[]{tile(wx), tile(wy), view.getPlane()};
		b = a.clone();
		return true;
	}

	@Override
	public void dragged(MapView view, double wx, double wy, MouseEvent e)
	{
		b = new int[]{tile(wx), tile(wy), view.getPlane()};
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		if (a == null || a[2] != view.getPlane())
		{
			return;
		}
		MapViewport vp = view.getViewport();
		double x0 = vp.screenX(a[0] + 0.5);
		double y0 = vp.screenY(a[1] + 0.5);
		double x1 = vp.screenX(b[0] + 0.5);
		double y1 = vp.screenY(b[1] + 0.5);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{8f, 5f}, 0f));
		g.setColor(new Color(0xffe066));
		g.drawLine((int) x0, (int) y0, (int) x1, (int) y1);
		g.setStroke(new BasicStroke(1f));
		outlineTile(g, vp, a[0], a[1], new Color(0xffe066), 2f);
		outlineTile(g, vp, b[0], b[1], new Color(0xffe066), 2f);
		int dx = Math.abs(b[0] - a[0]);
		int dy = Math.abs(b[1] - a[1]);
		label(g, String.format(Locale.ROOT, "%d x %d tiles  =  %d ticks walking straight", dx, dy, Math.max(dx, dy)),
			x1 + 12, y1 - 8, new Color(0xffe066));
	}
}

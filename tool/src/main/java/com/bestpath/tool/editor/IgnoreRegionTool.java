package com.bestpath.tool.editor;

import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

/**
 * Ignores a whole 64x64 region, on all planes: it turns red (hatched) at
 * once and is left out of the plugin's data completely. Click again to take
 * it back. Takes effect at once like every other edit.
 */
public final class IgnoreRegionTool extends EditorTool
{
	private int hoverRegion = -1;
	private double hoverWx;
	private double hoverWy;

	public IgnoreRegionTool(EditorContext ctx)
	{
		super(ctx, "Ignore region", 'i', "Click a 64x64 region to leave it out of the export completely (red, all planes). "
			+ "Click again to take it back.");
	}

	@Override
	public boolean pressed(MapView view, double wx, double wy, MouseEvent e)
	{
		int x = tile(wx);
		int y = tile(wy);
		Edit like = new Edit();
		like.type = Edit.Type.IGNORE_REGION;
		like.x = x & ~63;
		like.y = y & ~63;
		if (ctx.edits.cancel(like))
		{
			return true; // clicking again takes a change not built in yet back
		}
		boolean appliedIgnored = ctx.state != null && ctx.state.getIgnoredRegions().contains(like.regionId());
		like.on = !appliedIgnored;
		ctx.edits.add(like);
		return true;
	}

	@Override
	public void moved(MapView view, double wx, double wy)
	{
		hoverWx = wx;
		hoverWy = wy;
		hoverRegion = (tile(wx) >> 6) << 8 | (tile(wy) >> 6);
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		if (hoverRegion < 0)
		{
			return;
		}
		MapViewport vp = view.getViewport();
		int rx = hoverRegion >> 8;
		int ry = hoverRegion & 0xFF;
		int x0 = (int) vp.screenX(rx * 64);
		int x1 = (int) vp.screenX((rx + 1) * 64);
		int yTop = (int) vp.screenY((ry + 1) * 64);
		int yBot = (int) vp.screenY(ry * 64);
		g.setColor(new Color(255, 255, 255, 200));
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{8f, 6f}, 0f));
		g.drawRect(x0, yTop, x1 - x0, yBot - yTop);
		g.setStroke(new BasicStroke(1f));
		boolean ignored = ctx.effectiveIgnoredRegions().contains(hoverRegion);
		label(g, "Region " + hoverRegion + (ignored ? " - ignored" : "") + " - click to " + (ignored ? "stop ignoring" : "ignore"),
			vp.screenX(hoverWx) + 14, vp.screenY(hoverWy) + 4, Color.WHITE);
	}
}

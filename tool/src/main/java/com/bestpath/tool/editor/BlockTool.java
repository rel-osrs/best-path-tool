package com.bestpath.tool.editor;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

/**
 * Brush: the left button paints tiles blocked, the right button paints them
 * walkable again (brush size in the option bar). The middle button pans.
 */
public final class BlockTool extends EditorTool
{
	private int hoverX = Integer.MIN_VALUE;
	private int hoverY;
	private int hoverPlane;
	private boolean paintBlocked;

	public BlockTool(EditorContext ctx)
	{
		super(ctx, "Block brush", 'b', "Left click/drag: make blocked. Right click/drag: make walkable. Middle-drag moves the map.");
	}

	private boolean appliedBlocked(int x, int y, int plane)
	{
		return (ctx.grid.get(x, y, plane) & CollisionFlags.BLOCKED) != 0;
	}

	private void paintAt(int cx, int cy, int plane)
	{
		int r = ctx.brushSize / 2;
		for (int x = cx - r; x <= cx + r; x++)
		{
			for (int y = cy - r; y <= cy + r; y++)
			{
				Edit e = new Edit();
				e.type = Edit.Type.TILE;
				e.x = x;
				e.y = y;
				e.plane = plane;
				e.on = paintBlocked;
				if (paintBlocked == appliedBlocked(x, y, plane))
				{
					ctx.edits.cancel(e);
				}
				else if (ctx.edits.findUnbuilt(e) == null || ctx.edits.findUnbuilt(e).on != paintBlocked)
				{
					ctx.edits.add(e);
				}
			}
		}
	}

	@Override
	public boolean pressed(MapView view, double wx, double wy, MouseEvent e)
	{
		if (!ctx.hasData())
		{
			return true;
		}
		paintBlocked = !javax.swing.SwingUtilities.isRightMouseButton(e);
		paintAt(tile(wx), tile(wy), view.getPlane());
		return true;
	}

	@Override
	public boolean usesRightButton()
	{
		return true;
	}

	@Override
	public void dragged(MapView view, double wx, double wy, MouseEvent e)
	{
		moved(view, wx, wy);
		if (ctx.hasData())
		{
			paintAt(tile(wx), tile(wy), view.getPlane());
		}
	}

	@Override
	public void moved(MapView view, double wx, double wy)
	{
		hoverX = tile(wx);
		hoverY = tile(wy);
		hoverPlane = view.getPlane();
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		if (hoverX == Integer.MIN_VALUE || hoverPlane != view.getPlane())
		{
			return;
		}
		MapViewport vp = view.getViewport();
		int r = ctx.brushSize / 2;
		int x0 = (int) Math.floor(vp.screenX(hoverX - r));
		int y0 = (int) Math.floor(vp.screenY(hoverY + r + 1));
		int x1 = (int) Math.floor(vp.screenX(hoverX + r + 1));
		int y1 = (int) Math.floor(vp.screenY(hoverY - r));
		g.setStroke(new BasicStroke(2f));
		g.setColor(Color.WHITE);
		g.drawRect(x0, y0, x1 - x0, y1 - y0);
		g.setStroke(new BasicStroke(1f));
	}
}

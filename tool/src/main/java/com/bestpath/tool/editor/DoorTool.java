package com.bestpath.tool.editor;

import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

/**
 * Marks a door or gate shut (for doors a quest or requirement keeps closed;
 * the change records no requirement) or open again. While this tool is
 * active, every door in view is marked: green = open, red = shut.
 */
public final class DoorTool extends EditorTool
{
	public DoorTool(EditorContext ctx)
	{
		super(ctx, "Door", 'd', "Click a door or gate to mark it shut (red) or open (green). Doors are open unless you mark them.");
	}

	/** Nearest door within one tile, or null: {x, y, plane, objectId, type, rot}. */
	private int[] doorAt(int x, int y, int plane)
	{
		int[] best = null;
		int bestD = 2;
		for (int[] d : ctx.grid.getDoors())
		{
			if (d[2] != plane)
			{
				continue;
			}
			int dist = Math.max(Math.abs(d[0] - x), Math.abs(d[1] - y));
			if (dist < bestD)
			{
				bestD = dist;
				best = d;
			}
		}
		return best;
	}

	private Edit like(int[] d, boolean shut)
	{
		Edit e = new Edit();
		e.type = Edit.Type.DOOR;
		e.x = d[0];
		e.y = d[1];
		e.plane = d[2];
		e.objectId = d[3];
		e.on = shut;
		return e;
	}

	private boolean currentShut(int[] d)
	{
		Edit p = ctx.edits.findUnbuilt(like(d, false));
		return p != null ? p.on : ctx.state.isDoorShut(d[0], d[1], d[2], d[3]);
	}

	@Override
	public boolean pressed(MapView view, double wx, double wy, MouseEvent e)
	{
		if (!ctx.hasData())
		{
			return true;
		}
		int[] d = doorAt(tile(wx), tile(wy), view.getPlane());
		if (d == null)
		{
			return true;
		}
		boolean shut = !currentShut(d);
		if (shut == ctx.state.isDoorShut(d[0], d[1], d[2], d[3]))
		{
			ctx.edits.cancel(like(d, shut));
		}
		else
		{
			ctx.edits.add(like(d, shut));
		}
		return true;
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		if (!ctx.hasData())
		{
			return;
		}
		MapViewport vp = view.getViewport();
		if (vp.getScale() < 2)
		{
			return;
		}
		double minX = vp.worldX(0) - 1;
		double maxX = vp.worldX(view.getWidth()) + 1;
		double minY = vp.worldY(view.getHeight()) - 1;
		double maxY = vp.worldY(0) + 1;
		for (int[] d : ctx.grid.getDoors())
		{
			if (d[2] != view.getPlane() || d[0] < minX || d[0] > maxX || d[1] < minY || d[1] > maxY)
			{
				continue;
			}
			outlineTile(g, vp, d[0], d[1], currentShut(d) ? new Color(0xff4d4d) : new Color(0x2ecc71), 2.5f);
		}
	}
}

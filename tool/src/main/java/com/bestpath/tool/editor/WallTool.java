package com.bestpath.tool.editor;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Adds or removes a wall on one side of a tile: click near a tile edge.
 * Dragging along edges paints the same change (add or remove, decided by
 * the first edge). Walls are always stored as the north or east side of a
 * tile, so each wall has exactly one identity.
 */
public final class WallTool extends EditorTool
{
	/** How close (fraction of a tile) to an edge the mouse must be. */
	private static final double EDGE = 0.32;

	private int[] hover;
	private boolean dragDesired;
	private final Set<Long> painted = new HashSet<>();

	public WallTool(EditorContext ctx)
	{
		super(ctx, "Wall", 'w', "Click near a tile edge to add or remove a wall there. Drag along edges to paint.");
	}

	/** {x, y, plane, side} with side NORTH or EAST, or null if not near an edge. */
	private int[] edge(MapView view, double wx, double wy)
	{
		int x = tile(wx);
		int y = tile(wy);
		double fx = wx - x;
		double fy = wy - y;
		double[] d = {1 - fy, 1 - fx, fy, fx}; // north, east, south, west
		int side = 0;
		for (int i = 1; i < 4; i++)
		{
			if (d[i] < d[side])
			{
				side = i;
			}
		}
		if (d[side] > EDGE)
		{
			return null;
		}
		if (side == Edit.SOUTH)
		{
			return new int[]{x, y - 1, view.getPlane(), Edit.NORTH};
		}
		if (side == Edit.WEST)
		{
			return new int[]{x - 1, y, view.getPlane(), Edit.EAST};
		}
		return new int[]{x, y, view.getPlane(), side};
	}

	private boolean appliedWall(int[] e)
	{
		int flag = e[3] == Edit.NORTH ? CollisionFlags.NORTH : CollisionFlags.EAST;
		return (ctx.grid.get(e[0], e[1], e[2]) & flag) != 0;
	}

	private Edit like(int[] e, boolean on)
	{
		Edit ed = new Edit();
		ed.type = Edit.Type.WALL;
		ed.x = e[0];
		ed.y = e[1];
		ed.plane = e[2];
		ed.side = e[3];
		ed.on = on;
		return ed;
	}

	/** The wall after all changes, including those not built in yet. */
	private boolean current(int[] e)
	{
		Edit p = ctx.edits.findUnbuilt(like(e, false));
		return p != null ? p.on : appliedWall(e);
	}

	private void set(int[] e, boolean wall)
	{
		long k = ((long) e[0] << 24) ^ ((long) e[1] << 4) ^ (e[2] << 2) ^ e[3];
		if (!painted.add(k))
		{
			return;
		}
		if (wall == appliedWall(e))
		{
			ctx.edits.cancel(like(e, wall));
		}
		else
		{
			ctx.edits.add(like(e, wall));
		}
	}

	@Override
	public boolean pressed(MapView view, double wx, double wy, MouseEvent e)
	{
		painted.clear();
		int[] ed = ctx.hasData() ? edge(view, wx, wy) : null;
		if (ed != null)
		{
			dragDesired = !current(ed);
			set(ed, dragDesired);
		}
		return true;
	}

	@Override
	public void dragged(MapView view, double wx, double wy, MouseEvent e)
	{
		int[] ed = ctx.hasData() ? edge(view, wx, wy) : null;
		hover = ed;
		if (ed != null && !painted.isEmpty())
		{
			set(ed, dragDesired);
		}
	}

	@Override
	public void moved(MapView view, double wx, double wy)
	{
		hover = ctx.hasData() ? edge(view, wx, wy) : null;
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		if (hover == null || hover[2] != view.getPlane())
		{
			return;
		}
		drawEdge(g, view.getViewport(), hover[0], hover[1], hover[3], new Color(0x00e5ff), 4f);
	}

	/** Draws the north or east edge of a tile. */
	static void drawEdge(Graphics2D g, MapViewport vp, int x, int y, int side, Color c, float width)
	{
		double x0 = vp.screenX(side == Edit.EAST ? x + 1 : x);
		double y0 = vp.screenY(y + 1);
		double x1 = vp.screenX(x + 1);
		double y1 = vp.screenY(side == Edit.EAST ? y : y + 1);
		g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(c);
		g.drawLine((int) x0, (int) y0, (int) x1, (int) y1);
		g.setStroke(new BasicStroke(1f));
	}
}

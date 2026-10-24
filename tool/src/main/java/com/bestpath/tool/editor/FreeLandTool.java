package com.bestpath.tool.editor;

import com.bestpath.core.collision.FreeLand;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

/**
 * Free-to-play land (mark the free areas; the rest is members only). Drag a rectangle:
 * the left button marks it free (green), the right button members only. Every plane at once. The
 * "Free-to-play land" layer shows the result; Ctrl+Z undoes a rectangle.
 */
public final class FreeLandTool extends EditorTool
{
	private int[] from;
	private int[] to;
	private boolean markFree;
	private int hoverX = Integer.MIN_VALUE;
	private int hoverY;
	private double hoverWx;
	private double hoverWy;

	public FreeLandTool(EditorContext ctx)
	{
		super(ctx, "Free land", 'f', "Free-to-play land (green layer). Left-drag a rectangle: free to play. Right-drag: members only. "
			+ "Every plane. Middle-drag moves the map.");
	}

	@Override
	public boolean usesRightButton()
	{
		return true;
	}

	@Override
	public boolean pressed(MapView view, double wx, double wy, MouseEvent e)
	{
		markFree = !javax.swing.SwingUtilities.isRightMouseButton(e);
		from = new int[]{tile(wx), tile(wy)};
		to = from.clone();
		return true;
	}

	@Override
	public void dragged(MapView view, double wx, double wy, MouseEvent e)
	{
		moved(view, wx, wy);
		if (from != null)
		{
			to = new int[]{tile(wx), tile(wy)};
		}
	}

	@Override
	public void released(MapView view, double wx, double wy, MouseEvent e)
	{
		if (from == null)
		{
			return;
		}
		to = new int[]{tile(wx), tile(wy)};
		Edit edit = new Edit();
		edit.type = Edit.Type.FREE_LAND;
		edit.x = from[0];
		edit.y = from[1];
		edit.toX = to[0];
		edit.toY = to[1];
		edit.plane = view.getPlane();
		edit.on = markFree;
		from = null;
		to = null;
		ctx.edits.add(edit);
	}

	@Override
	public void deactivate()
	{
		from = null;
		to = null;
	}

	@Override
	public void moved(MapView view, double wx, double wy)
	{
		hoverX = tile(wx);
		hoverY = tile(wy);
		hoverWx = wx;
		hoverWy = wy;
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		MapViewport vp = view.getViewport();
		if (from != null && to != null)
		{
			int xa = Math.min(from[0], to[0]), xb = Math.max(from[0], to[0]);
			int ya = Math.min(from[1], to[1]), yb = Math.max(from[1], to[1]);
			int sx0 = (int) Math.floor(vp.screenX(xa));
			int sx1 = (int) Math.floor(vp.screenX(xb + 1));
			int syTop = (int) Math.floor(vp.screenY(yb + 1));
			int syBot = (int) Math.floor(vp.screenY(ya));
			Color c = markFree ? new Color(60, 220, 90) : new Color(230, 70, 70);
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
			g.fillRect(sx0, syTop, Math.max(1, sx1 - sx0), Math.max(1, syBot - syTop));
			g.setColor(c);
			g.setStroke(new BasicStroke(2f));
			g.drawRect(sx0, syTop, Math.max(1, sx1 - sx0), Math.max(1, syBot - syTop));
			g.setStroke(new BasicStroke(1f));
			label(g, (markFree ? "Free to play: " : "Members only: ") + (xb - xa + 1) + " x " + (yb - ya + 1) + " tiles",
				vp.screenX(hoverWx) + 14, vp.screenY(hoverWy) + 4, c);
			return;
		}
		if (hoverX == Integer.MIN_VALUE)
		{
			return;
		}
		FreeLand land = ctx.freeLand();
		boolean free = land.isFree(hoverX, hoverY);
		outlineTile(g, vp, hoverX, hoverY, free ? new Color(60, 220, 90) : new Color(230, 70, 70), 2f);
		label(g, hoverX + ", " + hoverY + (free ? " - free to play" : " - members only"), vp.screenX(hoverWx) + 14, vp.screenY(hoverWy) + 4,
			Color.WHITE);
	}
}

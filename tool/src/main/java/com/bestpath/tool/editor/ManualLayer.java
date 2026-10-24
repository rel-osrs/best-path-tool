package com.bestpath.tool.editor;

import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "Manual changes" layer: everything you changed by hand, hatched, so it
 * stands apart from what came from the cache. All changes in effect
 * (ones not built in yet also get a dashed outline from the edit overlay).
 * <ul>
 * <li>orange hatching: something blocking you added (blocked tile, wall, shut door);</li>
 * <li>cyan hatching: something you removed (tile made walkable, wall taken away, door opened);</li>
 * <li>grey hatching: objects you marked "not a transport".</li>
 * </ul>
 * Transports are on the "Confirmed transports" layer; hidden sub-regions
 * and ignored regions are on the "Ignored sub-regions" layer.
 */
public final class ManualLayer implements MapView.Overlay
{
	static final Color ADDED = new Color(0xff8c00);
	static final Color REMOVED = new Color(0x00e5ff);
	static final Color REJECTED = new Color(0xb0b0b0);

	private final EditorContext ctx;
	private boolean show;
	private final Map<Color, TexturePaint> hatches = new LinkedHashMap<>();

	public ManualLayer(EditorContext ctx)
	{
		this.ctx = ctx;
	}

	public void setShow(boolean show)
	{
		this.show = show;
		ctx.repaintAll();
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		if (!show)
		{
			return;
		}
		MapViewport vp = view.getViewport();
		int plane = view.getPlane();
		double minX = vp.worldX(0) - 2;
		double maxX = vp.worldX(view.getWidth()) + 2;
		double minY = vp.worldY(view.getHeight()) - 2;
		double maxY = vp.worldY(0) + 2;
		for (Edit e : current())
		{
			switch (e.type)
			{
				case TILE:
					if (in(e.x, e.y, e.plane, plane, minX, maxX, minY, maxY))
					{
						tile(g, vp, e.x, e.y, e.on ? ADDED : REMOVED);
					}
					break;
				case WALL:
					if (in(e.x, e.y, e.plane, plane, minX, maxX, minY, maxY))
					{
						side(g, vp, e.x, e.y, e.side, e.on ? ADDED : REMOVED);
					}
					break;
				case DOOR:
					if (in(e.x, e.y, e.plane, plane, minX, maxX, minY, maxY))
					{
						tile(g, vp, e.x, e.y, e.on ? ADDED : REMOVED);
					}
					break;
				case REJECT:
					if (e.on && in(e.x, e.y, e.plane, plane, minX, maxX, minY, maxY))
					{
						tile(g, vp, e.x, e.y, REJECTED);
					}
					break;
				default:
					break;
			}
		}
		g.setStroke(new BasicStroke(1f));
	}

	/**
	 * Changes in effect, oldest first; for each thing changed
	 * only the latest change counts (a tile blocked and then unblocked shows
	 * as unblocked).
	 */
	private List<Edit> current()
	{
		Map<String, Edit> last = new LinkedHashMap<>();
		List<Edit> all = ctx.edits.active(); // built in or not
		for (Edit e : all)
		{
			switch (e.type)
			{
				case TILE:
				case DOOR:
					last.put(e.type + ":" + e.x + "," + e.y + "," + e.plane + ":" + e.objectId, e);
					break;
				case WALL:
					last.put("WALL:" + e.x + "," + e.y + "," + e.plane + ":" + e.side, e);
					break;
				case REJECT:
					last.put("REJECT:" + e.target, e);
					break;
				default:
					break;
			}
		}
		return new ArrayList<>(last.values());
	}

	private static boolean in(int x, int y, int p, int plane, double minX, double maxX, double minY, double maxY)
	{
		return p == plane && x >= minX && x <= maxX && y >= minY && y <= maxY;
	}

	private void tile(Graphics2D g, MapViewport vp, int x, int y, Color c)
	{
		double x0 = vp.screenX(x);
		double x1 = vp.screenX(x + 1);
		double yTop = vp.screenY(y + 1);
		double yBot = vp.screenY(y);
		if (x1 - x0 < 4)
		{
			// zoomed far out: a small solid mark so it can still be found
			g.setColor(c);
			g.fillRect((int) x0 - 1, (int) yTop - 1, 3, 3);
			return;
		}
		Rectangle2D r = new Rectangle2D.Double(x0, yTop, x1 - x0, yBot - yTop);
		Paint old = g.getPaint();
		g.setPaint(hatch(c));
		g.fill(r);
		g.setPaint(old);
		g.setColor(c);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(r);
	}

	/** A hatched band along one side of a tile (the wall side). */
	private void side(Graphics2D g, MapViewport vp, int x, int y, int side, Color c)
	{
		double x0 = vp.screenX(x);
		double x1 = vp.screenX(x + 1);
		double yTop = vp.screenY(y + 1);
		double yBot = vp.screenY(y);
		double w = x1 - x0;
		if (w < 4)
		{
			g.setColor(c);
			g.fillRect((int) x0 - 1, (int) yTop - 1, 3, 3);
			return;
		}
		double t = Math.max(3, w * 0.35);
		Rectangle2D r;
		switch (side)
		{
			case Edit.NORTH:
				r = new Rectangle2D.Double(x0, yTop, w, t);
				break;
			case Edit.SOUTH:
				r = new Rectangle2D.Double(x0, yBot - t, w, t);
				break;
			case Edit.EAST:
				r = new Rectangle2D.Double(x1 - t, yTop, t, yBot - yTop);
				break;
			default:
				r = new Rectangle2D.Double(x0, yTop, t, yBot - yTop);
				break;
		}
		Paint old = g.getPaint();
		g.setPaint(hatch(c));
		g.fill(r);
		g.setPaint(old);
		g.setColor(c);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(r);
	}

	private TexturePaint hatch(Color c)
	{
		return hatches.computeIfAbsent(c, k ->
		{
			BufferedImage t = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
			Graphics2D tg = t.createGraphics();
			tg.setColor(new Color(0, 0, 0, 60));
			tg.fillRect(0, 0, 8, 8);
			tg.setColor(k);
			tg.setStroke(new BasicStroke(2f));
			tg.drawLine(0, 8, 8, 0);
			tg.drawLine(-4, 4, 4, -4);
			tg.drawLine(4, 12, 12, 4);
			tg.dispose();
			return new TexturePaint(t, new Rectangle2D.Double(0, 0, 8, 8));
		});
	}
}

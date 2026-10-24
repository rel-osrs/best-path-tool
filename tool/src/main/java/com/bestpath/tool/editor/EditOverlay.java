package com.bestpath.tool.editor;

import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/**
 * Draws, on every map view: changes not built into the data yet (cyan = added, red = removed,
 * dashed outlines), transports you created or confirmed (solid; pending ones
 * dashed yellow; category {@link TransportRecord#NEEDS_FIX} red), the "test in game" list (dashed purple), a dragged but
 * undecided suggestion (dashed white), and the round handles of the selection.
 */
public final class EditOverlay implements MapView.Overlay
{
	static final Color ADD = new Color(0x00e5ff);
	static final Color REMOVE = new Color(0xff4d4d);
	static final Color CONFIRMED = new Color(0x06d6a0);
	static final Color PENDING = new Color(0xffd166);
	static final Color DELETED = new Color(0x9e9e9e);
	static final Color TEST = new Color(0xc77dff);
	static final Color NEEDS_FIX = new Color(0xff2a2a);

	private final EditorContext ctx;
	private final TransportTool transportTool;

	public EditOverlay(EditorContext ctx, TransportTool transportTool)
	{
		this.ctx = ctx;
		this.transportTool = transportTool;
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		MapViewport vp = view.getViewport();
		int plane = view.getPlane();
		double minX = vp.worldX(0) - 2;
		double maxX = vp.worldX(view.getWidth()) + 2;
		double minY = vp.worldY(view.getHeight()) - 2;
		double maxY = vp.worldY(0) + 2;
		paintUnbuiltEdits(g, vp, plane, minX, maxX, minY, maxY);
		EditorContext.Selection sel = ctx.getSelection();
		paintTransports(g, view, vp, plane, sel);
		paintTestList(g, vp, plane, sel);
		// a dragged (undecided) suggestion
		if (sel != null && sel.draft != null && transportTool.preview == null)
		{
			drawTransport(g, vp, plane, sel.draft, Color.WHITE, true);
		}
		if (transportTool.preview != null)
		{
			drawTransport(g, vp, plane, transportTool.preview, PENDING, true);
		}
		if (sel != null)
		{
			paintHandles(g, vp, plane, sel);
		}
	}

	/** Wall, tile, door and hide changes not built into the data yet, inside the visible area. */
	private void paintUnbuiltEdits(Graphics2D g, MapViewport vp, int plane, double minX, double maxX, double minY, double maxY)
	{
		for (Edit e : ctx.edits.getUnbuilt())
		{
			if (e.plane != plane || e.x < minX || e.x > maxX || e.y < minY || e.y > maxY)
			{
				continue;
			}
			switch (e.type)
			{
				case WALL:
					if (e.on)
					{
						WallTool.drawEdge(g, vp, e.x, e.y, e.side, ADD, 4f);
					}
					else
					{
						WallTool.drawEdge(g, vp, e.x, e.y, e.side, REMOVE, 4f);
					}
					break;
				case TILE:
					fillTile(g, vp, e.x, e.y, e.on ? new Color(255, 77, 77, 120) : new Color(0, 229, 255, 110));
					break;
				case DOOR:
					EditorTool.outlineTile(g, vp, e.x, e.y, e.on ? REMOVE : CONFIRMED, 3f);
					break;
				case HIDE:
					EditorTool.outlineTile(g, vp, e.x, e.y, REMOVE, 3f);
					EditorTool.label(g, e.on ? "hide" : "unhide", vp.screenX(e.x + 1) + 3, vp.screenY(e.y) - 3, REMOVE);
					break;
				default:
					break;
			}
		}
	}

	/** Your transports (built in when that layer is on, pending always); the selected one with its full line. */
	private void paintTransports(Graphics2D g, MapView view, MapViewport vp, int plane, EditorContext.Selection sel)
	{
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			if (!t.pending && !view.isShowConfirmed())
			{
				continue;
			}
			if (transportTool.preview != null && transportTool.preview.id != null && transportTool.preview.id.equals(t.record.id))
			{
				continue; // being dragged: drawn as the preview instead
			}
			Color c = t.deleted ? DELETED : t.pending ? PENDING
				: TransportRecord.NEEDS_FIX.equals(t.record.category) ? NEEDS_FIX : CONFIRMED;
			if (sel != null && sel.record != null && sel.record.id.equals(t.record.id))
			{
				drawTransport(g, vp, plane, t.record, c, t.pending || t.deleted); // selected: the full line
			}
			else
			{
				drawCompact(g, vp, plane, t.record, c);
			}
		}
	}

	/** The "test in game" list (purple), always shown: these are your own open items. */
	private void paintTestList(Graphics2D g, MapViewport vp, int plane, EditorContext.Selection sel)
	{
		for (java.util.Map.Entry<String, TransportRecord> t : ctx.effectiveTestLater().entrySet())
		{
			TransportRecord r = t.getValue();
			if (r == null || (sel != null && sel.draft != null && sel.candidate != null && t.getKey().equals(sel.candidate.id())))
			{
				continue;
			}
			drawCompact(g, vp, plane, r, TEST);
		}
	}

	/** The round handles of the selected transport (suggestions included). */
	private void paintHandles(Graphics2D g, MapViewport vp, int plane, EditorContext.Selection sel)
	{
		int[] s = sel.start(ctx);
		int[] en = sel.end();
		// S = start, E = end; B on both ends when it works both ways
		TransportRecord r = sel.record != null ? sel.record : sel.draft;
		TransportRecord.Kind kind = r != null ? r.kind
			: ctx.newKind == TransportRecord.Kind.TELEPORT ? TransportRecord.Kind.TWO_WAY : ctx.newKind;
		boolean both = kind == TransportRecord.Kind.TWO_WAY;
		handle(g, vp, s, plane, both ? "B" : "S");
		handle(g, vp, en, plane, both ? "B" : "E");
	}

	private static void fillTile(Graphics2D g, MapViewport vp, int x, int y, Color c)
	{
		int sx = (int) Math.floor(vp.screenX(x));
		int sy = (int) Math.floor(vp.screenY(y + 1));
		int s = (int) Math.max(2, Math.ceil(vp.getScale()));
		g.setColor(c);
		g.fillRect(sx, sy, s, s);
	}

	private static void handle(Graphics2D g, MapViewport vp, int[] t, int plane, String text)
	{
		if (t == null || t[2] != plane)
		{
			return;
		}
		double cx = vp.screenX(t[0] + 0.5);
		double cy = vp.screenY(t[1] + 0.5);
		int r = (int) Math.max(6, Math.min(14, vp.getScale() * 0.5));
		g.setColor(new Color(0, 0, 0, 200));
		g.fillOval((int) cx - r - 2, (int) cy - r - 2, 2 * r + 4, 2 * r + 4);
		g.setColor(Color.WHITE);
		g.fillOval((int) cx - r, (int) cy - r, 2 * r, 2 * r);
		// the letter in the middle of the circle
		g.setFont(g.getFont().deriveFont(Font.BOLD, (float) Math.max(9, r * 1.3)));
		FontMetrics fm = g.getFontMetrics();
		g.setColor(Color.BLACK);
		g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
	}

	private static final BasicStroke THIN_DASHED = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
		new float[]{6f, 4f}, 0f);

	/**
	 * A transport that is not selected, drawn like a suggestion: a box on its
	 * start tile and a thin dashed arrow to its end (or "up/down to plane N"
	 * when the end is on another plane or far away). Selecting it shows the
	 * full line with its round handles.
	 */
	static void drawCompact(Graphics2D g, MapViewport vp, int plane, TransportRecord r, Color c)
	{
		if (vp.getScale() < 1.5)
		{
			// zoomed out: a dot where it starts (a teleport: where it arrives), so no transport is ever hidden
			compactDot(g, vp, plane, r, c);
			return;
		}
		g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
		if (!r.hasStart())
		{
			if (r.endPlane == plane)
			{
				star(g, vp.screenX(r.endX + 0.5), vp.screenY(r.endY + 0.5), Math.max(5, Math.min(9, vp.getScale() * 0.4)), c);
			}
			return;
		}
		boolean startHere = r.startPlane == plane;
		boolean endHere = r.endPlane == plane;
		if (!startHere && !endHere)
		{
			return;
		}
		// a box on the start (and on the end too when it works both ways);
		// a one-way transport seen from its end's plane gets a small ring where it arrives
		boolean both = r.kind == TransportRecord.Kind.TWO_WAY;
		if (startHere)
		{
			compactBox(g, vp, r.startX, r.startY, c);
		}
		if (endHere && both)
		{
			compactBox(g, vp, r.endX, r.endY, c);
		}
		else if (endHere && !startHere)
		{
			compactRing(g, vp, r.endX, r.endY, c);
		}
		boolean near = r.startPlane == r.endPlane && Math.max(Math.abs(r.endX - r.startX), Math.abs(r.endY - r.startY)) <= 64;
		if (startHere && near)
		{
			compactArrow(g, vp, r, c, both);
		}
		else if (vp.getScale() >= 3)
		{
			if (startHere)
			{
				EditorTool.label(g, farText(plane, r.endX, r.endY, r.endPlane), vp.screenX(r.startX), vp.screenY(r.startY + 1) - 4, c);
			}
			if (endHere && both)
			{
				EditorTool.label(g, farText(plane, r.startX, r.startY, r.startPlane), vp.screenX(r.endX), vp.screenY(r.endY + 1) - 4, c);
			}
		}
		g.setStroke(new BasicStroke(1f));
	}

	/** A small dot on the start tile (a teleport: on its end tile) when that is on this plane. */
	private static void compactDot(Graphics2D g, MapViewport vp, int plane, TransportRecord r, Color c)
	{
		boolean start = r.hasStart() && r.startPlane == plane;
		if (start || !r.hasStart() && r.endPlane == plane)
		{
			double x = vp.screenX((start ? r.startX : r.endX) + 0.5);
			double y = vp.screenY((start ? r.startY : r.endY) + 0.5);
			g.setColor(c);
			g.fill(new java.awt.geom.Ellipse2D.Double(x - 1.5, y - 1.5, 3, 3));
		}
	}

	/** A small ring where a one-way transport arrives. */
	private static void compactRing(Graphics2D g, MapViewport vp, int x, int y, Color c)
	{
		int rr = (int) Math.max(3, Math.min(8, vp.getScale() * 0.35));
		double cx = vp.screenX(x + 0.5);
		double cy = vp.screenY(y + 0.5);
		g.setStroke(new BasicStroke(2f));
		g.setColor(c);
		g.drawOval((int) cx - rr, (int) cy - rr, 2 * rr, 2 * rr);
	}

	/** A thin dashed line from start to end with an arrow head (on both ends when it works both ways). */
	private static void compactArrow(Graphics2D g, MapViewport vp, TransportRecord r, Color c, boolean both)
	{
		double sx = vp.screenX(r.startX + 0.5);
		double sy = vp.screenY(r.startY + 0.5);
		double ex = vp.screenX(r.endX + 0.5);
		double ey = vp.screenY(r.endY + 0.5);
		g.setStroke(THIN_DASHED);
		g.setColor(c);
		g.draw(new Line2D.Double(sx, sy, ex, ey));
		g.setStroke(new BasicStroke(1f));
		arrow(g, sx, sy, ex, ey);
		if (both)
		{
			arrow(g, ex, ey, sx, sy);
		}
	}

	private static void compactBox(Graphics2D g, MapViewport vp, int x, int y, Color c)
	{
		double x0 = vp.screenX(x);
		double x1 = vp.screenX(x + 1);
		double yTop = vp.screenY(y + 1);
		double yBot = vp.screenY(y);
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
		g.fill(new java.awt.geom.Rectangle2D.Double(x0, yTop, x1 - x0, yBot - yTop));
		g.setStroke(new BasicStroke(2f));
		g.setColor(c);
		g.draw(new java.awt.geom.Rectangle2D.Double(x0, yTop, x1 - x0, yBot - yTop));
	}

	/** Where the other end is, for an end that is far away or on another plane. */
	private static String farText(int plane, int x, int y, int toPlane)
	{
		return toPlane > plane ? "↑ plane " + toPlane : toPlane < plane ? "↓ plane " + toPlane : "→ " + x + "," + y;
	}

	/** One transport: a line with arrow(s); a teleport is a star at its end. */
	static void drawTransport(Graphics2D g, MapViewport vp, int plane, TransportRecord r, Color c, boolean dashed)
	{
		boolean startHere = r.hasStart() && r.startPlane == plane;
		boolean endHere = r.endPlane == plane;
		if (!startHere && !endHere)
		{
			return;
		}
		double ex = vp.screenX(r.endX + 0.5);
		double ey = vp.screenY(r.endY + 0.5);
		g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
		FontMetrics fm = g.getFontMetrics();
		if (!r.hasStart())
		{
			star(g, ex, ey, Math.max(7, Math.min(14, vp.getScale() * 0.6)), c);
			if (vp.getScale() >= 1)
			{
				EditorTool.label(g, r.name, ex + 10, ey - 8, c);
			}
			return;
		}
		double sx = vp.screenX(r.startX + 0.5);
		double sy = vp.screenY(r.startY + 0.5);
		BasicStroke stroke = dashed
			? new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{8f, 6f}, 0f)
			: new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0, 0, 0, 150));
		g.draw(new Line2D.Double(sx, sy, ex, ey));
		g.setStroke(stroke);
		g.setColor(c);
		g.draw(new Line2D.Double(sx, sy, ex, ey));
		g.setStroke(new BasicStroke(1f));
		arrow(g, sx, sy, ex, ey);
		if (r.kind == TransportRecord.Kind.TWO_WAY)
		{
			arrow(g, ex, ey, sx, sy);
		}
		if (vp.getScale() >= 3)
		{
			String text = r.name + " (" + r.ticks + "t" + (r.startPlane != r.endPlane ? ", to plane " + r.endPlane : "") + ")";
			double mx = (startHere ? sx : ex) + 8;
			double my = (startHere ? sy : ey) - 8;
			int w = fm.stringWidth(text);
			g.setColor(new Color(0, 0, 0, 170));
			g.fillRect((int) mx - 2, (int) my - fm.getAscent(), w + 4, fm.getHeight());
			g.setColor(c);
			g.drawString(text, (int) mx, (int) my);
		}
	}

	private static void arrow(Graphics2D g, double x0, double y0, double x1, double y1)
	{
		if (Math.hypot(x1 - x0, y1 - y0) < 8)
		{
			return;
		}
		double a = Math.atan2(y1 - y0, x1 - x0);
		double len = 11;
		Path2D p = new Path2D.Double();
		p.moveTo(x1, y1);
		p.lineTo(x1 - len * Math.cos(a - 0.45), y1 - len * Math.sin(a - 0.45));
		p.lineTo(x1 - len * Math.cos(a + 0.45), y1 - len * Math.sin(a + 0.45));
		p.closePath();
		g.fill(p);
	}

	private static void star(Graphics2D g, double cx, double cy, double r, Color c)
	{
		Path2D p = new Path2D.Double();
		for (int i = 0; i < 10; i++)
		{
			double rr = i % 2 == 0 ? r : r * 0.45;
			double a = Math.PI / 2 + i * Math.PI / 5;
			double x = cx + rr * Math.cos(a);
			double y = cy - rr * Math.sin(a);
			if (i == 0)
			{
				p.moveTo(x, y);
			}
			else
			{
				p.lineTo(x, y);
			}
		}
		p.closePath();
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(2f));
		g.draw(p);
		g.setColor(c);
		g.fill(p);
		g.setStroke(new BasicStroke(1f));
	}
}

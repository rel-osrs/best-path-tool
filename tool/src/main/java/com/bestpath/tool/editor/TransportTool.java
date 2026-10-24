package com.bestpath.tool.editor;

import com.bestpath.tool.edit.EditStore;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

/**
 * Creates and adjusts transports, in either map view:
 * <ul>
 * <li>click a transport to select it;</li>
 * <li>drag the round handle at the start or end of the selected transport to
 * move that end (on a suggestion this makes an undecided draft: Confirm,
 * Delete, Skip or Test later decides);</li>
 * <li>click an empty spot to set a new start, then click the end (in either
 * view, any plane); or drag from start to end in one view;</li>
 * <li>with the kind "Teleport", one click sets the end (no start).</li>
 * </ul>
 * Escape cancels a half-made transport.
 */
public final class TransportTool extends EditorTool
{
	/** Handle grab distance, in tiles: its own tile only, a click on the tile beside it selects nothing. */
	private static final double GRAB = 0.5;

	private int dragging; // 0 none, 1 start handle, 2 end handle, 3 creating by drag
	private TransportRecord working;
	private int[] pressTile;
	private boolean moved;
	private int[] hover;

	/** Preview of a handle drag or drag-create, drawn by the overlay. */
	public TransportRecord preview;

	public TransportTool(EditorContext ctx)
	{
		super(ctx, "Transport", 't', "Click a transport to select it; drag its round handles to move its ends. "
			+ "Right-click where several overlap to pick one. Click an empty spot, then the end (either map), to add one. Esc cancels.");
	}

	@Override
	public boolean pressed(MapView view, double wx, double wy, MouseEvent e)
	{
		moved = false;
		pressTile = new int[]{tile(wx), tile(wy), view.getPlane()};
		if (ctx.creationStart == null && pressHandle(view, wx, wy))
		{
			return true;
		}
		dragging = 0;
		return true;
	}

	/**
	 * Starts dragging a round handle of the selected transport if (wx, wy) is
	 * on one. Also used by the Pointer tool, so the selected transport can be
	 * adjusted without switching tools.
	 */
	public boolean pressHandle(MapView view, double wx, double wy)
	{
		EditorContext.Selection sel = ctx.getSelection();
		if (sel == null)
		{
			return false;
		}
		moved = false;
		int[] start = sel.start(ctx);
		int[] end = sel.end();
		if (near(end, wx, wy, view.getPlane()))
		{
			beginHandleDrag(sel, 2);
			return true;
		}
		if (near(start, wx, wy, view.getPlane()))
		{
			beginHandleDrag(sel, 1);
			return true;
		}
		return false;
	}

	/** Right click: when several transports or suggestions are at the tile, a menu to pick one (one is picked at once). */
	@Override
	public void rightClicked(MapView view, double wx, double wy, MouseEvent e)
	{
		if (!ctx.hasData())
		{
			return;
		}
		java.util.List<EditorContext.Hit> hits = ctx.hitsAt(tile(wx), tile(wy), view.getPlane());
		if (hits.size() == 1)
		{
			ctx.select(hits.get(0).selection, view, hits.get(0).atEnd);
			return;
		}
		if (hits.isEmpty())
		{
			return;
		}
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		for (EditorContext.Hit h : hits)
		{
			javax.swing.JMenuItem item = new javax.swing.JMenuItem(h.label);
			item.addActionListener(a -> ctx.select(h.selection, view, h.atEnd));
			menu.add(item);
		}
		menu.show(view, e.getX(), e.getY());
	}

	private static boolean near(int[] t, double wx, double wy, int plane)
	{
		return t != null && t[2] == plane && Math.abs(t[0] + 0.5 - wx) <= GRAB && Math.abs(t[1] + 0.5 - wy) <= GRAB;
	}

	private void beginHandleDrag(EditorContext.Selection sel, int which)
	{
		dragging = which;
		dragSel = sel;
		if (sel.record != null)
		{
			working = sel.record.copy();
		}
		else if (sel.draft != null)
		{
			working = sel.draft.copy();
		}
		else if (sel.teleport != null)
		{
			working = ctx.recordFromTeleport(sel.teleport);
		}
		else
		{
			int[] start = sel.start(ctx);
			int[] end = sel.end();
			working = ctx.recordFromCandidate(sel.candidate, start, end[0], end[1], end[2],
				"suggestion: " + sel.suggestion.getRule() + ", adjusted");
		}
		preview = working;
	}

	/** The selection a handle drag started from. */
	private EditorContext.Selection dragSel;

	@Override
	public void dragged(MapView view, double wx, double wy, MouseEvent e)
	{
		int[] t = {tile(wx), tile(wy), view.getPlane()};
		hover = t;
		if (dragging == 1 || dragging == 2)
		{
			if (dragging == 1)
			{
				working.startX = t[0];
				working.startY = t[1];
				working.startPlane = t[2];
			}
			else
			{
				working.endX = t[0];
				working.endY = t[1];
				working.endPlane = t[2];
			}
			if (working.kind == com.bestpath.tool.edit.TransportRecord.Kind.NETWORK)
			{
				// a network stop is used and arrived at on the same tile: both ends move together
				working.startX = t[0];
				working.startY = t[1];
				working.startPlane = t[2];
				working.endX = t[0];
				working.endY = t[1];
				working.endPlane = t[2];
			}
			moved = true;
			return;
		}
		if (pressTile != null && (t[0] != pressTile[0] || t[1] != pressTile[1]))
		{
			moved = true;
			dragging = 3;
			preview = newRecord(pressTile, t);
		}
	}

	@Override
	public void released(MapView view, double wx, double wy, MouseEvent e)
	{
		int[] t = {tile(wx), tile(wy), view.getPlane()};
		try
		{
			if (dragging == 1 || dragging == 2)
			{
				releaseHandle(view, t);
				return;
			}
			if (dragging == 3)
			{
				addAndSelect(newRecord(pressTile, t), view);
				return;
			}
			releaseClick(view, t);
		}
		finally
		{
			dragging = 0;
			preview = null;
			working = null;
			dragSel = null;
			ctx.repaintAll();
		}
	}

	/** The end of a handle drag: the moved transport or draft is selected; a click on a handle may pick a suggestion under it. */
	private void releaseHandle(MapView view, int[] t)
	{
		if (moved)
		{
			if (dragSel != null && dragSel.teleport != null && dragSel.record == null)
			{
				ctx.select(EditorContext.Selection.ofTeleport(dragSel.teleport, working), view, dragging == 2);
			}
			else if (dragSel != null && dragSel.candidate != null)
			{
				// a suggestion stays undecided: Confirm, Delete, Skip or Test later decides
				ctx.select(EditorContext.Selection.ofDraft(dragSel.candidate, dragSel.suggestion, working), view, dragging == 2);
			}
			else
			{
				ctx.pendTransport(working);
				ctx.select(EditorContext.Selection.ofRecord(working, true), view, dragging == 2);
			}
		}
		else
		{
			// a click (no drag) on a handle that sits on another suggestion selects that suggestion,
			// so Delete acts on it and not on the transport just confirmed
			Object[] hit = ctx.hitTransport(t[0], t[1], t[2]);
			EditorContext.Selection h = hit == null ? null : (EditorContext.Selection) hit[0];
			if (h != null && h.candidate != null
				&& (dragSel == null || dragSel.candidate == null || !h.candidate.id().equals(dragSel.candidate.id())))
			{
				ctx.select(h, view, (Boolean) hit[1]);
			}
		}
	}

	/** A click: ends a transport begun with a click, selects what is there, or begins a new transport. */
	private void releaseClick(MapView view, int[] t)
	{
		if (ctx.creationStart != null)
		{
			TransportRecord r = newRecord(ctx.creationStart, t);
			ctx.creationStart = null;
			addAndSelect(r, view);
			return;
		}
		Object[] hit = ctx.hitTransport(t[0], t[1], t[2]);
		if (hit != null)
		{
			ctx.select((EditorContext.Selection) hit[0], view, (Boolean) hit[1]);
			return;
		}
		if (ctx.newKind == TransportRecord.Kind.TELEPORT)
		{
			addAndSelect(newRecord(null, t), view);
			return;
		}
		ctx.creationStart = t;
		ctx.select(null, view, false);
	}

	/** Records a new transport and selects it (picked at its end). */
	private void addAndSelect(TransportRecord r, MapView view)
	{
		ctx.pendTransport(r);
		ctx.select(EditorContext.Selection.ofRecord(r, true), view, true);
	}

	private TransportRecord newRecord(int[] start, int[] end)
	{
		TransportRecord r = new TransportRecord();
		r.id = EditStore.newId("t");
		r.kind = start == null ? TransportRecord.Kind.TELEPORT
			: ctx.newKind == TransportRecord.Kind.TELEPORT ? TransportRecord.Kind.TWO_WAY : ctx.newKind;
		r.name = r.kind == TransportRecord.Kind.TELEPORT ? "Teleport" : "Custom transport";
		r.category = "CUSTOM";
		if (start != null)
		{
			r.startX = start[0];
			r.startY = start[1];
			r.startPlane = start[2];
		}
		r.endX = end[0];
		r.endY = end[1];
		r.endPlane = end[2];
		r.ticks = ctx.newTicks;
		r.ticksEstimate = false;
		r.source = "manual";
		return r;
	}

	@Override
	public void moved(MapView view, double wx, double wy)
	{
		hover = new int[]{tile(wx), tile(wy), view.getPlane()};
	}

	@Override
	public void deactivate()
	{
		ctx.creationStart = null;
		preview = null;
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		MapViewport vp = view.getViewport();
		int[] s = ctx.creationStart;
		if (s != null)
		{
			if (s[2] == view.getPlane())
			{
				outlineTile(g, vp, s[0], s[1], new Color(0xffd166), 3f);
				if (hover != null && hover[2] == view.getPlane())
				{
					g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 5f}, 0f));
					g.setColor(new Color(0xffd166));
					g.drawLine((int) vp.screenX(s[0] + 0.5), (int) vp.screenY(s[1] + 0.5),
						(int) vp.screenX(hover[0] + 0.5), (int) vp.screenY(hover[1] + 0.5));
					g.setStroke(new BasicStroke(1f));
				}
			}
			label(g, "New transport: start set at " + s[0] + ", " + s[1] + " (plane " + s[2] + "). Click the end, Esc cancels.",
				12, view.getHeight() - 16, new Color(0xffd166));
		}
		else if (hover != null && hover[2] == view.getPlane() && dragging == 0)
		{
			outlineTile(g, vp, hover[0], hover[1], new Color(255, 255, 255, 160), 1.5f);
		}
	}
}

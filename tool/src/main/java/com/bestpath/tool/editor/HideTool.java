package com.bestpath.tool.editor;

import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.map.MapView;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Hides a sub-region (it turns red at once on the "Ignored sub-regions" layer, and is left out of the
 * plugin's data), or unhides it. Click inside the sub-region. Right-click a closed-off pocket (grey, left
 * out automatically) to keep it in the data after all, or to leave it out again.
 */
public final class HideTool extends EditorTool
{
	private int hoverComp = -1;
	private double hoverWx;
	private double hoverWy;
	/** Every closed-off pocket of {@link #pocketsOf} (kept ones included). */
	private boolean[] allPockets;
	private CoarseGraph pocketsOf;

	public HideTool(EditorContext ctx)
	{
		super(ctx, "Hide sub-region", 'h', "Click a sub-region to hide it (red, left out of the plugin's data). Click again to unhide. "
			+ "Right-click a grey pocket to keep it in the data (right-click again to leave it out).");
	}

	@Override
	public boolean usesRightButton()
	{
		return true;
	}

	@Override
	public boolean pressed(MapView view, double wx, double wy, MouseEvent e)
	{
		if (!ctx.hasData())
		{
			return true;
		}
		int x = tile(wx);
		int y = tile(wy);
		int plane = view.getPlane();
		int comp = ctx.graph.componentAt(x, y, plane);
		if (comp < 0)
		{
			return true;
		}
		Edit.Type type = SwingUtilities.isRightMouseButton(e) ? Edit.Type.KEEP : Edit.Type.HIDE;
		if (type == Edit.Type.KEEP && !isPocket(comp))
		{
			return true; // only closed-off pockets are left out automatically
		}
		// a change of this kind not built in yet in the same sub-region: clicking again takes it back
		List<Edit> sameComp = new ArrayList<>();
		for (Edit p : ctx.edits.getUnbuilt())
		{
			if (p.type == type && ctx.graph.componentAt(p.x, p.y, p.plane) == comp)
			{
				sameComp.add(p);
			}
		}
		if (!sameComp.isEmpty())
		{
			sameComp.forEach(ctx.edits::revert);
			return true;
		}
		boolean on = type == Edit.Type.HIDE
			? !(ctx.hidden != null && comp < ctx.hidden.length && ctx.hidden[comp])
			: ctx.pockets != null && comp < ctx.pockets.length && ctx.pockets[comp];
		if (on)
		{
			Edit h = new Edit();
			h.type = type;
			h.x = x;
			h.y = y;
			h.plane = plane;
			h.on = true;
			h.factSize = ctx.graph.componentSize(comp);
			ctx.edits.add(h);
		}
		else
		{
			// switch off every applied anchor of this kind inside this sub-region
			List<int[]> anchors = type == Edit.Type.HIDE ? ctx.state.getHiddenAnchors() : ctx.state.getKeptAnchors();
			for (int[] a : anchors)
			{
				if (ctx.graph.componentAt(a[0], a[1], a[2]) == comp)
				{
					Edit u = new Edit();
					u.type = type;
					u.x = a[0];
					u.y = a[1];
					u.plane = a[2];
					u.on = false;
					ctx.edits.add(u);
				}
			}
		}
		return true;
	}

	/** Hidden (HIDE) or kept (KEEP) after the changes not built in yet; {@code built}: as built. */
	private boolean now(Edit.Type type, int comp, boolean built)
	{
		boolean out = built;
		for (Edit e : ctx.edits.getUnbuilt())
		{
			if (e.type == type && ctx.graph.componentAt(e.x, e.y, e.plane) == comp)
			{
				out = e.on;
			}
		}
		return out;
	}

	/** True if the sub-region is a closed-off pocket (kept or not). */
	private boolean isPocket(int comp)
	{
		if (pocketsOf != ctx.graph)
		{
			pocketsOf = ctx.graph;
			allPockets = ctx.graph.pockets();
		}
		return comp < allPockets.length && allPockets[comp];
	}

	@Override
	public void moved(MapView view, double wx, double wy)
	{
		hoverWx = wx;
		hoverWy = wy;
		hoverComp = ctx.hasData() ? ctx.graph.componentAt(tile(wx), tile(wy), view.getPlane()) : -1;
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		if (hoverComp < 0 || !ctx.hasData())
		{
			return;
		}
		boolean hidden = now(Edit.Type.HIDE, hoverComp, ctx.hidden != null && hoverComp < ctx.hidden.length && ctx.hidden[hoverComp]);
		String text = "Sub-region " + hoverComp + " (" + ctx.graph.componentSize(hoverComp) + " tiles)"
			+ (hidden ? " - hidden" : "") + " - click to " + (hidden ? "unhide" : "hide");
		if (isPocket(hoverComp))
		{
			boolean kept = now(Edit.Type.KEEP, hoverComp, ctx.pockets != null && hoverComp < ctx.pockets.length && !ctx.pockets[hoverComp]);
			text += kept ? " - kept pocket, right-click to leave it out" : ", right-click to keep this pocket";
		}
		label(g, text, view.getViewport().screenX(hoverWx) + 14, view.getViewport().screenY(hoverWy) + 4, Color.WHITE);
	}
}

package com.bestpath.tool.editor;

import com.bestpath.tool.map.MapView;
import com.bestpath.tool.route.RouteController;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

/**
 * Route check: left-click sets the start, right-click the destination, in
 * either map view and on any plane. The route is searched at once and
 * drawn by the route overlay. The middle button pans.
 */
public final class RouteTool extends EditorTool
{
	private final RouteController route;
	private int[] hover;

	public RouteTool(EditorContext ctx, RouteController route)
	{
		super(ctx, "Route", 'r', "Left-click: start. Right-click: destination. The route and its ticks show at once "
			+ "(Route tab below). Middle-drag moves the map.");
		this.route = route;
	}

	@Override
	public boolean usesRightButton()
	{
		return true;
	}

	@Override
	public boolean pressed(MapView view, double wx, double wy, MouseEvent e)
	{
		int x = tile(wx);
		int y = tile(wy);
		if (SwingUtilities.isRightMouseButton(e))
		{
			route.setEnd(x, y, view.getPlane());
		}
		else
		{
			route.setStart(x, y, view.getPlane());
		}
		return true;
	}

	@Override
	public void moved(MapView view, double wx, double wy)
	{
		hover = new int[]{tile(wx), tile(wy), view.getPlane()};
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		if (hover != null && hover[2] == view.getPlane())
		{
			outlineTile(g, view.getViewport(), hover[0], hover[1], new Color(255, 255, 255, 170), 1.5f);
		}
	}
}

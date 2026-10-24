package com.bestpath.tool.editor;

import com.bestpath.tool.map.MapView;
import java.awt.Cursor;
import java.awt.event.MouseEvent;

/**
 * Select and inspect: click a tile or transport; drag moves the map. The
 * round handles of the selected transport can be dragged here too.
 */
public final class PointerTool extends EditorTool
{
	private final TransportTool handles;
	private boolean draggingHandle;

	public PointerTool(EditorContext ctx, TransportTool handles)
	{
		super(ctx, "Pointer", 'v', "Click a tile or transport to select it; drag its round handles to move its ends. "
			+ "Right-click where several transports overlap to pick one. Drag elsewhere to move the map.");
		this.handles = handles;
	}

	@Override
	public Cursor getCursor()
	{
		return Cursor.getDefaultCursor();
	}

	@Override
	public boolean pressed(MapView view, double wx, double wy, MouseEvent e)
	{
		draggingHandle = handles.pressHandle(view, wx, wy);
		return draggingHandle; // otherwise dragging pans the map
	}

	@Override
	public void dragged(MapView view, double wx, double wy, MouseEvent e)
	{
		if (draggingHandle)
		{
			handles.dragged(view, wx, wy, e);
		}
	}

	@Override
	public void released(MapView view, double wx, double wy, MouseEvent e)
	{
		if (draggingHandle)
		{
			draggingHandle = false;
			handles.released(view, wx, wy, e);
		}
	}

	@Override
	public void rightClicked(MapView view, double wx, double wy, MouseEvent e)
	{
		handles.rightClicked(view, wx, wy, e); // pick one of several transports at the tile
	}

	@Override
	public boolean clickSelects()
	{
		return true;
	}
}

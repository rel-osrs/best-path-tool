package com.bestpath.tool.editor;

import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

/** Base for the editing tools in the tool palette. Every change a tool makes takes effect at once (Ctrl+Z undoes it). */
public abstract class EditorTool implements MapView.Interaction
{
	protected final EditorContext ctx;
	private final String name;
	private final char key;
	private final String help;

	protected EditorTool(EditorContext ctx, String name, char key, String help)
	{
		this.ctx = ctx;
		this.name = name;
		this.key = key;
		this.help = help;
	}

	public String getName()
	{
		return name;
	}

	/** Keyboard shortcut (lower case letter). */
	public char getKey()
	{
		return key;
	}

	/** One-line help shown in the options bar. */
	public String getHelp()
	{
		return help;
	}

	public Cursor getCursor()
	{
		return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
	}

	@Override
	public boolean pressed(MapView view, double wx, double wy, MouseEvent e)
	{
		return true;
	}

	@Override
	public void dragged(MapView view, double wx, double wy, MouseEvent e)
	{
	}

	@Override
	public void released(MapView view, double wx, double wy, MouseEvent e)
	{
	}

	@Override
	public void moved(MapView view, double wx, double wy)
	{
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
	}

	@Override
	public boolean clickSelects()
	{
		return false;
	}

	/** Called when the tool is switched off. */
	public void deactivate()
	{
	}

	protected static int tile(double w)
	{
		return (int) Math.floor(w);
	}

	/** Outline of one tile, in screen coordinates. */
	protected static void outlineTile(Graphics2D g, MapViewport vp, int x, int y, Color c, float width)
	{
		int sx = (int) Math.floor(vp.screenX(x));
		int sy = (int) Math.floor(vp.screenY(y + 1));
		int s = (int) Math.max(2, Math.ceil(vp.getScale()));
		g.setStroke(new BasicStroke(width));
		g.setColor(c);
		g.drawRect(sx, sy, s, s);
		g.setStroke(new BasicStroke(1f));
	}

	/** A small text label with a dark background. */
	protected static void label(Graphics2D g, String text, double x, double y, Color c)
	{
		g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text);
		g.setColor(new Color(0, 0, 0, 190));
		g.fillRect((int) x - 3, (int) y - fm.getAscent() - 2, w + 6, fm.getHeight() + 2);
		g.setColor(c);
		g.drawString(text, (int) x, (int) y);
	}
}

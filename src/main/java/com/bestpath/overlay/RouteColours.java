package com.bestpath.overlay;

import java.awt.Color;

/**
 * The route's colours, all from one colour the player picks: the walk ahead, the tiles where transports are used, the
 * boat's course and the current step are that colour; the current step stands out by being stronger and thicker, not
 * by another colour. Steps already done on the world map stay grey, the destination pin red. Immutable; {@link #of}
 * keeps the last one made, so overlays may ask for it every frame.
 */
public final class RouteColours
{
	/** The default colour, light blue. */
	public static final Color DEFAULT = new Color(0, 200, 255);

	private static volatile RouteColours last = new RouteColours(DEFAULT);

	/** The colour picked (no transparency). */
	final Color base;
	/** Game world: the walk ahead's fill and outline. */
	final Color pathFill;
	final Color pathLine;
	/** Game world: a transport's tile ahead. */
	final Color markerFill;
	final Color markerLine;
	/** Game world: the current step's tiles, outline and transport tile. */
	final Color currentFill;
	final Color currentLine;
	final Color currentMarkerFill;
	/** Game world: the boat's course ahead. */
	final Color course;
	/** Minimap: the walk ahead, transport tiles, the current step. */
	final Color minimapPath;
	final Color minimapMarker;
	final Color minimapCurrent;
	/** World map: the line ahead, the current step's, transport marks. */
	final Color mapAhead;
	final Color mapCurrent;
	final Color mapMark;

	private RouteColours(Color c)
	{
		base = new Color(c.getRed(), c.getGreen(), c.getBlue());
		pathFill = alpha(base, 40);
		pathLine = alpha(base, 150);
		markerFill = alpha(base, 60);
		markerLine = alpha(base, 230);
		Color bright = brighter(base);
		currentFill = alpha(bright, 90);
		currentLine = alpha(bright, 255);
		currentMarkerFill = alpha(bright, 130);
		course = alpha(base, 210);
		minimapPath = alpha(base, 220);
		minimapMarker = alpha(base, 255);
		minimapCurrent = alpha(bright, 255);
		mapAhead = alpha(base, 230);
		mapCurrent = alpha(bright, 255);
		mapMark = alpha(base, 255);
	}

	/** The colours for {@code c} (null: the default light blue). */
	public static RouteColours of(Color c)
	{
		Color want = c == null ? DEFAULT : c;
		RouteColours l = last;
		if (l.base.getRGB() == (want.getRGB() | 0xFF000000))
		{
			return l;
		}
		RouteColours made = new RouteColours(want);
		last = made;
		return made;
	}

	/** The default colours (light blue). */
	public static RouteColours defaults()
	{
		return of(DEFAULT);
	}

	static Color alpha(Color c, int a)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	/** The colour mixed 40% towards white: the current step, the same colour but lighter. */
	static Color brighter(Color c)
	{
		return new Color(mix(c.getRed()), mix(c.getGreen()), mix(c.getBlue()));
	}

	private static int mix(int v)
	{
		return v + Math.round((255 - v) * 0.4f);
	}
}

package com.bestpath.core.data;

import com.bestpath.core.path.Transport;
import java.util.List;

/**
 * The menu text of a route step's transport (what the player clicks, {@link MenuText}), for the step card: the
 * direction a two-way transport is used in, and for a network ride the boarding stop's click with the landing
 * stop's choice. Pure; no state.
 */
public final class TransportMenus
{
	private TransportMenus()
	{
	}

	/**
	 * The menu text for using {@code t} from the tile (x, y, plane) (a step's first tile); null when not known.
	 *
	 * @param t     the step's transport
	 * @param line  its transport line ({@code PlayerTransports.Result.lineOf(t)}: for a network ride the stop it goes
	 *              to)
	 * @param lines every transport line (to find a ride's boarding stop), e.g. {@code PluginData.getTransports()}
	 */
	public static MenuText of(Transport t, TransportLine line, List<TransportLine> lines, int x, int y, int plane)
	{
		if (t == null || line == null)
		{
			return null;
		}
		switch (line.getKind())
		{
			case NETWORK:
			{
				// a ride from the stop at its start tile to this line's stop
				TransportLine boarding = null;
				for (TransportLine l : lines)
				{
					if (l.getKind() == TransportLine.Kind.NETWORK && l.getNetwork().equals(line.getNetwork()) && l != line
						&& l.getEndX() == t.getStartX() && l.getEndY() == t.getStartY() && l.getEndPlane() == t.getStartPlane())
					{
						boarding = l;
						break;
					}
				}
				return boarding == null ? null : MenuText.ride(boarding.getMenu(), line.getMenu());
			}
			case TWO_WAY:
				if (t.getId().endsWith(":back"))
				{
					return line.getMenuBack();
				}
				return line.menuFor(startToEnd(line, x, y, plane));
			default:
				return line.getMenu();
		}
	}

	/** True when (x, y, plane) is the line's start side: on the start's plane and no farther from the start than the end. */
	static boolean startToEnd(TransportLine line, int x, int y, int plane)
	{
		boolean onStart = plane == line.getStartPlane();
		boolean onEnd = plane == line.getEndPlane();
		if (onStart != onEnd)
		{
			return onStart;
		}
		int toStart = Math.max(Math.abs(x - line.getStartX()), Math.abs(y - line.getStartY()));
		int toEnd = Math.max(Math.abs(x - line.getEndX()), Math.abs(y - line.getEndY()));
		return toStart <= toEnd;
	}
}

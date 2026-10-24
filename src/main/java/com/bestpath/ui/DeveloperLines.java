package com.bestpath.ui;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JPanel;

/**
 * Developer details on cards, shown only while the "Developer details" setting is on (off by default): the step's
 * transport id, its data name, ticks and end tile, then each part of its requirement with the player's result. Cards
 * otherwise never show tile coordinates. Swing thread only for {@link #addTo}.
 */
final class DeveloperLines
{
	private DeveloperLines()
	{
	}

	/** The lines for a step: transport id, data name, kind, ticks and end tile, then the requirement checks. */
	static List<String> of(RouteStep s)
	{
		List<String> out = new ArrayList<>();
		if (s.getTransportId() != null)
		{
			out.add("transport " + s.getTransportId());
		}
		StepInfo info = s.getInfo();
		if (!info.getName().isEmpty())
		{
			out.add("data: " + info.getName() + (info.getCategory().isEmpty() ? "" : " (" + info.getCategory() + ")"));
		}
		int end = s.getEndTile();
		out.add(s.getKind() + ", " + s.getTicks() + " ticks" + (end < 0 ? ""
			: ", ends " + Tiles.x(end) + ", " + Tiles.y(end) + ", " + Tiles.plane(end)));
		out.addAll(info.getCheckLines());
		return out;
	}

	/** Adds the lines in small grey text at the end of a card's text column. */
	static void addTo(JPanel text, List<String> lines, int width)
	{
		if (lines.isEmpty())
		{
			return;
		}
		text.add(Box.createVerticalStrut(3));
		for (String line : lines)
		{
			WrapLabel l = Ui.wrapped(line, width, Ui.small(), Ui.MUTED);
			l.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			text.add(l);
		}
		text.revalidate();
	}
}

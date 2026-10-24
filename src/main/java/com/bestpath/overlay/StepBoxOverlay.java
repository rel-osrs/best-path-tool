package com.bestpath.overlay;

import com.bestpath.route.NoRouteWhy;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStep;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The next-step box on the game screen: "Step 2 of 5" and what to do ("Climb-up Ladder"; for a bank step every item
 * to take out); while searching "Calculating..."; with no route, why (the sidebar's first line). Hidden once the
 * player has arrived. Shown with its own switch, and always in minimal mode (no side panel). The player can drag it
 * like any RuneLite box.
 */
public class StepBoxOverlay extends OverlayPanel
{

	/** The box's text width in pixels. */
	private static final int WIDTH = 190;

	private final Supplier<RouteResult> route;
	private final StepTargets targets;
	private final BooleanSupplier shown;
	private final Supplier<Color> colour;

	public StepBoxOverlay(Supplier<RouteResult> route, StepTargets targets, BooleanSupplier shown, Supplier<Color> colour)
	{
		this.route = route;
		this.targets = targets;
		this.shown = shown;
		this.colour = colour;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
	}

	/** The box's lines for a result and the next step: [title, line...]; empty = no box. Pure. */
	static String[] lines(RouteResult r, RouteStep next, int card, int cards, List<String> bankItems)
	{
		switch (r.getStatus())
		{
			case CALCULATING:
				return new String[]{"Best Path", "Calculating..."};
			case NO_ROUTE:
				NoRouteWhy why = r.getNoRouteWhy();
				return new String[]{r.isFindingWhy() ? "No route, finding out why..." : "No route",
					why == null ? r.getMessage() : why.getHeadline()};
			case ARRIVED:
				return new String[0]; // nothing left to show
			case ERROR:
			case NO_DATA:
				return new String[]{"Best Path", r.getMessage()};
			case READY:
				if (next == null)
				{
					return new String[]{"Walk to the destination"};
				}
				if (next.getKind() == RouteStep.Kind.BANK && !bankItems.isEmpty())
				{
					// every item to take out (the words in the world point here when there are more than three)
					String[] out = new String[2 + bankItems.size()];
					out[0] = "Step " + card + " of " + cards;
					out[1] = "Take out:";
					for (int i = 0; i < bankItems.size(); i++)
					{
						out[2 + i] = bankItems.get(i);
					}
					return out;
				}
				return new String[]{"Step " + card + " of " + cards, StepTargets.words(next, bankItems, true)};
			case IDLE:
			default:
				return new String[0];
		}
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!shown.getAsBoolean())
		{
			return null;
		}
		String[] l = lines(route.get(), targets.getStep(), targets.getCardNumber(), targets.getCardCount(), targets.getBankItems());
		if (l.length == 0)
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text(l[0]).color(colour.get()).build());
		for (int i = 1; i < l.length; i++)
		{
			if (!l[i].isEmpty())
			{
				panelComponent.getChildren().add(LineComponent.builder().left(l[i]).build());
			}
		}
		return super.render(g);
	}
}

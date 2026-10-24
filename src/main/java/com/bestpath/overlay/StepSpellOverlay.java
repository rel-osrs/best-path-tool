package com.bestpath.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The spell the next step casts glows in the open spellbook: a frame round its icon. The fairy ring code in the open
 * travel log gets a frame round its entry, cut to what shows of the list. Reads only {@link StepTargets}.
 */
public class StepSpellOverlay extends Overlay
{

	/** The frame's pen for each "Item and UI glow thickness" (indexed by the setting; 1 draws it 2 wide). */
	private static final Stroke[] FRAME = new Stroke[7];

	static
	{
		for (int i = 0; i < FRAME.length; i++)
		{
			FRAME[i] = new BasicStroke(i + 1);
		}
	}

	private final IntSupplier width;

	private final StepTargets targets;
	private final BooleanSupplier glow;
	private final Supplier<Color> colour;

	public StepSpellOverlay(StepTargets targets, BooleanSupplier glow, Supplier<Color> colour, IntSupplier width)
	{
		this.width = width;
		this.targets = targets;
		this.glow = glow;
		this.colour = colour;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!glow.getAsBoolean())
		{
			return null;
		}
		frame(g, targets.getSpell(), null);
		frame(g, targets.getFairyEntry(), targets.getFairyList());
		return null;
	}

	/** A frame round the widget, cut to {@code within} (a scrolled list) when given. */
	private void frame(Graphics2D g, Widget w, Widget within)
	{
		if (w == null || w.isHidden() || within != null && within.isHidden())
		{
			return;
		}
		Rectangle b = w.getBounds();
		if (b == null || b.width <= 0)
		{
			return;
		}
		Shape old = g.getClip();
		if (within != null)
		{
			g.clip(within.getBounds());
		}
		int pen = Math.max(1, Math.min(FRAME.length - 1, width.getAsInt()));
		g.setStroke(FRAME[pen]);
		g.setColor(colour.get());
		int out = pen / 2; // a thicker frame grows outwards
		g.drawRoundRect(b.x - 1 - out, b.y - 1 - out, b.width + 2 + 2 * out, b.height + 2 + 2 * out, 6, 6);
		g.setClip(old);
	}
}

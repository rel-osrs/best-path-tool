package com.bestpath.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * The item the next step is done with glows in the inventory and the worn equipment: an item teleport's item, in any
 * of its variants (each charge of an amulet of glory).
 */
public class StepItemOverlay extends WidgetItemOverlay
{

	private final ItemManager items;
	private final StepTargets targets;
	private final BooleanSupplier glow;
	private final Supplier<Color> colour;
	/** The "Item and UI glow thickness" setting. */
	private final IntSupplier width;

	public StepItemOverlay(ItemManager items, StepTargets targets, BooleanSupplier glow, Supplier<Color> colour,
		IntSupplier width)
	{
		this.width = width;
		this.items = items;
		this.targets = targets;
		this.glow = glow;
		this.colour = colour;
		showOnInventory();
		showOnEquipment();
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem item)
	{
		if (!glow.getAsBoolean() || !targets.usesItem(itemId))
		{
			return;
		}
		Rectangle b = item.getCanvasBounds();
		BufferedImage outline = items.getItemOutline(itemId, item.getQuantity(), colour.get());
		if (b == null || outline == null)
		{
			return;
		}
		// 1 pixel: the game's outline as it is; thicker: drawn again round it, out to that many pixels
		int r = Math.max(0, width.getAsInt() - 1);
		for (int dx = -r; dx <= r; dx++)
		{
			for (int dy = -r; dy <= r; dy++)
			{
				if (dx * dx + dy * dy <= r * r)
				{
					g.drawImage(outline, (int) b.getX() + dx, (int) b.getY() + dy, null);
				}
			}
		}
	}
}

package com.bestpath.ui;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Where the sidebar's pictures come from. In the plugin: RuneLite's
 * ItemManager, SkillIconManager and SpriteManager (public API only, see
 * {@link RuneLiteIcons}). The sidebar keeps every picture it gets (see
 * {@link IconCache}), so each one is asked for once.
 */
public interface IconProvider
{
	/** No pictures: every step keeps its coloured kind label. */
	IconProvider NONE = (key, done) ->
	{
	};

	/**
	 * Called on the Swing thread. Gives the picture to {@code done} on the Swing thread, now or later (an item's
	 * picture is drawn by the game client a moment later); never if there is none.
	 */
	void load(IconKey key, Consumer<BufferedImage> done);

	/**
	 * Called on the Swing thread. Gives an item's name ("Law rune") to {@code done} on the Swing thread, now or later,
	 * or null when there is none. Never answering is fine: the bank box then keeps the name from the route data.
	 */
	default void itemName(int itemId, Consumer<String> done)
	{
	}
}

package com.bestpath.core.data;

/**
 * What development code reads from a {@link MenuText} that the plugin itself never needs (the Data Tool matches menu
 * texts against the game cache).
 */
public final class MenuTextDiagnostics
{
	private MenuTextDiagnostics()
	{
	}

	/**
	 * {@link MenuText.Style#CHOICE} only: the keyboard key that picks the choice in the game's list ("5"), or null (none,
	 * or not known).
	 */
	public static String key(MenuText m)
	{
		return m.key;
	}

	/** The game's menu line: option then target ("Climb-up Ladder", "Edgeville Amulet of glory"); empty for a choice only. */
	public static String menuLine(MenuText m)
	{
		return m.isChoiceOnly() ? "" : m.getOption() + " " + m.getTarget();
	}
}

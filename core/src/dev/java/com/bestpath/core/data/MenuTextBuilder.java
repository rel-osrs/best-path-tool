package com.bestpath.core.data;

/**
 * Makes the {@link MenuText.Style#CHOICE} menu texts, when the data is made (the plugin reads them from its data).
 */
public final class MenuTextBuilder
{
	private MenuTextBuilder()
	{
	}

	/** "Teleport Construct. cape", then "Rimmington" in its sub-menu or list. */
	public static MenuText choice(String option, String target, String choice)
	{
		return new MenuText(MenuText.Style.CHOICE, option, target, choice);
	}

	/** As {@link #choice(String, String, String)}, picked in the list with this keyboard key ("5"); null = none. */
	public static MenuText choice(String option, String target, String choice, String key)
	{
		return new MenuText(MenuText.Style.CHOICE, option, target, choice, key);
	}

	/**
	 * A network stop whose click is not known, only the choice that picks it in the network's list (enough for rides
	 * TO it). Written "c|||Gandius".
	 */
	public static MenuText choiceOnly(String choice)
	{
		return new MenuText(MenuText.Style.CHOICE, "", "", choice);
	}
}

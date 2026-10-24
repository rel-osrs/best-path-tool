package com.bestpath.core.data;

import java.util.Objects;

/**
 * What the player clicks in the game to use a transport one way, in the game's own words, so a step card can say
 * "Construct. cape → Rimmington". Made from the game cache when the data is generated (item, object, NPC and spell
 * names and their menu options) and stored in the {@code transports.tsv} columns {@code menu} and {@code menu back}.
 * Immutable.
 *
 * <p>Three forms:
 * <ul>
 * <li>{@link Style#VERB}: the option is a verb, the card says it like the game's menu line: "Climb-up Ladder",
 * "Cast Varrock Teleport", "Commune Royal seed pod".</li>
 * <li>{@link Style#NAMED}: the option itself names the destination (worn jewellery, a tablet's other destination,
 * a spell's second destination): "Amulet of glory → Edgeville" for the menu line "Edgeville Amulet of glory".</li>
 * <li>{@link Style#CHOICE}: the click opens a sub-menu or a list, and the player then picks the destination:
 * "Construct. cape → Rimmington" (menu line "Teleport Construct. cape", then "Rimmington"), "Fairy ring → CKQ".</li>
 * </ul>
 * For a network ride (any stop to any other) each stop's text is its own click plus the choice that leads TO that
 * stop; {@link #ride(MenuText, MenuText)} joins the boarding stop's click with the landing stop's choice.
 *
 * <p>A {@link Style#CHOICE} may also carry the keyboard key that picks the choice in the game's list:
 * "Jewellery Box → Burthorpe (5)". Only where the cache says the key is fixed (the jewellery box's list); a key that
 * depends on the player (the portal nexus's list) is added by the plugin with {@link #withKey(String)}.
 */
public final class MenuText
{
	/** How the card's title is made from the parts. */
	public enum Style
	{
		/** "&lt;option&gt; &lt;target&gt;". */
		VERB('v'),
		/** "&lt;target&gt; → &lt;option&gt;": the option names the destination. */
		NAMED('n'),
		/** "&lt;target&gt; → &lt;choice&gt;": the click opens a sub-menu or list, then the choice. */
		CHOICE('c');

		private final char code;

		Style(char code)
		{
			this.code = code;
		}

		static Style byCode(char c)
		{
			for (Style s : values())
			{
				if (s.code == c)
				{
					return s;
				}
			}
			throw new IllegalArgumentException("unknown menu text style '" + c + "'");
		}
	}

	/** The arrow between a target and the destination it names ("Amulet of glory → Edgeville"). */
	public static final String ARROW = "→";

	/** Separates the parts in the file; game names never contain it (checked when the data is generated). */
	public static final char SEPARATOR = '|';

	private final Style style;
	private final String option;
	private final String target;
	private final String choice;
	/** {@link Style#CHOICE} only: the keyboard key that picks the choice in the list ("5", "M"), or null. */
	final String key;

	MenuText(Style style, String option, String target, String choice)
	{
		this(style, option, target, choice, null);
	}

	MenuText(Style style, String option, String target, String choice, String key)
	{
		this.style = Objects.requireNonNull(style);
		// a network stop may know only the choice that leads to it (option and target empty)
		boolean choiceOnly = style == Style.CHOICE && (option == null || option.isEmpty()) && (target == null || target.isEmpty());
		this.option = choiceOnly ? "" : part(option, "option");
		this.target = choiceOnly ? "" : part(target, "target");
		this.choice = style == Style.CHOICE ? part(choice, "choice") : null;
		this.key = style == Style.CHOICE && key != null && !key.isEmpty() ? part(key, "key") : null;
	}

	private static String part(String s, String what)
	{
		if (s == null || s.trim().isEmpty())
		{
			throw new IllegalArgumentException("menu text: empty " + what);
		}
		String t = TransportLine.clean(s).trim();
		if (t.indexOf(SEPARATOR) >= 0)
		{
			throw new IllegalArgumentException("menu text: '" + SEPARATOR + "' in " + what + " '" + s + "'");
		}
		return t;
	}

	/** "Climb-up Ladder": the option is a verb. */
	public static MenuText verb(String option, String target)
	{
		return new MenuText(Style.VERB, option, target, null);
	}

	/** "Edgeville Amulet of glory" in the game's menu: the option names the destination. */
	public static MenuText named(String option, String target)
	{
		return new MenuText(Style.NAMED, option, target, null);
	}

	/**
	 * This text with the keyboard key that picks its choice ("M"); null or empty removes it. Only a {@link Style#CHOICE}
	 * has a key: the others come back unchanged.
	 */
	public MenuText withKey(String k)
	{
		if (style != Style.CHOICE)
		{
			return this;
		}
		return new MenuText(Style.CHOICE, option, target, choice, k);
	}

	/**
	 * True for a network stop whose click is not known, only the choice that picks it in the network's list (written
	 * "c|||Gandius"): option and target empty.
	 */
	public boolean isChoiceOnly()
	{
		return option.isEmpty();
	}

	/**
	 * A network ride: the boarding stop's click and the landing stop's choice ("Spirit tree" at the Grand Exchange,
	 * then "Battlefield of Khazard"). Null when either is unknown, the boarding stop's click is not known, or the
	 * landing stop's text has no choice.
	 */
	public static MenuText ride(MenuText boarding, MenuText landing)
	{
		if (boarding == null || landing == null || boarding.isChoiceOnly() || landing.style != Style.CHOICE)
		{
			return null;
		}
		return new MenuText(Style.CHOICE, boarding.option, boarding.target, landing.choice, landing.key);
	}

	public Style getStyle()
	{
		return style;
	}

	/** The menu option as the game shows it ("Climb-up", "Edgeville", "Teleport"). */
	public String getOption()
	{
		return option;
	}

	/** What is clicked, as the game names it ("Ladder", "Amulet of glory", "Varrock Teleport"). */
	public String getTarget()
	{
		return target;
	}

	/** {@link Style#CHOICE} only: the sub-menu option or list entry picked after the click; else null. */
	public String getChoice()
	{
		return choice;
	}

	/**
	 * The card title: "Climb-up Ladder", "Amulet of glory → Edgeville", "Construct. cape → Rimmington", with the key
	 * when known: "Jewellery Box → Burthorpe (5)".
	 */
	public String title(String arrow)
	{
		switch (style)
		{
			case NAMED:
				return target + " " + arrow + " " + option;
			case CHOICE:
				return (isChoiceOnly() ? choice : target + " " + arrow + " " + choice) + (key == null ? "" : " (" + key + ")");
			case VERB:
			default:
				return option + " " + target;
		}
	}

	/**
	 * As written in the file: "v|Climb-up|Ladder", "n|Edgeville|Amulet of glory", "c|Teleport|Construct. cape|Rimmington",
	 * a choice with its key "c|Teleport Menu|Jewellery Box|Burthorpe|5".
	 */
	public String format()
	{
		String s = style.code + "" + SEPARATOR + option + SEPARATOR + target;
		if (style != Style.CHOICE)
		{
			return s;
		}
		return s + SEPARATOR + choice + (key == null ? "" : SEPARATOR + key);
	}

	/** Reads {@link #format()}; null for an empty text (unknown). */
	public static MenuText parse(String s)
	{
		if (s == null || s.isEmpty())
		{
			return null;
		}
		String[] p = s.split("\\" + SEPARATOR, -1);
		if (p[0].length() != 1)
		{
			throw new IllegalArgumentException("not a menu text: '" + s + "'");
		}
		Style style = Style.byCode(p[0].charAt(0));
		int want = style == Style.CHOICE ? 4 : 3;
		if (p.length != want && !(style == Style.CHOICE && p.length == 5))
		{
			throw new IllegalArgumentException("menu text '" + s + "': expected " + want + " parts, found " + p.length);
		}
		if (style != Style.CHOICE && (p[1].isEmpty() || p[2].isEmpty()) || style == Style.CHOICE && p[1].isEmpty() != p[2].isEmpty())
		{
			throw new IllegalArgumentException("menu text '" + s + "': empty option or target");
		}
		return new MenuText(style, p[1], p[2], style == Style.CHOICE ? p[3] : null, p.length == 5 ? p[4] : null);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof MenuText))
		{
			return false;
		}
		MenuText m = (MenuText) o;
		return style == m.style && option.equals(m.option) && target.equals(m.target) && Objects.equals(choice, m.choice)
			&& Objects.equals(key, m.key);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(style, option, target, choice, key);
	}

	@Override
	public String toString()
	{
		return format();
	}
}

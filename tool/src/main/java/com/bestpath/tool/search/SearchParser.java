package com.bestpath.tool.search;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the search box. Supported:
 * <ul>
 * <li>world coordinates: "3222 3218", "3222,3218,0", "3222, 3218, 1"</li>
 * <li>region id: "12850", "r12850", "region 12850"</li>
 * </ul>
 * Objects, transports and clues cannot be searched for yet.
 */
public final class SearchParser
{
	private static final Pattern COORD = Pattern.compile("^(\\d{1,5})\\s*[,\\s]\\s*(\\d{1,5})(?:\\s*[,\\s]\\s*([0-3]))?$");
	private static final Pattern REGION = Pattern.compile("^(?:r|region\\s*)?(\\d{1,5})$");

	private SearchParser()
	{
	}

	/** Returns the parsed target, or null if the text is not understood. */
	public static SearchTarget parse(String text)
	{
		if (text == null)
		{
			return null;
		}
		String s = text.trim().toLowerCase(Locale.ROOT);
		if (s.isEmpty())
		{
			return null;
		}
		Matcher m = COORD.matcher(s);
		if (m.matches())
		{
			int x = Integer.parseInt(m.group(1));
			int y = Integer.parseInt(m.group(2));
			int plane = m.group(3) == null ? SearchTarget.ANY_PLANE : Integer.parseInt(m.group(3));
			if (x > 16383 || y > 16383)
			{
				return null;
			}
			return SearchTarget.tile(x, y, plane);
		}
		m = REGION.matcher(s);
		if (m.matches())
		{
			int id = Integer.parseInt(m.group(1));
			return id <= 0xFFFF ? SearchTarget.region(id) : null;
		}
		return null;
	}
}

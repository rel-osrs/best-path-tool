package com.bestpath.tool.edit;

import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.FreeLandBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Free-to-play land for the tool: {@code data/manual/free-land.tsv} (the drawn base: rows of free
 * tiles, see {@link FreeLand}) with the FREE_LAND changes in effect on top, oldest first.
 */
public final class FreeLandFile
{
	public static final File FILE = new File(com.bestpath.tool.ToolSettings.findWorkspace(), "data/manual/free-land.tsv");

	private static long baseStamp = Long.MIN_VALUE;
	private static FreeLand base = FreeLand.NONE;
	private static String problem;
	private static List<Edit> lastEdits;
	private static long lastBaseStamp;
	private static FreeLand last = FreeLand.NONE;

	private FreeLandFile()
	{
	}

	/** The file as drawn (empty if missing or unreadable; see {@link #problem()}). */
	public static synchronized FreeLand base()
	{
		long stamp = com.bestpath.tool.req.FileStamp.of(FILE);
		if (stamp != baseStamp)
		{
			baseStamp = stamp;
			problem = null;
			try
			{
				base = FILE.isFile() ? FreeLand.parse(Files.readAllLines(FILE.toPath(), StandardCharsets.UTF_8)) : FreeLand.NONE;
			}
			catch (IOException | IllegalArgumentException e)
			{
				base = FreeLand.NONE;
				problem = FILE.getName() + ": " + e.getMessage();
			}
		}
		return base;
	}

	/** Why the file could not be read, or null. */
	public static synchronized String problem()
	{
		base();
		return problem;
	}

	/** The base with the FREE_LAND changes among {@code active} (changes in effect, oldest first) on top. */
	public static synchronized FreeLand effective(List<Edit> active)
	{
		FreeLand b = base();
		if (active == lastEdits && baseStamp == lastBaseStamp)
		{
			return last;
		}
		FreeLand.Builder out = null;
		for (Edit e : active)
		{
			if (e.type == Edit.Type.FREE_LAND)
			{
				if (out == null)
				{
					out = FreeLandBuilder.of(b);
				}
				out.set(e.x, e.y, e.toX, e.toY, e.on);
			}
		}
		last = out == null ? b : out.build();
		lastEdits = active;
		lastBaseStamp = baseStamp;
		return last;
	}
}

package com.bestpath.tool.bank;

import com.bestpath.core.data.BankList;
import com.bestpath.core.data.BankListDiagnostics;
import com.bestpath.core.path.Tiles;
import com.bestpath.tool.ToolSettings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The banks the Data Tool routes with and shows ({@code data/banks/banks.tsv}, made by {@code gradlew -p tool banks};
 * the plugin gets the same lines in its export). Read once, when first needed; any thread.
 */
public final class ToolBanks
{
	public static final String FILE = "data/banks/banks.tsv";

	/** One line of the file as the "Banks" map layer shows it. Immutable. */
	public static final class Row
	{
		/** The bank it belongs to, "booth" / "chest" / "banker", and what using it needs (a requirement line). */
		public final String bank;
		public final String kind;
		public final String needs;
		/** The tiles a player stands on to use it, packed ({@link Tiles}). */
		public final int[] use;
		/** The booth's, chest's or banker's own name and tile, and where the line came from. */
		public final String name;
		public final int x;
		public final int y;
		public final int plane;
		public final String source;

		Row(String bank, String kind, String needs, int[] use, String name, int x, int y, int plane, String source)
		{
			this.bank = bank;
			this.kind = kind;
			this.needs = needs;
			this.use = use;
			this.name = name;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.source = source;
		}
	}

	private static BankList list;
	private static List<Row> rows;
	private static String problem;

	private ToolBanks()
	{
	}

	/** The banks; empty when the file is missing or can't be read (see {@link #problem()}). */
	public static synchronized BankList list()
	{
		load();
		return list;
	}

	/** Every line with its booth, chest or banker tile (the map layer). */
	public static synchronized List<Row> rows()
	{
		load();
		return rows;
	}

	/** Why there are no banks, or null. */
	public static synchronized String problem()
	{
		load();
		return problem;
	}

	private static void load()
	{
		if (list != null)
		{
			return;
		}
		File f = new File(ToolSettings.findWorkspace(), FILE);
		List<Row> out = new ArrayList<>();
		try
		{
			String text = f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
			list = f.isFile() ? BankList.parse(text) : BankList.NONE;
			problem = f.isFile() ? null : "no " + FILE;
			int i = 0;
			for (String line : text.split("\r?\n"))
			{
				if (line.isBlank() || line.startsWith("#") || line.startsWith(BankList.HEADER))
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				BankList.Bank b = list.getBanks().get(i++);
				out.add(new Row(BankListDiagnostics.name(b), BankListDiagnostics.kind(b), b.getNeeds(), b.getUse(), c.length > 4 ? c[4] : BankListDiagnostics.kind(b),
					c.length > 5 ? Integer.parseInt(c[5]) : -1, c.length > 6 ? Integer.parseInt(c[6]) : -1,
					c.length > 7 ? Integer.parseInt(c[7]) : 0, c.length > 9 ? c[9] : ""));
			}
		}
		catch (IOException | RuntimeException e)
		{
			list = BankList.NONE;
			out.clear();
			problem = "could not read " + FILE + ": " + e.getMessage();
		}
		rows = Collections.unmodifiableList(out);
	}
}

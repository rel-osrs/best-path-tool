package com.bestpath.core.data;

import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.path.BankFirst;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * The places a player can use their bank: bank booths and bank chests from the
 * game cache, and bankers from the OSRS Wiki where a bank has no booth (deposit
 * boxes don't count). One line per booth, chest or banker, with the bank it
 * belongs to, what using it needs (a requirement line) and the tiles a player
 * stands on to use it. Immutable.
 *
 * <p>Text form ({@value #FILE}): {@code #} comment lines, the header
 * {@value #HEADER}, then tab-separated lines. Columns after the fourth are
 * ignored (they may record where each line came from).
 */
public final class BankList
{
	/** The plugin's optional data file. */
	public static final String FILE = "banks.tsv";
	public static final String HEADER = "bank\tkind\tneeds\tuse";

	/** One booth, chest or banker. */
	public static final class Bank
	{
		final String name;
		final String kind;
		final String needs;
		final int[] use;

		public Bank(String name, String kind, String needs, int[] use)
		{
			this.name = name;
			this.kind = kind;
			this.needs = needs == null ? "" : needs;
			this.use = use.clone();
		}

		/** A requirement line; empty = nothing. */
		public String getNeeds()
		{
			return needs;
		}

		/** The tiles a player stands on to use it, packed ({@link Tiles}). */
		public int[] getUse()
		{
			return use.clone();
		}
	}

	public static final BankList NONE = new BankList(List.of());

	final List<Bank> banks;

	public BankList(List<Bank> banks)
	{
		this.banks = Collections.unmodifiableList(new ArrayList<>(banks));
	}

	public List<Bank> getBanks()
	{
		return banks;
	}

	/**
	 * The banks a player may start a route at: those whose needs line {@code needsMet} accepts (met with what the
	 * player has now). On a free world ({@code freeLand} set) only use tiles on free land are kept.
	 */
	public BankFirst.Banks usable(Predicate<String> needsMet,
		FreeLand freeLand)
	{
		List<Integer> tiles = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (Bank b : banks)
		{
			if (b.use.length == 0 || !needsMet.test(b.needs))
			{
				continue;
			}
			for (int t : b.use)
			{
				if (freeLand == null || freeLand.isFree(Tiles.x(t), Tiles.y(t)))
				{
					tiles.add(t);
					names.add(b.name);
				}
			}
		}
		return tiles.isEmpty() ? BankFirst.Banks.NONE
			: new BankFirst.Banks(tiles.stream().mapToInt(Integer::intValue).toArray(), names.toArray(new String[0]));
	}

	/** Reads the text form; a line that can't be read throws, naming its number. */
	public static BankList parse(String text)
	{
		List<Bank> out = new ArrayList<>();
		String[] lines = text.split("\r?\n");
		for (int i = 0; i < lines.length; i++)
		{
			String line = lines[i];
			if (line.isBlank() || line.startsWith("#") || line.startsWith(HEADER))
			{
				continue;
			}
			String[] c = line.split("\t", -1);
			if (c.length < 4)
			{
				throw new IllegalArgumentException("banks line " + (i + 1) + ": 4 columns needed: " + line);
			}
			List<Integer> use = new ArrayList<>();
			for (String t : c[3].trim().split(" +"))
			{
				if (t.isEmpty())
				{
					continue;
				}
				String[] xyz = t.split(",");
				if (xyz.length != 3)
				{
					throw new IllegalArgumentException("banks line " + (i + 1) + ": tile x,y,plane expected: " + t);
				}
				use.add(Tiles.pack(Integer.parseInt(xyz[0].trim()), Integer.parseInt(xyz[1].trim()), Integer.parseInt(xyz[2].trim())));
			}
			out.add(new Bank(c[0], c[1], c[2], use.stream().mapToInt(Integer::intValue).toArray()));
		}
		return new BankList(out);
	}
}

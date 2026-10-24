package com.bestpath.player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Decides which quests to read again (reading one runs a client script, so
 * not on every var change). Pure Java; one thread (the plugin's client thread).
 *
 * <p>Each quest's progress var comes from {@code quest-vars.tsv} (the quest
 * varbit / varp numbers from the Quest Helper plugin's data, BSD-2-Clause):
 * <ul>
 * <li>a change of a quest's own var marks that quest for a re-read and
 * counts as "may matter" (the route may change);</li>
 * <li>any other var change only marks every quest as possibly stale: they
 * are all read again with the next snapshot, but at most once per
 * {@link #FULL_REREAD_TICKS} ticks (a safety net for quests whose state
 * also depends on other vars, and for quests without a known var);</li>
 * <li>all quests are read at the start and after {@link #all()}.</li>
 * </ul>
 */
public final class QuestWatch
{
	/** At most one full re-read of every quest per this many game ticks (60 s), when unrelated vars changed. */
	public static final int FULL_REREAD_TICKS = 100;
	/** Where the quest var table is in the jar. */
	public static final String RESOURCE = "/com/bestpath/player/quest-vars.tsv";

	private final Map<Integer, Set<String>> byVarbit = new HashMap<>();
	private final Map<Integer, Set<String>> byVarp = new HashMap<>();
	private final Set<String> quests;
	private final Set<String> unmapped = new LinkedHashSet<>();
	private final Set<String> dirty = new LinkedHashSet<>();
	private boolean allDirty = true;
	private boolean stale;
	private int lastFullRead = Integer.MIN_VALUE / 2;

	/**
	 * @param quests  the quest names our requirements mention
	 * @param varsOf  a quest name to its progress vars ({@code {kind, id}}, kind 0 = varbit, 1 = varp); empty if unknown
	 */
	public QuestWatch(Collection<String> quests, Function<String, List<int[]>> varsOf)
	{
		this.quests = Collections.unmodifiableSet(new LinkedHashSet<>(quests));
		for (String q : this.quests)
		{
			List<int[]> vars = varsOf.apply(q);
			if (vars == null || vars.isEmpty())
			{
				unmapped.add(q);
				continue;
			}
			for (int[] v : vars)
			{
				(v[0] == 0 ? byVarbit : byVarp).computeIfAbsent(v[1], k -> new LinkedHashSet<>()).add(q);
			}
		}
	}

	/** Quests without a known progress var (read only by the full re-read). */
	public Set<String> getUnmapped()
	{
		return Collections.unmodifiableSet(unmapped);
	}

	/**
	 * A var changed (the RuneLite event: varbit id, or -1 for a varp change, and the varp id).
	 *
	 * @return true if it is a watched quest's own var (that quest is read again with the next snapshot)
	 */
	public boolean onVarChanged(int varbitId, int varpId)
	{
		Set<String> hit = varbitId != -1 ? byVarbit.get(varbitId) : byVarp.get(varpId);
		if (hit != null)
		{
			dirty.addAll(hit);
			return true;
		}
		if (!quests.isEmpty())
		{
			stale = true;
		}
		return false;
	}

	/** Read every quest with the next snapshot (start, account switch, new requirement data). */
	public void all()
	{
		allDirty = true;
	}

	/**
	 * The quests to read now (when a snapshot is taken), and forgets them.
	 *
	 * @param tick the game tick now
	 */
	public Set<String> toRead(int tick)
	{
		if (allDirty || (stale && tick - lastFullRead >= FULL_REREAD_TICKS))
		{
			allDirty = false;
			stale = false;
			dirty.clear();
			lastFullRead = tick;
			return quests;
		}
		if (dirty.isEmpty())
		{
			return Set.of();
		}
		Set<String> out = new LinkedHashSet<>(dirty);
		dirty.clear();
		return out;
	}

	/**
	 * Reads {@code quest-vars.tsv}: RuneLite Quest name -> its vars ({@code {kind, id}}, 0 = varbit, 1 = varp).
	 * Comment lines start with #; bad lines are skipped.
	 */
	public static Map<String, List<int[]>> readTable(InputStream in) throws IOException
	{
		Map<String, List<int[]>> m = new HashMap<>();
		if (in == null)
		{
			return m;
		}
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t");
				if (c.length < 3 || !(c[1].equals("varbit") || c[1].equals("varp")))
				{
					continue;
				}
				try
				{
					int[] v = {c[1].equals("varbit") ? 0 : 1, Integer.parseInt(c[2].trim())};
					m.computeIfAbsent(c[0], k -> new ArrayList<>()).add(v);
				}
				catch (NumberFormatException e)
				{
					// skipped
				}
			}
		}
		return m;
	}

	/** The table in the jar (empty if it can't be read). */
	public static Map<String, List<int[]>> loadTable()
	{
		try (InputStream in = QuestWatch.class.getResourceAsStream(RESOURCE))
		{
			return readTable(in);
		}
		catch (IOException e)
		{
			return new HashMap<>();
		}
	}
}

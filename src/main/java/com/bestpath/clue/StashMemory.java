package com.bestpath.clue;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.runelite.api.gameval.VarbitID;

/**
 * What the plugin remembers about the player's STASH units, per account: each unit filled or empty, and the items
 * taken out of a unit for a clue step and not put back yet. A unit is named by its built varbit
 * ({@code VarbitID.HH_CONSTRUCTED_*}, the clue data's {@code stashVarbit}). The newest news wins, whichever of the
 * three sources it comes from (the game's clue helper, the STASH chart, a deposit or take-out the plugin saw).
 *
 * <p>Saved as two small texts ({@link #encodeFilled}, {@link #encodeTaken}): sorted, so the same contents give the
 * same text, and a change is found by comparing texts; damaged parts are skipped. Pure; client thread.
 */
public final class StashMemory
{
	/**
	 * Units the chart is known never to show as filled (the master unit at Brittle Isle, per the OSRS Wiki's STASH
	 * page): the chart's "empty" is not believed for them (its "filled" is).
	 */
	static final Set<Integer> CHART_HIDES_FILLED = Set.of(VarbitID.HH_CONSTRUCTED_MASTER_SAIL);

	/** Items taken out of a unit for a clue step, not put back yet. Immutable. */
	public static final class Taken
	{
		private final int unit;
		private final String stepKey;
		private final Map<Integer, Integer> items;

		public Taken(int unit, String stepKey, Map<Integer, Integer> items)
		{
			this.unit = unit;
			this.stepKey = stepKey == null ? "" : stepKey;
			this.items = Collections.unmodifiableMap(new TreeMap<>(items));
		}

		/** The unit's built varbit. */
		public int getUnit()
		{
			return unit;
		}

		/** Item id -> count taken out. */
		public Map<Integer, Integer> getItems()
		{
			return items;
		}

		/**
		 * True once the step they were taken out for is over: the player carries another step, or no clue at all.
		 *
		 * @param currentStepKey the carried clue step's key, or null (no clue, or one whose step isn't known)
		 */
		public boolean isDue(String currentStepKey)
		{
			return currentStepKey == null || !currentStepKey.equals(stepKey);
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Taken && ((Taken) o).unit == unit && ((Taken) o).stepKey.equals(stepKey)
				&& ((Taken) o).items.equals(items);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(unit, stepKey, items);
		}

		@Override
		public String toString()
		{
			return "unit " + unit + " for " + stepKey + ": " + items;
		}
	}

	private final Map<Integer, Boolean> filled = new TreeMap<>();
	private Taken taken;

	/** Filled (true), empty (false), or null when nothing is known. */
	public Boolean filled(int unit)
	{
		return filled.get(unit);
	}

	/** Items taken out and not put back yet, or null. */
	public Taken getTaken()
	{
		return taken;
	}

	/**
	 * News about one unit. "Filled" for the unit the items were taken out of means they are back.
	 *
	 * @return true if something remembered changed
	 */
	public boolean set(int unit, boolean isFilled)
	{
		if (unit < 0)
		{
			return false;
		}
		boolean changed = !Boolean.valueOf(isFilled).equals(filled.put(unit, isFilled));
		if (isFilled && taken != null && taken.unit == unit)
		{
			taken = null;
			changed = true;
		}
		return changed;
	}

	/**
	 * The STASH chart was read: every unit it could tell (unit -> filled). Its "empty" is not believed for
	 * {@link #CHART_HIDES_FILLED}.
	 *
	 * @return true if something remembered changed
	 */
	public boolean fromChart(Map<Integer, Boolean> units)
	{
		boolean changed = false;
		for (Map.Entry<Integer, Boolean> e : units.entrySet())
		{
			if (!e.getValue() && CHART_HIDES_FILLED.contains(e.getKey()))
			{
				continue;
			}
			changed |= set(e.getKey(), e.getValue());
		}
		return changed;
	}

	/**
	 * Items were put into a unit: it is filled (and the items taken out of it, if any, are back).
	 *
	 * @return true if something remembered changed
	 */
	public boolean deposited(int unit)
	{
		return set(unit, true);
	}

	/**
	 * Items were taken out of a unit: it is empty. With {@code stepKey} (the carried clue step uses this unit) they are
	 * remembered as to be put back once that step is over; without, any earlier record for this unit is dropped.
	 *
	 * @return true if something remembered changed
	 */
	public boolean takenOut(int unit, String stepKey, Map<Integer, Integer> items)
	{
		boolean changed = set(unit, false);
		Taken before = taken;
		if (stepKey != null && !items.isEmpty())
		{
			taken = new Taken(unit, stepKey, items);
		}
		else if (taken != null && taken.unit == unit)
		{
			taken = null;
		}
		return changed || !Objects.equals(before, taken);
	}

	/**
	 * The items to put back now, or null. Pure.
	 *
	 * @param taken     the items taken out and not put back yet, or null
	 * @param remind    the config switch "Remind to put STASH items back"; off = never
	 * @param unitKnown the unit's place is in the clue data
	 * @param current   the carried clue (null or {@link DetectedClue#NONE}: none, the clue is done)
	 */
	public static Taken due(Taken taken, boolean remind, boolean unitKnown, DetectedClue current)
	{
		if (taken == null || !remind || !unitKnown)
		{
			return null;
		}
		String now = current == null || current.getKind() == DetectedClue.Kind.NONE ? null : current.identity();
		return taken.isDue(now) ? taken : null;
	}

	/**
	 * "Skip" on the put-back card, for good and not just for now: forgets the items to put back when they came
	 * from this unit. Saved like any other change, so the reminder never comes back for those items; a
	 * later take-out for a clue step starts a new one. The unit stays empty (the items are still out).
	 *
	 * @return true if something remembered changed
	 */
	public boolean forgetTaken(int unit)
	{
		if (taken == null || taken.unit != unit)
		{
			return false;
		}
		taken = null;
		return true;
	}

	/** Loads saved texts (null = nothing saved). */
	public void load(String filledText, String takenText)
	{
		filled.clear();
		filled.putAll(decodeFilled(filledText));
		taken = decodeTaken(takenText);
	}

	/** "5229:1,5230:0": unit varbit, 1 filled / 0 empty; sorted. */
	public String encodeFilled()
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, Boolean> e : filled.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(e.getKey()).append(':').append(e.getValue() ? 1 : 0);
		}
		return sb.toString();
	}

	static Map<Integer, Boolean> decodeFilled(String text)
	{
		Map<Integer, Boolean> out = new TreeMap<>();
		if (text == null)
		{
			return out;
		}
		for (String part : text.split(","))
		{
			int colon = part.indexOf(':');
			if (colon <= 0)
			{
				continue;
			}
			try
			{
				int unit = Integer.parseInt(part.substring(0, colon).trim());
				String v = part.substring(colon + 1).trim();
				if (unit > 0 && (v.equals("1") || v.equals("0")))
				{
					out.put(unit, v.equals("1"));
				}
			}
			catch (NumberFormatException e)
			{
				// skip a damaged part
			}
		}
		return out;
	}

	/** "unit=5229\nstep=emote-1234\nitems=1101:1,1131:1"; "" when nothing waits to be put back. */
	public String encodeTaken()
	{
		if (taken == null)
		{
			return "";
		}
		StringBuilder items = new StringBuilder();
		for (Map.Entry<Integer, Integer> e : taken.items.entrySet())
		{
			if (items.length() > 0)
			{
				items.append(',');
			}
			items.append(e.getKey()).append(':').append(e.getValue());
		}
		return "unit=" + taken.unit + "\nstep=" + taken.stepKey + "\nitems=" + items;
	}

	static Taken decodeTaken(String text)
	{
		if (text == null || text.isBlank())
		{
			return null;
		}
		int unit = -1;
		String step = null;
		Map<Integer, Integer> items = new TreeMap<>();
		for (String line : text.split("\n"))
		{
			int eq = line.indexOf('=');
			if (eq <= 0)
			{
				continue;
			}
			String k = line.substring(0, eq).trim();
			String v = line.substring(eq + 1).trim();
			try
			{
				if (k.equals("unit"))
				{
					unit = Integer.parseInt(v);
				}
				else if (k.equals("step"))
				{
					step = v;
				}
				else if (k.equals("items"))
				{
					for (String part : v.split(","))
					{
						int colon = part.indexOf(':');
						if (colon > 0)
						{
							try
							{
								int id = Integer.parseInt(part.substring(0, colon).trim());
								int n = Integer.parseInt(part.substring(colon + 1).trim());
								if (id > 0 && n > 0)
								{
									items.merge(id, n, Integer::sum);
								}
							}
							catch (NumberFormatException e)
							{
								// skip a damaged part
							}
						}
					}
				}
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}
		return unit > 0 && step != null && !step.isEmpty() && !items.isEmpty() ? new Taken(unit, step, items) : null;
	}
}

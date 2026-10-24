package com.bestpath.clue;

import java.util.Objects;

/**
 * A clue step's STASH unit as the game's own vars tell it, read the way the game's client scripts do:
 * <ul>
 * <li><b>Built</b>: the STASH object's form varbit ({@code VarbitID.HH_CONSTRUCTED_*}, one bit of the tier's varp
 * {@code VarPlayerID.HH_CONSTRUCTED_<tier>}): 1 = built, 0 = not built (scripts 6472, 1479).</li>
 * <li><b>Filled</b>: the game keeps no per-unit var. Its clue helper shows "Filled" for the clue it tracks
 * ({@code VarPlayerID.CLUEHELPER_INFOBOX_CLUE}, the clue's cache row, set by the server) when
 * {@code VarbitID.CLUEHELPER_HIDEYHOLE_FILLED} is 1 (script 6624). The plugin also remembers each unit per account
 * ({@link StashMemory}: the clue helper, the STASH chart, deposits and take-outs it saw; the newest wins). The
 * remembered value is used first; without one, the clue helper when it tracks this very step; otherwise
 * {@link Filled#UNKNOWN}.</li>
 * </ul>
 * Immutable; read on the client thread ({@link #read}), shown on the Swing thread.
 */
public final class StashState
{
	public enum Built
	{
		YES,
		NO,
		/** Not logged in, or the step has no STASH unit. */
		UNKNOWN
	}

	public enum Filled
	{
		YES,
		NO,
		/** The game's clue helper is not tracking this step, so the game doesn't tell. */
		UNKNOWN
	}

	/** Nothing known (no STASH unit, not logged in). */
	public static final StashState UNKNOWN = new StashState(Built.UNKNOWN, Filled.UNKNOWN);

	private final Built built;
	private final Filled filled;

	public StashState(Built built, Filled filled)
	{
		this.built = Objects.requireNonNull(built);
		this.filled = Objects.requireNonNull(filled);
	}

	/**
	 * The state from the game's values and what the plugin remembers about this unit ({@link StashMemory}).
	 *
	 * @param builtValue    the step's STASH varbit (1 built, 0 not; anything else: unknown)
	 * @param helperClueRow the clue helper's clue ({@code VarPlayerID.CLUEHELPER_INFOBOX_CLUE}: a cache row, -1 none)
	 * @param filledValue   {@code VarbitID.CLUEHELPER_HIDEYHOLE_FILLED}
	 * @param stepRow       this step's cache row ({@code ClueData.Step#getRow})
	 * @param remembered    filled (true), empty (false), or null when nothing is remembered
	 */
	public static StashState read(int builtValue, int helperClueRow, int filledValue, int stepRow, Boolean remembered)
	{
		Built b = builtValue == 1 ? Built.YES : builtValue == 0 ? Built.NO : Built.UNKNOWN;
		Filled f;
		if (b != Built.NO && remembered != null)
		{
			// the newest news of the clue helper, the STASH chart or a deposit / take-out the plugin saw
			f = remembered ? Filled.YES : Filled.NO;
		}
		else if (stepRow >= 0 && helperClueRow == stepRow)
		{
			// the game's own clue helper line (script 6624): "Filled" when the varbit is 1
			f = filledValue == 1 ? Filled.YES : Filled.NO;
		}
		else
		{
			f = b == Built.NO ? Filled.NO : Filled.UNKNOWN; // a unit that isn't built holds nothing
		}
		return new StashState(b, f);
	}

	public Built getBuilt()
	{
		return built;
	}

	public Filled getFilled()
	{
		return filled;
	}

	/** True if the outfit is known to be waiting in the STASH unit. */
	public boolean holdsOutfit()
	{
		return filled == Filled.YES;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof StashState && ((StashState) o).built == built && ((StashState) o).filled == filled;
	}

	@Override
	public int hashCode()
	{
		return built.hashCode() * 31 + filled.hashCode();
	}

	@Override
	public String toString()
	{
		return "built " + built + ", filled " + filled;
	}
}

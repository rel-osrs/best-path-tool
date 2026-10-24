package com.bestpath.route;

import com.bestpath.core.req.Check;

/**
 * Whether the player has one thing a card needs: a green check, a bank symbol or a red X. Worked out with the
 * requirement model's own checks ({@link com.bestpath.core.req.Requirement#check}), so staffs, the rune pouch, worn
 * items and "every item" count the way the route search counts them.
 */
public enum NeedStatus
{
	/** Met now: carried or worn, the level or quest done, the cooldown ready (a green check). */
	HAVE("you have it"),
	/** Not carried, but met with the bank's items (the bank symbol). */
	BANK("in your bank"),
	/** Neither (a red X). */
	MISSING("you don't have it"),
	/**
	 * Not known for sure (a question mark): e.g. an item that may be in a STASH unit, or an axe that may be stored at
	 * the canoe stations, which the plugin hasn't seen yet. Not assumed missing.
	 */
	MAYBE("not known for sure: Best Path hasn't seen it yet"),
	/** Not known (no player to check against): no mark. */
	UNKNOWN("");

	private final String words;

	NeedStatus(String words)
	{
		this.words = words;
	}

	/** The mark's mouse-over in words ("you have it", "in your bank", "you don't have it"; "" when unknown). */
	public String getWords()
	{
		return words;
	}

	/** The status of a requirement's check: met, met with the bank, or not met. */
	public static NeedStatus of(Check c)
	{
		switch (c.getOutcome())
		{
			case MET:
				return HAVE;
			case NEEDS_BANK:
				return BANK;
			case NOT_MET:
			default:
				return MISSING;
		}
	}
}

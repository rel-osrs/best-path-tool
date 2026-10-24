package com.bestpath.player;

/**
 * One per-account value (the bank, the ownership cache) written to the
 * account's profile once its changes settle, instead of at every change: a
 * banking session of 30 deposits is one write. The value remembers which
 * account profile it belongs to, so a write that happens after an account
 * switch still goes to the right account. Pure Java; one thread (the
 * client thread).
 */
public final class DelayedSave
{
	/** Write after this many ticks without a new change (6 s)... */
	public static final int QUIET_TICKS = 10;
	/** ...or at the latest this many ticks after the first unsaved change (60 s). */
	public static final int MAX_WAIT_TICKS = 100;

	/** What to write: the account profile and the text. */
	public static final class Write
	{
		private final String profile;
		private final String text;

		Write(String profile, String text)
		{
			this.profile = profile;
			this.text = text;
		}

		/** The account profile key the value belongs to (null = this account has no profile yet). */
		public String getProfile()
		{
			return profile;
		}

		public String getText()
		{
			return text;
		}
	}

	private final Debouncer timer = new Debouncer(QUIET_TICKS, MAX_WAIT_TICKS);
	private String saved;
	private String profile;
	private String text;

	/** The text already in the profile (after loading it); nothing is waiting any more. */
	public void loaded(String savedText)
	{
		saved = savedText;
		text = null;
		profile = null;
		timer.clear();
	}

	/**
	 * The value changed. Nothing waits if it is the same as the saved text (diff before persisting).
	 *
	 * @param profile the account profile it belongs to (null if the account has none yet)
	 */
	public void changed(String profile, String newText, int tick)
	{
		if (newText.equals(saved))
		{
			text = null;
			timer.clear();
			return;
		}
		this.profile = profile;
		this.text = newText;
		timer.mark(tick);
	}

	/** True when the waiting change should be written now. */
	public boolean due(int tick)
	{
		return text != null && timer.ready(tick);
	}

	/** The waiting change (null if none), to write now; call {@link #written} after a successful write. */
	public Write pending()
	{
		return text == null ? null : new Write(profile, text);
	}

	/** The pending change was written. */
	public void written()
	{
		if (text != null)
		{
			saved = text;
		}
		text = null;
		profile = null;
		timer.clear();
	}
}

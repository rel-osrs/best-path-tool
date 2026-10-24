package com.bestpath.player;

/**
 * Waits for a burst of game events to settle before acting once (counted in
 * game ticks): {@link #ready} is true when something was marked and either
 * nothing new came for {@code quietTicks}, or {@code maxWaitTicks} passed since
 * the first mark (so endless small changes still get handled now and then).
 * One thread only (the client thread).
 */
public final class Debouncer
{
	private final int quietTicks;
	private final int maxWaitTicks;
	private boolean marked;
	private int firstTick;
	private int lastTick;

	public Debouncer(int quietTicks, int maxWaitTicks)
	{
		if (quietTicks < 0 || maxWaitTicks < quietTicks)
		{
			throw new IllegalArgumentException("need 0 <= quietTicks <= maxWaitTicks");
		}
		this.quietTicks = quietTicks;
		this.maxWaitTicks = maxWaitTicks;
	}

	/** Something changed at this tick. */
	public void mark(int tick)
	{
		if (!marked)
		{
			marked = true;
			firstTick = tick;
		}
		lastTick = tick;
	}

	/** True when it is time to act; the caller then calls {@link #clear}. */
	public boolean ready(int tick)
	{
		return marked && (tick - lastTick >= quietTicks || tick - firstTick >= maxWaitTicks);
	}

	public void clear()
	{
		marked = false;
	}
}

package com.bestpath.player;

import net.runelite.api.GameState;

/**
 * Tells a real login or world hop apart from a scene load: the game goes
 * LOADING -> LOGGED_IN after every scene load too, and that changes nothing
 * the routes depend on. A LOGGED_IN counts only when a state other than
 * LOADING (login screen, hopping, connection lost...) came since the last
 * one. One thread (the client thread).
 */
public final class LoginEdges
{
	private boolean away = true;

	/** @return true if this state is a login or the end of a world hop (members may have changed) */
	public boolean onState(GameState s)
	{
		if (s == GameState.LOGGED_IN)
		{
			boolean real = away;
			away = false;
			return real;
		}
		if (s != GameState.LOADING)
		{
			away = true;
		}
		return false;
	}
}

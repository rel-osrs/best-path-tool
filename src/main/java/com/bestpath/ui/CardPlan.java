package com.bestpath.ui;

import com.bestpath.route.RouteStep;
import java.util.ArrayList;
import java.util.List;

/**
 * Which steps of a route get a card in the sidebar and how each card looks
 * while the player follows the route. Walking steps get no card; the
 * overlays still draw the whole path. Pure functions, no Swing.
 */
final class CardPlan
{
	/** How a card looks for the player's progress. */
	enum State
	{
		/** Not reached yet (or no progress). */
		NONE,
		/** Done: greyed. */
		DONE,
		/** The player is at this step now. */
		CURRENT,
		/** The player is walking to this step (the walk itself has no card). */
		NEXT
	}

	private CardPlan()
	{
	}

	/** True if the step gets a card: everything but walks. */
	static boolean hasCard(RouteStep s)
	{
		return s.getKind() != RouteStep.Kind.WALK;
	}

	/** The indices (in the route's steps) of the steps with a card, in order; card k is numbered k + 1. */
	static int[] cardSteps(List<RouteStep> steps)
	{
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < steps.size(); i++)
		{
			if (hasCard(steps.get(i)))
			{
				out.add(i);
			}
		}
		int[] a = new int[out.size()];
		for (int i = 0; i < a.length; i++)
		{
			a[i] = out.get(i);
		}
		return a;
	}

	/** Tiles walked over the whole route (each walk's tiles after its first). */
	static int walkTiles(List<RouteStep> steps)
	{
		int n = 0;
		for (RouteStep s : steps)
		{
			if (s.getKind() == RouteStep.Kind.WALK)
			{
				n += Math.max(0, s.tileCount() - 1);
			}
		}
		return n;
	}

	/**
	 * Each card's look when the player is at step {@code current} (an index in {@code steps}; -1 = not following).
	 * Cards before it are done; its own card is current; when the current step is a walk (no card), the first card
	 * after it is "next".
	 */
	static State[] states(List<RouteStep> steps, int[] cardSteps, int current)
	{
		State[] out = new State[cardSteps.length];
		boolean walking = current >= 0 && current < steps.size() && !hasCard(steps.get(current));
		boolean nextGiven = false;
		for (int c = 0; c < cardSteps.length; c++)
		{
			int i = cardSteps[c];
			if (current < 0 || current >= steps.size())
			{
				out[c] = State.NONE;
			}
			else if (i < current)
			{
				out[c] = State.DONE;
			}
			else if (i == current)
			{
				out[c] = State.CURRENT;
			}
			else if (walking && !nextGiven)
			{
				out[c] = State.NEXT;
				nextGiven = true;
			}
			else
			{
				out[c] = State.NONE;
			}
		}
		return out;
	}

	/**
	 * The length in tiles of the walk from step {@code current} (a walk) to the next step with a card; 0 if the
	 * current step is not a walk. (The sidebar hears of progress once per step, not per tile, so this is the walk's
	 * whole length, not what is left of it.)
	 */
	static int walkToNext(List<RouteStep> steps, int current)
	{
		int n = 0;
		for (int i = current; i >= 0 && i < steps.size() && !hasCard(steps.get(i)); i++)
		{
			n += Math.max(0, steps.get(i).tileCount() - 1);
		}
		return n;
	}
}

package com.bestpath.core.path;

/** The ticks of a walk: one per step, plus the door cost for each step through a door. */
final class WalkCost
{
	private WalkCost()
	{
	}

	static int cost(int[] t, DoorMap doors, int doorCost)
	{
		int c = t.length - 1;
		if (doors != null && doorCost > 0)
		{
			for (int i = 1; i < t.length; i++)
			{
				if (doors.crosses(t[i - 1], t[i]))
				{
					c += doorCost;
				}
			}
		}
		return c;
	}
}

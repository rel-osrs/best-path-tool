package com.bestpath.core.path;

import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.SeaRouter;
import java.util.ArrayList;
import java.util.List;

/** A sailing leg found by the sea search: the boat, where it is boarded and left, and (once finished) its course. */
final class SeaLeg
{
	Boat boat;
	SeaBoarding from;
	/** Dock left at, or -1 (ends at sea). */
	int to;
	/** The sea search and the end it comes from (the course is worked out only for legs a route may use). */
	SeaSearch search;
	int end;
	/**
	 * The boat's course, one piece per stretch of water: the boat-only transports it goes through
	 * ({@link #through}) come between them ({@link #finish}).
	 */
	List<SeaRouter.Course> courses;
	/** The boat-only transports between the pieces of the course, in order (one fewer than the pieces). */
	List<SeaLink> through;
	/** Per piece of the course its real sailing ticks. */
	int[] pieceTicks;
	/** Ticks from the route's start to the end of the leg, leaving included. */
	int arrive;
	/** Where an ocean-only leg ends, for the step's name (null: a dock's name, or "the destination"). */
	String endName;

	/** Works out the course and the real sailing ticks. */
	void finish()
	{
		courses = new ArrayList<>();
		through = new ArrayList<>();
		search.pieces(end, courses, through);
		pieceTicks = new int[courses.size()];
		for (int i = 0; i < pieceTicks.length; i++)
		{
			// the course as drawn (pulled straight), at the boat's speed
			pieceTicks[i] = (courses.get(i).time(boat.speed()) + 99) / 100;
		}
	}
}

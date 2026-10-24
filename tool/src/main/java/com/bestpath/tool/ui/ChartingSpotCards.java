package com.bestpath.tool.ui;

import com.bestpath.core.data.ChartingTasks;
import com.bestpath.core.data.ChartingTasksDiagnostics;
import com.bestpath.core.path.Tiles;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.extract.ChartingSpots;

/**
 * The sea charting cards: one Boat / Land card per charting task that is not on open sea, pointing at the task's
 * spot. The answer is saved as a {@code CHARTING_SPOT} change.
 */
final class ChartingSpotCards
{
	/** Card ids of sea charting spots start with this, then the task number. */
	static final String ID = "chart-";

	private ChartingSpotCards()
	{
	}

	/** The card for a task's spot: it points at Quest Helper's place; its words; the place and the nearest sea dotted. */
	static QuestionCards.Question question(ChartingSpots.Spot s)
	{
		QuestionCards.Question q = new QuestionCards.Question();
		q.id = ID + s.task.getNumber();
		q.action = QuestionCards.Action.CHARTING_SPOT;
		q.task = s.task.getNumber();
		q.guessBoat = s.guessBoat;
		q.x = Tiles.x(s.place);
		q.y = Tiles.y(s.place);
		q.plane = Tiles.plane(s.place);
		q.places = s.sea < 0 ? new int[]{s.place} : new int[]{s.place, s.sea};
		q.text = text(s);
		return q;
	}

	/** "Sea charting task 0 (Bay of Sarim, find): Look at ... Done from a boat or on land? Guess: land (...)." */
	static String text(ChartingSpots.Spot s)
	{
		return "Sea charting task " + s.task.getNumber() + " (" + ChartingTasksDiagnostics.sea(s.task) + ", " + kindWord(s.task) + "): " + s.task.getText()
			+ (s.task.getTip().isEmpty() ? "" : " Quest Helper's tip: " + s.task.getTip()) + " Done from a boat or on land?"
			+ " Guess: " + (s.guessBoat ? "boat" : "land") + " (" + s.why + "). Until you answer, routes use the guess."
			+ (s.sea < 0 ? " There is no open sea within 30 tiles." : " The second dot is the nearest open sea, where a boat"
			+ " would wait.");
	}

	/** The kind's word for a card ("find", "spyglass", ...). */
	private static String kindWord(ChartingTasks.Task t)
	{
		return t.getKind().equals("generic") ? "find" : t.getKind().equals("crate") ? "drink crate"
			: t.getKind().equals("current") ? "current duck" : t.getKind();
	}

	/** The change a charting card's answer makes ({@code word} BOAT or LAND). */
	static Edit answer(QuestionCards.Question q, String word)
	{
		Edit e = new Edit();
		e.type = Edit.Type.CHARTING_SPOT;
		e.target = String.valueOf(q.task);
		e.on = word.equals("BOAT");
		e.x = q.x;
		e.y = q.y;
		e.plane = q.plane;
		e.note = "Card: " + (e.on ? "boat" : "land") + (e.on == q.guessBoat ? " (as guessed)" : " (not the guess)");
		return e;
	}
}

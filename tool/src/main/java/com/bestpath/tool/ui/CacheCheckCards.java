package com.bestpath.tool.ui;

import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.update.CompareFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The game update cards: one card per finding of the newest game update comparison that needs a look and has no
 * answer yet. Which buttons each kind has, and what each
 * answer changes, are fixed here: an answer is a {@code CACHE_CHECK} change (so the card is not made again), plus, for a
 * delete, take-back or remove, that change in the same undo step. Nothing is changed without your click.
 */
final class CacheCheckCards
{
	/** Card ids start with this, then the finding's key. */
	static final String ID = "cc-";
	/** The most detail lines on a card (the report has them all). */
	static final int DETAIL_LINES = 6;

	/** One button: the answer word saved, its label, its tooltip. */
	static final class Button
	{
		final String word;
		final String label;
		final String tip;

		Button(String word, String label, String tip)
		{
			this.word = word;
			this.label = label;
			this.tip = tip;
		}
	}

	private CacheCheckCards()
	{
	}

	/** The card for a finding of the comparison from cache {@code from} to {@code to}. */
	static QuestionCards.Question question(CompareFile.Item i, int from, int to)
	{
		QuestionCards.Question q = new QuestionCards.Question();
		q.id = ID + i.key;
		q.action = QuestionCards.Action.CACHE_CHECK;
		q.x = i.x;
		q.y = i.y;
		q.plane = Math.max(0, i.plane);
		q.checkKey = i.key;
		q.checkKind = i.kind;
		q.checkRef = i.ref;
		StringBuilder text = new StringBuilder("Game update #" + from + " -> #" + to + ". " + i.title + ": " + i.text);
		for (int k = 0; k < i.details.size() && k < DETAIL_LINES; k++)
		{
			text.append("\n").append(i.details.get(k).replace("  ", " - "));
		}
		if (i.details.size() > DETAIL_LINES)
		{
			text.append("\n(more in the report)");
		}
		q.text = text.toString();
		return q;
	}

	/** The buttons of a card of finding kind {@code kind} ({@code CacheCompare.Kind} name). */
	static List<Button> buttons(String kind)
	{
		List<Button> out = new ArrayList<>();
		switch (kind)
		{
			case "OBJECT_GONE":
			case "OBJECT_MOVED":
			case "OBJECT_REPLACED":
			case "OPTION_LOST":
			case "GROUND_CHANGED":
				out.add(new Button("KEEP", "Still right", "The transport is right as it is, or you have fixed it on the map"));
				out.add(new Button("DELETE", "Delete transport", "Delete the transport (Ctrl+Z or the Change log takes it back)"));
				break;
			case "EDIT_TILE_CHANGED":
				out.add(new Button("KEEP", "Keep my change", "Your tile or wall change stays on top of the game's new tile"));
				out.add(new Button("TAKE_BACK", "Use the game's tile",
					"Take your change back, so the game's tile is used (Ctrl+Z or the Change log puts it back)"));
				break;
			case "SHUT_DOOR_GONE":
				out.add(new Button("KEEP", "Keep", "Leave your change as it is"));
				out.add(new Button("TAKE_BACK", "Take it back", "Take back your \"shut\" change (Ctrl+Z or the Change log puts it back)"));
				break;
			case "PLACE_KIND_GONE":
				out.add(new Button("KEEP", "Keep", "Leave the kind as it is (it has no places for now)"));
				out.add(new Button("REMOVE", "Remove the kind",
					"Leave it out of the plugin's Nearest list (Ctrl+Z or the Change log takes it back)"));
				break;
			default:
				out.add(new Button("CHECKED", "Checked", "You looked at it; nothing to change"));
				break;
		}
		return out;
	}

	/**
	 * The changes an answer makes, apart from a take-back (which the cards do with the change log's undo of change
	 * {@code checkRef}): the {@code CACHE_CHECK} change, then the transport deleted or the place kind removed.
	 */
	static List<Edit> answer(QuestionCards.Question q, String word)
	{
		Edit check = new Edit();
		check.type = Edit.Type.CACHE_CHECK;
		check.target = q.checkKey;
		check.on = true;
		check.name = word;
		check.x = q.x;
		check.y = q.y;
		check.plane = q.plane;
		check.note = "Card: " + word.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
		if (word.equals("DELETE"))
		{
			Edit del = new Edit();
			del.type = Edit.Type.TRANSPORT_DELETE;
			del.target = q.checkRef;
			del.x = q.x;
			del.y = q.y;
			del.plane = q.plane;
			del.note = "Game update card: deleted";
			List<Edit> out = new ArrayList<>();
			out.add(check);
			out.add(del);
			return out;
		}
		if (word.equals("REMOVE"))
		{
			Edit kind = new Edit();
			kind.type = Edit.Type.PLACE_KIND;
			kind.target = q.checkRef;
			kind.on = true;
			kind.x = q.x;
			kind.y = q.y;
			kind.plane = q.plane;
			kind.note = "Game update card: removed";
			List<Edit> out = new ArrayList<>();
			out.add(check);
			out.add(kind);
			return out;
		}
		return Collections.singletonList(check);
	}
}

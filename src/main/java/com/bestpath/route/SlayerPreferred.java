package com.bestpath.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The Slayer places the player marked as preferred: shown at the top of a task's list with a star, and listed in the
 * Settings tab. One mark is a task ("Kalphites") and a place of it ({@link SlayerTask.Location#key}): preferring a
 * place for one task leaves it unmarked for the others. When a task has preferred places, "Go to the nearest place"
 * and "Automatic Slayer routes" pick only among them ({@link SlayerTask#nearest}). Immutable; sorted by task, then
 * place; saved per account as text ({@link #toText}).
 */
public final class SlayerPreferred
{
	public static final SlayerPreferred NONE = new SlayerPreferred(new TreeSet<>());

	/** One preferred place: the task and the place's key. */
	public static final class Mark implements Comparable<Mark>
	{
		private final String task;
		private final String place;

		Mark(String task, String place)
		{
			this.task = task;
			this.place = place;
		}

		/** The task's name, as the game writes it ("Kalphites"). */
		public String getTask()
		{
			return task;
		}

		/** The place's key ({@link SlayerTask.Location#key}: its name, "@", its first point). */
		public String getPlace()
		{
			return place;
		}

		/** The place's name ("Kalphite Lair"): its key without the point. */
		public String getPlaceName()
		{
			int at = place.lastIndexOf('@');
			return at < 0 ? place : place.substring(0, at);
		}

		@Override
		public int compareTo(Mark o)
		{
			int c = task.compareToIgnoreCase(o.task);
			c = c != 0 ? c : task.compareTo(o.task);
			c = c != 0 ? c : getPlaceName().compareToIgnoreCase(o.getPlaceName());
			return c != 0 ? c : place.compareTo(o.place);
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Mark && ((Mark) o).task.equals(task) && ((Mark) o).place.equals(place);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(task, place);
		}
	}

	private final TreeSet<Mark> marks;

	private SlayerPreferred(TreeSet<Mark> marks)
	{
		this.marks = marks;
	}

	/** Every mark, by task, then place. */
	public List<Mark> list()
	{
		return Collections.unmodifiableList(new ArrayList<>(marks));
	}

	public boolean isEmpty()
	{
		return marks.isEmpty();
	}

	/** This task's place (its {@link SlayerTask.Location#key}) is preferred. */
	public boolean has(String task, String place)
	{
		return task != null && place != null && marks.contains(new Mark(task, place));
	}

	/** With this place of this task preferred ({@code on}) or not. */
	public SlayerPreferred with(String task, String place, boolean on)
	{
		if (task == null || place == null || clean(task).isEmpty() || clean(place).isEmpty() || has(task, place) == on)
		{
			return this;
		}
		TreeSet<Mark> next = new TreeSet<>(marks);
		Mark m = new Mark(clean(task), clean(place));
		if (on)
		{
			next.add(m);
		}
		else
		{
			next.remove(m);
		}
		return new SlayerPreferred(next);
	}

	/** One line, no tabs (the text's separators). */
	private static String clean(String s)
	{
		return s.replaceAll("[\\t\\r\\n]+", " ").trim();
	}

	/** One line per mark: {@code <task> TAB <place key>}. */
	public String toText()
	{
		StringBuilder b = new StringBuilder();
		for (Mark m : marks)
		{
			b.append(m.task).append('\t').append(m.place).append('\n');
		}
		return b.toString();
	}

	/** Reads {@link #toText}; lines it cannot read are left out (null or empty: none). */
	public static SlayerPreferred fromText(String text)
	{
		SlayerPreferred p = NONE;
		if (text == null)
		{
			return p;
		}
		for (String line : text.split("\n"))
		{
			String[] parts = line.split("\t", -1);
			if (parts.length == 2)
			{
				p = p.with(parts[0], parts[1], true);
			}
		}
		return p;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof SlayerPreferred && marks.equals(((SlayerPreferred) o).marks);
	}

	@Override
	public int hashCode()
	{
		return marks.hashCode();
	}
}

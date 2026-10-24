package com.bestpath.route;

import com.bestpath.core.req.Requirement;
import com.bestpath.player.QuestWatch;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Quest;

/**
 * A player's note for a varbit / varp need on a "no route" card, for when developer details are off. A quest's own
 * progress var ({@code quest-vars.tsv}) names the quest, without the exact step ("Partial progress through ...");
 * any other var is an unlock. The numbers stay in the developer details ({@link RequirementCheck}). Pure; the table
 * is read once.
 */
final class VarNotes
{
	/** Not unlocked yet (a var that is not a quest's). */
	static final String LOCKED = "Not unlocked yet on this account";
	/** Ruled out by something on the account now (a var that must be unset, not a quest's). */
	static final String RULED_OUT = "Not available on this account right now";

	/** {@code "varbit 9649"} / {@code "varp 328"} -> the quest's name. */
	private static volatile Map<String, String> table;

	private VarNotes()
	{
	}

	/**
	 * The note for a need that is a var, a "not" of a var, or anything else with a var inside; null for a need with no
	 * var (its own text is plain already).
	 */
	static String of(Requirement r)
	{
		return of(r, quests());
	}

	/** As {@link #of(Requirement)}, with the var -> quest name table given. */
	static String of(Requirement r, Map<String, String> quests)
	{
		if (r instanceof Requirement.Var)
		{
			return note((Requirement.Var) r, false, quests);
		}
		if (r instanceof Requirement.Not && ((Requirement.Not) r).getPart() instanceof Requirement.Var)
		{
			return note((Requirement.Var) ((Requirement.Not) r).getPart(), true, quests);
		}
		String text = r.toText().toLowerCase(Locale.ROOT);
		return text.contains("varbit") || text.contains("varp") ? LOCKED : null;
	}

	/** {@code not}: the need is that the var does NOT compare so. */
	private static String note(Requirement.Var v, boolean not, Map<String, String> quests)
	{
		// does meeting it take the var further (a quest further on, an unlock set) or keep it back?
		boolean further;
		switch (v.getOp())
		{
			case EQ:
				further = v.getValue() != 0;
				break;
			case LT:
			case LE:
				further = false;
				break;
			default:
				// GT, GE, NE, the bit test
				further = true;
				break;
		}
		if (not)
		{
			further = !further;
		}
		String quest = quests.get(key(v.isVarp(), v.getId()));
		if (quest != null)
		{
			return further ? "Partial progress through " + quest : "Only before a certain point in " + quest;
		}
		return further ? LOCKED : RULED_OUT;
	}

	static String key(boolean varp, int id)
	{
		return (varp ? "varp " : "varbit ") + id;
	}

	private static Map<String, String> quests()
	{
		Map<String, String> t = table;
		if (t == null)
		{
			t = questsOf(QuestWatch.loadTable());
			table = t;
		}
		return t;
	}

	/** quest-vars.tsv (RuneLite Quest name -> its vars) turned round: var -> the quest's name as players see it. */
	static Map<String, String> questsOf(Map<String, List<int[]>> questVars)
	{
		Map<String, String> out = new HashMap<>();
		for (Map.Entry<String, List<int[]>> e : questVars.entrySet())
		{
			String name = questName(e.getKey());
			for (int[] v : e.getValue())
			{
				out.merge(key(v[0] == 1, v[1]), name, (a, b) -> a.equals(b) ? a : a + " / " + b);
			}
		}
		return out;
	}

	/** RuneLite's Quest name ("CHILDREN_OF_THE_SUN") as players see it ("Children of the Sun"). */
	static String questName(String runeLiteName)
	{
		try
		{
			return Quest.valueOf(runeLiteName).getName();
		}
		catch (IllegalArgumentException e)
		{
			StringBuilder sb = new StringBuilder();
			for (String w : runeLiteName.toLowerCase(Locale.ROOT).split("_+"))
			{
				if (!w.isEmpty())
				{
					sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
				}
			}
			return sb.toString();
		}
	}
}

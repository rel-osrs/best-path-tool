package com.bestpath.tool.req;

import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementDiagnostics;
import java.util.ArrayList;
import java.util.List;

/**
 * A requirement in plain words for the Data Tool ("Monkey Madness II done",
 * "carrying or wearing Sapphire lantern (4702)"): one entry per part that is
 * needed, so a transport's needs can be read at a glance.
 */
public final class RequirementWords
{
	private RequirementWords()
	{
	}

	/** One entry per needed part; empty when nothing is needed. */
	public static List<String> parts(Requirement r)
	{
		List<String> out = new ArrayList<>();
		if (r == null || r.isNone())
		{
			return out;
		}
		if (r instanceof Requirement.All)
		{
			for (Requirement p : ((Requirement.All) r).getParts())
			{
				out.add(words(p));
			}
		}
		else
		{
			out.add(words(r));
		}
		return out;
	}

	/** One part in words. */
	public static String words(Requirement r)
	{
		if (r instanceof Requirement.All)
		{
			List<String> each = new ArrayList<>();
			for (Requirement p : ((Requirement.All) r).getParts())
			{
				each.add(words(p));
			}
			return each.isEmpty() ? "nothing" : String.join(" and ", each);
		}
		if (r instanceof Requirement.AnyOf)
		{
			List<String> each = new ArrayList<>();
			for (Requirement p : ((Requirement.AnyOf) r).getAlternatives())
			{
				each.add(words(p));
			}
			return "ONE OF: " + String.join("  OR  ", each);
		}
		if (r instanceof Requirement.Not)
		{
			return "NOT (" + words(((Requirement.Not) r).getPart()) + ")";
		}
		if (r instanceof Requirement.Quest)
		{
			return questWords((Requirement.Quest) r);
		}
		if (r instanceof Requirement.Var)
		{
			Requirement.Var v = (Requirement.Var) r;
			return "game value " + (v.isVarp() ? "varp " : "varbit ") + v.getId() + " " + opWords(v.getOp()) + " " + v.getValue();
		}
		if (r instanceof Requirement.Item)
		{
			return itemWords((Requirement.Item) r);
		}
		return otherWords(r);
	}

	private static String questWords(Requirement.Quest q)
	{
		switch (q.getWanted())
		{
			case STARTED:
				return q.getQuest() + " started (or done)";
			case NOT_STARTED:
				return q.getQuest() + " not started yet";
			case NOT_DONE:
				return q.getQuest() + " not done yet";
			default:
				return q.getQuest() + " done";
		}
	}

	private static String itemWords(Requirement.Item i)
	{
		String what = i.getLabel().matches("[0-9/]+") ? itemNames(i.getItemIds()) : "a " + i.getLabel() + " (item group)";
		return (i.isWorn() ? "wearing " : "carrying or wearing ") + (i.getCount() > 1 ? i.getCount() + " x " : "") + what
			+ (i.isUsedUp() ? ", used up" : "");
	}

	/** The remaining kinds of part, each a short phrase; the requirement's own text for any other. */
	private static String otherWords(Requirement r)
	{
		if (r instanceof Requirement.Members)
		{
			return "members world";
		}
		if (r instanceof Requirement.Coins)
		{
			return ((Requirement.Coins) r).getAmount() + " coins";
		}
		if (r instanceof Requirement.Answer)
		{
			return "the player says: " + ((Requirement.Answer) r).getAnswer();
		}
		if (r instanceof Requirement.CollectionLog)
		{
			return "in the collection log: item " + RequirementDiagnostics.label(((Requirement.CollectionLog) r));
		}
		if (r instanceof Requirement.SpiritTree)
		{
			return "own spirit tree grown at " + RequirementDiagnostics.place(((Requirement.SpiritTree) r));
		}
		if (r instanceof Requirement.CanoeAxeStored)
		{
			return "an axe stored at the canoe stations";
		}
		if (r instanceof Requirement.EmptySlot)
		{
			return "nothing worn in the " + ((Requirement.EmptySlot) r).getSlot().getLabel() + " slot";
		}
		if (r instanceof Requirement.OnSpellbook)
		{
			return "on the " + RequirementDiagnostics.spellbook(((Requirement.OnSpellbook) r)).getLabel() + " spellbook";
		}
		if (r instanceof Requirement.Runes)
		{
			Requirement.Runes ru = (Requirement.Runes) r;
			return ru.getCount() + " x " + ru.getRune() + " rune";
		}
		if (r instanceof Requirement.House)
		{
			return houseWords((Requirement.House) r);
		}
		if (r instanceof Requirement.SkillLevel)
		{
			Requirement.SkillLevel s = (Requirement.SkillLevel) r;
			return s.getSkill().getLabel() + " level " + s.getLevel();
		}
		if (r instanceof Requirement.CombatLevel)
		{
			return "combat level " + RequirementDiagnostics.level(((Requirement.CombatLevel) r));
		}
		return r.toText();
	}

	private static String houseWords(Requirement.House h)
	{
		switch (h.getKind())
		{
			case AT:
				return "house at " + h.getWhat();
			case HAS:
				return "house has " + h.getWhat();
			default:
				return "owns a house";
		}
	}

	private static String opWords(Requirement.Var.Op op)
	{
		switch (op)
		{
			case EQ:
				return "is";
			case NE:
				return "is not";
			case GT:
				return "is above";
			case GE:
				return "is at least";
			case LT:
				return "is below";
			case LE:
				return "is at most";
			default:
				return "has bits";
		}
	}

	/** "Sapphire lantern (4702)", or "Ring of dueling(8) (2552) or ..." for several ids. */
	private static String itemNames(int[] ids)
	{
		List<String> each = new ArrayList<>();
		for (int id : ids)
		{
			String n = CacheNames.item(id);
			each.add(n == null ? "item " + id : n + " (" + id + ")");
		}
		return String.join(" or ", each);
	}
}

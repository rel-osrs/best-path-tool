package com.bestpath.route;

import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer details: a requirement taken apart, one line per part with whether the player the route was searched for
 * meets it, so a card shows exactly which part holds a transport back. "[ok]" met, "[bank]" met with the bank's items,
 * "[no]" not met, "[?]" no player; a part that rests on a question not answered yet says so. Pure.
 */
public final class RequirementCheck
{
	private RequirementCheck()
	{
	}

	/** The lines for a requirement (empty for none): "[ok] Magic 56", "[no] one of:", "  [no] item 567 used up", ... */
	public static List<String> lines(Requirement r, PlayerState p)
	{
		List<String> out = new ArrayList<>();
		if (r != null && !r.isNone())
		{
			add(r, p, 0, out);
		}
		return out;
	}

	private static void add(Requirement r, PlayerState p, int depth, List<String> out)
	{
		String pad = "  ".repeat(depth);
		if (r instanceof Requirement.All)
		{
			List<Requirement> parts = ((Requirement.All) r).getParts();
			if (depth > 0 && parts.size() > 1)
			{
				// inside a "one of": the parts of one choice together
				out.add(pad + mark(r, p) + " all of:");
				depth++;
			}
			for (Requirement part : parts)
			{
				add(part, p, depth, out);
			}
			return;
		}
		if (r instanceof Requirement.AnyOf)
		{
			out.add(pad + mark(r, p) + " one of:");
			for (Requirement alt : ((Requirement.AnyOf) r).getAlternatives())
			{
				add(alt, p, depth + 1, out);
			}
			return;
		}
		boolean open = p != null && !r.check(p).getUnanswered().isEmpty();
		out.add(pad + mark(r, p) + " " + r.toText() + value(r, p) + (open ? " (not answered yet)" : ""));
	}

	/** "[ok]", "[bank]", "[no]" or "[?]" (no player). */
	static String mark(Requirement r, PlayerState p)
	{
		if (p == null)
		{
			return "[?]";
		}
		String m;
		switch (r.check(p).getOutcome())
		{
			case MET:
				m = "[ok]";
				break;
			case NEEDS_BANK:
				m = "[bank]";
				break;
			case NOT_MET:
			default:
				m = "[no]";
				break;
		}
		return m;
	}

	/**
	 * A varbit / varp part's value now: " (is 12)"; "" for any other part or no player. The var numbers are shown only
	 * here; the "no route" card says it in words ({@link VarNotes}).
	 */
	static String value(Requirement r, PlayerState p)
	{
		Requirement v = r instanceof Requirement.Not ? ((Requirement.Not) r).getPart() : r;
		if (p == null || !(v instanceof Requirement.Var))
		{
			return "";
		}
		Requirement.Var var = (Requirement.Var) v;
		return " (is " + (var.isVarp() ? p.varp(var.getId()) : p.varbit(var.getId())) + ")";
	}
}

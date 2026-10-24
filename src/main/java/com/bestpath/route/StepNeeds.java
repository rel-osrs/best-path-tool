package com.bestpath.route;

import com.bestpath.core.data.Tables;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.route.StepInfo.Need;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * The things a step's requirement asks for, as the small icons on its card ({@link StepInfo.Need}): skill levels,
 * quests, items, runes (or the staff that gives them), coins and player answers, each with whether the player has it
 * ({@link NeedStatus}). Of a "one of (...)" only the choice that applies to the player is shown.
 */
final class StepNeeds
{
	/** The stored canoe axe's picture when the data has no default axe. */
	static final int BRONZE_AXE = ItemID.BRONZE_AXE;

	private StepNeeds()
	{
	}

	/** Whether the player has one requirement (a leaf): its own check; unknown without a player. */
	static NeedStatus status(Requirement r, PlayerState player)
	{
		return player == null ? NeedStatus.UNKNOWN : NeedStatus.of(r.check(player));
	}

	/**
	 * An answer need's mark: answered as needed, a check; answered the other way, an X; not answered yet (resting on the
	 * answer), no mark: the question is under the card.
	 */
	static NeedStatus answerStatus(Requirement r, PlayerState player)
	{
		if (player == null)
		{
			return NeedStatus.UNKNOWN;
		}
		Check c = r.check(player);
		return c.getUnanswered().isEmpty() ? NeedStatus.of(c) : NeedStatus.UNKNOWN;
	}

	/**
	 * The stored canoe axe's mark: stored (or everything unlocked), a check; seen taken back, an X; not seen yet, a
	 * question mark.
	 */
	static NeedStatus canoeAxeStatus(PlayerState player)
	{
		if (player == null)
		{
			return NeedStatus.UNKNOWN;
		}
		Boolean stored = player.getCanoeAxeStored();
		if (player.isAllUnlocked() || Boolean.TRUE.equals(stored))
		{
			return NeedStatus.HAVE;
		}
		return stored == null ? NeedStatus.MAYBE : NeedStatus.MISSING;
	}

	/**
	 * The things a requirement asks for, in its order, each once: skill levels, quests, items, runes and coins.
	 * Of a "one of (...)" only the choice that applies to the player is shown ({@link #choice}); its mouse-over
	 * lists every choice. The step's own item ({@code mainItem}, already its big icon) is left out.
	 */
	static List<Need> needs(Requirement r, int mainItem, PlayerState player, Tables tables)
	{
		Set<Need> out = new LinkedHashSet<>();
		if (r != null)
		{
			collect(r, out, mainItem, player, tables);
		}
		return new ArrayList<>(out);
	}

	private static void collect(Requirement r, Set<Need> out, int mainItem, PlayerState player, Tables tables)
	{
		if (r instanceof Requirement.All)
		{
			for (Requirement p : ((Requirement.All) r).getParts())
			{
				collect(p, out, mainItem, player, tables);
			}
		}
		else if (r instanceof Requirement.AnyOf)
		{
			Requirement.AnyOf any = (Requirement.AnyOf) r;
			int i = choice(any, player);
			if (i < 0)
			{
				return; // none met: the route would not use this
			}
			Set<Need> picked = new LinkedHashSet<>();
			collect(any.getAlternatives().get(i), picked, mainItem, player, tables);
			String all = choices(any, tables);
			for (Need n : picked)
			{
				out.add(n.withTooltip(n.getTooltip() + " (one of: " + all + ")"));
			}
		}
		else if (r instanceof Requirement.SkillLevel)
		{
			Requirement.SkillLevel s = (Requirement.SkillLevel) r;
			addUnlessLasting(out, Need.skill(s.getSkill(), s.getLevel()).withStatus(status(r, player)), true);
		}
		else if (r instanceof Requirement.Quest)
		{
			Requirement.Quest q = (Requirement.Quest) r;
			addUnlessLasting(out, Need.quest(q.getQuest(), q.getWanted()).withStatus(status(r, player)), true);
		}
		else if (r instanceof Requirement.Item)
		{
			Requirement.Item i = (Requirement.Item) r;
			int id = StepInfo.shownItem(i, player, tables);
			if (id != mainItem)
			{
				out.add(Need.item(i.getLabel(), id, i.getCount(), i.isWorn(), i.isUsedUp()).withStatus(status(r, player)));
			}
		}
		else if (r instanceof Requirement.Runes)
		{
			// a worn staff, combination runes and the rune pouch count (the requirement's own check); when a staff is
			// what gives them, the staff is shown, once for all the runes it gives
			Requirement.Runes u = (Requirement.Runes) r;
			int staff = staffUsed(u, player);
			if (staff >= 0)
			{
				addStaff(out, Need.staff(staff, u.getRune(), player.wears(staff)).withStatus(status(r, player)));
			}
			else
			{
				out.add(Need.rune(u.getRune(), u.getRuneIds()[0], u.getCount()).withStatus(status(r, player)));
			}
		}
		else if (r instanceof Requirement.Coins)
		{
			out.add(Need.coins(((Requirement.Coins) r).getAmount()).withStatus(status(r, player)));
		}
		else if (r instanceof Requirement.Answer)
		{
			String a = ((Requirement.Answer) r).getAnswer();
			addUnlessLasting(out, Need.answer(a, false).withStatus(answerStatus(r, player)), !AnswerQuestions.canChange(a));
		}
		else if (r instanceof Requirement.CanoeAxeStored)
		{
			// detected, never asked; not known yet = a question mark (like an item maybe in a STASH)
			int axe = tables == null ? -1 : tables.itemGroupDefault("axe");
			out.add(Need.storedCanoeAxe(axe >= 0 ? axe : BRONZE_AXE).withStatus(canoeAxeStatus(player)));
		}
		else if (r instanceof Requirement.Not && ((Requirement.Not) r).getPart() instanceof Requirement.Answer)
		{
			String a = ((Requirement.Answer) ((Requirement.Not) r).getPart()).getAnswer();
			addUnlessLasting(out, Need.answer(a, true).withStatus(answerStatus(r, player)), !AnswerQuestions.canChange(a));
		}
		// members, spellbook, varbits, empty slots, house: no icon
	}

	/**
	 * The staff that gives these runes for this player, as the requirement's own check uses it, or -1 (the runes
	 * themselves): a worn staff; else, without enough runes carried, a carried staff (wield it); else, when the bank
	 * has too few runes too, a staff in the bank (what the bank step takes out).
	 */
	static int staffUsed(Requirement.Runes u, PlayerState player)
	{
		if (player == null || player.isAllItems())
		{
			return -1;
		}
		int[] staves = u.getStaffIds();
		for (int id : staves)
		{
			if (player.wears(id))
			{
				return id;
			}
		}
		int carried = 0;
		int banked = 0;
		for (int id : u.getRuneIds())
		{
			carried += player.carried(id);
			banked += player.banked(id);
		}
		if (carried >= u.getCount())
		{
			return -1;
		}
		for (int id : staves)
		{
			if (player.carried(id) > 0)
			{
				return id;
			}
		}
		if (carried + banked >= u.getCount())
		{
			return -1;
		}
		for (int id : staves)
		{
			if (player.banked(id) > 0)
			{
				return id;
			}
		}
		return -1;
	}

	/** Adds a staff need, or gives the same staff (with the same mark) already shown these runes too. */
	private static void addStaff(Set<Need> out, Need staff)
	{
		List<Need> all = new ArrayList<>(out);
		for (int i = 0; i < all.size(); i++)
		{
			Need n = all.get(i);
			if (!n.getStaffRunes().isEmpty() && n.getItemId() == staff.getItemId() && n.getStatus() == staff.getStatus())
			{
				all.set(i, n.withStaffRunes(staff.getStaffRunes()));
				out.clear();
				out.addAll(all);
				return;
			}
		}
		out.add(staff);
	}

	/**
	 * Adds the need, except a met one that stays met for the character: skill levels, quests and one-time unlocks
	 * answered by the player are left out once met, so cards keep to what can change (items). Not met, in the bank or
	 * not known (no player): shown.
	 */
	private static void addUnlessLasting(Set<Need> out, Need n, boolean lasting)
	{
		if (!lasting || n.getStatus() != NeedStatus.HAVE)
		{
			out.add(n);
		}
	}

	/**
	 * The "one of (...)" choice to show: the one the router used for this player ({@link Requirement.AnyOf#chosen}:
	 * the first met, preferring met now over met with the bank); -1 if none is met; the first without a player.
	 */
	static int choice(Requirement.AnyOf any, PlayerState player)
	{
		return player == null ? 0 : any.chosen(player);
	}

	/** Every choice of a "one of (...)" in words, for the mouse-over: "10 coins or Quest: Prince Ali Rescue". */
	static String choices(Requirement.AnyOf any, Tables tables)
	{
		List<String> words = new ArrayList<>();
		for (Requirement alt : any.getAlternatives())
		{
			words.add(words(alt, tables));
		}
		return String.join(" or ", words);
	}

	private static String words(Requirement r, Tables tables)
	{
		if (r instanceof Requirement.All)
		{
			List<String> parts = new ArrayList<>();
			for (Requirement p : ((Requirement.All) r).getParts())
			{
				parts.add(words(p, tables));
			}
			return String.join(" and ", parts);
		}
		if (r instanceof Requirement.AnyOf)
		{
			return "(" + choices((Requirement.AnyOf) r, tables) + ")";
		}
		Set<Need> one = new LinkedHashSet<>();
		collect(r, one, -1, null, tables);
		String t = one.isEmpty() ? "" : one.iterator().next().getTooltip();
		return t.isEmpty() || t.startsWith("An item") ? r.toText() : t;
	}
}

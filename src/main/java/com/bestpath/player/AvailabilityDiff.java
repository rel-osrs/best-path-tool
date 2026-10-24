package com.bestpath.player;

import com.bestpath.core.path.Sailing;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.Boat;
import com.bestpath.route.PlayerSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compares two player states on what can change which transports are usable
 * (the availability inputs), to decide whether a route must be searched again.
 * Always compared: members, real levels, spellbook, house, player answers,
 * collection log items and grown spirit trees seen,
 * whether the rune pouch is carried and which home / minigame teleports are
 * cooling down. Compared only when some requirement
 * mentions them ({@link RequirementInputs}): varbits, varps, quests and items
 * (carried, worn, banked, in the rune pouch). Eating a shrimp or a varbit no
 * transport looks at is not a change. Pure.
 */
public final class AvailabilityDiff
{
	/** At most this many changes are listed (the list is for the debug log and tests). */
	private static final int MAX_LISTED = 10;

	private AvailabilityDiff()
	{
	}

	/**
	 * What changed, in words ("Agility 50 -> 51", "item 8007 carried 0 -> 1", "Home teleport ready again"); empty =
	 * nothing that matters.
	 */
	public static List<String> changes(PlayerState before, PlayerState after, RequirementInputs in)
	{
		List<String> out = changesBesideCooldowns(before, after, in);
		if (before != null && after != null)
		{
			for (Cooldown c : Cooldown.values())
			{
				if (before.cooldownReady(c) != after.cooldownReady(c))
				{
					out.add(0, c.getLabel() + (after.cooldownReady(c) ? " ready again" : " cooling down"));
				}
			}
		}
		return out.size() > MAX_LISTED ? new ArrayList<>(out.subList(0, MAX_LISTED)) : out;
	}

	private static List<String> changesBesideCooldowns(PlayerState before, PlayerState after, RequirementInputs in)
	{
		List<String> out = new ArrayList<>();
		if (before == null || after == null)
		{
			out.add("no earlier state");
			return out;
		}
		if (before.isMembers() != after.isMembers())
		{
			out.add("members " + before.isMembers() + " -> " + after.isMembers());
		}
		for (Skill s : Skill.values())
		{
			if (before.level(s) != after.level(s))
			{
				out.add(s.getLabel() + " " + before.level(s) + " -> " + after.level(s));
			}
		}
		if (before.getSpellbook() != after.getSpellbook())
		{
			out.add("spellbook " + before.getSpellbook().getLabel() + " -> " + after.getSpellbook().getLabel());
		}
		if (!Objects.equals(before.getHouse(), after.getHouse()) || !before.getHouseThings().equals(after.getHouseThings()))
		{
			out.add("house " + before.getHouse() + " -> " + after.getHouse());
		}
		if (!before.getAnswerMap().equals(after.getAnswerMap()))
		{
			out.add("answers changed");
		}
		if (!before.getCollectionLog().equals(after.getCollectionLog()))
		{
			out.add("collection log " + before.getCollectionLog() + " -> " + after.getCollectionLog());
		}
		if (!before.getSpiritTrees().equals(after.getSpiritTrees()))
		{
			out.add("spirit trees " + before.getSpiritTrees() + " -> " + after.getSpiritTrees());
		}
		if (!Objects.equals(before.getCanoeAxeStored(), after.getCanoeAxeStored()))
		{
			out.add("canoe axe stored " + before.getCanoeAxeStored() + " -> " + after.getCanoeAxeStored());
		}
		if (before.isPouchInBank() != after.isPouchInBank())
		{
			out.add(after.isPouchInBank() ? "rune pouch put away" : "rune pouch taken");
		}
		for (int id : in.getVarbits())
		{
			if (before.varbit(id) != after.varbit(id))
			{
				out.add("varbit " + id + " " + before.varbit(id) + " -> " + after.varbit(id));
			}
		}
		for (int id : in.getVarps())
		{
			if (before.varp(id) != after.varp(id))
			{
				out.add("varp " + id + " " + before.varp(id) + " -> " + after.varp(id));
			}
		}
		for (String q : in.getQuests())
		{
			if (before.quest(q) != after.quest(q))
			{
				out.add("quest " + q + " " + before.quest(q) + " -> " + after.quest(q));
			}
		}
		for (int id : in.getItems())
		{
			if (before.carried(id) != after.carried(id))
			{
				out.add("item " + id + " carried " + before.carried(id) + " -> " + after.carried(id));
			}
			if (before.banked(id) != after.banked(id))
			{
				out.add("item " + id + " banked " + before.banked(id) + " -> " + after.banked(id));
			}
			if (wornSlots(before, id) != wornSlots(after, id))
			{
				out.add("item " + id + " worn " + wornSlots(before, id) + " -> " + wornSlots(after, id));
			}
		}
		return out;
	}

	/**
	 * True if the route must be searched again: something in {@link #changes} changed and, when the inputs carry
	 * their requirements, at least one requirement now has another outcome (met / needs the bank / not met), other
	 * to-dos or other questions not answered yet. So spending 1 of 500 coins when the tolls ask for 10 is no reason;
	 * dropping below 10 is. A members change always counts (it changes where the player can walk). A home or minigame
	 * teleport turning ready always counts (it may make a quicker route); one starting to cool down (the player just
	 * used it) only when the rest of the route still needs it ({@code usedAhead}, see RouteFollower). So casting the
	 * route's own home teleport does not search again while the player is still casting it.
	 */
	public static boolean needsReroute(PlayerState before, PlayerState after, RequirementInputs in, Set<Cooldown> usedAhead)
	{
		if (before == null || after == null)
		{
			return true;
		}
		for (Cooldown c : Cooldown.values())
		{
			boolean was = before.cooldownReady(c);
			boolean is = after.cooldownReady(c);
			if (!was && is || was && !is && usedAhead.contains(c))
			{
				return true;
			}
		}
		if (changesBesideCooldowns(before, after, in).isEmpty())
		{
			return false;
		}
		if (before.isMembers() != after.isMembers() || in.getRequirements().isEmpty())
		{
			return true;
		}
		for (Requirement r : in.getRequirements())
		{
			Check a = r.check(before);
			Check b = r.check(after);
			// an answer given to a question the outcome rested on counts too: its card stops asking
			if (a.getOutcome() != b.getOutcome() || !a.getTodo().equals(b.getTodo()) || !a.getUnanswered().equals(b.getUnanswered()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * True if the route must be searched again for these two snapshots: the boats changed in something routes use
	 * ({@link #boatChanges}), the bottled boats became usable or not ({@link #bottlesChanged}), or the player state
	 * did ({@link #needsReroute(PlayerState, PlayerState, RequirementInputs, Set)}, with the cooldowns the rest of the
	 * route still uses).
	 */
	public static boolean needsReroute(PlayerSnapshot before, PlayerSnapshot after, RequirementInputs in,
		Set<Cooldown> usedAhead)
	{
		if (before == null || after == null)
		{
			return true;
		}
		return !boatChanges(before.getBoats(), after.getBoats()).isEmpty() || bottlesChanged(before, after)
			|| needsReroute(before.getState(), after.getState(), in, usedAhead);
	}

	/**
	 * True if the bottled boats can be used in one snapshot and not in the other (only with a full boat bottle per
	 * bottled boat in the inventory, {@link Sailing#bottlesInHand}). Picking up a second bottle when two boats are
	 * bottled counts; a bottle without a bottled boat doesn't.
	 */
	public static boolean bottlesChanged(PlayerSnapshot before, PlayerSnapshot after)
	{
		return Sailing.bottlesInHand(before.getState(), Sailing.bottled(before.getBoats()))
			!= Sailing.bottlesInHand(after.getState(), Sailing.bottled(after.getBoats()));
	}

	/**
	 * What changed in the boats that routes use, in words ("boat 1 moved from dock 0 to dock 3"); empty = nothing.
	 * Compared per slot: the kind, the speed, the waters it may enter, the teleport focus and where it is. A new sail
	 * of the same kind of resistance, a name or a cannon changes nothing.
	 */
	public static List<String> boatChanges(List<Boat> before, List<Boat> after)
	{
		List<String> out = new ArrayList<>();
		Map<Integer, Boat> a = bySlot(before);
		Map<Integer, Boat> b = bySlot(after);
		for (int slot : new TreeSet<>(union(a.keySet(), b.keySet())))
		{
			Boat x = a.get(slot);
			Boat y = b.get(slot);
			if (x == null || y == null)
			{
				out.add("boat " + slot + (x == null ? " added" : " gone"));
			}
			else if (x.getType() != y.getType() || x.speed() != y.speed() || x.areaMask() != y.areaMask()
				|| x.getFocus() != y.getFocus())
			{
				out.add("boat " + slot + " changed: " + y.describe());
			}
			else if (x.getDockId() != y.getDockId())
			{
				out.add("boat " + slot + " moved from " + place(x) + " to " + place(y));
			}
		}
		return out;
	}

	/** "dock 3", or "capsized" / "lost" / "bottled". */
	private static String place(Boat b)
	{
		return b.placeWord() != null ? b.placeWord() : "dock " + b.getDockId();
	}

	private static Map<Integer, Boat> bySlot(List<Boat> boats)
	{
		Map<Integer, Boat> m = new TreeMap<>();
		for (Boat b : boats == null ? List.<Boat>of() : boats)
		{
			m.put(b.getSlot(), b);
		}
		return m;
	}

	private static Set<Integer> union(Set<Integer> a, Set<Integer> b)
	{
		Set<Integer> s = new TreeSet<>(a);
		s.addAll(b);
		return s;
	}

	/** Which slots hold this item, as bits (wearing it in another slot can matter for "worn" checks). */
	private static int wornSlots(PlayerState p, int id)
	{
		int bits = 0;
		for (EquipSlot s : EquipSlot.values())
		{
			if (p.worn(s) == id)
			{
				bits |= 1 << s.ordinal();
			}
		}
		return bits;
	}
}

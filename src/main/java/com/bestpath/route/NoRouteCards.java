package com.bestpath.route;

import com.bestpath.core.data.NoRouteReasons;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.sea.BoatType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Core's "no route" diagnosis ({@link NoRouteReasons}) in plain words, with a card per thing the player can't use
 * yet ({@link NoRouteWhy}). Worker thread only.
 */
final class NoRouteCards
{
	private final PluginRouter router;
	private final TransportRules rules;

	NoRouteCards(PluginRouter router, TransportRules rules)
	{
		this.router = router;
		this.rules = rules;
	}

	/** Core's diagnosis in plain words, with a card per thing the player can't use yet. */
	NoRouteWhy why(NoRouteReasons n, RouteJob job, long nowMinute)
	{
		int places = job.getRequest().getTargets().length;
		boolean one = places == 1;
		String there = one ? "there" : "to the nearest place";
		List<String> lines = new ArrayList<>();
		List<NoRouteWhy.Blocker> cards = new ArrayList<>();
		List<List<NoRouteWhy.Blocker>> ways = new ArrayList<>();
		String head;
		switch (n.getKind())
		{
			case MEMBERS_LAND:
				head = one ? "This is members-only land, and you're on a free-to-play world."
					: "All " + places + " places are on members-only land, and you're on a free-to-play world.";
				break;
			case NOT_STANDABLE:
				head = n.isAtSea() ? (one ? "That's open sea: only a boat can be there." : "Those places are open sea: only a boat can be there.")
					: one ? "Nobody can stand there (water, a wall...)." : "Nobody can stand at any of the " + places + " places (water, a wall...).";
				lines.addAll(otherLines(n));
				break;
			case BLOCKED:
				// every way needing as few things, "or" between them
				for (NoRouteReasons.WayStep s : n.getBlocked())
				{
					cards.add(blocker(s, job, nowMinute));
				}
				ways.add(cards);
				Set<String> shown = new HashSet<>();
				for (NoRouteReasons.WayStep s : n.getBlocked())
				{
					shown.add(s.getCardKey());
				}
				for (List<NoRouteReasons.WayStep> other : n.getOtherWays())
				{
					List<NoRouteWhy.Blocker> c = new ArrayList<>();
					boolean repeats = false;
					for (NoRouteReasons.WayStep s : other)
					{
						if (s.isBlocked())
						{
							repeats |= shown.contains(s.getCardKey());
							c.add(blocker(s, job, nowMinute));
						}
					}
					// a way blocked by a transport an earlier way shows ("Fairy ring -> DLR" reached from elsewhere) is
					// left out: each transport shown once (core's searches already leave such ways out; this is a safety net)
					if (!repeats)
					{
						ways.add(c);
						for (NoRouteReasons.WayStep s : other)
						{
							if (s.isBlocked())
							{
								shown.add(s.getCardKey());
							}
						}
					}
				}
				head = ways.size() == 1 ? "The way " + there + " needs " + things(n.getThings()) + " you can't use yet:"
					: "There are " + ways.size() + " ways " + there + ", each needing " + things(n.getThings()) + " you can't use yet:";
				break;
			case BY_BOAT:
				head = (n.isAtSea() ? "That's open sea, so the way " + there + " is by boat. It needs "
					: "The way " + there + " is by boat, and needs ") + things(n.getThings()) + " you can't use yet:";
				for (NoRouteReasons.SailStep s : n.getSailSteps())
				{
					cards.add(sailBlocker(s, job.getPlayer().getState()));
				}
				break;
			case REACHABLE:
				head = "A way " + there + " exists with what you can use now. Try searching again.";
				break;
			case NO_KNOWN_WAY:
			default:
				head = "There's no known way " + (one ? "there" : "to any of these places") + (n.isFreeWorld() ? " on free-to-play land" : "")
					+ " in our map data.";
				lines.addAll(otherLines(n));
				break;
		}
		if (ways.isEmpty())
		{
			ways.add(cards);
		}
		return NoRouteWhy.ofWays(n.getKind(), head, lines, ways);
	}

	/** "1 thing", "3 things". */
	static String things(int n)
	{
		return n == 1 ? "1 thing" : n + " things";
	}

	/** Core's short lines after its main one (places on members land, at sea, how many places looked at), no coordinates. */
	private static List<String> otherLines(NoRouteReasons n)
	{
		List<String> out = new ArrayList<>();
		List<String> r = n.getReasons();
		for (int i = 1; i < r.size(); i++)
		{
			if (!r.get(i).startsWith("It is out at sea"))
			{
				out.add(r.get(i));
			}
		}
		return out;
	}

	/** One transport of the way the player can't use now, as its card shows it. */
	private NoRouteWhy.Blocker blocker(NoRouteReasons.WayStep s, RouteJob job, long nowMinute)
	{
		Transport t = s.getTransport();
		TransportLine line = s.getLine();
		PlayerState player = job.getPlayer().getState();
		Tables tables = router.getData().getTables();
		// "one of (...)" none of whose choices is met: the choice nearest to being met, as core reports it
		StepInfo info = line == null ? StepInfo.NONE : StepInfo.of(line, s.isReverse(), nearestChoices(s.getRequirement(), player),
			rules.category(t, line), player, tables).withoutCooldown();
		boolean teleport = !t.hasStart();
		RouteStep step = new RouteStep(teleport ? RouteStep.Kind.TELEPORT : StepTitles.kind(line),
			StepTitles.transportTitle(t, StepTitles.menuOf(s), teleport), "", s.getTicks(), List.of(), t.getId(), new int[0], -1, -1)
			.withInfo(info);
		// what it needs that has no small picture on the card (a members world, a spellbook, quest progress...); a
		// varbit / varp as a player's note, its numbers only in the developer details
		Set<String> notes = new LinkedHashSet<>();
		for (Requirement r : s.getUnmet())
		{
			if (!hasPicture(r))
			{
				String note = VarNotes.of(r);
				notes.add(r instanceof Requirement.Members ? "A members world" : note != null ? note
					: capital(String.join("; ", r.check(player).getMissing())));
			}
		}
		List<String> missing = new ArrayList<>(notes);
		List<String> reasons = new ArrayList<>();
		Set<NoRouteReasons.Why> why = s.getWhy();
		if (why.contains(NoRouteReasons.Why.REQUIREMENTS) && missing.isEmpty() && !anyMissing(info))
		{
			reasons.add("You don't meet what it needs yet.");
		}
		if (why.contains(NoRouteReasons.Why.BANK))
		{
			reasons.add("Needs things from your bank");
		}
		TransportSettings.Category turnOn = null;
		if (why.contains(NoRouteReasons.Why.FILTER))
		{
			TransportSettings.Category c = rules.category(t, line);
			reasons.add("Turned off in Settings" + (c == null ? "." : ": " + c.getLabel() + "."));
			// the type's tick box on the card when its whole category is switched off
			turnOn = c != null && !job.getSettings().isEnabled(c) ? c : null;
		}
		String id = t.getId();
		String base = id.endsWith(":back") ? id.substring(0, id.length() - 5) : id;
		TransportSettings settings = job.getSettings();
		String restore = settings.isBlacklisted(id) ? id : settings.isBlacklisted(base) ? base : null;
		Set<String> skips = job.getSessionExclusions();
		// by id (Skip on a route card), or by card (Skip on a "no route" card: every ride to that fairy ring)
		boolean skipped = skips.contains(id) || skips.contains(base) || line != null && skips.contains(s.getCardKey());
		if (why.contains(NoRouteReasons.Why.EXCLUDED) && restore != null)
		{
			reasons.add("You chose never to use this.");
		}
		if (skipped)
		{
			reasons.add("You skipped this for this route (Undo above allows it again).");
		}
		if (why.contains(NoRouteReasons.Why.COOLDOWN))
		{
			long ready = s.getReadyAtMinute();
			reasons.add(ready <= 0 ? "Cooling down: you used it a short while ago."
				: "Cooling down: ready again in about " + Math.max(1, ready - nowMinute) + " min.");
		}
		NoRouteWhy.Blocker b = new NoRouteWhy.Blocker(step, missing, reasons, why.contains(NoRouteReasons.Why.EXCLUDED) ? restore : null,
			skipped, turnOn);
		// Skip on the card skips what the card stands for (fairy ring DLR from every ring)
		return line == null ? b : b.withSkipId(s.getCardKey());
	}

	/** One part of a way by boat the player can't use now (the boat, or a dock), as its card shows it. */
	private static NoRouteWhy.Blocker sailBlocker(NoRouteReasons.SailStep s, PlayerState player)
	{
		// no boat: the raft's picture stands for "a boat"
		BoatType type = s.getBoat() == null ? BoatType.RAFT : s.getBoat().getType();
		String title;
		switch (s.getWhat())
		{
			case BOARD:
				title = "Board at " + s.getDock();
				break;
			case LEAVE:
				title = "Leave the boat at " + s.getDock();
				break;
			case BOAT:
			default:
				title = s.getBoat() == null ? "A boat" : s.getBoat().label();
				break;
		}
		StepInfo info = StepInfo.sailingNeeds(type, s.getRequirement(), player);
		RouteStep step = new RouteStep(RouteStep.Kind.BOAT, title, "", 0, List.of(), null, new int[0], -1, -1).withInfo(info);
		List<String> reasons = new ArrayList<>();
		// a boat or the shipwrights switched off in Settings get their tick box on the card
		List<NoRouteWhy.Toggle> toggles = new ArrayList<>();
		Set<NoRouteReasons.SailStep.SailWhy> why = s.getWhy();
		if (why.contains(NoRouteReasons.SailStep.SailWhy.NO_BOAT))
		{
			reasons.add("You have no boat.");
		}
		if (why.contains(NoRouteReasons.SailStep.SailWhy.BOAT_OFF))
		{
			reasons.add("Turned off in Settings (your boats).");
			if (s.getBoat() != null)
			{
				toggles.add(NoRouteWhy.Toggle.boat(s.getBoat().getSlot(), s.getBoat().label(), s.getBoat().getType()));
			}
		}
		if (why.contains(NoRouteReasons.SailStep.SailWhy.SHIPWRIGHTS_OFF))
		{
			reasons.add("A shipwright must bring it, and shipwrights are turned off in Settings.");
			toggles.add(NoRouteWhy.Toggle.shipwrights());
		}
		if (why.contains(NoRouteReasons.SailStep.SailWhy.BOTTLE))
		{
			reasons.add("It is in a boat bottle: carry the full bottle in your inventory.");
		}
		if (why.contains(NoRouteReasons.SailStep.SailWhy.PARTS))
		{
			// e.g. the Ynysdail water is Crystalline waters (an adamant keel)
			List<String> areas = new ArrayList<>();
			for (String t : s.getUnmetTexts())
			{
				if (!t.toLowerCase(Locale.ROOT).contains("bottle"))
				{
					areas.add(t);
				}
			}
			reasons.add("Its parts can't sail into " + String.join(", ", areas) + ".");
		}
		return new NoRouteWhy.Blocker(step, List.of(), reasons, null, false, toggles);
	}

	/** The requirement with each "one of (...)" none of whose choices is met replaced by the choice nearest to being met. */
	static Requirement nearestChoices(Requirement r, PlayerState player)
	{
		if (r == null || player == null)
		{
			return r;
		}
		if (r instanceof Requirement.All)
		{
			List<Requirement> parts = new ArrayList<>();
			for (Requirement p : ((Requirement.All) r).getParts())
			{
				parts.add(nearestChoices(p, player));
			}
			return parts.isEmpty() ? r : Requirement.all(parts);
		}
		if (r instanceof Requirement.AnyOf)
		{
			Requirement.AnyOf any = (Requirement.AnyOf) r;
			return any.chosen(player) >= 0 ? r : nearestChoices(any.getAlternatives().get(NoRouteReasons.closestChoice(any, player)), player);
		}
		return r;
	}

	/** True for the needs a card shows as a small picture with its mark (skills, quests, items, runes, coins). */
	private static boolean hasPicture(Requirement r)
	{
		return r instanceof Requirement.SkillLevel || r instanceof Requirement.Quest || r instanceof Requirement.Item
			|| r instanceof Requirement.Runes || r instanceof Requirement.Coins;
	}

	/** True if the card shows an X somewhere (a need or its own item). */
	private static boolean anyMissing(StepInfo info)
	{
		if (info.getItemStatus() == NeedStatus.MISSING)
		{
			return true;
		}
		for (StepInfo.Need n : info.getNeeds())
		{
			if (n.getStatus() == NeedStatus.MISSING)
			{
				return true;
			}
		}
		return false;
	}

	private static String capital(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}

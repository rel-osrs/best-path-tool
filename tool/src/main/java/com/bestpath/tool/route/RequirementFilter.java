package com.bestpath.tool.route;

import com.bestpath.core.req.BankItem;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateDiagnostics;
import com.bestpath.core.req.RequirementText;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.req.ItemGroupFile;
import com.bestpath.core.data.BankList;
import com.bestpath.core.path.BankFirst;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides which transports one test player can use, for a route search:
 * quest-only transports never; others when their requirement line is met.
 * A transport met only with items or coins from the bank is kept and marked
 * ({@link #itemsFromBank}): a route may begin with a bank visit, and the
 * search decides when that is quicker (core BankFirst, the same as the
 * plugin). Counts what it left out, for the status line.
 */
public final class RequirementFilter
{
	private final PlayerState player;
	private final int startWilderness;
	private final Map<String, Check> checked = new HashMap<>();
	/** Transports usable only with items from the bank: what to take out. */
	private final Map<com.bestpath.core.path.Transport, List<BankItem>> bank = new IdentityHashMap<>();
	private int tooDeep;
	private int coolingDown;
	private int leftOut;
	private int oneWayNow;
	private int questOnly;
	private int badLines;

	public RequirementFilter(PlayerState player)
	{
		this(player, 0);
	}

	/** {@code startWilderness}: the Wilderness level where the route starts (0 = outside); teleports deeper than their limit are left out. */
	public RequirementFilter(PlayerState player, int startWilderness)
	{
		this.player = player;
		this.startWilderness = startWilderness;
	}

	/** False (and counted) for a teleport that doesn't work at the route's start Wilderness level. */
	boolean teleportWorksHere(TransportRecord r)
	{
		if (r.kind != TransportRecord.Kind.TELEPORT || r.wildernessLimit == null || startWilderness <= r.wildernessLimit)
		{
			return true;
		}
		tooDeep++;
		return false;
	}

	/**
	 * False (and counted) for a long-cooldown teleport whose cooldown the test player is waiting out (core
	 * {@link Cooldown}; test players are ready unless "cooldowns used" is ticked). The plugin's PlayerTransports
	 * has the same rule.
	 */
	boolean cooldownReady(TransportRecord r)
	{
		Cooldown c = Cooldown.of(r.cooldown, r.teleportCategory);
		if (c == null || player.cooldownReady(c))
		{
			return true;
		}
		coolingDown++;
		return false;
	}

	/** The check of a requirement line for this player; a line that can't be read counts as met (and is counted). */
	public Check check(String line)
	{
		String key = line == null ? "" : line;
		Check c = checked.get(key);
		if (c == null)
		{
			try
			{
				c = ItemGroupFile.parse(key).check(player);
			}
			catch (RequirementText.ParseException e)
			{
				badLines++;
				c = Check.MET;
			}
			checked.put(key, c);
		}
		return c;
	}

	static boolean usable(Check c)
	{
		return c.getOutcome() != Check.Outcome.NOT_MET;
	}

	/**
	 * What a check needs from the bank ("coins 30", "axe", with each one's items and count), as the plugin's
	 * PlayerTransports lists it; empty if nothing.
	 */
	static List<BankItem> bankItems(Check c)
	{
		return c.getOutcome() != Check.Outcome.NEEDS_BANK ? List.of() : c.getBankItems();
	}

	void needsBank(com.bestpath.core.path.Transport t, List<BankItem> items)
	{
		bank.put(t, List.copyOf(items));
	}

	/** What must be taken from the bank to use this transport, in words ("coins 30"); empty if nothing. */
	public List<String> itemsFromBank(com.bestpath.core.path.Transport t)
	{
		return BankItem.labels(takeOutFromBank(t));
	}

	/** What must be taken from the bank to use this transport, with each one's items and count; empty if nothing. */
	public List<BankItem> takeOutFromBank(com.bestpath.core.path.Transport t)
	{
		return bank.getOrDefault(t, List.of());
	}

	/** {@code all} without the transports usable only with items from the bank (same order; the same list if none). */
	public List<com.bestpath.core.path.Transport> carried(List<com.bestpath.core.path.Transport> all)
	{
		if (bank.isEmpty())
		{
			return all;
		}
		List<com.bestpath.core.path.Transport> out = new ArrayList<>(all.size());
		for (com.bestpath.core.path.Transport t : all)
		{
			if (!bank.containsKey(t))
			{
				out.add(t);
			}
		}
		return out.size() == all.size() ? all : out;
	}

	/**
	 * The banks this player may start a route at: whose needs line is met with what the player has now (a line
	 * that can't be read counts as met), on free land only for a free world (as the plugin).
	 */
	public BankFirst.Banks banks(BankList list, com.bestpath.core.collision.FreeLand freeLand)
	{
		return list.usable(needs -> check(needs).isMet(), freeLand);
	}

	void leftOut(TransportRecord r)
	{
		if (r.questOnly)
		{
			questOnly++;
		}
		else
		{
			leftOut++;
		}
	}

	void oneWayNow()
	{
		oneWayNow++;
	}

	/** E.g. "for Fresh account: 812 transports left out (requirements), 3 quest only, 12 one way only". */
	public String summary()
	{
		StringBuilder sb = new StringBuilder("for ").append(PlayerStateDiagnostics.name(player)).append(": ");
		sb.append(leftOut).append(leftOut == 1 ? " transport" : " transports").append(" left out (requirements)");
		if (questOnly > 0)
		{
			sb.append(", ").append(questOnly).append(" quest only");
		}
		if (tooDeep > 0)
		{
			sb.append(", ").append(tooDeep).append(" teleport").append(tooDeep == 1 ? "" : "s").append(" too deep in the Wilderness");
		}
		if (coolingDown > 0)
		{
			sb.append(", ").append(coolingDown).append(" teleport").append(coolingDown == 1 ? "" : "s").append(" cooling down");
		}
		if (oneWayNow > 0)
		{
			sb.append(", ").append(oneWayNow).append(" usable one way only");
		}
		if (badLines > 0)
		{
			sb.append(", ").append(badLines).append(" unreadable requirement line").append(badLines == 1 ? "" : "s").append(" ignored");
		}
		return sb.toString();
	}
}

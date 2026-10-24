package com.bestpath.core.data;

import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.path.BankFirst;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.BankItem;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * The pathfinder's transports for one player, made from the exported
 * transport lines:
 * <ul>
 * <li>quest-only transports are never used;</li>
 * <li>a teleport deeper in the Wilderness than its limit is left out;</li>
 * <li>a long-cooldown teleport (home, minigame) is left out while the player's
 * cooldown for it runs ({@link Cooldown}, {@link PlayerState#cooldownReady});</li>
 * <li>a transport of a network also needs the network's requirement and
 * costs the network's extra ticks; a teleport's ticks are its teleport
 * category's ticks when it has a known category;</li>
 * <li>a transport is used when its requirement line is met; one met only
 * with items or coins from the bank is in {@link Result#getWithBank()} only,
 * for routes that begin with a bank visit
 * ({@link com.bestpath.core.path.BankFirst}); a two-way transport whose
 * two directions differ for this player becomes one transport per usable
 * direction;</li>
 * <li>network stops become a ride from every stop to every other stop of the
 * same network;</li>
 * <li>on a free world (not a member) only transports from free land to free
 * land are kept (a teleport: its landing).</li>
 * </ul>
 * The order is fixed (the lines' order, then the network rides), so equally
 * short routes always come out the same. Ids are the record ids ({@code ":back"}
 * for the way back of a split two-way transport, {@code "network:A>B"} for a
 * ride from stop A to stop B, with the stops' names).
 */
public final class PlayerTransports
{
	/**
	 * The transports for one player and where each came from: those usable with what the player carries or
	 * wears, and those usable once the bank's items count too (a route may begin with a bank visit).
	 */
	public static final class Result
	{
		private final List<Transport> carried;
		private final List<Transport> withBank;
		private final Map<Transport, TransportLine> lines;
		private final Map<Transport, List<BankItem>> bank;
		/** Transports the player can't use (requirements, quest only, Wilderness limit, members land). */
		final int leftOut;

		Result(List<Transport> withBank, Map<Transport, TransportLine> lines, Map<Transport, List<BankItem>> bank, int leftOut)
		{
			this.withBank = Collections.unmodifiableList(withBank);
			List<Transport> c = new ArrayList<>(withBank.size());
			for (Transport t : withBank)
			{
				if (!bank.containsKey(t))
				{
					c.add(t);
				}
			}
			this.carried = c.size() == withBank.size() ? this.withBank : Collections.unmodifiableList(c);
			this.lines = lines;
			this.bank = bank;
			this.leftOut = leftOut;
		}

		/** The transports usable with what the player carries or wears (the normal route's). */
		public List<Transport> getTransports()
		{
			return carried;
		}

		/**
		 * The transports usable after a bank visit (the bank's items count too), in the same order; the same list as
		 * {@link #getTransports()} when nothing in the bank makes a difference.
		 */
		public List<Transport> getWithBank()
		{
			return withBank;
		}

		/** True when the bank's items make more transports usable. */
		public boolean bankMatters()
		{
			return carried != withBank;
		}

		/** The exported line a transport was made from (for a ride: the stop it goes to), or null. */
		public TransportLine lineOf(Transport t)
		{
			return lines.get(t);
		}

		/** What must be taken from the bank to use it, with each one's items and count; empty if nothing. */
		public List<BankItem> takeOut(Transport t)
		{
			return bank.getOrDefault(t, List.of());
		}

		/**
		 * The same transports with the player's own tick changes ({@link Transport#withTickChange(int)}), by
		 * transport id (a split two-way transport's way back has its own id, {@code id + ":back"}); this result
		 * itself when nothing changes. Lines and bank items stay with the changed transports.
		 */
		synchronized Result withTickChanges(ToIntFunction<String> changes)
		{
			int[] each = new int[withBank.size()];
			for (int i = 0; i < each.length; i++)
			{
				each[i] = changes.applyAsInt(withBank.get(i).getId());
			}
			if (changed != null && Arrays.equals(each, changedBy))
			{
				return changed; // the same changes as last time: the same transport objects
			}
			List<Transport> out = null;
			for (int i = 0; i < withBank.size(); i++)
			{
				Transport t = withBank.get(i);
				Transport c = t.withTickChange(each[i]);
				if (c != t && out == null)
				{
					out = new ArrayList<>(withBank.subList(0, i));
				}
				if (out != null)
				{
					out.add(c);
				}
			}
			if (out == null)
			{
				return this;
			}
			Map<Transport, TransportLine> l = new IdentityHashMap<>();
			Map<Transport, List<BankItem>> b = new IdentityHashMap<>();
			for (int i = 0; i < out.size(); i++)
			{
				Transport was = withBank.get(i);
				Transport now = out.get(i);
				TransportLine line = lines.get(was);
				if (line != null)
				{
					l.put(now, line);
				}
				List<BankItem> items = bank.get(was);
				if (items != null)
				{
					b.put(now, items);
				}
			}
			changedBy = each;
			changed = new Result(out, l, b, leftOut);
			return changed;
		}

		/** The last {@link #withTickChanges} result and its change per transport (kept: the same changes, the same objects). */
		private Result changed;
		private int[] changedBy;
	}

	private final List<TransportLine> lines;
	private final Tables tables;
	/** Parsed requirement lines (shared by every player), by text. */
	final Map<String, Requirement> parsed = new HashMap<>();

	public PlayerTransports(List<TransportLine> lines, Tables tables)
	{
		this.lines = List.copyOf(lines);
		this.tables = tables;
	}

	/** One network stop: its tile, the ticks to ride to it, its line and bank items. */
	private static final class Stop
	{
		final String name;
		final TransportLine line;
		final int ticks;
		final List<BankItem> bank;

		Stop(String name, TransportLine line, int ticks, List<BankItem> bank)
		{
			this.name = name;
			this.line = line;
			this.ticks = ticks;
			this.bank = bank;
		}
	}

	/**
	 * @param player          what the player has
	 * @param startWilderness the Wilderness level where the route starts (0 = outside)
	 * @param freeLand        on a free world (player not a member): the free land; null = members
	 */
	public synchronized Result build(PlayerState player, int startWilderness, FreeLand freeLand)
	{
		// the same player state as last time (a route again after walking off it, a skip...) gets the same transport
		// objects, so the searches' indexes of them are reused and less short-lived memory is made
		if (lastResult != null && startWilderness == lastWilderness && freeLand == lastLand && player.sameAs(lastPlayer))
		{
			return lastResult;
		}
		Result r = make(player, startWilderness, freeLand);
		lastPlayer = player;
		lastWilderness = startWilderness;
		lastLand = freeLand;
		lastResult = r;
		return r;
	}

	/** The last {@link #build(PlayerState, int, FreeLand)}: what it was for and its result. */
	private PlayerState lastPlayer;
	private int lastWilderness;
	private FreeLand lastLand;
	private Result lastResult;

	private Result make(PlayerState player, int startWilderness, FreeLand freeLand)
	{
		List<Transport> out = new ArrayList<>();
		Map<Transport, TransportLine> from = new IdentityHashMap<>();
		Map<Transport, List<BankItem>> bank = new IdentityHashMap<>();
		Map<String, List<Stop>> networks = new LinkedHashMap<>();
		Map<String, Check> checked = new HashMap<>();
		int leftOut = 0;
		for (TransportLine r : lines)
		{
			if (r.isQuestOnly())
			{
				leftOut++;
				continue;
			}
			if (r.getKind() == TransportLine.Kind.TELEPORT && r.getWildernessLimit() != null
				&& startWilderness > r.getWildernessLimit())
			{
				leftOut++;
				continue;
			}
			if (coolingDown(r, player))
			{
				leftOut++;
				continue;
			}
			Check there = check(tables.networkRequirement(r.getNetwork(), r.getRequirements()), player, checked);
			Check back = r.getKind() == TransportLine.Kind.TWO_WAY
				? check(tables.networkRequirement(r.getNetwork(), r.requirementsEndToStart()), player, checked) : there;
			if (!usable(there) && !usable(back))
			{
				leftOut++;
				continue;
			}
			switch (r.getKind())
			{
				case NETWORK:
					networks.computeIfAbsent(r.getNetwork(), k -> new ArrayList<>())
						.add(new Stop(r.getName(), r, rideTicks(r), bankItems(there)));
					break;
				case TELEPORT:
				{
					Transport t = Transport.teleport(r.getId(), r.getName(), r.getEndX(), r.getEndY(), r.getEndPlane(),
						teleportTicks(r), true);
					add(out, from, bank, t, r, there);
					break;
				}
				default:
				{
					boolean twoWay = r.getKind() == TransportLine.Kind.TWO_WAY;
					boolean forward = usable(there);
					boolean backward = twoWay && usable(back);
					if (forward && (backward || !twoWay) && there.getOutcome() == back.getOutcome())
					{
						add(out, from, bank, Transport.between(r.getId(), r.getName(), r.getStartX(), r.getStartY(),
							r.getStartPlane(), r.getEndX(), r.getEndY(), r.getEndPlane(), rideTicks(r), twoWay, true).forBoats(r.getLargestBoat()), r, there);
						break;
					}
					// the two directions differ for this player: one transport per usable direction
					if (forward)
					{
						add(out, from, bank, Transport.between(r.getId(), r.getName(), r.getStartX(), r.getStartY(),
							r.getStartPlane(), r.getEndX(), r.getEndY(), r.getEndPlane(), rideTicks(r), false, true).forBoats(r.getLargestBoat()), r, there);
					}
					if (backward)
					{
						add(out, from, bank, Transport.between(r.getId() + ":back", r.getName(), r.getEndX(), r.getEndY(),
							r.getEndPlane(), r.getStartX(), r.getStartY(), r.getStartPlane(), rideTicks(r), false, true).forBoats(r.getLargestBoat()), r, back);
					}
					break;
				}
			}
		}
		// every stop of a network to every other stop; a ride costs the destination stop's ticks
		networks.forEach((net, stops) ->
		{
			for (Stop a : stops)
			{
				for (Stop b : stops)
				{
					TransportLine la = a.line;
					TransportLine lb = b.line;
					if (a != b && (la.getEndX() != lb.getEndX() || la.getEndY() != lb.getEndY() || la.getEndPlane() != lb.getEndPlane()))
					{
						// a stop on a solid tile (the middle of a fairy ring) is used from beside it
						Transport t = Transport.between(net + ":" + a.name + ">" + b.name, b.name, la.getEndX(), la.getEndY(),
							la.getEndPlane(), lb.getEndX(), lb.getEndY(), lb.getEndPlane(), b.ticks, false, true).usedFromBeside();
						out.add(t);
						from.put(t, lb);
						List<BankItem> both = new ArrayList<>(a.bank);
						for (BankItem s : b.bank)
						{
							if (!both.contains(s))
							{
								both.add(s);
							}
						}
						if (!both.isEmpty())
						{
							bank.put(t, both);
						}
					}
				}
			}
		});
		if (freeLand != null)
		{
			// a free world: only transports from free land to free land (a teleport: its landing)
			int before = out.size();
			out.removeIf(t -> !freeLand.isFree(t.getEndX(), t.getEndY()) || t.hasStart() && !freeLand.isFree(t.getStartX(), t.getStartY()));
			leftOut += before - out.size();
		}
		return new Result(out, from, bank, leftOut);
	}

	/**
	 * The player's own tick changes: one transport's (- / + on its step card) and a whole teleport category's or
	 * network's (the Settings tab's "Teleport and travel ticks"). 0 = none.
	 */
	public interface TickChanges
	{
		/** The change of one transport, by its id ({@code ":back"}, network rides as in {@link PlayerTransports}). */
		int transport(String transportId);

		/** The change of every teleport in this teleport category (its name as {@link TickGroups.Group#getName()}). */
		int teleportCategory(String category);

		/** The change of every ride of this network (its name as {@link TickGroups.Group#getName()}). */
		int network(String network);

		/** The change of every transport that costs coins (the GP row). */
		default int coins()
		{
			return 0;
		}
	}

	/**
	 * As {@link #build(PlayerState, int, FreeLand)}, with the player's own tick changes ({@code null} = none): a
	 * transport's cost is its data cost plus its teleport category's or network's change (which on its own never
	 * takes its ticks below 0, see {@link TickGroups}) plus its own change, never below 0. The transports keep their
	 * data ticks in {@link Transport#getTicks()}; {@link Transport#cost(int)}, which every search and lower bound
	 * reads, has the changes.
	 */
	public Result build(PlayerState player, int startWilderness, FreeLand freeLand, TickChanges changes)
	{
		Result r = build(player, startWilderness, freeLand);
		if (changes == null)
		{
			return r;
		}
		// each transport's teleport category or network change, by id (every built transport has its line)
		Map<String, Integer> group = new HashMap<>();
		for (Map.Entry<Transport, TransportLine> e : r.lines.entrySet())
		{
			int g = TickGroups.change(e.getValue(), tables, changes, e.getKey().getTicks());
			if (g != 0)
			{
				group.put(e.getKey().getId(), g);
			}
		}
		return r.withTickChanges(id -> group.getOrDefault(id, 0) + changes.transport(id));
	}

	/**
	 * One transport of {@link #everything}: what it was made from and whether this player can use it now. Immutable.
	 */
	public static final class Candidate
	{
		private final Transport transport;
		private final TransportLine line;
		private final TransportLine fromStop;
		private final Check.Outcome outcome;
		private final boolean coolingDown;

		Candidate(Transport transport, TransportLine line, TransportLine fromStop, Check.Outcome outcome, boolean coolingDown)
		{
			this.transport = transport;
			this.line = line;
			this.fromStop = fromStop;
			this.outcome = outcome;
			this.coolingDown = coolingDown;
		}

		/** The transport as {@link #build} makes it (the same id, ends and ticks, with the player's tick changes). */
		public Transport getTransport()
		{
			return transport;
		}

		/** The exported line it was made from (for a network ride: the stop it goes to), as {@link Result#lineOf}. */
		public TransportLine getLine()
		{
			return line;
		}

		/** A network ride: the stop it leaves from; null for every other transport. */
		public TransportLine getFromStop()
		{
			return fromStop;
		}

		/**
		 * Its requirement line (with its network's) checked against the player: for a network ride the worse of its
		 * two stops; for a two-way transport kept whole, both ways have this outcome (else it is split, as by
		 * {@link #build}: {@code id} and {@code id + ":back"}).
		 */
		public Check.Outcome getOutcome()
		{
			return outcome;
		}

		/** A long-cooldown teleport whose cooldown runs ({@link #coolingDown(TransportLine, PlayerState)}). */
		public boolean isCoolingDown()
		{
			return coolingDown;
		}

		/**
		 * True when {@link #build} gives the player this transport for a normal route ({@link Result#getTransports()}:
		 * met with what they carry or wear, not cooling down).
		 */
		public boolean isUsableNow()
		{
			return outcome == Check.Outcome.MET && !coolingDown;
		}
	}

	/** Every transport for one world type whatever the player has: see {@link #everything}. Immutable. */
	public static final class Everything
	{
		private final List<Candidate> candidates;

		Everything(List<Candidate> candidates)
		{
			this.candidates = Collections.unmodifiableList(candidates);
		}

		/** In the same order as {@link #build} makes them (the usable ones are exactly {@link Result#getTransports()}). */
		public List<Candidate> getCandidates()
		{
			return candidates;
		}
	}

	/**
	 * Every transport the exported lines make for this world type, whatever the player has (to show why there is "no
	 * route"): the transports {@link #build(PlayerState, int, FreeLand, TickChanges)} would make with every
	 * requirement met and no cooldown running, each with whether this player can use it now. Only quest-only
	 * transports are never made (no route uses them). The same ids, ends, ticks and tick changes as {@link #build};
	 * a two-way transport is split into {@code id} and {@code id + ":back"} exactly when its two ways' requirements
	 * have different outcomes for this player (as {@link #build} splits it); network rides go from every stop to every
	 * other stop. On a free world ({@code freeLand} set) only transports from free land to free land. Routes start
	 * outside the Wilderness here (the plugin's routes always do), so no Wilderness limit applies.
	 *
	 * <p>Keep in step with {@link #build(PlayerState, int, FreeLand)}: the candidates usable now must be its
	 * transports, in its order.
	 */
	public synchronized Everything everything(PlayerState player, FreeLand freeLand, TickChanges changes)
	{
		List<Candidate> out = new ArrayList<>();
		Map<String, List<Candidate>> networks = new LinkedHashMap<>();
		Map<String, Check> checked = new HashMap<>();
		for (TransportLine r : lines)
		{
			if (r.isQuestOnly())
			{
				continue; // never used on a route
			}
			boolean cooling = coolingDown(r, player);
			Check there = check(tables.networkRequirement(r.getNetwork(), r.getRequirements()), player, checked);
			Check back = r.getKind() == TransportLine.Kind.TWO_WAY
				? check(tables.networkRequirement(r.getNetwork(), r.requirementsEndToStart()), player, checked) : there;
			switch (r.getKind())
			{
				case NETWORK:
					// a stop: its ride ticks are the transport's ticks; the rides are made below
					networks.computeIfAbsent(r.getNetwork(), k -> new ArrayList<>()).add(new Candidate(Transport.between(r.getId(),
						r.getName(), r.getEndX(), r.getEndY(), r.getEndPlane(), r.getEndX(), r.getEndY(), r.getEndPlane(), rideTicks(r),
						false, true), r, null, there.getOutcome(), cooling));
					break;
				case TELEPORT:
					out.add(new Candidate(Transport.teleport(r.getId(), r.getName(), r.getEndX(), r.getEndY(), r.getEndPlane(),
						teleportTicks(r), true), r, null, there.getOutcome(), cooling));
					break;
				default:
				{
					boolean twoWay = r.getKind() == TransportLine.Kind.TWO_WAY;
					if (!twoWay || there.getOutcome() == back.getOutcome())
					{
						out.add(new Candidate(Transport.between(r.getId(), r.getName(), r.getStartX(), r.getStartY(), r.getStartPlane(),
							r.getEndX(), r.getEndY(), r.getEndPlane(), rideTicks(r), twoWay, true).forBoats(r.getLargestBoat()), r, null, there.getOutcome(), cooling));
						break;
					}
					// the two ways differ for this player: one transport per way, as build() splits it
					out.add(new Candidate(Transport.between(r.getId(), r.getName(), r.getStartX(), r.getStartY(), r.getStartPlane(),
						r.getEndX(), r.getEndY(), r.getEndPlane(), rideTicks(r), false, true).forBoats(r.getLargestBoat()), r, null, there.getOutcome(), cooling));
					out.add(new Candidate(Transport.between(r.getId() + ":back", r.getName(), r.getEndX(), r.getEndY(), r.getEndPlane(),
						r.getStartX(), r.getStartY(), r.getStartPlane(), rideTicks(r), false, true).forBoats(r.getLargestBoat()), r, null, back.getOutcome(), cooling));
					break;
				}
			}
		}
		// every stop of a network to every other stop; a ride costs the destination stop's ticks (as build())
		networks.forEach((net, stops) ->
		{
			for (Candidate a : stops)
			{
				for (Candidate b : stops)
				{
					TransportLine la = a.line;
					TransportLine lb = b.line;
					if (a != b && (la.getEndX() != lb.getEndX() || la.getEndY() != lb.getEndY() || la.getEndPlane() != lb.getEndPlane()))
					{
						Transport t = Transport.between(net + ":" + la.getName() + ">" + lb.getName(), lb.getName(), la.getEndX(),
							la.getEndY(), la.getEndPlane(), lb.getEndX(), lb.getEndY(), lb.getEndPlane(), b.transport.getTicks(), false,
							true).usedFromBeside();
						Check.Outcome worse = a.outcome.compareTo(b.outcome) >= 0 ? a.outcome : b.outcome;
						out.add(new Candidate(t, lb, la, worse, a.coolingDown || b.coolingDown));
					}
				}
			}
		});
		if (freeLand != null)
		{
			// a free world: only transports from free land to free land (a teleport: its landing)
			out.removeIf(c -> !freeLand.isFree(c.transport.getEndX(), c.transport.getEndY())
				|| c.transport.hasStart() && !freeLand.isFree(c.transport.getStartX(), c.transport.getStartY()));
		}
		if (changes != null)
		{
			// the player's own tick changes, as build(..., TickChanges): the teleport category's or network's, plus its own
			for (int i = 0; i < out.size(); i++)
			{
				Candidate c = out.get(i);
				int change = TickGroups.change(c.line, tables, changes, c.transport.getTicks()) + changes.transport(c.transport.getId());
				Transport t = c.transport.withTickChange(change);
				if (t != c.transport)
				{
					out.set(i, new Candidate(t, c.line, c.fromStop, c.outcome, c.coolingDown));
				}
			}
		}
		return new Everything(out);
	}

	/**
	 * A requirement line read (kept for later, as for the transports); null when it can't be read (such a line counts
	 * as met). The empty line is {@link Requirement#NONE}.
	 */
	public synchronized Requirement requirement(String line)
	{
		return parse(line == null ? "" : line);
	}

	private static void add(List<Transport> out, Map<Transport, TransportLine> from, Map<Transport, List<BankItem>> bank,
		Transport t, TransportLine r, Check c)
	{
		out.add(t);
		from.put(t, r);
		List<BankItem> b = bankItems(c);
		if (!b.isEmpty())
		{
			bank.put(t, b);
		}
	}

	/**
	 * The banks this player may start a route at: those whose needs are met with what the
	 * player has now (a line that can't be read counts as met, as for transports); on a free world
	 * ({@code freeLand} set) only use tiles on free land.
	 */
	public synchronized BankFirst.Banks banks(BankList list, PlayerState player, FreeLand freeLand)
	{
		Map<String, Check> checked = new HashMap<>();
		return list.usable(needs -> check(needs, player, checked).isMet(), freeLand);
	}

	/** Every bank's needs, read once (the plugin watches the varbits and quests they use). */
	public synchronized List<Requirement> bankRequirements(BankList list)
	{
		List<Requirement> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (BankList.Bank b : list.getBanks())
		{
			if (b.getNeeds().isEmpty() || !seen.add(b.getNeeds()))
			{
				continue;
			}
			Requirement req = parse(b.getNeeds());
			if (req != null)
			{
				out.add(req);
			}
		}
		return out;
	}

	/**
	 * True if this is a long-cooldown teleport whose cooldown the player is waiting out ({@link Cooldown}).
	 */
	public static boolean coolingDown(TransportLine r, PlayerState player)
	{
		Cooldown c = Cooldown.of(r.isCooldown(), r.getTeleportCategory());
		return c != null && !player.cooldownReady(c);
	}

	private int rideTicks(TransportLine r)
	{
		return r.getTicks() + tables.networkExtraTicks(r.getNetwork());
	}

	private int teleportTicks(TransportLine r)
	{
		Integer c = tables.teleportCategoryTicks(r.getTeleportCategory());
		return c != null ? c : r.getTicks();
	}

	/** The check of a requirement line; a line that can't be read counts as met. */
	private Check check(String line, PlayerState player, Map<String, Check> checked)
	{
		String key = line == null ? "" : line;
		Check c = checked.get(key);
		if (c == null)
		{
			Requirement req = parse(key);
			c = req == null ? Check.MET : req.check(player);
			checked.put(key, c);
		}
		return c;
	}

	/** A requirement line read once (kept); null when it can't be read (counts as met). */
	private Requirement parse(String key)
	{
		Requirement req = parsed.get(key);
		if (req == null && !parsed.containsKey(key))
		{
			try
			{
				req = RequirementText.parse(key, tables.itemGroups());
			}
			catch (RequirementText.ParseException e)
			{
				req = null; // unreadable: counts as met
			}
			parsed.put(key, req);
		}
		return req;
	}

	private static boolean usable(Check c)
	{
		return c.getOutcome() != Check.Outcome.NOT_MET;
	}

	/** What a check needs from the bank (empty unless it is met only with the bank). */
	private static List<BankItem> bankItems(Check c)
	{
		return c.getOutcome() != Check.Outcome.NEEDS_BANK ? List.of() : c.getBankItems();
	}

	/**
	 * Every requirement the transports can need, read once: each line's
	 * requirement and its way back, with its network's requirement in front
	 * (lines that can't be read are left out). The plugin uses them to know
	 * which varbits, quests and items to watch.
	 */
	public synchronized List<Requirement> allRequirements()
	{
		List<Requirement> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (TransportLine r : lines)
		{
			for (String text : new String[]{tables.networkRequirement(r.getNetwork(), r.getRequirements()),
				tables.networkRequirement(r.getNetwork(), r.requirementsEndToStart())})
			{
				if (!seen.add(text))
				{
					continue;
				}
				Requirement req = parsed.get(text);
				if (req == null && !parsed.containsKey(text))
				{
					try
					{
						req = RequirementText.parse(text, tables.itemGroups());
					}
					catch (RequirementText.ParseException e)
					{
						req = null;
					}
					parsed.put(text, req);
				}
				if (req != null)
				{
					out.add(req);
				}
			}
		}
		return out;
	}
}

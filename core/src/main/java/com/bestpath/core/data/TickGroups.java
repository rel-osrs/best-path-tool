package com.bestpath.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The teleport categories and transport networks whose ticks a player may change as a whole, with the ticks their
 * transports take. The groups come from the shipped teleport-categories.tsv and networks.tsv tables.
 *
 * <p>A transport's ticks in a route: its data ticks (a teleport: its category's ticks; a ride: its own ticks plus its
 * network's extra), plus its teleport category's or network's change (which on its own never takes it below 0
 * ticks), plus its own change (- / + on its step card), plus the plane-change penalty when it changes plane, never
 * below 0. {@link PlayerTransports#build(com.bestpath.core.req.PlayerState, int,
 * com.bestpath.core.collision.FreeLand, PlayerTransports.TickChanges)} builds the player's transports that way and
 * {@link #ticks(String, PlayerTransports.TickChanges)} gives the same number for one transport (for the plugin's
 * Settings tab). Immutable; any thread.
 */
public final class TickGroups
{
	/** A teleport category (every teleport in it takes its ticks) or a network (every ride gets its extra). */
	public enum Kind
	{
		TELEPORT_CATEGORY,
		NETWORK,
		/** Every transport that costs coins (the "GP" row). */
		COINS
	}

	/** The coins row's name. */
	public static final String COINS_NAME = "GP (costs coins)";
	/** "coins 30" in a requirement (a fare, a payment; also as one choice of a "one of"). */
	private static final Pattern COINS = Pattern.compile("(^|[;(|&])\\s*coins\\s+\\d");

	/** One teleport category or network and the ticks its transports take in the data. */
	public static final class Group
	{
		private final Kind kind;
		private final String name;
		private final int lowest;
		private final int highest;
		private final int count;

		Group(Kind kind, String name, int lowest, int highest, int count)
		{
			this.kind = kind;
			this.name = name;
			this.lowest = lowest;
			this.highest = highest;
			this.count = count;
		}

		public Kind getKind()
		{
			return kind;
		}

		/** As the table has it ("spell", "jewellery (infinite)"; "Fairy ring", "Player-owned house"). */
		public String getName()
		{
			return name;
		}

		/**
		 * How many transport lines it covers (teleports; for a network its rides' lines, or its stops); quest-only
		 * lines are left out, as routes never use them.
		 */
		public int getCount()
		{
			return count;
		}

		/** The lowest ticks of its transports with this change (never below 0); a teleport category's are all the same. */
		public int lowest(int change)
		{
			return Math.max(0, lowest + change);
		}

		/** The highest ticks of its transports with this change (never below 0). */
		public int highest(int change)
		{
			return Math.max(0, highest + change);
		}

		/** The lowest change that still does something: with it every transport of the group is at 0 ticks. */
		public int lowestChange()
		{
			return -highest;
		}
	}

	/** No groups (no map data yet). */
	public static final TickGroups EMPTY = new TickGroups(List.of(), Tables.empty(), List.of());

	private final List<TransportLine> lines;
	private final Tables tables;
	private final List<Group> groups;
	/** The lines by id (the Settings tab asks for thousands of transports' ticks at once). */
	private final Map<String, TransportLine> byId = new HashMap<>();

	private TickGroups(List<TransportLine> lines, Tables tables, List<Group> groups)
	{
		this.lines = lines;
		for (TransportLine l : lines)
		{
			byId.putIfAbsent(l.getId(), l);
		}
		this.tables = tables;
		this.groups = Collections.unmodifiableList(groups);
	}

	/**
	 * Every teleport category, then every network, in the tables' order; then the coins group when any transport costs
	 * coins.
	 */
	public static TickGroups of(List<TransportLine> lines, Tables tables)
	{
		List<Group> out = new ArrayList<>();
		for (String c : tables.teleportCategoryNames())
		{
			int ticks = tables.teleportCategoryTicks(c);
			int n = 0;
			for (TransportLine l : lines)
			{
				if (l.getKind() == TransportLine.Kind.TELEPORT && !l.isQuestOnly() && c.equalsIgnoreCase(l.getTeleportCategory().trim()))
				{
					n++;
				}
			}
			out.add(new Group(Kind.TELEPORT_CATEGORY, c, ticks, ticks, n));
		}
		for (Tables.Network net : tables.networks())
		{
			int lo = Integer.MAX_VALUE;
			int hi = Integer.MIN_VALUE;
			int n = 0;
			for (TransportLine l : lines)
			{
				if (l.getKind() != TransportLine.Kind.TELEPORT && !l.isQuestOnly() && net.getName().equalsIgnoreCase(l.getNetwork().trim()))
				{
					int t = l.getTicks() + net.getExtraTicks();
					lo = Math.min(lo, t);
					hi = Math.max(hi, t);
					n++;
				}
			}
			if (n == 0)
			{
				lo = net.getExtraTicks();
				hi = lo;
			}
			out.add(new Group(Kind.NETWORK, net.getName(), lo, hi, n));
		}
		// every transport that costs coins, at the ticks routes give it without the player's changes
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		int n = 0;
		for (TransportLine l : lines)
		{
			if (!l.isQuestOnly() && costsCoins(l, tables))
			{
				int t = dataTicks(l, tables);
				lo = Math.min(lo, t);
				hi = Math.max(hi, t);
				n++;
			}
		}
		if (n > 0)
		{
			out.add(new Group(Kind.COINS, COINS_NAME, lo, hi, n));
		}
		return new TickGroups(List.copyOf(lines), tables, out);
	}

	/** A line's ticks in the data: a teleport its category's (if the table knows it), any other its own plus its network's extra. */
	private static int dataTicks(TransportLine l, Tables tables)
	{
		if (l.getKind() == TransportLine.Kind.TELEPORT)
		{
			Integer c = tables.teleportCategoryTicks(l.getTeleportCategory());
			return c != null ? c : l.getTicks();
		}
		return l.getTicks() + tables.networkExtraTicks(l.getNetwork());
	}

	/**
	 * True if using it costs coins: "coins N" in its requirement either way, its network's included (also as one choice of
	 * a "one of": a magic carpet's fare, which a diary can make free, counts).
	 */
	public static boolean costsCoins(TransportLine line, Tables tables)
	{
		String req = (tables.networkRequirement(line.getNetwork(), line.getRequirements()) + " ; "
			+ tables.networkRequirement(line.getNetwork(), line.requirementsEndToStart())).toLowerCase(Locale.ROOT);
		return COINS.matcher(req).find();
	}

	public List<Group> getGroups()
	{
		return groups;
	}

	/**
	 * The change of its teleport category (a teleport whose category the table knows) or of its network (any other
	 * transport whose network the table knows) that applies to a transport made from {@code line}; on its own it
	 * never takes the transport's {@code ticks} below 0. 0 = none. {@code changes} is asked by the group's name as
	 * {@link Group#getName()} has it.
	 */
	static int change(TransportLine line, Tables tables, PlayerTransports.TickChanges changes, int ticks)
	{
		int g;
		if (line.getKind() == TransportLine.Kind.TELEPORT)
		{
			String c = line.getTeleportCategory().trim().toLowerCase(Locale.ROOT);
			g = c.isEmpty() || tables.teleportCategoryTicks(c) == null ? 0 : changes.teleportCategory(c);
		}
		else
		{
			Tables.Network n = line.getNetwork().isEmpty() ? null : tables.network(line.getNetwork());
			g = n == null ? 0 : changes.network(n.getName());
		}
		// the GP row's change counts on top for a transport that costs coins
		int coins = changes.coins();
		if (coins != 0 && costsCoins(line, tables))
		{
			g += coins;
		}
		return g == 0 ? 0 : Math.max(g, -ticks);
	}

	/**
	 * The ticks routes use for this transport with these changes (the plane-change penalty included), the same as
	 * the transport {@link PlayerTransports} builds costs; -1 for an id the data does not have. {@code transportId}
	 * as in {@link PlayerTransports}: a line's id, {@code id + ":back"}, or a network ride
	 * {@code "network:A>B"}.
	 */
	public int ticks(String transportId, PlayerTransports.TickChanges changes)
	{
		if (transportId == null)
		{
			return -1;
		}
		String id = transportId.endsWith(":back") ? transportId.substring(0, transportId.length() - 5) : transportId;
		TransportLine line = byId.get(id);
		int ticks;
		boolean planeChange;
		if (line != null)
		{
			if (line.getKind() == TransportLine.Kind.TELEPORT)
			{
				Integer c = tables.teleportCategoryTicks(line.getTeleportCategory());
				ticks = c != null ? c : line.getTicks();
			}
			else
			{
				ticks = line.getTicks() + tables.networkExtraTicks(line.getNetwork());
			}
			planeChange = line.hasStart() && line.getStartPlane() != line.getEndPlane();
		}
		else
		{
			// a network ride "network:A>B": stop B's ticks, from stop A's place to stop B's
			TransportLine[] ride = ride(transportId);
			if (ride == null)
			{
				return -1;
			}
			line = ride[1];
			ticks = line.getTicks() + tables.networkExtraTicks(line.getNetwork());
			planeChange = ride[0].getEndPlane() != line.getEndPlane();
		}
		int c = change(line, tables, changes, ticks) + changes.transport(transportId);
		return Math.max(0, ticks + (planeChange ? PluginRouter.PLANE_CHANGE_PENALTY : 0) + c);
	}

	/** The two stops {A, B} of a network ride id {@code "network:A>B"}, or null. */
	private TransportLine[] ride(String id)
	{
		for (TransportLine b : lines)
		{
			if (b.getKind() != TransportLine.Kind.NETWORK || !id.startsWith(b.getNetwork() + ":") || !id.endsWith(">" + b.getName()))
			{
				continue;
			}
			for (TransportLine a : lines)
			{
				if (a.getKind() == TransportLine.Kind.NETWORK && a.getNetwork().equals(b.getNetwork())
					&& id.equals(b.getNetwork() + ":" + a.getName() + ">" + b.getName()))
				{
					return new TransportLine[]{a, b};
				}
			}
		}
		return null;
	}
}

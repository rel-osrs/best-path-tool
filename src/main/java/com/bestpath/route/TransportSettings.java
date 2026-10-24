package com.bestpath.route;

import com.bestpath.core.data.PlayerTransports;
import com.bestpath.core.path.BankFirst;
import com.bestpath.core.sea.Dock;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The sidebar Settings tab's choices (per account profile): transport
 * categories switched off, "avoid" criteria and the persistent blacklist;
 * Sailing; the player's own tick changes (the - / + on step cards, and per
 * teleport category or network in the Settings tab); the bank stop's ticks (a
 * route may begin with a bank visit).
 * Immutable; every change makes a new copy.
 *
 * <p>{@link TransportRules} says which transports each setting covers.
 */
public final class TransportSettings
{
	/** Transport categories that can be switched off (names are saved; labels can change). */
	public enum Category
	{
		TELEPORT_SPELLS("Spells, home and minigame teleports"),
		TELEPORT_ITEMS("Teleport items, jewellery"),
		HOUSE("House (POH) teleports and portals"),
		STAIRS_LADDERS("Stairs and ladders"),
		SHORTCUTS("Shortcuts"),
		DOORS("Gates and barriers that need something"),
		BOATS("Boats, fairy rings, carts, other rides");

		private final String label;

		Category(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	/** "Avoid ..." rules: cost, runes, known bugs, the data's long-cooldown flag and the Wilderness. */
	public enum Avoid
	{
		COST("Transports that cost something"),
		RUNES("Teleports that use runes"),
		KNOWN_BUGS("Transports with known bugs"),
		COOLDOWN("Long-cooldown teleports"),
		/** Routes keep out of the Wilderness unless there is no other way. */
		WILDERNESS("The Wilderness (unless there's no other way)");

		private final String label;

		Avoid(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	private static final TransportSettings DEFAULTS = new Draft().build();

	private final Set<Category> disabled;
	private final Set<Avoid> avoid;
	/** Persistent blacklist: transport id -> name shown in the list, in the order they were added. */
	private final Map<String, String> blacklist;
	/** Sailing: boat slots routes may not use; whether a shipwright may bring a boat. */
	private final Set<Integer> boatsOff;
	private final boolean shipwrights;
	/**
	 * The player's own tick changes (the - / + on a step card): transport key ({@link #tickKey}) -> change (never 0),
	 * and the name shown for each, in the order they were first changed.
	 */
	private final Map<String, Integer> ticks;
	private final Map<String, String> tickNames;
	/** When each tick change was last made: key -> milliseconds since 1970; missing when not known. */
	private final Map<String, Long> tickTimes;
	/**
	 * The player's own change of whole teleport categories and networks (the Settings tab's "Teleport and travel
	 * ticks"): key ({@link #teleportCategoryKey}, {@link #networkKey}) -> change (never 0). Tests read it.
	 */
	final Map<String, Integer> groupTicks;

	/** The bank stop's ticks unless the player changes them. */
	public static final int DEFAULT_BANK_TICKS = BankFirst.DEFAULT_TICKS;
	/** The most the bank stop and the boarding ticks go up to. */
	public static final int MAX_BANK_TICKS = 10000;
	/** Ticks a route that begins at a bank counts for using it; the player can change it. */
	private final int bankTicks;
	/** The Settings tab's "Teleport and travel ticks" list is unfolded (folded unless opened). */
	private final boolean ticksListOpen;
	/**
	 * Sailing: never leave the boat the player is on at sea; a route from it starts by sailing it to a dock with a
	 * shipwright. Off by default (the quicker way may leave it at sea; a shipwright may bring it back later).
	 */
	private final boolean keepBoat;

	/** Ticks to board a boat, and again to leave it, unless the player changes them. */
	public static final int DEFAULT_BOARD_TICKS = Dock.BOARD_TICKS;
	/**
	 * Routes may start at a bank; on by default. Off: routes use only what the player carries and never go to a bank
	 * first.
	 */
	private final boolean bankDetour;

	/** Ticks to board a boat, and again to leave it or release it from its bottle; the player can change it. */
	private final int boardTicks;

	/** The coin limit's amount until the player changes it (the tick box starts off). */
	public static final int DEFAULT_COIN_CAP = 1000;
	/** The most the coin limit goes up to. */
	public static final int MAX_COIN_CAP = 100_000_000;
	/**
	 * The coin limit: with the tick box on, routes never use a transport whose fare is more than {@link #coinCap} coins
	 * (each fare on its own, not the route's total).
	 */
	private final boolean coinCapOn;
	private final int coinCap;

	private TransportSettings(Draft d)
	{
		this.coinCapOn = d.coinCapOn;
		this.coinCap = Math.max(0, Math.min(MAX_COIN_CAP, d.coinCap));
		this.bankDetour = d.bankDetour;
		this.boardTicks = Math.max(0, Math.min(MAX_BANK_TICKS, d.boardTicks));
		this.keepBoat = d.keepBoat;
		this.ticksListOpen = d.ticksListOpen;
		this.bankTicks = Math.max(0, Math.min(MAX_BANK_TICKS, d.bankTicks));
		this.groupTicks = Collections.unmodifiableMap(new LinkedHashMap<>(d.groupTicks));
		this.ticks = Collections.unmodifiableMap(new LinkedHashMap<>(d.ticks));
		this.tickNames = Collections.unmodifiableMap(new LinkedHashMap<>(d.tickNames));
		Map<String, Long> times = new LinkedHashMap<>(d.tickTimes);
		times.keySet().retainAll(d.ticks.keySet());
		this.tickTimes = Collections.unmodifiableMap(times);
		this.boatsOff = Collections.unmodifiableSet(new TreeSet<>(d.boatsOff));
		this.shipwrights = d.shipwrights;
		this.disabled = Collections.unmodifiableSet(d.disabled.isEmpty() ? EnumSet.noneOf(Category.class) : EnumSet.copyOf(d.disabled));
		this.avoid = Collections.unmodifiableSet(d.avoid.isEmpty() ? EnumSet.noneOf(Avoid.class) : EnumSet.copyOf(d.avoid));
		this.blacklist = Collections.unmodifiableMap(new LinkedHashMap<>(d.blacklist));
	}

	/**
	 * A changeable copy of the settings: each with... method changes one part of a copy and makes new settings from it
	 * ({@link #build}, which also keeps every number in range).
	 */
	private static final class Draft
	{
		Set<Category> disabled = EnumSet.noneOf(Category.class);
		Set<Avoid> avoid = EnumSet.noneOf(Avoid.class);
		Map<String, String> blacklist = Map.of();
		Set<Integer> boatsOff = Set.of();
		boolean shipwrights = true;
		Map<String, Integer> ticks = Map.of();
		Map<String, String> tickNames = Map.of();
		Map<String, Long> tickTimes = Map.of();
		Map<String, Integer> groupTicks = Map.of();
		int bankTicks = BankFirst.DEFAULT_TICKS;
		boolean ticksListOpen;
		boolean keepBoat;
		int boardTicks = Dock.BOARD_TICKS;
		boolean bankDetour = true;
		boolean coinCapOn;
		int coinCap = DEFAULT_COIN_CAP;

		/** The defaults: everything allowed, nothing blacklisted, no tick changes. */
		Draft()
		{
		}

		Draft(TransportSettings s)
		{
			disabled = s.disabled;
			avoid = s.avoid;
			blacklist = s.blacklist;
			boatsOff = s.boatsOff;
			shipwrights = s.shipwrights;
			ticks = s.ticks;
			tickNames = s.tickNames;
			tickTimes = s.tickTimes;
			groupTicks = s.groupTicks;
			bankTicks = s.bankTicks;
			ticksListOpen = s.ticksListOpen;
			keepBoat = s.keepBoat;
			boardTicks = s.boardTicks;
			bankDetour = s.bankDetour;
			coinCapOn = s.coinCapOn;
			coinCap = s.coinCap;
		}

		TransportSettings build()
		{
			return new TransportSettings(this);
		}
	}

	/** A changeable copy of these settings. */
	private Draft draft()
	{
		return new Draft(this);
	}

	/** Everything allowed, nothing blacklisted. */
	public static TransportSettings defaults()
	{
		return DEFAULTS;
	}

	public boolean isEnabled(Category c)
	{
		return !disabled.contains(c);
	}

	public boolean avoids(Avoid a)
	{
		return avoid.contains(a);
	}

	public boolean isBlacklisted(String transportId)
	{
		return blacklist.containsKey(transportId);
	}

	/** Blacklisted transports, id -> name, oldest first. */
	public Map<String, String> getBlacklist()
	{
		return blacklist;
	}

	/** Sailing: whether routes may use the boat in this slot (1-5); every boat unless switched off. */
	public boolean isBoatAllowed(int slot)
	{
		return !boatsOff.contains(slot);
	}

	/** Sailing: whether a shipwright may bring a boat to the player's dock (costs coins); allowed unless switched off. */
	public boolean isShipwrights()
	{
		return shipwrights;
	}

	public TransportSettings withBoatAllowed(int slot, boolean allowed)
	{
		Set<Integer> off = new TreeSet<>(boatsOff);
		if (allowed)
		{
			off.remove(slot);
		}
		else
		{
			off.add(slot);
		}
		Draft d = draft();
		d.boatsOff = off;
		return d.build();
	}

	/** Sailing: never leave the boat the player is on at sea (Settings; off unless switched on). */
	public boolean isKeepBoat()
	{
		return keepBoat;
	}

	public TransportSettings withKeepBoat(boolean keep)
	{
		Draft d = draft();
		d.keepBoat = keep;
		return d.build();
	}

	public TransportSettings withShipwrights(boolean allowed)
	{
		Draft d = draft();
		d.shipwrights = allowed;
		return d.build();
	}

	public TransportSettings withCategory(Category c, boolean enabled)
	{
		EnumSet<Category> off = disabled.isEmpty() ? EnumSet.noneOf(Category.class) : EnumSet.copyOf(disabled);
		if (enabled)
		{
			off.remove(c);
		}
		else
		{
			off.add(c);
		}
		Draft d = draft();
		d.disabled = off;
		return d.build();
	}

	public TransportSettings withAvoid(Avoid a, boolean on)
	{
		EnumSet<Avoid> v = avoid.isEmpty() ? EnumSet.noneOf(Avoid.class) : EnumSet.copyOf(avoid);
		if (on)
		{
			v.add(a);
		}
		else
		{
			v.remove(a);
		}
		Draft d = draft();
		d.avoid = v;
		return d.build();
	}

	public TransportSettings withBlacklisted(String transportId, String name)
	{
		Map<String, String> b = new LinkedHashMap<>(blacklist);
		b.put(transportId, name == null || name.isEmpty() ? transportId : name);
		Draft d = draft();
		d.blacklist = b;
		return d.build();
	}

	public TransportSettings withoutBlacklisted(String transportId)
	{
		Map<String, String> b = new LinkedHashMap<>(blacklist);
		b.remove(transportId);
		Draft d = draft();
		d.blacklist = b;
		return d.build();
	}

	/**
	 * The key a transport's tick change is saved under: its id, with a split two-way transport's way back
	 * ({@code id + ":back"}) under the transport's own id, so one change counts for both ways (the data has one
	 * tick value for both). Network rides keep their own ids (one per ride, as for "Skip").
	 */
	public static String tickKey(String transportId)
	{
		return transportId != null && transportId.endsWith(":back") ? transportId.substring(0, transportId.length() - 5)
			: transportId;
	}

	/** The player's own change of this transport's ticks (0 = the data's ticks). */
	public int tickChange(String transportId)
	{
		return transportId == null ? 0 : ticks.getOrDefault(tickKey(transportId), 0);
	}

	/** Every tick change: key ({@link #tickKey}) -> change, in the order they were first made. */
	public Map<String, Integer> getTickChanges()
	{
		return ticks;
	}

	/** When this tick change was last made (milliseconds since 1970); 0 = not known, or no change. */
	public long tickTime(String key)
	{
		return tickTimes.getOrDefault(key, 0L);
	}

	/** The name shown for a changed transport (its key if none). */
	public String tickName(String key)
	{
		return tickNames.getOrDefault(key, key);
	}

	/**
	 * With this transport's ticks changed by {@code change} for this player; 0 = back to the data's ticks (the change
	 * is removed). {@code name}: what the Settings tab shows for it.
	 */
	public TransportSettings withTickChange(String transportId, int change, String name)
	{
		return withTickChange(transportId, change, name, System.currentTimeMillis());
	}

	/** As {@link #withTickChange(String, int, String)}, made at {@code when} (milliseconds since 1970). */
	public TransportSettings withTickChange(String transportId, int change, String name, long when)
	{
		String key = tickKey(transportId);
		Map<String, Integer> t = new LinkedHashMap<>(ticks);
		Map<String, String> n = new LinkedHashMap<>(tickNames);
		Map<String, Long> tt = new LinkedHashMap<>(tickTimes);
		if (change == 0)
		{
			t.remove(key);
			n.remove(key);
			tt.remove(key);
		}
		else
		{
			t.put(key, change);
			n.put(key, name == null || name.isEmpty() ? key : name);
			tt.put(key, when);
		}
		Draft d = draft();
		d.ticks = t;
		d.tickNames = n;
		d.tickTimes = tt;
		return d.build();
	}

	/** The key a whole teleport category's tick change is saved under ("teleport:spell"; any case). */
	public static String teleportCategoryKey(String category)
	{
		return "teleport:" + (category == null ? "" : category.trim().toLowerCase(Locale.ROOT));
	}

	/** The key the GP row's change (every transport that costs coins) is saved under. */
	public static final String COINS_KEY = "cost:coins";

	/** The key a whole network's tick change is saved under ("network:fairy ring"; any case). */
	public static String networkKey(String network)
	{
		return "network:" + (network == null ? "" : network.trim().toLowerCase(Locale.ROOT));
	}

	/** The player's own change of a whole teleport category or network, by its key; 0 = the data's ticks. */
	public int groupTickChange(String key)
	{
		return key == null ? 0 : groupTicks.getOrDefault(key, 0);
	}

	/** The player's own change of every teleport in this category (0 = none). */
	public int teleportCategoryTickChange(String category)
	{
		return groupTicks.isEmpty() ? 0 : groupTickChange(teleportCategoryKey(category));
	}

	/** The player's own change of every ride of this network (0 = none). */
	public int networkTickChange(String network)
	{
		return groupTicks.isEmpty() ? 0 : groupTickChange(networkKey(network));
	}

	/** The player's own change of every transport that costs coins, the GP row (0 = none). */
	public int coinsTickChange()
	{
		return groupTicks.isEmpty() ? 0 : groupTickChange(COINS_KEY);
	}

	/**
	 * With a whole teleport category's or network's ticks changed by {@code change} for this player (key from
	 * {@link #teleportCategoryKey} or {@link #networkKey}); 0 = back to the data's ticks (the change is removed).
	 */
	public TransportSettings withGroupTickChange(String key, int change)
	{
		Map<String, Integer> g = new LinkedHashMap<>(groupTicks);
		if (change == 0)
		{
			g.remove(key);
		}
		else
		{
			g.put(key, change);
		}
		Draft d = draft();
		d.groupTicks = g;
		return d.build();
	}

	/** True if the player changed any ticks (a transport's on its card, or a teleport category's or network's). */
	public boolean hasTickChanges()
	{
		return !ticks.isEmpty() || !groupTicks.isEmpty();
	}

	/** Every tick change of these settings the way core asks for them ({@link com.bestpath.core.data.TickGroups#ticks}). */
	public PlayerTransports.TickChanges tickChanges()
	{
		return new PlayerTransports.TickChanges()
		{
			@Override
			public int transport(String transportId)
			{
				return tickChange(transportId);
			}

			@Override
			public int teleportCategory(String category)
			{
				return teleportCategoryTickChange(category);
			}

			@Override
			public int network(String network)
			{
				return networkTickChange(network);
			}

			@Override
			public int coins()
			{
				return coinsTickChange();
			}
		};
	}

	/** True if routes may start at a bank; on unless switched off. */
	public boolean isBankDetour()
	{
		return bankDetour;
	}

	public TransportSettings withBankDetour(boolean allowed)
	{
		Draft d = draft();
		d.bankDetour = allowed;
		return d.build();
	}

	/** The bank stop's ticks (a route may begin with a bank visit). */
	public int getBankTicks()
	{
		return bankTicks;
	}

	/** With another bank stop (0 to {@value #MAX_BANK_TICKS} ticks). */
	public TransportSettings withBankTicks(int ticks)
	{
		Draft d = draft();
		d.bankTicks = ticks;
		return d.build();
	}

	/** Ticks to board a boat, and again to leave it or release it from its bottle. */
	public int getBoardTicks()
	{
		return boardTicks;
	}

	/** With other boarding ticks (0 to {@value #MAX_BANK_TICKS}, as every tick setting). */
	public TransportSettings withBoardTicks(int ticks)
	{
		Draft d = draft();
		d.boardTicks = ticks;
		return d.build();
	}

	/** True when routes keep to the coin limit ({@link #getCoinCap}); off unless switched on. */
	public boolean isCoinCapOn()
	{
		return coinCapOn;
	}

	/** The most coins one transport may cost while {@link #isCoinCapOn} (0 to {@value #MAX_COIN_CAP}). */
	public int getCoinCap()
	{
		return coinCap;
	}

	public TransportSettings withCoinCapOn(boolean on)
	{
		Draft d = draft();
		d.coinCapOn = on;
		return d.build();
	}

	public TransportSettings withCoinCap(int coins)
	{
		Draft d = draft();
		d.coinCap = coins;
		return d.build();
	}

	/** True when the Settings tab shows the "Teleport and travel ticks" list unfolded (a display choice, saved per account). */
	public boolean isTicksListOpen()
	{
		return ticksListOpen;
	}

	public TransportSettings withTicksListOpen(boolean open)
	{
		Draft d = draft();
		d.ticksListOpen = open;
		return d.build();
	}

	/**
	 * Text form for the RuneLite profile config, one setting per line:
	 * {@code off=<category>}, {@code avoid=<rule>}, {@code never=<id> <name>} (id and name URL-encoded),
	 * {@code boatoff=<slot>}, {@code shipwrights=off}, {@code keepboat=on},
	 * {@code ticks=<id> <change> <name> <when>} (id and name URL-encoded; when: milliseconds since 1970, left out when
	 * not known), {@code groupticks=<key> <change>} (key URL-encoded), {@code bank=<ticks>} (the bank stop),
	 * {@code board=<ticks>} (boarding or leaving a boat), {@code tickslist=open} (the Settings tab's "Teleport and
	 * travel ticks" unfolded), {@code bankdetour=off} (routes never start at a bank), {@code coincap=on} (the coin
	 * limit switched on), {@code coins=<n>} (its amount). bank, board and coins are written only when not the default.
	 */
	public String encode()
	{
		StringBuilder sb = new StringBuilder("v1\n");
		for (Category c : disabled)
		{
			sb.append("off=").append(c.name()).append('\n');
		}
		for (Avoid a : avoid)
		{
			sb.append("avoid=").append(a.name()).append('\n');
		}
		for (Map.Entry<String, String> e : blacklist.entrySet())
		{
			sb.append("never=").append(enc(e.getKey())).append(' ').append(enc(e.getValue())).append('\n');
		}
		for (int slot : boatsOff)
		{
			sb.append("boatoff=").append(slot).append('\n');
		}
		if (!shipwrights)
		{
			sb.append("shipwrights=off\n");
		}
		if (keepBoat)
		{
			sb.append("keepboat=on\n");
		}
		for (Map.Entry<String, Integer> e : ticks.entrySet())
		{
			sb.append("ticks=").append(enc(e.getKey())).append(' ').append(e.getValue()).append(' ')
				.append(enc(tickName(e.getKey())));
			long when = tickTime(e.getKey());
			if (when > 0)
			{
				sb.append(' ').append(when); // when it was made, if known
			}
			sb.append('\n');
		}
		for (Map.Entry<String, Integer> e : groupTicks.entrySet())
		{
			sb.append("groupticks=").append(enc(e.getKey())).append(' ').append(e.getValue()).append('\n');
		}
		if (bankTicks != DEFAULT_BANK_TICKS)
		{
			sb.append("bank=").append(bankTicks).append('\n');
		}
		if (boardTicks != DEFAULT_BOARD_TICKS)
		{
			sb.append("board=").append(boardTicks).append('\n');
		}
		if (ticksListOpen)
		{
			sb.append("tickslist=open\n");
		}
		if (!bankDetour)
		{
			sb.append("bankdetour=off\n");
		}
		if (coinCapOn)
		{
			sb.append("coincap=on\n");
		}
		if (coinCap != DEFAULT_COIN_CAP)
		{
			sb.append("coins=").append(coinCap).append('\n');
		}
		return sb.toString();
	}

	/** Reads {@link #encode()}'s text; unknown or broken lines are skipped (older/newer plugin versions). */
	public static TransportSettings decode(String text)
	{
		if (text == null || text.isEmpty())
		{
			return DEFAULTS;
		}
		Draft s = new Draft();
		Map<String, String> b = new LinkedHashMap<>();
		Set<Integer> boats = new TreeSet<>();
		Map<String, Integer> tc = new LinkedHashMap<>();
		Map<String, String> tn = new LinkedHashMap<>();
		Map<String, Long> tt = new LinkedHashMap<>();
		Map<String, Integer> gt = new LinkedHashMap<>();
		s.blacklist = b;
		s.boatsOff = boats;
		s.ticks = tc;
		s.tickNames = tn;
		s.tickTimes = tt;
		s.groupTicks = gt;
		for (String line : text.split("\n"))
		{
			int eq = line.indexOf('=');
			if (eq < 0)
			{
				continue;
			}
			String key = line.substring(0, eq);
			String value = line.substring(eq + 1).trim();
			try
			{
				switch (key)
				{
					case "off":
						s.disabled.add(Category.valueOf(value));
						break;
					case "avoid":
						s.avoid.add(Avoid.valueOf(value));
						break;
					case "never":
						int sp = value.indexOf(' ');
						String id = dec(sp < 0 ? value : value.substring(0, sp));
						String name = sp < 0 ? id : dec(value.substring(sp + 1));
						if (!id.isEmpty())
						{
							b.put(id, name);
						}
						break;
					case "boatoff":
						boats.add(Integer.parseInt(value));
						break;
					case "shipwrights":
						s.shipwrights = !value.equals("off");
						break;
					case "keepboat":
						s.keepBoat = value.equals("on");
						break;
					case "ticks":
					{
						String[] p = value.split(" ");
						String tid = p.length >= 2 ? dec(p[0]) : "";
						int change = p.length >= 2 ? Integer.parseInt(p[1]) : 0;
						if (!tid.isEmpty() && change != 0)
						{
							tc.put(tid, change);
							tn.put(tid, p.length >= 3 ? dec(p[2]) : tid);
							if (p.length >= 4)
							{
								tt.put(tid, Long.parseLong(p[3]));
							}
						}
						break;
					}
					case "groupticks":
					{
						String[] p = value.split(" ");
						String gk = p.length >= 2 ? dec(p[0]) : "";
						int change = p.length >= 2 ? Integer.parseInt(p[1]) : 0;
						if ((gk.startsWith("teleport:") || gk.startsWith("network:") || gk.equals(COINS_KEY)) && change != 0)
						{
							gt.put(gk, change);
						}
						break;
					}
					case "bank":
						s.bankTicks = Integer.parseInt(value);
						break;
					case "board":
						s.boardTicks = Integer.parseInt(value);
						break;
					case "tickslist":
						s.ticksListOpen = value.equals("open");
						break;
					case "bankdetour":
						s.bankDetour = !value.equals("off");
						break;
					case "coincap":
						s.coinCapOn = value.equals("on");
						break;
					case "coins":
						s.coinCap = Integer.parseInt(value);
						break;
					default:
						break;
				}
			}
			catch (IllegalArgumentException e)
			{
				// an unknown category or rule, or a broken number: skip the line
			}
		}
		return s.build();
	}

	private static String enc(String s)
	{
		try
		{
			return URLEncoder.encode(s, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static String dec(String s)
	{
		try
		{
			return URLDecoder.decode(s, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			throw new IllegalStateException(e);
		}
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof TransportSettings))
		{
			return false;
		}
		TransportSettings t = (TransportSettings) o;
		return disabled.equals(t.disabled) && avoid.equals(t.avoid) && blacklist.equals(t.blacklist)
			&& boatsOff.equals(t.boatsOff) && shipwrights == t.shipwrights && ticks.equals(t.ticks) && tickNames.equals(t.tickNames)
			&& groupTicks.equals(t.groupTicks) && bankTicks == t.bankTicks && ticksListOpen == t.ticksListOpen && keepBoat == t.keepBoat
			&& boardTicks == t.boardTicks && bankDetour == t.bankDetour && coinCapOn == t.coinCapOn && coinCap == t.coinCap
			&& String.join(",", blacklist.keySet()).equals(String.join(",", t.blacklist.keySet()));
	}

	@Override
	public int hashCode()
	{
		return disabled.hashCode() * 31 + avoid.hashCode() * 17 + blacklist.hashCode() + boatsOff.hashCode() * 7 + (shipwrights ? 1 : 0)
			+ ticks.hashCode() * 13 + groupTicks.hashCode() * 11 + bankTicks * 101 + (ticksListOpen ? 2 : 0) + (keepBoat ? 4 : 0) + boardTicks * 37 + (bankDetour ? 8 : 0)
			+ (coinCapOn ? 16 : 0) + coinCap * 41;
	}
}

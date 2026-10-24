package com.bestpath;

import com.bestpath.route.TransportSettings;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The sidebar Settings tab's switches, shown in the RuneLite config panel too: the two places mirror each other. The
 * choices are saved per account ({@link TransportSettings}); the config panel's values ({@link BestPathConfig#TYPES},
 * {@link BestPathConfig#AVOID}, {@link BestPathConfig#BANK}, {@link BestPathConfig#SAILING}) are the account logged in's,
 * written whenever its choices change or another account logs in ({@link #values}), and read back into its choices when
 * the player changes them in the panel ({@link #apply}). Lists (blacklist, tick changes, each boat) stay sidebar-only.
 * The Avoid rule without data (known bugs) is left out, as the sidebar greys it out. Pure.
 */
public final class TransportConfig
{
	static final Map<TransportSettings.Category, String> TYPE_KEYS = new EnumMap<>(TransportSettings.Category.class);
	static final Map<TransportSettings.Avoid, String> AVOID_KEYS = new EnumMap<>(TransportSettings.Avoid.class);
	static final String BANK_DETOUR_KEY = "bankDetour";
	static final String BANK_TICKS_KEY = "bankTicks";
	static final String SHIPWRIGHTS_KEY = "shipwrights";
	static final String KEEP_BOAT_KEY = "keepBoat";
	static final String BOARD_TICKS_KEY = "boardTicks";
	/** The coin limit (Avoid). */
	static final String COIN_CAP_KEY = "coinCap";
	static final String COIN_AMOUNT_KEY = "coinCapAmount";
	/** Every config key this class mirrors. */
	public static final Set<String> KEYS;

	static
	{
		TYPE_KEYS.put(TransportSettings.Category.TELEPORT_SPELLS, "typeTeleportSpells");
		TYPE_KEYS.put(TransportSettings.Category.TELEPORT_ITEMS, "typeTeleportItems");
		TYPE_KEYS.put(TransportSettings.Category.HOUSE, "typeHouse");
		TYPE_KEYS.put(TransportSettings.Category.STAIRS_LADDERS, "typeStairsLadders");
		TYPE_KEYS.put(TransportSettings.Category.SHORTCUTS, "typeShortcuts");
		TYPE_KEYS.put(TransportSettings.Category.DOORS, "typeDoors");
		TYPE_KEYS.put(TransportSettings.Category.BOATS, "typeBoats");
		AVOID_KEYS.put(TransportSettings.Avoid.COST, "avoidCost");
		AVOID_KEYS.put(TransportSettings.Avoid.RUNES, "avoidRunes");
		AVOID_KEYS.put(TransportSettings.Avoid.COOLDOWN, "avoidCooldown");
		AVOID_KEYS.put(TransportSettings.Avoid.WILDERNESS, "avoidWilderness");
		Map<String, String> all = values(TransportSettings.defaults());
		KEYS = Collections.unmodifiableSet(all.keySet());
	}

	private TransportConfig()
	{
	}

	/** The config panel's values for these choices, key -> value as RuneLite stores it ("true", "30"). */
	public static Map<String, String> values(TransportSettings s)
	{
		Map<String, String> v = new LinkedHashMap<>();
		TYPE_KEYS.forEach((c, key) -> v.put(key, String.valueOf(s.isEnabled(c))));
		AVOID_KEYS.forEach((a, key) -> v.put(key, String.valueOf(s.avoids(a))));
		v.put(BANK_DETOUR_KEY, String.valueOf(s.isBankDetour()));
		v.put(BANK_TICKS_KEY, String.valueOf(s.getBankTicks()));
		v.put(SHIPWRIGHTS_KEY, String.valueOf(s.isShipwrights()));
		v.put(KEEP_BOAT_KEY, String.valueOf(s.isKeepBoat()));
		v.put(BOARD_TICKS_KEY, String.valueOf(s.getBoardTicks()));
		v.put(COIN_CAP_KEY, String.valueOf(s.isCoinCapOn()));
		v.put(COIN_AMOUNT_KEY, String.valueOf(s.getCoinCap()));
		return v;
	}

	/**
	 * These choices with the config panel's values. A value not set (the panel's "Reset") is the default; one that
	 * can't be read keeps the choice as it is.
	 *
	 * @param config the stored value for a key (null: not set)
	 */
	public static TransportSettings apply(TransportSettings s, Function<String, String> config)
	{
		Map<String, String> defaults = values(TransportSettings.defaults());
		Function<String, String> read = key ->
		{
			String v = config.apply(key);
			return v == null ? defaults.get(key) : v;
		};
		TransportSettings out = s;
		for (Map.Entry<TransportSettings.Category, String> e : TYPE_KEYS.entrySet())
		{
			Boolean on = bool(read.apply(e.getValue()));
			if (on != null && on != out.isEnabled(e.getKey()))
			{
				out = out.withCategory(e.getKey(), on);
			}
		}
		for (Map.Entry<TransportSettings.Avoid, String> e : AVOID_KEYS.entrySet())
		{
			Boolean on = bool(read.apply(e.getValue()));
			if (on != null && on != out.avoids(e.getKey()))
			{
				out = out.withAvoid(e.getKey(), on);
			}
		}
		Boolean detour = bool(read.apply(BANK_DETOUR_KEY));
		if (detour != null && detour != out.isBankDetour())
		{
			out = out.withBankDetour(detour);
		}
		Integer bank = number(read.apply(BANK_TICKS_KEY));
		if (bank != null && bank != out.getBankTicks())
		{
			out = out.withBankTicks(bank);
		}
		Boolean ship = bool(read.apply(SHIPWRIGHTS_KEY));
		if (ship != null && ship != out.isShipwrights())
		{
			out = out.withShipwrights(ship);
		}
		Boolean keep = bool(read.apply(KEEP_BOAT_KEY));
		if (keep != null && keep != out.isKeepBoat())
		{
			out = out.withKeepBoat(keep);
		}
		Integer board = number(read.apply(BOARD_TICKS_KEY));
		if (board != null && board != out.getBoardTicks())
		{
			out = out.withBoardTicks(board);
		}
		Boolean capOn = bool(read.apply(COIN_CAP_KEY));
		if (capOn != null && capOn != out.isCoinCapOn())
		{
			out = out.withCoinCapOn(capOn);
		}
		Integer coins = number(read.apply(COIN_AMOUNT_KEY));
		if (coins != null && coins != out.getCoinCap())
		{
			out = out.withCoinCap(coins);
		}
		return out;
	}

	private static Boolean bool(String v)
	{
		return "true".equals(v) ? Boolean.TRUE : "false".equals(v) ? Boolean.FALSE : null;
	}

	private static Integer number(String v)
	{
		try
		{
			return v == null ? null : Integer.valueOf(v.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}

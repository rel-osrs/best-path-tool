package com.bestpath;

import com.bestpath.clue.DetectedClue;
import com.bestpath.clue.StashChart;
import com.bestpath.clue.StashMemory;
import com.bestpath.clue.StashSearch;
import com.bestpath.clue.StashVars;
import com.bestpath.core.data.ClueData;
import com.bestpath.core.path.Tiles;
import com.bestpath.player.DelayedSave;
import com.bestpath.route.RouteRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;

/**
 * The player's STASH units, filled or empty, remembered per account ({@link StashMemory}), from three sources, the
 * newest winning:
 * <ol>
 * <li>the game's clue helper ({@link StashVars#HELPER_CLUE}, {@link StashVars#FILLED}): read one tick after either
 * changed (they may arrive in either order);</li>
 * <li>the STASH chart: the arguments of its scripts when it opens or redraws ({@link StashChart}; public
 * {@code ScriptPreFired}), the units' objects mapped to their built varbits with the public object definitions;</li>
 * <li>"Search" on a built STASH unit followed by a change of what the player carries and wears ({@link StashSearch}).
 * Items taken out while carrying a clue step that uses that unit are remembered as to be put back once that step is
 * over: the next clue route goes to the unit first, or, when the clue is done, a route to the unit only. "Skip" on
 * that card forgets it for good; the config switch "Remind to put STASH items back" turns both off.</li>
 * </ol>
 * Saved like the bank ({@link DelayedSave}: written once changes settle, to the account they belong to). CLIENT
 * THREAD ONLY.
 */
@Slf4j
final class StashTracker
{
	static final String FILLED_KEY = "stashFilled";
	static final String TAKEN_KEY = "stashTaken";
	/** The id of the "put the items back" card (its Skip button): this plus the unit's varbit. */
	static final String STOP_PREFIX = "stash-return:";

	private final Client client;
	private final ProfileStore store;
	private final StashMemory memory = new StashMemory();
	private final DelayedSave filledSave = new DelayedSave();
	private final DelayedSave takenSave = new DelayedSave();
	private final StashSearch search = new StashSearch();
	/** Unit (built varbit) -> a clue step with that unit (its tile and place); from the clue data. */
	private Map<Integer, ClueData.Step> units = Map.of();
	/** The clue steps (the clue helper names one by its cache row); null until loaded. */
	private ClueData clueData;
	/** STASH object id -> unit (built varbit), from the object definitions (read once per object). */
	private final Map<Integer, Integer> objectUnits = new HashMap<>();
	/** The clue helper's vars changed: read them on the next tick. */
	private boolean helperChanged;
	/** What the player carries or wears changed while a search click waits. */
	private boolean itemsChanged;

	StashTracker(Client client, ProfileStore store)
	{
		this.client = client;
		this.store = store;
	}

	/** The clue data loaded: which units exist and where. */
	void setClues(ClueData data)
	{
		clueData = data;
		Map<Integer, ClueData.Step> m = new HashMap<>();
		if (data != null)
		{
			for (ClueData.Step s : data.getSteps())
			{
				if (s.getStashVarbit() > 0 && s.getStash() != -1)
				{
					m.putIfAbsent(s.getStashVarbit(), s);
				}
			}
		}
		units = m;
		helperChanged = true;
	}

	/** Startup and account switch: this account's units. Anything waiting for the previous one is written first. */
	void loadProfile()
	{
		flushSaves();
		String f = store.read(FILLED_KEY);
		String t = store.read(TAKEN_KEY);
		memory.load(f, t);
		filledSave.loaded(f);
		takenSave.loaded(t);
		search.cancel();
		helperChanged = true;
	}

	/** Writes what is still waiting, each to its own account (account switch, plugin stop). */
	void flushSaves()
	{
		write(filledSave, FILLED_KEY, false);
		write(takenSave, TAKEN_KEY, false);
	}

	private void write(DelayedSave s, String key, boolean toLoggedIn)
	{
		DelayedSave.Write w = s.pending();
		if (w != null && (w.getProfile() != null || toLoggedIn) && store.write(w.getProfile(), key, w.getText()))
		{
			s.written();
		}
	}

	/** Remembered: filled (true), empty (false), or null. */
	Boolean remembered(int unit)
	{
		return memory.filled(unit);
	}

	/**
	 * Each game tick.
	 *
	 * @param player  where the player stands, or null
	 * @param current the carried clue (the take-out is remembered for it when it uses that unit)
	 * @return true if anything remembered changed (the clue card and the put-back stop may change)
	 */
	boolean onTick(int tick, WorldPoint player, DetectedClue current)
	{
		boolean changed = false;
		if (helperChanged && clueData != null)
		{
			helperChanged = false;
			changed |= readHelper();
		}
		if (search.isWaiting())
		{
			int at = player == null ? -1 : Tiles.pack(player.getX(), player.getY(), player.getPlane());
			StashSearch.Result r = itemsChanged || tick % 5 == 0 ? search.onItems(carried(), at, tick) : null;
			itemsChanged = false;
			if (r != null)
			{
				log.debug("STASH search: {}", r);
				changed |= r.isDeposited() ? memory.deposited(r.getUnit())
					: memory.takenOut(r.getUnit(), usesUnit(current, r.getUnit()) ? current.identity() : null, r.getItems());
			}
		}
		if (changed)
		{
			saveLater(tick);
		}
		if (filledSave.due(tick))
		{
			write(filledSave, FILLED_KEY, true);
		}
		if (takenSave.due(tick))
		{
			write(takenSave, TAKEN_KEY, true);
		}
		return changed;
	}

	private void saveLater(int tick)
	{
		String profile = store.profileKey();
		filledSave.changed(profile, memory.encodeFilled(), tick);
		takenSave.changed(profile, memory.encodeTaken(), tick);
	}

	/** True if the detected clue is a step that uses this STASH unit. */
	private static boolean usesUnit(DetectedClue d, int unit)
	{
		if (d == null || d.getKind() != DetectedClue.Kind.STEP)
		{
			return false;
		}
		for (ClueData.Step s : d.getSteps())
		{
			if (s.getStashVarbit() == unit)
			{
				return true;
			}
		}
		return false;
	}

	/** The clue helper's unit: its filled bit, for the step it shows. */
	private boolean readHelper()
	{
		int row = client.getVarpValue(StashVars.HELPER_CLUE);
		ClueData.Step shown = row > 0 && clueData != null ? clueData.byRow(row) : null;
		if (shown == null || shown.getStashVarbit() <= 0)
		{
			return false;
		}
		return memory.set(shown.getStashVarbit(), client.getVarbitValue(StashVars.FILLED) == 1);
	}

	/** A var changed: the clue helper's are read on the next tick. */
	void onVarbitChanged(VarbitChanged e)
	{
		if (e.getVarbitId() == StashVars.FILLED || (e.getVarbitId() == -1 && e.getVarpId() == StashVars.HELPER_CLUE))
		{
			helperChanged = true;
		}
	}

	/** What the player carries or wears changed. */
	void onItemsChanged(int containerId)
	{
		if (containerId == InventoryID.INV || containerId == InventoryID.WORN)
		{
			itemsChanged = true;
		}
	}

	/**
	 * The STASH chart opened or redrew: every unit it tells.
	 *
	 * @return true if anything remembered changed
	 */
	boolean onScriptPreFired(ScriptPreFired e)
	{
		if (e.getScriptId() != StashChart.OPEN_SCRIPT && e.getScriptId() != StashChart.UPDATE_SCRIPT)
		{
			return false;
		}
		int[] args = e.getScriptEvent() == null ? null : StashChart.args(e.getScriptEvent().getArguments());
		if (args == null)
		{
			return false;
		}
		ItemContainer inv = client.getItemContainer(InventoryID.HH_INV);
		Map<Integer, Boolean> byObject = StashChart.read(args, enumInts(StashChart.TIER_ENUM),
			enumInts(StashChart.INV_ENUM), enumInts(StashChart.BITS_ENUM), client::getVarpValue,
			inv == null ? null : slot ->
			{
				Item it = inv.getItem(slot);
				return it == null || it.getId() <= 0 ? -1 : it.getId();
			});
		Map<Integer, Boolean> byUnit = new HashMap<>();
		for (Map.Entry<Integer, Boolean> en : byObject.entrySet())
		{
			int unit = unitOf(en.getKey());
			if (unit > 0)
			{
				byUnit.put(unit, en.getValue());
			}
		}
		boolean changed = memory.fromChart(byUnit);
		log.debug("STASH chart read: {} units{}", byUnit.size(), changed ? " (changed)" : "");
		if (changed)
		{
			saveLater(client.getTickCount());
		}
		return changed;
	}

	/** An enum's int values by key (public {@code Client#getEnum}). */
	private Map<Integer, Integer> enumInts(int id)
	{
		Map<Integer, Integer> out = new HashMap<>();
		EnumComposition en = client.getEnum(id);
		if (en != null)
		{
			int[] keys = en.getKeys();
			int[] vals = en.getIntVals();
			for (int i = 0; keys != null && vals != null && i < keys.length && i < vals.length; i++)
			{
				out.put(keys[i], vals[i]);
			}
		}
		return out;
	}

	/** A STASH object's unit: its built varbit (the object's own form varbit, for every unit), or -1. */
	private int unitOf(int objectId)
	{
		Integer u = objectUnits.get(objectId);
		if (u == null)
		{
			ObjectComposition c = client.getObjectDefinition(objectId);
			u = c == null ? -1 : c.getVarbitId();
			objectUnits.put(objectId, u);
		}
		return u;
	}

	/** A click: "Search" on a built STASH unit starts watching for a deposit or take-out; any other click stops it. */
	void onMenuOptionClicked(MenuOptionClicked e)
	{
		MenuAction a = e.getMenuAction();
		boolean objectOp = a == MenuAction.GAME_OBJECT_FIRST_OPTION || a == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| a == MenuAction.GAME_OBJECT_THIRD_OPTION || a == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| a == MenuAction.GAME_OBJECT_FIFTH_OPTION;
		if (objectOp && "Search".equalsIgnoreCase(e.getMenuOption()))
		{
			int unit = unitOf(e.getId());
			ClueData.Step s = units.get(unit);
			if (s != null && client.getVarbitValue(unit) == 1)
			{
				search.searched(unit, s.getStash(), carried(), client.getTickCount());
				itemsChanged = false;
				return;
			}
		}
		search.cancel();
	}

	/** What the player carries and wears: item id -> count. */
	private Map<Integer, Integer> carried()
	{
		Map<Integer, Integer> m = new HashMap<>();
		add(m, client.getItemContainer(InventoryID.INV));
		add(m, client.getItemContainer(InventoryID.WORN));
		return m;
	}

	private static void add(Map<Integer, Integer> m, ItemContainer c)
	{
		if (c != null)
		{
			for (Item it : c.getItems())
			{
				if (it.getId() > 0 && it.getQuantity() > 0)
				{
					m.merge(it.getId(), it.getQuantity(), Integer::sum);
				}
			}
		}
	}

	/**
	 * The items to put back now, or null: the reminder is switched on, they were taken out for a clue step that is
	 * over (another step, or no clue carried) and the unit's place is known.
	 *
	 * @param remind the config switch "Remind to put STASH items back" ({@link BestPathConfig#remindStashPutBack})
	 */
	StashMemory.Taken dueTaken(DetectedClue current, boolean remind)
	{
		StashMemory.Taken t = memory.getTaken();
		return StashMemory.due(t, remind, t != null && units.containsKey(t.getUnit()), current);
	}

	/** The route's first stop for these items: the unit's tile, "Put the items back in the STASH unit (place)". */
	RouteRequest.Stop stop(StashMemory.Taken t, IntFunction<String> itemName)
	{
		ClueData.Step s = t == null ? null : units.get(t.getUnit());
		if (s == null)
		{
			return null;
		}
		List<String> names = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : t.getItems().entrySet())
		{
			String n = itemName.apply(e.getKey());
			names.add((n == null ? "Item " + e.getKey() : n) + (e.getValue() > 1 ? " x" + e.getValue() : ""));
		}
		String place = s.getStashPlace().isEmpty() ? "" : " (" + s.getStashPlace() + ")";
		return new RouteRequest.Stop(STOP_PREFIX + t.getUnit(), s.getStash(), "Put the items back in the STASH unit" + place,
			List.of("Items: " + String.join(", ", names), "Search the STASH unit with them in your inventory."));
	}

	/**
	 * "Skip" on the put-back card (its id: {@link #STOP_PREFIX} + unit): that reminder is forgotten for good, saved for
	 * this account. A later take-out for a clue step starts a new one.
	 *
	 * @return true if a reminder was forgotten
	 */
	boolean skipTaken(String stopId, int tick)
	{
		int unit = unitOfStop(stopId);
		if (unit <= 0 || !memory.forgetTaken(unit))
		{
			return false;
		}
		saveLater(tick);
		return true;
	}

	/** The unit of a put-back card's id ({@link #STOP_PREFIX} + unit), or -1. */
	static int unitOfStop(String stopId)
	{
		if (stopId == null || !stopId.startsWith(STOP_PREFIX))
		{
			return -1;
		}
		try
		{
			return Integer.parseInt(stopId.substring(STOP_PREFIX.length()).trim());
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}

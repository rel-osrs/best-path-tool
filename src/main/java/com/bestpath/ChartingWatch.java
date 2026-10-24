package com.bestpath;

import com.bestpath.core.data.ChartingTasks;
import com.bestpath.route.ChartingProgress;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;

/**
 * Follows the player's sea charting. Event-driven: the tasks' varbits (and where the charting tools, drinks and weather
 * stations are) are read again on the next game tick, and only after one of them, the quest Current Affairs, the
 * Sailing level, the inventory, worn items, bank or the login changed, or the waters the player's boats may enter (the
 * charting hazards). Client thread only.
 */
final class ChartingWatch
{
	private final Client client;
	/** The player's bank as last seen (item id to count); null before it was ever seen. */
	private final Supplier<Map<Integer, Integer>> bank;
	private ChartingTasks tasks = ChartingTasks.NONE;
	private int lowVarbit = -1;
	private int highVarbit = -1;
	private boolean dirty = true;
	private ChartingProgress shown;
	/** The sea areas the player's boats may enter together ({@link ChartingProgress#ANY_BOAT}: not known). */
	private final IntSupplier boatAreas;
	private int lastBoatAreas = ChartingProgress.ANY_BOAT;

	ChartingWatch(Client client, Supplier<Map<Integer, Integer>> bank,
		IntSupplier boatAreas)
	{
		this.client = client;
		this.bank = bank;
		this.boatAreas = boatAreas;
	}

	/** The inventory, worn items or bank changed: where the charting tools are (spyglass, current duck, crowbar). */
	void onItemsChanged(int containerId)
	{
		if (containerId == InventoryID.INV || containerId == InventoryID.WORN || containerId == InventoryID.BANK)
		{
			dirty = true;
		}
	}

	/** Whether the player carries or wears the item (client thread). */
	boolean carries(int itemId)
	{
		for (int container : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer c = client.getItemContainer(container);
			if (c != null && c.contains(itemId))
			{
				return true;
			}
		}
		return false;
	}

	/** The tasks of the map data (once it has loaded). */
	void setTasks(ChartingTasks t)
	{
		tasks = t;
		int[] range = t.varbitRange();
		lowVarbit = range[0];
		highVarbit = range[1];
		dirty = true;
	}

	void onVarbitChanged(VarbitChanged e)
	{
		int v = e.getVarbitId();
		if (v != -1 && (v >= lowVarbit && v <= highVarbit || v == VarbitID.CURRENT_AFFAIRS))
		{
			dirty = true;
		}
	}

	void onStatChanged(StatChanged e)
	{
		if (e.getSkill() == Skill.SAILING)
		{
			dirty = true;
		}
	}

	/** Logged in or out, or another account. */
	void onLoginChange()
	{
		dirty = true;
	}

	/** Each game tick: the progress when what it shows changed since last time, else null. */
	ChartingProgress onTick()
	{
		int areas = client.getGameState() == GameState.LOGGED_IN ? boatAreas.getAsInt() : ChartingProgress.ANY_BOAT;
		if (areas != lastBoatAreas)
		{
			lastBoatAreas = areas;
			dirty = true;
		}
		if (!dirty)
		{
			return null;
		}
		dirty = false;
		Map<Integer, Integer> b = bank.get();
		ChartingProgress p = client.getGameState() == GameState.LOGGED_IN
			? ChartingProgress.read(tasks, client::getVarbitValue, client.getRealSkillLevel(Skill.SAILING), this::carries,
				id -> b != null && b.getOrDefault(id, 0) > 0, areas)
			: ChartingProgress.loggedOut(tasks);
		if (p.equals(shown))
		{
			return null;
		}
		shown = p;
		return p;
	}
}

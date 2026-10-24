package com.bestpath;

import com.bestpath.player.AccountSettings;
import com.bestpath.player.CanoeAxe;
import com.bestpath.player.HouseFurniture;
import com.bestpath.player.SpiritTreePatches;
import com.bestpath.player.UnlockMemory;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * Unlocks detected instead of asked, remembered per account ({@link UnlockMemory}). CLIENT THREAD ONLY.
 *
 * <ul>
 * <li><b>Collection log</b> ("collection log ..." requirements): the items of a collection log page the player opens
 * (after the game's own script that draws the page, {@link ScriptID#COLLECTION_DRAW_LIST}: an obtained item is drawn
 * fully opaque, the way RuneLite's own chat commands plugin reads the pet list), and the game's "New item added to
 * your collection log" message. Only items some requirement names are kept. Pages opened inside a player-owned house
 * are skipped: the adventure log there can show another player's log.</li>
 * <li><b>Spirit trees</b> ("spirit tree ..." requirements): the patch's farming varbit, read when the player enters
 * the patch's map region and when that varbit changes there ({@link SpiritTreePatches}). A patch not seen yet comes
 * from RuneLite's Time Tracking plugin's own record of it, when it has one.</li>
 * <li><b>Canoe axe</b> ("canoe axe stored" requirements): the canoe station NPC's words when it
 * takes or hands back the axe, and a canoe chopped down or shaped with no axe carried or worn ({@link CanoeAxe}).</li>
 * </ul>
 */
@Slf4j
final class UnlockWatch
{
	static final String LOG_KEY = "collectionLog";
	static final String TREES_KEY = "spiritTrees";
	static final String CANOE_AXE_KEY = "canoeAxe";
	/** How long (ticks) after "Chop-down" / "Shape-Canoe" on a station its change counts (time to walk there). */
	private static final int AXE_CLICK_TICKS = 50;

	private final Client client;
	private final ProfileStore store;
	private final UnlockMemory memory;
	private final CanoeAxe canoeAxe;
	/** The game tick of the last click on a canoe station that needs an axe (-1: none). */
	private int axeClickTick = -1;
	/** The map region the player was in at the last tick (-1: not known). */
	private int lastRegion = -1;
	/** The spirit tree patch of the player's region must be read again (entered the region, or its varbit changed). */
	private boolean treeDirty = true;

	UnlockWatch(Client client, ProfileStore store)
	{
		this.client = client;
		this.store = store;
		this.memory = new UnlockMemory(profileStore(store, LOG_KEY), profileStore(store, TREES_KEY));
		this.canoeAxe = new CanoeAxe(profileStore(store, CANOE_AXE_KEY));
	}

	private static AccountSettings.Store profileStore(ProfileStore store, String key)
	{
		return new AccountSettings.Store()
		{
			@Override
			public String profileKey()
			{
				return store.profileKey();
			}

			@Override
			public String read()
			{
				return store.read(key);
			}

			@Override
			public boolean write(String text)
			{
				return store.write(null, key, text);
			}
		};
	}

	/** This account's remembered unlocks (startup, account switch). */
	void load()
	{
		memory.load();
		canoeAxe.load();
		lastRegion = -1;
		treeDirty = true;
		axeClickTick = -1;
	}

	/** Whether an axe was last seen stored at the canoe stations on this account; null = not known yet. */
	Boolean canoeAxeStored()
	{
		return canoeAxe.getStored();
	}

	/** A menu click: "Chop-down" / "Shape-Canoe" on a canoe station is remembered for {@link #onCanoeStation}. */
	void onMenuClick(String option, String target)
	{
		if (CanoeAxe.needsAxe(option, Text.removeTags(target == null ? "" : target)))
		{
			axeClickTick = client.getTickCount();
		}
	}

	/**
	 * A var changed: a canoe station chopped down or shaped soon after the player clicked it, with no axe carried or
	 * worn, means an axe is stored (the game used the stored one).
	 *
	 * @param axeIds the "axe" item group's ids (null: data not loaded yet)
	 * @return true if the stored axe became known (routes may change)
	 */
	boolean onCanoeStation(VarbitChanged e, int[] axeIds)
	{
		if (axeIds == null || e.getVarbitId() == -1 || !CanoeAxe.STATION_VARBITS.contains(e.getVarbitId())
			|| !CanoeAxe.axeUsed(e.getValue()) || axeClickTick < 0
			|| client.getTickCount() - axeClickTick > AXE_CLICK_TICKS)
		{
			return false;
		}
		if (holds(InventoryID.INV, axeIds) || holds(InventoryID.WORN, axeIds))
		{
			return false; // the carried axe was used: says nothing about the stored one
		}
		boolean changed = canoeAxe.see(true);
		if (changed)
		{
			log.debug("Canoe station used with no axe carried: an axe is stored");
		}
		return changed;
	}

	private boolean holds(int containerId, int[] ids)
	{
		ItemContainer c = client.getItemContainer(containerId);
		if (c == null)
		{
			return false;
		}
		for (Item i : c.getItems())
		{
			for (int id : ids)
			{
				if (i.getId() == id)
				{
					return true;
				}
			}
		}
		return false;
	}

	/** The collection log items seen on this account (only the ones some requirement names). */
	Set<Integer> collectionLog()
	{
		return memory.getLog();
	}

	/**
	 * The places whose spirit tree is grown: as this plugin last saw it, else as RuneLite's Time Tracking plugin last
	 * saw it (its record for this account), else not grown.
	 */
	Set<String> grownSpiritTrees()
	{
		Map<String, Boolean> seen = memory.getTrees();
		Set<String> out = new TreeSet<>();
		for (SpiritTreePatches.Patch p : SpiritTreePatches.PATCHES)
		{
			Boolean grown = seen.get(p.getPlace());
			if (grown == null)
			{
				grown = SpiritTreePatches.fromTimeTracking(
					store.readOther(SpiritTreePatches.TIME_TRACKING_GROUP, p.getTimeTrackingKey()));
			}
			if (Boolean.TRUE.equals(grown))
			{
				out.add(p.getPlace());
			}
		}
		return out;
	}

	/** A var changed: a spirit tree patch varbit is read on the next tick (if the player is at that patch). */
	void onVarbitChanged(VarbitChanged e)
	{
		if (e.getVarbitId() != -1 && SpiritTreePatches.isPatchVarbit(e.getVarbitId()))
		{
			treeDirty = true;
		}
	}

	/**
	 * Each game tick with the player's tile (null: not logged in): reads the spirit tree patch of the player's region
	 * when the player just entered it or its varbit changed. One int comparison otherwise.
	 *
	 * @return true if a spirit tree's state changed (routes may change)
	 */
	boolean onTick(WorldPoint w)
	{
		int region = w == null ? -1 : w.getRegionID();
		if (region != lastRegion)
		{
			lastRegion = region;
			treeDirty = true;
		}
		if (!treeDirty || region == -1)
		{
			return false;
		}
		treeDirty = false;
		SpiritTreePatches.Patch p = SpiritTreePatches.inRegion(region);
		if (p == null)
		{
			return false;
		}
		int value = client.getVarbitValue(p.getVarbit());
		boolean changed = memory.seeTree(p.getPlace(), SpiritTreePatches.grown(value));
		if (changed)
		{
			log.debug("Spirit tree at {}: patch state {} ({})", p.getPlace(), value,
				SpiritTreePatches.grown(value) ? "grown" : "not grown");
		}
		return changed;
	}

	/**
	 * A chat message: the game's "New item added to your collection log: X" for an item some requirement names, or a
	 * canoe station NPC taking or handing back the axe.
	 *
	 * @param mentioned the item ids the requirements name ({@link com.bestpath.player.RequirementInputs#getCollectionLog})
	 * @return true if an item was new to the remembered log, or the stored axe changed
	 */
	boolean onChatMessage(ChatMessage e, Set<Integer> mentioned)
	{
		if (e.getType() == ChatMessageType.DIALOG)
		{
			Boolean stored = CanoeAxe.fromDialogue(Text.removeTags(e.getMessage()));
			boolean changed = stored != null && canoeAxe.see(stored);
			if (changed)
			{
				log.debug("Canoe axe {}", stored ? "stored" : "taken back");
			}
			return changed;
		}
		if (e.getType() != ChatMessageType.GAMEMESSAGE || mentioned.isEmpty())
		{
			return false;
		}
		String name = UnlockMemory.newLogItem(Text.removeTags(e.getMessage()));
		if (name == null)
		{
			return false;
		}
		Set<Integer> found = new HashSet<>();
		for (int id : mentioned)
		{
			ItemComposition c = client.getItemDefinition(id);
			if (c != null && name.equalsIgnoreCase(c.getName()))
			{
				found.add(id);
			}
		}
		return !found.isEmpty() && memory.addToLog(found);
	}

	/**
	 * A client script finished: after the collection log draws a page, its obtained items (drawn fully opaque) that
	 * some requirement names are remembered. Not inside a house (another player's adventure log).
	 *
	 * @param position the player's tile, or null
	 * @return true if an item was new to the remembered log
	 */
	boolean onScriptPostFired(ScriptPostFired e, Set<Integer> mentioned, WorldPoint position)
	{
		if (e.getScriptId() != ScriptID.COLLECTION_DRAW_LIST || mentioned.isEmpty() || position == null
			|| HouseFurniture.isHouseTemplateTile(position.getX(), position.getY()))
		{
			return false;
		}
		Widget items = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		Widget[] children = items == null ? null : items.getChildren();
		if (children == null)
		{
			return false;
		}
		Set<Integer> found = new HashSet<>();
		for (Widget child : children)
		{
			if (child != null && child.getOpacity() == 0 && mentioned.contains(child.getItemId()))
			{
				found.add(child.getItemId());
			}
		}
		boolean changed = !found.isEmpty() && memory.addToLog(found);
		if (changed)
		{
			log.debug("Collection log items seen: {}", found);
		}
		return changed;
	}
}

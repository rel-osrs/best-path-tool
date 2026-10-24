package com.bestpath;

import com.bestpath.player.AccountSettings;
import com.bestpath.player.Debouncer;
import com.bestpath.player.HouseFurniture;
import com.bestpath.player.HouseMemory;
import com.bestpath.player.OwnHouseWatch;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;

/**
 * The house scan: while the player is inside their own house ({@link OwnHouseWatch}), the furniture in the scene says
 * which house things are built ({@link HouseFurniture}); the result is remembered per account ({@link HouseMemory})
 * and goes into every player snapshot. Client thread only.
 *
 * <p>Event-driven (no polling): the furniture the table knows is tracked from the object spawn / despawn events;
 * once per house visit, when the scene has settled, the whole scene is read once (in case a piece spawned before the
 * visit was known to be a house); after that only spawn / despawn events and the nexus slot varbits make it look
 * again. A scan runs only when nothing new happened for 2 ticks (at most 10 ticks after the first change).
 */
@Slf4j
final class HouseScanner
{
	static final String HOUSE_KEY = "houseThings";
	/** When the own house was last scanned (epoch milliseconds; the Settings tab's "House (POH)" section). */
	static final String HOUSE_TIME_KEY = "houseScanned";
	/** The portal nexus list's keyboard keys. */
	static final String NEXUS_KEYS_KEY = "houseNexusKeys";

	private final Client client;
	private final HouseFurniture table;
	private final HouseMemory memory;
	/** The nexus list's keys as last scanned, one "text=key" per line (the account profile's {@value #NEXUS_KEYS_KEY}). */
	private final HouseMemory keys;
	private final OwnHouseWatch watch = new OwnHouseWatch();
	private final Debouncer settle = new Debouncer(2, 10);
	/** Known furniture in the scene now: object hash -> object id. */
	private final Map<Long, Integer> present = new HashMap<>();
	/** The scene of this house visit was read once. */
	private boolean sceneRead;
	private final Set<Integer> nexusSlots = new HashSet<>();

	/** Which built object is which house furniture (also used to glow the furniture the next step uses). */
	HouseFurniture table()
	{
		return table;
	}

	HouseScanner(Client client, ProfileStore store, HouseFurniture table)
	{
		this.client = client;
		this.table = table;
		this.memory = new HouseMemory(profileStore(store, HOUSE_KEY), profileStore(store, HOUSE_TIME_KEY));
		this.keys = new HouseMemory(profileStore(store, NEXUS_KEYS_KEY));
		for (int v : HouseFurniture.NEXUS_SLOTS)
		{
			nexusSlots.add(v);
		}
	}

	/** One key of the logged-in account's profile. */
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

	/** This account's remembered house things (startup, account switch). */
	void load()
	{
		memory.load();
		keys.load();
	}

	/**
	 * The keyboard key of each line of the own house's portal nexus list as last scanned ("Senntisten" -> "M"; empty
	 * = no nexus, or not scanned yet).
	 */
	Map<String, String> getNexusKeys()
	{
		Map<String, String> out = new HashMap<>();
		for (String line : keys.get())
		{
			int eq = line.lastIndexOf('=');
			if (eq > 0 && eq < line.length() - 1)
			{
				out.put(line.substring(0, eq), line.substring(eq + 1));
			}
		}
		return out;
	}

	/** "Senntisten=M", one per key (saved like the house things). */
	static Set<String> encodeKeys(Map<String, String> keys)
	{
		Set<String> out = new HashSet<>();
		keys.forEach((text, key) -> out.add(text + "=" + key));
		return out;
	}

	/** What is built in the player's house as last scanned (immutable; empty = not known / nothing). */
	Set<String> getThings()
	{
		return memory.get();
	}

	/** When the own house was last scanned (epoch milliseconds), 0 = not looked at yet on this account. */
	long getScannedAt()
	{
		return memory.getScannedAt();
	}

	/** A menu click: its option, target and item (the menu entry's item id, -1 when not an item). */
	void onMenuClick(String option, String target, int itemId)
	{
		watch.onMenuClick(OwnHouseWatch.classify(option, target, itemId), client.getTickCount());
	}

	void onGameState(GameState s)
	{
		switch (s)
		{
			case LOADING:
				present.clear(); // the new scene's objects spawn again
				break;
			case LOGGED_IN:
			{
				boolean house = isHouseScene();
				boolean wasOwn = watch.isInOwnHouse();
				watch.onSceneLoaded(house, client.getTickCount());
				sceneRead = false;
				if (house)
				{
					settle.mark(client.getTickCount());
				}
				if (watch.isInOwnHouse() != wasOwn)
				{
					log.debug("House: {}", watch.isInOwnHouse() ? "in the player's own house" : "left the own house");
				}
				break;
			}
			case LOGIN_SCREEN:
			case HOPPING:
			case CONNECTION_LOST:
				watch.reset();
				present.clear();
				settle.clear();
				break;
			default:
				break;
		}
	}

	/** An object spawned or despawned (any of the four kinds). */
	void onObject(TileObject o, boolean spawned)
	{
		if (o == null || !table.isWatched(o.getId()))
		{
			return;
		}
		if (spawned)
		{
			present.put(o.getHash(), o.getId());
		}
		else
		{
			present.remove(o.getHash());
		}
		if (watch.isInHouse())
		{
			settle.mark(client.getTickCount());
		}
	}

	/**
	 * A nexus slot, which nexus is built (POH_NEXUS_ID: how many slots its list shows; it may come later than the
	 * house's scene) or the building-mode setting changed while in a house: look again.
	 */
	void onVarbitChanged(VarbitChanged e)
	{
		int v = e.getVarbitId();
		if (watch.isInHouse() && (nexusSlots.contains(v) || v == VarbitID.POH_NEXUS_ID || v == VarbitID.POH_BUILDING_MODE))
		{
			settle.mark(client.getTickCount());
		}
	}

	/**
	 * Each game tick: scans when the house scene settled.
	 *
	 * @return true when what is built changed (saved; routes may change)
	 */
	boolean onTick(int tick)
	{
		if (!settle.ready(tick))
		{
			return false;
		}
		settle.clear();
		if (!watch.isInHouse() || client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}
		if (!watch.isInOwnHouse())
		{
			if (client.getVarbitValue(VarbitID.POH_BUILDING_MODE) != 1 || !sceneHasBuildSpaces())
			{
				return false; // maybe a friend's house: never scanned
			}
			watch.onBuildModeSeen();
			log.debug("House: building mode with build spaces: the player's own house");
		}
		if (!sceneRead)
		{
			readScene();
			sceneRead = true;
			memory.scanned(System.currentTimeMillis()); // once per scene of the own house: "Last looked" in Settings
		}
		int[] slots = new int[HouseFurniture.NEXUS_SLOTS.length];
		for (int i = 0; i < slots.length; i++)
		{
			slots[i] = client.getVarbitValue(HouseFurniture.NEXUS_SLOTS[i]);
		}
		Set<String> found = table.thingsOf(present.values(), slots);
		boolean changed = memory.update(found);
		if (changed)
		{
			log.debug("House scan: {}", found);
		}
		// the nexus list's keyboard keys ("Nexus: Senntisten (M)"): they follow this player's slots, nexus, Magic
		// level and the alternates' unlocks, so they are worked out here
		boolean nexus = false;
		for (String t : found)
		{
			nexus |= t.startsWith(HouseFurniture.NEXUS_PREFIX);
		}
		int nexusId = client.getVarbitValue(VarbitID.POH_NEXUS_ID);
		Map<String, String> k = nexus ? table.nexusKeys(slots, nexusId, client.getRealSkillLevel(Skill.MAGIC),
			client::getVarbitValue, client::getVarpValue) : Map.of();
		if (nexus && HouseFurniture.keepSaved(k, table.knowsNexusId(nexusId), getNexusKeys()))
		{
			// POH_NEXUS_ID not read yet (a change of it scans again): a part of the saved list, which is kept
			log.debug("House scan: nexus id {} not known, saved nexus keys kept", nexusId);
			return changed;
		}
		boolean keysChanged = keys.update(encodeKeys(k));
		if (keysChanged)
		{
			log.debug("House scan: nexus id {}, nexus keys {}", nexusId, k);
		}
		return changed || keysChanged;
	}

	/** The scene is an instance copied from the house room templates. */
	private boolean isHouseScene()
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null || !wv.isInstance())
		{
			return false;
		}
		int[][][] chunks = wv.getInstanceTemplateChunks();
		if (chunks == null)
		{
			return false;
		}
		for (int[][] plane : chunks)
		{
			for (int[] column : plane)
			{
				for (int c : column)
				{
					if (HouseFurniture.isHouseChunk(c))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Once per visit: every known piece of furniture in the scene (all planes, all four object kinds). */
	private void readScene()
	{
		present.clear();
		forEachObject(o ->
		{
			if (table.isWatched(o.getId()))
			{
				present.put(o.getHash(), o.getId());
			}
			return false;
		});
	}

	/** True if some object in the scene has a "Build" option (an empty build space, seen only by the house's owner). */
	private boolean sceneHasBuildSpaces()
	{
		Map<Integer, Boolean> checked = new HashMap<>();
		return forEachObject(o -> checked.computeIfAbsent(o.getId(), id ->
		{
			ObjectComposition c = client.getObjectDefinition(id);
			if (c == null || c.getActions() == null)
			{
				return false;
			}
			for (String a : c.getActions())
			{
				if ("Build".equals(a))
				{
					return true;
				}
			}
			return false;
		}));
	}

	/** Calls {@code f} for every object of the scene until it returns true; true if it did. */
	private boolean forEachObject(Predicate<TileObject> f)
	{
		WorldView wv = client.getTopLevelWorldView();
		Scene scene = wv == null ? null : wv.getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		if (tiles == null)
		{
			return false;
		}
		for (Tile[][] plane : tiles)
		{
			for (Tile[] column : plane)
			{
				for (Tile t : column)
				{
					if (t == null)
					{
						continue;
					}
					GameObject[] objects = t.getGameObjects();
					if (objects != null)
					{
						for (GameObject g : objects)
						{
							if (g != null && f.test(g))
							{
								return true;
							}
						}
					}
					if (t.getWallObject() != null && f.test(t.getWallObject())
						|| t.getDecorativeObject() != null && f.test(t.getDecorativeObject())
						|| t.getGroundObject() != null && f.test(t.getGroundObject()))
					{
						return true;
					}
				}
			}
		}
		return false;
	}
}

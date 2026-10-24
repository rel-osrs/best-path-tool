package com.bestpath;

import com.bestpath.core.data.BoatTable;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Skill;
import com.bestpath.player.BoatReading;
import com.bestpath.player.BoatVars;
import com.bestpath.player.CooldownWatch;
import com.bestpath.player.DelayedSave;
import com.bestpath.player.GameReading;
import com.bestpath.player.HouseFurniture;
import com.bestpath.player.HouseView;
import com.bestpath.player.ProfileText;
import com.bestpath.player.QuestNames;
import com.bestpath.player.PlayerBoats;
import com.bestpath.player.QuestWatch;
import com.bestpath.player.RequirementInputs;
import com.bestpath.player.SnapshotMapper;
import com.bestpath.route.PlayerSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;

/**
 * The player state the availability predicates need, kept up to date from
 * game events (no polling) and turned into an immutable {@link PlayerSnapshot}
 * on request. CLIENT THREAD ONLY (event handlers and {@link #snapshot}), except
 * {@link #setInputs}.
 *
 * <p>Read from the game when a snapshot is taken (cheap): position, real
 * levels, members world, inventory, worn items, the varbits / varps that some
 * requirement mentions, spellbook, house location, the rune pouch, and the
 * minute the home and minigame teleports were last used with the game's minute
 * clock (which of them are cooling down: {@link CooldownWatch}). Kept
 * between snapshots: what is built in the house (scanned in the player's own
 * house), the bank (only visible while open, so it is remembered
 * and saved per account), quest states of the quests some requirement
 * mentions (reading one runs a client script: re-read only when the quest's
 * own progress var changed, see {@link QuestWatch}), the ownership cache
 * (requirement items ever seen on this account) and the player answers (per
 * account; given with Yes / No under a step card or in the Settings tab).
 *
 * <p>The bank and the ownership cache are written to the account profile
 * once their changes settle ({@link DelayedSave}: a banking session is one
 * write), to the account they belong to, and before another account's values
 * are loaded.
 *
 * <p>The ownership cache is NOT put into the snapshot: the requirement model
 * ({@code core/req}) knows carried, worn and banked items only, and an item
 * owned once is not assumed to be in the bank.
 *
 * <p>The event handlers return true when the change may matter for
 * availability; the plugin then tells {@link com.bestpath.route.RouteFollower}.
 * The game's minute clock (varp DATE_MINUTES, every minute) counts only at the
 * moment a cooling-down teleport turns ready; {@link #cooldownTurnedReady}
 * (each tick, one comparison) does the same on the computer's clock.
 *
 * <p>Sailing: the player's boats come from the boat varbits
 * ({@link BoatVars}), read again only after one of them changed or the
 * player got on or off a boat, and turned into boats with the map data's boat
 * table ({@link SnapshotMapper#boats}). On the player's own boat at sea the
 * snapshot's tile is the boat's tile in the world (RuneLite's public
 * {@link WorldEntity}), and that boat is marked as where the route starts.
 *
 * <p>The house: what is built in it comes from the last scan of the player's
 * own house ({@link HouseScanner}, remembered per account).
 *
 * <p>Collection log items and grown spirit trees are detected, never asked:
 * they come from {@link UnlockWatch}, remembered per account.
 */
@Slf4j
final class PlayerTracker
{
	static final String BANK_KEY = "bank";
	static final String OWNED_KEY = "ownedItems";
	static final String ANSWERS_KEY = "answers";

	/** Rune pouch slot varbits (the same ones RuneLite's rune pouch overlay reads). */
	private static final int[] POUCH_TYPES = {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2,
		VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6};
	private static final int[] POUCH_COUNTS = {VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2,
		VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5,
		VarbitID.RUNE_POUCH_QUANTITY_6};
	/** Varbits always read (not from requirement data): spellbook, house location, rune pouch. */
	private static final Set<Integer> ALWAYS_VARBITS = new HashSet<>();
	/** Quest (RuneLite name) -> its progress vars, from quest-vars.tsv (Quest Helper's numbers). */
	private static final Map<String, List<int[]>> QUEST_VARS = QuestWatch.loadTable();

	static
	{
		ALWAYS_VARBITS.add(VarbitID.SPELLBOOK);
		ALWAYS_VARBITS.add(VarbitID.POH_HOUSE_LOCATION);
		for (int v : POUCH_TYPES)
		{
			ALWAYS_VARBITS.add(v);
		}
		for (int v : POUCH_COUNTS)
		{
			ALWAYS_VARBITS.add(v);
		}
	}

	private final Client client;
	private final ProfileStore store;
	private volatile RequirementInputs inputs = RequirementInputs.NONE;

	/** Null until the bank was seen on this account. */
	private Map<Integer, Integer> bank;
	private final DelayedSave bankSave = new DelayedSave();
	private final Set<Integer> owned = new TreeSet<>();
	private final DelayedSave ownedSave = new DelayedSave();
	/** The player answers: the data's words -> yes / no; not in it = not answered yet. */
	private Map<String, Boolean> answers = Map.of();
	/** Quest states of the mentioned quests (re-read as {@link #questWatch} says). */
	private final Map<String, QuestState> quests = new HashMap<>();
	/** Which quests to read again; made for {@link #watchedInputs}. */
	private QuestWatch questWatch;
	private RequirementInputs watchedInputs;
	private final Set<String> unknownQuestsLogged = new HashSet<>();
	/** Last real level per our skill (0 = not seen yet), to ignore boost-only StatChanged events. */
	private final int[] realLevels = new int[Skill.values().length];
	/** What the boat varbits mean (the map data's boat table; empty until the map data loaded). */
	private BoatTable boatTable = BoatTable.EMPTY;
	/** The boats must be read again (a boat varbit changed, the player got on or off a boat, login, new data). */
	private boolean boatsDirty = true;
	private boolean lastAboard;
	private PlayerBoats boats = PlayerBoats.NOT_LOGGED_IN;
	/** What the Settings tab was last given. */
	private PlayerBoats shownBoats;
	/** What the Settings tab's "House (POH)" section was last given, and what it was made from. */
	private HouseView shownHouse;
	private int shownHouseValue = -1;
	private Set<String> shownHouseThings;
	/** Home and minigame teleports: notices the moment one turns ready again (or starts cooling down). */
	private final CooldownWatch cooldowns = new CooldownWatch();
	/** What is built in the player's own house (scanned while inside it). */
	private final HouseScanner house;
	/** Collection log items and spirit trees, detected instead of asked. */
	private final UnlockWatch unlocks;

	PlayerTracker(Client client, ProfileStore store)
	{
		this.client = client;
		this.store = store;
		this.house = new HouseScanner(client, store, HouseFurniture.load());
		this.unlocks = new UnlockWatch(client, store);
	}

	/** The collection log and spirit tree detection (the plugin passes it its events). Client thread. */
	UnlockWatch unlocks()
	{
		return unlocks;
	}

	/** The house scan (the plugin passes it the object, menu and game state events). Client thread. */
	HouseScanner house()
	{
		return house;
	}

	/** What the loaded transport data's requirements mention (any thread). */
	void setInputs(RequirementInputs in)
	{
		inputs = in == null ? RequirementInputs.NONE : in;
	}

	RequirementInputs getInputs()
	{
		return inputs;
	}

	/** What the boat varbits mean (client thread; the map data's boat table). */
	void setBoatTable(BoatTable table)
	{
		boatTable = table == null ? BoatTable.EMPTY : table;
		boatsDirty = true;
	}

	/** The quest watch for these inputs (made again when the inputs changed). */
	private QuestWatch questWatch(RequirementInputs in)
	{
		if (questWatch == null || watchedInputs != in)
		{
			watchedInputs = in;
			questWatch = new QuestWatch(in.getQuests(), name ->
			{
				Quest q = QuestNames.find(name);
				return q == null ? List.of() : QUEST_VARS.getOrDefault(q.name(), List.of());
			});
			quests.clear();
			if (!questWatch.getUnmapped().isEmpty())
			{
				log.debug("Quests without a known progress var (read again at most once a minute): {}",
					questWatch.getUnmapped());
			}
		}
		return questWatch;
	}

	/**
	 * Loads this account's saved bank, ownership cache and answers (startup and account switch). Anything still
	 * waiting to be written for the previous account is written first (to that account).
	 */
	void loadProfile()
	{
		flushSaves();
		String savedBank = store.read(BANK_KEY);
		bank = ProfileText.decodeCounts(savedBank);
		bankSave.loaded(savedBank);
		String savedOwned = store.read(OWNED_KEY);
		owned.clear();
		owned.addAll(ProfileText.decodeIds(savedOwned));
		ownedSave.loaded(savedOwned);
		answers = ProfileText.decodeAnswers(store.read(ANSWERS_KEY));
		house.load();
		unlocks.load();
		quests.clear();
		questWatch(inputs).all();
		Arrays.fill(realLevels, 0);
		boatsDirty = true;
		cooldowns.clear(); // the game sends this account's last-use varps; a snapshot reads them anyway
	}

	/** The player answers loaded or given (the data's words -> yes / no), for the Settings tab. */
	Map<String, Boolean> getAnswers()
	{
		return Collections.unmodifiableMap(new LinkedHashMap<>(answers));
	}

	/**
	 * A player answer (Yes / No under a step card or the Settings tab): yes, no, or null to clear it (asked again).
	 * Saved for the account at once (a few bytes; the answers' text is compared first). The next snapshot has it, so the
	 * availability check searches the route again.
	 *
	 * @return true if the answers changed
	 */
	boolean setAnswer(String answer, Boolean yes)
	{
		if (answer == null || answer.isBlank())
		{
			return false;
		}
		Map<String, Boolean> next = new LinkedHashMap<>(answers);
		next.keySet().removeIf(k -> k.equalsIgnoreCase(answer.trim()));
		if (yes != null)
		{
			next.put(answer.trim(), yes);
		}
		String before = ProfileText.encodeAnswers(answers);
		String text = ProfileText.encodeAnswers(next);
		answers = next;
		if (text.equals(before))
		{
			return false;
		}
		if (!store.write(null, ANSWERS_KEY, text))
		{
			log.debug("Answer kept until the plugin stops (no account to save it to)");
		}
		return true;
	}

	/**
	 * Each game tick: writes the bank / ownership cache once their changes settled. A value seen while the account
	 * had no profile goes to the account logged in now (RuneLite creates its profile), at least 6 s after login.
	 */
	void onTick(int tick)
	{
		if (bankSave.due(tick))
		{
			write(bankSave, BANK_KEY, true);
		}
		if (ownedSave.due(tick))
		{
			write(ownedSave, OWNED_KEY, true);
		}
	}

	/**
	 * Writes everything still waiting, each to its own account (account switch, plugin stop). A value seen while
	 * the account had no profile is not written here (which account it belongs to is not known any more); the
	 * account's bank and items are seen again on the next login.
	 */
	void flushSaves()
	{
		write(bankSave, BANK_KEY, false);
		write(ownedSave, OWNED_KEY, false);
	}

	private void write(DelayedSave s, String key, boolean toLoggedIn)
	{
		DelayedSave.Write w = s.pending();
		if (w != null && (w.getProfile() != null || toLoggedIn) && store.write(w.getProfile(), key, w.getText()))
		{
			s.written();
		}
	}

	/** A value changed: written once changes settle ({@link DelayedSave}), to the account logged in now. */
	private void changed(DelayedSave s, String text)
	{
		s.changed(store.profileKey(), text, client.getTickCount());
	}

	/** @return true if this container change may matter for availability */
	boolean onItemContainerChanged(ItemContainerChanged e)
	{
		int id = e.getContainerId();
		if (id != InventoryID.INV && id != InventoryID.WORN && id != InventoryID.BANK)
		{
			return false;
		}
		Map<Integer, Integer> items = counts(e.getItemContainer());
		if (ProfileText.addOwned(owned, items.keySet(), inputs.getItems()))
		{
			changed(ownedSave, ProfileText.encodeIds(owned));
		}
		if (id != InventoryID.BANK)
		{
			return true;
		}
		if (items.equals(bank))
		{
			return false; // bank opened, nothing changed since last time
		}
		bank = items;
		changed(bankSave, ProfileText.encodeCounts(items));
		return true;
	}

	/**
	 * @return true if this var may matter: a mentioned varbit / varp, spellbook, house, rune pouch, or the progress
	 * var of a mentioned quest (other vars only make the quests be read again within a minute, see {@link QuestWatch})
	 */
	boolean onVarbitChanged(VarbitChanged e)
	{
		RequirementInputs in = inputs;
		int varbit = e.getVarbitId();
		if (varbit == -1 && !in.getCooldowns().isEmpty())
		{
			// the game's minute clock: only the moment a home / minigame teleport turns ready again matters (not every
			// minute); a last-use varp: noted here, and below it counts like any watched varp
			if (e.getVarpId() == Cooldown.DATE_MINUTES_VARP)
			{
				return cooldowns.gameMinute(client.getVarpValue(Cooldown.DATE_MINUTES_VARP), System.currentTimeMillis());
			}
			for (Cooldown c : in.getCooldowns())
			{
				if (e.getVarpId() == c.getVarp())
				{
					cooldowns.lastUse(c, client.getVarpValue(c.getVarp()), System.currentTimeMillis());
				}
			}
		}
		if (varbit != -1 && BoatVars.isBoatVarbit(varbit))
		{
			boatsDirty = true; // read again when next needed; whether routes change is decided by comparing the boats
			return true;
		}
		if (questWatch(in).onVarChanged(varbit, e.getVarpId()))
		{
			return true;
		}
		if (varbit != -1)
		{
			return ALWAYS_VARBITS.contains(varbit) || in.getVarbits().contains(varbit);
		}
		return in.getVarps().contains(e.getVarpId());
	}

	/**
	 * Each game tick: true (once) when a home or minigame teleport's stored ready time has passed on the computer's
	 * clock and it turned ready (in case the game's minute clock did not move on). One comparison otherwise.
	 */
	boolean cooldownTurnedReady()
	{
		return !inputs.getCooldowns().isEmpty() && cooldowns.tick(System.currentTimeMillis());
	}

	/** @return true if a real level changed (boost-only changes don't matter) */
	boolean onStatChanged(StatChanged e)
	{
		Skill ours = Skill.byName(e.getSkill().getName());
		if (ours == null)
		{
			return false;
		}
		int i = ours.ordinal();
		if (realLevels[i] == e.getLevel())
		{
			return false;
		}
		realLevels[i] = e.getLevel();
		return true;
	}

	/**
	 * Where the server has the player (public {@code Actor#getWorldLocation}: "typically ahead of where the client
	 * renders the actor"), as a real-world tile (instance-safe), or null when not logged in. {@link #position()} is
	 * where the player is drawn, which trails a few tiles behind while running: a STASH take-out can arrive while the
	 * drawn player is still 3+ tiles from the unit.
	 */
	WorldPoint serverPosition()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		Player me = client.getLocalPlayer();
		WorldPoint w = me == null ? null : me.getWorldLocation();
		if (w == null)
		{
			return null;
		}
		LocalPoint lp = LocalPoint.fromWorld(me.getWorldView(), w);
		return lp == null ? w : WorldPoint.fromLocalInstance(client, lp);
	}

	/** The player's real-world tile (instance-safe), or null when not logged in. */
	WorldPoint position()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		Player me = client.getLocalPlayer();
		return me == null ? null : WorldPoint.fromLocalInstance(client, me.getLocalLocation());
	}

	/**
	 * Developer details: where the player stands, in words, for the Route tab (client thread), useful for instanced
	 * areas whose places the game shows elsewhere (e.g. the Dagannoth Kings' area). The game's own tile
	 * ({@code getWorldLocation}: in an instance, the copy's tile), the map tile it is a copy of (what routes use,
	 * {@link WorldPoint#fromLocalInstance}), each with its region; in an instance also the copied chunk and its rotation
	 * (RuneLite's {@code WorldView#getInstanceTemplateChunks}); inside a house or on a boat what routes use.
	 */
	List<String> whereLines()
	{
		List<String> out = new ArrayList<>();
		Player me = client.getGameState() == GameState.LOGGED_IN ? client.getLocalPlayer() : null;
		LocalPoint lp = me == null ? null : me.getLocalLocation();
		if (lp == null)
		{
			out.add("Not logged in");
			return out;
		}
		WorldPoint map = WorldPoint.fromLocalInstance(client, lp);
		out.add("Game tile: " + tile(me.getWorldLocation()));
		out.add("Map tile: " + tile(map));
		WorldView wv = client.getWorldView(lp.getWorldView());
		if (wv != null && wv.isInstance())
		{
			int[][][] chunks = wv.getInstanceTemplateChunks();
			int cx = lp.getSceneX() / 8;
			int cy = lp.getSceneY() / 8;
			int plane = wv.getPlane();
			if (chunks != null && plane < chunks.length && cx >= 0 && cx < chunks[plane].length && cy >= 0
				&& cy < chunks[plane][cx].length)
			{
				int data = chunks[plane][cx][cy];
				// the chunk data as RuneLite's own WorldPoint.fromLocalInstance reads it
				out.add("Instance: copies chunk " + ((data >> 14 & 0x3FF) * 8) + ", " + ((data >> 3 & 0x7FF) * 8) + ", "
					+ (data >> 24 & 3) + ", turned " + (data >> 1 & 3) + " x 90 degrees");
			}
			else
			{
				out.add("Instance (chunk not known)");
			}
		}
		WorldPoint route = routePosition();
		if (route != null && !route.equals(map))
		{
			out.add("Routes use: " + tile(route));
		}
		if (isOnBoat())
		{
			out.add("On a boat at " + tile(boatTile()));
		}
		out.add("World " + client.getWorld());
		return out;
	}

	private static String tile(WorldPoint w)
	{
		return w == null ? "?" : w.getX() + ", " + w.getY() + ", " + w.getPlane() + " (region " + w.getRegionID() + ")";
	}

	/**
	 * Where routes start from and progress is followed (client thread): {@link #position()}, except inside a house,
	 * where it is the house spot of the transport data ({@link HouseFurniture#routeTile}: the rooms are not on the
	 * walking map; the rides out of the house leave from that spot). Null when not logged in.
	 */
	WorldPoint routePosition()
	{
		WorldPoint w = position();
		if (w == null)
		{
			return null;
		}
		int[] t = HouseFurniture.routeTile(w.getX(), w.getY(), w.getPlane());
		return t[0] == w.getX() && t[1] == w.getY() && t[2] == w.getPlane() ? w : new WorldPoint(t[0], t[1], t[2]);
	}

	/**
	 * Where the player is on the map (client thread): on a boat the boat's tile at sea (as the route's start), else
	 * {@link #routePosition()}. Null when not logged in.
	 */
	WorldPoint mapPosition()
	{
		WorldPoint w = routePosition();
		if (w != null && isOnBoat())
		{
			WorldPoint bt = boatTile();
			return bt != null ? bt : w;
		}
		return w;
	}

	/**
	 * Where the game is walking the player now (its destination flag, public {@code Client#getLocalDestinationLocation}),
	 * packed ({@link com.bestpath.core.path.Tiles}); -1 when there is none (standing still).
	 */
	int destination()
	{
		LocalPoint lp = client.getLocalDestinationLocation();
		if (lp == null)
		{
			return -1;
		}
		WorldPoint w = WorldPoint.fromLocalInstance(client, lp);
		if (w == null || HouseFurniture.isHouseTemplateTile(w.getX(), w.getY()))
		{
			return -1; // inside a house the route starts at the house spot: no walk line to redraw
		}
		return Tiles.pack(w.getX(), w.getY(), w.getPlane());
	}

	/**
	 * True while the player stands in a world view other than the top-level one (a Sailing boat: RuneLite's
	 * public {@link WorldView#isTopLevel()}). The tile {@link #position()} gives is then not a real map tile, so
	 * route following pauses.
	 */
	boolean isOnBoat()
	{
		Player me = client.getLocalPlayer();
		LocalPoint lp = me == null ? null : me.getLocalLocation();
		if (lp == null)
		{
			return false;
		}
		WorldView wv = client.getWorldView(lp.getWorldView());
		return wv != null && !wv.isTopLevel();
	}

	/**
	 * The boat entity the player stands on (RuneLite's public API, as RuneLite's own Perspective code finds it: the
	 * top-level world view's world entity with the player's world view id), or null when on the ground.
	 */
	private WorldEntity boatEntity()
	{
		Player me = client.getLocalPlayer();
		LocalPoint lp = me == null ? null : me.getLocalLocation();
		if (lp == null)
		{
			return null;
		}
		WorldView wv = client.getWorldView(lp.getWorldView());
		WorldView top = client.getTopLevelWorldView();
		if (wv == null || wv.isTopLevel() || top == null)
		{
			return null;
		}
		return top.worldEntities().byIndex(wv.getId());
	}

	/** The world tile of the boat the player stands on (the boat's own location in the world), or null. */
	private WorldPoint boatTile()
	{
		WorldEntity we = boatEntity();
		LocalPoint at = we == null ? null : we.getLocalLocation();
		return at == null ? null : WorldPoint.fromLocalInstance(client, at);
	}

	/** The player's boats (read again only when something about them changed); not logged in: none. Client thread. */
	PlayerBoats boats()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			boatsDirty = true;
			lastAboard = false;
			return PlayerBoats.NOT_LOGGED_IN;
		}
		boolean aboard = isOnBoat();
		if (aboard != lastAboard)
		{
			lastAboard = aboard; // got on or off a boat: no varbit has to change for that
			boatsDirty = true;
		}
		if (boatsDirty)
		{
			boatsDirty = false;
			boats = readBoats();
		}
		return boats;
	}

	/**
	 * Each game tick (and on login / logout): notices the player getting on or off a boat, and returns the boats when
	 * what the Settings tab shows changed (else null). Reads the varbits only when one of them changed.
	 */
	PlayerBoats boatsForSidebar()
	{
		PlayerBoats b = boats();
		if (b.sameView(shownBoats))
		{
			return null;
		}
		shownBoats = b;
		return b;
	}

	private PlayerBoats readBoats()
	{
		BoatReading r = new BoatReading();
		for (int s = 0; s < BoatVars.SLOTS; s++)
		{
			for (int f = 0; f < BoatVars.FIELDS; f++)
			{
				r.slots[s][f] = client.getVarbitValue(BoatVars.id(s + 1, f));
			}
		}
		WorldEntity we = boatEntity();
		r.aboard = we != null;
		r.ownBoat = we != null && we.getOwnerType() == WorldEntity.OWNER_TYPE_SELF_PLAYER;
		r.boardedType = client.getVarbitValue(BoatVars.boardedType());
		for (int i = 0; i < 3; i++)
		{
			r.boardedName[i] = client.getVarbitValue(BoatVars.boardedName(i));
		}
		PlayerBoats b = SnapshotMapper.boats(r, boatTable);
		log.debug("Boats read: {}{}", b.getRows(), b.getAboardNote() == null ? "" : " (" + b.getAboardNote() + ")");
		return b;
	}

	/**
	 * Each game tick (and on login / logout): the Settings tab's "House (POH)" section when what it shows changed
	 * (where the house is, what the last scan found, when it looked), else null. One varbit read. Client thread.
	 */
	HouseView houseForSidebar()
	{
		boolean in = client.getGameState() == GameState.LOGGED_IN;
		int value = in ? client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION) : -1;
		Set<String> things = house.getThings(); // the same set until a scan finds something else
		long at = house.getScannedAt();
		if (shownHouse != null && value == shownHouseValue && things == shownHouseThings && at == shownHouse.getScannedAt())
		{
			return null; // nothing new: no allocation each tick
		}
		shownHouseValue = value;
		shownHouseThings = things;
		HouseView v = in ? HouseView.of(SnapshotMapper.housePlace(value, houseName(value)), things, at) : HouseView.NOT_LOGGED_IN;
		if (v.equals(shownHouse))
		{
			return null;
		}
		shownHouse = v;
		return v;
	}

	/**
	 * The game's own name of a house place value our table doesn't know (a new place), from its enum (cache enum 252,
	 * public {@code Client#getEnum}); null for a known value, no house, or when the enum has no such key. Client thread.
	 */
	private String houseName(int value)
	{
		if (value <= 0 || SnapshotMapper.housePlace(value) != null)
		{
			return null;
		}
		EnumComposition e = client.getEnum(SnapshotMapper.HOUSE_PLACE_ENUM);
		if (e == null)
		{
			return null;
		}
		for (int k : e.getKeys())
		{
			if (k == value)
			{
				return e.getStringValue(value);
			}
		}
		return null;
	}

	/** The bank as last seen (item id to count, read only); null before it was ever seen (sea charting tools). */
	Map<Integer, Integer> bankItems()
	{
		Map<Integer, Integer> b = bank;
		return b == null ? null : Collections.unmodifiableMap(b);
	}

	/** Copies the player state into an immutable snapshot; null when not logged in. */
	PlayerSnapshot snapshot()
	{
		WorldPoint w = routePosition(); // inside a house: the house spot
		if (w == null)
		{
			return null;
		}
		PlayerBoats pb = boats();
		if (lastAboard) // boats() just checked
		{
			// on a boat the player's own tile is not a map tile: the route starts from the boat's tile at sea
			WorldPoint bt = boatTile();
			w = bt != null ? bt : w;
		}
		RequirementInputs in = inputs;
		GameReading r = new GameReading();
		r.x = w.getX();
		r.y = w.getY();
		r.plane = w.getPlane();
		r.boats = pb.getBoats();
		for (Skill s : Skill.values())
		{
			net.runelite.api.Skill rs = PlayerSnapshots.runeliteSkill(s);
			if (rs != null)
			{
				r.levels[s.ordinal()] = client.getRealSkillLevel(rs);
			}
		}
		r.members = client.getWorldType().contains(WorldType.MEMBERS);
		r.inventory.putAll(counts(client.getItemContainer(InventoryID.INV)));
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
			{
				EquipSlot ours = PlayerSnapshots.equipSlot(slot);
				Item it = worn.getItem(slot.getSlotIdx());
				if (ours != null && it != null && it.getId() > 0)
				{
					r.worn[ours.ordinal()] = it.getId();
				}
			}
		}
		r.bank = bank;
		for (int id : in.getVarbits())
		{
			r.varbits.put(id, client.getVarbitValue(id));
		}
		for (int id : in.getVarps())
		{
			r.varps.put(id, client.getVarpValue(id));
		}
		r.quests.putAll(quests(in));
		r.spellbookValue = client.getVarbitValue(VarbitID.SPELLBOOK);
		r.houseValue = client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION);
		r.houseName = houseName(r.houseValue);
		r.houseThings.addAll(house.getThings());
		r.nexusKeys.putAll(house.getNexusKeys());
		EnumComposition runes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int i = 0; i < SnapshotMapper.POUCH_SLOTS; i++)
		{
			int type = client.getVarbitValue(POUCH_TYPES[i]);
			int count = client.getVarbitValue(POUCH_COUNTS[i]);
			if (type > 0 && count > 0 && runes != null)
			{
				r.pouchItems[i] = runes.getIntValue(type);
				r.pouchCounts[i] = count;
			}
		}
		r.answers.putAll(answers);
		r.collectionLog.addAll(unlocks.collectionLog());
		r.spiritTrees.addAll(unlocks.grownSpiritTrees());
		r.canoeAxeStored = unlocks.canoeAxeStored();
		// home and minigame teleports: the minute each was last used and the game's minute clock (see Cooldown)
		for (Cooldown c : Cooldown.values())
		{
			r.cooldownLastUse[c.ordinal()] = client.getVarpValue(c.getVarp());
		}
		r.gameMinute = client.getVarpValue(Cooldown.DATE_MINUTES_VARP);
		r.clockMillis = System.currentTimeMillis();
		cooldowns.read(r.cooldownLastUse, r.gameMinute, r.clockMillis); // the snapshot itself has the newest decision
		return SnapshotMapper.toSnapshot(r);
	}

	/** The mentioned quests' states; only those {@link QuestWatch} names are read again. */
	private Map<String, QuestState> quests(RequirementInputs in)
	{
		for (String name : questWatch(in).toRead(client.getTickCount()))
		{
			Quest q = QuestNames.find(name);
			if (q == null)
			{
				if (unknownQuestsLogged.add(name))
				{
					log.debug("Quest '{}' is not one RuneLite knows; it counts as not started", name);
				}
				continue;
			}
			quests.put(name, SnapshotMapper.questState(q.getState(client)));
		}
		return quests;
	}

	/** Item id to count over the container's slots (empty slots and placeholders skipped). */
	private static Map<Integer, Integer> counts(ItemContainer c)
	{
		Map<Integer, Integer> m = new HashMap<>();
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
		return m;
	}
}

package com.bestpath;

import com.bestpath.clue.ClueCard;
import com.bestpath.clue.ClueWatcher;
import com.bestpath.clue.DetectedClue;
import com.bestpath.clue.KeyStop;
import com.bestpath.clue.StashState;
import com.bestpath.clue.StashVars;
import com.bestpath.core.data.ClueData;
import com.bestpath.core.data.Tables;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.QuestHelperPath;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteService;
import com.bestpath.route.RouteStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;

/**
 * Clue scrolls: detects the carried clue ({@link ClueWatcher}), shows it and its step's card (the items it needs and
 * where the player has them, its STASH unit), and with "Automatic clue routes" on routes to it. A clue route may start
 * with stops: putting items back in a STASH unit ({@link StashTracker}) and killing the NPC that drops a key.
 * Client thread only.
 */
@Slf4j
final class ClueRoutes
{
	/** How many game ticks to keep trying to read the clue text after the clue interface opened. */
	private static final int CLUE_TEXT_TRIES = 3;
	/** The request keys of clue routes start with this. */
	private static final String CLUE_KEY = "clue ";

	private final Client client;
	private final BestPathConfig config;
	private final PluginContext plugin;
	private final ClueWatcher watcher = new ClueWatcher(null);
	private final StashTracker stash;
	/** Item param 623 (the clue step's cache row) per item id, read once per item. */
	private final Map<Integer, Integer> clueParams = new HashMap<>();
	/** Game ticks left to try reading the clue scroll's text after its interface opened. */
	private int textTicks;
	/** The map data's tables (item groups) once it loaded; null before. */
	private Tables tables;
	/** The carried clue's card must be made again. */
	private boolean cardDirty;
	/** The card last given to the sidebar. */
	private ClueCard shownCard;

	ClueRoutes(Client client, BestPathConfig config, ProfileStore store, PluginContext plugin)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		this.stash = new StashTracker(client, store);
	}

	/** The map data loaded: its item groups and clue steps (the STASH units and where they are). */
	void setData(Tables mapTables, ClueData clueData)
	{
		tables = mapTables; // the clue card reads the needs with the map data's item groups
		stash.setClues(clueData);
	}

	/** Once the rest of the map data is in place: the carried clue is detected with the clue steps. */
	void detectClues(ClueData clueData)
	{
		onClue(watcher.setData(clueData.size() == 0 ? null : clueData, this::clueParam));
	}

	/** This account's STASH units (start, login, account switch). */
	void loadProfile()
	{
		stash.loadProfile();
	}

	/** Writes the STASH units not written yet (the plugin stops). */
	void flushSaves()
	{
		stash.flushSaves();
	}

	/** The clue step's card is made again on the next tick (logged in or out, items or vars changed). */
	void cardChanged()
	{
		cardDirty = true;
	}

	/** A clue is carried and Best Path routes it ({@link QuestHelperPath#heldBack}). */
	boolean holdsRoute()
	{
		RouteService service = plugin.routes();
		RouteRequest current = service == null ? null : service.getCurrent().getRequest();
		boolean clueRoute = current != null && current.getKey() != null && current.getKey().startsWith(CLUE_KEY);
		return QuestHelperPath.heldBack(watcher.getCurrent().getKind() != DetectedClue.Kind.NONE,
			config.allowClueRouting() && !config.preferShortestClue() || clueRoute);
	}

	/** Each game tick: the clue text, the STASH units, and the card and the route's first stops when they changed. */
	void onTick(int tick, WorldPoint serverPosition)
	{
		if (textTicks > 0)
		{
			readClueText();
		}
		// the game's own tile, not the drawn one: a STASH search is judged by where the server has the player (the
		// drawn player can still be running towards the unit when the items arrive)
		if (stash.onTick(tick, serverPosition, watcher.getCurrent()))
		{
			cardDirty = true; // a STASH unit's filled / empty changed
		}
		if (cardDirty)
		{
			updateCard(); // at most once per tick, after an item, STASH var or clue change
			firstStopsChanged(); // the STASH put-back and kill-for-the-key stops follow the items and units
		}
	}

	/** The clue scroll's interface opened: its text may be filled in later, so it is tried now and on the next ticks. */
	void clueInterfaceLoaded()
	{
		textTicks = CLUE_TEXT_TRIES;
		plugin.onClient(() ->
		{
			if (textTicks > 0)
			{
				readClueText();
			}
		});
	}

	/** Reads the open clue scroll's text and hands it to the clue detection. */
	private void readClueText()
	{
		Widget text = client.getWidget(InterfaceID.TrailCluetext.TEXT);
		String s = text == null || text.isHidden() ? null : text.getText();
		if (s == null || s.trim().isEmpty())
		{
			textTicks--;
			return;
		}
		textTicks = 0;
		onClue(watcher.onClueText(s));
	}

	/** An item's clue param (the step's cache row), or -1. Public API: ItemComposition.getIntValue. */
	private int clueParam(int itemId)
	{
		Integer v = clueParams.get(itemId);
		if (v == null)
		{
			ItemComposition c = client.getItemDefinition(itemId);
			v = c == null ? -1 : c.getIntValue(ParamID.CLUE_SCROLL);
			clueParams.put(itemId, v);
		}
		return v;
	}

	/** An item container changed: STASH deposits, the clue card's items, and a clue picked up or dropped. */
	void onItemsChanged(ItemContainerChanged e)
	{
		int container = e.getContainerId();
		stash.onItemsChanged(container); // a search of a STASH unit: put in or taken out
		if (container == InventoryID.INV || container == InventoryID.WORN || container == InventoryID.BANK)
		{
			cardDirty = true; // the clue card says where its items are
		}
		if (container == InventoryID.INV)
		{
			List<Integer> ids = new ArrayList<>();
			ItemContainer c = e.getItemContainer();
			if (c != null)
			{
				for (Item it : c.getItems())
				{
					if (it.getId() > 0)
					{
						ids.add(it.getId());
					}
				}
			}
			onClue(watcher.onInventory(ids, this::clueParam));
		}
	}

	/** A menu click: "Search" on a STASH unit, or any other click. */
	void onMenuOptionClicked(MenuOptionClicked e)
	{
		stash.onMenuOptionClicked(e);
	}

	/** A script started: the STASH chart may tell which units are filled. */
	void onScriptPreFired(ScriptPreFired e)
	{
		if (stash.onScriptPreFired(e))
		{
			cardDirty = true;
			firstStopsChanged();
		}
	}

	/** A varbit or varp changed: the game's clue helper, and the carried clue's STASH unit (built, filled). */
	void onVarbitChanged(VarbitChanged e)
	{
		stash.onVarbitChanged(e); // the game's clue helper: read on the next tick
		// only the carried clue's STASH unit vars make the card be made again
		DetectedClue d = watcher.getCurrent();
		if (d.getKind() == DetectedClue.Kind.STEP && !d.getSteps().isEmpty() && d.getSteps().get(0).getStashVarbit() >= 0
			&& StashVars.matters(e.getVarbitId(), e.getVarpId(), d.getSteps().get(0).getStashVarbit()))
		{
			cardDirty = true;
		}
	}

	/**
	 * The carried clue changed (null = no change). Shows it in the sidebar; with "Automatic clue routes" on, a newly
	 * detected clue with a place becomes the destination. A clue route whose clue is gone is dropped.
	 */
	private void onClue(DetectedClue d)
	{
		if (d == null)
		{
			return;
		}
		log.debug("Clue detected: {}", d);
		cardDirty = true; // the clue step's card (made on the next tick)
		plugin.toSidebar(sidebar -> sidebar.showClue(d));
		RouteService service = plugin.routes();
		if (service == null)
		{
			return;
		}
		if (d.isRoutable() && config.allowClueRouting() && !config.preferShortestClue())
		{
			// with "Use Shortest Clue for clues" on, Shortest Clue's route comes instead
			startRoute(d);
			plugin.openSidebar(config.openForClue(), false);
		}
		else if (d.getKind() == DetectedClue.Kind.NONE && config.allowClueRouting() && startStashOnlyRoute())
		{
			// the clue is done (or gone) and items taken out of a STASH unit for it wait to be put back: the route
			// goes to that unit
			log.debug("Route to the STASH unit to put the items back");
		}
		else if (d.getKind() == DetectedClue.Kind.NONE)
		{
			RouteResult r = service.getCurrent();
			if (r.getRequest() != null && r.getRequest().getKey() != null && r.getRequest().getKey().startsWith(CLUE_KEY))
			{
				service.clear();
			}
		}
	}

	/** The sidebar's "Clue" button: a route to the carried clue's places. */
	void routeToClue()
	{
		if (watcher.getCurrent().isRoutable())
		{
			startRoute(watcher.getCurrent());
		}
	}

	/** Route to the clue's places. */
	private void startRoute(DetectedClue d)
	{
		RouteService service = plugin.routes();
		PlayerTracker t = plugin.tracker();
		RouteRequest request = d.request();
		if (service == null || t == null || request == null)
		{
			return;
		}
		PlayerSnapshot snap = t.snapshot();
		if (snap == null)
		{
			service.fail(request, "Log in to find a route.");
		}
		else
		{
			// what the clue step needs there (outfit, spade, key...) goes with the request: items only in the bank make
			// the route start at a bank (bank detours), items in a filled STASH unit at the spot are left out
			ClueCard card = makeCard(d, snap);
			RouteRequest withNeeds = card == null ? request : request.withNeeds(card.destinationNeeds());
			// items taken out of a STASH unit for an earlier step: put them back first; a key step without its key:
			// kill the NPC that drops it first
			service.request(withNeeds.withFirstStops(firstStops(d, snap)), snap, plugin.settings());
		}
	}

	/**
	 * The first stops of a route for this clue, in order: put the STASH items back, then kill for the key (each only
	 * when due).
	 */
	private List<RouteRequest.Stop> firstStops(DetectedClue d, PlayerSnapshot snap)
	{
		List<RouteRequest.Stop> out = new ArrayList<>();
		RouteRequest.Stop put = stashStop(d);
		if (put != null)
		{
			out.add(put);
		}
		RouteRequest.Stop kill = KeyStop.stop(d, snap == null ? null : snap.getState(), this::itemName);
		if (kill != null)
		{
			out.add(kill);
		}
		return out;
	}

	/**
	 * The "put the items back" first stop for routes while this clue is carried, or null (also when the config switch
	 * "Remind to put STASH items back" is off).
	 */
	private RouteRequest.Stop stashStop(DetectedClue d)
	{
		return stash.stop(stash.dueTaken(d, config.remindStashPutBack()), this::itemName);
	}

	/**
	 * No clue carried, and items wait to be put back in a STASH unit: a route to that unit only (its card is the
	 * route's last). False when nothing waits.
	 */
	private boolean startStashOnlyRoute()
	{
		RouteRequest.Stop stop = stashStop(DetectedClue.NONE);
		if (stop == null)
		{
			return false;
		}
		RouteRequest r = RouteRequest.places("stash " + stop.getId(), "Put the items back in the STASH unit",
			List.of("You took them out for your clue."), new int[]{stop.getTile()}).withFirstStop(stop);
		plugin.requestRoute(r);
		return true;
	}

	/** "Skip" on the put-back card: that reminder is forgotten for good (saved per account). */
	void skipPutBack(String stopId, int tick)
	{
		stash.skipTaken(stopId, tick);
		firstStopsChanged();
	}

	/**
	 * What the STASH units remember changed (items put back or taken out, or "Skip"), or what the player carries (the
	 * key of a key step picked up): the current clue route gets or loses its first stops; a route to the unit only is
	 * dropped once the items are back. Also when "Remind to put STASH items back" is switched.
	 */
	void firstStopsChanged()
	{
		RouteService service = plugin.routes();
		PlayerTracker t = plugin.tracker();
		RouteRequest req = service == null ? null : service.getCurrent().getRequest();
		if (req == null || t == null || req.getKey() == null)
		{
			return;
		}
		if (req.isStopOnly())
		{
			if (stashStop(watcher.getCurrent()) == null)
			{
				service.clear(); // the items are back (or skipped)
			}
			return;
		}
		PlayerSnapshot snap = req.getKey().startsWith(CLUE_KEY) ? t.snapshot() : null;
		if (snap == null)
		{
			return;
		}
		List<RouteRequest.Stop> want = firstStops(watcher.getCurrent(), snap);
		if (!want.equals(req.getFirstStops()))
		{
			service.request(req.withFirstStops(want), snap, plugin.settings());
		}
	}

	/**
	 * The carried clue step's card (items and where the player has them, its STASH unit), from a player snapshot and the
	 * STASH vars; null without a routable clue or when logged out.
	 */
	private ClueCard makeCard(DetectedClue d, PlayerSnapshot snap)
	{
		Tables t = tables;
		if (d == null || !d.isRoutable() || snap == null || t == null || d.getSteps().isEmpty())
		{
			return null;
		}
		ClueData.Step s = d.getSteps().get(0);
		// filled: what the plugin remembers for this unit (clue helper, STASH chart, deposits; newest wins), else the
		// clue helper when it tracks this step
		StashState unit = s.getStashVarbit() < 0 || d.getKind() != DetectedClue.Kind.STEP ? StashState.UNKNOWN
			: StashState.read(client.getVarbitValue(s.getStashVarbit()), client.getVarpValue(StashVars.HELPER_CLUE),
				client.getVarbitValue(StashVars.FILLED), s.getRow(), stash.remembered(s.getStashVarbit()));
		return ClueCard.build(d, snap.getState(), unit, t.itemGroups(), t, this::itemName);
	}

	/** An item's name (RuneLite's item definition), or null. */
	private String itemName(int id)
	{
		ItemComposition c = client.getItemDefinition(id);
		return c == null ? null : c.getName();
	}

	/**
	 * Makes the carried clue's card again and gives it to the sidebar when it changed. When the clue's route is shown
	 * and what its destination needs changed (e.g. the STASH unit turned out to be filled), the route is searched again
	 * with the new needs.
	 */
	private void updateCard()
	{
		cardDirty = false;
		PlayerTracker t = plugin.tracker();
		DetectedClue d = watcher.getCurrent();
		PlayerSnapshot snap = d.isRoutable() && t != null ? t.snapshot() : null;
		ClueCard card = makeCard(d, snap);
		if (Objects.equals(card, shownCard))
		{
			return;
		}
		shownCard = card;
		plugin.toSidebar(sidebar -> sidebar.showClueCard(card));
		RouteService service = plugin.routes();
		if (card == null || service == null || snap == null)
		{
			return;
		}
		RouteResult r = service.getCurrent();
		RouteRequest req = r.getRequest();
		if (req != null && card.getRequestKey().equals(req.getKey()) && !req.getNeeds().equals(card.destinationNeeds())
			&& (r.getStatus() == RouteStatus.READY || r.getStatus() == RouteStatus.NO_ROUTE))
		{
			service.request(req.withNeeds(card.destinationNeeds()), snap, plugin.settings());
		}
	}
}

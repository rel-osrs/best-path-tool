package com.bestpath.route;

import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.NoRouteReasons;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.SailingSetup;
import com.bestpath.core.data.SeaBits;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.path.GamePath;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.RouteDestination;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.BankItem;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.Dock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Routes with the exported map data and core's pathfinder ({@link PluginRouter}:
 * the player's transports by their requirements, doors, the route-end step,
 * free land for free-to-play).
 * Session exclusions and the persistent blacklist are applied by transport
 * id; the Settings tab's switched-off categories leave transports out, and
 * its "avoid" rules leave transports out unless there is no route without
 * them ({@link TransportRules}). The player's own tick changes (- / + on a
 * step card, and whole teleport categories and networks in the Settings tab)
 * change those transports' ticks ({@link StepTicks#withTickChanges}); each step says
 * the card change it used ({@link RouteStep#getTickChange()}). Worker thread
 * only.
 *
 * <p>Sailing: the player's boats come with the snapshot ({@link PlayerSnapshot#getBoats()}),
 * the Settings tab says which may be used and whether a shipwright may bring one
 * ({@link TransportSettings#isBoatAllowed}, {@link TransportSettings#isShipwrights}).
 * A sailing route's steps: (walk), bring the boat, board, Sail (the boat's course,
 * straight legs drawn as arrows), leave, (walk); the sailing steps can't be skipped
 * or blacklisted (no transport id).
 */
public final class CoreRouteEngine implements RouteEngine
{
	/** The header note when only a route with avoided transports exists. */
	public static final String AVOID_NOTE = "No route without the transports you chose to avoid: this one uses them.";
	/** The step note on an avoided transport the route uses anyway. */
	public static final String AVOIDED_STEP_NOTE = "A transport you chose to avoid (Settings): no route without it.";

	private final PluginRouter router;
	private final TransportRules rules;
	/** A transport step's card info, what to click and a house portal's words. */
	private final LineDetails lines;
	/** The "no route" cards. */
	private final NoRouteCards cards;

	public CoreRouteEngine(PluginRouter router)
	{
		this.router = router;
		this.rules = new TransportRules(router.getData().getTransports(), router.getData().getTables());
		this.lines = new LineDetails(router, rules);
		this.cards = new NoRouteCards(router, rules);
	}

	public PluginRouter getRouter()
	{
		return router;
	}

	/** Made when first needed (worker thread); the game's walking on the members map, doors shut. */
	private GamePath game;

	@Override
	public GamePath gamePath()
	{
		if (game == null)
		{
			game = new GamePath(router.getData().getMap(), router.getData().getDoors());
		}
		return game;
	}

	/** One search's answer, with the place chosen and the search's notes. */
	private static final class Found
	{
		PluginRouter.Answer answer;
		int chosen;
		final List<String> notes = new ArrayList<>();
	}

	private Found search(RouteJob job, PluginRouter.TransportFilter filter)
	{
		PlayerSnapshot p = job.getPlayer();
		RouteRequest r = job.getRequest();
		int[] targets = r.getTargets();
		Found f = new Found();
		SailingSetup boats = sailing(job);
		PluginRouter.BankSetup bank = bank(job);
		// the nearest bank: only the banks the player may use (their needs met), as a route starting at a bank
		int[] kept = NearestPlaces.BANK_KEY.equals(r.getKey())
			? NearestPlaces.usable(targets, router.usableBanks(p.getState())) : null;
		if (kept != null)
		{
			int[] all = targets;
			targets = new int[kept.length];
			for (int i = 0; i < kept.length; i++)
			{
				targets[i] = all[kept[i]];
			}
		}
		if (targets.length == 1)
		{
			f.answer = router.route(p.getState(), p.getX(), p.getY(), p.getPlane(), Tiles.x(targets[0]), Tiles.y(targets[0]),
				Tiles.plane(targets[0]), job::isExcluded, filter, boats, bank);
			f.chosen = kept == null ? 0 : kept[0];
		}
		else
		{
			PluginRouter.NearestAnswer n = router.routeNearest(p.getState(), p.getX(), p.getY(), p.getPlane(), targets,
				job::isExcluded, filter, MAX_SEARCHES, boats, bank);
			f.answer = n.getAnswer();
			f.chosen = kept == null ? Math.max(0, n.getChosen()) : kept[Math.max(0, n.getChosen())];
			if (n.getChosen() >= 0)
			{
				// no coordinates: they mean little to most players
				f.notes.add("Nearest of " + targets.length + " places" + (n.isCapped()
					? " (compared " + n.getSearched() + " of them by full search; another might be a little quicker)" : "") + ".");
			}
			if (n.getReachable() < targets.length)
			{
				int out = targets.length - n.getReachable();
				f.notes.add(out + (out == 1 ? " place can't" : " places can't") + " be reached at all.");
			}
		}
		if (kept != null)
		{
			f.notes.add("Only the banks you can use now (" + (r.getTargets().length - kept.length) + " of the bank places need"
				+ " something you don't have yet).");
		}
		return f;
	}

	/**
	 * Going to a bank first: the Settings tab's bank stop, and what the destination needs that the player has only in
	 * the bank (a clue's outfit: items in a filled STASH unit are already left out of the needs).
	 * Such items make the route begin at a bank (the bank-first route only, never the faster of the two), and the
	 * bank card lists them with what the way on needs.
	 */
	static PluginRouter.BankSetup bank(RouteJob job)
	{
		return PluginRouter.BankSetup.fromBank(job.getSettings().getBankTicks(), takeOut(fromBank(job), job.getPlayer().getState()))
			.withDetour(job.getSettings().isBankDetour());
	}

	/**
	 * The items as the bank card takes them: the words ("Gold ring", "Feather x5"), the items, and how many to take out
	 * (what is needed minus what the player carries).
	 */
	public static List<BankItem> takeOut(List<DestinationNeeds.Item> items, PlayerState player)
	{
		List<BankItem> out = new ArrayList<>();
		for (DestinationNeeds.Item it : items)
		{
			int carried = 0;
			for (int id : it.getIds())
			{
				carried += player == null ? 0 : player.carried(id);
			}
			out.add(new BankItem(it.toString(), it.getIds(), it.getCount() - carried));
		}
		return out;
	}

	/** What the destination needs that the player has only in the bank (empty: nothing). */
	static List<DestinationNeeds.Item> fromBank(RouteJob job)
	{
		return job.getRequest().getNeeds().fromBank(job.getPlayer().getState());
	}

	/** The items as the bank card lists them ("Gold ring", "Feather x5"). */
	static List<String> labels(List<DestinationNeeds.Item> items)
	{
		List<String> out = new ArrayList<>();
		for (DestinationNeeds.Item it : items)
		{
			out.add(it.toString());
		}
		return out;
	}

	/** The player's boats with the Settings tab's choices (which boats, shipwrights). */
	static SailingSetup sailing(PlayerSnapshot p, TransportSettings settings)
	{
		if (p.getBoats().isEmpty())
		{
			return SailingSetup.NONE;
		}
		Set<Integer> allowed = new LinkedHashSet<>();
		for (Boat b : p.getBoats())
		{
			if (settings.isBoatAllowed(b.getSlot()))
			{
				allowed.add(b.getSlot());
			}
		}
		return new SailingSetup(p.getBoats(), allowed, settings.isShipwrights(), settings.isKeepBoat(),
			settings.getBoardTicks());
	}

	/**
	 * {@link #sailing(PlayerSnapshot, TransportSettings)} for this job: "Park my boat" on the boat card keeps the boat for
	 * this route, as the Settings choice "Never leave my boat at sea" does.
	 */
	static SailingSetup sailing(RouteJob job)
	{
		SailingSetup s = sailing(job.getPlayer(), job.getSettings());
		return job.getBoatMode() == BoatMode.PARK ? s.withKeepBoat(true) : s;
	}

	@Override
	public RouteResult route(RouteJob job)
	{
		// where the time goes, for Developer details
		router.startTimes();
		RouteResult result = routeTimed(withCoinCap(job));
		return result.withTimes(router.timeLines(), router.getTimes().totalMillis());
	}

	/**
	 * The job with the Settings tab's coin limit in its player state: a fare above it counts as not met, everywhere a
	 * requirement is checked (one choice of "coins or a ticket" still works with the ticket; each way of a two-way
	 * transport on its own).
	 */
	static RouteJob withCoinCap(RouteJob job)
	{
		PlayerSnapshot p = job.getPlayer();
		if (p == null || p.getState() == null)
		{
			return job;
		}
		TransportSettings s = job.getSettings();
		int cap = s != null && s.isCoinCapOn() ? s.getCoinCap() : -1;
		PlayerSnapshot capped = p.withState(p.getState().withCoinCap(cap));
		return capped == p ? job : job.with(job.getRequest(), capped);
	}

	private RouteResult routeTimed(RouteJob job)
	{
		RouteRequest r = job.getRequest();
		if (r.isPlaneFromMap())
		{
			// the world map gives no plane: use the plane with ground there, picked here on the route worker (the
			// router is busy while a search runs)
			RouteRequest picked = r.onPlane(router.mapPlane(r.getX(), r.getY())).withNeeds(r.getNeeds())
				.withFirstStops(r.getFirstStops());
			return routeTimed(job.with(picked, job.getPlayer()));
		}
		// first stops (put STASH items back, kill for a clue's key): one search per part, see FirstStopRoute
		return job.getRequest().getFirstStop() != null ? FirstStopRoute.route(job, this::routeDirect) : routeDirect(job);
	}

	/** The route to the request's places (its first stop, if any, left to {@link FirstStopRoute}). */
	private RouteResult routeDirect(RouteJob job)
	{
		RouteRequest r = job.getRequest();
		int[] targets = r.getTargets();
		TransportSettings settings = job.getSettings();
		// What the destination needs (a clue's outfit, spade, key): items only in the bank mean the route must start at
		// a bank (routes bank only at the start). The search (bank(job)) then takes the bank-first route only: to a bank
		// with what the player carries, the bank stop (its card lists these items too), then on with the bank's items.
		List<DestinationNeeds.Item> fromBank = fromBank(job);
		// avoided transports only when nothing else works: first without them, then (if no route) with them
		boolean avoiding = TransportRules.avoidsAnything(settings);
		PluginRouter.TransportFilter lastFilter = StepTicks.withTickChanges(rules.filter(settings, true), settings);
		// on the boat: "Ocean only path" first; a place not near the sea, or one the boat can't sail near, gets the
		// normal route
		boolean aboard = RouteService.onBoat(job.getPlayer());
		BoatMode mode = aboard ? job.getBoatMode() : BoatMode.NORMAL;
		Found ocean = null;
		String boatNote = null;
		if (mode == BoatMode.OCEAN_ONLY)
		{
			PlayerSnapshot p = job.getPlayer();
			PluginRouter.OceanAnswer oa = router.routeOceanOnly(p.getState(), p.getX(), p.getY(), p.getPlane(), targets,
				job::isExcluded, lastFilter, sailing(job));
			if (oa.getAnswer() != null)
			{
				ocean = new Found();
				ocean.answer = oa.getAnswer();
				ocean.chosen = Math.max(0, oa.getChosen());
			}
			else
			{
				boatNote = oa.isNear() ? "Your boat can't sail near " + (targets.length == 1 ? "this destination" : "these places")
					+ ", so this is the normal route."
					: (targets.length == 1 ? "The destination isn't" : "None of these places is") + " within "
					+ Sailing.OCEAN_ONLY_RANGE + " tiles of a dock or open sea, so this is the normal route.";
			}
		}
		Found f = ocean != null ? ocean : search(job, lastFilter);
		boolean usedAvoided = false;
		if (ocean == null && avoiding && !f.answer.getRoute().isFound())
		{
			// the filter of the last search made: "no route" is explained with it (avoided transports allowed)
			lastFilter = StepTicks.withTickChanges(rules.filter(settings, false), settings);
			Found loose = search(job, lastFilter);
			if (loose.answer.getRoute().isFound())
			{
				f = loose;
				usedAvoided = true;
				f.notes.add(AVOID_NOTE);
			}
		}
		if (ocean == null && !fromBank.isEmpty() && f.answer.getRoute().isFound()
			&& (f.answer.getBankPlan() == null || !f.answer.getBankPlan().isBanked()))
		{
			// no bank this player can use can be reached first: the route goes straight there
			f.notes.add(0, job.getSettings().isBankDetour() ? noBankNote(fromBank)
				: NO_DETOUR_NOTE + String.join(", ", labels(fromBank)) + ".");
		}
		PluginRouter.Answer a = f.answer;
		List<String> notes = f.notes;
		int chosen = f.chosen;
		Route route = a.getRoute();
		if (!route.isFound())
		{
			String where = a.isFreeWorld() ? " on free-to-play land" : "";
			String what = targets.length == 1 ? "the destination" : "any of the " + targets.length + " places";
			// why: worked out afterwards on the worker, with "No route, finding out why..." shown meanwhile
			PluginRouter.TransportFilter whyFilter = lastFilter;
			return RouteResult.noRouteFindingWhy(job.getGeneration(), r, a.getStop() == null
				? "No route: nothing within " + PluginRouter.NEAR_RANGE + " tiles of " + what + " can be reached" + where + "."
				: "No route to " + what + where + " with the transports allowed.", notes,
				() -> whyNoRoute(job, whyFilter, System.currentTimeMillis() / 60_000L))
				.withBoatCard(boatCard(job, mode, route, boatNote));
		}
		long cards = System.nanoTime();
		int dest = targets[chosen];
		List<RouteStep> steps = stepCards(job, a, dest, usedAvoided, ocean != null);
		ArriveArea arrive = ArriveArea.walk(router.getData().getMap(), dest, lastTile(steps), ArriveArea.MAX_STEPS);
		router.addTime("Step cards", System.nanoTime() - cards);
		return RouteResult.ready(job.getGeneration(), r, steps, dest, notes, arrive).withBoatCard(boatCard(job, mode, route, boatNote));
	}

	/**
	 * The route's step cards, one per core step: its notes (an avoided transport used anyway; the last step when the
	 * route stops short of the destination), its card info and tick change, the Wilderness, a house portal's words for
	 * this player, what to click and the words in the game world; then a "Go into the Wilderness" card wherever a walk
	 * crosses into it.
	 *
	 * @param oceanOnly the route is the "Ocean only path" (it stops on the boat)
	 */
	private List<RouteStep> stepCards(RouteJob job, PluginRouter.Answer a, int dest, boolean usedAvoided, boolean oceanOnly)
	{
		TransportSettings settings = job.getSettings();
		int dx = Tiles.x(dest);
		int dy = Tiles.y(dest);
		int dp = Tiles.plane(dest);
		List<RouteStep> steps = new ArrayList<>();
		List<Route.Step> core = a.getRoute().getSteps();
		for (int i = 0; i < core.size(); i++)
		{
			List<String> extra = new ArrayList<>();
			int[] stop = a.getStop();
			Transport t = core.get(i).getTransport();
			if (usedAvoided && t != null && rules.isAvoided(settings, a.getTransports().lineOf(t)))
			{
				extra.add(AVOIDED_STEP_NOTE);
			}
			if (i == core.size() - 1 && stop != null && (stop[0] != dx || stop[1] != dy || stop[2] != dp))
			{
				int away = Math.max(Math.abs(stop[0] - dx), Math.abs(stop[1] - dy));
				String tiles = away + (away == 1 ? " tile" : " tiles");
				// a rock out at sea: the boat stops on open water near it
				extra.add(oceanOnly ? "Ocean only: this stops " + tiles + " away, on your boat."
					: stop[3] == RouteDestination.OPEN_WATER
					? "The spot itself is blocked: this stops " + tiles + " away, on open water."
					: "The destination itself can't be reached: this stops " + tiles + " away"
					+ (stop[3] == 1 ? ", in sight of it." : " (nothing nearer has it in sight)."));
			}
			// sailing steps show the boat's picture; others their transport's sort and needs;
			// a transport step says the player's own tick change it used (- / + on its card)
			// a bank step: what to take out, each with its picture and count (a box on its card)
			RouteStep card = StepTitles.toStep(core.get(i), a.getTransports(), router.getData().getTransports(), extra, docks());
			StepInfo info = StepTitles.isSailing(t) ? StepInfo.sailing(StepTitles.boatKind(t, job.getPlayer().getBoats()))
				: core.get(i).getKind() == Route.Step.Kind.BANK ? bankInfo(core.get(i), job.getPlayer().getState())
				: lines.info(core.get(i), a.getTransports(), job.getPlayer().getState());
			steps.add(StepTicks.withTicks(card.withInfo(info), t, a.getTransports(), settings));
			steps.set(steps.size() - 1, WildernessSteps.inWilderness(steps.get(steps.size() - 1), router.getData().getWilderness()));
			// a house portal: the portal nexus with this player's key, or the portal chamber's portal
			TransportLine line = t == null ? null : a.getTransports().lineOf(t);
			MenuText menu = line == null ? null : StepTitles.menuOf(core.get(i), line, router.getData().getTransports());
			MenuText inHouse = lines.houseMenu(menu, line, job.getPlayer());
			if (inHouse != menu)
			{
				RouteStep s = steps.get(steps.size() - 1);
				steps.set(steps.size() - 1,
					s.withTitle(StepTitles.transportTitle(t, inHouse, s.getKind() == RouteStep.Kind.TELEPORT)));
			}
			// what to click, for the text and the glow in the game
			steps.set(steps.size() - 1, steps.get(steps.size() - 1).withClick(lines.click(core.get(i), a.getTransports(),
				steps.get(steps.size() - 1), job.getPlayer().getState(), inHouse)));
			// shorter words in the game world than on the card (GameText)
			steps.set(steps.size() - 1, steps.get(steps.size() - 1).withGameText(StepTitles.gameText(steps.get(steps.size() - 1), t,
				line, inHouse)));
		}
		return WildernessSteps.intoWilderness(steps, router.getData().getWilderness());
	}

	/**
	 * The card at the top of a route started on the player's boat, or null: off the boat; or the normal route, which
	 * does not leave the boat at sea (its first step sails it). The normal route that teleports away first gets "Park
	 * my boat" and "Ocean only path" (greyed out when no dock or open sea is near enough); "Park my boat" or "Ocean
	 * only path" on: the card says so, with the way back.
	 *
	 * @param note why the chosen mode changed nothing (ocean only, but nothing near the sea), or null
	 */
	BoatCard boatCard(RouteJob job, BoatMode mode, Route route, String note)
	{
		PlayerSnapshot p = job.getPlayer();
		Boat on = null;
		for (Boat b : p.getBoats())
		{
			if (b.getDockId() == Boat.AT_START)
			{
				on = b;
			}
		}
		Sailing sea = router.getSailing();
		if (on == null || sea == null || !sea.atSea(p.getX(), p.getY(), p.getPlane()))
		{
			return null; // not on the boat out at sea (e.g. the part of a route after a first stop on land)
		}
		if (mode != BoatMode.NORMAL)
		{
			return new BoatCard(mode, on.label(), on.getType(), true, note);
		}
		boolean leaves = route.isFound() && !route.getSteps().isEmpty() && route.getSteps().get(0).getKind() != Route.Step.Kind.SAIL;
		if (!leaves)
		{
			return null;
		}
		boolean near = router.oceanOnlyNear(p.getState(), p.getX(), p.getY(), p.getPlane(), job.getRequest().getTargets(), sailing(job));
		return new BoatCard(BoatMode.NORMAL, on.label(), on.getType(), near, null);
	}

	/**
	 * Why there is no route: core's diagnosis ({@link PluginRouter#explainNoRoute}) with the request's places, the job's
	 * skips and blacklist, the filter of the last search made, and the player's boats with the Settings choices;
	 * turned into plain words and cards. Null when it could not be worked out (the plain "No route" line is shown).
	 * Transports skipped for this route are on no way ("Skip" on a "no route" card shows the next way without it);
	 * only when no way is left without them are they shown, as "You skipped this for this route".
	 *
	 * @param nowMinute the minute now (since 1970 UTC), for a cooling-down teleport's "ready again in about N min"
	 */
	NoRouteWhy whyNoRoute(RouteJob job, PluginRouter.TransportFilter filter, long nowMinute)
	{
		PlayerSnapshot p = job.getPlayer();
		Set<String> skips = job.getSessionExclusions();
		NoRouteReasons n;
		try
		{
			n = router.explainNoRoute(p.getState(), p.getX(), p.getY(), p.getPlane(), job.getRequest().getTargets(), job::isExcluded,
				skips.isEmpty() ? null : skips::contains, filter, sailing(job));
			if (!skips.isEmpty() && n.getKind() != NoRouteReasons.Kind.BLOCKED && n.getKind() != NoRouteReasons.Kind.BY_BOAT)
			{
				// no way without the skipped transports: the way with them, each saying it was skipped (Undo above)
				n = router.explainNoRoute(p.getState(), p.getX(), p.getY(), p.getPlane(), job.getRequest().getTargets(),
					job::isExcluded, filter, sailing(job));
			}
		}
		catch (RuntimeException e)
		{
			return null; // the plain "No route" line is shown
		}
		return cards.why(n, job, nowMinute);
	}

	/**
	 * The header line when the destination needs items only in the bank but no bank can be reached first (no bank
	 * data, or none this player may use on the way): the route then goes straight there.
	 */
	static String noBankNote(List<DestinationNeeds.Item> fromBank)
	{
		return NO_BANK_NOTE + String.join(", ", labels(fromBank)) + ".";
	}

	/** Starts {@link #noBankNote}. */
	public static final String NO_BANK_NOTE = "No bank can be reached first; only in the bank: ";
	/** Bank detours switched off in Settings and the place needs something only in the bank. */
	public static final String NO_DETOUR_NOTE = "Bank detours are off in Settings; only in the bank: ";

	/** A bank step's info: the bank's name and what to take out, for this player (the pictures of what they bank). */
	static StepInfo bankInfo(Route.Step s, PlayerState player)
	{
		List<StepInfo.TakeOut> out = new ArrayList<>();
		for (BankItem b : s.getTakeOutItems())
		{
			out.add(StepInfo.TakeOut.of(b, player));
		}
		return StepInfo.bank(s.getBankName(), out);
	}

	/** The route's last tile: the last step's end that has one, or -1. */
	static int lastTile(List<RouteStep> steps)
	{
		for (int i = steps.size() - 1; i >= 0; i--)
		{
			if (steps.get(i).getEndTile() != -1)
			{
				return steps.get(i).getEndTile();
			}
		}
		return -1;
	}

	/**
	 * For several places: at most this many full searches. The coarse lower bound rules out the others first: on the
	 * real data every clue needed 1 or 2 (136 master hot/cold spots: 1).
	 */
	public static final int MAX_SEARCHES = 24;

	/** The plugin data's docks (empty without sea data). */
	private List<Dock> docks()
	{
		SeaBits.Data sea = router.getData().getSea();
		return sea == null ? List.of() : sea.getDocks();
	}

	/** "Bank at Varrock west bank: take out coins 30, law rune" (just "Bank at ..." when nothing is needed). */
	public static String bankTitle(String bank, List<String> takeOut)
	{
		String where = "Bank at " + (bank == null || bank.isEmpty() ? "the bank" : bank);
		return takeOut.isEmpty() ? where : where + ": take out " + String.join(", ", takeOut);
	}

	/**
	 * The arrow in a card title ("Construct. cape -> Rimmington"). RuneLite's RuneScape fonts (small, normal, bold; the
	 * cards' fonts) have no glyph for U+2192 (the arrow); Java borrows one from a system font, so it looks different from
	 * the text and depends on the computer's fonts. "->" is in the RuneScape fonts and is what the game's own menus
	 * write ("Use Coins -> Bank booth").
	 */
	public static final String ARROW = "->";

}

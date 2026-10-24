package com.bestpath.core.data;

import com.bestpath.core.data.NoRouteReasons.SailStep;
import com.bestpath.core.data.NoRouteReasons.SailStep.SailWhy;
import com.bestpath.core.data.NoRouteReasons.SailStep.What;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaArea;
import com.bestpath.core.sea.SeaGrid;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "No route" by boat: when there is no way on land (or the place is out at sea), would a boat get the player there,
 * and what can't they use yet? Only the route search's own sailing rules ({@link Sailing}) are used. With the land
 * transports the player can use now, a few sailing searches, each allowing a little more:
 * <ol>
 * <li>the player has boats: every one of them (also those turned off in Settings) and shipwrights allowed, with the
 * player's own Sailing level, quests and inventory (a bottled boat only with its bottle);</li>
 * <li>then also every dock's level and quest met and the boat bottles carried;</li>
 * <li>then also the boats' parts upgraded so they may enter every sea area;</li>
 * <li>the player has no boat: a made-up raft that a shipwright brings to any shipwright's dock (a lost boat, as the
 * search brings one), every dock's level and quest met: "you have no boat" when that finds a way.</li>
 * </ol>
 * The first way found that needs something the player can't use is explained: the boat (none, turned off in Settings,
 * its shipwright turned off, a bottled boat's bottle, sea areas its parts keep it out of) and the docks it is boarded
 * and left at (their Sailing level and quest, checked against the real player). Not a search for the fewest things
 * over every boat and dock: the stages go from the fewest kinds of change to the most. Immutable result.
 */
final class SeaWay
{
	/** A Sailing level every dock allows (the relaxed searches). */
	private static final int ANY_LEVEL = 200;

	private final List<SailStep> steps;
	private final int things;
	private final int ticks;

	private SeaWay(List<SailStep> steps, int things, int ticks)
	{
		this.steps = List.copyOf(steps);
		this.things = things;
		this.ticks = ticks;
	}

	List<SailStep> getSteps()
	{
		return steps;
	}

	/** Distinct things the player can't use now. */
	int getThings()
	{
		return things;
	}

	/** The way's ticks. */
	int getTicks()
	{
		return ticks;
	}

	/**
	 * The way by boat to {@code target} and what it needs that the player can't use, or null: no sea routes (no sea
	 * data, a free world), no way by boat either, or one the player can already use (then this is no reason).
	 *
	 * @param cands   every land transport of this world type; {@code blocked}: those the player can't use now
	 */
	static SeaWay find(Sailing sailing, SailingSetup boats, Pathfinder pf, PluginData data, List<PlayerTransports.Candidate> cands,
		boolean[] blocked, PlayerState player, int sx, int sy, int sp, int target)
	{
		if (sailing == null || !player.isMembers())
		{
			return null;
		}
		SailingSetup setup = boats == null ? SailingSetup.NONE : boats;
		List<Transport> usable = new ArrayList<>();
		for (int i = 0; i < cands.size(); i++)
		{
			if (!blocked[i])
			{
				usable.add(cands.get(i).getTransport());
			}
		}
		List<Boat> mine = setup.getBoats();
		Boat raft = new Boat(1, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, Boat.LOST);
		int[] dest = destination(sailing, pf.getGraph(), target, mine.isEmpty() ? List.of(raft) : mine);
		if (dest == null)
		{
			return null;
		}
		PathQuery q = new PathQuery(sx, sy, sp, dest[0], dest[1], dest[2], usable, PluginRouter.PLANE_CHANGE_PENALTY, data.getDoors(),
			PluginRouter.DOOR_COST);
		int bottled = Sailing.bottled(mine);
		PlayerState relaxed = new PlayerState.Builder("Every dock allowed").members(true).allUnlocked(true)
			.carry(Boat.BOTTLE_FULL, Math.max(1, bottled)).build();
		List<Sailing.Options> stages = new ArrayList<>();
		if (!mine.isEmpty())
		{
			stages.add(new Sailing.Options(player.level(Skill.SAILING), player, mine, bottled, true, Sailing.DEFAULT_TURN_COST));
			stages.add(new Sailing.Options(ANY_LEVEL, relaxed, mine, bottled, true, Sailing.DEFAULT_TURN_COST));
			// then the same boats with parts that enter every sea area
			stages.add(new Sailing.Options(ANY_LEVEL, relaxed, upgraded(mine), bottled, true, Sailing.DEFAULT_TURN_COST));
		}
		else
		{
			stages.add(new Sailing.Options(ANY_LEVEL, relaxed, List.of(raft), 0, true, Sailing.DEFAULT_TURN_COST));
		}
		for (Sailing.Options o : stages)
		{
			Route r = sailing.route(q, o, pf);
			if (!r.isFound())
			{
				continue;
			}
			SeaWay w = explain(r, sailing, setup, player, mine.isEmpty());
			if (w != null)
			{
				return w;
			}
		}
		return null;
	}

	/**
	 * The boats with parts that let them into every sea area (a sail that resists storms, an adamant keel and helm or
	 * their own when better, an inoculation station, an eternal brazier); a raft still has no keel.
	 */
	private static List<Boat> upgraded(List<Boat> boats)
	{
		List<Boat> out = new ArrayList<>();
		for (Boat b : boats)
		{
			Boat u = new Boat(b.getSlot(), b.getType(), b.getHull(), Math.max(b.getSail(), 1), Math.max(b.getKeel(), 4),
				Math.max(b.getHelm(), 4), true, true, b.getFocus(), b.getDockId());
			out.add(b.getName() == null ? u : u.withName(b.getName()));
		}
		return out;
	}

	/** The sea areas the way's sailing crosses that the boat may not enter: "Crystalline waters (an adamant keel)". */
	private static List<String> areasKeptOut(Route r, Sailing sailing, Boat boat)
	{
		SeaGrid grid = sailing.getSeaGraph().getGrid();
		Set<SeaArea> out = EnumSet.noneOf(SeaArea.class);
		for (Route.Step s : r.getSteps())
		{
			Transport t = s.getTransport();
			if (t == null || Sailing.boatSlot(t.getId()) < 0)
			{
				continue; // not a step of the boat's (its tiles at sea are the course)
			}
			for (int tile : s.getTiles())
			{
				int x = Tiles.x(tile);
				int y = Tiles.y(tile);
				if (Tiles.plane(tile) == 0 && grid.isOcean(x, y))
				{
					SeaArea a = SeaArea.values()[grid.areaOrdinalAt(x, y)];
					if (!boat.canEnter(a))
					{
						out.add(a);
					}
				}
			}
		}
		List<String> texts = new ArrayList<>();
		for (SeaArea a : out)
		{
			texts.add(a.getLabel() + (a.getNeeds() == null ? "" : " (" + a.getNeeds() + ")"));
		}
		return texts;
	}

	/**
	 * Where the way by boat goes (the route's own rules, {@link com.bestpath.core.path.RouteDestination}: a boat ends
	 * only at a dock or on open sea): the place itself when it is open sea or can be walked on; a blocked spot out at
	 * sea with nothing to walk on within {@link PluginRouter#NEAR_RANGE}: the nearest open water one of {@code boats}
	 * can be at ({@link Sailing#openWaterNear}); else the nearest tile within range that can be walked on (same plane;
	 * above the main map inside its own region); null when none.
	 */
	private static int[] destination(Sailing sailing, CoarseGraph graph, int target, List<Boat> boats)
	{
		int dx = Tiles.x(target);
		int dy = Tiles.y(target);
		int dp = Tiles.plane(target);
		if (graph.componentAt(dx, dy, dp) >= 0 || sailing.isOpenSea(dx, dy, dp))
		{
			return new int[]{dx, dy, dp};
		}
		boolean own = dy >= PluginRouter.MAIN_MAP_TOP_Y;
		for (int r = 1; r <= PluginRouter.NEAR_RANGE; r++)
		{
			for (int x = dx - r; x <= dx + r; x++)
			{
				for (int y = dy - r; y <= dy + r; y++)
				{
					if (Math.max(Math.abs(x - dx), Math.abs(y - dy)) != r || own && ((x >> 6) != (dx >> 6) || (y >> 6) != (dy >> 6)))
					{
						continue;
					}
					if (graph.componentAt(x, y, dp) >= 0)
					{
						return new int[]{x, y, dp};
					}
				}
			}
		}
		int[] water = sailing.atSea(dx, dy, dp) ? sailing.openWaterNear(dx, dy, dp, boats) : null;
		return water == null ? null : new int[]{water[0], water[1], dp};
	}

	/** What a way by boat needs that the player can't use now; null when nothing (no reason). */
	private static SeaWay explain(Route r, Sailing sailing, SailingSetup setup, PlayerState player, boolean noBoat)
	{
		int slot = -1;
		boolean released = false;
		boolean brought = false;
		Dock board = null;
		Dock leave = null;
		for (Route.Step s : r.getSteps())
		{
			Transport t = s.getTransport();
			String id = t == null ? "" : t.getId();
			int[] tiles = s.getTiles();
			int at = tiles.length == 0 ? -1 : tiles[0];
			if (id.startsWith("sail-release:"))
			{
				released = true;
			}
			else if (id.startsWith("sail-bring:"))
			{
				brought = true;
			}
			else if (id.startsWith("sail-board:"))
			{
				board = dockAt(sailing, at);
			}
			else if (id.startsWith("sail-leave:"))
			{
				leave = dockAt(sailing, at);
			}
			if (Sailing.boatSlot(id) >= 0)
			{
				slot = Sailing.boatSlot(id);
			}
		}
		if (slot < 0)
		{
			return null; // no sailing in it: the land way (should not happen: there was none)
		}
		Boat boat = null;
		for (Boat b : setup.getBoats())
		{
			if (b.getSlot() == slot)
			{
				boat = b;
			}
		}
		List<SailStep> steps = new ArrayList<>();
		Set<String> things = new LinkedHashSet<>();
		// the boat
		Set<SailWhy> why = EnumSet.noneOf(SailWhy.class);
		Requirement req = Requirement.NONE;
		List<Requirement> unmet = new ArrayList<>();
		List<String> unmetTexts = new ArrayList<>();
		if (noBoat || boat == null)
		{
			why.add(SailWhy.NO_BOAT);
			things.add("no boat");
			boat = null;
		}
		else
		{
			if (setup.getAllowedSlots() != null && !setup.getAllowedSlots().contains(slot))
			{
				why.add(SailWhy.BOAT_OFF);
				things.add("boat off " + slot);
			}
			if (brought && !setup.isShipwrights() && (boat.needsShipwright() || boat.getFocus() == Boat.Focus.NONE))
			{
				why.add(SailWhy.SHIPWRIGHTS_OFF);
				things.add("shipwrights off");
			}
			int bottled = Sailing.bottled(setup.getBoats());
			if (released && !Sailing.bottlesInHand(player, bottled))
			{
				// every full boat bottle is the same item: one per bottled boat (Sailing.bottlesInHand)
				req = new Requirement.Item("Boat bottle (full)", new int[]{Boat.BOTTLE_FULL}, bottled, false, false);
				NoRouteReasons.collect(req, player, unmet, unmetTexts, new ArrayList<>(), new ArrayList<>());
				if (unmetTexts.isEmpty())
				{
					unmetTexts.add("a full boat bottle in your inventory for each bottled boat (" + bottled + ")");
				}
				why.add(SailWhy.BOTTLE);
				things.add("bottle");
			}
			// the way crosses water the boat's own parts keep it out of (found with parts that enter everywhere)
			List<String> areas = areasKeptOut(r, sailing, boat);
			if (!areas.isEmpty())
			{
				unmetTexts.addAll(areas);
				why.add(SailWhy.PARTS);
				things.addAll(areas);
			}
		}
		if (!why.isEmpty())
		{
			steps.add(new SailStep(What.BOAT, boat, null, why, req, unmet, unmetTexts));
		}
		// the docks it is boarded and left at
		addDock(steps, things, What.BOARD, board, boat, player);
		if (leave != null && leave != board)
		{
			addDock(steps, things, What.LEAVE, leave, boat, player);
		}
		return things.isEmpty() ? null : new SeaWay(steps, things.size(), r.getTicks());
	}

	/** A dock whose Sailing level or quest the player does not meet. */
	private static void addDock(List<SailStep> steps, Set<String> things, What what, Dock d, Boat boat, PlayerState player)
	{
		if (d == null)
		{
			return;
		}
		List<Requirement> parts = new ArrayList<>();
		if (d.getLevel() > 1)
		{
			parts.add(new Requirement.SkillLevel(Skill.SAILING, d.getLevel()));
		}
		if (!d.questRequirement().isNone())
		{
			parts.add(d.questRequirement());
		}
		Requirement req = parts.isEmpty() ? Requirement.NONE : Requirement.all(parts);
		List<Requirement> unmet = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		NoRouteReasons.collect(req, player, unmet, texts, new ArrayList<>(), new ArrayList<>());
		if (unmet.isEmpty())
		{
			return;
		}
		for (Requirement u : unmet)
		{
			things.add("req " + u.toText());
		}
		steps.add(new SailStep(what, boat, d.getName(), EnumSet.of(SailWhy.REQUIREMENTS), req, unmet, texts));
	}

	/** The dock whose land tile this is (sailing's docks), or null. */
	private static Dock dockAt(Sailing sailing, int tile)
	{
		if (tile < 0)
		{
			return null;
		}
		for (Dock d : sailing.getDocks())
		{
			for (int[] t : d.getLandTiles())
			{
				if (Tiles.pack(t[0], t[1], t[2]) == tile)
				{
					return d;
				}
			}
		}
		return null;
	}
}

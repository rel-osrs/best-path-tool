package com.bestpath.overlay;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.ClickHint;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.ScriptID;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * The next thing to do on the route, found in the game so the overlays can label it and make it glow: the next step
 * that is not a walk, its words, and what is clicked for it: the object the data records (by id, near where the step
 * is used), else an object or NPC with the menu's name near there, the spell in the spellbook; the items are matched
 * by id where they are drawn. Worked out once a game tick ({@link #update}), never in render(); the overlays only read
 * what is here. CLIENT THREAD ONLY.
 */
public final class StepTargets
{
	/** How far from where the step is used an object is looked for (the data's tile may be the object's corner). */
	static final int OBJECT_RADIUS = 4;
	/** How far an NPC may have wandered from where the step is used. */
	static final int NPC_RADIUS = 15;

	private final Client client;
	/** Which built object is which house furniture (the house scan's table); null = none. */
	private final IntFunction<String> furniture;

	private RouteStep step;
	/** The next step's card number (walks have no card, as in the sidebar) and how many cards the route has. */
	private int cardNumber;
	private int cardCount;
	private TileObject object;
	private NPC npc;
	private Widget spell;
	/** Where the text goes when there is no object or NPC: the step's tile (packed), or -1 above the player. */
	private int textTile = -1;
	/** A bank step: what to take out, by the game's item names ("Spade", "Feather x5"); empty otherwise. */
	private List<String> bankItems = List.of();
	/** The step uses house furniture that is not found (yet): no text anywhere until it is. */
	private boolean waitingForFurniture;
	/**
	 * In the house: the walk to the furniture the step uses (packed tiles, the player's first), drawn only and never
	 * used for pathfinding; null when none.
	 */
	private int[] housePath;
	/** The fairy ring's travel log is open and lists the ride's code: its entry, framed; null otherwise. */
	private Widget fairyEntry;
	private Widget fairyList;
	/** The code the open log was scrolled to (once per opening, so the player's own scrolling is never undone). */
	private String fairyScrolled;

	/** {@code furniture}: the house table's furniture name of a built object id, or null (HouseFurniture.thing). */
	public StepTargets(Client client, IntFunction<String> furniture)
	{
		this.client = client;
		this.furniture = furniture;
	}

	/**
	 * The index of the next step that is not a walk, from the current one on (the step being walked to ends at it), or
	 * -1. Pure.
	 */
	public static int nextAction(RouteResult r, Progress p)
	{
		int from = p != null && p.isFor(r) ? Math.max(0, p.getStep()) : 0;
		for (int i = from; i < r.getSteps().size(); i++)
		{
			if (r.getSteps().get(i).getKind() != RouteStep.Kind.WALK)
			{
				return i;
			}
		}
		return -1;
	}

	/** Once a game tick: the next step and what is clicked for it. */
	public void update(RouteResult r, Progress p)
	{
		int i = r == null ? -1 : nextAction(r, p);
		step = i < 0 ? null : r.getSteps().get(i);
		cardNumber = 0;
		cardCount = 0;
		for (int k = 0; r != null && k < r.getSteps().size(); k++)
		{
			if (r.getSteps().get(k).getKind() != RouteStep.Kind.WALK)
			{
				cardCount++;
				if (k <= i)
				{
					cardNumber = cardCount;
				}
			}
		}
		object = null;
		npc = null;
		spell = null;
		textTile = -1;
		bankItems = List.of();
		waitingForFurniture = false;
		housePath = null;
		fairyEntry = null;
		fairyList = null;
		Widget log = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
		if (log == null || log.isHidden())
		{
			fairyScrolled = null; // closed: the next opening scrolls again
		}
		if (step == null)
		{
			return;
		}
		if (step.getKind() == RouteStep.Kind.BANK)
		{
			bankItems = bankItems(step);
		}
		ClickHint h = step.getClick();
		WorldView wv = client.getTopLevelWorldView();
		Player me = client.getLocalPlayer();
		int near = h.getNear() != -1 ? h.getNear() : step.getMarkerTile();
		if (h.isSpell())
		{
			spell = findSpell(h.getTarget());
		}
		String code = fairyCode(h.getChoice());
		if (code != null && log != null && !log.isHidden())
		{
			showFairyCode(log, code);
		}
		if (!h.getHouseThings().isEmpty() && furniture != null && wv != null)
		{
			// in the house (an instance built from room templates): the furniture anywhere in the loaded scene
			object = findFurniture(wv, h);
			if (object != null)
			{
				housePath = houseWalk(wv, object, me);
				return;
			}
			// no words at all until the furniture is found (searched again every tick until then), so they never show
			// over the player first and then jump onto the furniture
			waitingForFurniture = true;
			return;
		}
		if (near == -1 || wv == null || step.getKind() == RouteStep.Kind.TELEPORT || step.getKind() == RouteStep.Kind.SAIL)
		{
			return; // nothing in the world to click: the text goes above the player
		}
		textTile = near;
		object = findObject(wv, h, near);
		if (object == null && !h.getTarget().isEmpty())
		{
			npc = findNpc(wv, h.getTarget(), near);
		}
	}

	/**
	 * A new scene loads (leaving the house, a teleport, a world hop, logging out): what was found in the old scene is
	 * forgotten at once. Outlining an object of a scene that is gone throws in the outline drawing, and the furniture
	 * search keeps its find for a few ticks, so this must not wait for the next {@link #update}.
	 */
	public void sceneChanged()
	{
		object = null;
		npc = null;
		housePath = null;
		furnitureFound = null;
		furnitureFor = null;
	}

	/** Ticks a house search is kept before it is done again (the furniture does not move). */
	private static final int FURNITURE_TICKS = 5;
	private TileObject furnitureFound;
	private ClickHint furnitureFor;
	private int furnitureAge;

	/**
	 * The house furniture the step uses, anywhere in the loaded scene on the player's plane (the house is the whole
	 * scene); searched again every few ticks or for another step. Null outside the house (none is found).
	 */
	private TileObject findFurniture(WorldView wv, ClickHint h)
	{
		if (h == furnitureFor && furnitureFound != null && furnitureAge++ < FURNITURE_TICKS)
		{
			return furnitureFound; // a miss is never kept: the house's scene may still be loading
		}
		furnitureFor = h;
		furnitureAge = 0;
		furnitureFound = null;
		Scene scene = wv.getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		int plane = wv.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return null;
		}
		for (Tile[] row : tiles[plane])
		{
			for (Tile t : row)
			{
				if (t == null)
				{
					continue;
				}
				for (GameObject g : t.getGameObjects())
				{
					if (g != null && h.usesFurniture(furniture.apply(g.getId())))
					{
						furnitureFound = g;
						return g;
					}
				}
				WallObject w = t.getWallObject();
				if (w != null && h.usesFurniture(furniture.apply(w.getId())))
				{
					furnitureFound = w;
					return w;
				}
				DecorativeObject d = t.getDecorativeObject();
				if (d != null && h.usesFurniture(furniture.apply(d.getId())))
				{
					furnitureFound = d;
					return d;
				}
			}
		}
		return null;
	}

	/** The object near {@code near}: the data's id first, else one named as the menu's target with its option. */
	private TileObject findObject(WorldView wv, ClickHint h, int near)
	{
		Scene scene = wv.getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		int plane = Tiles.plane(near);
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return null;
		}
		TileObject found = findObject(wv, tiles, plane, h, near, true);
		// by name only when no object has the menu's option too (a fairy ring's options change with its last destination)
		return found != null || h.getObjectId() >= 0 ? found : findObject(wv, tiles, plane, h, near, false);
	}

	private TileObject findObject(WorldView wv, Tile[][][] tiles, int plane, ClickHint h, int near, boolean withOption)
	{
		TileObject best = null;
		int bestD = Integer.MAX_VALUE;
		for (int dx = -OBJECT_RADIUS; dx <= OBJECT_RADIUS; dx++)
		{
			for (int dy = -OBJECT_RADIUS; dy <= OBJECT_RADIUS; dy++)
			{
				LocalPoint lp = LocalPoint.fromWorld(wv, Tiles.x(near) + dx, Tiles.y(near) + dy);
				if (lp == null)
				{
					continue;
				}
				Tile t = tiles[plane][lp.getSceneX()][lp.getSceneY()];
				if (t == null)
				{
					continue;
				}
				int d = Math.max(Math.abs(dx), Math.abs(dy));
				if (d >= bestD)
				{
					continue;
				}
				TileObject o = matching(t, h, withOption);
				if (o != null)
				{
					best = o;
					bestD = d;
				}
			}
		}
		return best;
	}

	private TileObject matching(Tile t, ClickHint h, boolean withOption)
	{
		for (GameObject g : t.getGameObjects())
		{
			if (g != null && matches(g, h, withOption))
			{
				return g;
			}
		}
		WallObject w = t.getWallObject();
		if (w != null && matches(w, h, withOption))
		{
			return w;
		}
		DecorativeObject d = t.getDecorativeObject();
		if (d != null && matches(d, h, withOption))
		{
			return d;
		}
		GroundObject g = t.getGroundObject();
		return g != null && matches(g, h, withOption) ? g : null;
	}

	private boolean matches(TileObject o, ClickHint h, boolean withOption)
	{
		if (h.getObjectId() >= 0)
		{
			return o.getId() == h.getObjectId();
		}
		if (h.getTarget().isEmpty())
		{
			return false;
		}
		ObjectComposition c = client.getObjectDefinition(o.getId());
		if (c != null && c.getImpostorIds() != null)
		{
			c = c.getImpostor();
		}
		return c != null && Text.removeTags(c.getName()).equalsIgnoreCase(h.getTarget())
			&& (!withOption || hasOption(c.getActions(), h.getOption()));
	}

	private static boolean hasOption(String[] actions, String option)
	{
		if (option.isEmpty() || actions == null)
		{
			return true;
		}
		for (String a : actions)
		{
			if (a != null && a.equalsIgnoreCase(option))
			{
				return true;
			}
		}
		return false;
	}

	/** The NPC named {@code name} nearest {@code near} (within {@link #NPC_RADIUS}), or null. */
	private NPC findNpc(WorldView wv, String name, int near)
	{
		NPC best = null;
		int bestD = Integer.MAX_VALUE;
		for (NPC n : wv.npcs())
		{
			if (n == null || n.getName() == null || !Text.removeTags(n.getName()).equalsIgnoreCase(name))
			{
				continue;
			}
			WorldPoint w = n.getWorldLocation();
			if (w.getPlane() != Tiles.plane(near))
			{
				continue;
			}
			int d = Math.max(Math.abs(w.getX() - Tiles.x(near)), Math.abs(w.getY() - Tiles.y(near)));
			if (d <= NPC_RADIUS && d < bestD)
			{
				best = n;
				bestD = d;
			}
		}
		return best;
	}

	/** The spell named {@code name} in the spellbook when it is open and shows it, or null. */
	private Widget findSpell(String name)
	{
		Widget layer = client.getWidget(InterfaceID.MagicSpellbook.SPELLLAYER);
		if (layer == null || layer.isHidden() || name.isEmpty())
		{
			return null;
		}
		Widget[] kids = layer.getStaticChildren();
		if (kids == null)
		{
			return null;
		}
		for (Widget w : kids)
		{
			if (w != null && !w.isHidden() && w.getName() != null && Text.removeTags(w.getName()).equalsIgnoreCase(name))
			{
				return w;
			}
		}
		return null;
	}

	/**
	 * The walk from the player to the furniture on the house's own walls as the game has loaded them ({@link HousePath});
	 * null when there is none (another plane, a closed door).
	 */
	private int[] houseWalk(WorldView wv, TileObject o, Player me)
	{
		CollisionData[] maps = wv.getCollisionMaps();
		int plane = wv.getPlane();
		if (me == null || maps == null || plane < 0 || plane >= maps.length || maps[plane] == null || o.getPlane() != plane)
		{
			return null;
		}
		int[][] fl = maps[plane].getFlags();
		LocalPoint at = LocalPoint.fromWorld(wv, me.getWorldLocation());
		if (at == null)
		{
			return null;
		}
		int[] box;
		if (o instanceof GameObject)
		{
			Point lo = ((GameObject) o).getSceneMinLocation();
			Point hi = ((GameObject) o).getSceneMaxLocation();
			box = new int[]{lo.getX(), lo.getY(), hi.getX(), hi.getY()};
		}
		else
		{
			LocalPoint lp = o.getLocalLocation();
			box = new int[]{lp.getSceneX(), lp.getSceneY(), lp.getSceneX(), lp.getSceneY()};
		}
		int size = fl.length;
		MovementRules.Flags f = (x, y) -> x < 0 || y < 0 || x >= size || y >= fl[x].length
			? CollisionFlags.BLOCKED : fl[x][y];
		int[] scene = HousePath.find(f, size, at.getSceneX(), at.getSceneY(), box);
		if (scene == null)
		{
			return null;
		}
		int[] out = new int[scene.length];
		for (int i = 0; i < scene.length; i++)
		{
			WorldPoint w = WorldPoint.fromScene(wv, scene[i] >>> 16, scene[i] & 0xFFFF, plane);
			out[i] = Tiles.pack(w.getX(), w.getY(), w.getPlane());
		}
		return out;
	}

	/**
	 * The travel log's entry for a fairy ring code ("ALP"), as its component id: the log has one entry per code (and one
	 * for its favourite star after it), in the order A-D, I-L, P-S (RuneLite gameval {@code InterfaceID.FairyringsLog}:
	 * AIP, AIQ ... DLS, two ids apart; checked: AIQ = AIP + 2, AJP = AIP + 8, BIP = AIP + 32, DLS = AIP + 126). -1 for
	 * anything that is not a code. Pure.
	 */
	static int fairyEntryId(String code)
	{
		code = fairyCode(code);
		if (code == null)
		{
			return -1;
		}
		int i = (code.charAt(0) - 'A') * 16 + (code.charAt(1) - 'I') * 4 + (code.charAt(2) - 'P');
		return InterfaceID.FairyringsLog.AIP + 2 * i;
	}

	/** The fairy ring code in a menu choice ("ALP", "alp"), upper case, or null. */
	static String fairyCode(String choice)
	{
		if (choice == null || choice.length() != 3)
		{
			return null;
		}
		String c = choice.toUpperCase(Locale.ROOT);
		return c.charAt(0) >= 'A' && c.charAt(0) <= 'D' && c.charAt(1) >= 'I' && c.charAt(1) <= 'L' && c.charAt(2) >= 'P'
			&& c.charAt(2) <= 'S' ? c : null;
	}

	/**
	 * The open travel log: the code's entry is framed, and the first time it is seen in this opening the log scrolls
	 * so the entry is in the middle (the game's own scrollbar script moves the bar with it). A code the player has
	 * never used is not in the log: nothing then.
	 */
	private void showFairyCode(Widget log, String code)
	{
		Widget e = client.getWidget(fairyEntryId(code));
		if (e == null || e.isHidden())
		{
			return;
		}
		fairyEntry = e;
		fairyList = log;
		if (code.equals(fairyScrolled))
		{
			return;
		}
		fairyScrolled = code;
		int max = Math.max(0, log.getScrollHeight() - log.getHeight());
		int y = Math.max(0, Math.min(max, e.getRelativeY() - (log.getHeight() - e.getHeight()) / 2));
		log.setScrollY(y);
		client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.FairyringsLog.SCROLLBAR,
			InterfaceID.FairyringsLog.CONTENTS, y);
	}

	/** The open fairy ring travel log's entry for the ride's code, or null. */
	public Widget getFairyEntry()
	{
		return fairyEntry;
	}

	/** The open travel log's list (what shows of it: the frame is cut to it), or null. */
	public Widget getFairyList()
	{
		return fairyList;
	}

	/** In the house: the walk to the furniture the next step uses (drawn only), or null. */
	public int[] getHousePath()
	{
		return housePath;
	}

	/** A bank step's items by their game names, with the count when more than one ("Feather x5"). */
	private List<String> bankItems(RouteStep s)
	{
		List<String> out = new ArrayList<>();
		for (StepInfo.TakeOut t : s.getInfo().getTakeOut())
		{
			String name = null;
			if (t.getItemId() >= 0)
			{
				ItemComposition c = client.getItemDefinition(t.getItemId());
				name = c == null ? null : c.getName();
			}
			if (name == null || name.isEmpty() || name.equalsIgnoreCase("null"))
			{
				name = t.getLabel();
			}
			out.add(t.getCount() > 1 ? name + " x" + t.getCount() : name);
		}
		return out;
	}

	/** A bank step's items by their game names (empty for any other step). */
	public List<String> getBankItems()
	{
		return bankItems;
	}

	/** True while the step's house furniture is not found yet: show no words (they would jump onto it a moment later). */
	public boolean isWaitingForFurniture()
	{
		return waitingForFurniture;
	}

	/** The longest bank words that still list the items; longer ones give only the count. */
	static final int MAX_WORDS = 40;

	/**
	 * The words for the step in the game world: its game text; for a bank step only what to take out, by the items'
	 * names, or with many items the count and where to see them all.
	 */
	public static String words(RouteStep s, List<String> bankItems, boolean boxShown)
	{
		if (s.getKind() == RouteStep.Kind.STASH)
		{
			// kept short in the game world; the card names the STASH unit's place
			return "Put items back";
		}
		if (s.getKind() != RouteStep.Kind.BANK)
		{
			return s.getGameText(); // shorter than the card's title for some steps
		}
		if (bankItems.isEmpty())
		{
			return "Use the bank";
		}
		String all = "Take out: " + String.join(", ", bankItems);
		// more than 3 items or MAX_WORDS characters: only the count; the list is on the card / in the box
		if (bankItems.size() > 3 || all.length() > MAX_WORDS)
		{
			return "Take out " + bankItems.size() + " items (see the " + (boxShown ? "box" : "side panel") + ")";
		}
		return all;
	}

	/** The next step that is not a walk, or null (no route, or only walking left). */
	public RouteStep getStep()
	{
		return step;
	}

	/** The next step's card number (1-based, walks have no card, as in the sidebar), or 0. */
	public int getCardNumber()
	{
		return step == null ? 0 : cardNumber;
	}

	/** How many cards the route has. */
	public int getCardCount()
	{
		return cardCount;
	}

	/** The object to click, found in the loaded scene, or null. */
	public TileObject getObject()
	{
		return object;
	}

	/** The NPC to click, or null. */
	public NPC getNpc()
	{
		return npc;
	}

	/** The spell to cast, shown in the open spellbook, or null. */
	public Widget getSpell()
	{
		return spell;
	}

	/** Where the text goes without an object or NPC: the step's tile (packed), or -1 (above the player). */
	public int getTextTile()
	{
		return textTile;
	}

	/** True when this item id is one the next step is done with. */
	public boolean usesItem(int id)
	{
		return step != null && step.getClick().usesItem(id);
	}
}

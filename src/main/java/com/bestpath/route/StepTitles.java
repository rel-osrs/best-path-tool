package com.bestpath.route;

import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.NoRouteReasons;
import com.bestpath.core.data.PlayerTransports;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.TransportMenus;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.Dock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A route step's words: one core step as the sidebar shows it ({@link #toStep}), a transport card's title in the
 * game's menu words, the sailing cards' titles, the words written in the game world ({@link #gameText}), and the
 * card's label ({@link #kind}).
 */
final class StepTitles
{
	/** The arrow in card titles ({@link CoreRouteEngine#ARROW}). */
	private static final String ARROW = CoreRouteEngine.ARROW;

	private StepTitles()
	{
	}

	/**
	 * One core step as the sidebar shows it. The cards show no coordinates (they mean little to most players): a
	 * transport's, a sailing step's and the bank's detail line is empty (the card then has no such line); a walk's says
	 * its tiles (walks have no card). {@code docks} (the plugin data's) give the sailing cards that are one click their
	 * menu text.
	 */
	static RouteStep toStep(Route.Step s, PlayerTransports.Result from, List<TransportLine> allLines, List<String> extraNotes,
		List<Dock> docks)
	{
		int[] tiles = s.getTiles();
		int last = tiles.length == 0 ? -1 : tiles[tiles.length - 1];
		String to = last == -1 ? "" : "to " + Tiles.x(last) + ", " + Tiles.y(last) + ", " + Tiles.plane(last);
		Transport t = s.getTransport();
		TransportLine line = t == null || from == null ? null : from.lineOf(t);
		List<String> notes = new ArrayList<>();
		if (t != null && !t.isConfirmed())
		{
			notes.add("Unconfirmed transport");
		}
		if (line != null && line.isCanFail())
		{
			notes.add("You can fail this; you then try again.");
		}
		if (line != null && !line.getPlayerNote().isEmpty())
		{
			notes.add(line.getPlayerNote());
		}
		notes.addAll(extraNotes);
		switch (s.getKind())
		{
			case BANK:
			{
				// a route that begins at a bank: where, and what later steps and the destination need
				int at = tiles.length == 0 ? -1 : tiles[0];
				return new RouteStep(RouteStep.Kind.BANK, CoreRouteEngine.bankTitle(s.getBankName(), s.getTakeOut()), "", s.getTicks(),
					notes, null, new int[0], at, at);
			}
			case WALK:
				return new RouteStep(RouteStep.Kind.WALK, "Walk " + to, (tiles.length - 1) + " tiles", s.getTicks(),
					notes, null, tiles, -1);
			case SAIL:
				// "Sail Boat 1: Sloop to Port Sarim"; the tiles are the boat's course (plane 0) and its corners the straight
				// legs, drawn as arrows
				return new RouteStep(RouteStep.Kind.SAIL, t == null ? "Sail " + to : t.getName(), "", s.getTicks(), notes, null,
					tiles, -1).withCourse(s.getCorners(), s.getCoursePoints());
			case TELEPORT:
				// from the start, or from the tile the route got to first (a Wilderness teleport limit): marked there
				return new RouteStep(RouteStep.Kind.TELEPORT, transportTitle(t, menuOf(s, line, allLines), true), "", s.getTicks(),
					notes, id(t), tiles, tiles.length >= 2 ? tiles[0] : -1, last);
			case TRANSPORT:
			default:
				// a transport's tiles are its start (if it has one) then its landing (where progress moves on)
				int marker = tiles.length >= 2 ? tiles[0] : -1;
				// boarding, leaving and bringing the boat happen at the dock's land tile (a shipwright's: at the shipwright)
				String sailing = isSailing(t) ? sailingTitle(t, docks) : null;
				return new RouteStep(isSailing(t) ? RouteStep.Kind.BOAT : kind(line),
					sailing != null ? sailing : transportTitle(t, menuOf(s, line, allLines), false), "", s.getTicks(), notes, id(t),
					new int[0], marker, last);
		}
	}

	/**
	 * The words written in the game world for a step ({@link GameText}): sailing "Sail -> Rellekka"
	 * and "Leave the boat" (the card keeps the boat's name); a transport's shorter words; else the title.
	 *
	 * @param menu the menu text the step's title came from (null: the transport's name)
	 */
	static String gameText(RouteStep s, Transport t, TransportLine line, MenuText menu)
	{
		if (s.getKind() == RouteStep.Kind.SAIL)
		{
			return GameText.sail(s.getTitle());
		}
		if (t == null || s.getKind() == RouteStep.Kind.BANK || s.getKind() == RouteStep.Kind.WALK)
		{
			return s.getTitle();
		}
		if (isSailing(t))
		{
			// "Leave the boat"; "Sail to Rellekka" (the sailing step that lands at a dock) -> "Sail -> Rellekka"
			return t.getId().startsWith("sail-leave:") ? "Leave the boat" : t.getId().startsWith("sail-arrive:") ? GameText.sail(s.getTitle())
				: s.getTitle();
		}
		// a two-way transport used from its start: it lands on the line's end
		boolean forward = line == null || t.getEndX() == line.getEndX() && t.getEndY() == line.getEndY()
			&& t.getEndPlane() == line.getEndPlane();
		return GameText.of(s.getTitle(), menu != null, t.getName(), s.getKind() == RouteStep.Kind.TELEPORT, forward);
	}

	/**
	 * A sailing card's title in the game's menu words, with the player's boat name, or null to keep core's name. Core
	 * names the boat by {@code Boat.label()}: the player's own boat name as the game shows it, else "Boat 1: Sloop". In
	 * the cache, the 16 NPCs with "Recover-boat" (op 4) are named exactly as the docks' shipwrights ("Shipwright Sam" ...
	 * "Junior Jim"); the dock's land object ("Gangplank" / "Mooring point") has "Board" first.
	 * <ul>
	 * <li>a shipwright brings the boat: "Recover-boat Shipwright Sam -> Jolly Raven"</li>
	 * <li>boarding: "Board Jolly Raven"</li>
	 * <li>a teleport focus brings the boat: "Summon Boat -> Jolly Raven (teleport focus)" (the game's boat list is
	 * "Summon Boat", table 149)</li>
	 * <li>a bottled boat released: "Unbottle-boat -> Jolly Raven" (the full bottle's option)</li>
	 * <li>sailing and leaving keep core's names, which already say the boat: "Sail Jolly Raven to Rellekka", "Leave
	 * Jolly Raven at Rellekka"</li>
	 * </ul>
	 */
	static String sailingTitle(Transport t, List<Dock> docks)
	{
		String id = t.getId();
		String name = t.getName();
		if (id.startsWith("sail-bring:"))
		{
			for (Dock d : docks)
			{
				String w = d.getShipwright();
				String head = w + " brings ";
				String tail = " to " + d.getName();
				int end = name.lastIndexOf(tail);
				if (w != null && name.startsWith(head) && end > head.length())
				{
					return "Recover-boat " + w + " " + ARROW + " " + name.substring(head.length(), end);
				}
			}
			for (Boat.Focus f : Boat.Focus.values())
			{
				String head = f.getLabel() + ": ";
				int end = name.lastIndexOf(" brought to ");
				if (f != Boat.Focus.NONE && name.startsWith(head) && end > head.length())
				{
					return "Summon Boat " + ARROW + " " + name.substring(head.length(), end) + " (" + f.getLabel().toLowerCase(Locale.ROOT) + ")";
				}
			}
			return null;
		}
		if (id.startsWith("sail-release:"))
		{
			int end = name.lastIndexOf(" from its bottle at ");
			return name.startsWith("Release ") && end > "Release ".length() ? "Unbottle-boat " + ARROW + " " + name.substring("Release ".length(), end) : null;
		}
		if (id.startsWith("sail-board:"))
		{
			for (Dock d : docks)
			{
				String tail = " at " + d.getName();
				if (name.startsWith("Board ") && name.endsWith(tail) && name.length() > "Board ".length() + tail.length())
				{
					return name.substring(0, name.length() - tail.length());
				}
			}
			return null;
		}
		return null;
	}

	/**
	 * A transport card's title: the exact menu text of the click when the export knows it ("Construct. cape ->
	 * Rimmington", "Climb-up Ladder", "Spirit tree -> Battlefield of Khazard"); else the transport's name, a teleport's
	 * with "Teleport: " when its name lacks the word.
	 */
	static String transportTitle(Transport t, MenuText menu, boolean teleport)
	{
		if (menu != null)
		{
			return menu.title(ARROW);
		}
		return teleport ? teleportTitle(name(t)) : name(t);
	}

	/** The menu text of a route step's transport, the way it is used (null: not known, or a step core's sailing made). */
	static MenuText menuOf(Route.Step s, TransportLine line, List<TransportLine> allLines)
	{
		Transport t = s.getTransport();
		if (t == null || line == null || isSailing(t))
		{
			return null;
		}
		// a transport step's tiles are its start (if it has one) then its landing; a teleport's only its landing
		int[] tiles = s.getTiles();
		int from = tiles.length >= 2 ? tiles[0] : -1;
		return from == -1 ? line.getMenu() : TransportMenus.of(t, line, allLines, Tiles.x(from), Tiles.y(from), Tiles.plane(from));
	}

	/** The menu text of a transport the player can't use now, the way the route would use it (null: not known). */
	static MenuText menuOf(NoRouteReasons.WayStep s)
	{
		TransportLine line = s.getLine();
		if (line == null || isSailing(s.getTransport()))
		{
			return null;
		}
		if (s.getFromStop() != null)
		{
			return MenuText.ride(s.getFromStop().getMenu(), line.getMenu());
		}
		return line.getKind() == TransportLine.Kind.TWO_WAY ? line.menuFor(!s.isReverse()) : line.getMenu();
	}

	// teleport names often say "Teleport" already ("Varrock Teleport"); only add the word when they don't
	static String teleportTitle(String name)
	{
		return name.toLowerCase(Locale.ROOT).contains("teleport") ? name : "Teleport: " + name;
	}

	/**
	 * The card's label: boats, carts and network rides "Boat"; gates and barriers that need something (PASS) "Door";
	 * everything else with a start "Climb".
	 */
	static RouteStep.Kind kind(TransportLine line)
	{
		if (line != null && (line.getKind() == TransportLine.Kind.NETWORK || "TRAVEL".equals(line.getCategory())))
		{
			return RouteStep.Kind.BOAT;
		}
		if (line != null && "PASS".equals(line.getCategory()))
		{
			return RouteStep.Kind.DOOR;
		}
		return RouteStep.Kind.TRAVERSAL;
	}

	private static String name(Transport t)
	{
		return t == null ? "Transport" : t.getName();
	}

	/** No id (no skip / blacklist) for the sailing steps the search makes itself. */
	private static String id(Transport t)
	{
		return t == null || isSailing(t) ? null : t.getId();
	}

	/**
	 * A step core's sailing made (board, leave, bring the boat, release it from its bottle, sail, greater focus
	 * teleport): ids start "sail".
	 */
	static boolean isSailing(Transport t)
	{
		return t != null && (t.getId().startsWith("sail-") || t.getId().startsWith("sail:"));
	}

	/** The kind of the player's boat a sailing step uses (its id names the boat's slot), or null. */
	static BoatType boatKind(Transport t, List<Boat> boats)
	{
		int slot = t == null ? -1 : Sailing.boatSlot(t.getId());
		for (Boat b : boats)
		{
			if (b.getSlot() == slot)
			{
				return b.getType();
			}
		}
		return null;
	}
}

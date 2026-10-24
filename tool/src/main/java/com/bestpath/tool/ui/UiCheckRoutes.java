package com.bestpath.tool.ui;

import static com.bestpath.tool.ui.UiCheck.get;
import static com.bestpath.tool.ui.UiCheck.run;
import static com.bestpath.tool.ui.UiCheck.snap;
import static com.bestpath.tool.ui.UiCheck.waitForRoute;
import static com.bestpath.tool.ui.UiCheckControls.clickBox;

import com.bestpath.core.path.Route;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.tool.req.TestPlayer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * DEVELOPER AID: the {@link UiCheck} route modes: "routes" (each listed route searched and pictured), "examples"
 * (the Route tab's example list) and "sailing" (routes with a test boat).
 */
final class UiCheckRoutes
{
	private UiCheckRoutes()
	{
	}

	/**
	 * sailing on, with a test boat (teak skiff, oak sails, moored at Port Sarim) unless a name says otherwise;
	 * then the listed routes as with "routes" ("-raft", "-sloop", "-atsea": the boat is at the route's start;
	 * "-nowright": shipwrights may not bring the boat; "-focus" / "-greater": teleport focus; "-capsized": the
	 * boat has capsized; "-bottled": it is bottled and its bottle is in the inventory)
	 */
	static void setUpSailing(MainWindow window, String[] args) throws Exception
	{
		String all = String.join(" ", args);
		BoatType type = all.contains("-sloop") ? BoatType.SLOOP
			: all.contains("-raft") ? BoatType.RAFT : BoatType.SKIFF;
		Boat.Focus focus = all.contains("-greater") ? Boat.Focus.GREATER
			: all.contains("-focus") ? Boat.Focus.TELEPORT : Boat.Focus.NONE;
		int dock = all.contains("-atsea") ? Boat.AT_START : all.contains("-capsized")
			? Boat.CAPSIZED : all.contains("-bottled") ? Boat.BOTTLED : 0;
		Boat boat = new Boat(1, type, 2, 1, 0, 0, false, false, focus, dock);
		run(() ->
		{
			window.devRoute().setBottles(all.contains("-bottled") ? 1 : 0);
			window.devRoute().setBoats(List.of(boat));
			window.devRoute().setSailingLevel(99);
			window.devRoute().setShipwrights(!all.contains("-nowright"));
			window.devRoute().setSailingOn(true);
			clickBox(window, "Sea (sailing)");
			clickBox(window, "Docks (sailing)");
		});
	}

	/** The same arguments with "routes" in place of the mode's name. */
	static String[] asRoutes(String[] args)
	{
		String[] rest = new String[args.length];
		rest[0] = "routes";
		System.arraycopy(args, 1, rest, 1, args.length - 1);
		return rest;
	}

	/** The Route tab's example list, turned into "routes" arguments. */
	static String[] exampleArgs(File workspace) throws Exception
	{
		List<String> list = new ArrayList<>(List.of("routes"));
		int n = 0;
		for (String line : java.nio.file.Files.readAllLines(new File(workspace, "data/route-examples.txt").toPath()))
		{
			String[] p = line.trim().split("\\|");
			if (line.trim().startsWith("#") || p.length < 3)
			{
				continue;
			}
			n++;
			String flags = p.length > 3 ? String.join("|", java.util.Arrays.copyOfRange(p, 3, p.length)) : "";
			String name = "example" + n + (flags.contains("unconfirmed") ? "-unconfirmed" : "");
			list.add(name + ":" + p[1].trim() + ">" + p[2].trim());
			System.out.println(name + " = " + p[0].trim());
		}
		return list.toArray(new String[0]);
	}

	/** Only the listed routes: "name:x,y,p>x,y,p" each; the map is fitted to the route. */
	static void routes(MainWindow window, String[] args) throws Exception
	{
		for (int i = 1; i < args.length; i++)
		{
			String[] nameAndRoute = args[i].split(":");
			String[] ends = nameAndRoute[1].split(">");
			int[] s = parse(ends[0]);
			int[] e = parse(ends[1]);
			String name = nameAndRoute[0];
			run(() -> startRoute(window, name, s, e));
			waitForRoute();
			run(() -> fitToRoute(window, s, e));
			Thread.sleep(2500);
			if (name.contains("-steps"))
			{
				System.out.println(get(() -> window.devRoute().getRoute() == null ? "no route"
					: window.devRoute().getRoute().getSteps().stream().map(st -> "  " + st.getKind() + " " + st.getTicks() + "t " + (st.getTransport() == null ? st.getTiles().length + " tiles" : st.getTransport().getName())).collect(java.util.stream.Collectors.joining("\n"))));
			}
			System.out.println(name + ": " + get(() -> window.devRoute().getStatus()) + "  tiles hash " + get(() -> window.devRoute().getRoute() == null ? 0 : java.util.Arrays.deepHashCode(window.devRoute().getRoute().getSteps().stream().map(Route.Step::getTiles).toArray())));
			snap(name);
		}
	}

	private static int[] parse(String s)
	{
		String[] p = s.split(",");
		return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), p.length > 2 ? Integer.parseInt(p[2].trim()) : 0};
	}

	/** Route tool and tab, the options and test player the route's name asks for, the map between the ends, then the search. */
	private static void startRoute(MainWindow window, String name, int[] s, int[] e)
	{
		window.devSelectTool('r');
		window.devSelectTab(3);
		window.devRoute().setShowSearched(name.endsWith("-searched"));
		if (window.devRoute().isUseUnconfirmed() != name.contains("-unconfirmed"))
		{
			window.devRoute().setUseUnconfirmed(name.contains("-unconfirmed"));
		}
		setTestPlayers(window, name);
		window.devMainMap().setPlane(s[2]);
		int w = Math.max(Math.abs(s[0] - e[0]), Math.abs(s[1] - e[1])) + 20;
		window.devMainMap().goTo((s[0] + e[0]) / 2, (s[1] + e[1]) / 2, Math.max(1, Math.min(20, 900.0 / w)));
		window.devRoute().setStart(s[0], s[1], s[2]);
		window.devRoute().setEnd(e[0], e[1], e[2]);
	}

	/** The test player each of the route name's player words asks for ("-house", "-f2p", "-low", "-noitems", "-fresh", "-coins"). */
	private static void setTestPlayers(MainWindow window, String name)
	{
		if (name.contains("-house"))
		{
			window.devRoute().setPlayer(housePlayer());
		}
		if (name.contains("-f2p"))
		{
			window.devRoute().setPlayer(freePlayer());
			window.devMainMap().setShowFreeLand(true);
		}
		if (name.contains("-low"))
		{
			window.devRoute().setPlayer(lowLevelPlayer());
		}
		if (name.contains("-noitems"))
		{
			window.devRoute().setPlayer(noItemsPlayer());
		}
		if (name.contains("-fresh") || name.contains("-coins"))
		{
			window.devRoute().setPlayer(freshPlayer(name.contains("-coins")));
		}
	}

	/** A member with a house in Rimmington (ornate jewellery box, Varrock portal, garden fairy ring) and nothing else. */
	private static TestPlayer housePlayer()
	{
		TestPlayer p = new TestPlayer();
		p.name = "House only";
		p.house = "Rimmington";
		p.houseThings.add("ornate jewellery box");
		p.houseThings.add("portal nexus: Varrock Teleport");
		p.houseThings.add("fairy ring");
		return p;
	}

	/** A free-world player with every level, unlock and item: only free land, free transports. */
	private static TestPlayer freePlayer()
	{
		TestPlayer p = new TestPlayer();
		p.name = "Free player";
		p.members = false;
		p.allUnlocked = true;
		p.allItems = true;
		for (Skill sk : Skill.values())
		{
			p.levels.put(sk.getLabel(), 99);
		}
		return p;
	}

	/** A member with every unlock and item but all levels 1 (Hitpoints 10): level-gated doors and shortcuts shut. */
	private static TestPlayer lowLevelPlayer()
	{
		TestPlayer p = new TestPlayer();
		p.name = "Low levels";
		p.allUnlocked = true;
		p.allItems = true;
		return p;
	}

	/** A maxed member with every unlock but no items at all (no light source, ghostspeak, runes ...): dark areas shut. */
	private static TestPlayer noItemsPlayer()
	{
		TestPlayer p = new TestPlayer();
		p.name = "No items";
		p.allUnlocked = true;
		for (Skill sk : Skill.values())
		{
			p.levels.put(sk.getLabel(), 99);
		}
		return p;
	}

	/** The built-in fresh account: a member with nothing (no quests, no items, level 1); {@code coins}: carrying 1000 coins. */
	private static TestPlayer freshPlayer(boolean coins)
	{
		TestPlayer p = TestPlayer.fresh();
		if (coins)
		{
			p.name = "Fresh account with coins";
			p.builtIn = false;
			p.inventory.put(String.valueOf(com.bestpath.core.req.PlayerState.COINS), 1000);
		}
		return p;
	}

	/** Fits the view to the whole route (it can wander far from the straight line). */
	private static void fitToRoute(MainWindow window, int[] s, int[] e)
	{
		Route r = window.devRoute().getRoute();
		if (r == null || !r.isFound())
		{
			return;
		}
		int x0 = Math.min(s[0], e[0]);
		int x1 = Math.max(s[0], e[0]);
		int y0 = Math.min(s[1], e[1]);
		int y1 = Math.max(s[1], e[1]);
		for (Route.Step st : r.getSteps())
		{
			for (int t : st.getTiles())
			{
				if (Tiles.plane(t) == s[2])
				{
					x0 = Math.min(x0, Tiles.x(t));
					x1 = Math.max(x1, Tiles.x(t));
					y0 = Math.min(y0, Tiles.y(t));
					y1 = Math.max(y1, Tiles.y(t));
				}
			}
		}
		double scale = Math.min(960.0 / (x1 - x0 + 16), 520.0 / (y1 - y0 + 16));
		window.devMainMap().goTo((x0 + x1) / 2, (y0 + y1) / 2, Math.max(0.5, Math.min(20, scale)));
	}
}

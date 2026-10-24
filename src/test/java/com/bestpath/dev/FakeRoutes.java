package com.bestpath.dev;

import com.bestpath.core.data.PlayerTransports;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TickGroups;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.Skill;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.RouteEngine;
import com.bestpath.route.RouteJob;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import com.bestpath.route.TransportSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DEV ONLY (src/test, never in the plugin jar): made-up routes and players for
 * the UI preview and the UI pictures. The steps look like real ones but are
 * sample data, not searched.
 */
public final class FakeRoutes
{
	public static final RouteRequest DESTINATION = new RouteRequest(3233, 9860, 0);

	private FakeRoutes()
	{
	}

	/** Fake players to switch between in the preview. */
	public static Map<String, PlayerSnapshot> players()
	{
		Map<String, PlayerSnapshot> m = new LinkedHashMap<>();
		m.put("Mid-level (Magic 45, Agility 55)", new PlayerSnapshot(3222, 3218, 0,
			// air runes carried, fire runes in the bank, no law runes: the sample teleport card shows each mark
			new PlayerState.Builder("Mid-level").members(true).level(Skill.MAGIC, 45).level(Skill.AGILITY, 55).carry(556, 100)
				.bank(554, 50).build(), List.of()));
		m.put("Fresh account (all 1s)", new PlayerSnapshot(3222, 3218, 0, PlayerStateBuilder.fresh(), List.of()));
		m.put("Maxed", new PlayerSnapshot(3222, 3218, 0, PlayerStateBuilder.maxed(), List.of()));
		m.put("Free-to-play, Magic 30", new PlayerSnapshot(3222, 3218, 0,
			new PlayerState.Builder("F2P").members(false).level(Skill.MAGIC, 30).build(), List.of()));
		return m;
	}

	/** The sample route as a mid-level member with nothing skipped: 8 steps. */
	public static RouteResult eightSteps(long generation)
	{
		return build(generation, DESTINATION, players().values().iterator().next(), new java.util.HashSet<>(),
			TransportSettings.defaults());
	}

	/** A route that depends on the player and the skipped / blacklisted transports. */
	static RouteResult build(long generation, RouteRequest to, PlayerSnapshot p, java.util.Set<String> skipped,
		TransportSettings settings)
	{
		PlayerState s = p.getState();
		List<RouteStep> steps = new ArrayList<>();
		if ((p.getX() != 3222 || p.getY() != 3218) && p.getPlane() == 0)
		{
			// the fake player walked off (preview "walk along" mode): first back to the usual start
			steps.add(walk("Walk to 3222, 3218, 0", p.getX(), p.getY(), 3222, 3218));
		}
		steps.add(walk("Walk to 3206, 3209, 0", 3222, 3218, 3206, 3209));
		steps.add(new RouteStep(RouteStep.Kind.DOOR, "Open the door", "Lumbridge Castle", 2, List.of(), "t-door-lumb",
			new int[0], Tiles.pack(3207, 3210, 0)));
		boolean tele = s.level(Skill.MAGIC) >= 25 && usable("t-varrock-tele", skipped, settings)
			&& settings.isEnabled(TransportSettings.Category.TELEPORT_SPELLS)
			&& !settings.avoids(TransportSettings.Avoid.RUNES);
		if (tele)
		{
			// no coordinates on the cards; each need with its mark for the fake player, as the real
			// engine makes them (the requirement model's own checks)
			steps.add(new RouteStep(RouteStep.Kind.TELEPORT, "Varrock Teleport", "", 4,
				List.of(), "t-varrock-tele", new int[0], -1, Tiles.pack(3212, 3424, 0)).withInfo(new StepInfo(
				"Varrock Teleport", TransportSettings.Category.TELEPORT_SPELLS, "TELEPORT", false, "spell", "", -1,
				List.of(StepInfo.Need.skill(Skill.MAGIC, 25).withStatus(mark(new Requirement.SkillLevel(Skill.MAGIC, 25), s)),
					StepInfo.Need.rune("air", 556, 3).withStatus(mark(runes("air", 3, 556, 1381), s)),
					StepInfo.Need.rune("fire", 554, 1).withStatus(mark(runes("fire", 1, 554, 1387), s)),
					StepInfo.Need.rune("law", 563, 1).withStatus(mark(runes("law", 1, 563, -1), s))))));
			steps.add(walk("Walk to 3237, 3459, 0", 3212, 3424, 3237, 3459));
		}
		else
		{
			steps.add(walk("Walk to 3237, 3459, 0", 3206, 3209, 3237, 3459));
		}
		steps.add(new RouteStep(RouteStep.Kind.TRAVERSAL, "Climb down the manhole", "", 2,
			List.of(), "t-manhole", new int[0], Tiles.pack(3237, 3458, 0), Tiles.pack(3237, 9858, 0)).withInfo(new StepInfo(
			"Manhole", TransportSettings.Category.STAIRS_LADDERS, "CLIMB_DOWN", false, "", "", -1, List.of())));
		boolean pipe = s.level(Skill.AGILITY) >= 51 && s.isMembers() && usable("t-pipe", skipped, settings)
			&& settings.isEnabled(TransportSettings.Category.SHORTCUTS);
		if (pipe)
		{
			steps.add(walk("Walk to 3150, 9906, 0", 3237, 9858, 3150, 9906));
			steps.add(new RouteStep(RouteStep.Kind.TRAVERSAL, "Squeeze through the obstacle pipe",
				"Agility shortcut", 6, List.of("You can fail this squeeze; you then try again from the same side."),
				"t-pipe", new int[0], Tiles.pack(3150, 9906, 0)).withInfo(new StepInfo("Obstacle pipe",
				TransportSettings.Category.SHORTCUTS, "SHORTCUT", false, "", "", -1,
				List.of(StepInfo.Need.skill(Skill.AGILITY, 51).withStatus(mark(new Requirement.SkillLevel(Skill.AGILITY, 51), s))))));
		}
		else
		{
			steps.add(walk("Walk to 3160, 9900, 0 (long way)", 3237, 9858, 3160, 9900));
		}
		steps.add(walk("Walk to " + RouteRequest.tileLabel(to.getX(), to.getY(), to.getPlane()), 3150, 9906, to.getX(),
			to.getY()));
		// the player's own tick changes (- / + on the cards, teleport categories and networks in Settings), as the
		// real engine applies them: the core's own sum (TickGroups.ticks)
		steps.replaceAll(st -> withTicks(st, settings));
		return RouteResults.ready(generation, to, steps);
	}

	/** The mark of one need for the fake player (the requirement model's own check). */
	private static com.bestpath.route.NeedStatus mark(Requirement r, PlayerState s)
	{
		return com.bestpath.route.NeedStatus.of(r.check(s));
	}

	/** Runes of one kind: the rune itself and (if any) one staff that gives them. */
	private static Requirement runes(String rune, int count, int runeId, int staffId)
	{
		return new Requirement.Runes(rune, count, new int[]{runeId}, staffId < 0 ? new int[0] : new int[]{staffId});
	}

	/** The sample route's transports as exported data lines (the Settings tab's totals use them, as the real data's). */
	static final List<TransportLine> LINES = List.of(
		line("t-door-lumb", TransportLine.Kind.ONE_WAY, "Door", "PASS", new int[]{3207, 3210, 0}, new int[]{3206, 3209, 0}, 2, ""),
		line("t-varrock-tele", TransportLine.Kind.TELEPORT, "Varrock Teleport", "", new int[3], new int[]{3212, 3424, 0}, 5,
			"spell"),
		line("t-manhole", TransportLine.Kind.ONE_WAY, "Manhole", "CLIMB_DOWN", new int[]{3237, 3458, 0},
			new int[]{3237, 9858, 0}, 2, ""),
		line("t-pipe", TransportLine.Kind.TWO_WAY, "Obstacle pipe", "SHORTCUT", new int[]{3150, 9906, 0},
			new int[]{3153, 9906, 0}, 6, ""));

	private static TransportLine line(String id, TransportLine.Kind kind, String name, String category, int[] s, int[] e,
		int ticks, String teleportCategory)
	{
		return TransportLine.builder(id, kind).name(name).category(category).start(s[0], s[1], s[2])
			.end(e[0], e[1], e[2]).ticks(ticks).teleportCategory(teleportCategory).build();
	}

	private static TickGroups groups;

	/**
	 * The Settings tab's "Teleport and travel ticks" for the preview: the real teleport categories and networks
	 * (data/manual/teleport-categories.tsv and networks.tsv, read here at dev time; a short made-up table if they
	 * can't be found) with the sample route's transports, plus the exported transports when there is an export
	 * (gradlew -p tool exportPlugin), so the rides' ticks are the real ones. The sample teleport is a spell: it takes
	 * the "spell" category's ticks.
	 */
	public static synchronized TickGroups tickGroups()
	{
		if (groups == null)
		{
			List<String> cats = read("data/manual/teleport-categories.tsv", List.of("spell\t4\tspells", "tablet\t4\ttablets"));
			List<String> nets = read("data/manual/networks.tsv", List.of("Fairy ring\t0\t\tfairy rings"));
			List<TransportLine> lines = new ArrayList<>(LINES);
			for (String l : read("src/main/resources/com/bestpath/data/" + com.bestpath.core.data.PluginData.TRANSPORTS, List.of()))
			{
				if (!l.isEmpty() && !l.startsWith("#"))
				{
					lines.add(TransportLine.parse(l));
				}
			}
			groups = TickGroups.of(lines, Tables.parse(List.of(), List.of(), nets, cats, List.of()));
		}
		return groups;
	}

	private static List<String> read(String path, List<String> fallback)
	{
		try
		{
			return java.nio.file.Files.readAllLines(java.nio.file.Paths.get(path), java.nio.charset.StandardCharsets.UTF_8);
		}
		catch (java.io.IOException e)
		{
			return fallback;
		}
	}

	/** The step with the player's tick changes, as the real engine makes it; the step itself without one. */
	private static RouteStep withTicks(RouteStep s, TransportSettings settings)
	{
		String id = s.getTransportId();
		int ticks = id == null ? -1 : tickGroups().ticks(id, settings.tickChanges());
		if (ticks < 0)
		{
			return s;
		}
		// without the card's own change (the card shows the total and changes only its own part)
		PlayerTransports.TickChanges all = settings.tickChanges();
		int before = tickGroups().ticks(id, new PlayerTransports.TickChanges()
		{
			@Override
			public int transport(String transportId)
			{
				return 0;
			}

			@Override
			public int teleportCategory(String category)
			{
				return all.teleportCategory(category);
			}

			@Override
			public int network(String network)
			{
				return all.network(network);
			}
		});
		int[] tiles = new int[s.tileCount()];
		for (int i = 0; i < tiles.length; i++)
		{
			tiles[i] = s.tile(i);
		}
		return new RouteStep(s.getKind(), s.getTitle(), s.getDetail(), ticks, s.getNotes(), id, tiles,
			s.getMarkerTile(), s.getEndTile()).withInfo(s.getInfo()).withTickChange(ticks - before);
	}

	private static boolean usable(String id, java.util.Set<String> skipped, TransportSettings settings)
	{
		return !skipped.contains(id) && !settings.isBlacklisted(id);
	}

	private static RouteStep walk(String title, int x0, int y0, int x1, int y1)
	{
		int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		int[] tiles = new int[n + 1];
		for (int i = 0; i <= n; i++)
		{
			int x = x0 + (n == 0 ? 0 : (x1 - x0) * i / n);
			int y = y0 + (n == 0 ? 0 : (y1 - y0) * i / n);
			tiles[i] = Tiles.pack(x, y, 0);
		}
		return new RouteStep(RouteStep.Kind.WALK, title, n + " tiles", n, List.of(), null, tiles, -1);
	}

	/** A route that is only walking (35 tiles from the usual start). */
	public static RouteResult walkOnly(long generation)
	{
		RouteRequest to = new RouteRequest(3222, 3253, 0);
		return RouteResults.ready(generation, to, List.of(walk("Walk to 3222, 3253, 0", 3222, 3218, 3222, 3253)));
	}

	/** A "no route" answer. */
	public static RouteResult noRoute(long generation)
	{
		return RouteResult.status(generation, DESTINATION, RouteStatus.NO_ROUTE,
			"No route found with the transports allowed. Try allowing more transport types in Settings.");
	}

	/** The fake engine: answers after a short pause so "Calculating..." shows. */
	public static RouteEngine engine(long delayMillis)
	{
		return (RouteJob job) ->
		{
			if (delayMillis > 0)
			{
				try
				{
					// dev-only fake work; the real plugin never sleeps
					Thread.sleep(delayMillis);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			}
			if (job.getRequest().getPlane() == 3)
			{
				return RouteResult.status(job.getGeneration(), job.getRequest(), RouteStatus.NO_ROUTE,
					"No route found with the transports allowed (the fake engine says so for plane 3).");
			}
			return build(job.getGeneration(), job.getRequest(), job.getPlayer(), job.getSessionExclusions(),
				job.getSettings());
		};
	}
}

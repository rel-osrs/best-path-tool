package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.data.MapDataLoader;
import com.bestpath.player.GameReading;
import com.bestpath.player.HouseFurniture;
import com.bestpath.player.SnapshotMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The player-owned house on the REAL exported map data, the way the plugin does it: the house varbit and the house
 * scan's things go through the game reading and {@link SnapshotMapper} into the player state, then through the
 * plugin's route engine (the transports the player can use, the Settings tab's categories, the network's walk ticks).
 * Skipped when the export is missing.
 *
 * <p>Found with these tests: no route ever went through the house, because the export leaves out cells with no walkable
 * tile and the house spot (1857,7065,0) had none (fixed in core PluginRouter: hub cells); and a player standing inside the
 * house had no route at all (fixed: routes start from the house spot, {@link HouseFurniture#routeTile}).
 */
public class HouseRealDataTest
{
	/** Lumbridge Castle courtyard. */
	private static final int[] LUMBRIDGE = {3222, 3218, 0};
	/** Castle Wars, where the jewellery box lands (2440, 3090). */
	private static final RouteRequest CASTLE_WARS = new RouteRequest(2440, 3090, 0);
	/** By Varrock's fountain, a few steps from where Varrock Teleport (and the nexus's Varrock portal) lands. */
	private static final RouteRequest VARROCK = new RouteRequest(3213, 3424, 0);
	/** Ardougne market: far from Varrock. */
	private static final int[] ARDOUGNE = {2662, 3305, 0};
	private static final int TABLET = 8013;
	private static final String HOUSE_NETWORK = TransportRules.HOUSE_NETWORK;

	private static RouteEngine engine;

	@BeforeClass
	public static void load()
	{
		engine = MapDataLoader.engine();
	}

	private static PluginData data()
	{
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		return ((CoreRouteEngine) engine).getRouter().getData();
	}

	/** A member as the plugin reads one: house place varbit value, the house scan's things, house tablets carried. */
	private static PlayerState player(int houseValue, String... things)
	{
		GameReading r = new GameReading();
		r.x = LUMBRIDGE[0];
		r.y = LUMBRIDGE[1];
		r.plane = LUMBRIDGE[2];
		r.members = true;
		r.inventory.put(TABLET, 5);
		r.houseValue = houseValue;
		r.houseThings.addAll(List.of(things));
		return SnapshotMapper.toSnapshot(r).getState();
	}

	private static RouteResult route(int[] from, RouteRequest to, PlayerState state, TransportSettings settings)
	{
		data();
		PlayerSnapshot me = new PlayerSnapshot(from[0], from[1], from[2], state, List.of());
		return engine.route(new RouteJob(1, to, me, settings, Set.of()));
	}

	private static RouteResult route(int[] from, RouteRequest to, PlayerState state)
	{
		return route(from, to, state, TransportSettings.defaults());
	}

	private static List<String> titles(RouteResult r)
	{
		List<String> out = new ArrayList<>();
		for (RouteStep s : r.getSteps())
		{
			out.add(s.getTitle() + " (" + s.getTicks() + ")");
		}
		return out;
	}

	private static RouteStep step(RouteResult r, String nameStart)
	{
		for (RouteStep s : r.getSteps())
		{
			if (s.getInfo().getName().startsWith(nameStart))
			{
				return s;
			}
		}
		return null;
	}

	@Test
	public void theJewelleryBoxTakesAMemberToCastleWars()
	{
		RouteResult r = route(LUMBRIDGE, CASTLE_WARS, player(1, "ornate jewellery box"));
		assertEquals(RouteStatus.READY, r.getStatus());
		RouteStep tablet = step(r, "Teleport to house (tablet) (inside");
		RouteStep box = step(r, "House jewellery box: Castle Wars");
		assertNotNull("teleports into the house: " + titles(r), tablet);
		assertNotNull("rides the jewellery box: " + titles(r), box);
		assertTrue(r.getSteps().indexOf(tablet) < r.getSteps().indexOf(box));
		// the box: its 10 ticks (network rides take at least 10) and the house network's 15 walk ticks inside the house
		assertEquals(10 + 15, box.getTicks());
		assertEquals(HOUSE_NETWORK, box.getInfo().getNetwork());
		assertEquals(TransportSettings.Category.HOUSE, box.getInfo().getSettingsCategory());
		assertEquals(10 + 10 + 15, r.getTotalTicks());

		// the same player before the house was looked at: no box, a slower way
		RouteResult unknown = route(LUMBRIDGE, CASTLE_WARS, player(1));
		assertEquals(null, step(unknown, "House jewellery box"));
		assertTrue(titles(unknown).toString(), unknown.getTotalTicks() > r.getTotalTicks());
		// a basic box goes there too (every tier has Castle Wars)
		assertNotNull(step(route(LUMBRIDGE, CASTLE_WARS, player(1, "basic jewellery box")), "House jewellery box: Castle Wars"));
	}

	@Test
	public void thePortalNexusTakesAMemberToVarrock()
	{
		RouteResult r = route(ARDOUGNE, VARROCK, player(1, "portal nexus: Varrock Teleport"));
		assertEquals(RouteStatus.READY, r.getStatus());
		assertNotNull("teleports into the house: " + titles(r), step(r, "Teleport to house (tablet) (inside"));
		assertNotNull("takes the nexus's Varrock portal: " + titles(r), step(r, "House portal (nexus or chamber): Varrock Teleport"));
		RouteResult none = route(ARDOUGNE, VARROCK, player(1));
		assertTrue(titles(none).toString(), none.getTotalTicks() > r.getTotalTicks());
		// a portal chamber portal works the same
		assertNotNull(step(route(ARDOUGNE, VARROCK, player(1, "portal chamber: Varrock Teleport")),
			"House portal (nexus or chamber): Varrock Teleport"));
	}

	@Test
	public void fromInsideTheHouseTheRouteStartsAtTheHouseSpot()
	{
		// a player in the house: RuneLite gives a tile of the house room templates; routes start at the house spot
		int[] inside = HouseFurniture.routeTile(1864, 7070, 0);
		assertEquals(List.of(1857, 7065, 0), List.of(inside[0], inside[1], inside[2]));
		RouteResult r = route(inside, CASTLE_WARS, player(1, "ornate jewellery box"));
		assertEquals(RouteStatus.READY, r.getStatus());
		assertTrue(titles(r).toString(), r.getSteps().get(0).getInfo().getName().startsWith("House jewellery box: Castle Wars"));
		assertEquals(10 + 15, r.getTotalTicks());
		// nothing scanned, no tablets: out through the house portal (at Rimmington) and on from there
		RouteResult out = route(inside, new RouteRequest(2957, 3214, 0),
			new PlayerState.Builder("No tablets").members(true).house("Rimmington").build());
		assertEquals(RouteStatus.READY, out.getStatus());
		assertTrue(titles(out).toString(), out.getSteps().get(0).getInfo().getName().startsWith("House portal: Rimmington"));
	}

	@Test
	public void theSettingsTabsHouseTypeAndWorldTypeLeaveTheHouseOut()
	{
		TransportSettings noHouse = TransportSettings.defaults().withCategory(TransportSettings.Category.HOUSE, false);
		RouteResult off = route(LUMBRIDGE, CASTLE_WARS, player(1, "ornate jewellery box"), noHouse);
		assertEquals(null, step(off, "House jewellery box"));
		assertEquals(null, step(off, "Teleport to house"));
		// the house network's walk ticks changed in Settings ("Teleport and travel ticks"): the box costs that much more
		TransportSettings slower = TransportSettings.defaults().withGroupTickChange(TransportSettings.networkKey(HOUSE_NETWORK), 5);
		RouteResult r = route(LUMBRIDGE, CASTLE_WARS, player(1, "ornate jewellery box"), slower);
		assertEquals(10 + 10 + 15 + 5, r.getTotalTicks());
		// a free-to-play world: no house
		GameReading f = new GameReading();
		f.x = LUMBRIDGE[0];
		f.y = LUMBRIDGE[1];
		f.members = false;
		f.inventory.put(TABLET, 5);
		f.houseValue = 1;
		f.houseThings.add("ornate jewellery box");
		RouteResult free = route(LUMBRIDGE, new RouteRequest(3212, 3424, 0), SnapshotMapper.toSnapshot(f).getState());
		assertEquals(null, step(free, "Teleport to house"));
	}

	@Test
	public void everyHouseLocationValue()
	{
		// the varbit's values (cache enum 252) to the places the data's "house at <place>" names, and the house portals
		Set<String> dataPlaces = new TreeSet<>();
		for (TransportLine l : data().getTransports())
		{
			for (String part : (l.getRequirements() + ";" + l.requirementsEndToStart()).split("[;|()]"))
			{
				String p = part.trim();
				if (p.toLowerCase(Locale.ROOT).startsWith("house at "))
				{
					dataPlaces.add(p.substring("house at ".length()).trim());
				}
			}
		}
		Set<String> table = new TreeSet<>();
		for (int v = 0; v <= 15; v++)
		{
			String place = SnapshotMapper.housePlace(v);
			if (place != null)
			{
				table.add(place);
				PlayerState p = player(v);
				assertTrue(p.hasHouse());
				assertTrue(place, p.houseAt(place));
				// the tablet's "outside" teleport lands at that place's house portal
				RouteResult r = route(LUMBRIDGE, CASTLE_WARS, player(v, "ornate jewellery box"));
				assertEquals(place, 10 + 10 + 15, r.getTotalTicks());
			}
		}
		assertEquals(Set.of("Aldarin", "Brimhaven", "Hosidius", "Pollnivneach", "Prifddinas", "Rellekka", "Rimmington", "Taverley",
			"Yanille"), table);
		assertEquals("every place the data names has a varbit value", dataPlaces, table);

		// 0 = no house (the game's script: "You must own a house")
		assertFalse(player(0, "ornate jewellery box").hasHouse());
		assertEquals(null, step(route(LUMBRIDGE, CASTLE_WARS, player(0, "ornate jewellery box")), "House jewellery box"));
		// a value not in the table (a new place): still a house, so in through the tablet and out through the box
		PlayerState unknown = player(7, "ornate jewellery box");
		assertTrue(unknown.hasHouse());
		assertFalse(unknown.houseAt("Rimmington"));
		RouteResult r = route(LUMBRIDGE, CASTLE_WARS, unknown);
		assertNotNull(titles(r).toString(), step(r, "House jewellery box: Castle Wars"));
	}

	@Test
	public void theHouseSpotIsWhereTheDataPutsTheHouse()
	{
		int rides = 0;
		int into = 0;
		for (TransportLine l : data().getTransports())
		{
			if (HOUSE_NETWORK.equalsIgnoreCase(l.getNetwork()))
			{
				rides++;
				assertEquals(l.getName(), List.of(HouseFurniture.HOUSE_SPOT_X, HouseFurniture.HOUSE_SPOT_Y, HouseFurniture.HOUSE_SPOT_PLANE),
					List.of(l.getStartX(), l.getStartY(), l.getStartPlane()));
			}
			if (l.getName().contains("(inside your house)"))
			{
				into++;
				assertEquals(l.getName(), List.of(HouseFurniture.HOUSE_SPOT_X, HouseFurniture.HOUSE_SPOT_Y), List.of(l.getEndX(), l.getEndY()));
			}
		}
		assertTrue("rides out of the house: " + rides, rides > 90);
		assertTrue("ways into the house: " + into, into >= 4);
		// the house spot lies in the house room templates, so a player inside a house is routed from it
		assertTrue(HouseFurniture.isHouseTemplateTile(HouseFurniture.HOUSE_SPOT_X, HouseFurniture.HOUSE_SPOT_Y));
		assertFalse(HouseFurniture.isHouseTemplateTile(3222, 3218));
	}

	/**
	 * The house scan's names and the data's "house has" names are the same words: every thing the data needs can be
	 * found by the scan, and everything the scan finds is used (except the altars and the Sailing portal).
	 */
	@Test
	public void theScanFindsWhatTheDataNeeds() throws IOException
	{
		Set<String> scan = new TreeSet<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			HouseFurniture.class.getResourceAsStream(HouseFurniture.RESOURCE), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t");
				if (line.startsWith("#") || c.length < 3)
				{
					continue;
				}
				if (c[0].equals("object") && !c[2].equals(HouseFurniture.NEXUS))
				{
					scan.add(c[2].trim().toLowerCase(Locale.ROOT));
				}
				else if (c[0].equals("destination"))
				{
					scan.add((HouseFurniture.NEXUS_PREFIX + c[2].trim()).toLowerCase(Locale.ROOT));
				}
			}
		}
		PluginData d = data();
		Set<String> needed = new TreeSet<>();
		for (TransportLine l : d.getTransports())
		{
			for (String own : new String[]{l.getRequirements(), l.requirementsEndToStart()})
			{
				Requirement req;
				try
				{
					req = RequirementText.parse(d.getTables().networkRequirement(l.getNetwork(), own), d.getTables().itemGroups());
				}
				catch (RequirementText.ParseException e)
				{
					continue;
				}
				req.visit(r ->
				{
					if (r instanceof Requirement.House && ((Requirement.House) r).getKind() == Requirement.House.Kind.HAS)
					{
						needed.add(((Requirement.House) r).getWhat().trim().toLowerCase(Locale.ROOT));
					}
				});
			}
		}
		assertTrue("the data needs house things: " + needed, needed.size() > 50);
		Set<String> missing = new TreeSet<>(needed);
		missing.removeAll(scan);
		assertEquals("needed by the data, never found by the scan", Set.of(), missing);
		Set<String> unused = new TreeSet<>(scan);
		unused.removeAll(needed);
		unused.removeIf(t -> t.endsWith("altar") || t.endsWith("teleport to boat") || t.endsWith("battlefront teleport"));
		assertEquals("found by the scan, used by no transport", Set.of(), unused);
	}
}

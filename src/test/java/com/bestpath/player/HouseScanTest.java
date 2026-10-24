package com.bestpath.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.route.PlayerSnapshot;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

/**
 * The house scan: furniture ids -> house things (the table generated from the cache), knowing the
 * player is in their own house, remembering the result per account, and the snapshot.
 */
public class HouseScanTest
{
	private static final HouseFurniture TABLE = HouseFurniture.load();

	// ---- furniture id -> thing (the shipped table, cache #2735) ----

	@Test
	public void tableKnowsTheFurniture()
	{
		assertTrue(TABLE.things.size() > 250);
		// jewellery boxes: the placed multi-state objects (varbit 2308 picks the left-click teleport) and each state
		assertEquals("basic jewellery box", TABLE.thing(29154));
		assertEquals("fancy jewellery box", TABLE.thing(29155));
		assertEquals("ornate jewellery box", TABLE.thing(29156));
		assertEquals("basic jewellery box", TABLE.thing(37500));
		assertEquals("ornate jewellery box", TABLE.thing(37546));
		assertEquals("ornate jewellery box", TABLE.thing(50712));
		assertEquals("mounted glory", TABLE.thing(13523));
		assertEquals("mounted mythical cape", TABLE.thing(31986));
		assertEquals("mounted mythical cape", TABLE.thing(31983));
		assertEquals("mounted xeric's talisman", TABLE.thing(33419));
		assertEquals("mounted xeric's talisman", TABLE.thing(33411));
		assertEquals("mounted digsite pendant", TABLE.thing(33420));
		assertEquals("obelisk", TABLE.thing(31554));
		assertEquals("spirit tree", TABLE.thing(29227));
		assertEquals("fairy ring", TABLE.thing(29228));
		assertEquals("spirit tree & fairy ring", TABLE.thing(29229));
		assertEquals(HouseFurniture.NEXUS, TABLE.thing(33408));
		assertEquals(HouseFurniture.NEXUS, TABLE.thing(33410));
		// portal chamber: Varrock / Grand Exchange toggle (multi-state 13615), Kourend, the basalt teleports
		assertEquals("portal chamber: Varrock Teleport", TABLE.thing(13615));
		assertEquals("portal chamber: Varrock Teleport", TABLE.thing(33093)); // Grand Exchange Portal
		assertEquals("portal chamber: Lumbridge Teleport", TABLE.thing(13616));
		assertEquals("portal chamber: Fenkenstrain's Castle Teleport", TABLE.thing(37587));
		assertEquals("portal chamber: Icy basalt", TABLE.thing(37581)); // Weiss Portal
	}

	@Test
	public void tableLeavesOtherObjectsOut()
	{
		assertNull(TABLE.thing(1)); // anything else
		assertNull(TABLE.thing(29142)); // Jewellery box space (building mode)
		assertNull(TABLE.thing(33346)); // Portal Nexus space
		assertNull(TABLE.thing(12094)); // a fairy ring in the world
		assertNull(TABLE.thing(26260)); // a spirit tree in the world
		assertNull(TABLE.thing(14826)); // a Wilderness obelisk
		assertNull(TABLE.thing(39621)); // mounted max cape (mythical): its teleports are not known
		assertNull(TABLE.thing(31924)); // "Ornate Jewellery Box" without options (not house furniture)
		assertFalse(TABLE.isWatched(29142));
		assertTrue(TABLE.isWatched(29154));
	}

	@Test
	public void everyNameIsOneTheRequirementLinesUse() throws Exception
	{
		Set<String> things = new TreeSet<>();
		Set<String> destinations = new TreeSet<>();
		for (String line : Files.readAllLines(new File("data/manual/house-things.tsv").toPath(), StandardCharsets.UTF_8))
		{
			String[] c = line.split("\t");
			if (c.length >= 2 && c[0].equals("thing"))
			{
				things.add(c[1]);
			}
			else if (c.length >= 2 && c[0].equals("destination"))
			{
				destinations.add(c[1]);
			}
		}
		destinations.add("Civitas illa Fortis Teleport"); // in the cache, not yet in house-things.tsv
		String text = new String(HouseScanTest.class.getResourceAsStream(HouseFurniture.RESOURCE).readAllBytes(),
			StandardCharsets.UTF_8);
		Set<String> seen = new TreeSet<>();
		for (String line : text.split("\n"))
		{
			String[] c = line.split("\t");
			if (c.length >= 3 && c[0].equals("object"))
			{
				String t = c[2].trim();
				seen.add(t);
				boolean ok = things.contains(t) || t.equals(HouseFurniture.NEXUS)
					|| t.startsWith("portal chamber: ") && destinations.contains(t.substring("portal chamber: ".length()));
				assertTrue("unknown thing " + t, ok);
			}
			else if (c.length >= 3 && c[0].equals("destination"))
			{
				assertTrue("unknown destination " + c[2], destinations.contains(c[2].trim()));
			}
		}
		// every thing the requirement lines name is found in the cache
		for (String t : things)
		{
			assertTrue("no furniture for " + t, seen.contains(t));
		}
	}

	@Test
	public void requirementLinesReadTheScanNames()
	{
		RequirementText.ItemGroups none = new RequirementText.ItemGroups()
		{
			@Override
			public int[] ids(String name)
			{
				return null;
			}

			@Override
			public RequirementText.RuneSource rune(String name)
			{
				return null;
			}
		};
		Set<String> found = TABLE.thingsOf(List.of(29156, 33408), slots(1, 40));
		PlayerState.Builder b = new PlayerState.Builder("p").house("Rimmington");
		found.forEach(b::houseThing);
		PlayerState p = b.build();
		for (String line : new String[]{"house has ornate jewellery box",
			"one of (house has portal nexus: Varrock Teleport | house has portal chamber: Varrock Teleport)",
			"house has portal nexus: Respawn Teleport"})
		{
			Requirement r = RequirementText.parse(line, none);
			assertEquals(line, Check.Outcome.MET, r.check(p).getOutcome());
		}
		Requirement fancy = RequirementText.parse("house has fancy jewellery box", none);
		assertEquals(Check.Outcome.NOT_MET, fancy.check(p).getOutcome()); // exactly what is built
	}

	// ---- what is built, from furniture ids and nexus slots ----

	private static int[] slots(int... values)
	{
		int[] s = new int[HouseFurniture.NEXUS_SLOTS.length];
		System.arraycopy(values, 0, s, 0, values.length);
		return s;
	}

	@Test
	public void thingsFromFurnitureAndNexusSlots()
	{
		Set<String> t = TABLE.thingsOf(List.of(29156, 13523, 29229, 33408, 13615, 33093, 1), slots(1, 8, 30, 0, 99));
		assertEquals(new TreeSet<>(List.of("ornate jewellery box", "mounted glory", "spirit tree & fairy ring",
			"portal chamber: Varrock Teleport", "portal nexus: Varrock Teleport",
			"portal nexus: Ape Atoll Teleport (standard)", "portal nexus: Ape Atoll Teleport (Arceuus)")), t);
	}

	@Test
	public void nexusSlotsCountOnlyWithANexus()
	{
		assertEquals(Set.of("obelisk"), TABLE.thingsOf(List.of(31554), slots(1, 2, 3)));
		assertEquals(Set.of(), TABLE.thingsOf(List.of(33409), slots())); // a nexus with no destinations set
		assertEquals(Set.of(), TABLE.thingsOf(List.of(33409), null));
		assertEquals(45, HouseFurniture.NEXUS_SLOTS.length);
		assertEquals(6654, HouseFurniture.NEXUS_SLOTS[0]); // checked against the game's script 2671 by the tool
		assertEquals(20120, HouseFurniture.NEXUS_SLOTS[44]);
	}

	// ---- is the scene a house (instance template chunks) ----

	private static int chunk(int worldX, int worldY, int plane, int rotation)
	{
		return (worldX / 8) << 14 | (worldY / 8) << 3 | plane << 24 | rotation << 1;
	}

	@Test
	public void houseTemplateChunks()
	{
		assertTrue(HouseFurniture.isHouseChunk(chunk(1870, 7105, 0, 0))); // region 7535: house room templates
		assertTrue(HouseFurniture.isHouseChunk(chunk(2062, 7105, 1, 3))); // region 8303, plane 1, rotated
		assertFalse(HouseFurniture.isHouseChunk(chunk(3222, 3218, 0, 0))); // Lumbridge
		assertFalse(HouseFurniture.isHouseChunk(chunk(3232, 9312, 0, 0))); // some other instance template
		assertFalse(HouseFurniture.isHouseChunk(-1)); // no chunk
	}

	// ---- own house or not ----

	private static OwnHouseWatch.Entry click(String option, String target)
	{
		return OwnHouseWatch.classify(option, target, -1);
	}

	@Test
	public void menuClicksThatLeadIntoTheOwnHouse()
	{
		assertEquals(OwnHouseWatch.Entry.OWN_HOUSE, click("Cast", "<col=00ff00>Teleport to House</col>"));
		assertEquals(OwnHouseWatch.Entry.OWN_HOUSE, click("Break", "<col=ff9040>Teleport to house</col>"));
		assertEquals(OwnHouseWatch.Entry.OWN_HOUSE, click("Inside", "<col=ff9040>Teleport to house</col>"));
		assertEquals(OwnHouseWatch.Entry.OWN_HOUSE, click("Tele to POH", "<col=ff9040>Construct. cape</col>"));
		assertEquals(OwnHouseWatch.Entry.OWN_HOUSE, click("Home", "<col=ffff>Portal"));
		assertEquals(OwnHouseWatch.Entry.OWN_HOUSE, click("Build mode", "<col=ffff>Portal"));
		// lands outside, asks first, or a friend's house
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Outside", "<col=ff9040>Teleport to house</col>"));
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Group", "<col=ff9040>Teleport to house</col>"));
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Enter", "<col=ffff>Portal"));
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Friend's house", "<col=ffff>Portal"));
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Visit-Last", "<col=ffff>House Advertisement"));
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Home", "<col=ffff>Max cape")); // which Max cape: not known
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Eat", "<col=ff9040>Shark"));
		assertEquals(OwnHouseWatch.Entry.IGNORED, click("Walk here", ""));
		assertEquals(OwnHouseWatch.Entry.IGNORED, click("Cancel", ""));
	}

	@Test
	public void theMaxCapesHomeCountsOnlyOnTheCarriedCape()
	{
		// the Max cape's "Teleports" -> "Home" always goes into the own house. Cache #2735: only the
		// carried Max cape (13280) has "Teleports" -> "Home"; a click on a sub-option reads "Home" on "Max cape"
		assertEquals(OwnHouseWatch.Entry.OWN_HOUSE,
			OwnHouseWatch.classify("Home", "<col=ff9040>Max cape</col>", OwnHouseWatch.MAX_CAPE));
		// the worn Max cape (13342): worn "Home" and "POH Portals" -> "Home" read the same and are not known to go
		// inside: not counted
		assertEquals(OwnHouseWatch.Entry.OTHER, OwnHouseWatch.classify("Home", "<col=ff9040>Max cape</col>", 13342));
		assertEquals(OwnHouseWatch.Entry.OTHER, OwnHouseWatch.classify("Home", "<col=ff9040>Max cape</col>", -1));
		// the carried cape's other sub-options (a house portal's place lands outside it, the guilds...) cancel
		assertEquals(OwnHouseWatch.Entry.OTHER, OwnHouseWatch.classify("Rimmington", "<col=ff9040>Max cape</col>", 13280));
		assertEquals(OwnHouseWatch.Entry.OTHER, OwnHouseWatch.classify("Teleports", "<col=ff9040>Max cape</col>", 13280));
		// "Home" on something else with the same item id can't happen, but does not count either
		assertEquals(OwnHouseWatch.Entry.OTHER, OwnHouseWatch.classify("Home", "<col=ff9040>Mythical max cape</col>", 13280));

		OwnHouseWatch w = new OwnHouseWatch();
		w.onMenuClick(OwnHouseWatch.classify("Home", "<col=ff9040>Max cape</col>", OwnHouseWatch.MAX_CAPE), 100);
		w.onSceneLoaded(true, 104);
		assertTrue(w.isInOwnHouse());
		// the worn cape's "Home": a house, but not known to be the own one, so never scanned
		w = new OwnHouseWatch();
		w.onMenuClick(OwnHouseWatch.classify("Home", "<col=ff9040>Max cape</col>", 13342), 100);
		w.onSceneLoaded(true, 104);
		assertTrue(w.isInHouse());
		assertFalse(w.isInOwnHouse());
	}

	@Test
	public void onlyTheExactClicksLetAScanThrough()
	{
		// the same things clicked for something else (the bank, using, dropping): never an entry into the own house
		for (String o : new String[]{"Withdraw-1", "Withdraw-All", "Deposit-1", "Use", "Drop", "Group", "Outside",
			"Wear", "Teleport", "Rearrange"})
		{
			assertEquals(o, OwnHouseWatch.Entry.OTHER, click(o, "<col=ff9040>Teleport to house</col>"));
		}
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Outside", "<col=00ff00>Teleport to House</col>")); // the spell
		// "Tele to POH" only on the Construction capes (the only things with it in the cache)
		assertEquals(OwnHouseWatch.Entry.OWN_HOUSE, click("Tele to POH", "<col=ff9040>Construct. cape(t)</col>"));
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Tele to POH", "<col=ff9040>Cape rack</col>"));
		// "Home" / "Build mode" only on a "Portal" (the house portals 15477-15482, 28822, 30172, 34947, 55353)
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Home", "<col=ffff>House Advertisement"));
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Build mode", "<col=ffff>Portal Nexus"));
		// a spirit tree's "Your house" is not on the list (not asked); the worn Construction cape's "Teleport" -> "Home"
		// is (the card says "Home"), on the Construction capes only
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Your house", "<col=ffff>Spirit Tree"));
		assertEquals(OwnHouseWatch.Entry.OWN_HOUSE, click("Home", "<col=ff9040>Construct. cape</col>"));
		assertEquals(OwnHouseWatch.Entry.OWN_HOUSE, click("Home", "<col=ff9040>Construct. cape(t)</col>"));
		assertEquals(OwnHouseWatch.Entry.OTHER, click("Home", "<col=ff9040>Hood</col>"));

		// a bank click on the tablet does not leave an entry waiting for the next house
		OwnHouseWatch w = new OwnHouseWatch();
		w.onMenuClick(click("Withdraw-1", "<col=ff9040>Teleport to house</col>"), 100);
		w.onSceneLoaded(true, 110);
		assertFalse(w.isInOwnHouse());
	}

	@Test
	public void teleportIntoTheHouseIsTheOwnHouse()
	{
		OwnHouseWatch w = new OwnHouseWatch();
		w.onMenuClick(click("Cast", "<col=00ff00>Teleport to House</col>"), 100);
		w.onMenuClick(click("Walk here", ""), 101); // walking does not cancel
		w.onSceneLoaded(true, 104);
		assertTrue(w.isInOwnHouse());
		w.onSceneLoaded(true, 140); // building mode switched: the house loads again
		assertTrue(w.isInOwnHouse());
		w.onSceneLoaded(false, 200); // left through the portal
		assertFalse(w.isInHouse());
		assertFalse(w.isInOwnHouse());
		w.onSceneLoaded(true, 300); // a house again, but nothing says whose
		assertTrue(w.isInHouse());
		assertFalse(w.isInOwnHouse());
	}

	@Test
	public void aFriendsHouseIsNeverTheOwnHouse()
	{
		OwnHouseWatch w = new OwnHouseWatch();
		w.onMenuClick(click("Friend's house", "Portal"), 100);
		w.onSceneLoaded(true, 110);
		assertTrue(w.isInHouse());
		assertFalse(w.isInOwnHouse());

		// an own-house click cancelled by another click before the house loaded
		w = new OwnHouseWatch();
		w.onMenuClick(click("Break", "Teleport to house"), 100);
		w.onMenuClick(click("Enter", "Portal"), 105);
		w.onSceneLoaded(true, 120);
		assertFalse(w.isInOwnHouse());

		// an old click (the teleport failed) does not count for a later house
		w = new OwnHouseWatch();
		w.onMenuClick(click("Break", "Teleport to house"), 100);
		w.onSceneLoaded(true, 100 + OwnHouseWatch.PENDING_TICKS + 1);
		assertFalse(w.isInOwnHouse());

		// the click is used up by the first scene load (here: teleported outside the house)
		w = new OwnHouseWatch();
		w.onMenuClick(click("Home", "Portal"), 100);
		w.onSceneLoaded(false, 103);
		w.onSceneLoaded(true, 110);
		assertFalse(w.isInOwnHouse());
	}

	@Test
	public void buildingModeMarksTheOwnHouse()
	{
		OwnHouseWatch w = new OwnHouseWatch();
		w.onBuildModeSeen(); // not in a house: nothing
		assertFalse(w.isInOwnHouse());
		w.onSceneLoaded(true, 10);
		assertFalse(w.isInOwnHouse());
		w.onBuildModeSeen();
		assertTrue(w.isInOwnHouse());
		w.reset(); // logged out
		assertFalse(w.isInHouse());
		assertFalse(w.isInOwnHouse());
	}

	// ---- remembered per account ----

	/** A fake RuneLite profile store with accounts. */
	private static final class FakeStore implements AccountSettings.Store
	{
		String key;
		final Map<String, String> saved = new HashMap<>();
		final List<String> writes = new ArrayList<>();

		@Override
		public String profileKey()
		{
			return key;
		}

		@Override
		public String read()
		{
			return key == null ? null : saved.get(key);
		}

		@Override
		public boolean write(String text)
		{
			if (key == null)
			{
				return false;
			}
			saved.put(key, text);
			writes.add(key + "=" + text);
			return true;
		}
	}

	@Test
	public void rememberedPerAccountAndSavedOnlyWhenChanged()
	{
		FakeStore store = new FakeStore();
		HouseMemory m = new HouseMemory(store);
		store.key = "A";
		m.load();
		assertEquals(Set.of(), m.get()); // never scanned: nothing built

		assertTrue(m.update(Set.of("ornate jewellery box", "fairy ring")));
		assertFalse(m.update(new TreeSet<>(List.of("fairy ring", "ornate jewellery box")))); // same: no write
		assertEquals(List.of("A=fairy ring\nornate jewellery box"), store.writes);

		store.key = "B"; // another account
		m.load();
		assertEquals(Set.of(), m.get());
		assertTrue(m.update(Set.of("obelisk")));

		store.key = "A";
		m.load();
		assertEquals(Set.of("fairy ring", "ornate jewellery box"), m.get());
		assertTrue(m.update(Set.of())); // everything removed: saved as "scanned, nothing"
		m.load();
		assertEquals(Set.of(), m.get());
		assertEquals("", store.saved.get("A"));
		assertEquals("obelisk", store.saved.get("B"));
	}

	// ---- into the snapshot ----

	@Test
	public void snapshotCarriesTheHouseThings()
	{
		GameReading r = new GameReading();
		r.houseValue = 1; // Rimmington
		r.houseThings.add("ornate jewellery box");
		r.houseThings.add("portal nexus: Varrock Teleport");
		PlayerSnapshot s = SnapshotMapper.toSnapshot(r);
		assertTrue(s.getState().houseHas("ornate jewellery box"));
		assertTrue(s.getState().houseHas("portal nexus: Varrock Teleport"));
		assertFalse(s.getState().houseHas("fairy ring"));

		r.houseValue = 0; // no house any more: nothing in it counts
		assertFalse(SnapshotMapper.toSnapshot(r).getState().houseHas("ornate jewellery box"));
	}

	@Test
	public void newHouseThingsAreAReasonToSearchAgain()
	{
		PlayerState before = new PlayerState.Builder("p").house("Rimmington").build();
		PlayerState after = new PlayerState.Builder("p").house("Rimmington").houseThing("fairy ring").build();
		assertFalse(AvailabilityDiff.changes(before, after, RequirementInputs.NONE).isEmpty());
	}
}

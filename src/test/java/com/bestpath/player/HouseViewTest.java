package com.bestpath.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.req.PlayerState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * The Settings tab's "House (POH)" section and what it rests on: the house place from the varbit (every
 * value), the scan's things as rows, when the house was last looked at (per account), and where routes start for a
 * player inside a house.
 */
public class HouseViewTest
{
	/** Two keys of one account profile at a time (as ProfileStore: one value per key per account). */
	private static final class Profiles
	{
		String account;
		final Map<String, String> saved = new HashMap<>();
		final List<String> writes = new ArrayList<>();

		AccountSettings.Store key(String k)
		{
			return new AccountSettings.Store()
			{
				@Override
				public String profileKey()
				{
					return account;
				}

				@Override
				public String read()
				{
					return account == null ? null : saved.get(account + "/" + k);
				}

				@Override
				public boolean write(String text)
				{
					if (account == null)
					{
						return false;
					}
					saved.put(account + "/" + k, text);
					writes.add(account + "/" + k + "=" + text);
					return true;
				}
			};
		}
	}

	@Test
	public void lastLookedIsRememberedPerAccount()
	{
		Profiles p = new Profiles();
		HouseMemory m = new HouseMemory(p.key("houseThings"), p.key("houseScanned"));
		p.account = "A";
		m.load();
		assertEquals(0, m.getScannedAt()); // not looked at yet

		// a scan that found nothing: "looked" (not "not looked at yet"), no things written (nothing changed)
		m.scanned(1_000);
		assertFalse(m.update(Set.of()));
		assertEquals(1_000, m.getScannedAt());
		assertEquals(List.of("A/houseScanned=1000"), p.writes);

		m.scanned(2_000);
		assertTrue(m.update(Set.of("ornate jewellery box")));

		p.account = "B";
		m.load();
		assertEquals(0, m.getScannedAt());
		assertEquals(Set.of(), m.get());

		p.account = "A";
		m.load();
		assertEquals(2_000, m.getScannedAt());
		assertEquals(Set.of("ornate jewellery box"), m.get());

		assertEquals(0, HouseMemory.parseTime("not a number"));
		assertEquals(0, HouseMemory.parseTime(null));
		assertEquals(0, HouseMemory.parseTime("-5"));
	}

	@Test
	public void everyHousePlaceValue()
	{
		String[] want = {null, "Rimmington", "Taverley", "Pollnivneach", "Rellekka", "Brimhaven", "Yanille", null, "Hosidius",
			"Prifddinas", null, null, null, "Aldarin", null, null};
		for (int v = 0; v < want.length; v++)
		{
			assertEquals("value " + v, want[v], SnapshotMapper.housePlace(v));
			String place = SnapshotMapper.housePlace(v, null);
			if (v == 0)
			{
				assertNull(place);
			}
			else if (want[v] != null)
			{
				assertEquals(want[v], place);
			}
			else
			{
				// a value the table doesn't know: the player owns a house somewhere new
				assertEquals(SnapshotMapper.UNKNOWN_HOUSE_PLACE, place);
				PlayerState s = new PlayerState.Builder("p").house(place).build();
				assertTrue(s.hasHouse());
				assertFalse(s.houseAt("Rimmington"));
			}
		}
		assertNull(SnapshotMapper.housePlace(-1, "Rimmington"));
		// the game's own enum names a new place: that name (a known value keeps the table's name)
		assertEquals("Port Roberts", SnapshotMapper.housePlace(14, "Port Roberts"));
		assertEquals("Rimmington", SnapshotMapper.housePlace(1, "Something else"));
		assertEquals(SnapshotMapper.UNKNOWN_HOUSE_PLACE, SnapshotMapper.housePlace(10, " "));

		GameReading r = new GameReading();
		r.houseValue = 11;
		r.houseThings.add("ornate jewellery box");
		PlayerState s = SnapshotMapper.toSnapshot(r).getState();
		assertTrue("a new place still has its jewellery box", s.houseHas("ornate jewellery box"));
	}

	@Test
	public void theSectionsLines()
	{
		assertEquals("Log in to see your house.", HouseView.NOT_LOGGED_IN.placeText());
		HouseView none = HouseView.of(null, Set.of(), 0);
		assertEquals(HouseView.Status.NO_HOUSE, none.getStatus());
		assertEquals("No house found", none.placeText());

		HouseView fresh = HouseView.of("Rimmington", Set.of(), 0);
		assertEquals("Your house: Rimmington", fresh.placeText());
		assertEquals(List.of(), fresh.rows());
		assertEquals(0, fresh.getScannedAt());

		HouseView v = HouseView.of("Rimmington", Set.of("ornate jewellery box", "portal nexus: Varrock Teleport",
			"portal nexus: Camelot Teleport", "portal chamber: Watchtower Teleport", "mounted glory", "spirit tree & fairy ring",
			"obelisk", "occult altar", "mounted xeric's talisman"), 5_000);
		List<String> texts = new ArrayList<>();
		List<HouseView.Row.Kind> kinds = new ArrayList<>();
		for (HouseView.Row r : v.rows())
		{
			texts.add(r.toString());
			kinds.add(r.getKind());
		}
		assertEquals(List.of("Ornate jewellery box", "Mounted glory", "Mounted xeric's talisman", "Spirit tree & fairy ring", "Obelisk",
			"Occult altar", "Portal: Watchtower Teleport", "Portal nexus (2 destinations) [Camelot Teleport, Varrock Teleport]"), texts);
		assertEquals(HouseView.Row.Kind.PORTAL_CHAMBER, kinds.get(6));
		assertEquals("Watchtower Teleport", v.rows().get(6).getDestination());
		assertEquals(HouseView.Row.Kind.PORTAL_NEXUS, kinds.get(7));

		// same content = equal (the plugin hands the Settings tab a new one only when it changed)
		assertEquals(v, HouseView.of("Rimmington", v.things, 5_000));
		assertFalse(v.equals(HouseView.of("Rimmington", v.things, 6_000)));
		assertFalse(v.equals(HouseView.of("Taverley", v.things, 5_000)));
	}

	@Test
	public void insideAHouseRoutesStartAtTheHouseSpot()
	{
		// RuneLite puts a player inside a house on a tile of the house room templates (regions 7534 ... 8303)
		for (int[] t : new int[][]{{1857, 7065, 0}, {1864, 7070, 1}, {2100, 7160, 0}, {1920, 7100, 2}})
		{
			int[] r = HouseFurniture.routeTile(t[0], t[1], t[2]);
			assertEquals(List.of(1857, 7065, 0), List.of(r[0], r[1], r[2]));
		}
		// anywhere else: the tile itself (Rimmington, by the house portal)
		int[] out = HouseFurniture.routeTile(2953, 3224, 0);
		assertEquals(List.of(2953, 3224, 0), List.of(out[0], out[1], out[2]));
		assertFalse(HouseFurniture.isHouseTemplateTile(1855, 7065)); // region 7534 starts at x 1856
		assertTrue(HouseFurniture.isHouseTemplateTile(1856, 7040));
		assertFalse(HouseFurniture.isHouseTemplateTile(1856, 7168)); // y 7168 is region 112: not a template
	}
}

package com.bestpath.tool.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.transport.TransportCandidate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The game update comparison's rules, on facts written by hand. */
class CacheCompareTest
{
	private static final int LADDER = 16683;
	private static final int REGION = CacheFacts.region(3200, 3200);

	private CacheFacts a;
	private CacheFacts b;
	private OurData data;
	private TransportRecord ladder;

	private static CacheFacts facts(int id)
	{
		CacheFacts f = new CacheFacts();
		f.cacheId = id;
		f.build = 241;
		f.date = "2026-10-07T10:45:05Z";
		f.objects.put(LADDER, new CacheFacts.ObjectFact("Ladder", Arrays.asList("climb-up", "climb-down"), 1, 1));
		f.objects.put(1530, new CacheFacts.ObjectFact("Door", Arrays.asList("open"), 1, 1));
		f.blocks.put(REGION << 2, 1L);
		return f;
	}

	private static void place(CacheFacts f, long... locs)
	{
		long[] all = Arrays.copyOf(f.locs, f.locs.length + locs.length);
		System.arraycopy(locs, 0, all, f.locs.length, locs.length);
		Arrays.sort(all);
		f.locs = all;
	}

	@BeforeEach
	void setUp()
	{
		a = facts(2735);
		b = facts(2740);
		place(a, CacheFacts.loc(LADDER, 3200, 3200, 0, 10, 0));
		place(b, CacheFacts.loc(LADDER, 3200, 3200, 0, 10, 0));
		ladder = new TransportRecord();
		ladder.id = "t-1";
		ladder.name = "Ladder";
		ladder.objectId = LADDER;
		ladder.objectX = 3200;
		ladder.objectY = 3200;
		ladder.objectPlane = 0;
		ladder.startX = 3201;
		ladder.startY = 3200;
		ladder.endX = 3201;
		ladder.endY = 3200;
		ladder.endPlane = 1;
		data = new OurData();
		data.transports.add(ladder);
		for (long t : data.tiles())
		{
			a.tileFlags.put(t, 0);
			b.tileFlags.put(t, 0);
		}
	}

	private CacheCompare.Report run()
	{
		return CacheCompare.compare(a, b, data);
	}

	@Test
	void theSameCachesNeedNothing()
	{
		CacheCompare.Report r = run();
		assertEquals(0, r.lookCount());
		assertEquals(0, r.findings.size());
		assertTrue(r.carried.contains("1 transports: their object is where it was, with the same menu options"), r.carried.toString());
		assertTrue(r.carried.contains("2 transport ends: the ground is as it was"));
		assertTrue(CacheCompare.markdown(r).contains("Nothing needs a look"));
	}

	@Test
	void aGoneObjectIsFound()
	{
		b.locs = new long[0];
		CacheCompare.Report r = run();
		assertEquals(1, r.of(CacheCompare.Kind.OBJECT_GONE).size());
		assertEquals("t-1", r.of(CacheCompare.Kind.OBJECT_GONE).get(0).ref);
		assertEquals(1, r.lookCount());
	}

	@Test
	void aMovedObjectIsFoundWithItsNewTile()
	{
		b.locs = new long[0];
		place(b, CacheFacts.loc(LADDER, 3203, 3201, 0, 10, 0), CacheFacts.loc(LADDER, 3300, 3200, 0, 10, 0));
		List<CacheCompare.Finding> f = run().of(CacheCompare.Kind.OBJECT_MOVED);
		assertEquals(1, f.size());
		assertTrue(f.get(0).text.contains("3203,3201,0 (3 tiles away)"), f.get(0).text);
	}

	@Test
	void anObjectReplacedByOneOfTheSameNameIsFound()
	{
		b.locs = new long[0];
		b.objects.put(99999, new CacheFacts.ObjectFact("Ladder", Collections.singletonList("climb-up"), 1, 1));
		place(b, CacheFacts.loc(99999, 3200, 3200, 0, 10, 0));
		List<CacheCompare.Finding> f = run().of(CacheCompare.Kind.OBJECT_REPLACED);
		assertEquals(1, f.size());
		assertTrue(f.get(0).text.contains("object 99999"), f.get(0).text);
	}

	@Test
	void aLostMenuOptionIsFound()
	{
		b.objects.put(LADDER, new CacheFacts.ObjectFact("Ladder", Collections.singletonList("climb-down"), 1, 1));
		List<CacheCompare.Finding> f = run().of(CacheCompare.Kind.OPTION_LOST);
		assertEquals(1, f.size());
		assertTrue(f.get(0).text.contains("\"climb-up\""), f.get(0).text);
	}

	@Test
	void aRenamedObjectIsOnlyForInformation()
	{
		b.objects.put(LADDER, new CacheFacts.ObjectFact("Old ladder", Arrays.asList("climb-up", "climb-down"), 1, 1));
		CacheCompare.Report r = run();
		assertEquals(1, r.of(CacheCompare.Kind.OBJECT_RENAMED).size());
		assertEquals(0, r.lookCount());
	}

	@Test
	void anObjectNotThereInTheOldCacheEitherIsNotThisUpdatesChange()
	{
		a.locs = new long[0];
		b.locs = new long[0];
		CacheCompare.Report r = run();
		assertEquals(0, r.lookCount());
		assertTrue(r.notCompared.get(0).startsWith("1 transports name an object that is not on their tile in the old cache"));
	}

	@Test
	void groundChangedUnderATransportEnd()
	{
		b.tileFlags.put(CacheFacts.tile(3201, 3200, 1), CollisionFlags.OBJECT);
		List<CacheCompare.Finding> f = run().of(CacheCompare.Kind.GROUND_CHANGED);
		assertEquals(1, f.size());
		assertTrue(f.get(0).text.endsWith("its end at 3201,3200,1: open -> blocked by an object"), f.get(0).text);
	}

	@Test
	void aManualTileChangeOnATileTheGameChanged()
	{
		Edit e = new Edit();
		e.id = "e-7";
		e.type = Edit.Type.WALL;
		e.x = 3205;
		e.y = 3205;
		e.side = 0;
		e.on = true;
		data.tileEdits.add(e);
		a.tileFlags.put(CacheFacts.tile(3205, 3206, 0), 0);
		b.tileFlags.put(CacheFacts.tile(3205, 3206, 0), CollisionFlags.SOUTH);
		a.tileFlags.put(CacheFacts.tile(3205, 3205, 0), 0);
		b.tileFlags.put(CacheFacts.tile(3205, 3205, 0), 0);
		List<CacheCompare.Finding> f = run().of(CacheCompare.Kind.EDIT_TILE_CHANGED);
		assertEquals(1, f.size());
		assertTrue(f.get(0).text.contains("added a wall on the north side of 3205,3205,0"), f.get(0).text);
		assertTrue(f.get(0).text.contains("3205,3206,0: open -> walls south"), f.get(0).text);
	}

	@Test
	void aDoorSetShutThatIsGone()
	{
		Edit e = new Edit();
		e.id = "e-9";
		e.type = Edit.Type.DOOR;
		e.x = 3210;
		e.y = 3210;
		e.objectId = 1530;
		e.on = true;
		data.shutDoors.add(e);
		a.doors.add(CacheFacts.door(3210, 3210, 0, 1530));
		CacheCompare.Report r = run();
		assertEquals(1, r.of(CacheCompare.Kind.SHUT_DOOR_GONE).size());
		assertEquals(1, r.of(CacheCompare.Kind.DOOR_REMOVED).size());
	}

	@Test
	void wallsChangedInAReviewedRegionNeedALookOtherRegionsDoNot()
	{
		int other = CacheFacts.region(3300, 3300);
		a.blocks.put(other << 2, 5L);
		b.blocks.put(REGION << 2, 2L);
		b.blocks.put(other << 2, 6L);
		data.reviewedRegions.add(REGION);
		CacheCompare.Report r = run();
		assertEquals(1, r.of(CacheCompare.Kind.REVIEWED_REGION_CHANGED).size());
		assertEquals(Integer.toString(REGION), r.of(CacheCompare.Kind.REVIEWED_REGION_CHANGED).get(0).ref);
		assertEquals(1, r.of(CacheCompare.Kind.REGION_CHANGED).size());
	}

	@Test
	void wallsChangedOnlyOnTilesBlockedBeforeAndAfterChangeNoWalking()
	{
		b.blocks.put(REGION << 2, 2L);
		data.reviewedRegions.add(REGION);
		data.anchors.put(new int[]{3210, 3210, 0}, false);
		int[] before = new int[4 * 4096];
		int[] after = new int[4 * 4096];
		int i = 1 * 4096 + (3262 & 63) * 64 + (3262 & 63);
		before[i] = CollisionFlags.NO_FLOOR | CollisionFlags.NORTH;
		after[i] = CollisionFlags.NO_FLOOR;
		a.regionFlags.put(REGION, before);
		b.regionFlags.put(REGION, after);
		CacheCompare.Report r = run();
		assertEquals(0, r.lookCount(), r.findings.isEmpty() ? "" : r.findings.get(0).text);
		CacheCompare.Finding f = r.of(CacheCompare.Kind.REGION_CHANGED).get(0);
		assertTrue(f.text.contains("can't be walked on before or after"), f.text);
		assertTrue(f.details.get(1).contains("3262,3262,1: no floor, walls north -> no floor (blocked before and after)"),
			f.details.toString());

		// the same region with a walkable tile changed: a look, and the kept area with its nearest changed tile
		int j = (3212 & 63) * 64 + (3214 & 63);
		after[j] = CollisionFlags.OBJECT;
		r = run();
		assertEquals(1, r.of(CacheCompare.Kind.REVIEWED_REGION_CHANGED).size());
		assertTrue(r.of(CacheCompare.Kind.AREA_REGION_CHANGED).get(0).text.contains("3212,3214,0 (4 tiles away)"),
			r.of(CacheCompare.Kind.AREA_REGION_CHANGED).get(0).text);
	}

	@Test
	void newTransportLikeObjectsAreListedExceptRejectedOnes()
	{
		TransportCandidate fresh = new TransportCandidate(7, "Trapdoor", 3220, 3220, 0, 10, 0, 1, 1,
			Collections.singletonList("climb-down"), TransportCandidate.Category.CLIMB_DOWN, -1, -1);
		TransportCandidate rejected = new TransportCandidate(8, "Stairs", 3230, 3220, 0, 10, 0, 1, 1,
			Collections.singletonList("climb-up"), TransportCandidate.Category.CLIMB_UP, -1, -1);
		b.candidates.put(fresh.id(), fresh);
		b.candidates.put(rejected.id(), rejected);
		data.rejected.add(rejected.id());
		List<CacheCompare.Finding> f = run().of(CacheCompare.Kind.NEW_CANDIDATE);
		assertEquals(1, f.size());
		assertEquals("7@3220,3220,0", f.get(0).ref);
	}

	@Test
	void mapIconKindsNewAndGone()
	{
		a.mapIcons.put(45, new int[]{1, 2});
		b.mapIcons.put(46, new int[]{1});
		data.placeKinds.put(45, "Altar");
		CacheCompare.Report r = run();
		assertEquals(1, r.of(CacheCompare.Kind.PLACE_KIND_NEW).size());
		assertEquals(1, r.of(CacheCompare.Kind.PLACE_KIND_GONE).size());
		assertTrue(r.of(CacheCompare.Kind.PLACE_KIND_GONE).get(0).text.startsWith("\"Altar\" (45)"));
	}

	@Test
	void otherGameDataIsListed()
	{
		a.items.put(1, "Bronze dagger");
		b.items.put(1, "Bronze knife");
		b.npcs.put(5, "Hans");
		a.archives.put(2, Collections.singletonMap(6, 111));
		b.archives.put(2, Collections.singletonMap(6, 222));
		CacheCompare.Report r = run();
		assertEquals(Collections.singletonList("1 renamed: \"Bronze dagger\" -> \"Bronze knife\""),
			r.other.get("Items: new, removed or renamed (names only)"));
		assertEquals(Collections.singletonList("5 new: \"Hans\""), r.other.get("NPCs: new, removed or renamed (names only)"));
		assertTrue(r.overview.get(0).contains("1 changed") && r.overview.get(0).contains("object"), r.overview.toString());
	}

	@Test
	void theSameInputsGiveTheSameReport()
	{
		b.locs = new long[0];
		b.tileFlags.put(CacheFacts.tile(3201, 3200, 1), CollisionFlags.NO_FLOOR);
		List<String> first = new ArrayList<>(Collections.singletonList(CacheCompare.markdown(run())));
		assertEquals(first.get(0), CacheCompare.markdown(run()));
	}
}

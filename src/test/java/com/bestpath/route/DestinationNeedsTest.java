package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** What a destination needs: bank first only for items in the bank and not carried; the request carries it. */
public class DestinationNeedsTest
{
	private static final DestinationNeeds RING_AND_TOKENS = new DestinationNeeds(List.of(
		new DestinationNeeds.Item("Ring of visibility", new int[]{4657, 28327}, 1),
		new DestinationNeeds.Item("Ecto-token", new int[]{4278}, 25)));

	/** The needed items the player has nowhere (not even in the bank). */
	private static List<DestinationNeeds.Item> missing(DestinationNeeds needs, PlayerState p)
	{
		List<DestinationNeeds.Item> out = new ArrayList<>();
		if (p == null || p.isAllItems())
		{
			return out;
		}
		for (DestinationNeeds.Item it : needs.items)
		{
			if (it.carried(p) + it.banked(p) < it.getCount())
			{
				out.add(it);
			}
		}
		return out;
	}

	@Test
	public void bankFirstOnlyWhenSomethingIsInTheBankAndNotCarried()
	{
		PlayerState carried = new PlayerState.Builder("p").wear(EquipSlot.RING, 28327).carry(4278, 30).build();
		assertTrue(RING_AND_TOKENS.fromBank(carried).isEmpty());
		PlayerState banked = new PlayerState.Builder("p").bank(4657, 1).carry(4278, 30).build();
		assertFalse(RING_AND_TOKENS.fromBank(banked).isEmpty());
		assertEquals("Ring of visibility", RING_AND_TOKENS.fromBank(banked).get(0).toString());
		// 20 carried and 10 in the bank make 25: the bank is needed
		PlayerState split = new PlayerState.Builder("p").carry(4657, 1).carry(4278, 20).bank(4278, 10).build();
		assertEquals(List.of(RING_AND_TOKENS.items.get(1)), RING_AND_TOKENS.fromBank(split));
		// nowhere: missing, no bank trip for it
		PlayerState none = new PlayerState.Builder("p").carry(4278, 25).build();
		assertTrue(RING_AND_TOKENS.fromBank(none).isEmpty());
		assertEquals(1, missing(RING_AND_TOKENS, none).size());
		// the Data Tool's Maxed carries everything
		assertTrue(RING_AND_TOKENS.fromBank(PlayerStateBuilder.maxed()).isEmpty());
		assertTrue(missing(RING_AND_TOKENS, PlayerStateBuilder.maxed()).isEmpty());
		assertTrue(DestinationNeeds.NONE.fromBank(banked).isEmpty());
	}

	@Test
	public void itemsAreTheSameNeedWhateverTheirLabel()
	{
		assertEquals(new DestinationNeeds.Item("Gold ring", new int[]{1635}, 1), new DestinationNeeds.Item("Ring", new int[]{1635}, 1));
		assertNotEquals(new DestinationNeeds.Item("Gold ring", new int[]{1635}, 1), new DestinationNeeds.Item("Gold ring", new int[]{1635}, 2));
	}

	@Test
	public void theRequestCarriesItsNeedsWithoutBecomingAnotherDestination()
	{
		RouteRequest r = RouteRequest.places("clue STEP:easy:emote-1,", "Emote clue (easy)", List.of("a"),
			new int[]{Tiles.pack(3200, 3200, 0)});
		assertTrue(r.getNeeds().isEmpty());
		RouteRequest withNeeds = r.withNeeds(RING_AND_TOKENS);
		assertEquals(RING_AND_TOKENS, withNeeds.getNeeds());
		assertEquals("same key and places: the same destination (session exclusions are kept)", r, withNeeds);
		assertEquals("Emote clue (easy)", withNeeds.getTitle());
		assertEquals(List.of("a"), withNeeds.getNotes());
		assertEquals(3200, withNeeds.getX());
		assertTrue(new RouteRequest(1, 2, 0).getNeeds().isEmpty());
	}

	@Test
	public void followerSearchesAgainWhenTheBankNeedChanges()
	{
		RouteRequest r = new RouteRequest(3200, 3200, 0).withNeeds(RING_AND_TOKENS);
		PlayerSnapshot banked = new PlayerSnapshot(1, 1, 0, new PlayerState.Builder("p").bank(4657, 1).carry(4278, 30).build(), List.of());
		PlayerSnapshot taken = new PlayerSnapshot(1, 1, 0, new PlayerState.Builder("p").carry(4657, 1).carry(4278, 30).build(), List.of());
		PlayerSnapshot otherItem = new PlayerSnapshot(1, 1, 0, new PlayerState.Builder("p").bank(4657, 1).carry(4278, 30).carry(995, 5)
			.build(), List.of());
		assertTrue(RouteFollower.destinationNeedsChanged(r, banked, taken));
		assertFalse("something else changed", RouteFollower.destinationNeedsChanged(r, banked, otherItem));
		assertFalse("no needs", RouteFollower.destinationNeedsChanged(new RouteRequest(3200, 3200, 0), banked, taken));
	}
}

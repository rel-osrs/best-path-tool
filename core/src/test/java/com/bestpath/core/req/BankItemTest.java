package com.bestpath.core.req;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.req.Check.Outcome;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What a requirement met only with the bank says to take out (the bank card shows each in a box
 * with its picture and count): the old words, the items any one of which will do, and how many to take out.
 */
class BankItemTest
{
	private static final int LAW = 563;
	private static final int[] AXES = {1351, 1349, 1353};
	private static final RequirementText.ItemGroups GROUPS = new RequirementText.ItemGroups()
	{
		@Override
		public int[] ids(String name)
		{
			return Map.of("axe", AXES).get(name);
		}

		@Override
		public RequirementText.RuneSource rune(String name)
		{
			return name.equalsIgnoreCase("air") ? new RequirementText.RuneSource(new int[]{556, 4696}, new int[]{1381, 11998}) : null;
		}
	};

	/**
	 * Two holes, one needing a "light source" and one a "safe light source", listed two bullseye
	 * lanterns: it only tells the player to get one, as it is not used up.
	 */
	@Test
	void aKeptItemOneOnTheListStandsForIsListedOnce()
	{
		BankItem light = new BankItem("Bullseye lantern", new int[]{33, 4531, 4550}, 1);
		BankItem safe = new BankItem("Bullseye lantern", new int[]{4531, 4550, 9065}, 1);
		List<BankItem> take = new java.util.ArrayList<>();
		BankItem.addTo(take, light);
		BankItem.addTo(take, safe);
		assertEquals(1, take.size());
		assertArrayEquals(new int[]{4531, 4550}, take.get(0).getItemIds()); // only what does for both
		// nothing in common: both listed
		BankItem.addTo(take, new BankItem("Spade", new int[]{952}, 1));
		assertEquals(2, take.size());
		// used up (runes, coins): the same items add up into one line
		List<BankItem> runes = new java.util.ArrayList<>();
		BankItem.addTo(runes, new BankItem("law runes x1", new int[]{LAW}, 1, true));
		BankItem.addTo(runes, new BankItem("law runes x2", new int[]{LAW}, 2, true));
		assertEquals(1, runes.size());
		assertTrue(runes.get(0).usedUp);
		assertEquals("law runes x3", runes.get(0).getLabel());
		assertEquals(3, runes.get(0).getCount());
		// a kept item's larger count wins
		List<BankItem> two = new java.util.ArrayList<>();
		BankItem.addTo(two, new BankItem("Rope", new int[]{954}, 1));
		BankItem.addTo(two, new BankItem("Rope x2", new int[]{954}, 2));
		assertEquals(1, two.size());
		assertEquals(2, two.get(0).getCount());
	}

	/** Two fares of 30 coins after one bank visit: take out enough for both, what is carried counted once. */
	@Test
	void usedUpNeedsOfLaterStepsAddUp()
	{
		PlayerState p = new PlayerState.Builder("p").carry(PlayerState.COINS, 10).bank(PlayerState.COINS, 100)
			.carry(556, 1).bank(556, 10).build();
		BankItem fare = check("coins 30", p).getBankItems().get(0);
		assertEquals(20, fare.getCount());
		List<BankItem> take = new java.util.ArrayList<>();
		BankItem.addTo(take, fare);
		BankItem.addTo(take, fare);
		assertEquals(1, take.size());
		assertEquals("coins 60", take.get(0).getLabel());
		assertEquals(50, take.get(0).getCount(), "60 needed, 10 carried");
		// runes the same way; a third fare adds on
		BankItem.addTo(take, check("rune air x2", p).getBankItems().get(0));
		BankItem.addTo(take, check("rune air x3", p).getBankItems().get(0));
		BankItem.addTo(take, fare);
		assertEquals(List.of("coins 90", "air runes x5"), BankItem.labels(take));
		assertEquals(80, take.get(0).getCount());
		assertEquals(4, take.get(1).getCount(), "5 needed, 1 carried");
		// a used-up item without a count gets one
		List<BankItem> feathers = new java.util.ArrayList<>();
		BankItem.addTo(feathers, new BankItem("Feather", new int[]{314}, 1, true));
		BankItem.addTo(feathers, new BankItem("Feather", new int[]{314}, 1, true));
		assertEquals(List.of("Feather x2"), BankItem.labels(feathers));
		assertEquals(2, feathers.get(0).getCount());
	}

	@Test
	void runesAndCoinsAreUsedUpAnItemOnlyWhenTheDataSaysSo()
	{
		PlayerState banked = new PlayerState.Builder("b").bank(LAW, 5).bank(PlayerState.COINS, 100).bank(1351, 1).build();
		assertTrue(check("coins 30", banked).getBankItems().get(0).usedUp);
		assertTrue(!check("item axe", banked).getBankItems().get(0).usedUp);
	}

	private static Check check(String line, PlayerState p)
	{
		return RequirementText.parse(line, GROUPS).check(p);
	}

	@Test
	void anItemInTheBankSaysWhichItemsAndHowMany()
	{
		Check c = check("item " + LAW + " x3 used up", new PlayerState.Builder("p").carry(LAW, 1).bank(LAW, 10).build());
		assertEquals(Outcome.NEEDS_BANK, c.getOutcome());
		assertEquals(List.of(LAW + " x3 from the bank"), c.getMissing(), "the words as before");
		BankItem b = c.getBankItems().get(0);
		assertEquals(LAW + " x3", b.getLabel());
		assertArrayEquals(new int[]{LAW}, b.getItemIds());
		assertEquals(2, b.getCount(), "3 needed, 1 carried: take out 2");

		// an item group: any of its items
		BankItem axe = check("item axe", new PlayerState.Builder("p").bank(1353, 1).build()).getBankItems().get(0);
		assertEquals("axe", axe.getLabel());
		assertArrayEquals(AXES, axe.getItemIds());
		assertEquals(1, axe.getCount());
	}

	@Test
	void coinsAndRunes()
	{
		BankItem coins = check("coins 30", new PlayerState.Builder("p").carry(PlayerState.COINS, 10).bank(PlayerState.COINS, 100)
			.build()).getBankItems().get(0);
		assertEquals("coins 30", coins.getLabel());
		assertArrayEquals(new int[]{PlayerState.COINS}, coins.getItemIds());
		assertEquals(20, coins.getCount());

		BankItem runes = check("rune air x3", new PlayerState.Builder("p").bank(4696, 50).build()).getBankItems().get(0);
		assertEquals("air runes x3", runes.getLabel());
		assertArrayEquals(new int[]{556, 4696}, runes.getItemIds(), "the rune and the combination runes that count");
		assertEquals(3, runes.getCount());

		// not enough runes, but a staff in the bank: take out a staff (one)
		BankItem staff = check("rune air x3", new PlayerState.Builder("p").carry(556, 1).bank(11998, 1).build()).getBankItems().get(0);
		assertArrayEquals(new int[]{1381, 11998}, staff.getItemIds());
		assertEquals(1, staff.getCount());
	}

	@Test
	void onlyAMetWithTheBankCheckHasThem()
	{
		assertTrue(check("item axe", new PlayerState.Builder("p").carry(1351, 1).build()).getBankItems().isEmpty());
		assertTrue(check("item axe", PlayerStateBuilder.fresh()).getBankItems().isEmpty());
		assertTrue(check("item axe", PlayerStateBuilder.maxed()).getBankItems().isEmpty());
		// "all of": every part met with the bank, in order; a part not met at all makes it not met (nothing to take out)
		PlayerState banked = new PlayerState.Builder("p").bank(1351, 1).bank(PlayerState.COINS, 50).build();
		Check both = check("item axe; Attack 1; coins 30", banked);
		assertEquals(Outcome.NEEDS_BANK, both.getOutcome());
		assertEquals(List.of("axe", "coins 30"), BankItem.labels(both.getBankItems()));
		assertTrue(check("item axe; Attack 50", banked).getBankItems().isEmpty());
		// "one of": the choice used (met now beats met with the bank)
		Check any = check("one of (item axe | coins 30)", banked);
		assertEquals(List.of("axe"), BankItem.labels(any.getBankItems()));
		Check carriedCoins = check("one of (item axe | coins 30)", new PlayerState.Builder("p").bank(1351, 1)
			.carry(PlayerState.COINS, 30).build());
		assertEquals(Outcome.MET, carriedCoins.getOutcome());
		assertTrue(carriedCoins.getBankItems().isEmpty());
	}

	@Test
	void sameWordsItemsAndCountAreEqual()
	{
		assertEquals(new BankItem("axe", AXES, 1), new BankItem("axe", AXES.clone(), 1));
		assertEquals(new BankItem("axe", AXES, 1).hashCode(), new BankItem("axe", AXES.clone(), 1).hashCode());
		assertTrue(!new BankItem("axe", AXES, 1).equals(new BankItem("axe", AXES, 2)));
		assertEquals(1, new BankItem("x", new int[]{1}, 0).getCount(), "at least one");
		assertThrows(IllegalArgumentException.class, () -> new BankItem("x", new int[0], 1));
	}
}

package com.bestpath.clue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.ClueData;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.RequirementText;
import com.bestpath.core.req.Skill;
import com.bestpath.route.DestinationNeeds;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import org.junit.Test;

/** The clue step's card: where each item is, the STASH unit, the card's state and its words, the route's needs. */
public class ClueCardTest
{
	private static final Map<Integer, String> NAMES = Map.of(1949, "Chef's hat", 1007, "Red cape", 952, "Spade", 2836,
		"Key (medium)", 716, "Bullroarer", 4531, "Candle lantern");
	private static final IntFunction<String> NAME = NAMES::get;

	/** "Cheer at Iffie Nitter. Equip a chef hat and a red cape." (beginner, with a STASH unit; cache 2735 values). */
	private static final ClueData.Step EMOTE = new ClueData.Step("emote-2086", "emote", "beginner", "coord", new int[0],
		new int[]{Tiles.pack(3205, 3416, 0)}, Tiles.pack(3209, 3416, 0), "Cheer at Iffie Nitter. Equip a chef hat and a red cape.",
		"Fine clothes in Varrock.", "", "item 1949 worn; item 1007 worn", "", "emote 7 Cheer", 8255, "Thessalia's shop", -1, "", -1);
	private static final ClueData.Step DIG = new ClueData.Step("coordinate-1960", "coordinate", "medium", "coord",
		new int[]{2827}, new int[]{Tiles.pack(2479, 3158, 0)}, -1, "00.05S 01.13E", "", "", "item 952", "", "dig", -1, "", -1, "", -1);
	private static final ClueData.Step KEY = new ClueData.Step("cryptic-2278", "cryptic", "medium", "key", new int[]{2835},
		new int[]{Tiles.pack(2611, 3324, 1)}, -1, "In a town where thieves steal from stalls...", "Kill any Guard ... for a medium key.",
		"", "item 2836", "", "key Drawers", -1, "", -1, "", -1);

	private static DetectedClue clue(ClueData.Step s, int item)
	{
		return new DetectedClue(DetectedClue.Kind.STEP, s.getTier(), item, List.of(s), "");
	}

	private static ClueCard card(ClueData.Step s, PlayerState p, StashState stash)
	{
		return ClueCard.build(clue(s, s.getItems().length > 0 ? s.getItems()[0] : -1), p, stash, RequirementText.ItemGroups.NONE, null,
			NAME);
	}

	private static PlayerState.Builder player()
	{
		return new PlayerState.Builder("test").members(true);
	}

	@Test
	public void emoteOutfitWaitingInAFilledStashUnitIsReady()
	{
		ClueCard c = card(EMOTE, player().build(), new StashState(StashState.Built.YES, StashState.Filled.YES));
		assertEquals("Emote clue (beginner)", c.getTitle());
		assertEquals(2, c.getItems().size());
		assertEquals("Chef's hat", c.getItems().get(0).name);
		assertTrue(c.getItems().get(0).isWear());
		assertEquals(ClueCard.Where.STASH, c.getItems().get(0).getWhere());
		assertEquals(ClueCard.Where.STASH, c.getItems().get(1).getWhere());
		assertEquals(ClueCard.State.READY, c.getState());
		assertEquals("Ready: search the STASH unit for your outfit.", c.getStatus());
		assertEquals("STASH unit: built and filled.", c.stashLine());
		// the route doesn't fetch what waits at the spot
		assertTrue(c.destinationNeeds().isEmpty());
		assertEquals(ClueCard.BEGINNER_SCROLL, c.getClueItemId());
	}

	@Test
	public void emoteOutfitOnlyInTheBankMakesTheRouteBankFirst()
	{
		PlayerState p = player().bank(1949, 1).bank(1007, 1).build();
		ClueCard c = card(EMOTE, p, new StashState(StashState.Built.NO, StashState.Filled.NO));
		assertEquals(ClueCard.Where.BANK, c.getItems().get(0).getWhere());
		assertEquals(ClueCard.State.BANK, c.getState());
		assertEquals("Take from the bank: Chef's hat, Red cape.", c.getStatus());
		assertEquals("STASH unit: not built.", c.stashLine());
		assertEquals(ClueCard.Where.BANK, c.getItems().get(1).getWhere());
		DestinationNeeds n = c.destinationNeeds();
		assertEquals(2, n.fromBank(p).size());
		assertFalse(n.fromBank(p).isEmpty());
		assertEquals("Chef's hat", n.fromBank(p).get(0).toString());
	}

	@Test
	public void wornAndCarriedItemsAreReadyWhateverTheStash()
	{
		PlayerState p = player().wear(EquipSlot.HEAD, 1949).carry(1007, 1).build();
		ClueCard c = card(EMOTE, p, new StashState(StashState.Built.YES, StashState.Filled.UNKNOWN));
		assertEquals(ClueCard.Where.WORN, c.getItems().get(0).getWhere());
		assertEquals(ClueCard.Where.CARRIED, c.getItems().get(1).getWhere());
		assertEquals(ClueCard.State.READY, c.getState());
		assertEquals("Everything is ready.", c.getStatus());
		assertTrue(c.stashLine().startsWith("STASH unit: built, filled not known"));
		assertTrue(c.destinationNeeds().fromBank(p).isEmpty());
	}

	/** Where filled is not known, the card suggests Watson's noticeboard or the house chart. */
	@Test
	public void filledNotKnownSuggestsTheNoticeboard()
	{
		ClueCard c = card(EMOTE, player().build(), new StashState(StashState.Built.YES, StashState.Filled.UNKNOWN));
		assertEquals("STASH unit: built, filled not known.", c.stashLine());
		// the player is told to go to Watson's house, with a "Take me there" button
		assertTrue(c.learnsFromWatson());
		assertEquals("Go to Watson's house (Hosidius) and read his noticeboard to load your STASH units.",
			ClueCard.LEARN_FILLED_HINT);
		assertEquals(com.bestpath.core.path.Tiles.pack(1644, 3575, 0), ClueCard.watsonRequest().getTargets()[0]);
		for (StashState known : List.of(new StashState(StashState.Built.YES, StashState.Filled.YES),
			new StashState(StashState.Built.YES, StashState.Filled.NO), new StashState(StashState.Built.NO, StashState.Filled.NO)))
		{
			assertFalse("known: not sent to Watson", card(EMOTE, player().build(), known).learnsFromWatson());
		}
		// the Brittle Isle master unit: the chart never shows it filled, so reading it would not help
		ClueData.Step brittle = new ClueData.Step("emote-9999", "emote", "master", "coord", new int[0],
			new int[]{Tiles.pack(3205, 3416, 0)}, Tiles.pack(3209, 3416, 0), "Some master emote.", "", "", "", "",
			"emote 7 Cheer", net.runelite.api.gameval.VarbitID.HH_CONSTRUCTED_MASTER_SAIL, "Brittle Isle", -1, "", -1);
		ClueCard b = card(brittle, player().build(), new StashState(StashState.Built.YES, StashState.Filled.UNKNOWN));
		assertEquals("STASH unit: built, filled not known.", b.stashLine());
		assertFalse("the noticeboard would not tell", b.learnsFromWatson());
	}

	@Test
	public void stashFillNotKnownIsNotCountedAsFilled()
	{
		// the outfit is in the bank and the game doesn't say whether the STASH unit holds it: the bank is used
		PlayerState p = player().bank(1949, 1).bank(1007, 1).build();
		ClueCard c = card(EMOTE, p, new StashState(StashState.Built.YES, StashState.Filled.UNKNOWN));
		assertEquals(ClueCard.State.BANK, c.getState());
		assertFalse(c.destinationNeeds().fromBank(p).isEmpty());
	}

	/**
	 * A player who has just installed the plugin is not assumed to have nothing in the STASH units: where it is not
	 * known for sure whether an outfit is stashed, the card shows a question mark.
	 */
	@Test
	public void anOutfitNotCarriedOrBankedMayBeInAUnitNotKnownToBeFilled()
	{
		PlayerState p = player().build();
		ClueCard c = card(EMOTE, p, new StashState(StashState.Built.YES, StashState.Filled.UNKNOWN));
		for (ClueCard.ItemRow r : c.getItems())
		{
			assertEquals(ClueCard.Where.MAYBE_STASH, r.getWhere());
			assertEquals(com.bestpath.route.NeedStatus.MAYBE, c.mark(r));
		}
		assertEquals(ClueCard.State.BANK, c.getState());
		assertEquals("no bottom line (the rows say it)", "", c.getStatus());
		assertTrue("never fetched from the bank", c.destinationNeeds().fromBank(p).isEmpty());
		// known empty, or not built: missing as before
		assertEquals(ClueCard.Where.MISSING,
			card(EMOTE, p, new StashState(StashState.Built.YES, StashState.Filled.NO)).getItems().get(0).getWhere());
		assertEquals(ClueCard.Where.MISSING,
			card(EMOTE, p, new StashState(StashState.Built.NO, StashState.Filled.NO)).getItems().get(0).getWhere());
		// known filled: in the STASH unit
		assertEquals(ClueCard.Where.STASH,
			card(EMOTE, p, new StashState(StashState.Built.YES, StashState.Filled.YES)).getItems().get(0).getWhere());
	}

	/** Empty slots say "Wear: nothing", not "Wear: Item 6512" (6512 is the game's blank item). */
	@Test
	public void emptySlotsSayWearNothing()
	{
		String all = "empty head; empty cape; empty neck; empty weapon; empty body; empty shield; empty legs; empty hands;"
			+ " empty feet; empty ring; empty ammo";
		ClueData.Step bare = new ClueData.Step("emote-2095", "emote", "easy", "coord", new int[0], new int[]{Tiles.pack(2207, 4952, 0)},
			-1, "Cheer at the games room. Have nothing equipped at all when you do.", "", "", all, "", "emote 7 Cheer", -1, "", -1, "", -1);
		ClueCard c = card(bare, player().build(), StashState.UNKNOWN);
		assertTrue(c.getItems().isEmpty());
		assertEquals(1, c.getOthers().size());
		assertEquals("Wear: nothing", c.getOthers().get(0).getText());
		assertTrue(c.getOthers().get(0).isMet());
		ClueCard dressed = card(bare, player().wear(EquipSlot.HEAD, 1949).build(), StashState.UNKNOWN);
		assertEquals("Wear: nothing (take off: head)", dressed.getOthers().get(0).getText());
		assertFalse(dressed.getOthers().get(0).isMet());
		// "no boots" with an outfit
		ClueData.Step noBoots = new ClueData.Step("emote-2138", "emote", "medium", "coord", new int[0],
			new int[]{Tiles.pack(3504, 3576, 0)}, -1, "Equip a mithril plate skirt, a maple longbow and no boots.", "", "",
			"item 1949 worn; empty feet", "", "emote 18 Panic", -1, "", -1, "", -1);
		ClueCard boots = card(noBoots, player().wear(EquipSlot.HEAD, 1949).build(), StashState.UNKNOWN);
		assertEquals(1, boots.getItems().size());
		assertEquals("Wear nothing: feet", boots.getOthers().get(0).getText());
		for (ClueCard.ItemRow r : boots.getItems())
		{
			assertFalse(r.name.contains("6512"));
		}
	}

	@Test
	public void digWithTheSpadeCarried()
	{
		ClueCard c = card(DIG, player().carry(952, 1).build(), StashState.UNKNOWN);
		assertEquals("Coordinate clue (medium)", c.getTitle());
		assertEquals(ClueCard.Where.CARRIED, c.getItems().get(0).getWhere());
		assertEquals("Spade", c.getItems().get(0).name);
		assertFalse(c.getItems().get(0).isWear());
		assertEquals(ClueCard.State.READY, c.getState());
		assertEquals("no STASH unit", "", c.stashLine());
		assertEquals("the carried clue scroll", 2827, c.getClueItemId());
	}

	@Test
	public void missingItemIsSaidPlainlyAndTheRouteStillGoes()
	{
		PlayerState p = player().build();
		ClueCard c = card(KEY, p, StashState.UNKNOWN);
		assertEquals(ClueCard.Where.MISSING, c.getItems().get(0).getWhere());
		assertEquals(ClueCard.State.MISSING, c.getState());
		assertEquals("Missing: Key (medium). The route still goes there.", c.getStatus());
		// missing items don't make the route bank
		assertTrue(c.destinationNeeds().fromBank(p).isEmpty());
		assertEquals(new DestinationNeeds(List.of(new DestinationNeeds.Item("Key (medium)", new int[]{2836}, 1))),
			c.destinationNeeds());
	}

	@Test
	public void missingAndBankItemsTogether()
	{
		PlayerState p = player().bank(1949, 1).build();
		ClueCard c = card(EMOTE, p, StashState.UNKNOWN);
		assertEquals(ClueCard.State.MISSING, c.getState());
		assertEquals("Missing: Red cape. Take from the bank: Chef's hat. The route still goes there.", c.getStatus());
		assertFalse("the hat still comes from the bank", c.destinationNeeds().fromBank(p).isEmpty());
	}

	@Test
	public void lightMetByTheFireOfEternalLightNeedsNoItem()
	{
		ClueData.Step dark = new ClueData.Step("emote-2135", "emote", "medium", "coord", new int[]{1}, new int[]{Tiles.pack(3170, 9571, 0)},
			-1, "Dance in the swamp caves.", "", "", "one of (item 4531/4534 | varbit 6533 = 1)", "", "emote 12 Dance", -1, "", -1, "", -1);
		ClueCard lit = card(dark, player().varbit(6533, 1).build(), StashState.UNKNOWN);
		assertTrue(lit.getItems().isEmpty());
		assertEquals(ClueCard.State.READY, lit.getState());
		assertEquals("Nothing to bring.", lit.getStatus());
		ClueCard dim = card(dark, player().build(), StashState.UNKNOWN);
		assertEquals(1, dim.getItems().size());
		assertEquals("Candle lantern", dim.getItems().get(0).name);
		assertEquals(ClueCard.Where.MISSING, dim.getItems().get(0).getWhere());
		ClueCard carried = card(dark, player().carry(4534, 1).build(), StashState.UNKNOWN);
		assertEquals("the player own item is shown", 4534, carried.getItems().get(0).getItemId());
		assertEquals(ClueCard.Where.CARRIED, carried.getItems().get(0).getWhere());
	}

	@Test
	public void levelsAndQuestsAreRowsToo()
	{
		ClueData.Step challenge = new ClueData.Step("skillchallenge-1823", "skillchallenge", "elite", "npc", new int[0],
			new int[]{Tiles.pack(2733, 3415, 0)}, -1, "Complete a lap of the Ape Atoll agility course.", "", "",
			"Agility 25; quest Monkey Madness I", "", "talk Sherlock", -1, "", -1, "", -1);
		ClueCard c = card(challenge, player().level(Skill.AGILITY, 20).build(), StashState.UNKNOWN);
		assertEquals(2, c.getOthers().size());
		assertEquals("Agility 25 (you have 20)", c.getOthers().get(0).getText());
		assertFalse(c.getOthers().get(0).isMet());
		assertEquals("Quest: Monkey Madness I", c.getOthers().get(1).getText());
		assertEquals(ClueCard.State.MISSING, c.getState());
		assertTrue(c.getStatus().startsWith("Missing: Agility 25 (you have 20), Quest: Monkey Madness I."));
		ClueCard ok = card(challenge, player().level(Skill.AGILITY, 30).allUnlocked(true).build(), StashState.UNKNOWN);
		assertEquals(ClueCard.State.READY, ok.getState());
	}

	@Test
	public void theOwnersNoteIsOnTheCard()
	{
		ClueData.Step kill = new ClueData.Step("cryptic-1458", "cryptic", "elite", "kill", new int[]{12074}, new int[]{Tiles.pack(1766, 5344, 0)},
			-1, "Ghostly bones.", "", "", "", "Kill the barbarian spirit here", "kill Ferocious barbarian spirit", -1, "", -1, "", -1);
		ClueCard c = card(kill, player().build(), StashState.UNKNOWN);
		assertEquals("Kill the barbarian spirit here", c.getNote());
		assertEquals("", card(DIG, player().build(), StashState.UNKNOWN).getNote());
	}

	@Test
	public void maxedTestPlayerCarriesEverything()
	{
		ClueCard c = card(EMOTE, PlayerStateBuilder.maxed(), StashState.UNKNOWN);
		assertEquals(ClueCard.Where.CARRIED, c.getItems().get(0).getWhere());
		assertEquals(ClueCard.State.READY, c.getState());
		assertTrue(c.destinationNeeds().fromBank(PlayerStateBuilder.maxed()).isEmpty());
	}

	@Test
	public void noCardWithoutARoutableClue()
	{
		assertNull(ClueCard.build(DetectedClue.NONE, player().build(), StashState.UNKNOWN, RequirementText.ItemGroups.NONE, null, NAME));
		ClueCard out = ClueCard.build(clue(DIG, 2827), null, StashState.UNKNOWN, RequirementText.ItemGroups.NONE, null, NAME);
		assertEquals("Log in to see which of these items you have.", out.getStatus());
	}

	@Test
	public void requestKeyIsTheClueRoutesKey()
	{
		DetectedClue d = clue(DIG, 2827);
		assertEquals(d.request().getKey(), card(DIG, player().build(), StashState.UNKNOWN).getRequestKey());
	}
}

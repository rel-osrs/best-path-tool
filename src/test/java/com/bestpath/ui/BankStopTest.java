package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.BankList;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataBuilder;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Route;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.CoreRouteEngine;
import com.bestpath.route.DestinationNeeds;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.RouteJob;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStep;
import com.bestpath.route.TransportSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.Test;

/**
 * A route that begins at a bank in the plugin: the "Bank at <place>: take out ..." card with the
 * world map's bank symbol, the Settings tab's "Bank stop" - / + (saved with the settings, routes use it), and no
 * "take from the bank first" note any more.
 */
public class BankStopTest
{
	private static final int LAW = 563;

	private static CoreRouteEngine engine()
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		TransportLine tele = TransportLine.builder("t-tele", TransportLine.Kind.TELEPORT).name("Test teleport")
			.category("TELEPORT").end(3250, 3250, 0).ticks(5).requirements("item " + LAW + " used up")
			.teleportCategory("spell").build();
		FreeLand free = new FreeLand.Builder().set(3200, 3200, 3263, 3263, true).build();
		PluginData data = PluginDataBuilder.withBanks(PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(tele),
			Tables.empty(), free), BankList.parse(BankList.HEADER + "\nTest bank\tbooth\t\t3205,3201,0\n"));
		return new CoreRouteEngine(new PluginRouter(data, Long.MAX_VALUE));
	}

	private static RouteResult route(CoreRouteEngine e, PlayerState p, TransportSettings settings)
	{
		PlayerSnapshot snap = new PlayerSnapshot(3201, 3201, 0, p, List.of());
		return e.route(new RouteJob(1, new RouteRequest(3251, 3250, 0), snap, settings, java.util.Set.of()));
	}

	@Test
	public void theBankCardSaysWhereAndWhatToTakeOut()
	{
		RouteResult r = route(engine(), new PlayerState.Builder("p").members(true).bank(LAW, 10).build(), TransportSettings.defaults());
		List<RouteStep> steps = r.getSteps();
		RouteStep bank = steps.get(1);
		assertEquals(RouteStep.Kind.BANK, bank.getKind());
		assertEquals("Bank at Test bank: take out " + LAW, bank.getTitle());
		assertEquals(TransportSettings.DEFAULT_BANK_TICKS, bank.getTicks());
		assertNull("no skip or never-use on the bank card", bank.getTransportId());
		assertEquals(com.bestpath.core.path.Tiles.pack(3205, 3201, 0), bank.getMarkerTile());
		assertEquals(IconKey.sprite(IconChooser.BANK), IconChooser.main(bank));
		assertEquals(4 + TransportSettings.DEFAULT_BANK_TICKS + 5 + 1, r.getTotalTicks());
		for (RouteStep s : steps)
		{
			for (String n : s.getNotes())
			{
				assertFalse(n, n.contains("bank first"));
			}
		}
		// the bank stop's ticks from the Settings tab
		RouteResult longer = route(engine(), new PlayerState.Builder("p").members(true).bank(LAW, 10).build(),
			TransportSettings.defaults().withBankTicks(30));
		assertEquals(30, longer.getSteps().get(1).getTicks());
		assertEquals(4 + 30 + 5 + 1, longer.getTotalTicks());
		RouteResult tooLong = route(engine(), new PlayerState.Builder("p").members(true).bank(LAW, 10).build(),
			TransportSettings.defaults().withBankTicks(40));
		assertEquals("no quicker than walking: no bank", 50, tooLong.getTotalTicks());
	}

	private static final int HELMET = 1137;
	private static final int APRON = 1005;

	/** An emote clue's outfit: an iron medium helmet and a white apron. */
	private static RouteRequest clue(int... places)
	{
		DestinationNeeds outfit = new DestinationNeeds(List.of(new DestinationNeeds.Item("Iron med helm", new int[]{HELMET}, 1),
			new DestinationNeeds.Item("White apron", new int[]{APRON}, 1)));
		return RouteRequest.places("clue emote-test", "Emote clue (easy)", List.of(), places).withNeeds(outfit);
	}

	private static RouteResult route(CoreRouteEngine e, PlayerState p, RouteRequest request)
	{
		return e.route(new RouteJob(1, request, new PlayerSnapshot(3201, 3201, 0, p, List.of()), TransportSettings.defaults(), java.util.Set.of()));
	}

	/**
	 * A clue whose outfit is only in the bank always starts at a bank (the bank-first route only,
	 * even when going straight there is quicker); the bank card lists the outfit too. No "take from the bank first"
	 * header any more.
	 */
	@Test
	public void aClueWhoseOutfitIsOnlyInTheBankStartsAtABank()
	{
		CoreRouteEngine e = engine();
		PlayerState banked = new PlayerState.Builder("p").members(true).carry(APRON, 1).bank(HELMET, 1).build();
		RouteResult r = route(e, banked, clue(Tiles.pack(3230, 3201, 0)));
		List<RouteStep> steps = r.getSteps();
		assertEquals(RouteStep.Kind.BANK, steps.get(1).getKind());
		assertEquals("only what the clue needs from the bank", "Bank at Test bank: take out Iron med helm", steps.get(1).getTitle());
		assertEquals("4 to the bank, the stop, 25 on (straight there: 29)", 4 + TransportSettings.DEFAULT_BANK_TICKS + 25, r.getTotalTicks());
		assertTrue(r.getNotes().toString(), r.getNotes().isEmpty());

		// several places (a clue step's usual request): the same, to the place chosen
		r = route(e, banked, clue(Tiles.pack(3230, 3201, 0), Tiles.pack(3240, 3240, 0)));
		assertEquals(RouteStep.Kind.BANK, r.getSteps().get(1).getKind());
		assertEquals(Tiles.pack(3230, 3201, 0), r.getDestination());
		assertEquals(4 + TransportSettings.DEFAULT_BANK_TICKS + 25, r.getTotalTicks());

		// the runes for the teleport in the bank too: the card lists both, the way on teleports
		PlayerState both = new PlayerState.Builder("p").members(true).carry(APRON, 1).bank(HELMET, 1).bank(LAW, 10).build();
		r = route(e, both, clue(Tiles.pack(3251, 3250, 0)));
		assertEquals("Bank at Test bank: take out " + LAW + ", Iron med helm", r.getSteps().get(1).getTitle());
		assertEquals(4 + TransportSettings.DEFAULT_BANK_TICKS + 5 + 1, r.getTotalTicks());

		// the outfit carried: straight there, no bank
		PlayerState carried = new PlayerState.Builder("p").members(true).carry(APRON, 1).carry(HELMET, 1).build();
		r = route(e, carried, clue(Tiles.pack(3230, 3201, 0)));
		assertEquals(29, r.getTotalTicks());
		for (RouteStep s : r.getSteps())
		{
			assertNotEquals(RouteStep.Kind.BANK, s.getKind());
		}
		// missing (not in the bank either): no bank helps, straight there
		PlayerState missing = new PlayerState.Builder("p").members(true).carry(APRON, 1).build();
		assertEquals(29, route(e, missing, clue(Tiles.pack(3230, 3201, 0))).getTotalTicks());
	}

	/**
	 * When the bank tells you to get an item, there is a box with the item: the bank step
	 * carries each item to take out with its items and count; its card says "Bank at <bank>" and lists them in a box
	 * with the bank symbol; no coordinates on the cards.
	 */
	@Test
	public void theBankCardHasABoxWithTheItemsToTakeOut() throws Exception
	{
		CoreRouteEngine e = engine();
		// 1 law rune carried for the teleport? none: 10 in the bank; the clue's helmet in the bank, the apron carried
		PlayerState both = new PlayerState.Builder("p").members(true).carry(APRON, 1).bank(HELMET, 1).bank(LAW, 10).build();
		RouteResult r = route(e, both, clue(Tiles.pack(3251, 3250, 0)));
		RouteStep bank = r.getSteps().get(1);
		assertEquals(RouteStep.Kind.BANK, bank.getKind());
		List<com.bestpath.route.StepInfo.TakeOut> take = bank.getInfo().getTakeOut();
		assertEquals(2, take.size());
		assertEquals(LAW, take.get(0).getItemId());
		assertEquals(1, take.get(0).getCount());
		assertEquals(String.valueOf(LAW), take.get(0).getLabel());
		assertEquals(HELMET, take.get(1).getItemId());
		assertEquals("Iron med helm", take.get(1).getLabel());
		assertEquals(com.bestpath.route.NeedStatus.BANK, take.get(1).getStatus());
		assertEquals("Test bank", bank.getInfo().getName());
		// no coordinates: the bank card and the teleport card have no "at x, y" / "to x, y" line
		for (RouteStep s : r.getSteps())
		{
			if (s.getKind() != RouteStep.Kind.WALK)
			{
				assertEquals(s.getTitle(), "", s.getDetail());
			}
		}
		// the teleport card's needs: its rune in the bank gets the bank symbol
		RouteStep tele = r.getSteps().get(2);
		assertEquals(com.bestpath.route.NeedStatus.BANK, tele.getInfo().getNeeds().get(0).getStatus());

		// the card: "Bank at Test bank", its ticks, and the box (names from the game when it has them)
		Map<Integer, String> names = Map.of(LAW, "Law rune");
		IconProvider named = new IconProvider()
		{
			@Override
			public void load(IconKey key, java.util.function.Consumer<java.awt.image.BufferedImage> done)
			{
			}

			@Override
			public void itemName(int itemId, java.util.function.Consumer<String> done)
			{
				done.accept(names.get(itemId));
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			StepCard card = new StepCard(1, bank, new IconCache(named), s -> { }, s -> { }, null, null);
			assertEquals("Bank at Test bank", card.title.getText());
			assertEquals(TransportSettings.DEFAULT_BANK_TICKS + " ticks", card.detailLine.getText());
			assertEquals(List.of("Law rune", "Iron med helm"), card.bankBox.lines);
			StepCard plain = new StepCard(1, bank, new IconCache(IconProvider.NONE), s -> { }, s -> { }, null, null);
			assertEquals("no names known: the data's words", List.of("Item " + LAW, "Iron med helm"), plain.bankBox.lines);
		});
		// two of an item, one carried: take out one more
		PlayerState two = new PlayerState.Builder("p").members(true).carry(APRON, 1).carry(HELMET, 1).bank(HELMET, 1).build();
		DestinationNeeds helmets = new DestinationNeeds(List.of(new DestinationNeeds.Item("Iron med helm", new int[]{HELMET}, 2)));
		RouteResult r2 = route(e, two, RouteRequest.places("clue x", "Clue", List.of(), new int[]{Tiles.pack(3230, 3201, 0)}).withNeeds(helmets));
		assertEquals(RouteStep.Kind.BANK, r2.getSteps().get(1).getKind());
		assertEquals(1, r2.getSteps().get(1).getInfo().getTakeOut().get(0).getCount());
		assertEquals(List.of(new com.bestpath.core.req.BankItem("Iron med helm x2", new int[]{HELMET}, 1)),
			CoreRouteEngine.takeOut(helmets.fromBank(two), two));
	}

	@Test
	public void bankTitleWithNothingToTakeOut()
	{
		assertEquals("Bank at Edgeville bank", CoreRouteEngine.bankTitle("Edgeville bank", List.of()));
		assertEquals("Bank at Edgeville bank: take out coins 30, axe", CoreRouteEngine.bankTitle("Edgeville bank", List.of("coins 30", "axe")));
	}

	@Test
	public void theBankStopIsSavedWithTheSettings()
	{
		TransportSettings d = TransportSettings.defaults();
		assertEquals(30, d.getBankTicks());
		assertFalse("the default is not written", d.encode().contains("bank="));
		TransportSettings s = d.withBankTicks(22);
		assertNotEquals(d, s);
		assertEquals(22, TransportSettings.decode(s.encode()).getBankTicks());
		assertEquals(s, TransportSettings.decode(s.encode()));
		assertEquals(0, d.withBankTicks(-5).getBankTicks());
		assertEquals(TransportSettings.MAX_BANK_TICKS, d.withBankTicks(10_000).getBankTicks());
		assertEquals(22, s.withCategory(TransportSettings.Category.DOORS, false).withTickChange("t", 3, "T").getBankTicks());
	}

	@Test
	public void theSettingsTabsMinusAndPlusChangeTheBankStop() throws Exception
	{
		List<TransportSettings> changes = new ArrayList<>();
		PanelActions record = new PanelActions()
		{
			@Override
			public void findRoute(RouteRequest request)
			{
			}

			@Override
			public void clearRoute()
			{
			}

			@Override
			public void useClue()
			{
			}

			@Override
			public void skipForThisRoute(String transportId)
			{
			}

			@Override
			public void clearSkips()
			{
			}

			@Override
			public void settingsChanged(TransportSettings settings)
			{
				changes.add(settings);
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsTab tab = new SettingsTab(record, new IconCache(IconProvider.NONE));
			assertEquals("30 ticks", tab.bankTicks.stepper.total.getText());
			tab.bankTicks.click(1);
			tab.bankTicks.click(1);
			assertEquals("32 ticks", tab.bankTicks.stepper.total.getText());
			assertTrue("sent after a pause, not per click", changes.isEmpty());
			tab.bankTicks.send();
			assertEquals(1, changes.size());
			assertEquals(32, changes.get(0).getBankTicks());
			tab.show(TransportSettings.defaults().withBankTicks(3));
			assertEquals("3 ticks", tab.bankTicks.stepper.total.getText());
			for (int i = 0; i < 5; i++)
			{
				tab.bankTicks.click(-1);
			}
			assertEquals("never below 0", "0 ticks", tab.bankTicks.stepper.total.getText());
		});
	}

	@Test
	public void boardingABoatIsTenTicksUnlessThePlayerChangesIt() throws Exception
	{
		// boarding a boat 10 ticks by default, player adjustable (and the same to leave it)
		TransportSettings d = TransportSettings.defaults();
		assertEquals(10, d.getBoardTicks());
		assertFalse("the default is not written", d.encode().contains("board="));
		TransportSettings s = d.withBoardTicks(4);
		assertNotEquals(d, s);
		assertEquals(4, TransportSettings.decode(s.encode()).getBoardTicks());
		assertEquals(s, TransportSettings.decode(s.encode()));
		assertEquals(0, d.withBoardTicks(-5).getBoardTicks());
		assertEquals(4, s.withKeepBoat(true).withBankTicks(3).getBoardTicks());
		List<TransportSettings> changes = new ArrayList<>();
		PanelActions record = new PanelActions()
		{
			@Override
			public void findRoute(RouteRequest request)
			{
			}

			@Override
			public void clearRoute()
			{
			}

			@Override
			public void useClue()
			{
			}

			@Override
			public void skipForThisRoute(String transportId)
			{
			}

			@Override
			public void clearSkips()
			{
			}

			@Override
			public void settingsChanged(TransportSettings settings)
			{
				changes.add(settings);
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsTab tab = new SettingsTab(record, new IconCache(IconProvider.NONE));
			assertEquals("10 ticks", tab.boardTicks.stepper.total.getText());
			tab.boardTicks.click(-1);
			tab.boardTicks.click(-1);
			assertEquals("8 ticks", tab.boardTicks.stepper.total.getText());
			assertTrue("sent after a pause, not per click", changes.isEmpty());
			tab.boardTicks.send();
			assertEquals(1, changes.size());
			assertEquals(8, changes.get(0).getBoardTicks());
			tab.show(TransportSettings.defaults().withBoardTicks(25));
			assertEquals("25 ticks", tab.boardTicks.stepper.total.getText());
		});
	}

	@Test
	public void routesWithNothingInTheBankHaveNoBankStep()
	{
		RouteResult r = route(engine(), new PlayerState.Builder("p").members(true).carry(LAW, 1).build(), TransportSettings.defaults());
		for (RouteStep s : r.getSteps())
		{
			assertNotEquals(RouteStep.Kind.BANK, s.getKind());
		}
		assertEquals(6, r.getTotalTicks());
		Route.Step.Kind.valueOf("BANK"); // the core kind exists for the card
	}
}

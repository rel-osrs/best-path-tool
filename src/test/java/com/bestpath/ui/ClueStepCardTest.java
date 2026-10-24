package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.clue.ClueCard;
import com.bestpath.clue.ClueWatcher;
import com.bestpath.clue.DetectedClue;
import com.bestpath.clue.StashState;
import com.bestpath.core.data.ClueData;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.RequirementText;
import com.bestpath.route.NeedStatus;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.RouteStep;
import com.bestpath.route.TransportSettings;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import net.runelite.api.gameval.SpriteID;
import org.junit.Test;

/** The clue step's card on the Route tab: last card of the clue's route only, kept on arrival, its lines and pictures. */
public class ClueStepCardTest
{
	private static final ClueData.Step EMOTE = new ClueData.Step("emote-2092", "emote", "easy", "coord", new int[]{10180},
		new int[]{Tiles.pack(3200, 3210, 0)}, Tiles.pack(3201, 3210, 0), "Cheer at the sample spot. Equip a chef hat.", "A sample spot.",
		"", "item 1949 worn", "Stand by the sample sign.", "emote 7 Cheer", 5199, "Sample place", -1, "", -1);
	private static final ClueData DATA = new ClueData(List.of(EMOTE));

	private static RouteTab tab()
	{
		PanelActions none = new PanelActions()
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
			}
		};
		return new RouteTab(none, TransportSettings::defaults, new IconCache(IconProvider.NONE));
	}

	private static ClueCard card(DetectedClue d, StashState s)
	{
		return ClueCard.build(d, new PlayerState.Builder("p").members(true).build(), s, RequirementText.ItemGroups.NONE, null,
			Map.of(1949, "Chef's hat")::get);
	}

	@Test
	public void lastCardOfTheCluesRouteOnlyAndKeptOnArrival() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RouteTab t = tab();
			DetectedClue d = new ClueWatcher(DATA).onInventory(List.of(10180), id -> -1);
			ClueCard c = card(d, new StashState(StashState.Built.YES, StashState.Filled.YES));
			RouteRequest req = d.request();
			RouteStep ladder = new RouteStep(RouteStep.Kind.TRAVERSAL, "Ladder", "", 2, List.of(), "t-ladder", new int[0],
				Tiles.pack(3200, 3205, 0), Tiles.pack(3200, 3205, 1));
			t.showClueCard(c);
			assertNull("no route yet", t.clueCardShown);
			t.show(RouteResults.ready(3, req, List.of(ladder)), Set.of());
			ClueStepCard shown = t.clueCardShown;
			assertNotNull(shown);
			assertEquals(1, t.cards.size());
			// its lines, in order
			List<String> lines = shown.lines;
			assertEquals("Emote clue (easy)", lines.get(0));
			assertEquals("\"Cheer at the sample spot. Equip a chef hat.\"", lines.get(1));
			assertEquals("A sample spot.", lines.get(2));
			// no "Perform the Cheer emote" row (the clue plugin shows it), a short bottom
			assertEquals("the clue's note, a plain line", "Note: Stand by the sample sign.", lines.get(3));
			assertEquals("Wear: Chef's hat | In the STASH unit", lines.get(4));
			assertEquals("STASH unit: built and filled.", lines.get(5));
			// no "ready" text
			assertEquals(6, lines.size());
			// the card changes (the outfit taken out of the STASH unit and banked): replaced in place, still one
			ClueCard empty = card(d, new StashState(StashState.Built.YES, StashState.Filled.NO));
			t.showClueCard(empty);
			assertEquals(empty, t.clueCardShown.card);
			assertTrue(t.clueCardShown.lines.contains("Wear: Chef's hat | Missing"));
			// arrived: the result has no request any more, the card stays
			t.show(RouteResult.status(4, null, RouteStatus.ARRIVED, "You have arrived (Emote clue (easy))."), Set.of());
			assertNotNull(t.clueCardShown);
			// another destination: no clue card
			t.show(RouteResults.ready(5, new RouteRequest(3210, 3200, 0), List.of(ladder)), Set.of());
			assertNull(t.clueCardShown);
			// the clue's route again, then the clue is gone (null card): the card goes
			t.show(RouteResults.ready(6, req, List.of(ladder)), Set.of());
			assertNotNull(t.clueCardShown);
			t.showClueCard(null);
			assertNull(t.clueCardShown);
		});
	}

	/** A made-up step needing a chef's hat (worn), a spade, coins 5 and a level and a quest. */
	private static final ClueData.Step MIXED = new ClueData.Step("emote-9999", "emote", "medium", "coord", new int[]{10180},
		new int[]{Tiles.pack(3200, 3210, 0)}, Tiles.pack(3201, 3210, 0), "Mixed sample.", "A sample spot.", "",
		"item 1949 worn; item 952; item 1351; Agility 20; quest Plague City", "", "emote 7 Cheer", 5199, "Sample place", -1, "", -1);

	/**
	 * Each item row and level / quest row has its mark: worn, carried or waiting in the built, filled
	 * STASH unit: a check; in the bank: the bank symbol; missing: an X. The coloured lines stay.
	 */
	@Test
	public void eachRowHasItsMark() throws Exception
	{
		DetectedClue d = new ClueWatcher(new ClueData(List.of(MIXED))).onInventory(List.of(10180), id -> -1);
		PlayerState p = new PlayerState.Builder("p").members(true).bank(952, 1).level(com.bestpath.core.req.Skill.AGILITY, 25).build();
		ClueCard c = ClueCard.build(d, p, new StashState(StashState.Built.YES, StashState.Filled.YES), RequirementText.ItemGroups.NONE,
			null, Map.of(1949, "Chef's hat", 952, "Spade", 1351, "Bronze axe")::get);
		SwingUtilities.invokeAndWait(() ->
		{
			ClueStepCard card = new ClueStepCard(3, c, new IconCache(IconProvider.NONE), null);
			// the hat in the STASH unit, the spade in the bank, the axe missing; the quest not done. Agility is met for
			// good: no row
			assertEquals(List.of(NeedStatus.HAVE, NeedStatus.BANK, NeedStatus.MISSING, NeedStatus.MISSING), card.marks);
			assertTrue(card.lines.contains("Wear: Chef's hat | In the STASH unit"));
			assertTrue(card.lines.contains("Spade | In your bank"));
			assertTrue(card.lines.contains("Bronze axe | Missing"));
			assertFalse(card.lines.contains("Agility 20 | Done"));
		});
		// logged out: no marks (nothing is known)
		ClueCard out = ClueCard.build(d, null, StashState.UNKNOWN, RequirementText.ItemGroups.NONE, null, id -> null);
		SwingUtilities.invokeAndWait(() -> assertTrue(new ClueStepCard(1, out, new IconCache(IconProvider.NONE), null).marks.isEmpty()));
		assertEquals(NeedStatus.UNKNOWN, out.mark(out.getItems().get(0)));
	}

	@Test
	public void picturesForTheRows()
	{
		assertEquals(IconKey.sprite(SpriteID.SideIcons.QUEST), IconChooser.clueOther("Quest: Regicide"));
		assertEquals(IconKey.smallSkill(com.bestpath.core.req.Skill.AGILITY), IconChooser.clueOther("Agility 25 (you have 20)"));
	}

	/** Unknown STASH contents: go to Watson's house, a "Take me there" button; "Wear: nothing" always shows. */
	@Test
	public void unknownStashSendsThePlayerToWatson() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			DetectedClue d = new ClueWatcher(DATA).onInventory(List.of(10180), id -> -1);
			java.util.List<String> ran = new java.util.ArrayList<>();
			ClueStepCard unknown = new ClueStepCard(1, card(d, new StashState(StashState.Built.YES, StashState.Filled.UNKNOWN)),
				new IconCache(IconProvider.NONE), () -> ran.add("watson"));
			assertTrue(unknown.lines.contains(ClueCard.LEARN_FILLED_HINT));
			assertNotNull(unknown.watson);
			assertEquals("Take me there", unknown.watson.getText());
			unknown.watson.doClick();
			assertEquals(List.of("watson"), ran);
			// no "Maybe in the STASH unit (not known yet)" line at the bottom; the box spans the card
			assertTrue(unknown.lines.stream().noneMatch(l -> l.startsWith("Maybe in the STASH unit (not known yet)")));
			unknown.setSize(unknown.getPreferredSize());
			unknown.doLayout();
			java.awt.Component box = ((java.awt.BorderLayout) unknown.getLayout()).getLayoutComponent(java.awt.BorderLayout.SOUTH);
			assertNotNull(box);
			assertEquals(0, box.getX() - unknown.getInsets().left);
			// known: no hint, no button
			ClueStepCard known = new ClueStepCard(1, card(d, new StashState(StashState.Built.YES, StashState.Filled.NO)),
				new IconCache(IconProvider.NONE), () -> ran.add("x"));
			assertNull(known.watson);
			assertTrue(!known.lines.contains(ClueCard.LEARN_FILLED_HINT));
		});
	}
}

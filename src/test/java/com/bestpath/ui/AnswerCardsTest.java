package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.RequirementText;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import com.bestpath.route.TransportSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.Test;

/**
 * A step card whose route rests on an answer not given yet asks it with Yes / No under the card; the
 * Settings tab's folding "Your answers" lists every answer to change or clear. Also a STASH
 * put-back card has Skip only, no "Never use" and no - / +.
 */
public class AnswerCardsTest
{
	private static final String TREE = "Axe stored at the canoe stations";

	/** The answers the sidebar sent: "answer=true|false|null". */
	private static final class Recorder implements PanelActions
	{
		final List<String> sent = new ArrayList<>();

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

		@Override
		public void answer(String answer, Boolean yes)
		{
			sent.add(answer + "=" + yes);
		}
	}

	private static RouteStep treeStep(String id, PlayerState player)
	{
		String req = "answer " + TREE;
		TransportLine line = TransportLine.builder(id, TransportLine.Kind.NETWORK).name("Spirit tree: Port Sarim")
			.category("TRAVEL").end(3058, 3257, 0).ticks(3).network("Spirit tree").requirements(req).build();
		StepInfo info = StepInfo.of(line, false, RequirementText.parse(req, RequirementText.ItemGroups.of(Map.of())), null,
			player, null);
		return new RouteStep(RouteStep.Kind.TELEPORT, "Spirit tree to Port Sarim", "", 5, List.of(), id, new int[0],
			Tiles.pack(3058, 3257, 0)).withInfo(info);
	}

	private static PlayerState asking()
	{
		return new PlayerState.Builder("p").members(true).askUnanswered(true).build();
	}

	@Test
	public void theCardAsksAndYesIsSentOnce() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			RouteTab tab = new RouteTab(r, TransportSettings::defaults, new IconCache(IconProvider.NONE));
			tab.show(RouteResults.ready(3, new RouteRequest(3058, 3257, 0),
				List.of(treeStep("tree-a", asking()), treeStep("tree-b", asking()))), Set.of());
			StepCard first = tab.cards.get(0);
			StepCard.QuestionBox box = first.questionBox;
			assertNotNull("the question is under the card", box);
			assertEquals(List.of(TREE), PanelDriver.answers(box));
			PanelDriver.button(box, TREE, true).doClick(0);
			assertEquals(List.of(TREE + "=true"), r.sent);
			assertFalse(PanelDriver.button(box, TREE, true).isEnabled());
			assertFalse(PanelDriver.button(box, TREE, false).isEnabled());
			assertEquals(StepCard.QuestionBox.SAVED_YES, PanelDriver.stateText(box, TREE));
			// the same question on another card shows it answered too
			StepCard.QuestionBox other = tab.cards.get(1).questionBox;
			assertFalse(PanelDriver.button(other, TREE, false).isEnabled());

			// the new route (searched again with the answer) has no question
			PlayerState yes = new PlayerState.Builder("p").members(true).askUnanswered(true).answer(TREE, true).build();
			tab.show(RouteResults.ready(4, new RouteRequest(3058, 3257, 0), List.of(treeStep("tree-a", yes))), Set.of());
			assertNull(tab.cards.get(0).questionBox);
		});
	}

	@Test
	public void noIsSentToo() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			RouteTab tab = new RouteTab(r, TransportSettings::defaults, new IconCache(IconProvider.NONE));
			tab.show(RouteResults.ready(3, new RouteRequest(3058, 3257, 0), List.of(treeStep("tree-a", asking()))), Set.of());
			StepCard.QuestionBox box = tab.cards.get(0).questionBox;
			PanelDriver.button(box, TREE, false).doClick(0);
			assertEquals(List.of(TREE + "=false"), r.sent);
			assertEquals(StepCard.QuestionBox.SAVED_NO, PanelDriver.stateText(box, TREE));
		});
	}

	@Test
	public void aStashCardHasSkipOnly() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			RouteTab tab = new RouteTab(r, TransportSettings::defaults, new IconCache(IconProvider.NONE));
			RouteStep stash = new RouteStep(RouteStep.Kind.STASH, "Put the items back", "", 3, List.of(),
				"stash-return:1", new int[0], Tiles.pack(3200, 3200, 0));
			RouteStep tree = treeStep("tree-a", asking());
			tab.show(RouteResults.ready(3, new RouteRequest(3058, 3257, 0), List.of(stash, tree)), Set.of());
			StepCard s = tab.cards.get(0);
			assertNull("no Never use on a STASH card", s.neverButton);
			assertNull("no - / + on a STASH card", s.stepper);
			assertNull(PanelDriver.ticksText(s));
			assertTrue(texts(s).contains("Skip"));
			// a transport's card keeps both
			StepCard t = tab.cards.get(1);
			assertNotNull(t.neverButton);
			assertNotNull(t.stepper);
		});
	}

	private static List<String> texts(java.awt.Container c)
	{
		List<String> out = new ArrayList<>();
		for (java.awt.Component k : c.getComponents())
		{
			if (k instanceof javax.swing.JButton)
			{
				out.add(((javax.swing.JButton) k).getText());
			}
			if (k instanceof java.awt.Container)
			{
				out.addAll(texts((java.awt.Container) k));
			}
		}
		return out;
	}

	@Test
	public void settingsListsAnswersToChangeOrClear() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			SettingsTab tab = new SettingsTab(r, new IconCache(IconProvider.NONE));
			assertFalse("folded at first", tab.answers.body.isVisible());
			tab.answers.toggle();
			assertTrue(tab.answers.body.isVisible());
			Map<String, Boolean> given = new LinkedHashMap<>();
			given.put(TREE, true);
			given.put("Respawn point set to Prifddinas", false);
			tab.showAnswers(given);
			// sorted by their question: "Have you planted..." before "Is your respawn point..."
			assertEquals(List.of(TREE, "Respawn point set to Prifddinas"), new ArrayList<>(tab.answers.answers.keySet()));
			List<String> buttons = texts(tab);
			assertEquals(2, buttons.stream().filter("Change"::equals).count());
			assertEquals(2, buttons.stream().filter("Clear"::equals).count());
			// change: shown at once and sent
			tab.answers.setAnswer(TREE, false);
			assertEquals(Boolean.FALSE, tab.answers.answers.get(TREE));
			// clear: gone from the list (asked again)
			tab.answers.setAnswer("Respawn point set to Prifddinas", null);
			assertEquals(Map.of(TREE, false), tab.answers.answers);
			assertEquals(List.of(TREE + "=false", "Respawn point set to Prifddinas=null"), r.sent);
			// a click on "Change" sends the other answer
			clickFirst(tab, "Change");
			assertEquals(TREE + "=true", r.sent.get(r.sent.size() - 1));
			clickFirst(tab, "Clear");
			assertTrue(tab.answers.answers.isEmpty());
			assertEquals(TREE + "=null", r.sent.get(r.sent.size() - 1));
		});
	}

	@Test
	public void settingsListsTheQuestionsNotAnsweredYet() throws Exception
	{
		// answer them in advance; answered rows keep Change / Clear
		List<String> all = com.bestpath.route.AnswerQuestions.answersIn(List.of(
			RequirementText.parse("answer " + TREE, RequirementText.ItemGroups.of(Map.of())),
			RequirementText.parse("one of (answer Respawn point set to Prifddinas | Magic 50); answer axe STORED at the canoe stations",
				RequirementText.ItemGroups.of(Map.of()))));
		// each once whatever its case, sorted by question
		assertEquals(List.of(TREE, "Respawn point set to Prifddinas"), all);
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			SettingsTab tab = new SettingsTab(r, new IconCache(IconProvider.NONE));
			tab.answers.toggle();
			tab.showQuestions(all);
			assertEquals(all, tab.answers.getUnanswered());
			List<String> buttons = texts(tab);
			assertEquals(2, buttons.stream().filter("Yes"::equals).count());
			assertEquals(2, buttons.stream().filter("No"::equals).count());
			assertEquals(0, buttons.stream().filter("Change"::equals).count());
			// answered (any case): no longer in the open list
			tab.showAnswers(Map.of("axe STORED at the canoe stations", true));
			assertEquals(List.of("Respawn point set to Prifddinas"), tab.answers.getUnanswered());
			buttons = texts(tab);
			assertEquals(1, buttons.stream().filter("Yes"::equals).count());
			assertEquals(1, buttons.stream().filter("Change"::equals).count());
			// "No" on an open question: sent, and it moves up to the answers
			clickFirst(tab, "No");
			assertEquals("Respawn point set to Prifddinas=false", r.sent.get(r.sent.size() - 1));
			assertEquals(Boolean.FALSE, tab.answers.answers.get("Respawn point set to Prifddinas"));
			assertTrue(tab.answers.getUnanswered().isEmpty());
		});
	}

	private static void clickFirst(java.awt.Container c, String text)
	{
		find(c, text).doClick(0);
	}

	private static javax.swing.JButton find(java.awt.Container c, String text)
	{
		for (java.awt.Component k : c.getComponents())
		{
			if (k instanceof javax.swing.JButton && text.equals(((javax.swing.JButton) k).getText()))
			{
				return (javax.swing.JButton) k;
			}
			if (k instanceof java.awt.Container)
			{
				javax.swing.JButton b = find((java.awt.Container) k, text);
				if (b != null)
				{
					return b;
				}
			}
		}
		return null;
	}
}

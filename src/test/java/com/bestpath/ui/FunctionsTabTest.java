package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.QuestHelperPath;
import com.bestpath.route.RecentDestinations;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.SavedLocations;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.junit.Test;

/** The Functions tab, Quest Helper's step and the recent destinations. */
public class FunctionsTabTest
{
	private final List<String> calls = new ArrayList<>();
	private int routeTabShown;
	/** Where the fake player stands (-1: logged out). */
	private int playerTile = -1;

	private FunctionsTab tab()
	{
		return new FunctionsTab(new PanelActions()
		{
			@Override
			public void findRoute(RouteRequest request)
			{
				calls.add("route " + request.label());
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
			public void useQuestStep()
			{
				calls.add("quest");
			}

			@Override
			public void setConfig(String key, String value)
			{
				calls.add("config " + key + " " + value);
			}

			@Override
			public void removeRecent(int index)
			{
				calls.add("remove " + index);
			}

			@Override
			public void clearRecent()
			{
				calls.add("clear");
			}

			@Override
			public void saveLocation(String name, int tile)
			{
				calls.add("save " + name + " " + Tiles.x(tile) + "," + Tiles.y(tile) + "," + Tiles.plane(tile));
			}

			@Override
			public void removeSavedLocation(int index)
			{
				calls.add("unsave " + index);
			}

			@Override
			public int playerTile()
			{
				return playerTile;
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
			public void settingsChanged(com.bestpath.route.TransportSettings settings)
			{
			}
		}, () -> routeTabShown++, new IconCache(IconProvider.NONE));
	}

	@Test
	public void theQuestButtonWorksOnlyWithAStep()
	{
		FunctionsTab t = tab();
		assertFalse(t.questButton.isEnabled());
		assertEquals(FunctionsTab.NO_QUEST_STEP, t.questText.getText());
		RouteRequest step = RouteRequest.places(QuestHelperPath.KEY, QuestHelperPath.TITLE, List.of(),
			new int[]{Tiles.pack(3208, 3220, 2)});
		t.showQuestStep(step, true);
		assertTrue(t.questButton.isEnabled());
		assertEquals("Last place sent: 3208, 3220, 2.", t.questText.getText());
		assertTrue(t.followBox.isSelected());
		t.showQuestStep(step, false);
		assertFalse("the switch follows the setting", t.followBox.isSelected());
		// the setting "Routes from other plugins" is switched here too
		t.followBox.doClick();
		assertEquals(List.of("config allowPluginRoutes true"), calls);
		calls.clear();
		t.questButton.doClick();
		assertEquals(List.of("quest"), calls);
		assertEquals(1, routeTabShown);
	}

	@Test
	public void recentDestinationsAreButtons()
	{
		FunctionsTab t = tab();
		assertTrue(buttons(t).stream().noneMatch(b -> "Clear the list".equals(b.getText())));
		RouteRequest quest = RouteRequest.places(QuestHelperPath.KEY, QuestHelperPath.TITLE, List.of(),
			new int[]{Tiles.pack(3208, 3220, 2)});
		t.showRecent(RecentDestinations.NONE.with(RouteRequest.fromMap(2900, 3500)).with(new RouteRequest(3222, 3218, 0)).with(quest));
		FunctionCard card = t.cards.get(1);
		assertEquals("Recent destinations", card.title);
		assertEquals("3 places", card.summary.getText());
		List<String> labels = new ArrayList<>();
		JPanel page = t.pages.get("Recent destinations");
		for (JButton b : buttons(page))
		{
			labels.add(b.getText());
		}
		assertEquals(List.of("< Back", "Plugin route (3208, 3220, 2)", "x", "3222, 3218, 0", "x", "2900, 3500 (world map)",
			"x", "Clear the list"), labels);
		buttons(page).get(3).doClick(); // 3222, 3218, 0
		buttons(page).get(4).doClick(); // its x
		buttons(page).get(7).doClick();
		assertEquals(List.of("route 3222, 3218, 0", "remove 1", "clear"), calls);
		assertEquals(1, routeTabShown);
	}

	/**
	 * The functions are titled cards; a card is not a dropdown but opens its own page, with a back button to the
	 * function select screen.
	 */
	@Test
	public void eachFunctionIsACardThatOpensItsOwnPage()
	{
		FunctionsTab t = tab();
		assertNull(t.shownPage); // the select screen first
		List<String> titles = new ArrayList<>();
		for (FunctionCard c : t.cards)
		{
			titles.add(c.title);
		}
		// Quest Helper moved into "Integrated plugins"
		assertEquals(List.of("Saved locations", "Recent destinations", "Nearest", FunctionsTab.INTEGRATED, "Slayer task",
			"Sea charting"), titles);
		FunctionCard quest = t.cards.get(3);
		assertEquals(FunctionsTab.INTEGRATED_PLUGINS.length + " plugins work with Best Path", quest.summary.getText());
		// a click on the card opens its page, with the function in it
		for (java.awt.event.MouseListener l : quest.getMouseListeners())
		{
			l.mouseClicked(null);
		}
		assertEquals(FunctionsTab.INTEGRATED, t.shownPage);
		JPanel page = t.pages.get(FunctionsTab.INTEGRATED);
		assertTrue(buttons(page).contains(t.questButton));
		assertEquals("Quest Helper", FunctionsTab.INTEGRATED_PLUGINS[0][0]);
		// Back: the select screen again
		JButton back = buttons(page).get(0);
		assertEquals("< Back", back.getText());
		back.doClick();
		assertNull(t.shownPage);
		t.showQuestStep(RouteRequest.places(QuestHelperPath.KEY, QuestHelperPath.TITLE, List.of(),
			new int[]{Tiles.pack(3208, 3220, 2)}), true);
		assertEquals("Last place sent: 3208, 3220, 2", quest.summary.getText());
		t.cards.get(4).open.run();
		assertEquals("Slayer task", t.shownPage);
		t.openPage("No such function");
		assertEquals("Slayer task", t.shownPage);
	}

	/** The player can save 10 different custom locations and name them. */
	@Test
	public void savedLocationsAreNamedAndSaved()
	{
		FunctionsTab t = tab();
		SavedSection s = t.saved;
		FunctionCard card = t.cards.get(0);
		assertEquals("None saved yet", card.summary.getText());
		// empty tile, logged out: nothing saved
		s.name.setText("Home");
		s.save.doClick();
		assertEquals(SavedSection.NOT_LOGGED_IN, s.message.getText());
		// empty tile: where the player stands
		playerTile = Tiles.pack(3222, 3218, 0);
		s.save.doClick();
		assertEquals("Saved 3222, 3218, 0.", s.message.getText());
		assertEquals("", s.name.getText());
		// a typed tile; a bad one says why
		s.name.setText("Farm");
		s.tile.setText("3000 nope");
		s.save.doClick();
		assertEquals("Not a whole number: nope", s.message.getText());
		s.tile.setText("3052, 3305, 0");
		s.save.doClick();
		assertEquals(List.of("save Home 3222,3218,0", "save Farm 3052,3305,0"), calls);
		// the plugin shows the list: a button per place, x to remove
		SavedLocations saved = SavedLocations.NONE.with("Home", Tiles.pack(3222, 3218, 0)).with("Farm", Tiles.pack(3052, 3305, 0));
		t.showSaved(saved);
		assertEquals("2 of 10 saved", card.summary.getText());
		List<JButton> b = buttons(t.pages.get("Saved locations"));
		assertEquals("< Back", b.get(0).getText());
		assertEquals("Home", b.get(1).getText());
		b.get(3).doClick(); // Farm
		b.get(4).doClick(); // its x
		assertEquals("route Farm", calls.get(2));
		assertEquals("unsave 1", calls.get(3));
		assertEquals(1, routeTabShown);
		// full: a new tile is refused, a saved one can be renamed
		SavedLocations full = SavedLocations.NONE;
		for (int i = 0; i < SavedLocations.MAX; i++)
		{
			full = full.with("P" + i, Tiles.pack(3200 + i, 3200, 0));
		}
		t.showSaved(full);
		s.tile.setText("3100, 3100, 0");
		s.save.doClick();
		assertEquals(SavedSection.FULL, s.message.getText());
		s.name.setText("Renamed");
		s.tile.setText("3200, 3200, 0");
		s.save.doClick();
		assertEquals("Renamed 3200, 3200, 0.", s.message.getText());
		assertEquals("save Renamed 3200,3200,0", calls.get(calls.size() - 1));
	}

	private static List<JButton> buttons(Container c)
	{
		List<JButton> l = new ArrayList<>();
		for (Component k : c.getComponents())
		{
			if (k instanceof JButton && !((JButton) k).getText().isEmpty()) // not the scrollbar's arrows
			{
				l.add((JButton) k);
			}
			else if (k instanceof Container)
			{
				l.addAll(buttons((Container) k));
			}
		}
		return l;
	}
}

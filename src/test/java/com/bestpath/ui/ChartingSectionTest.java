package com.bestpath.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bestpath.route.ChartingProgress;
import com.bestpath.route.ChartingProgressTest;
import com.bestpath.route.RouteRequest;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import org.junit.Test;

/** The Functions tab's sea charting part: sets, filter, rewards, routes. */
public class ChartingSectionTest
{
	private final List<RouteRequest> routes = new ArrayList<>();
	private int routeTabShown;

	private ChartingSection section()
	{
		return new ChartingSection(new PanelActions()
		{
			@Override
			public void findRoute(RouteRequest request)
			{
				routes.add(request);
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
			public void settingsChanged(com.bestpath.route.TransportSettings settings)
			{
			}
		}, () -> routeTabShown++);
	}

	/** Sailing 10, the board done: of the Bay of Sarim only the wreck can be done now. */
	private static ChartingProgress progress()
	{
		return ChartingProgress.read(ChartingProgressTest.tasks(), v -> v == ChartingProgressTest.CA ? 45 : v == 18574 ? 1 : 0, 10,
			null, null, ChartingProgress.ANY_BOAT);
	}

	/** At this Sailing level, these tasks done (varbits). */
	private static ChartingProgress progress(int sailing, java.util.Set<Integer> done)
	{
		return ChartingProgress.read(ChartingProgressTest.tasks(), v -> v == ChartingProgressTest.CA ? 45 : done.contains(v) ? 1 : 0,
			sailing, null, null, ChartingProgress.ANY_BOAT);
	}

	@Test
	public void withoutDataOrLoginNothingRoutes()
	{
		ChartingSection s = section();
		assertEquals(ChartingSection.NO_DATA, s.status.getText());
		assertFalse(s.chart.isEnabled());
		s.show(ChartingProgress.loggedOut(ChartingProgressTest.tasks()));
		assertEquals(ChartingSection.LOGGED_OUT, s.status.getText());
		assertFalse(s.chart.isEnabled());
		assertFalse(s.onlyCanDo.isEnabled());
		assertFalse(s.fullSeas.isEnabled());
		assertFalse(s.fullOceans.isEnabled());
		assertFalse(s.everything.isEnabled());
		assertEquals(ChartingSection.LOGGED_OUT, s.everything.getToolTipText());
	}

	/** The top dropdown has only oceans (and the "everything" and "all oceans" choices). */
	@Test
	public void theListHasOnlyOceans()
	{
		ChartingSection s = section();
		s.show(progress());
		List<String> sets = new ArrayList<>();
		for (int i = 0; i < s.setChoice.getItemCount(); i++)
		{
			sets.add(s.setChoice.getItemAt(i));
		}
		// the "all oceans" choice stays in the dropdown
		assertEquals(List.of(ChartingProgress.EVERY_SEA, ChartingSection.EVERYTHING, "Ardent Ocean", "Miscellaneous"), sets);
		assertEquals("All oceans", s.label(ChartingProgress.EVERY_SEA));
		assertEquals("Ardent Ocean", s.label("Ardent Ocean"));
		assertEquals("Miscellaneous (bonus sets)", s.label("Miscellaneous"));
		assertEquals("Charted 1 of 5 tasks. Sailing level 10.", s.status.getText());
		assertEquals("1 of 5 done. 1 of the 4 tasks left can be done now: 35 XP.", s.rewards.getText());
		assertEquals("Chart all oceans", s.chart.getText());
		s.setChoice.setSelectedItem("Ardent Ocean");
		assertEquals("1 of 4 done. 1 of the 3 tasks left can be done now: 35 XP. The ocean can't be completed yet.",
			s.rewards.getText());
	}

	@Test
	public void chartThisOceanRoutesToItsTasksLeft()
	{
		ChartingSection s = section();
		s.show(progress());
		s.setChoice.setSelectedItem("Ardent Ocean");
		assertEquals("Chart this ocean", s.chart.getText());
		assertTrue(s.chart.isEnabled());
		s.chart.doClick();
		assertEquals(1, routes.size());
		assertArrayEquals(new int[]{ChartingProgressTest.WRECK}, routes.get(0).getTargets());
		assertEquals("Ardent Ocean", ChartingProgress.setOf(routes.get(0)));
		assertEquals(1, routeTabShown);
		s.setChoice.setSelectedItem("Miscellaneous");
		assertFalse("the duck needs Sailing 22", s.chart.isEnabled());
		assertTrue(texts(s.list).contains(ChartingSection.NOTHING_TO_DO));
	}

	/** Each category has its own "Go" button for that section; individual tasks have none. */
	@Test
	public void eachSeaHasItsOwnGoAndTasksHaveNone()
	{
		ChartingSection s = section();
		s.show(progress());
		s.setChoice.setSelectedItem("Ardent Ocean");
		assertTrue(texts(s.list).contains("Bay of Sarim"));
		assertTrue(texts(s.list).contains("1 / 3"));
		List<JButton> go = goButtons(s.list);
		assertEquals("Kharidian Sea and Bay of Sarim", 2, go.size());
		assertFalse("Kharidian Sea: its only task has no place", go.get(0).isEnabled());
		assertTrue(go.get(1).isEnabled());
		s.toggle("Bay of Sarim");
		assertTrue(texts(s.list).contains("Done"));
		assertTrue(texts(s.list).contains("Find the wreck of the Grandyozenaym."));
		assertEquals("no Go on the tasks", 2, goButtons(s.list).size());
		goButtons(s.list).get(1).doClick();
		assertArrayEquals(new int[]{ChartingProgressTest.WRECK}, routes.get(0).getTargets());
		assertEquals("Bay of Sarim", ChartingProgress.setOf(routes.get(0)));
		s.show(progress());
		assertTrue("kept open while the progress changes", texts(s.list).contains("Find the wreck"));
	}

	@Test
	public void theFilterLeavesWhatCanBeDone()
	{
		// "only what I can do now" hides seas and oceans that can only be partially finished
		ChartingSection s = section();
		s.show(progress());
		s.setChoice.setSelectedItem("Ardent Ocean");
		s.onlyCanDo.doClick();
		assertFalse("nothing in it can be done now", texts(s.list).contains("Kharidian Sea"));
		assertFalse("only partly: the rock needs Sailing 24", texts(s.list).contains("Bay of Sarim"));
		s.show(progress(30, java.util.Set.of(18574)));
		assertTrue("Sailing 30: it can be finished", texts(s.list).contains("Bay of Sarim"));
		assertFalse(texts(s.list).contains("Kharidian Sea"));
		s.setChoice.setSelectedItem(ChartingProgress.EVERY_SEA);
		assertFalse("the Ardent Ocean only partly", texts(s.list).contains("Ardent Ocean"));
		assertTrue(texts(s.list).contains("Miscellaneous"));
	}

	/**
	 * A button routes to complete every full sea the player can, another every ocean, and one completes the entire sea
	 * if the player has the requirements.
	 */
	@Test
	public void theBigButtonsChartWhatCanBeCompleted()
	{
		ChartingSection s = section();
		s.show(progress());
		assertEquals("Chart every sea I can complete (0)", s.fullSeas.getText());
		assertFalse(s.fullSeas.isEnabled());
		assertFalse(s.fullOceans.isEnabled());
		assertFalse(s.everything.isEnabled());
		assertTrue(s.everything.getToolTipText().startsWith("Not yet: "));
		// Sailing 30: the Bay of Sarim and the bonus currents can be completed, so can the Miscellaneous ocean
		s.show(progress(30, java.util.Set.of(18574)));
		assertEquals("Chart every sea I can complete (2)", s.fullSeas.getText());
		assertEquals("Chart every ocean I can complete (1)", s.fullOceans.getText());
		assertTrue(s.fullSeas.getToolTipText().startsWith(
			"2 seas: 3 tasks left, 195 XP, and 700 XP for completing them."));
		s.fullSeas.doClick();
		assertEquals(ChartingProgress.FULL_SEAS, ChartingProgress.setOf(routes.get(0)));
		assertArrayEquals(sorted(new int[]{ChartingProgressTest.DUCK, ChartingProgressTest.ROCK, ChartingProgressTest.WRECK}),
			sorted(routes.get(0).getTargets()));
		s.fullOceans.doClick();
		assertArrayEquals(new int[]{ChartingProgressTest.DUCK}, routes.get(1).getTargets());
		assertFalse("the Kharidian Sea's task has no place", s.everything.isEnabled());
		// with that one done everything left can be done
		s.show(progress(30, java.util.Set.of(18574, 18587)));
		assertTrue(s.everything.isEnabled());
		s.everything.doClick();
		assertEquals(ChartingProgress.EVERY_SEA, ChartingProgress.setOf(routes.get(2)));
	}

	/** An "everything" category shows everything in the sailing section. */
	@Test
	public void everythingShowsEverySeaUnderItsOcean()
	{
		ChartingSection s = section();
		s.show(progress());
		s.setChoice.setSelectedItem(ChartingSection.EVERYTHING);
		assertEquals("Everything", s.label(ChartingSection.EVERYTHING));
		String list = texts(s.list);
		for (String name : List.of("Ardent Ocean", "Kharidian Sea", "Bay of Sarim", "Miscellaneous (bonus sets)",
			"Bonus Currents (bonus)"))
		{
			assertTrue(name, list.contains(name));
		}
		assertTrue("the oceans' names come before their seas", list.indexOf("Ardent Ocean") < list.indexOf("Kharidian Sea")
			&& list.indexOf("Bay of Sarim") < list.indexOf("Miscellaneous (bonus sets)"));
		assertEquals("a Go per sea and bonus set", 3, goButtons(s.list).size());
		assertEquals("Chart all oceans", s.chart.getText());
		s.chart.doClick();
		assertEquals(ChartingProgress.EVERY_SEA, ChartingProgress.setOf(routes.get(0)));
		s.toggle("Bay of Sarim");
		assertTrue(texts(s.list).contains("Find the wreck"));
	}

	/** All oceans: a row per ocean, each with its Go; a click on one picks it. */
	@Test
	public void allOceansListsTheOceans()
	{
		ChartingSection s = section();
		s.show(progress());
		assertTrue(texts(s.list).contains("Ardent Ocean"));
		assertTrue(texts(s.list).contains("Miscellaneous"));
		assertEquals(2, goButtons(s.list).size());
		clickRow(s.list, "Ardent Ocean");
		assertEquals("Ardent Ocean", s.setChoice.getSelectedItem());
	}

	/** Items that cannot be completed show red; a "hide completed" button trims the sailing list. */
	@Test
	public void redForWhatCantBeCompletedAndHideCompleted()
	{
		ChartingSection s = section();
		s.show(progress());
		s.setChoice.setSelectedItem("Ardent Ocean");
		assertEquals("its only task has no place", Ui.BAD, label(s.list, "Kharidian Sea").getForeground());
		assertEquals("the rock needs Sailing 24", Ui.BAD, label(s.list, "Bay of Sarim").getForeground());
		s.show(progress(30, java.util.Set.of(18574)));
		assertEquals(Ui.TITLE, label(s.list, "Bay of Sarim").getForeground());
		s.toggle("Bay of Sarim");
		assertTrue(texts(s.list).contains("Done"));
		s.hideDone.doClick();
		assertFalse("the board is done", texts(s.list).contains("Done"));
		assertTrue(texts(s.list).contains("Find the wreck"));
		s.show(progress(30, java.util.Set.of(18574, 18578, 18579)));
		assertFalse("a completed sea is hidden", texts(s.list).contains("Bay of Sarim"));
		s.hideDone.doClick();
		assertEquals(Ui.GOOD, label(s.list, "Bay of Sarim").getForeground());
	}

	private static javax.swing.JLabel label(Container c, String text)
	{
		for (Component k : c.getComponents())
		{
			if (k instanceof javax.swing.JLabel && text.equals(((javax.swing.JLabel) k).getText()))
			{
				return (javax.swing.JLabel) k;
			}
			if (k instanceof Container)
			{
				javax.swing.JLabel l = label((Container) k, text);
				if (l != null)
				{
					return l;
				}
			}
		}
		return null;
	}

	private static void clickRow(Container c, String text)
	{
		Component row = label(c, text).getParent();
		for (java.awt.event.MouseListener l : row.getMouseListeners())
		{
			l.mouseClicked(null);
		}
	}

	private static int[] sorted(int[] a)
	{
		int[] c = a.clone();
		java.util.Arrays.sort(c);
		return c;
	}

	private static List<JButton> goButtons(Container c)
	{
		List<JButton> out = new ArrayList<>();
		for (JButton b : buttons(c))
		{
			if (b.getText().equals("Go"))
			{
				out.add(b);
			}
		}
		return out;
	}

	static List<JButton> buttons(Container c)
	{
		List<JButton> out = new ArrayList<>();
		for (Component k : c.getComponents())
		{
			if (k instanceof JButton)
			{
				out.add((JButton) k);
			}
			if (k instanceof Container)
			{
				out.addAll(buttons((Container) k));
			}
		}
		return out;
	}

	static String texts(Container c)
	{
		StringBuilder sb = new StringBuilder();
		for (Component k : c.getComponents())
		{
			if (k instanceof javax.swing.JLabel)
			{
				sb.append(((javax.swing.JLabel) k).getText()).append('\n');
			}
			if (k instanceof WrapLabel)
			{
				sb.append(((WrapLabel) k).getText()).append('\n');
			}
			if (k instanceof Container)
			{
				sb.append(texts((Container) k));
			}
		}
		return sb.toString();
	}

	/** The player is told to drink the drinks they have, only on the page (it does not get in the way of routes). */
	@Test
	public void heldDrinksAreListedNotRouted()
	{
		ChartingSection s = section();
		s.show(ChartingProgress.read(new com.bestpath.core.data.ChartingTasks(List.of(
			new com.bestpath.core.data.ChartingTasks.Set("Ardent Ocean", com.bestpath.core.data.ChartingTasks.OCEAN, "", 5000),
			new com.bestpath.core.data.ChartingTasks.Set("Bay of Sarim", com.bestpath.core.data.ChartingTasks.SEA, "Ardent Ocean", 190)),
			List.of(new com.bestpath.core.data.ChartingTasks.Task(38, 18612, "crate", "Bay of Sarim", "", 12, 75, "", "", false,
				new int[]{ChartingProgressTest.WRECK}, "Find a sealed crate.", "", "Crowbar", new int[]{31807}, 31831, "Smuggled rum"))),
			v -> v == ChartingProgressTest.CA ? 45 : 0, 20, id -> false, id -> id == 31831, ChartingProgress.ANY_BOAT));
		assertEquals("Drink when you like: Smuggled rum (in your bank).", s.drinks.getText());
		s.setChoice.setSelectedItem("Ardent Ocean");
		s.toggle("Bay of Sarim");
		assertTrue(texts(s.list).contains("Drink it"));
		assertFalse("nothing to route to", s.chart.isEnabled());
		assertEquals("not red: it only needs drinking", Ui.TITLE, label(s.list, "Bay of Sarim").getForeground());
		s.show(progress());
		assertEquals("", s.drinks.getText());
	}
}

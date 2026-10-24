package com.bestpath.ui;

import com.bestpath.core.data.PlaceKinds;
import com.bestpath.route.ChartingProgress;
import com.bestpath.route.RecentDestinations;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.SavedLocations;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.SlayerTask;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import com.bestpath.BestPathConfig;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * The Functions tab: one-click destinations. It opens on the function select screen, one titled card per function
 * ({@link FunctionCard}); a card opens that function's own page, with a Back button to the select screen. The
 * functions: saved locations, recent destinations, the nearest place of a kind, integrated plugins (with the last place
 * another plugin sent), the Slayer task and sea charting. A click on a destination routes there and shows the Route tab
 * (the page stays open). Swing thread only.
 */
final class FunctionsTab extends JPanel
{
	static final String NO_QUEST_STEP = "No plugin has sent a place yet.";
	/** The card's title on the select screen and its page's title. */
	static final String INTEGRATED = "Integrated plugins";
	/**
	 * The plugins that work with Best Path, each with what it does here and what to switch on (see
	 * {@link com.bestpath.route.OtherPathPlugins} for the ones that get in the way). Above them on the page: the
	 * "Routes from other plugins" switch and the last place sent.
	 */
	static final String[][] INTEGRATED_PLUGINS = {
		{"Quest Helper", "Each quest step. Turn on its \"Use 'Shortest Path' plugin\"."},
		{"Bank Tags (RuneLite)", "A bank button shows the items your route needs."},
		{"Hunter Rumours", "Your rumour's hunting spot. Turn on its \"Use 'Shortest Path' Plugin\"."},
		{"Tasks Tracker", "Each task of a task route. Turn on its \"Use Shortest Path\"."},
		{"Watchdog", "Alerts with a Shortest Path action."},
		{"Shortest Grave", "After a death, the way back to your grave."},
	};
	static final String NO_RECENT = "No routes yet. The places you route to show here (the last "
		+ RecentDestinations.MAX + ").";

	private final PanelActions actions;
	private final Runnable showRoute;
	final WrapLabel questText = new WrapLabel("", Ui.CONTENT_WIDTH, Ui.small(), Ui.TEXT);
	final JButton questButton = Ui.button("Go to the last place sent", "Route to the last place another plugin sent");
	/** The config setting "Routes from other plugins", also shown in the config panel and the Settings tab. */
	final JCheckBox followBox = Ui.checkBox(ConfigList.PLUGIN_ROUTES);
	private final JPanel recentList = new JPanel();
	private final ChartingSection charting;
	final NearestSection nearest;
	private final SlayerSection slayer;
	final SavedSection saved;
	/** The cards on the select screen, top to bottom. */
	final List<FunctionCard> cards = new ArrayList<>();
	/** Each function's page, by title. */
	final Map<String, JPanel> pages = new LinkedHashMap<>();
	/** The select screen. */
	private final JPanel menu = new ScrollablePanel();
	private final JScrollPane scroll;
	/** The page shown, or null for the select screen. */
	String shownPage;
	private final FunctionCard savedCard;
	private final FunctionCard recentCard;
	private final FunctionCard nearestCard;
	private final FunctionCard integratedCard;
	private final FunctionCard slayerCard;
	private final FunctionCard chartingCard;

	/** {@code showRoute}: shows the Route tab (after a click starts a route); {@code icons}: the sidebar's pictures. */
	FunctionsTab(PanelActions actions, Runnable showRoute, IconCache icons)
	{
		this.actions = actions;
		this.showRoute = showRoute;
		setLayout(new BorderLayout());
		setBackground(Ui.BACKGROUND);

		JPanel body = menu;
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(Ui.BACKGROUND);
		body.setBorder(Ui.padding(Ui.PAD, Ui.PAD, Ui.PAD, Ui.PAD));

		saved = new SavedSection(actions, showRoute);
		savedCard = card(body, "Saved locations", saved);

		recentList.setLayout(new BoxLayout(recentList, BoxLayout.Y_AXIS));
		recentList.setOpaque(false);
		recentCard = card(body, "Recent destinations", recentList);

		// go to the nearest place of a kind
		nearest = new NearestSection(actions, showRoute, icons);
		nearestCard = card(body, "Nearest", nearest);
		nearestCard.setSummary(NearestSection.LOADING);

		JPanel integrated = new JPanel();
		integrated.setLayout(new BoxLayout(integrated, BoxLayout.Y_AXIS));
		integrated.setOpaque(false);
		questButton.addActionListener(e ->
		{
			actions.useQuestStep();
			showRoute.run();
		});
		followBox.setToolTipText(ConfigList.PLUGIN_ROUTES_TIP);
		followBox.addActionListener(e -> actions.setConfig(BestPathConfig.PLUGIN_ROUTES_KEY,
			String.valueOf(followBox.isSelected())));
		// the switch and the last place sent, then the plugins
		Ui.addRow(integrated, followBox);
		Ui.addRow(integrated, Box.createVerticalStrut(4));
		Ui.addRow(integrated, questText);
		Ui.addRow(integrated, Box.createVerticalStrut(4));
		Ui.addRow(integrated, questButton);
		for (String[] plugin : INTEGRATED_PLUGINS)
		{
			Ui.addRow(integrated, Box.createVerticalStrut(8));
			Ui.addRow(integrated, Ui.wrapped(plugin[0], Ui.CONTENT_WIDTH, Ui.bold(), Ui.TITLE));
			Ui.addRow(integrated, Ui.wrapped(plugin[1], Ui.CONTENT_WIDTH, Ui.small(), Ui.TEXT));
		}
		integratedCard = card(body, INTEGRATED, integrated);

		slayer = new SlayerSection(actions, showRoute, icons);
		slayerCard = card(body, "Slayer task", slayer);

		charting = new ChartingSection(actions, showRoute);
		chartingCard = card(body, "Sea charting", charting);

		scroll = Ui.verticalScroll(body);
		add(scroll, BorderLayout.CENTER);

		showQuestStep(null, true);
		showRecent(RecentDestinations.NONE);
		showSaved(SavedLocations.NONE);
		showSlayer(SlayerTask.LOGGED_OUT);
		showCharting(ChartingProgress.NONE);
	}

	/**
	 * A function: its card on the select screen (a gap above every card but the first) and its own page holding
	 * {@code content}.
	 */
	private FunctionCard card(JPanel body, String title, JComponent content)
	{
		if (!cards.isEmpty())
		{
			Ui.addRow(body, Box.createVerticalStrut(6));
		}
		FunctionCard c = new FunctionCard(title, () -> openPage(title));
		cards.add(c);
		Ui.addRow(body, c);
		pages.put(title, page(title, content));
		return c;
	}

	/** A function's page: Back and the title at the top, then the function. */
	private JPanel page(String title, JComponent content)
	{
		JPanel p = new ScrollablePanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(Ui.BACKGROUND);
		p.setBorder(Ui.padding(Ui.PAD, Ui.PAD, Ui.PAD, Ui.PAD));
		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setOpaque(false);
		JButton back = Ui.button("< Back", "Back to the functions");
		back.addActionListener(e -> showMenu());
		top.add(back, BorderLayout.WEST);
		JLabel name = Ui.label(title, Ui.bold(), Ui.TITLE);
		top.add(name, BorderLayout.CENTER);
		top.setMaximumSize(new Dimension(Integer.MAX_VALUE, top.getPreferredSize().height));
		Ui.addRow(p, top);
		Ui.addRow(p, Box.createVerticalStrut(8));
		Ui.addRow(p, content);
		return p;
	}

	/** Shows the function's page (its title as on its card). */
	void openPage(String title)
	{
		JPanel p = pages.get(title);
		if (p == null)
		{
			return;
		}
		shownPage = title;
		show(p);
	}

	/** Back to the function select screen. */
	void showMenu()
	{
		shownPage = null;
		show(menu);
	}

	private void show(JPanel view)
	{
		scroll.setViewportView(view);
		scroll.getVerticalScrollBar().setValue(0);
		revalidate();
		repaint();
	}

	/** The player's saved locations. */
	void showSaved(SavedLocations s)
	{
		saved.show(s);
		savedCard.setSummary(saved.summary());
	}

	/**
	 * The last place another plugin sent, such as Quest Helper's step (null: none sent, or it ended), and whether
	 * routes follow each new one (the "Routes from other plugins" setting; the switch here shows it).
	 */
	void showQuestStep(RouteRequest step, boolean following)
	{
		questButton.setEnabled(step != null);
		followBox.setSelected(following);
		questText.setText(step == null ? NO_QUEST_STEP : "Last place sent: " + where(step) + ".");
		integratedCard.setSummary(step == null ? INTEGRATED_PLUGINS.length + " plugins work with Best Path"
			: "Last place sent: " + where(step));
		revalidate();
		repaint();
	}

	void showRecent(RecentDestinations d)
	{
		recentList.removeAll();
		List<RouteRequest> places = d.list();
		if (places.isEmpty())
		{
			Ui.addRow(recentList, Ui.wrapped(NO_RECENT, Ui.CONTENT_WIDTH, Ui.small(), Ui.MUTED));
		}
		for (int i = 0; i < places.size(); i++)
		{
			RouteRequest r = places.get(i);
			int index = i;
			JButton go = Ui.button(label(r), "Route to " + label(r));
			go.addActionListener(e ->
			{
				actions.findRoute(r);
				showRoute.run();
			});
			JButton remove = Ui.button("x", "Take it off the list");
			remove.addActionListener(e -> actions.removeRecent(index));
			JPanel line = Ui.buttonLine(go, remove);
			if (i > 0)
			{
				Ui.addRow(recentList, Box.createVerticalStrut(3));
			}
			Ui.addRow(recentList, line);
		}
		if (!places.isEmpty())
		{
			Ui.addRow(recentList, Box.createVerticalStrut(4));
			JButton clear = Ui.button("Clear the list", "Forget every recent destination");
			clear.addActionListener(e -> actions.clearRecent());
			Ui.addRow(recentList, clear);
		}
		recentCard.setSummary(places.isEmpty() ? "None yet" : places.size() + (places.size() == 1 ? " place" : " places"));
		revalidate();
		repaint();
	}

	/** The place kinds for "Nearest" (the banks in); {@code loaded} false while the map data loads. */
	void showPlaces(PlaceKinds kinds, boolean loaded)
	{
		nearest.showKinds(kinds, loaded);
		int n = kinds == null ? 0 : kinds.getKinds().size();
		nearestCard.setSummary(!loaded ? NearestSection.LOADING : n == 0 ? NearestSection.NO_PLACES
			: "The nearest of " + n + " kinds of place (bank, altar, anvil, ...)");
	}

	/** The player's sea charting (done tasks, Sailing level). */
	void showCharting(ChartingProgress p)
	{
		charting.show(p);
		chartingCard.setSummary(charting.summary());
	}

	/** "3222, 3218, 0", "3222, 3218 (world map)", "Plugin route (3222, 3218, 0)". */
	static String label(RouteRequest r)
	{
		if (!r.hasTitle())
		{
			return r.isPlaneFromMap() ? r.label() + " (world map)" : r.label();
		}
		return r.targetCount() == 1 ? r.getTitle() + " (" + where(r) + ")" : r.getTitle();
	}

	private static String where(RouteRequest r)
	{
		if (r.targetCount() > 1)
		{
			return "the nearest of " + r.targetCount() + " places";
		}
		int t = r.getTargets()[0];
		return RouteRequest.tileLabel(Tiles.x(t), Tiles.y(t), Tiles.plane(t));
	}

	/** The player's Slayer task and its places. */
	void showSlayer(SlayerTask task)
	{
		slayer.show(task);
		slayerCard.setSummary(slayer.summary());
	}
}

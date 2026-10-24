package com.bestpath.dev;

import com.bestpath.route.LoadingEngine;
import com.bestpath.route.NoMapDataEngine;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.RouteEngine;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteJob;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.TransportSettings;
import com.bestpath.ui.PanelDriver;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * DEV ONLY ("stripped RuneLite"): opens just the Best Path sidebar in a
 * window, with RuneLite's look and feel, colours and fonts, a fake player and
 * a fake route engine. No game client, no login, no plugin manager, so it
 * opens in about a second. Run with {@code gradlew uiPreview}
 * (see docs/PLUGIN_UI.md). The left side holds developer controls; the right
 * side is the real sidebar panel at RuneLite's width.
 *
 * <p>{@code gradlew uiPreview -PrealData}: routes are searched for real on
 * the exported map data (gradlew -p tool exportPlugin) with the plugin's own
 * loader and engine, for the Data Tool's test players.
 */
public final class UiPreview
{
	/** Fake search time, so "Calculating..." can be seen. */
	private static final long FAKE_SEARCH_MILLIS = 400;
	/** "Step off" moves the fake player this far: past the default re-route distance. */
	private static final int STEP_OFF_TILES = com.bestpath.route.RouteProgress.DEFAULT_STRAY_DISTANCE + 5;

	private UiPreview()
	{
	}

	public static void main(String[] args)
	{
		boolean exitAfterShow = false;
		// --shot <file.png>: show the window, find a route, save a picture of the window's inside, then exit
		File shot = null;
		// --real-data: the real exported map data and the plugin's engine instead of fake routes
		boolean real = false;
		for (int i = 0; i < args.length; i++)
		{
			if ("--exit-after-show".equals(args[i]))
			{
				exitAfterShow = true;
			}
			else if ("--shot".equals(args[i]) && i + 1 < args.length)
			{
				shot = new File(args[++i]);
			}
			else if ("--real-data".equals(args[i]))
			{
				real = true;
			}
		}
		boolean exit = exitAfterShow;
		File file = shot;
		boolean useReal = real;
		SwingUtilities.invokeLater(() -> open(exit, file, useReal));
	}

	private static void open(boolean exitAfterShow, File shot, boolean real)
	{
		PreviewHarness.setupLookAndFeel();
		Map<String, PlayerSnapshot> players = real ? RealData.players() : FakeRoutes.players();
		LoadingEngine loading = real ? new LoadingEngine(RealData::engine) : null;
		RouteEngine engine = real ? loading : FakeRoutes.engine(FAKE_SEARCH_MILLIS);
		PreviewHarness h = new PreviewHarness(players.values().iterator().next(), engine, false);
		if (loading != null)
		{
			h.service.runOnWorker(loading::load); // as the plugin: the map data loads on the route worker
		}

		JFrame frame = new JFrame(real ? "Best Path - UI preview (REAL map data, test players, dev only)"
			: "Best Path - UI preview (fake data, dev only)");
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		JPanel root = new JPanel(new BorderLayout());
		root.add(controls(h, players, real), BorderLayout.CENTER);
		JPanel side = h.panel;
		side.setPreferredSize(new Dimension(UiShot.WIDTH, UiShot.HEIGHT));
		JPanel sideBox = new JPanel(new BorderLayout());
		sideBox.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, ColorScheme.BORDER_COLOR));
		sideBox.add(side, BorderLayout.CENTER);
		root.add(sideBox, BorderLayout.EAST);
		frame.setContentPane(root);
		frame.pack();
		frame.setLocationByPlatform(true);
		frame.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowOpened(WindowEvent e)
			{
				System.out.printf("UI preview window shown %d ms after the JVM started%n",
					ManagementFactory.getRuntimeMXBean().getUptime());
				if (exitAfterShow)
				{
					SwingUtilities.invokeLater(() -> System.exit(0));
				}
				if (shot != null)
				{
					h.findRoute(real ? RealData.DESTINATION : FakeRoutes.DESTINATION);
					// save once the search is done (the real data first loads, about a second), at most 30 s
					long until = System.currentTimeMillis() + 30_000;
					Timer t = new Timer(200, null);
					t.addActionListener(ev ->
					{
						boolean busy = h.service.getCurrent().getStatus() == RouteStatus.CALCULATING;
						if (!busy || System.currentTimeMillis() > until)
						{
							t.stop();
							// one more moment for the sidebar's invokeLater update
							Timer later = new Timer(300, ev2 -> saveAndExit(root, shot));
							later.setRepeats(false);
							later.start();
						}
					});
					t.setInitialDelay((int) FAKE_SEARCH_MILLIS);
					t.start();
				}
			}
		});
		frame.setVisible(true);
	}

	private static void saveAndExit(JComponent c, File file)
	{
		BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		c.paint(g);
		g.dispose();
		try
		{
			ImageIO.write(img, "png", file);
			System.out.println("Saved " + file.getAbsolutePath());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		System.exit(0);
	}

	private static JComponent controls(PreviewHarness h, Map<String, PlayerSnapshot> players, boolean real)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		p.setPreferredSize(new Dimension(250, UiShot.HEIGHT));

		add(p, heading("Developer controls"));
		add(p, note(real
			? "This stands in for the game screen. The sidebar on the right is the real panel and routes are REAL "
			+ "(the exported map data, the plugin's own search). Type a tile there and press Go."
			: "This stands in for the game screen. The sidebar on the right is the real panel; routes are "
			+ "fake. Type a tile there and press Go (plane 3 gives \"No route\")."));
		add(p, Box.createVerticalStrut(10));

		add(p, heading(real ? "Test player" : "Fake player"));
		JComboBox<String> who = new JComboBox<>(players.keySet().toArray(new String[0]));
		add(p, who);
		add(p, note(real ? "Changing the player searches again (requirements: levels, items, quests...)."
			: "Changing the player searches again (no Magic 25: no teleport; no Agility 51: no pipe)."));
		add(p, Box.createVerticalStrut(6));
		add(p, heading("Player stands at"));
		PlayerSnapshot first = players.values().iterator().next();
		JTextField at = new JTextField(first.getX() + ", " + first.getY() + ", " + first.getPlane());
		JLabel atProblem = note(" ");
		Runnable apply = () ->
		{
			try
			{
				RouteRequest t = RouteRequest.parse(at.getText());
				PlayerSnapshot chosen = players.get((String) who.getSelectedItem());
				h.setPlayer(new PlayerSnapshot(t.getX(), t.getY(), t.getPlane(), chosen.getState(), chosen.getBoats()));
				atProblem.setText(" ");
			}
			catch (IllegalArgumentException ex)
			{
				atProblem.setText(ex.getMessage());
			}
		};
		at.addActionListener(e -> apply.run());
		who.addActionListener(e -> apply.run());
		add(p, at);
		add(p, atProblem);
		add(p, Box.createVerticalStrut(10));

		add(p, heading("Show a state"));
		JPanel states = new JPanel(new GridLayout(0, 2, 4, 4));
		states.setOpaque(false);
		states.add(button("Find route", () -> h.findRoute(real ? RealData.DESTINATION : FakeRoutes.DESTINATION)));
		states.add(button("Empty", h::clearRoute));
		states.add(button("Calculating", () -> h.panel.showResult(RouteResult.status(0, FakeRoutes.DESTINATION,
			RouteStatus.CALCULATING, "Calculating..."), Set.of())));
		states.add(button("No route", () -> h.panel.showResult(FakeRoutes.noRoute(0), Set.of())));
		states.add(button("No map data", () -> h.panel.showResult(new NoMapDataEngine().route(
			new RouteJob(0, FakeRoutes.DESTINATION, players.values().iterator().next(), TransportSettings.defaults(),
				Set.of())), Set.of())));
		states.add(button("Not logged in", () -> h.panel.showResult(RouteResult.status(0, FakeRoutes.DESTINATION,
			RouteStatus.ERROR, "Log in to find a route."), Set.of())));
		states.add(button("Route tab", h.panel::selectRouteTab));
		states.add(button("Settings tab", () -> PanelDriver.selectSettingsTab(h.panel)));
		// Sailing: three made-up boats read the plugin's way (with the real boat table when there is real data)
		states.add(button("Fake boats", () ->
		{
			com.bestpath.core.data.BoatTable table = FakeBoats.SAMPLE_TABLE;
			if (real && RealData.engine() instanceof com.bestpath.route.CoreRouteEngine
				&& !((com.bestpath.route.CoreRouteEngine) RealData.engine()).getRouter().getData().getBoats().isEmpty())
			{
				table = ((com.bestpath.route.CoreRouteEngine) RealData.engine()).getRouter().getData().getBoats();
			}
			h.panel.showBoats(FakeBoats.sample(table));
			PanelDriver.selectSettingsTab(h.panel);
		}));
		states.add(button("No boats", () ->
		{
			h.panel.showBoats(com.bestpath.player.PlayerBoats.NOT_LOGGED_IN);
			PanelDriver.selectSettingsTab(h.panel);
		}));
		add(p, states);
		add(p, Box.createVerticalStrut(10));

		add(p, heading("Fake walking player"));
		add(p, note("Walks the shown route one tile per tick (fast: 0.15 s a tick); the sidebar highlights the "
			+ "current step. \"Step off\" puts the player " + STEP_OFF_TILES + " tiles away: after 3 ticks the route is"
			+ " searched again."));
		JLabel where = note("Player at 3222, 3218, 0");
		JPanel walking = new JPanel(new GridLayout(0, 2, 4, 4));
		walking.setOpaque(false);
		Walker walker = new Walker(h, where);
		walking.add(button("Walk along", walker::toggle));
		walking.add(button("Step off", walker::stepOff));
		add(p, walking);
		add(p, where);
		add(p, Box.createVerticalGlue());
		return p;
	}

	/** Drives the fake walking player with a Swing timer (dev only; the plugin uses game ticks). */
	private static final class Walker
	{
		private final PreviewHarness h;
		private final JLabel where;
		private final Timer timer;
		private java.util.List<Integer> path = java.util.List.of();
		private RouteResult walking;
		private int at;

		Walker(PreviewHarness h, JLabel where)
		{
			this.h = h;
			this.where = where;
			this.timer = new Timer(150, e -> step());
		}

		void toggle()
		{
			if (timer.isRunning())
			{
				timer.stop();
			}
			else
			{
				timer.start();
			}
		}

		void stepOff()
		{
			PlayerSnapshot p = h.getPlayer();
			// past the default re-route distance (10)
			int off = com.bestpath.core.path.Tiles.pack(p.getX(), p.getY() + STEP_OFF_TILES, p.getPlane());
			path = java.util.Collections.nCopies(4, off);
			walking = h.service.getCurrent();
			at = 0;
			if (!timer.isRunning())
			{
				timer.start();
			}
		}

		private void step()
		{
			RouteResult r = h.service.getCurrent();
			if (at >= path.size() && h.service.isSearching())
			{
				return; // wait for the new route
			}
			if (r.getStatus() != RouteStatus.READY && at >= path.size())
			{
				timer.stop(); // arrived, cleared or no route
				return;
			}
			if (r != walking && r.getStatus() == RouteStatus.READY)
			{
				walking = r; // a new route (rerouted): walk that one from its start
				path = FakeWalk.positions(r);
				at = 0;
			}
			if (at < path.size())
			{
				int tile = path.get(at++);
				h.tick(tile);
				where.setText("<html><div style='width:170px'>Player at " + FakeWalk.label(tile) + ", step "
					+ (h.follower.getProgress().getStep() + 1) + "</div></html>");
			}
			else
			{
				timer.stop();
			}
		}
	}

	private static void add(JPanel p, JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (!(c instanceof JLabel))
		{
			c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		}
		p.add(c);
	}

	private static void add(JPanel p, Component c)
	{
		p.add(c);
	}

	private static JLabel heading(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(Color.WHITE);
		return l;
	}

	private static JLabel note(String text)
	{
		JLabel l = new JLabel("<html><div style='width:170px'>" + text + "</div></html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return l;
	}

	private static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusPainted(false);
		b.addActionListener(e -> action.run());
		return b;
	}
}

package com.bestpath.tool.ui;

import com.bestpath.core.path.Route;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.sea.Boat;
import com.bestpath.tool.req.TestPlayer;
import com.bestpath.tool.req.TestPlayers;
import com.bestpath.tool.route.RouteController;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

/**
 * Bottom panel for the route check: example routes, the search options,
 * the result line and the steps. Clicking a step shows it on the main map.
 */
final class RoutePanel extends JPanel
{
	interface Jump
	{
		void jump(int x, int y, int plane);
	}

	/** Shows an area of the main map (tiles, inclusive) on a plane. */
	interface ShowArea
	{
		void show(int x0, int y0, int x1, int y1, int plane);
	}

	/** One example route from {@code data/route-examples.txt}. */
	private static final class Example
	{
		final String name;
		final int[] start;
		final int[] end;
		final boolean unconfirmed;

		Example(String name, int[] start, int[] end, boolean unconfirmed)
		{
			this.name = name;
			this.start = start;
			this.end = end;
			this.unconfirmed = unconfirmed;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private final RouteController route;
	private final ShowArea area;
	private final JLabel status = new JLabel(" ");
	private final DefaultListModel<String> model = new DefaultListModel<>();
	private final JList<String> steps = new JList<>(model);
	private final List<int[]> stepTiles = new ArrayList<>();
	private final JCheckBox unconfirmed;
	private final JComboBox<TestPlayer> player = new JComboBox<>();
	/** True while the player list is filled (its selection changes then are not the user's). */
	private boolean fillingPlayers;
	private boolean refreshing;
	/** After choosing an example: fit the map to the route once it is found. */
	private boolean fitWhenFound;

	RoutePanel(RouteController route, Jump jump, ShowArea area, File examplesFile)
	{
		super(new BorderLayout(4, 4));
		this.route = route;
		this.area = area;
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		unconfirmed = new JCheckBox("Use unconfirmed suggestions", route.isUseUnconfirmed());
		JPanel exampleRow = exampleRow(examplesFile);
		JPanel searchRow = searchRow();
		JPanel moreRow = moreRow();
		JPanel sailRow = sailRow();
		JPanel playerRow = playerRow();

		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.add(playerRow);
		rows.add(exampleRow);
		rows.add(searchRow);
		rows.add(moreRow);
		rows.add(sailRow);
		JPanel head = new JPanel(new BorderLayout());
		head.add(rows, BorderLayout.NORTH);
		status.setFont(status.getFont().deriveFont(Font.BOLD));
		status.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
		head.add(status, BorderLayout.SOUTH);
		add(head, BorderLayout.NORTH);
		steps.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		steps.addListSelectionListener(e ->
		{
			int i = steps.getSelectedIndex();
			if (!refreshing && !e.getValueIsAdjusting() && i >= 0 && i < stepTiles.size())
			{
				int[] t = stepTiles.get(i);
				jump.jump(t[0], t[1], t[2]);
			}
		});
		add(new JScrollPane(steps), BorderLayout.CENTER);
		route.addListener(this::refresh);
		refresh();
	}

	private static JPanel row()
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	/** The example routes; choosing one loads it and fits the map to it. */
	private JPanel exampleRow(File examplesFile)
	{
		JComboBox<Object> examples = new JComboBox<>();
		examples.addItem("Choose an example route ...");
		for (Example ex : readExamples(examplesFile))
		{
			examples.addItem(ex);
		}
		examples.setToolTipText("Example routes (from data/route-examples.txt): choose one to load it, then look around");
		examples.addActionListener(e ->
		{
			Object o = examples.getSelectedItem();
			if (o instanceof Example)
			{
				loadExample((Example) o);
			}
		});
		JPanel exampleRow = row();
		exampleRow.add(new JLabel("Example routes:"));
		exampleRow.add(examples);
		return exampleRow;
	}

	private void loadExample(Example ex)
	{
		if (unconfirmed.isSelected() != ex.unconfirmed)
		{
			unconfirmed.setSelected(ex.unconfirmed);
			route.setUseUnconfirmed(ex.unconfirmed);
		}
		fitWhenFound = true;
		area.show(Math.min(ex.start[0], ex.end[0]), Math.min(ex.start[1], ex.end[1]),
			Math.max(ex.start[0], ex.end[0]), Math.max(ex.start[1], ex.end[1]), ex.start[2]);
		route.setStart(ex.start[0], ex.start[1], ex.start[2]);
		route.setEnd(ex.end[0], ex.end[1], ex.end[2]);
	}

	/** The search options: unconfirmed suggestions, plane change and door costs, and Clear route. */
	private JPanel searchRow()
	{
		unconfirmed.setToolTipText("<html>Off: only transports you confirmed (and pending ones).<br>"
			+ "On: also every undecided candidate's best suggestion, at default ticks; they are marked \"unconfirmed\".</html>");
		unconfirmed.addActionListener(e -> route.setUseUnconfirmed(unconfirmed.isSelected()));
		JSpinner penalty = new JSpinner(new SpinnerNumberModel(route.getPlanePenalty(), 0, 100, 1));
		penalty.setToolTipText("Extra ticks for any transport that changes plane");
		penalty.addChangeListener(e -> route.setPlanePenalty((Integer) penalty.getValue()));
		JSpinner doorCost = new JSpinner(new SpinnerNumberModel(route.getDoorCost(), 0, 100, 1));
		doorCost.setToolTipText("<html>Extra ticks for each step through a door or gate, so routes go round the simple way<br>"
			+ "unless the door really saves time.</html>");
		doorCost.addChangeListener(e -> route.setDoorCost((Integer) doorCost.getValue()));
		JButton clear = new JButton("Clear route");
		clear.addActionListener(e -> route.clear());
		JPanel searchRow = row();
		searchRow.add(new JLabel("Search: Fast (always the best route)."));
		searchRow.add(unconfirmed);
		searchRow.add(new JLabel("Plane change (ticks):"));
		searchRow.add(penalty);
		searchRow.add(new JLabel("Door/gate (ticks):"));
		searchRow.add(doorCost);
		searchRow.add(clear);
		return searchRow;
	}

	/** Where to stop near an unreachable destination, and the searched sub-regions tint. */
	private JPanel moreRow()
	{
		JSpinner near = new JSpinner(new SpinnerNumberModel(route.getNearRange(), 0, 64, 1));
		near.setToolTipText("<html>If the destination can't be reached (inside a wall, a rock...), the route stops at the nearest<br>"
			+ "reachable tile within this many tiles that has it in sight (only walls block the view);<br>"
			+ "if none has it in sight, at the nearest reachable tile in range.</html>");
		near.addChangeListener(e -> route.setNearRange((Integer) near.getValue()));
		JSpinner topY = new JSpinner(new SpinnerNumberModel(route.getMainMapTopY(), 0, 16383, 64));
		topY.setEditor(new JSpinner.NumberEditor(topY, "#"));
		topY.setToolTipText("<html>Where the main map ends (y). Above it, dungeons and other areas sit side by side,<br>"
			+ "so a stop tile for an unreachable destination there stays inside the destination's 64x64 region.</html>");
		topY.addChangeListener(e -> route.setMainMapTopY((Integer) topY.getValue()));
		JCheckBox searched = new JCheckBox("Show searched sub-regions", route.isShowSearched());
		searched.setToolTipText("<html>Tint the sub-regions the search went through for this route.<br>"
			+ "Fewer = the search was well aimed.</html>");
		searched.addActionListener(e -> route.setShowSearched(searched.isSelected()));
		JPanel moreRow = row();
		moreRow.add(new JLabel("Stop near an unreachable destination, within (tiles):"));
		moreRow.add(near);
		moreRow.add(new JLabel("Main map ends at y:"));
		moreRow.add(topY);
		moreRow.add(searched);
		return moreRow;
	}

	/** Sailing on or off, the Sailing level, shipwrights, the turn cost and the player's boats. */
	private JPanel sailRow()
	{
		JCheckBox sailing = new JCheckBox("Sailing", route.isSailingOn());
		sailing.setToolTipText("<html>Routes may sail: board one of your boats where it is moored, sail, leave it at another dock<br>"
			+ "(10 ticks each to board and leave). A destination at sea ends on the boat.</html>");
		sailing.addActionListener(e -> route.setSailingOn(sailing.isSelected()));
		JSpinner level = new JSpinner(new SpinnerNumberModel(route.getSailingLevel(), 1, 99, 1));
		level.setToolTipText("Docks above this Sailing level are not used, nor docks whose quest the test player has not finished");
		level.addChangeListener(e -> route.setSailingLevel((Integer) level.getValue()));
		JCheckBox wrights = new JCheckBox("Shipwrights may bring a boat", route.isShipwrights());
		wrights.setToolTipText("<html>At the 16 docks with a shipwright, \"Recover-boat\" brings your boat there<br>"
			+ "for coins (raft 250, skiff 3,750, sloop 50,000) and 10 ticks more.</html>");
		wrights.addActionListener(e -> route.setShipwrights(wrights.isSelected()));
		JSpinner turn = new JSpinner(new SpinnerNumberModel(route.getTurnCost() / 100.0, 0.0, 10.0, 0.25));
		turn.setToolTipText("<html>Extra ticks for every 22.5 degrees the boat turns, so sailing routes keep a straight course<br>"
			+ "(0 = turning is free). Only the choice of course: the route's ticks are the boat's real sailing time.</html>");
		turn.addChangeListener(e -> route.setTurnCost((int) Math.round(((Number) turn.getValue()).doubleValue() * 100)));
		JLabel boatsText = new JLabel();
		JButton boatsButton = new JButton("Boats ...");
		boatsButton.setToolTipText("Your boats (up to five): kind, parts and where each is moored");
		boatsButton.addActionListener(e -> editBoats());
		Runnable boatsSummary = () -> showBoats(boatsText, sailing, wrights, level);
		boatsSummary.run();
		route.addListener(boatsSummary);
		JPanel sailRow = row();
		sailRow.add(sailing);
		sailRow.add(new JLabel("Sailing level:"));
		sailRow.add(level);
		sailRow.add(wrights);
		sailRow.add(new JLabel("Turn cost (ticks per 22.5°):"));
		sailRow.add(turn);
		sailRow.add(boatsButton);
		sailRow.add(boatsText);
		return sailRow;
	}

	private void editBoats()
	{
		BoatsDialog.Result edited = BoatsDialog.edit(this, route.getBoats(), route.getBottles(), route.sailingDocks());
		if (edited != null)
		{
			route.setBottles(edited.bottles);
			route.setBoats(edited.boats);
		}
	}

	/** The boats in a line (each described in the tooltip); the sailing controls follow changes made elsewhere. */
	private void showBoats(JLabel boatsText, JCheckBox sailing, JCheckBox wrights, JSpinner level)
	{
		List<Boat> bs = route.getBoats();
		StringBuilder sb = new StringBuilder();
		StringBuilder tip = new StringBuilder("<html>");
		for (Boat b : bs)
		{
			// "Boat 1: Sloop (capsized)" for a boat at no dock
			String place = b.placeWord() == null ? "" : " (" + b.placeWord() + ")";
			sb.append(sb.length() == 0 ? "" : ", ").append(b.label()).append(place);
			tip.append(b.label()).append(place).append(": ").append(b.describe()).append("<br>");
		}
		if (route.getBottles() > 0)
		{
			sb.append(", ").append(route.getBottles()).append(route.getBottles() == 1 ? " boat bottle" : " boat bottles");
			tip.append("Full boat bottles in the inventory: ").append(route.getBottles()).append("<br>");
		}
		boatsText.setText(bs.isEmpty() ? "no boats yet" : sb.toString());
		boatsText.setToolTipText(bs.isEmpty() ? null : tip.append("</html>").toString());
		// follow changes made elsewhere (settings, checks)
		sailing.setSelected(route.isSailingOn());
		wrights.setSelected(route.isShipwrights());
		if ((Integer) level.getValue() != route.getSailingLevel())
		{
			level.setValue(route.getSailingLevel());
		}
	}

	/** The test player, the Wilderness level at the start, the bank stop and the teleport costs. */
	private JPanel playerRow()
	{
		player.setToolTipText("<html>The player the route is for: transports they can't use (levels, quests, items...) are left out.<br>"
			+ "Maxed can use everything.</html>");
		fillPlayers();
		player.addActionListener(e ->
		{
			Object o = player.getSelectedItem();
			if (!fillingPlayers && o instanceof TestPlayer && o != route.getPlayer())
			{
				route.setPlayer((TestPlayer) o);
			}
		});
		JButton players = new JButton("Players ...");
		players.setToolTipText("Make and edit test players: levels, quests, unlocks, items carried, worn and in the bank");
		players.addActionListener(e -> editPlayers());
		JSpinner wild = new JSpinner(new SpinnerNumberModel(route.getStartWilderness(), 0, 56, 1));
		wild.setToolTipText("<html>The Wilderness level where the route starts (0 = not in the Wilderness).<br>"
			+ "Teleports work up to level 20 (some members' items up to 30). Set by hand until the tile rule is known.</html>");
		wild.addChangeListener(e -> route.setStartWilderness((Integer) wild.getValue()));
		JButton costs = new JButton("Teleport and travel costs ...");
		costs.setToolTipText("The tick cost of each teleport category (spells, jewellery, tablets...) and each network's extra ticks");
		costs.addActionListener(e ->
		{
			if (TeleportCostsDialog.edit(this))
			{
				route.recompute();
			}
		});
		JSpinner bankStop = new JSpinner(new SpinnerNumberModel(route.getBankTicks(), 0, 200, 1));
		bankStop.setToolTipText("<html>Ticks a route counts for using a bank at its start (the plugin's Settings tab has the same setting).<br>"
			+ "A route starts at a bank only when the test player's bank items make it quicker.</html>");
		bankStop.addChangeListener(e -> route.setBankTicks((Integer) bankStop.getValue()));
		JPanel playerRow = row();
		playerRow.add(new JLabel("Player:"));
		playerRow.add(player);
		playerRow.add(players);
		playerRow.add(new JLabel("   Start in Wilderness level:"));
		playerRow.add(wild);
		playerRow.add(new JLabel("   Bank stop (ticks):"));
		playerRow.add(bankStop);
		playerRow.add(costs);
		return playerRow;
	}

	/** Fills the player list from the saved test players, keeping the route's player selected. */
	private void fillPlayers()
	{
		fillingPlayers = true;
		String keep = route.getPlayer().name;
		player.removeAllItems();
		for (TestPlayer p : TestPlayers.load())
		{
			player.addItem(p);
			if (p.name.equals(keep))
			{
				player.setSelectedItem(p);
			}
		}
		fillingPlayers = false;
	}

	private void editPlayers()
	{
		if (TestPlayersDialog.edit(this, route.getPlayer().name))
		{
			fillPlayers();
			Object o = player.getSelectedItem();
			if (o instanceof TestPlayer)
			{
				route.setPlayer((TestPlayer) o);
			}
		}
	}

	/** Lines "name | x,y,plane | x,y,plane [| unconfirmed] [| shortest-path]"; # starts a comment. */
	private static List<Example> readExamples(File f)
	{
		List<Example> out = new ArrayList<>();
		if (f == null || !f.isFile())
		{
			return out;
		}
		try
		{
			for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
			{
				String t = line.trim();
				if (t.isEmpty() || t.startsWith("#"))
				{
					continue;
				}
				String[] p = t.split("\\|");
				if (p.length < 3)
				{
					continue;
				}
				boolean unconf = false;
				for (int i = 3; i < p.length; i++)
				{
					unconf |= p[i].trim().equalsIgnoreCase("unconfirmed");
				}
				out.add(new Example(p[0].trim(), tile(p[1]), tile(p[2]), unconf));
			}
		}
		catch (Exception ex)
		{
			// a broken examples file only means no examples
		}
		return out;
	}

	private static int[] tile(String s)
	{
		String[] p = s.trim().split("\\s*,\\s*");
		return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), p.length > 2 ? Integer.parseInt(p[2]) : 0};
	}

	private void refresh()
	{
		refreshing = true;
		status.setText(route.getStatus());
		model.clear();
		stepTiles.clear();
		Route r = route.getRoute();
		if (r != null && r.isFound())
		{
			int n = 1;
			int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			int[] start = route.getStart();
			for (Route.Step s : r.getSteps())
			{
				int[] t = s.getTiles();
				int first = t[0];
				for (int tile : t)
				{
					if (start != null && Tiles.plane(tile) == start[2])
					{
						box[0] = Math.min(box[0], Tiles.x(tile));
						box[1] = Math.min(box[1], Tiles.y(tile));
						box[2] = Math.max(box[2], Tiles.x(tile));
						box[3] = Math.max(box[3], Tiles.y(tile));
					}
				}
				model.addElement(stepText(n, s));
				stepTiles.add(new int[]{Tiles.x(first), Tiles.y(first), Tiles.plane(first)});
				n++;
			}
			if (fitWhenFound && box[0] <= box[2])
			{
				// the example's route: fit the main map to it (on the start's plane)
				fitWhenFound = false;
				area.show(box[0], box[1], box[2], box[3], start[2]);
			}
		}
		else if (r != null)
		{
			fitWhenFound = false;
		}
		refreshing = false;
	}

	/** Step {@code n} of the route as one line of the list. */
	private static String stepText(int n, Route.Step s)
	{
		int[] t = s.getTiles();
		int first = t[0];
		int last = t[t.length - 1];
		switch (s.getKind())
		{
			case WALK:
				return String.format("%d. Walk %d tiles (%d ticks) to %s", n, t.length - 1, s.getTicks(), where(last));
			case TELEPORT:
				return String.format("%d. Teleport: %s to %s (%d ticks)", n, s.getTransport().getName(), where(last), s.getTicks());
			case SAIL:
				return String.format("%d. %s: %d sea tiles (%d ticks) to %s", n, s.getTransport().getName(), t.length - 1,
					s.getTicks(), where(last));
			case BANK:
				return String.format("%d. Bank at %s%s, at %s (%d ticks)", n, s.getBankName(),
					s.getTakeOut().isEmpty() ? "" : ": take out " + String.join(", ", s.getTakeOut()), where(first), s.getTicks());
			default:
				return first == last
					? String.format("%d. %s, at %s (%d ticks)", n, s.getTransport().getName(), where(first), s.getTicks())
					: String.format("%d. %s from %s to %s (%d ticks%s)", n, s.getTransport().getName(), where(first),
					where(last), s.getTicks(), Tiles.plane(first) != Tiles.plane(last) ? ", plane change included" : "");
		}
	}

	private static String where(int t)
	{
		return Tiles.x(t) + ", " + Tiles.y(t) + ", plane " + Tiles.plane(t);
	}
}

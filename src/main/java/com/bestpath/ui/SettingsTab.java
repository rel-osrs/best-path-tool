package com.bestpath.ui;

import com.bestpath.core.data.TickGroups;
import com.bestpath.core.sea.BoatType;
import com.bestpath.player.BoatInfo;
import com.bestpath.player.HouseView;
import com.bestpath.player.PlayerBoats;
import com.bestpath.route.NoRouteWhy;
import com.bestpath.route.SlayerPreferred;
import com.bestpath.route.TransportFinder;
import com.bestpath.route.TransportRules;
import com.bestpath.route.TransportSettings;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;

/**
 * The sidebar Settings tab: transport types, "avoid" rules (with the coin limit), the persistent blacklist, preferred
 * Slayer places, "Transport ticks" (each transport's ticks), "Teleport and travel ticks" (each teleport category and
 * network, {@link TickGroupList}), "Your answers" ({@link AnswersSection}), the bank stop, the house (where it is and
 * what the house scan found; nothing to tick) and Sailing (which of the player's boats routes may use, whether a
 * shipwright may bring one, and the boarding ticks).
 * <p>
 * Each checkbox's tooltip says what it covers in the transport data ({@link TransportRules}); an "avoid" rule the
 * data can't support is left out. Each type and rule has the same icon as the step cards
 * ({@link IconChooser#category}, {@link IconChooser#avoid}). Swing thread only.
 * <p>
 * The RuneLite config panel's own settings come first (and "Advanced" last), with the same names, order and sections,
 * read from and written to RuneLite's config ({@link ConfigMirror}), so the two places mirror each other.
 * <p>
 * The tab is one column of rows. A plain section is a heading, a hint and the rows under it, folded by
 * {@link #fold} up to the next {@link #FOLD_EDGE}; "Teleport and travel ticks", "Your answers" and the config panel's
 * sections have folds of their own.
 */
final class SettingsTab extends JPanel
{
	private final PanelActions actions;
	private final IconCache icons;
	/** The tab's rows, top to bottom (the plain sections fold inside it). */
	private final JPanel body = new ScrollablePanel();
	/** The settings shown (the account's, with every change made here). */
	private TransportSettings settings = TransportSettings.defaults();
	/** True while the boxes are set from code (no change events then). */
	private boolean updating;

	/** The RuneLite config panel's settings, mirrored at the top and bottom of the tab. */
	final ConfigMirror mirror;

	// "Transport types" and "Avoid"
	private final Map<TransportSettings.Category, JCheckBox> categories = new EnumMap<>(TransportSettings.Category.class);
	private final Map<TransportSettings.Avoid, JCheckBox> avoid = new EnumMap<>(TransportSettings.Avoid.class);
	/** Avoid: never use a transport that costs more coins than the limit. The amount is sent after a pause. */
	JCheckBox coinCap;
	final JSpinner coinAmount = new JSpinner(new SpinnerNumberModel(TransportSettings.DEFAULT_COIN_CAP, 0,
		TransportSettings.MAX_COIN_CAP, 100));
	private final Timer coinTimer = new Timer(BANK_SEND_DELAY_MS, e -> sendCoinCap());
	static final String COIN_CAP_LABEL = "Don't use travel that costs over";
	static final String COIN_CAP_TIP = "Never use a fare over the coin limit, even when nothing else works";
	static final String COIN_AMOUNT_LABEL = "Coin limit";
	static final String COIN_AMOUNT_TIP = "The most coins one transport may cost (each fare on its own)";

	/** "Never used (blacklist)": each transport the player chose "Never use" for, with a Restore button. */
	private final JPanel blacklist = new JPanel();

	/** "Preferred Slayer places", each with an Unmark button. */
	private final JPanel preferredList = new JPanel();
	SlayerPreferred preferred = SlayerPreferred.NONE;
	static final String PREFERRED_TITLE = "Preferred Slayer places";
	static final String PREFERRED_HINT = "Places you starred on the Slayer task page. Go to the nearest place and Automatic "
		+ "Slayer routes pick among a task's preferred places.";

	/** Kept empty: each tick change's Reset sits next to its own - / + (see {@link #showTickList}). */
	private final JPanel tickList = new JPanel();
	/** "Transport ticks": every transport to search, filter, sort and change. */
	final TransportTickList transportTicks;
	static final String TRANSPORT_TICKS_HINT = "Change any transport's ticks with - / + (or click to type).";

	static final String TICKS_TITLE = "Teleport and travel ticks";
	/** "Teleport and travel ticks": its foldable header, the title with the fold arrow on its left. */
	private final JPanel ticksHeader = FoldArrow.header();
	private final JLabel ticksTitle = FoldArrow.heading(TICKS_TITLE, false);
	/** The fold's content: its hint and the rows. */
	final JPanel ticksBody = new JPanel();
	/** Every teleport category and network with its total ticks and - / +. */
	final TickGroupList tickGroups;

	/** "Your answers": every answer given (yes or no), to change or clear. */
	final AnswersSection answers;

	/** Bank: whether routes may start at a bank. */
	private JCheckBox bankDetour;
	static final String BANK_DETOUR_LABEL = "Routes may start at a bank (bank detours)";
	static final String BANK_DETOUR_TIP = "Stop at a bank first when quicker or needed. Unticked: use only what you carry.";
	static final String BANK_TIP = "Ticks counted for a bank stop.";
	/** The pause after the last click on the bank stop, the boarding ticks or the coin limit before it is sent. */
	static final int BANK_SEND_DELAY_MS = 600;
	/** The bank stop: "Bank stop" with its total ticks above - / +, sent after a short pause, so clicks add up first. */
	final TickSettingRow bankTicks = new TickSettingRow("1 tick less for the bank stop",
		"1 tick more for the bank stop", TransportSettings.DEFAULT_BANK_TICKS, BANK_SEND_DELAY_MS,
		ticks -> change(settings.withBankTicks(ticks)));

	/** "House (POH)": where the house is, what the house scan found and when (rebuilt when that changes). */
	final JPanel houseList = new JPanel();
	private HouseView house = HouseView.NOT_LOGGED_IN;
	static final String HOUSE_HINT = "Routes use your house. It's checked when you enter it.";
	/** Under what was found: how to make Best Path look again. */
	static final String HOUSE_AGAIN = "Changed something? Enter your house again.";
	static final String HOUSE_NOTHING = "Nothing found that routes can use.";
	static final String NO_HOUSE_NOTE = "Routes use house teleports once you own a house.";

	/** Sailing: one row per boat the player owns (or a short note), rebuilt when the boats or settings change. */
	private final JPanel boatList = new JPanel();
	private PlayerBoats boats = PlayerBoats.NOT_LOGGED_IN;
	private JCheckBox shipwrights;
	/** Sailing: never leave the boat the player is on at sea. */
	private JCheckBox keepBoat;
	static final String KEEP_BOAT_LABEL = "Never leave my boat at sea";
	static final String KEEP_BOAT_TIP = "On your boat, sail to a shipwright's dock first. Unticked: it may be left at sea.";
	static final String SHIPWRIGHT_LABEL = NoRouteWhy.Toggle.SHIPWRIGHTS_LABEL;
	static final String SHIPWRIGHT_TIP = "Let a shipwright bring your boat (raft 250, skiff 3,750, sloop 50,000 coins).";
	static final String BOARD_LABEL = "Board or leave a boat";
	static final String BOARD_TIP = "Ticks counted for boarding or leaving a boat.";
	/** Ticks to board or leave a boat, changed and sent like the bank stop. */
	final TickSettingRow boardTicks = new TickSettingRow("1 tick less to board or leave a boat",
		"1 tick more to board or leave a boat", TransportSettings.DEFAULT_BOARD_TICKS, BANK_SEND_DELAY_MS,
		ticks -> change(settings.withBoardTicks(ticks)));

	SettingsTab(PanelActions actions, IconCache icons)
	{
		this.actions = actions;
		this.icons = icons;
		setLayout(new BorderLayout());
		setBackground(Ui.BACKGROUND);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(Ui.BACKGROUND);
		body.setBorder(Ui.padding(Ui.PAD, Ui.PAD, Ui.PAD, Ui.PAD));

		// the sections, top to bottom: the config panel's settings first in its order (its Advanced section at the very
		// bottom, as there), the sidebar's own sections between
		mirror = new ConfigMirror(actions);
		addConfigSections();
		addTransportTypes();
		addAvoid();
		addBlacklist();
		addPreferredPlaces();
		transportTicks = addTransportTicks();
		tickGroups = addTickGroups();
		answers = addAnswers();
		addBank();
		addHouse();
		addSailing();
		addConfigAdvanced();
		add(Ui.verticalScroll(body), BorderLayout.CENTER);

		show(settings);
		showHouseList();
		configChanged();
		// every section starts folded
		setSectionsOpen(false);
	}

	/** The config panel's top items (without a heading), then its sections but Advanced. */
	private void addConfigSections()
	{
		Ui.addRow(body, mirror.part(null));
		for (ConfigList.Section s : ConfigList.SECTIONS)
		{
			if (s != ConfigList.ADVANCED)
			{
				Ui.addRow(body, Box.createVerticalStrut(10));
				Ui.addRow(body, mirror.part(s));
			}
		}
	}

	/** "Transport types": a tick box per type. */
	private void addTransportTypes()
	{
		section(body, "Transport types", "Untick a type to never use it.");
		for (TransportSettings.Category c : TransportSettings.Category.values())
		{
			JCheckBox box = Ui.checkBox("");
			box.addActionListener(e -> change(settings.withCategory(c, box.isSelected())));
			categories.put(c, box);
			Ui.addRow(body, new IconRow(box, IconChooser.category(c), c.getLabel(), TransportRules.describe(c), icons));
		}
	}

	/** "Avoid": a tick box per rule the data supports, then the coin limit's tick box and amount. */
	private void addAvoid()
	{
		section(body, "Avoid", "Use these only when nothing else works.");
		for (TransportSettings.Avoid a : TransportSettings.Avoid.values())
		{
			if (!TransportRules.isSupported(a))
			{
				continue; // no data backs this rule
			}
			JCheckBox box = Ui.checkBox("");
			box.addActionListener(e -> change(settings.withAvoid(a, box.isSelected())));
			avoid.put(a, box);
			Ui.addRow(body, new IconRow(box, IconChooser.avoid(a), a.getLabel(), TransportRules.describe(a), icons));
		}
		coinCap = Ui.checkBox("");
		coinCap.addActionListener(e -> change(settings.withCoinCapOn(coinCap.isSelected())));
		Ui.addRow(body, new IconRow(coinCap, IconChooser.avoid(TransportSettings.Avoid.COST), COIN_CAP_LABEL, COIN_CAP_TIP, icons));
		coinTimer.setRepeats(false);
		Ui.addRow(body, coinRow());
	}

	/** "Never used (blacklist)": the list is filled by {@link #showBlacklist}. */
	private void addBlacklist()
	{
		section(body, "Never used (blacklist)", "Transports you chose \"Never use\" for.");
		blacklist.setLayout(new BoxLayout(blacklist, BoxLayout.Y_AXIS));
		blacklist.setOpaque(false);
		Ui.addRow(body, blacklist);
	}

	/** "Preferred Slayer places": a list, so it is in the sidebar only (the config panel can't show it). */
	private void addPreferredPlaces()
	{
		section(body, PREFERRED_TITLE, PREFERRED_HINT);
		preferredList.setLayout(new BoxLayout(preferredList, BoxLayout.Y_AXIS));
		preferredList.setOpaque(false);
		Ui.addRow(body, preferredList);
		showPreferredList();
	}

	/**
	 * "Transport ticks": each transport's ticks, to search, filter and change ({@link TransportTickList}); whole types
	 * are changed in "Teleport and travel ticks" below.
	 */
	private TransportTickList addTransportTicks()
	{
		section(body, "Transport ticks", TRANSPORT_TICKS_HINT);
		tickList.setLayout(new BoxLayout(tickList, BoxLayout.Y_AXIS));
		tickList.setOpaque(false);
		Ui.addRow(body, tickList);
		TransportTickList list = new TransportTickList(icons, () -> settings, this::change, actions::playerTile);
		Ui.addRow(body, list);
		return list;
	}

	/**
	 * "Teleport and travel ticks": folded unless the player opened it (saved for the account); a click on the header
	 * folds or unfolds it.
	 */
	private TickGroupList addTickGroups()
	{
		Ui.addRow(body, edgeGap(10));
		Ui.addRow(body, ticksHeader);
		ticksBody.setLayout(new BoxLayout(ticksBody, BoxLayout.Y_AXIS));
		ticksBody.setOpaque(false);
		Ui.addRow(ticksBody, Ui.wrapped("Ticks for each type. Change a whole type with - / +.", Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.MUTED));
		Ui.addRow(ticksBody, Box.createVerticalStrut(3));
		TickGroupList groups = new TickGroupList(icons, () -> settings, this::change);
		Ui.addRow(ticksBody, groups);
		Ui.addRow(body, ticksBody);
		ticksHeader.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggleTicksList();
			}
		});
		showTicksFold(false);
		return groups;
	}

	/** "Your answers": folded like "Teleport and travel ticks" (but not saved). */
	private AnswersSection addAnswers()
	{
		Ui.addRow(body, edgeGap(10));
		AnswersSection section = new AnswersSection(this, actions::answer);
		Ui.addRow(body, section.header());
		Ui.addRow(body, section.body());
		return section;
	}

	/** "Bank": whether routes may start at a bank, and the bank stop's ticks. */
	private void addBank()
	{
		section(body, "Bank", "A route may start at a bank when that is quicker.");
		bankDetour = Ui.checkBox("");
		bankDetour.addActionListener(e -> change(settings.withBankDetour(bankDetour.isSelected())));
		Ui.addRow(body, new IconRow(bankDetour, IconKey.sprite(IconChooser.BANK), BANK_DETOUR_LABEL, BANK_DETOUR_TIP, icons));
		Ui.addRow(body, Box.createVerticalStrut(4));
		Ui.addRow(body, bankTicks.panel(IconKey.sprite(IconChooser.BANK), "Bank stop", BANK_TIP, icons));
	}

	/** "House (POH)": from the house scan only, nothing to tick (filled by {@link #showHouseList}). */
	private void addHouse()
	{
		section(body, "House (POH)", HOUSE_HINT);
		houseList.setLayout(new BoxLayout(houseList, BoxLayout.Y_AXIS));
		houseList.setOpaque(false);
		Ui.addRow(body, houseList);
	}

	/** "Sailing": the player's boats (filled by {@link #showBoatList}), the shipwright, keeping the boat, boarding ticks. */
	private void addSailing()
	{
		section(body, "Sailing", "Tick the boats routes may use.");
		boatList.setLayout(new BoxLayout(boatList, BoxLayout.Y_AXIS));
		boatList.setOpaque(false);
		Ui.addRow(body, boatList);
		Ui.addRow(body, Box.createVerticalStrut(4));
		shipwrights = Ui.checkBox("");
		shipwrights.addActionListener(e -> change(settings.withShipwrights(shipwrights.isSelected())));
		Ui.addRow(body, new IconRow(shipwrights, IconChooser.shipwright(), SHIPWRIGHT_LABEL, SHIPWRIGHT_TIP, icons));
		keepBoat = Ui.checkBox("");
		keepBoat.addActionListener(e -> change(settings.withKeepBoat(keepBoat.isSelected())));
		Ui.addRow(body, new IconRow(keepBoat, IconChooser.boat(BoatType.SLOOP), KEEP_BOAT_LABEL, KEEP_BOAT_TIP,
			icons));
		Ui.addRow(body, Box.createVerticalStrut(4));
		Ui.addRow(body, boardTicks.panel(IconChooser.boat(BoatType.SKIFF), BOARD_LABEL, BOARD_TIP, icons));
	}

	/** The config panel's Advanced section, last as there (folded at first, as there). */
	private void addConfigAdvanced()
	{
		Ui.addRow(body, edgeGap(10));
		Ui.addRow(body, mirror.part(ConfigList.ADVANCED));
	}

	/**
	 * Folds or unfolds every section of the tab: the config panel's (Advanced too) and the plain ones. "Your answers"
	 * and "Teleport and travel ticks" keep their own folds.
	 */
	void setSectionsOpen(boolean open)
	{
		for (Component c : body.getComponents())
		{
			if (c instanceof JLabel && FOLD_EDGE.equals(c.getName()))
			{
				fold(body, (JLabel) c, open);
			}
		}
		for (ConfigList.Section s : ConfigList.SECTIONS)
		{
			if (mirror.isOpen(s) != open)
			{
				mirror.toggle(s);
			}
		}
	}

	/** The config panel's settings shown here ({@link ConfigList}): read again from RuneLite's config, nothing sent back. */
	void configChanged()
	{
		mirror.configChanged();
	}

	/** Folds or unfolds "Teleport and travel ticks" and saves that for this account (the route stays). */
	void toggleTicksList()
	{
		boolean open = !settings.isTicksListOpen();
		settings = settings.withTicksListOpen(open);
		showTicksFold(open);
		actions.settingsSaved(settings);
	}

	private void showTicksFold(boolean open)
	{
		if (ticksHeader.getComponentCount() == 0)
		{
			ticksHeader.add(ticksTitle, BorderLayout.WEST);
		}
		FoldArrow.set(ticksTitle, open);
		ticksBody.setVisible(open);
		revalidate();
		repaint();
	}

	/** The player's answers (account switch, and after each answer): the data's words -> yes / no. */
	void showAnswers(Map<String, Boolean> given)
	{
		answers.showAnswers(given);
	}

	/**
	 * Every question the map data asks (its answer words, {@link com.bestpath.route.AnswerQuestions#answersIn}), once it
	 * has loaded. The ones not answered yet are listed under the answers, with Yes / No.
	 */
	void showQuestions(List<String> all)
	{
		answers.showQuestions(all);
	}

	/** "Coin limit" and its amount (typed or with the arrows, 100 a click). */
	private JPanel coinRow()
	{
		JPanel line = Ui.cardLine(4, 0);
		line.setToolTipText(COIN_AMOUNT_TIP);
		JLabel name = Ui.label(COIN_AMOUNT_LABEL, Ui.small(), Ui.TEXT);
		name.setToolTipText(COIN_AMOUNT_TIP);
		line.add(name, BorderLayout.CENTER);
		Ui.smallSpinner(coinAmount, 7, COIN_AMOUNT_TIP);
		coinAmount.addChangeListener(e ->
		{
			if (!updating)
			{
				coinTimer.restart(); // a few clicks make one change (and one new route)
			}
		});
		line.add(coinAmount, BorderLayout.EAST);
		return line;
	}

	/** Sends the coin limit shown, after the pause. */
	void sendCoinCap()
	{
		coinTimer.stop();
		int coins = ((Number) coinAmount.getValue()).intValue();
		if (coins != settings.getCoinCap())
		{
			change(settings.withCoinCap(coins));
		}
	}

	/** Adds a plain section's heading (with its fold arrow) and its hint; a gap ends the section above it. */
	private static void section(JPanel body, String title, String hint)
	{
		if (body.getComponentCount() > 0)
		{
			Ui.addRow(body, edgeGap(10));
		}
		// the fold arrow sits left of the title; the rows under it are not indented
		JLabel heading = FoldArrow.heading(title, true);
		heading.setName(FOLD_EDGE);
		heading.setToolTipText("Click to show or hide");
		heading.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				fold(body, heading, !FoldArrow.isOpen(heading));
			}
		});
		Ui.addRow(body, heading);
		Ui.addRow(body, Ui.wrapped(hint, Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.MUTED));
		Ui.addRow(body, Box.createVerticalStrut(3));
	}

	/** Where a plain section's fold ends: the next heading, or the gap before a block with its own fold. */
	static final String FOLD_EDGE = "fold-edge";

	/** Folds or unfolds a plain section: every row under its heading up to the next {@link #FOLD_EDGE}. */
	static void fold(JPanel body, JLabel heading, boolean open)
	{
		FoldArrow.set(heading, open);
		boolean in = false;
		for (Component c : body.getComponents())
		{
			if (c == heading)
			{
				in = true;
				continue;
			}
			if (in && FOLD_EDGE.equals(c.getName()))
			{
				break;
			}
			if (in)
			{
				c.setVisible(open);
			}
		}
		body.revalidate();
		body.repaint();
	}

	/** A gap that ends the plain section above it (the block after it has its own fold). */
	private static Component edgeGap(int height)
	{
		Component gap = Box.createVerticalStrut(height);
		gap.setName(FOLD_EDGE);
		return gap;
	}

	/** A change made here: kept, sent to the plugin (saved; the route is searched again) and shown in every section. */
	private void change(TransportSettings s)
	{
		if (updating || s.equals(settings))
		{
			return;
		}
		settings = s;
		actions.settingsChanged(s);
		showBlacklist();
		showTickList();
		showBoatList();
		tickGroups.show(s);
		transportTicks.settingsChanged();
	}

	TransportSettings getSettings()
	{
		return settings;
	}

	/** Shows saved settings (e.g. after switching account). */
	void show(TransportSettings s)
	{
		settings = s;
		updating = true;
		try
		{
			for (Map.Entry<TransportSettings.Category, JCheckBox> e : categories.entrySet())
			{
				e.getValue().setSelected(s.isEnabled(e.getKey()));
			}
			for (Map.Entry<TransportSettings.Avoid, JCheckBox> e : avoid.entrySet())
			{
				// a rule the data can't support shows unticked (it is never applied)
				e.getValue().setSelected(s.avoids(e.getKey()) && TransportRules.isSupported(e.getKey()));
			}
			shipwrights.setSelected(s.isShipwrights());
			bankDetour.setSelected(s.isBankDetour());
			keepBoat.setSelected(s.isKeepBoat());
			coinCap.setSelected(s.isCoinCapOn());
			coinTimer.stop();
			coinAmount.setValue(s.getCoinCap());
			bankTicks.show(s.getBankTicks());
			boardTicks.show(s.getBoardTicks());
			showTicksFold(s.isTicksListOpen());
		}
		finally
		{
			updating = false;
		}
		showBlacklist();
		showTickList();
		showBoatList();
		tickGroups.show(s);
		transportTicks.settingsChanged();
	}

	/** The plugin data's transports for "Transport ticks" (once the map data has loaded). */
	void showTransports(TransportFinder f)
	{
		transportTicks.setFinder(f);
	}

	/** The map data's teleport categories and networks (once it has loaded): their rows and the changes' totals. */
	void showTickGroups(TickGroups g)
	{
		tickGroups.setGroups(g);
		showTickList();
	}

	/** The player's house changed (place from the game; what the house scan found; login, logout). */
	void showHouse(HouseView v)
	{
		house = v == null ? HouseView.NOT_LOGGED_IN : v;
		showHouseList();
	}

	/** "Last looked: 14 Oct 2026, 20:15" (the computer's time zone). */
	static String lastLookedText(long epochMillis, ZoneId zone)
	{
		DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
		return "Last looked: " + Instant.ofEpochMilli(epochMillis).atZone(zone).format(f);
	}

	private void showHouseList()
	{
		houseList.removeAll();
		houseList.add(Ui.left(new PictureRow(IconKey.sprite(IconChooser.HOUSE), house.placeText(), null, icons)));
		houseList.add(Box.createVerticalStrut(3));
		if (house.getStatus() == HouseView.Status.NO_HOUSE)
		{
			houseList.add(Ui.left(Ui.wrapped(NO_HOUSE_NOTE, Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.MUTED)));
		}
		else if (house.getStatus() == HouseView.Status.HOUSE)
		{
			List<HouseView.Row> rows = house.rows();
			if (house.getScannedAt() == 0 && rows.isEmpty())
			{
				// not looked at yet
				houseList.add(Ui.left(Ui.wrapped(HouseView.NOT_LOOKED_HINT, Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.NOTE)));
			}
			else
			{
				houseList.add(Ui.left(Ui.label("Built:", Ui.small(), Ui.TEXT)));
				houseList.add(Box.createVerticalStrut(2));
				if (rows.isEmpty())
				{
					houseList.add(Ui.left(Ui.wrapped(HOUSE_NOTHING, Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.TEXT)));
				}
				for (HouseView.Row r : rows)
				{
					houseList.add(Ui.left(new PictureRow(IconChooser.houseRow(r), r.getText(), r.getDetail(), icons)));
					houseList.add(Box.createVerticalStrut(2));
				}
				houseList.add(Box.createVerticalStrut(2));
				houseList.add(Ui.left(Ui.label(house.getScannedAt() == 0 ? "Last looked: not known"
					: lastLookedText(house.getScannedAt(), ZoneId.systemDefault()), Ui.small(), Ui.MUTED)));
				houseList.add(Ui.left(Ui.wrapped(HOUSE_AGAIN, Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.MUTED)));
			}
		}
		houseList.revalidate();
		houseList.repaint();
	}

	/** The player's boats changed (read from the game; login, logout): rebuilds the Sailing rows. */
	void showBoats(PlayerBoats b)
	{
		boats = b == null ? PlayerBoats.NOT_LOGGED_IN : b;
		showBoatList();
	}

	/** Text when no boat is listed. */
	static String noBoatsText(PlayerBoats b)
	{
		switch (b.getStatus())
		{
			case NOT_LOGGED_IN:
				return "Log in to see your boats here.";
			case NO_DATA:
				return "Your boats can't be read yet.";
			default:
				return "You have no boats yet. Routes sail once you own one.";
		}
	}

	private void showBoatList()
	{
		boatList.removeAll();
		if (boats.getRows().isEmpty())
		{
			boatList.add(Ui.left(Ui.wrapped(noBoatsText(boats), Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.TEXT)));
		}
		for (BoatInfo info : boats.getRows())
		{
			JCheckBox box = Ui.checkBox("");
			box.setEnabled(info.isUsable());
			box.setSelected(info.isUsable() && settings.isBoatAllowed(info.getSlot()));
			int slot = info.getSlot();
			box.addActionListener(e -> change(settings.withBoatAllowed(slot, box.isSelected())));
			String tip = "Boat " + slot + ": " + info.getDetails() + (info.isUsable() ? ". Tick to let routes use it." : "");
			boatList.add(Ui.left(new IconRow(box, IconChooser.boat(info.getType()), info.getTitle(), info.getWhere(), tip, icons)));
			boatList.add(Box.createVerticalStrut(3));
		}
		if (boats.getAboardNote() != null)
		{
			boatList.add(Ui.left(Ui.wrapped(boats.getAboardNote(), Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.NOTE)));
		}
		boatList.revalidate();
		boatList.repaint();
	}

	private void showBlacklist()
	{
		blacklist.removeAll();
		if (settings.getBlacklist().isEmpty())
		{
			blacklist.add(nothingHere());
		}
		for (Map.Entry<String, String> e : settings.getBlacklist().entrySet())
		{
			JButton restore = Ui.button("Restore", "Allow this transport again");
			String id = e.getKey();
			restore.addActionListener(ev -> change(settings.withoutBlacklisted(id)));
			blacklist.add(listLine(e.getValue(), restore));
			blacklist.add(Box.createVerticalStrut(3));
		}
		blacklist.revalidate();
		blacklist.repaint();
	}

	/** The account's preferred Slayer places (login, account switch, after a star or an Unmark). */
	void showSlayerPreferred(SlayerPreferred p)
	{
		preferred = p == null ? SlayerPreferred.NONE : p;
		showPreferredList();
	}

	/** One row per preferred place, "Kalphites: Kalphite Lair", with Unmark. */
	private void showPreferredList()
	{
		preferredList.removeAll();
		if (preferred.isEmpty())
		{
			preferredList.add(nothingHere());
		}
		for (SlayerPreferred.Mark m : preferred.list())
		{
			JButton unmark = Ui.button("Unmark", "No longer prefer this place for " + m.getTask());
			unmark.addActionListener(ev ->
			{
				showSlayerPreferred(preferred.with(m.getTask(), m.getPlace(), false));
				actions.preferSlayerPlace(m.getTask(), m.getPlace(), false);
			});
			preferredList.add(listLine(m.getTask() + ": " + m.getPlaceName(), unmark));
			preferredList.add(Box.createVerticalStrut(3));
		}
		preferredList.revalidate();
		preferredList.repaint();
	}

	/** "Nothing here." for an empty list. */
	private static JLabel nothingHere()
	{
		JLabel none = Ui.label("Nothing here.", Ui.small(), Ui.TEXT);
		none.setAlignmentX(LEFT_ALIGNMENT);
		return none;
	}

	/** One listed thing on the card colour, its text wrapping beside the button at its end ("Restore", "Unmark"). */
	private static JPanel listLine(String text, JButton button)
	{
		JPanel line = Ui.cardLine(4, 0);
		line.add(Ui.wrapped(text, Ui.CONTENT_WIDTH - 90, Ui.small(), Ui.TEXT), BorderLayout.CENTER);
		line.add(button, BorderLayout.EAST);
		Ui.left(line);
		return line;
	}

	/**
	 * Keeps the tick change list empty: each type's Reset sits under its - / + in "Teleport and travel ticks", and each
	 * transport's in "Transport ticks".
	 */
	private void showTickList()
	{
		tickList.removeAll();
		tickList.revalidate();
		tickList.repaint();
	}
}

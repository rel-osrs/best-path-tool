package com.bestpath.tool.ui;

import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.editor.EditorTool;
import com.bestpath.tool.transport.Suggestion;
import com.bestpath.tool.transport.TransportCandidate;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * The bar above the maps: the active tool's options and its help text. It also
 * makes the decision bar below the maps (the selected transport and what you
 * can do with it, shown whatever the tool).
 */
final class ToolOptionsBar extends JPanel
{
	private final EditorContext ctx;
	private final CardLayout cards = new CardLayout();
	private final JPanel options = new JPanel(cards);
	private final JLabel help = new JLabel(" ");

	// the selected transport or suggestion
	private final JLabel selected = new JLabel("Nothing selected");

	/** Names a sea channel clicked in the "selected" line; Delete (Del) deletes it. */
	void showChannel(String text)
	{
		selected.setText(text);
		delete.setEnabled(true);
	}
	private final JButton nextSug = new JButton("Next suggestion (N)");
	private final JButton confirm = new JButton("Confirm (C)");
	private final JButton delete = new JButton("Delete (Del)");
	private final JButton skip = new JButton("Skip (S)");
	private final JButton testLater = new JButton("Test in game later (L)");
	private final JButton reverse = new JButton("Reverse direction");
	private final JButton twoWay = new JButton("One-way / two-way (O)");
	private final JButton requirements = new JButton("More...");

	// what the selected transport needs: in words, and its line to edit in place
	private final javax.swing.JTextArea needsWords = new javax.swing.JTextArea();
	private final JTextField needsField = new JTextField(50);
	private final JLabel needsCheck = new JLabel(" ");
	private final JButton applyNeeds = new JButton("Apply");
	/** The record (id and line) the edit box was filled from; the box is only refilled when that changes. */
	private String needsLoaded;

	// where the selected transport is: its start and end tiles, typed by hand
	private final JLabel startLabel = new JLabel("Start:");
	private final JLabel endLabel = new JLabel("End:");
	private final JTextField[] startFields = {new JTextField(5), new JTextField(5), new JTextField(2)};
	private final JTextField[] endFields = {new JTextField(5), new JTextField(5), new JTextField(2)};
	private final JButton move = new JButton("Move");
	private final JLabel whereCheck = new JLabel(" ");
	/** The record (id and tiles) the Where boxes were filled from; they are only refilled when that changes. */
	private String whereLoaded;

	// transport tool options
	// a network stop is only made from a suggested network destination, never chosen here
	private final JComboBox<TransportRecord.Kind> kind = new JComboBox<>(new TransportRecord.Kind[]{
		TransportRecord.Kind.TWO_WAY, TransportRecord.Kind.ONE_WAY, TransportRecord.Kind.TELEPORT});
	private final JSpinner ticks = new JSpinner(new SpinnerNumberModel(2, 0, 1000, 1));
	private final JTextField name = new JTextField(14);
	private final JButton applyProps = new JButton("Set on selected");
	private boolean updating;

	ToolOptionsBar(EditorContext ctx)
	{
		super(new BorderLayout(10, 2));
		this.ctx = ctx;
		setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
		help.setFont(help.getFont().deriveFont(Font.ITALIC));
		decisionBar = selectionRow();
		add(options, BorderLayout.CENTER);
		add(help, BorderLayout.SOUTH);
		options.add(new JPanel(), "none");
		options.add(blockOptions(), "Block brush");
		options.add(transportOptions(), "Transport");
		ctx.addSelectionListener(this::refreshSelection);
		ctx.edits.addListener(this::refreshSelection);
		refreshSelection();
	}

	void showTool(EditorTool tool)
	{
		help.setText(tool.getName() + ": " + tool.getHelp());
		String card = tool.getName().equals("Block brush") || tool.getName().equals("Transport") ? tool.getName() : "none";
		cards.show(options, card);
	}

	/** The decision buttons for the selected transport; MainWindow places them below the maps. */
	private JPanel decisionBar;

	JPanel getDecisionBar()
	{
		return decisionBar;
	}

	private JPanel selectionRow()
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, java.awt.Color.GRAY),
			BorderFactory.createEmptyBorder(4, 4, 4, 4)));
		selected.setFont(selected.getFont().deriveFont(Font.BOLD));
		p.add(selected);
		p.add(nextSug);
		p.add(confirm);
		p.add(delete);
		p.add(skip);
		p.add(testLater);
		p.add(reverse);
		nextSug.setToolTipText("Show the next suggested destination for this object (same plane, up, down)");
		nextSug.addActionListener(e -> ctx.nextSuggestion());
		confirm.setToolTipText("Turn the selected suggestion, as shown (also after dragging its ends), into a transport. Saved at once (Ctrl+Z undoes).");
		confirm.addActionListener(e -> ctx.confirmSelected());
		delete.setToolTipText("Suggestion: 'not a transport'. Transport: delete it. Saved at once (Ctrl+Z undoes).");
		delete.addActionListener(e -> ctx.deleteSelected());
		skip.setToolTipText("Decide later: go to the nearest candidate you have not decided on yet");
		skip.addActionListener(e -> skipNext());
		testLater.setToolTipText("Not sure: put it, as shown, on the 'Test in game' list (purple) to check in the game later");
		testLater.addActionListener(e -> ctx.testLaterSelected());
		reverse.setToolTipText("Swap start and end of the selected transport");
		reverse.addActionListener(e -> reverseSelected());
		p.add(twoWay);
		twoWay.setToolTipText("Switch the selected transport between working in one direction only (start to end) and in both directions");
		twoWay.addActionListener(e -> ctx.toggleTwoWaySelected());
		requirements.setToolTipText("All requirement settings: a different line for end to start, the source, "
			+ "the 'quest only' / 'can fail' marks, the network or teleport category, and the help for writing lines");
		requirements.addActionListener(e -> editRequirements());
		// the description gets its own line, so a long one never pushes the buttons out of sight
		JPanel bar = new JPanel(new BorderLayout(0, 2));
		bar.setBorder(p.getBorder());
		p.setBorder(null);
		p.remove(selected);
		JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		line.add(selected);
		bar.add(line, BorderLayout.NORTH);
		bar.add(p, BorderLayout.CENTER);
		JPanel below = new JPanel(new BorderLayout(0, 2));
		below.add(wherePanel(), BorderLayout.NORTH);
		below.add(needsPanel(), BorderLayout.CENTER);
		bar.add(below, BorderLayout.SOUTH);
		return bar;
	}

	/**
	 * The "Where" row: the selected transport's start and end typed by hand. This is the way to move an end to
	 * another plane (dragging a handle stays on the plane of the view it is dragged in). Enter or Move saves.
	 */
	private JPanel wherePanel()
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		startLabel.setFont(startLabel.getFont().deriveFont(Font.BOLD));
		endLabel.setFont(endLabel.getFont().deriveFont(Font.BOLD));
		p.add(startLabel);
		for (JTextField f : startFields)
		{
			p.add(f);
		}
		p.add(new JLabel("   "));
		p.add(endLabel);
		for (JTextField f : endFields)
		{
			p.add(f);
		}
		p.add(move);
		p.add(whereCheck);
		String tip = "x, y, plane (0-3). Enter or Move saves (Ctrl+Z undoes). Only the tile itself is checked: it needs a floor "
			+ "on that plane";
		for (JTextField f : allWhereFields())
		{
			f.setToolTipText(tip);
			f.addActionListener(e -> applyWhere());
			f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
			{
				@Override
				public void insertUpdate(javax.swing.event.DocumentEvent e)
				{
					checkWhere();
				}

				@Override
				public void removeUpdate(javax.swing.event.DocumentEvent e)
				{
					checkWhere();
				}

				@Override
				public void changedUpdate(javax.swing.event.DocumentEvent e)
				{
					checkWhere();
				}
			});
			// Esc puts the saved tiles back
			f.registerKeyboardAction(e ->
			{
				whereLoaded = null;
				refreshSelection();
			}, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
		}
		move.setToolTipText("Move the selected transport's ends to the typed tiles (Enter does the same; Ctrl+Z undoes)");
		move.addActionListener(e -> applyWhere());
		return p;
	}

	private JTextField[] allWhereFields()
	{
		return new JTextField[]{startFields[0], startFields[1], startFields[2], endFields[0], endFields[1], endFields[2]};
	}

	/** Whether the record has a start of its own to type (a teleport has none; a network stop's start is its end). */
	private static boolean ownStart(TransportRecord r)
	{
		return r.hasStart() && r.kind != TransportRecord.Kind.NETWORK;
	}

	/** Fills the Where row for the selection (the boxes only when another transport or its tiles changed). */
	private void refreshWhere(EditorContext.Selection s)
	{
		TransportRecord r = s == null ? null : s.record;
		boolean start = r != null && ownStart(r);
		for (JTextField f : startFields)
		{
			f.setEnabled(start);
		}
		for (JTextField f : endFields)
		{
			f.setEnabled(r != null);
		}
		endLabel.setText(r != null && r.kind == TransportRecord.Kind.NETWORK ? "Stop:" : "End:");
		if (r == null)
		{
			whereLoaded = null;
			for (JTextField f : allWhereFields())
			{
				f.setText("");
			}
			move.setEnabled(false);
			whereCheck.setText(" ");
			return;
		}
		String key = r.id + " " + r.startX + "," + r.startY + "," + r.startPlane + " " + r.endX + "," + r.endY + "," + r.endPlane;
		if (!key.equals(whereLoaded))
		{
			whereLoaded = key;
			int[] st = start ? new int[]{r.startX, r.startY, r.startPlane} : null;
			int[] en = {r.endX, r.endY, r.endPlane};
			for (int i = 0; i < 3; i++)
			{
				startFields[i].setText(st == null ? "" : String.valueOf(st[i]));
				endFields[i].setText(String.valueOf(en[i]));
			}
		}
		checkWhere();
	}

	/** The typed tile {x, y, plane}, or null when a box is not a number in range. */
	private static int[] typedTile(JTextField[] f)
	{
		try
		{
			int x = Integer.parseInt(f[0].getText().trim());
			int y = Integer.parseInt(f[1].getText().trim());
			int p = Integer.parseInt(f[2].getText().trim());
			return WhereCheck.inRange(x, y, p) ? new int[]{x, y, p} : null;
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	/** Shows whether the typed tiles can be used; Move only when they can and they differ from the saved ones. */
	private void checkWhere()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null || s.record == null)
		{
			return;
		}
		TransportRecord r = s.record;
		boolean start = ownStart(r);
		int[] st = start ? typedTile(startFields) : null;
		int[] en = typedTile(endFields);
		String problem = WhereCheck.problem(start, st, en, ctx.graph == null ? null : ctx.graph::componentAt);
		boolean changed = en != null && (en[0] != r.endX || en[1] != r.endY || en[2] != r.endPlane)
			|| st != null && (st[0] != r.startX || st[1] != r.startY || st[2] != r.startPlane);
		move.setEnabled(problem == null && changed);
		whereCheck.setForeground(problem != null ? java.awt.Color.RED : new java.awt.Color(0, 120, 0));
		whereCheck.setText(problem != null ? problem : changed ? "OK, not moved yet" : ctx.graph == null ? "saved (floor not checked: no map)"
			: "saved");
	}

	/** Moves the selected transport's ends to the typed tiles (a change in effect at once; Ctrl+Z undoes). */
	private void applyWhere()
	{
		EditorContext.Selection s = ctx.getSelection();
		checkWhere();
		if (s == null || s.record == null || !move.isEnabled())
		{
			return;
		}
		TransportRecord r = s.record.copy();
		int[] en = typedTile(endFields);
		r.endX = en[0];
		r.endY = en[1];
		r.endPlane = en[2];
		if (ownStart(r))
		{
			int[] st = typedTile(startFields);
			r.startX = st[0];
			r.startY = st[1];
			r.startPlane = st[2];
		}
		else if (r.kind == TransportRecord.Kind.NETWORK)
		{
			// a network stop is used and arrived at on the same tile: both ends move together (as a handle drag)
			r.startX = en[0];
			r.startY = en[1];
			r.startPlane = en[2];
		}
		ctx.pendTransport(r);
		ctx.select(EditorContext.Selection.ofRecord(r, true), null, false);
	}

	/** The "Needs" box: the selected transport's requirements in words, and its line to edit (Enter or Apply saves). */
	private JPanel needsPanel()
	{
		JPanel box = new JPanel(new BorderLayout(6, 4));
		box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0),
			BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new java.awt.Color(200, 150, 0), 2),
				BorderFactory.createEmptyBorder(4, 6, 4, 6))));
		box.setBackground(new java.awt.Color(255, 248, 220));
		JLabel title = new JLabel("Needs:");
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));
		title.setVerticalAlignment(JLabel.TOP);
		needsWords.setEditable(false);
		needsWords.setLineWrap(true);
		needsWords.setWrapStyleWord(true);
		needsWords.setOpaque(false);
		needsWords.setFocusable(false);
		needsWords.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() - 1f));
		box.add(title, BorderLayout.WEST);
		box.add(needsWords, BorderLayout.CENTER);

		JPanel edit = new JPanel(new BorderLayout(6, 0));
		edit.setOpaque(false);
		JLabel lineLabel = new JLabel("Line:");
		lineLabel.setToolTipText("The requirement line. Parts joined with ; are all needed; one of (A | B) needs one. "
			+ "Examples: Agility 61; quest Regicide; item 4702; varbit 9653 >= 710; members. 'More...' shows all the parts.");
		needsField.setToolTipText(lineLabel.getToolTipText());
		edit.add(lineLabel, BorderLayout.WEST);
		edit.add(needsField, BorderLayout.CENTER);
		JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		right.setOpaque(false);
		right.add(applyNeeds);
		right.add(requirements);
		right.add(needsCheck);
		edit.add(right, BorderLayout.EAST);
		box.add(edit, BorderLayout.SOUTH);

		applyNeeds.setToolTipText("Save this line as what the selected transport needs (Enter does the same; Ctrl+Z undoes)");
		applyNeeds.addActionListener(e -> applyNeedsLine());
		needsField.addActionListener(e -> applyNeedsLine());
		needsField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e)
			{
				checkNeedsLine();
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e)
			{
				checkNeedsLine();
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e)
			{
				checkNeedsLine();
			}
		});
		// Esc in the box puts the saved line back
		needsField.registerKeyboardAction(e ->
		{
			needsLoaded = null;
			refreshSelection();
		}, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
		return box;
	}

	/** Fills the Needs box for the selection (the edit box only when another transport or line is selected). */
	private void refreshNeeds(EditorContext.Selection s)
	{
		TransportRecord r = s == null ? null : s.record;
		needsField.setEnabled(r != null);
		requirements.setEnabled(r != null);
		if (r == null)
		{
			needsWords.setText(s == null ? "Click a transport to see what the player needs to use it."
				: "Not a transport yet: confirm it first, then its requirements can be set here.");
			needsField.setText("");
			needsCheck.setText(" ");
			applyNeeds.setEnabled(false);
			needsLoaded = null;
			return;
		}
		StringBuilder sb = new StringBuilder();
		boolean twoWay = r.kind == TransportRecord.Kind.TWO_WAY && r.requirementsBack != null;
		sb.append(twoWay ? "Start to end: " : "").append(inWords(r.requirements));
		if (twoWay)
		{
			sb.append("\nEnd to start: ").append(inWords(r.requirementsBack));
		}
		com.bestpath.tool.req.Networks.Network net = r.kind == TransportRecord.Kind.NETWORK ? null
			: com.bestpath.tool.req.Networks.get(r.network);
		if (net != null && !net.requirement.isEmpty())
		{
			sb.append("\nPlus, for every ").append(net.name).append(" ride: ").append(inWords(net.requirement));
		}
		String marks = marks(r);
		if (!marks.isEmpty())
		{
			sb.append("\n").append(marks);
		}
		needsWords.setText(sb.toString());
		String key = r.id + "\n" + r.requirements;
		if (!key.equals(needsLoaded))
		{
			needsLoaded = key;
			needsField.setText(r.requirements == null ? "" : r.requirements);
			needsField.setCaretPosition(0);
		}
		checkNeedsLine();
	}

	/** A line in words, parts joined with bullets; "nothing" when empty, the line itself when it cannot be read. */
	private static String inWords(String line)
	{
		if (line == null || line.isBlank())
		{
			return "nothing";
		}
		try
		{
			java.util.List<String> parts = com.bestpath.tool.req.RequirementWords.parts(com.bestpath.tool.req.ItemGroupFile.parse(line));
			return parts.isEmpty() ? "nothing" : String.join("   •   ", parts);
		}
		catch (com.bestpath.core.req.RequirementText.ParseException ex)
		{
			return line + "   (CANNOT BE READ: " + ex.getMessage() + ")";
		}
	}

	/** The marks that change how the transport is used ("quest only", Wilderness...), or empty. */
	private static String marks(TransportRecord r)
	{
		java.util.List<String> m = new java.util.ArrayList<>();
		if (r.questOnly)
		{
			m.add("quest only (never used for normal routes)");
		}
		if (r.canFail)
		{
			m.add("can fail");
		}
		if (r.network != null && !r.network.isEmpty() && r.kind != TransportRecord.Kind.NETWORK
			&& com.bestpath.tool.req.Networks.get(r.network) == null)
		{
			m.add("network " + r.network + " (not in networks.tsv)");
		}
		if (r.teleportCategory != null && !r.teleportCategory.isEmpty())
		{
			m.add("teleport category " + r.teleportCategory);
		}
		if (r.wildernessLimit != null)
		{
			m.add(r.wildernessLimit == 0 ? "not in the Wilderness" : "Wilderness up to level " + r.wildernessLimit);
		}
		if (r.cooldown)
		{
			m.add("cooldown");
		}
		return m.isEmpty() ? "" : "Marks: " + String.join(", ", m);
	}

	/** Shows whether the typed line can be read; Apply only when it can and it differs from the saved one. */
	private void checkNeedsLine()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null || s.record == null)
		{
			return;
		}
		String typed = needsField.getText().trim();
		String saved = s.record.requirements == null ? "" : s.record.requirements.trim();
		try
		{
			com.bestpath.core.req.Requirement req = com.bestpath.tool.req.ItemGroupFile.parse(typed);
			java.util.List<String> unknown = com.bestpath.tool.req.CacheNames.unknownNames(req);
			boolean changed = !typed.equals(saved);
			applyNeeds.setEnabled(changed);
			needsCheck.setForeground(unknown.isEmpty() ? new java.awt.Color(0, 120, 0) : new java.awt.Color(190, 100, 0));
			needsCheck.setText(!unknown.isEmpty() ? "unknown: " + String.join(", ", unknown)
				: changed ? "OK, not saved yet: " + com.bestpath.tool.req.RequirementWords.parts(req).size() + " part(s)" : "saved");
		}
		catch (com.bestpath.core.req.RequirementText.ParseException ex)
		{
			applyNeeds.setEnabled(false);
			needsCheck.setForeground(java.awt.Color.RED);
			needsCheck.setText(ex.getMessage());
		}
	}

	/** Saves the typed line as the selected transport's requirements (a change in effect at once; Ctrl+Z undoes). */
	private void applyNeedsLine()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null || s.record == null || !applyNeeds.isEnabled())
		{
			return;
		}
		TransportRecord r = s.record.copy();
		r.requirements = com.bestpath.tool.req.CacheNames.tidyQuestNames(needsField.getText());
		ctx.pendTransport(r);
		ctx.select(EditorContext.Selection.ofRecord(r, true), null, false);
	}

	/** Skip, with a message when nothing is left. */
	void skipNext()
	{
		if (!ctx.skipToNext())
		{
			selected.setText("No undecided candidates left (skipped ones come back after a restart)");
		}
	}

	private JPanel blockOptions()
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		JComboBox<Integer> size = new JComboBox<>(new Integer[]{1, 3, 5, 7, 9});
		size.addActionListener(e -> ctx.brushSize = (Integer) size.getSelectedItem());
		p.add(new JLabel("Left button: blocked    Right button: walkable        Brush size:"));
		p.add(size);
		return p;
	}

	private JPanel transportOptions()
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		p.add(new JLabel("Kind:"));
		p.add(kind);
		p.add(new JLabel("Ticks:"));
		p.add(ticks);
		p.add(new JLabel("Name:"));
		p.add(name);
		p.add(applyProps);
		kind.addActionListener(e ->
		{
			if (!updating)
			{
				ctx.newKind = (TransportRecord.Kind) kind.getSelectedItem();
			}
		});
		ticks.addChangeListener(e ->
		{
			if (!updating)
			{
				ctx.newTicks = (Integer) ticks.getValue();
			}
		});
		applyProps.setToolTipText("Give the selected transport this kind, tick cost and name (a pending change)");
		applyProps.addActionListener(e -> applyToSelected());
		return p;
	}

	private void refreshSelection()
	{
		EditorContext.Selection s = ctx.getSelection();
		boolean candidate = s != null && s.candidate != null;
		boolean record = s != null && s.record != null;
		boolean teleport = s != null && s.teleport != null;
		boolean network = record && s.record.kind == TransportRecord.Kind.NETWORK;
		confirm.setEnabled(teleport || (candidate && (s.suggestion != null || s.draft != null))
			|| (record && ctx.effectiveTestLater().containsKey(EditorContext.objectKey(s.record))));
		nextSug.setEnabled(false);
		delete.setEnabled(s != null);
		skip.setEnabled(ctx.transports != null);
		testLater.setEnabled(s != null);
		reverse.setEnabled(record && s.record.hasStart() && !network);
		twoWay.setEnabled((record && s.record.hasStart() && !network) || (candidate && (s.draft != null || s.suggestion != null)));
		applyProps.setEnabled(record);
		refreshNeeds(s);
		refreshWhere(s);
		if (s == null)
		{
			selected.setText("Nothing selected");
			return;
		}
		String which = "Suggestion: ";
		if (teleport)
		{
			which = (s.teleport.isNetwork ? "Suggested network stop" : "Suggested teleport") + " (" + s.teleport.source + ")"
				+ (s.draft != null ? ", moved" : "") + ": ";
		}
		if (candidate && ctx.transports != null)
		{
			List<Suggestion> all = ctx.transports.suggestionsFor(s.candidate);
			nextSug.setEnabled(all.size() > 1);
			if (s.suggestion == null)
			{
				which = "No suggestion: ";
			}
			else if (all.size() > 1)
			{
				which = "Suggestion " + (all.indexOf(s.suggestion) + 1) + " of " + all.size()
					+ planeNote(s.candidate.getPlane(), s.suggestion.getDestPlane()) + ": ";
			}
			if (ctx.effectiveTestLater().containsKey(s.candidate.id()))
			{
				which = "[on the test list] " + which;
			}
		}
		selected.setText((record ? (s.pending ? "Pending: " : "Selected: ") : which) + s.name() + wayNote(s)
			+ (record ? categoryNote(s.record.category) : ""));
		if (record)
		{
			updating = true;
			kind.setSelectedItem(s.record.kind);
			ticks.setValue(s.record.ticks);
			name.setText(s.record.name);
			updating = false;
		}
	}

	/** " (two-way)" or " (one-way)" for the selection, as it would be made; empty for teleports or nothing to show. */
	private String wayNote(EditorContext.Selection s)
	{
		TransportRecord r = s.record != null ? s.record : s.draft;
		TransportRecord.Kind k;
		if (r != null)
		{
			k = r.kind;
		}
		else if (s.suggestion != null)
		{
			k = ctx.newKind == TransportRecord.Kind.TELEPORT ? TransportRecord.Kind.TWO_WAY : ctx.newKind;
		}
		else
		{
			return "";
		}
		return k == TransportRecord.Kind.TWO_WAY ? " (two-way)" : k == TransportRecord.Kind.ONE_WAY ? " (one-way)" : "";
	}

	/** ", category: Climb up" for a transport's category (its label when it has one). */
	private static String categoryNote(String category)
	{
		if (TransportRecord.NEEDS_FIX.equals(category))
		{
			return ", category: NEEDS FIXING";
		}
		for (TransportCandidate.Category c : TransportCandidate.Category.values())
		{
			if (c.name().equals(category))
			{
				return ", category: " + c.getLabel();
			}
		}
		return category == null || category.isEmpty() ? "" : ", category: " + category.charAt(0) + category.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String planeNote(int from, int to)
	{
		return to > from ? " (up to plane " + to + ")" : to < from ? " (down to plane " + to + ")" : " (same plane)";
	}

	private void applyToSelected()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null || s.record == null)
		{
			return;
		}
		TransportRecord r = s.record.copy();
		TransportRecord.Kind k = (TransportRecord.Kind) kind.getSelectedItem();
		if (r.kind != TransportRecord.Kind.NETWORK && (k == TransportRecord.Kind.TELEPORT || r.kind != TransportRecord.Kind.TELEPORT))
		{
			r.kind = k;
		}
		r.ticks = (Integer) ticks.getValue();
		r.ticksEstimate = false;
		if (!name.getText().isBlank())
		{
			r.name = name.getText().trim();
		}
		ctx.pendTransport(r);
		ctx.select(EditorContext.Selection.ofRecord(r, true), null, false);
	}

	private void reverseSelected()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null || s.record == null || !s.record.hasStart())
		{
			return;
		}
		TransportRecord r = s.record.copy();
		r.startX = s.record.endX;
		r.startY = s.record.endY;
		r.startPlane = s.record.endPlane;
		r.endX = s.record.startX;
		r.endY = s.record.startY;
		r.endPlane = s.record.startPlane;
		if (s.record.requirementsBack != null)
		{
			r.requirements = s.record.requirementsBack;
			r.requirementsBack = s.record.requirements;
		}
		ctx.pendTransport(r);
		ctx.select(EditorContext.Selection.ofRecord(r, true), null, false);
	}

	private void editRequirements()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null || s.record == null)
		{
			return;
		}
		TransportRecord r = RequirementsDialog.edit(this, s.record);
		if (r != null)
		{
			ctx.pendTransport(r);
			ctx.select(EditorContext.Selection.ofRecord(r, true), null, false);
		}
	}
}

package com.bestpath.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * The RuneLite config panel's settings in the sidebar's Settings tab: one control per {@link ConfigList} item, with the
 * config panel's name (its description on mouse-over), in the config panel's order and sections. A tick box for on /
 * off, a number box with arrows for a number (kept in the config panel's range), a list for a choice and a colour
 * square that opens RuneLite's colour picker. A change is written to RuneLite's config
 * ({@link PanelActions#setConfig}); {@link #configChanged} shows the config's values again (the config panel changed
 * one) without writing anything back. Swing thread only.
 */
final class ConfigMirror
{
	private final PanelActions actions;
	/** Each item's control, by config key. */
	final Map<String, JComponent> controls = new LinkedHashMap<>();
	/** Each item's value as shown (as RuneLite stores it): a control's change is written only when it differs. */
	private final Map<String, String> shown = new HashMap<>();
	/** Each section's fold: its heading (with the fold arrow) and its rows. */
	private final Map<ConfigList.Section, JLabel> foldLabels = new HashMap<>();
	private final Map<ConfigList.Section, JPanel> foldBodies = new HashMap<>();
	/** True while the controls are set from the config (no changes written then). */
	private boolean updating;

	ConfigMirror(PanelActions actions)
	{
		this.actions = actions;
	}

	/**
	 * The rows of a section: {@code null} gives the config panel's top items (no heading), a section gives its heading
	 * (click to fold or unfold, as "Your answers"; open unless the config panel folds it at first) and its rows.
	 */
	JPanel part(ConfigList.Section section)
	{
		JPanel rows = column();
		boolean first = true;
		for (ConfigList.Item item : ConfigList.in(section))
		{
			if (!first)
			{
				rows.add(Box.createVerticalStrut(2));
			}
			first = false;
			rows.add(Ui.left(row(item)));
		}
		if (section == null)
		{
			return rows;
		}
		JPanel part = column();
		JLabel header = FoldArrow.heading(section.name, true);
		header.setToolTipText(section.description + ". Click to show or hide.");
		JLabel fold = header;
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggle(section);
			}
		});
		part.add(Ui.left(header));
		part.add(Box.createVerticalStrut(3));
		part.add(rows);
		foldLabels.put(section, fold);
		foldBodies.put(section, rows);
		showFold(section, !section.closedByDefault);
		return part;
	}

	/** Folds or unfolds a section (kept until the plugin restarts, as "Your answers"). */
	void toggle(ConfigList.Section section)
	{
		showFold(section, !isOpen(section));
	}

	/** True when the section's rows are shown. */
	boolean isOpen(ConfigList.Section section)
	{
		JPanel body = foldBodies.get(section);
		return body != null && body.isVisible();
	}

	private void showFold(ConfigList.Section section, boolean open)
	{
		JPanel body = foldBodies.get(section);
		if (body == null)
		{
			return;
		}
		FoldArrow.set(foldLabels.get(section), open);
		body.setVisible(open);
		body.revalidate();
		body.repaint();
	}

	/** Shows every item's value from RuneLite's config ({@link PanelActions#config}; none stored: its default). */
	void configChanged()
	{
		updating = true;
		try
		{
			for (ConfigList.Item item : ConfigList.ITEMS)
			{
				JComponent c = controls.get(item.key);
				if (c == null)
				{
					continue;
				}
				Object v = ConfigList.fromConfig(item, actions.config(item.key));
				shown.put(item.key, ConfigList.toConfig(v));
				switch (item.kind)
				{
					case BOOLEAN:
						((JCheckBox) c).setSelected((Boolean) v);
						break;
					case INT:
						((JSpinner) c).setValue(v);
						break;
					case COLOUR:
						((Swatch) c).setColour((Color) v);
						break;
					case ENUM:
					default:
						((JComboBox<?>) c).setSelectedItem(v);
						break;
				}
			}
		}
		finally
		{
			updating = false;
		}
	}

	/** A control changed: written to the config when it is a new value (never while the controls are being set). */
	private void change(ConfigList.Item item, Object value)
	{
		if (updating || value == null)
		{
			return;
		}
		String s = ConfigList.toConfig(value);
		if (s.equals(shown.get(item.key)))
		{
			return;
		}
		shown.put(item.key, s);
		actions.setConfig(item.key, s);
	}

	private JComponent row(ConfigList.Item item)
	{
		switch (item.kind)
		{
			case BOOLEAN:
			{
				JCheckBox box = Ui.checkBox("");
				box.setSelected((Boolean) item.defaultValue);
				box.addActionListener(e -> change(item, box.isSelected()));
				box.setName("config:" + item.key);
				controls.put(item.key, box);
				return new Row(box, item.name, null, item.description);
			}
			case INT:
			{
				JSpinner spinner = new JSpinner(new SpinnerNumberModel((int) (Integer) item.defaultValue, item.min, item.max, 1));
				Ui.smallSpinner(spinner, 3, item.description + " (" + item.min + " to " + item.max + ")");
				spinner.addChangeListener(e -> change(item, spinner.getValue()));
				spinner.setName("config:" + item.key);
				controls.put(item.key, spinner);
				return new Row(null, item.name, spinner, item.description);
			}
			case COLOUR:
			{
				Swatch swatch = new Swatch((Color) item.defaultValue);
				swatch.setToolTipText(item.description + ". Click to choose the colour.");
				swatch.addActionListener(e -> actions.pickColour(swatch, swatch.getColour(), item.name, c ->
				{
					if (c != null)
					{
						swatch.setColour(c);
						change(item, c);
					}
				}));
				swatch.setName("config:" + item.key);
				controls.put(item.key, swatch);
				return new Row(null, item.name, swatch, item.description);
			}
			case ENUM:
			default:
			{
				JComboBox<Enum<?>> combo = new JComboBox<>(item.options.toArray(new Enum<?>[0]));
				combo.setFont(Ui.small());
				combo.setFocusable(false);
				combo.setSelectedItem(item.defaultValue);
				combo.setToolTipText(item.description);
				combo.addActionListener(e -> change(item, combo.getSelectedItem()));
				combo.setName("config:" + item.key);
				controls.put(item.key, combo);
				return new Row(null, item.name, combo, item.description);
			}
		}
	}

	private static JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		p.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		return p;
	}

	/**
	 * One setting: a tick box at the left (or none), the config panel's name wrapping beside it, and its number box,
	 * list or colour square at the right; the controls sit centred on the first line of text (as the Settings tab's
	 * other rows). A click on the name ticks the box, as a checkbox's own text would.
	 */
	static final class Row extends JPanel
	{
		static final int GAP = 4;

		private final JComponent leftControl;
		private final JComponent rightControl;
		private final WrapLabel label;

		Row(JComponent leftControl, String text, JComponent rightControl, String tooltip)
		{
			super(null);
			this.leftControl = leftControl;
			this.rightControl = rightControl;
			setOpaque(false);
			int textW = Ui.CONTENT_WIDTH - width(leftControl) - width(rightControl);
			label = Ui.wrapped(text, textW, Ui.small(), Ui.TEXT);
			label.setToolTipText(tooltip);
			setToolTipText(tooltip);
			add(label);
			if (leftControl != null)
			{
				add(leftControl);
				leftControl.setToolTipText(tooltip);
				if (leftControl instanceof JCheckBox)
				{
					MouseAdapter click = Ui.clickTicks((JCheckBox) leftControl);
					addMouseListener(click);
					label.addMouseListener(click);
				}
			}
			if (rightControl != null)
			{
				add(rightControl);
			}
		}

		/** A control's width with its gap to the text (0 without one). */
		private static int width(JComponent c)
		{
			return c == null ? 0 : c.getPreferredSize().width + GAP;
		}

		private int lineHeight()
		{
			return label.getFontMetrics(label.getFont()).getHeight();
		}

		private int firstLine()
		{
			int h = lineHeight();
			if (leftControl != null)
			{
				h = Math.max(h, leftControl.getPreferredSize().height);
			}
			if (rightControl != null)
			{
				h = Math.max(h, rightControl.getPreferredSize().height);
			}
			return h;
		}

		@Override
		public Dimension getPreferredSize()
		{
			int first = firstLine();
			int textTop = (first - lineHeight()) / 2;
			Dimension l = label.getPreferredSize();
			return new Dimension(width(leftControl) + l.width + width(rightControl), Math.max(first, textTop + l.height));
		}

		@Override
		public Dimension getMinimumSize()
		{
			return getPreferredSize();
		}

		@Override
		public void doLayout()
		{
			int first = firstLine();
			int x = 0;
			if (leftControl != null)
			{
				Dimension b = leftControl.getPreferredSize();
				leftControl.setBounds(0, (first - b.height) / 2, b.width, b.height);
				x = b.width + GAP;
			}
			int right = getWidth();
			if (rightControl != null)
			{
				Dimension r = rightControl.getPreferredSize();
				right -= r.width;
				rightControl.setBounds(right, (first - r.height) / 2, r.width, r.height);
				right -= GAP;
			}
			label.setBounds(x, (first - lineHeight()) / 2, Math.max(0, right - x), label.getPreferredSize().height);
		}
	}

	/** A colour setting's square: the colour, framed; a click opens RuneLite's colour picker. */
	static final class Swatch extends JButton
	{
		static final int W = 36;
		static final int H = 16;

		private Color colour;

		Swatch(Color colour)
		{
			this.colour = colour;
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		Color getColour()
		{
			return colour;
		}

		void setColour(Color c)
		{
			colour = c;
			repaint();
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(W, H);
		}

		@Override
		public Dimension getMinimumSize()
		{
			return getPreferredSize();
		}

		@Override
		public Dimension getMaximumSize()
		{
			return getPreferredSize();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue()));
			g2.fillRect(1, 1, getWidth() - 2, getHeight() - 2);
			g2.setColor(getModel().isRollover() ? Ui.TITLE : Ui.MUTED);
			g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			g2.dispose();
		}
	}
}

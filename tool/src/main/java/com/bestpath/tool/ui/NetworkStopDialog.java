package com.bestpath.tool.ui;

import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.PathfinderDiagnostics;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.req.CacheNames;
import com.bestpath.tool.req.ItemGroupFile;
import com.bestpath.tool.req.Networks;
import com.bestpath.tool.req.RequirementWords;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The Networks tab's "Add a stop..." form: a new stop's name, tile, ride ticks, what it needs on top of the network's
 * requirement, and where it comes from. The tile and the requirement are checked while typing.
 */
final class NetworkStopDialog
{
	/** Start of a label that wraps at the dialog's width. */
	private static final String WRAP = "<html><div style='width:560px'>";

	private static final String[] SIDE_NAMES = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

	/** Its pathfinder (null while the map is being built) checks the stop's tile. */
	private final EditorContext ctx;
	private final Networks.Network net;
	/** The tile last clicked on the map, or null. */
	private final int[] clickedTile;
	private final JTextField stopName;
	private final JSpinner sx;
	private final JSpinner sy;
	private final JSpinner sp;
	private final JSpinner ticks;
	private final JTextField ownNeeds = new JTextField(36);
	private final JLabel ownCheck = new JLabel(" ");
	private final JTextField source = new JTextField(36);
	private final JLabel tileCheck = new JLabel(" ");

	/** {@code usualTicks}: the ride ticks the form starts with. */
	NetworkStopDialog(EditorContext ctx, Networks.Network net, int[] clickedTile, int usualTicks)
	{
		this.ctx = ctx;
		this.net = net;
		this.clickedTile = clickedTile;
		int[] t = clickedTile != null ? clickedTile : new int[]{3222, 3218, 0};
		stopName = new JTextField(net.name + ": ", 36);
		sx = new JSpinner(new SpinnerNumberModel(t[0], 0, 16383, 1));
		sy = new JSpinner(new SpinnerNumberModel(t[1], 0, 16383, 1));
		sp = new JSpinner(new SpinnerNumberModel(t[2], 0, 3, 1));
		ticks = new JSpinner(new SpinnerNumberModel(usualTicks, 0, 1000, 1));
		sx.addChangeListener(e -> recheck());
		sy.addChangeListener(e -> recheck());
		sp.addChangeListener(e -> recheck());
		ownNeeds.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				recheck();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				recheck();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				recheck();
			}
		});
		recheck();
	}

	/**
	 * Shows the form until it is cancelled or filled in so it can be read; the new stop (see
	 * {@link NetworksPanel#newStop}), or null when cancelled.
	 */
	TransportRecord ask(Component parent)
	{
		JPanel form = form();
		while (true)
		{
			int ok = JOptionPane.showConfirmDialog(parent, form, "Add a stop to " + net.name, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
			if (ok != JOptionPane.OK_OPTION)
			{
				return null;
			}
			String problem = problem();
			if (problem == null)
			{
				break;
			}
			JOptionPane.showMessageDialog(parent, problem, "Add a stop", JOptionPane.WARNING_MESSAGE);
		}
		int[] tile = {(Integer) sx.getValue(), (Integer) sy.getValue(), (Integer) sp.getValue()};
		return NetworksPanel.newStop(net.name, stopName.getText(), tile, (Integer) ticks.getValue(),
			CacheNames.tidyQuestNames(ownNeeds.getText()), source.getText());
	}

	/** What is wrong with the filled-in form, or null when the stop can be made. */
	private String problem()
	{
		String name = stopName.getText().trim();
		if (name.isEmpty() || name.equalsIgnoreCase(net.name + ":") || name.contains("\t"))
		{
			return "Give the stop a name (e.g. " + net.name + ": CKS (Canifis)).";
		}
		try
		{
			ItemGroupFile.parse(ownNeeds.getText());
		}
		catch (RequirementText.ParseException ex)
		{
			return "\"Also needs\" can't be read: " + ex.getMessage();
		}
		return null;
	}

	private JPanel form()
	{
		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 4, 3, 4);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		NetworksPanel.row(form, c, 0, "Name:", stopName);
		NetworksPanel.row(form, c, 1, "", new JLabel("e.g. " + net.name + ": CKS (Canifis)  (the code and the place, as players know it)"));
		JPanel tileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		tileRow.add(new JLabel("x"));
		tileRow.add(sx);
		tileRow.add(new JLabel("y"));
		tileRow.add(sy);
		tileRow.add(new JLabel("plane"));
		tileRow.add(sp);
		tileRow.add(new JLabel(clickedTile != null ? "  (the tile you clicked last on the map)" : "  (click the map first to fill these in)"));
		NetworksPanel.row(form, c, 2, "Tile:", tileRow);
		NetworksPanel.row(form, c, 3, "", tileCheck);
		JPanel ticksRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		ticksRow.add(ticks);
		ticksRow.add(new JLabel("  ticks for a ride to this stop (+ " + net.extraTicks + " extra ticks of every " + net.name + " ride)"));
		NetworksPanel.row(form, c, 4, "Ride:", ticksRow);
		NetworksPanel.row(form, c, 5, "Also needs:", ownNeeds);
		NetworksPanel.row(form, c, 6, "", ownCheck);
		NetworksPanel.row(form, c, 7, "", new JLabel(WRAP + "Only what THIS stop needs (e.g. quest Lost City; varbit 1234 &gt;= 5). Every ride already needs: "
			+ NetworksPanel.inWords(net.requirement).replace("&", "&amp;").replace("<", "&lt;") + ". Empty: nothing more.</html>"));
		NetworksPanel.row(form, c, 8, "Comes from:", source);
		NetworksPanel.row(form, c, 9, "", new JLabel("Where you got it (e.g. Wiki: Fairy ring, or \"seen in game\"): kept with the stop."));
		return form;
	}

	/** Checks the tile and the "Also needs" line, and shows what each means. */
	private void recheck()
	{
		Object[] d = describeTile((Integer) sx.getValue(), (Integer) sy.getValue(), (Integer) sp.getValue());
		tileCheck.setText((String) d[0]);
		tileCheck.setForeground((Color) d[1]);
		try
		{
			Requirement r = ItemGroupFile.parse(ownNeeds.getText());
			List<String> unknown = CacheNames.unknownNames(r);
			List<String> words = RequirementWords.parts(r);
			ownCheck.setForeground(unknown.isEmpty() ? new Color(0, 120, 0) : new Color(190, 100, 0));
			ownCheck.setText((unknown.isEmpty() ? "" : "unknown " + String.join(", ", unknown) + ": ")
				+ (words.isEmpty() ? "nothing more than every ride needs" : String.join("   •   ", words)));
		}
		catch (RequirementText.ParseException ex)
		{
			ownCheck.setForeground(Color.RED);
			ownCheck.setText(ex.getMessage());
		}
	}

	/** What routes will do with a stop on this tile, in words (and whether that is fine): {text, colour}. */
	private Object[] describeTile(int x, int y, int plane)
	{
		Pathfinder pf = ctx.pathfinder;
		if (pf == null)
		{
			return new Object[]{"The map is still being built: the tile can't be checked yet.", Color.DARK_GRAY};
		}
		if (pf.getGraph().componentAt(x, y, plane) >= 0)
		{
			return new Object[]{"This tile can be walked on: routes use the stop right on it. Fine.", new Color(0, 120, 0)};
		}
		int[][] b = PathfinderDiagnostics.besideTiles(pf, x, y, plane);
		if (b[0].length == 0)
		{
			return new Object[]{WRAP + "This tile is solid and none of its four sides can be walked on: routes can't board this stop "
				+ "(only a transport landing on this very tile can use it). Check the tile (for a fairy ring: the middle of the ring).</html>",
				new Color(190, 100, 0)};
		}
		List<String> sides = new ArrayList<>();
		for (int t : b[0])
		{
			int dx = Tiles.x(t) - x;
			int dy = Tiles.y(t) - y;
			sides.add(SIDE_NAMES[dx == 0 ? (dy > 0 ? 0 : 4) : dx > 0 ? 2 : 6]);
		}
		return new Object[]{WRAP + "This tile is solid (e.g. the middle of a fairy ring): routes board the stop from the "
			+ String.join(", ", sides) + " side and, arriving, step off it to one of " + b[1].length + " tiles beside it. Fine.</html>",
			new Color(0, 120, 0)};
	}
}

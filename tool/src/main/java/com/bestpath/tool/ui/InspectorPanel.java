package com.bestpath.tool.ui;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.DockDiagnostics;
import com.bestpath.core.sea.SeaArea;
import com.bestpath.core.sea.SeaGraph;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import com.bestpath.tool.extract.FlagGrid;
import com.bestpath.tool.extract.SeaExtractor;
import com.bestpath.tool.geo.Regions;
import com.bestpath.tool.map.ChannelLayer;
import com.bestpath.tool.transport.CacheTableTeleports;
import com.bestpath.tool.transport.Suggestion;
import com.bestpath.tool.transport.TransportCandidate;
import com.bestpath.tool.transport.TransportData;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Right-hand panel: details of the selected tile and its region. */
final class InspectorPanel extends JPanel
{
	private final JTextArea text = new JTextArea();
	private final JButton copy = new JButton("Copy coordinates");
	private String coords = "";
	/** The sea graph whose channels a clicked tile may be on, or null. */
	private SeaGraph seaGraph;

	void setSeaGraph(SeaGraph g)
	{
		seaGraph = g;
	}

	InspectorPanel()
	{
		super(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setPreferredSize(new Dimension(260, 100));
		JLabel title = new JLabel("Inspector");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		add(title, BorderLayout.NORTH);
		text.setEditable(false);
		text.setOpaque(true);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		text.setText("Click a tile on the map to see its details.");
		add(new JScrollPane(text), BorderLayout.CENTER);
		copy.setEnabled(false);
		copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(coords), null));
		add(copy, BorderLayout.SOUTH);
	}

	private static String describeBlock(int f)
	{
		StringBuilder sb = new StringBuilder();
		if ((f & CollisionFlags.OBJECT) != 0)
		{
			sb.append("object ");
		}
		if ((f & CollisionFlags.FLOOR) != 0)
		{
			sb.append("floor ");
		}
		if ((f & CollisionFlags.FLOOR_DECORATION) != 0)
		{
			sb.append("decoration ");
		}
		if ((f & CollisionFlags.NO_FLOOR) != 0)
		{
			sb.append("no-floor ");
		}
		return sb.length() == 0 ? "nothing" : sb.toString().trim();
	}

	private static String describeWalls(int f)
	{
		String[] names = {"NW-corner", "N", "NE-corner", "E", "SE-corner", "S", "SW-corner", "W"};
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8; i++)
		{
			if ((f >> i & 1) != 0)
			{
				sb.append(names[i]).append(' ');
			}
		}
		return sb.length() == 0 ? "none" : sb.toString().trim();
	}

	private static String describeSteps(FlagGrid g, int x, int y, int plane)
	{
		int[][] dirs = {{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}};
		String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8; i++)
		{
			if (MovementRules.canStep(g.plane(plane), x, y, dirs[i][0], dirs[i][1]))
			{
				sb.append(names[i]).append(' ');
			}
		}
		return sb.length() == 0 ? "none" : sb.toString().trim();
	}

	/** Whether the tile is on or next to the dock's buoy or land object (the stand-on tiles are next to it). */
	private static boolean nearDock(Dock d, int x, int y, int plane)
	{
		if (plane != DockDiagnostics.landPlane(d))
		{
			return false;
		}
		boolean buoy = x >= d.getBuoyX() - 1 && x <= d.getBuoyX() + d.getBuoySize() && y >= d.getBuoyY() - 1 && y <= d.getBuoyY() + d.getBuoySize();
		boolean land = x >= DockDiagnostics.landX(d) - 1 && x <= DockDiagnostics.landX(d) + DockDiagnostics.landSize(d) && y >= DockDiagnostics.landY(d) - 1 && y <= DockDiagnostics.landY(d) + DockDiagnostics.landSize(d);
		return buoy || land;
	}

	void showTile(int x, int y, int plane, Set<Integer> regionIds, FlagGrid grid,
		SeaMap sea, SeaPieces pieces, List<Dock> docks, CoarseGraph coarse, TransportData transports, boolean[] hidden,
		boolean[] pockets)
	{
		coords = x + ", " + y + ", " + plane;
		StringBuilder sb = new StringBuilder();
		// a sea channel clicked comes first, where it is seen without scrolling
		String channel = ChannelLayer.describeAt(seaGraph, x, y, plane);
		if (!channel.isEmpty())
		{
			sb.append(channel.substring(1)).append('\n');
		}
		appendTile(sb, x, y, regionIds);
		appendMovement(sb, grid, x, y, plane);
		if (sea != null)
		{
			appendSea(sb, sea, pieces, x, y, plane);
		}
		if (docks != null)
		{
			for (Dock d : docks)
			{
				if (nearDock(d, x, y, plane))
				{
					appendDock(sb, d, pieces);
				}
			}
		}
		if (coarse != null)
		{
			appendComponent(sb, coarse, hidden, pockets, x, y, plane);
		}
		if (transports != null)
		{
			appendTransports(sb, transports, x, y, plane);
		}
		text.setText(sb.toString());
		text.setCaretPosition(0);
		copy.setEnabled(true);
	}

	/** The tile's coordinates and its region. */
	private void appendTile(StringBuilder sb, int x, int y, Set<Integer> regionIds)
	{
		int id = Regions.idOfTile(x, y);
		sb.append("Tile\n");
		sb.append("  x, y, plane  ").append(coords).append('\n');
		sb.append('\n');
		sb.append("Region (cell)\n");
		sb.append("  id           ").append(id).append('\n');
		sb.append("  region x, y  ").append(Regions.regionX(id)).append(", ").append(Regions.regionY(id)).append('\n');
		sb.append("  base x, y    ").append(Regions.baseX(id)).append(", ").append(Regions.baseY(id)).append('\n');
		sb.append("  local x, y   ").append(x & 63).append(", ").append(y & 63).append('\n');
		sb.append("  chunk x, y   ").append((x & 63) >> 3).append(", ").append((y & 63) >> 3).append('\n');
		sb.append("  in cache     ").append(regionIds.contains(id) ? "yes" : "no").append('\n');
		sb.append('\n');
	}

	/** What blocks the tile, its walls and the steps a player can take from it. */
	private static void appendMovement(StringBuilder sb, FlagGrid grid, int x, int y, int plane)
	{
		sb.append("Movement\n");
		if (grid == null)
		{
			sb.append("  (walls are still being extracted)\n");
		}
		else
		{
			int f = grid.get(x, y, plane);
			sb.append("  blocked by   ").append(describeBlock(f)).append('\n');
			sb.append("  walls        ").append(describeWalls(f)).append('\n');
			sb.append("  can step     ").append(describeSteps(grid, x, y, plane)).append('\n');
		}
	}

	/** Whether the tile is sea, its sea area and which piece of sea it belongs to. */
	private static void appendSea(StringBuilder sb, SeaMap sea, SeaPieces pieces, int x, int y, int plane)
	{
		int tex = sea.texture(x, y, plane);
		String kind = tex == SeaExtractor.SHALLOWS ? "yes, coastal shallows"
			: tex == 208 ? "yes, beyond the world edge (texture 208)"
			: "yes, water texture " + tex + " (family " + (130 + (tex - 130) / 5 * 5) + "-" + (134 + (tex - 130) / 5 * 5) + ")";
		sb.append("  sea          ").append(tex < 0 ? "no" : kind + "\n               (closed to walking: boats only)").append('\n');
		SeaArea area = SeaMap.areaOf(tex);
		if (area == null)
		{
			return;
		}
		sb.append("  sea area     ").append(area.getLabel()).append('\n');
		if (area.getNeeds() != null)
		{
			sb.append("  boat needs   ").append(area.getNeeds()).append('\n');
		}
		if (pieces != null)
		{
			int piece = pieces.pieceAt(x, y, plane);
			sb.append("  sea piece    ").append(piece < 0 ? "none (blocked: boats cannot pass)"
				: piece == pieces.mainPiece() ? String.format(Locale.ROOT, "main ocean (%,d tiles)", pieces.size(piece))
				: String.format(Locale.ROOT, "cut off from the main ocean\n               by blocked tiles (%,d tiles)", pieces.size(piece))).append('\n');
		}
	}

	/** A sailing dock next to the tile: its name, needs, shipwright and both sides. */
	private static void appendDock(StringBuilder sb, Dock d, SeaPieces pieces)
	{
		sb.append('\n');
		sb.append("Sailing dock\n");
		sb.append("  name         ").append(d.getName()).append(" (dock ").append(d.getId()).append(")\n");
		sb.append("  needs        Sailing ").append(DockDiagnostics.requirementText(d)).append('\n');
		if (d.getShipwright() != null)
		{
			sb.append("  shipwright   ").append(d.getShipwright()).append(" (Recover-boat brings your boat here for coins)\n");
			int[] w = d.getShipwrightTile();
			if (w != null)
			{
				sb.append("  shipwright at ").append(w[0]).append(", ").append(w[1]).append(", ").append(w[2])
					.append(" (Wiki; routes walk next to it first)\n");
			}
		}
		sb.append("  land side    ").append(DockDiagnostics.label(DockDiagnostics.kind(d))).append(" at ").append(DockDiagnostics.landX(d)).append(", ")
			.append(DockDiagnostics.landY(d)).append(", ").append(DockDiagnostics.landPlane(d)).append('\n');
		sb.append("  sea side     buoy at ").append(d.getBuoyX()).append(", ").append(d.getBuoyY()).append(", 0\n");
		sb.append("  stand on     ");
		if (d.getLandTiles().isEmpty())
		{
			sb.append("(no walkable land tile found)");
		}
		for (int[] t : d.getLandTiles())
		{
			sb.append(t[0]).append(", ").append(t[1]).append("  ");
		}
		sb.append('\n');
		if (pieces != null && !DockDiagnostics.onMainOcean(d, pieces))
		{
			sb.append("  NOT on the main ocean (the buoy's water\n  is cut off by blocked tiles)\n");
		}
	}

	/** The sub-region the tile is in, and whether it is left out of the plugin's data. */
	private static void appendComponent(StringBuilder sb, CoarseGraph coarse, boolean[] hidden, boolean[] pockets,
		int x, int y, int plane)
	{
		sb.append('\n');
		sb.append("Sub-region (component)\n");
		int comp = coarse.componentAt(x, y, plane);
		if (comp < 0)
		{
			sb.append("  none (not walkable)\n");
			return;
		}
		int ents = coarse.entrancesPerComponent()[comp];
		sb.append("  id           ").append(comp).append('\n');
		sb.append("  tiles        ").append(coarse.componentSize(comp)).append('\n');
		sb.append("  entrances    ").append(ents).append('\n');
		if (hidden != null && comp < hidden.length && hidden[comp])
		{
			sb.append("  hidden by you: shown red on the\n  \"Ignored sub-regions\" layer,\n  left out of the plugin's data\n");
		}
		else if (pockets != null && comp < pockets.length && pockets[comp])
		{
			sb.append("  closed-off pocket: shown grey on the\n  \"Ignored sub-regions\" layer,\n  left out of the plugin's data\n");
		}
		else if (coarse.pockets()[comp])
		{
			sb.append("  closed-off pocket kept by you:\n  in the plugin's data\n");
		}
		else if (ents == 0)
		{
			sb.append("  (no walking way out of this region;\n   needs a door, stairs or other transport)\n");
		}
	}

	/** Transport candidates on or next to the tile, and suggested teleport destinations there. */
	private static void appendTransports(StringBuilder sb, TransportData transports, int x, int y, int plane)
	{
		for (TransportCandidate c : transports.near(x, y, plane, 1))
		{
			appendCandidate(sb, c, transports.suggestionsFor(c));
		}
		for (CacheTableTeleports.Destination d : transports.getDestinations())
		{
			if (d.plane == plane && Math.abs(d.x - x) <= 1 && Math.abs(d.y - y) <= 1)
			{
				sb.append('\n');
				sb.append(d.isNetwork ? "Suggested network stop\n" : "Suggested teleport\n");
				sb.append("  ").append(d.label()).append('\n');
				sb.append("  source       ").append(d.source).append('\n');
				sb.append("  at          ").append(d.x).append(", ").append(d.y).append(", ").append(d.plane).append('\n');
			}
		}
	}

	/** One transport candidate with up to three suggested destinations and why each was suggested. */
	private static void appendCandidate(StringBuilder sb, TransportCandidate c, List<Suggestion> sug)
	{
		sb.append('\n');
		sb.append("Transport candidate\n");
		sb.append("  ").append(c.getName()).append("  (").append(c.getCategory().getLabel()).append(")\n");
		sb.append("  object       ").append(c.getObjectId()).append(" at ").append(c.getX()).append(", ")
			.append(c.getY()).append(", ").append(c.getPlane()).append('\n');
		sb.append("  size         ").append(c.getSizeX()).append(" x ").append(c.getSizeY()).append('\n');
		sb.append("  options      ").append(String.join(", ", c.getOptions())).append('\n');
		if (c.getVarbit() >= 0 || c.getVarp() >= 0)
		{
			sb.append("  changes with ").append(c.getVarbit() >= 0 ? "varbit " + c.getVarbit() : "varp " + c.getVarp()).append('\n');
		}
		if (sug.isEmpty())
		{
			sb.append("  no suggestion\n");
		}
		int n = 0;
		for (Suggestion s : sug)
		{
			if (++n > 3)
			{
				break;
			}
			sb.append("  suggestion ").append(n).append(": ").append(s.getDestX()).append(", ").append(s.getDestY())
				.append(", ").append(s.getDestPlane()).append('\n');
			sb.append("    rule: ").append(s.getRule().getDescription()).append('\n');
			for (String ev : s.getEvidence())
			{
				sb.append("    - ").append(ev).append('\n');
			}
		}
	}
}

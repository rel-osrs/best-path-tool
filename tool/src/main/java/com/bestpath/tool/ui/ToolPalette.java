package com.bestpath.tool.ui;

import com.bestpath.tool.editor.BlockTool;
import com.bestpath.tool.editor.DoorTool;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.editor.EditorTool;
import com.bestpath.tool.editor.FreeLandTool;
import com.bestpath.tool.editor.HideTool;
import com.bestpath.tool.editor.IgnoreRegionTool;
import com.bestpath.tool.editor.MeasureTool;
import com.bestpath.tool.editor.PointerTool;
import com.bestpath.tool.editor.RouteTool;
import com.bestpath.tool.editor.TransportTool;
import com.bestpath.tool.editor.WallTool;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.route.RouteController;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.ButtonGroup;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;

/** The editing tools as a vertical palette left of the maps (like an image editor); one tool is active at a time. */
final class ToolPalette extends JToolBar
{
	private final List<EditorTool> tools = new ArrayList<>();
	private final Map<EditorTool, JToggleButton> buttons = new HashMap<>();
	private final TransportTool transportTool;
	/** Applies a change to both map views. */
	private final Consumer<Consumer<MapView>> forViews;
	/** Told about every tool picked, after the maps use it. */
	private final Consumer<EditorTool> onSelect;
	private EditorTool active;

	ToolPalette(EditorContext ctx, RouteController routeController, Consumer<Consumer<MapView>> forViews,
		Consumer<EditorTool> onSelect)
	{
		super(JToolBar.VERTICAL);
		this.forViews = forViews;
		this.onSelect = onSelect;
		transportTool = new TransportTool(ctx);
		tools.add(new PointerTool(ctx, transportTool));
		tools.add(transportTool);
		tools.add(new WallTool(ctx));
		tools.add(new BlockTool(ctx));
		tools.add(new DoorTool(ctx));
		tools.add(new HideTool(ctx));
		tools.add(new IgnoreRegionTool(ctx));
		tools.add(new FreeLandTool(ctx));
		tools.add(new RouteTool(ctx, routeController));
		tools.add(new MeasureTool(ctx));
		setFloatable(false);
		ButtonGroup group = new ButtonGroup();
		for (EditorTool t : tools)
		{
			JToggleButton b = new JToggleButton("<html><center>" + t.getName().replace(" ", "<br>") + "<br><small>("
				+ Character.toUpperCase(t.getKey()) + ")</small></center></html>");
			b.setToolTipText(t.getHelp() + "  Shortcut: " + Character.toUpperCase(t.getKey()));
			b.setFocusable(false);
			b.setMaximumSize(new Dimension(84, 60));
			b.addActionListener(e -> select(t));
			group.add(b);
			buttons.put(t, b);
			add(b);
		}
	}

	/** The tools in palette order (the first is the Pointer). */
	List<EditorTool> getTools()
	{
		return tools;
	}

	TransportTool getTransportTool()
	{
		return transportTool;
	}

	/** Makes {@code t} the active tool in both views and presses its button. */
	void select(EditorTool t)
	{
		if (active != null && active != t)
		{
			active.deactivate();
		}
		active = t;
		JToggleButton b = buttons.get(t);
		if (b != null)
		{
			b.setSelected(true);
		}
		forViews.accept(v -> v.setInteraction(t, t.getCursor()));
		onSelect.accept(t);
	}
}

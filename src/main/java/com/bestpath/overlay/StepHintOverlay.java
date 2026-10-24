package com.bestpath.overlay;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteStep;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * The next step in the game world: the object or NPC to click glows in the "Glow colour" and the step's words float
 * above it ("Climb-up Ladder"); with nothing in the world to click (a teleport, sailing) the words float above the
 * player ("Cast Varrock Teleport") in their own colour (white by default). Both are on by default and can be switched
 * off. Reads only {@link StepTargets}, worked out once a tick.
 */
public class StepHintOverlay extends Overlay
{

	/** How high above the ground the words float over a tile (local units; a tile is 128). */
	private static final int TEXT_HEIGHT = 150;
	private static final int OUTLINE_FEATHER = 4;
	/** The "Glow thickness" setting. */
	private final IntSupplier glowWidth;
	/** The "Text size" and "Bold text" settings. */
	private final IntSupplier textSize;
	private final BooleanSupplier bold;
	/** The font last made, and what it was made from (made again only when one of them changes). */
	private Font font;
	private Font fontFrom;
	private int fontSize;
	private boolean fontBold;

	private final Client client;
	private final StepTargets targets;
	private final ModelOutlineRenderer outlines;
	private final BooleanSupplier text;
	private final BooleanSupplier glow;
	private final Supplier<Color> colour;
	/** The colour of the words above the thing to click. */
	private final Supplier<Color> textColour;
	/** The colour of the words above the player (white by default). */
	private final Supplier<Color> playerColour;
	/** Whether the next-step box is shown (a bank step with many items points there). */
	private final BooleanSupplier boxShown;

	public StepHintOverlay(Client client, StepTargets targets, ModelOutlineRenderer outlines, BooleanSupplier text,
		BooleanSupplier glow, Supplier<Color> colour, Supplier<Color> textColour, Supplier<Color> playerColour,
		BooleanSupplier boxShown, IntSupplier glowWidth, IntSupplier textSize, BooleanSupplier bold)
	{
		this.glowWidth = glowWidth;
		this.textSize = textSize;
		this.bold = bold;
		this.textColour = textColour;
		this.playerColour = playerColour;
		this.boxShown = boxShown;
		this.client = client;
		this.targets = targets;
		this.outlines = outlines;
		this.text = text;
		this.glow = glow;
		this.colour = colour;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		RouteStep step = targets.getStep();
		if (step == null)
		{
			return null;
		}
		Color c = colour.get();
		TileObject object = targets.getObject();
		NPC npc = targets.getNpc();
		if (glow.getAsBoolean())
		{
			if (object != null)
			{
				outlines.drawOutline(object, glowWidth.getAsInt(), c, OUTLINE_FEATHER);
			}
			else if (npc != null)
			{
				outlines.drawOutline(npc, glowWidth.getAsInt(), c, OUTLINE_FEATHER);
			}
		}
		if (!text.getAsBoolean() || targets.isWaitingForFurniture())
		{
			return null;
		}
		String words = StepTargets.words(step, targets.getBankItems(), boxShown.getAsBoolean());
		c = textColour.get();
		g.setFont(font(g.getFont()));
		Point at = null;
		if (object != null)
		{
			at = object.getCanvasTextLocation(g, words, TEXT_HEIGHT);
		}
		else if (npc != null)
		{
			at = npc.getCanvasTextLocation(g, words, npc.getLogicalHeight() + 40);
		}
		else if (targets.getTextTile() != -1)
		{
			int t = targets.getTextTile();
			WorldView wv = client.getTopLevelWorldView();
			LocalPoint lp = Tiles.plane(t) == wv.getPlane() ? LocalPoint.fromWorld(wv, Tiles.x(t), Tiles.y(t)) : null;
			at = lp == null ? null : Perspective.getCanvasTextLocation(client, g, lp, words, TEXT_HEIGHT);
		}
		boolean inWorld = object != null || npc != null || targets.getTextTile() != -1;
		if (at == null && inWorld)
		{
			// the target is off screen: hide the words rather than moving them above the player
			return null;
		}
		if (at == null)
		{
			// nothing in the world to click (a teleport, sailing): above the player, in its own colour
			Player me = client.getLocalPlayer();
			at = me == null ? null : me.getCanvasTextLocation(g, words, me.getLogicalHeight() + 60);
			c = playerColour.get();
		}
		if (at != null)
		{
			OverlayUtil.renderTextLocation(g, at, words, c);
		}
		return null;
	}

	/**
	 * The words' font: RuneLite's overlay font (or its bold RuneScape font) at the "Text size"; 16, not bold, is RuneLite's
	 * own font unchanged.
	 */
	private Font font(Font base)
	{
		int size = textSize.getAsInt();
		boolean b = bold.getAsBoolean();
		if (font == null || base != fontFrom || size != fontSize || b != fontBold)
		{
			fontFrom = base;
			fontSize = size;
			fontBold = b;
			font = (b ? FontManager.getRunescapeBoldFont() : base).deriveFont((float) size);
		}
		return font;
	}
}

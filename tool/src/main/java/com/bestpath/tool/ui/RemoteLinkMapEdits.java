package com.bestpath.tool.ui;

import static com.bestpath.tool.ui.RemoteLink.num;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.FreeLandDiagnostics;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.editor.EditorContext;

/**
 * The {@link RemoteLink} commands about walking: tile and wall changes ({@code open}, {@code block}), pockets to
 * keep, free-to-play land, doors, and what the data says about a tile ({@code flags}, {@code usable},
 * {@code canstep}, {@code groups}).
 */
final class RemoteLinkMapEdits
{
	private RemoteLinkMapEdits()
	{
	}

	/** Runs a walking command; its reply, or null when {@code w[0]} is not one. */
	static String run(EditorContext ctx, String[] w, String line)
	{
		switch (w[0])
		{
			case "keep":
				return keep(ctx, w);
			case "groups":
				return groups(ctx, w);
			case "flags":
				// flags x y p: the tile's applied collision flags in hex (walls 0xFF, blocked 0x100 etc.)
				return "flags=0x" + Integer.toHexString(ctx.grid.get(num(w[1]), num(w[2]), num(w[3])));
			case "open":
				return open(ctx, w);
			case "block":
				return block(ctx, w);
			case "usable":
				return "usable=" + ctx.usable(num(w[1]), num(w[2]), num(w[3]), false);
			case "free":
				return free(ctx, w);
			case "isfree":
			{
				// isfree x y: is the tile free-to-play land
				com.bestpath.core.collision.FreeLand land = ctx.freeLand();
				return "free=" + land.isFree(num(w[1]), num(w[2])) + " (" + FreeLandDiagnostics.tileCount(land) + " free tiles)";
			}
			case "door":
				return door(ctx, w);
			case "canstep":
				// canstep x y p dx dy: one walking step allowed (walls, blocked tiles, no corner cutting)?
				return "canstep=" + (ctx.grid != null
					&& com.bestpath.core.collision.MovementRules.canStep(ctx.grid.plane(num(w[3])), num(w[1]), num(w[2]), num(w[4]), num(w[5])));
			default:
				return null;
		}
	}

	/** keep x y p: keep a closed-off pocket (like a right-click with the Hide tool), e.g. a room a ladder lands in */
	private static String keep(EditorContext ctx, String[] w)
	{
		int comp = ctx.graph.componentAt(num(w[1]), num(w[2]), num(w[3]));
		if (comp < 0 || ctx.pockets == null || comp >= ctx.pockets.length || !ctx.pockets[comp])
		{
			return "not a pocket";
		}
		Edit k = new Edit();
		k.type = Edit.Type.KEEP;
		k.x = num(w[1]);
		k.y = num(w[2]);
		k.plane = num(w[3]);
		k.on = true;
		k.factSize = ctx.graph.componentSize(comp);
		ctx.edits.add(k);
		return "kept " + k.factSize + " tiles";
	}

	/** groups x y p [x y p ...]: the walking group of each tile (-1 = not walkable), as in facts */
	private static String groups(EditorContext ctx, String[] w)
	{
		int[] g = RemoteLinkFacts.groups(ctx);
		StringBuilder sb = new StringBuilder();
		for (int k = 1; k + 2 < w.length; k += 3)
		{
			int c = ctx.graph.componentAt(num(w[k]), num(w[k + 1]), num(w[k + 2]));
			sb.append(c < 0 ? -1 : g[c]).append(' ');
		}
		return sb.toString().trim();
	}

	/**
	 * open x0 y0 x1 y1 p: make every tile of the box walkable and remove the walls between
	 * tiles of the box (like the Block brush's right click plus the Wall tool), e.g. the
	 * Wilderness Ditch (players walk over it; it needs no transport). Walls on the box's
	 * outer edge stay.
	 */
	private static String open(EditorContext ctx, String[] w)
	{
		int x0 = num(w[1]), y0 = num(w[2]), x1 = num(w[3]), y1 = num(w[4]), p = num(w[5]);
		int tiles = 0, walls = 0;
		for (int x = x0; x <= x1; x++)
		{
			for (int y = y0; y <= y1; y++)
			{
				int fl = ctx.grid.get(x, y, p);
				if ((fl & CollisionFlags.BLOCKED) != 0)
				{
					ctx.edits.add(tile(x, y, p, false));
					tiles++;
				}
				if (y < y1 && (fl & CollisionFlags.NORTH) != 0)
				{
					ctx.edits.add(wall(x, y, p, Edit.NORTH));
					walls++;
				}
				if (x < x1 && (fl & CollisionFlags.EAST) != 0)
				{
					ctx.edits.add(wall(x, y, p, Edit.EAST));
					walls++;
				}
			}
		}
		return "opened " + tiles + " blocked tiles, " + walls + " walls";
	}

	/**
	 * block x0 y0 x1 y1 p: make every walkable tile of the box blocked (like the Block brush), e.g. an obstacle
	 * tile the cache leaves walkable that only a transport may cross (Mac's island tree and rockslide)
	 */
	private static String block(EditorContext ctx, String[] w)
	{
		int x0 = num(w[1]), y0 = num(w[2]), x1 = num(w[3]), y1 = num(w[4]), p = num(w[5]);
		int tiles = 0;
		for (int x = x0; x <= x1; x++)
		{
			for (int y = y0; y <= y1; y++)
			{
				if ((ctx.grid.get(x, y, p) & CollisionFlags.BLOCKED) == 0)
				{
					ctx.edits.add(tile(x, y, p, true));
					tiles++;
				}
			}
		}
		return "blocked " + tiles + " tiles";
	}

	/** free x1 y1 x2 y2 on|off: mark a rectangle free-to-play land (on) or members only (off), every plane */
	private static String free(EditorContext ctx, String[] w)
	{
		Edit e = new Edit();
		e.type = Edit.Type.FREE_LAND;
		e.x = num(w[1]);
		e.y = num(w[2]);
		e.toX = num(w[3]);
		e.toY = num(w[4]);
		e.on = w[5].equals("on");
		if (!e.on && !w[5].equals("off"))
		{
			return "free x1 y1 x2 y2 on|off";
		}
		ctx.edits.add(e);
		return e.describe() + " -> " + FreeLandDiagnostics.tileCount(ctx.freeLand()) + " free tiles";
	}

	/** door x y p objectId shut|open: like the Door tool (a door with requirements is set shut; a door transport carries its requirements) */
	private static String door(EditorContext ctx, String[] w)
	{
		int[] d = null;
		for (int[] k : ctx.grid.getDoors())
		{
			if (k[0] == num(w[1]) && k[1] == num(w[2]) && k[2] == num(w[3]) && k[3] == num(w[4]))
			{
				d = k;
				break;
			}
		}
		if (d == null)
		{
			return "no such door";
		}
		boolean shut = w[5].equals("shut");
		if (!shut && !w[5].equals("open"))
		{
			return "door x y p objectId shut|open";
		}
		Edit e = new Edit();
		e.type = Edit.Type.DOOR;
		e.x = d[0];
		e.y = d[1];
		e.plane = d[2];
		e.objectId = d[3];
		e.on = shut;
		Edit pend = ctx.edits.findUnbuilt(e);
		boolean built = ctx.state.isDoorShut(d[0], d[1], d[2], d[3]);
		if ((pend != null ? pend.on : built) == shut)
		{
			return "already " + w[5];
		}
		if (shut == built)
		{
			ctx.edits.cancel(e);
		}
		else
		{
			ctx.edits.add(e);
		}
		return "ok (" + w[5] + ")";
	}

	/** A "block this tile" ({@code on}) or "make it walkable" edit, like the Block brush. */
	private static Edit tile(int x, int y, int plane, boolean on)
	{
		Edit e = new Edit();
		e.type = Edit.Type.TILE;
		e.x = x;
		e.y = y;
		e.plane = plane;
		e.on = on;
		return e;
	}

	/** A "remove this wall" edit, like a click with the Wall tool on a wall that is there. */
	private static Edit wall(int x, int y, int plane, int side)
	{
		Edit e = new Edit();
		e.type = Edit.Type.WALL;
		e.x = x;
		e.y = y;
		e.plane = plane;
		e.side = side;
		e.on = false;
		return e;
	}
}

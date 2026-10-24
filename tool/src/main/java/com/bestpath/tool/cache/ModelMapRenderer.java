package com.bestpath.tool.cache;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.cache.IndexType;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.item.RSTextureProvider;
import net.runelite.cache.models.JagexColor;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;

/**
 * Draws one region-plane as seen from straight above: the ground with its
 * heights shaded, and every placed object's 3D model (roofs left out).
 * Floor colours come from the world-map floor picture (4 px per tile); the
 * models are drawn by our own small rasterizer with a depth buffer.
 *
 * Units: 128 per tile (the game's own). Heights from the cache are negative
 * upwards; here "up" values are positive upwards.
 */
final class ModelMapRenderer
{
	/** Placed objects this close to a region edge (tiles) in a neighbour region are drawn too (they can overhang). */
	private static final int MARGIN = 6;

	private final Store store;
	private final RegionLoader regions;
	private final Map<Integer, ObjectDefinition> objects;
	private final Index modelIndex;
	private final RSTextureProvider textures;
	private final Map<Integer, ModelDefinition> modelCache = new HashMap<>();
	private final Map<Integer, Integer> textureColour = new HashMap<>();

	ModelMapRenderer(Store store, RegionLoader regions, Map<Integer, ObjectDefinition> objects) throws IOException
	{
		this.store = store;
		this.regions = regions;
		this.objects = objects;
		this.modelIndex = store.getIndex(IndexType.MODELS);
		TextureManager tm = new TextureManager(store);
		tm.load();
		SpriteManager sm = new SpriteManager(store);
		sm.load();
		this.textures = new RSTextureProvider(tm, sm);
	}

	/**
	 * Renders {@code region} on {@code plane} at {@code s} pixels per tile.
	 * {@code floor} is the world-map floor picture of the same region-plane
	 * (transparent where there is no floor), {@code underlay} the same without
	 * overlays (paths, floors, water); either may be null.
	 * Pixels where nothing was drawn are transparent.
	 */
	BufferedImage render(Region region, int plane, int s, BufferedImage floor, BufferedImage underlay)
	{
		Canvas c = new Canvas(64 * s, region.getBaseX(), region.getBaseY(), s);
		drawGround(c, region, plane, floor, underlay);
		List<Placed> placed = new ArrayList<>();
		for (int rx = -1; rx <= 1; rx++)
		{
			for (int ry = -1; ry <= 1; ry++)
			{
				Region r = regions.findRegionForRegionCoordinates(region.getRegionX() + rx, region.getRegionY() + ry);
				if (r == null)
				{
					continue;
				}
				for (Location l : r.getLocations())
				{
					int x = l.getPosition().getX();
					int y = l.getPosition().getY();
					if (x < c.baseX - MARGIN || y < c.baseY - MARGIN || x >= c.baseX + 64 + MARGIN || y >= c.baseY + 64 + MARGIN)
					{
						continue;
					}
					if (logicalPlane(l) != plane)
					{
						continue;
					}
					addPlaced(placed, l);
				}
			}
		}
		// opaque faces first, then see-through ones blended over them
		for (Placed p : placed)
		{
			drawModel(c, p, false);
		}
		for (Placed p : placed)
		{
			drawModel(c, p, true);
		}
		return c.toImage();
	}

	// ------------------------------------------------------------------ ground

	private void drawGround(Canvas c, Region region, int plane, BufferedImage floor, BufferedImage underlay)
	{
		if (floor == null)
		{
			return;
		}
		int s = c.s;
		int fw = floor.getWidth();
		int fs = fw / 64; // floor pixels per tile
		int[] fpx = floor.getRGB(0, 0, fw, fw, null, 0, fw);
		int[] upx = underlay == null || underlay.getWidth() != fw ? null : underlay.getRGB(0, 0, fw, fw, null, 0, fw);
		for (int tx = 0; tx < 64; tx++)
		{
			for (int ty = 0; ty < 64; ty++)
			{
				int wx = c.baseX + tx;
				int wy = c.baseY + ty;
				int z = storedPlane(region, plane, tx, ty);
				double h00 = up(z, wx, wy);
				double h10 = up(z, wx + 1, wy);
				double h01 = up(z, wx, wy + 1);
				double h11 = up(z, wx + 1, wy + 1);
				// hill shading at the corners, blended across the tile so slopes look smooth
				double s00 = cornerShade(z, wx, wy);
				double s10 = cornerShade(z, wx + 1, wy);
				double s01 = cornerShade(z, wx, wy + 1);
				double s11 = cornerShade(z, wx + 1, wy + 1);
				for (int i = 0; i < s; i++)
				{
					for (int j = 0; j < s; j++)
					{
						int px = tx * s + i;
						int py = (63 - ty) * s + j;
						int fx = px * fs / s;
						int fy = py * fs / s;
						int col = fpx[fy * fw + fx];
						if ((col >>> 24) == 0)
						{
							continue;
						}
						if (upx != null && upx[fy * fw + fx] == col)
						{
							// plain ground (no overlay here): blend between tile centres instead of in blocks
							col = smooth(upx, fw, fs, (px + 0.5) / s - 0.5, (py + 0.5) / s - 0.5, col);
						}
						double u = (i + 0.5) / s;
						double v = 1 - (j + 0.5) / s;
						double h = h00 * (1 - u) * (1 - v) + h10 * u * (1 - v) + h01 * (1 - u) * v + h11 * u * v;
						double shade = s00 * (1 - u) * (1 - v) + s10 * u * (1 - v) + s01 * (1 - u) * v + s11 * u * v;
						c.put(px, py, (float) h, shadeRgb(col, shade), 1f);
					}
				}
			}
		}
	}

	/** Brightness of the ground at a tile corner, from the slope around it. */
	private double cornerShade(int z, int wx, int wy)
	{
		double gx = (up(z, wx + 1, wy) - up(z, wx - 1, wy)) / 256.0;
		double gy = (up(z, wx, wy + 1) - up(z, wx, wy - 1)) / 256.0;
		return light(-gx, 1, -gy);
	}

	/**
	 * Bilinear blend of the colours at tile centres of a floor picture with
	 * {@code fs} pixels per tile; {@code tx, ty} in tiles from the top-left
	 * tile's centre. {@code fallback} if any of the four is transparent.
	 */
	private static int smooth(int[] px, int w, int fs, double tx, double ty, int fallback)
	{
		int n = w / fs;
		int x0 = Math.max(0, Math.min(n - 2, (int) Math.floor(tx)));
		int y0 = Math.max(0, Math.min(n - 2, (int) Math.floor(ty)));
		double u = Math.max(0, Math.min(1, tx - x0));
		double v = Math.max(0, Math.min(1, ty - y0));
		int h = fs / 2;
		int a = px[(y0 * fs + h) * w + x0 * fs + h];
		int b = px[(y0 * fs + h) * w + (x0 + 1) * fs + h];
		int c = px[((y0 + 1) * fs + h) * w + x0 * fs + h];
		int d = px[((y0 + 1) * fs + h) * w + (x0 + 1) * fs + h];
		if ((a >>> 24) == 0 || (b >>> 24) == 0 || (c >>> 24) == 0 || (d >>> 24) == 0)
		{
			return fallback;
		}
		int out = 0xFF000000;
		for (int sh = 0; sh <= 16; sh += 8)
		{
			double ch = ((a >> sh) & 0xFF) * (1 - u) * (1 - v) + ((b >> sh) & 0xFF) * u * (1 - v)
				+ ((c >> sh) & 0xFF) * (1 - u) * v + ((d >> sh) & 0xFF) * u * v;
			out |= ((int) Math.round(ch) & 0xFF) << sh;
		}
		return out;
	}

	/** Which stored plane holds logical plane {@code plane} at a tile (bridges move everything above down one). */
	private static int storedPlane(Region r, int plane, int tx, int ty)
	{
		if (plane < 3 && (r.getTileSetting(1, tx, ty) & 2) != 0)
		{
			return plane + 1;
		}
		return plane;
	}

	private int logicalPlane(Location l)
	{
		int z = l.getPosition().getZ();
		if (z == 0)
		{
			return 0;
		}
		Region r = regions.findRegionForWorldCoordinates(l.getPosition().getX(), l.getPosition().getY());
		if (r != null && (r.getTileSetting(1, l.getPosition().getX() - r.getBaseX(), l.getPosition().getY() - r.getBaseY()) & 2) != 0)
		{
			return z - 1;
		}
		return z;
	}

	/** Ground height (up positive) at a tile corner on a stored plane; 0 outside the map. */
	private double up(int z, int wx, int wy)
	{
		Region r = regions.findRegionForWorldCoordinates(wx, wy);
		if (r == null)
		{
			r = regions.findRegionForWorldCoordinates(wx - 1, wy - 1);
			if (r == null)
			{
				return 0;
			}
			wx = Math.min(wx, r.getBaseX() + 63);
			wy = Math.min(wy, r.getBaseY() + 63);
		}
		return -r.getTileHeight(z, wx - r.getBaseX(), wy - r.getBaseY());
	}

	/** Ground height at any point in world units (128 per tile), bilinear between corners. */
	private double upAt(int z, double ux, double uy)
	{
		int tx = (int) Math.floor(ux / 128);
		int ty = (int) Math.floor(uy / 128);
		double u = ux / 128 - tx;
		double v = uy / 128 - ty;
		return up(z, tx, ty) * (1 - u) * (1 - v) + up(z, tx + 1, ty) * u * (1 - v)
			+ up(z, tx, ty + 1) * (1 - u) * v + up(z, tx + 1, ty + 1) * u * v;
	}

	// ------------------------------------------------------------------ objects

	/** One model placed in the world: which model and how it is turned and moved. */
	private static final class Placed
	{
		ObjectDefinition def;
		int modelId;
		int z;
		double cx;
		double cy; // centre, world units
		double angle; // clockwise from above, radians
		boolean mirror;
		double baseUp;
	}

	private void addPlaced(List<Placed> out, Location l)
	{
		ObjectDefinition d = objects.get(l.getId());
		if (d == null)
		{
			return;
		}
		int type = l.getType();
		if (type >= 12 && type <= 21)
		{
			return; // roofs hide what is under them
		}
		int o = l.getOrientation() & 3;
		int x = l.getPosition().getX();
		int y = l.getPosition().getY();
		int z = l.getPosition().getZ();
		int w = (o & 1) == 1 ? d.getSizeY() : d.getSizeX();
		int len = (o & 1) == 1 ? d.getSizeX() : d.getSizeY();
		if (type != 10 && type != 11)
		{
			w = 1;
			len = 1;
		}
		double cx = x * 128 + w * 64;
		double cy = y * 128 + len * 64;
		double base = (up(z, x, y) + up(z, x + w, y) + up(z, x, y + len) + up(z, x + w, y + len)) / 4;
		switch (type)
		{
			case 11:
				add(out, d, 10, o, false, cx, cy, z, base, Math.PI / 4);
				break;
			case 2:
				add(out, d, 2, o, true, cx, cy, z, base, 0);
				add(out, d, 2, (o + 1) & 3, false, cx, cy, z, base, 0);
				break;
			case 5:
			{
				// wall decoration pushed out from the wall it hangs on
				double disp = d.getDecorDisplacement();
				double[] dir = WALL_NORMAL[o];
				add(out, d, 4, o, false, cx + dir[0] * disp, cy + dir[1] * disp, z, base, 0);
				break;
			}
			case 6:
			case 7:
			case 8:
				add(out, d, 4, o, false, cx, cy, z, base, Math.PI / 4);
				break;
			default:
				add(out, d, type, o, false, cx, cy, z, base, 0);
		}
	}

	/** Faces closer to vertical than this (up part of the unit normal) are drawn as lines. */
	private static final double STEEP = 0.2;

	/** Direction from a wall on side o (0 west, 1 north, 2 east, 3 south) into its tile. */
	private static final double[][] WALL_NORMAL = {{1, 0}, {0, -1}, {-1, 0}, {0, 1}};

	private void add(List<Placed> out, ObjectDefinition d, int type, int o, boolean extraMirror,
		double cx, double cy, int z, double base, double extraAngle)
	{
		int[] models = d.getObjectModels();
		if (models == null)
		{
			return;
		}
		int[] types = d.getObjectTypes();
		List<Integer> ids = new ArrayList<>();
		if (types == null)
		{
			if (type != 10)
			{
				return;
			}
			for (int m : models)
			{
				ids.add(m);
			}
		}
		else
		{
			for (int i = 0; i < types.length && i < models.length; i++)
			{
				if (types[i] == type)
				{
					ids.add(models[i]);
				}
			}
		}
		for (int id : ids)
		{
			Placed p = new Placed();
			p.def = d;
			p.modelId = id;
			p.z = z;
			p.cx = cx;
			p.cy = cy;
			p.angle = o * Math.PI / 2 + extraAngle;
			p.mirror = d.isRotated() ^ extraMirror;
			p.baseUp = base;
			out.add(p);
		}
	}

	private ModelDefinition model(int id)
	{
		if (modelCache.containsKey(id))
		{
			return modelCache.get(id);
		}
		ModelDefinition m = null;
		try
		{
			Archive a = modelIndex.getArchive(id);
			if (a != null)
			{
				byte[] data = a.decompress(store.getStorage().loadArchive(a));
				m = new ModelLoader().load(id, data);
			}
		}
		catch (Exception e)
		{
			m = null;
		}
		if (modelCache.size() > 4000)
		{
			modelCache.clear();
		}
		modelCache.put(id, m);
		return m;
	}

	private void drawModel(Canvas c, Placed p, boolean seeThroughPass)
	{
		ModelDefinition m = model(p.modelId);
		if (m == null || m.faceCount == 0)
		{
			return;
		}
		double[][] world = placeVertices(m, p);
		double[] wx = world[0];
		double[] wy = world[1];
		double[] wu = world[2];
		for (int f = 0; f < m.faceCount; f++)
		{
			drawFace(c, m, p, f, wx, wy, wu, seeThroughPass);
		}
	}

	/** The model's vertices turned, scaled and moved into place: world x (east), y (north) and up, in world units. */
	private double[][] placeVertices(ModelDefinition m, Placed p)
	{
		ObjectDefinition d = p.def;
		int n = m.vertexCount;
		double[] wx = new double[n];
		double[] wy = new double[n];
		double[] wu = new double[n];
		double cos = Math.cos(p.angle);
		double sin = Math.sin(p.angle);
		double sx = d.getModelSizeX() / 128.0;
		double sh = d.getModelSizeHeight() / 128.0;
		double sy = d.getModelSizeY() / 128.0;
		boolean contour = d.getContouredGround() >= 0;
		for (int i = 0; i < n; i++)
		{
			double mx = m.vertexX[i];
			double my = p.mirror ? -m.vertexZ[i] : m.vertexZ[i]; // model z points north
			double mu = -m.vertexY[i]; // model y is negative upwards
			// turn clockwise as seen from above
			double rx = mx * cos + my * sin;
			double ry = -mx * sin + my * cos;
			rx = rx * sx + d.getOffsetX();
			ry = ry * sy + d.getOffsetY();
			mu = mu * sh - d.getOffsetHeight();
			wx[i] = p.cx + rx;
			wy[i] = p.cy + ry;
			wu[i] = (contour ? upAt(p.z, wx[i], wy[i]) : p.baseUp) + mu;
		}
		return new double[][]{wx, wy, wu};
	}

	/**
	 * Draws face {@code f} if it belongs to this pass (opaque or see-through) and faces up: as a shaded
	 * triangle, or as a line along its top when it is steep (a wall seen edge-on).
	 */
	private void drawFace(Canvas c, ModelDefinition m, Placed p, int f, double[] wx, double[] wy, double[] wu, boolean seeThroughPass)
	{
		short[] tex = m.faceTextures;
		byte[] alpha = m.faceTransparencies;
		int t = alpha == null ? 0 : alpha[f] & 0xFF;
		if ((t > 0) != seeThroughPass || t >= 254)
		{
			return;
		}
		if (m.faceRenderTypes != null && (m.faceRenderTypes[f] & 3) == 2 && (tex == null || tex[f] == -1))
		{
			return; // marked hidden in the model
		}
		int a = m.faceIndices1[f];
		int b = m.faceIndices2[f];
		int e = m.faceIndices3[f];
		if (p.mirror)
		{
			int tmp = b;
			b = e;
			e = tmp;
		}
		// face normal (x east, y north, u up)
		double ax = wx[b] - wx[a], ay = wy[b] - wy[a], au = wu[b] - wu[a];
		double bx = wx[e] - wx[a], by = wy[e] - wy[a], bu = wu[e] - wu[a];
		double nx = ay * bu - au * by;
		double ny = au * bx - ax * bu;
		double nu = ax * by - ay * bx;
		double nlen = Math.sqrt(nx * nx + ny * ny + nu * nu);
		boolean steep = nlen > 0 && Math.abs(nu) < STEEP * nlen;
		if (nu < 0 && !steep)
		{
			// facing away from a camera above; the game's models expect these to be culled
			return;
		}
		int rgb = shadeRgb(faceColour(m, p.def, f), light(nx, nu, ny));
		if (steep)
		{
			// walls and fences are edge-on from above: draw them as a line along their top
			c.edge(wx[a], wy[a], wu[a], wx[b], wy[b], wu[b], wx[e], wy[e], wu[e], rgb, 1f - t / 255f);
			return;
		}
		c.triangle(wx[a], wy[a], wu[a], wx[b], wy[b], wu[b], wx[e], wy[e], wu[e], rgb, 1f - t / 255f);
	}

	/** A face's colour before shading: its texture's average colour, else its own colour (both after the object's swaps). */
	private int faceColour(ModelDefinition m, ObjectDefinition d, int f)
	{
		short[] tex = m.faceTextures;
		short[] retFind = d.getRetextureToFind();
		short[] retRepl = d.getTextureToReplace();
		int texId = tex == null ? -1 : tex[f];
		if (texId != -1 && retFind != null)
		{
			for (int k = 0; k < retFind.length; k++)
			{
				if (retFind[k] == texId)
				{
					texId = retRepl[k];
				}
			}
		}
		if (texId != -1)
		{
			return textureColour.computeIfAbsent(texId, textures::getAverageTextureRGB);
		}
		short hsl = m.faceColors[f];
		short[] recFind = d.getRecolorToFind();
		short[] recRepl = d.getRecolorToReplace();
		if (recFind != null)
		{
			for (int k = 0; k < recFind.length; k++)
			{
				if (recFind[k] == hsl)
				{
					hsl = recRepl[k];
				}
			}
		}
		return JagexColor.HSLtoRGB(hsl, JagexColor.BRIGHTNESS_LOW);
	}

	// ------------------------------------------------------------------ lighting

	/** Brightness for a surface normal (x east, u up, y north): light from the north-west, above. */
	private static double light(double nx, double nu, double ny)
	{
		double len = Math.sqrt(nx * nx + nu * nu + ny * ny);
		if (len == 0)
		{
			return 1;
		}
		double lx = -0.45, lu = 0.8, ly = 0.4;
		double ll = Math.sqrt(lx * lx + lu * lu + ly * ly);
		double dot = (nx * lx + nu * lu + ny * ly) / (len * ll);
		return 0.55 + 0.6 * Math.max(0, dot);
	}

	private static int shadeRgb(int rgb, double k)
	{
		int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k));
		int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k));
		int b = Math.min(255, (int) ((rgb & 0xFF) * k));
		return (r << 16) | (g << 8) | b;
	}

	// ------------------------------------------------------------------ rasterizer

	/** Colour and depth buffer for one region picture; world units in, pixels out. */
	private static final class Canvas
	{
		final int size;
		final int baseX;
		final int baseY;
		final int s;
		final int[] rgb;
		final float[] depth;
		final boolean[] drawn;

		Canvas(int size, int baseX, int baseY, int s)
		{
			this.size = size;
			this.baseX = baseX;
			this.baseY = baseY;
			this.s = s;
			this.rgb = new int[size * size];
			this.depth = new float[size * size];
			this.drawn = new boolean[size * size];
			Arrays.fill(depth, Float.NEGATIVE_INFINITY);
		}

		void put(int px, int py, float up, int colour, float alpha)
		{
			int i = py * size + px;
			if (up < depth[i])
			{
				return;
			}
			if (alpha >= 1f)
			{
				rgb[i] = colour;
				depth[i] = up;
			}
			else
			{
				int old = drawn[i] ? rgb[i] : 0;
				rgb[i] = blend(old, colour, alpha);
			}
			drawn[i] = true;
		}

		private static int blend(int under, int over, float a)
		{
			int r = (int) (((under >> 16) & 0xFF) * (1 - a) + ((over >> 16) & 0xFF) * a);
			int g = (int) (((under >> 8) & 0xFF) * (1 - a) + ((over >> 8) & 0xFF) * a);
			int b = (int) ((under & 0xFF) * (1 - a) + (over & 0xFF) * a);
			return (r << 16) | (g << 8) | b;
		}

		void triangle(double x0, double y0, double u0, double x1, double y1, double u1,
			double x2, double y2, double u2, int colour, float alpha)
		{
			double k = s / 128.0;
			// to pixels: x east, y down from the region's north edge
			double ax = (x0 - baseX * 128) * k, ay = size - (y0 - baseY * 128) * k;
			double bx = (x1 - baseX * 128) * k, by = size - (y1 - baseY * 128) * k;
			double cx = (x2 - baseX * 128) * k, cy = size - (y2 - baseY * 128) * k;
			double area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
			if (area == 0)
			{
				return;
			}
			int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
			int maxX = Math.min(size - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
			int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
			int maxY = Math.min(size - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
			if (minX > maxX || minY > maxY)
			{
				return;
			}
			for (int py = minY; py <= maxY; py++)
			{
				double sy = py + 0.5;
				for (int px = minX; px <= maxX; px++)
				{
					double sxp = px + 0.5;
					double w0 = ((bx - sxp) * (cy - sy) - (by - sy) * (cx - sxp)) / area;
					double w1 = ((cx - sxp) * (ay - sy) - (cy - sy) * (ax - sxp)) / area;
					double w2 = 1 - w0 - w1;
					if (w0 < 0 || w1 < 0 || w2 < 0)
					{
						continue;
					}
					put(px, py, (float) (w0 * u0 + w1 * u1 + w2 * u2), colour, alpha);
				}
			}
		}

		/**
		 * Draws a near-vertical face as a line between its two corners farthest
		 * apart as seen from above, at the face's top height.
		 */
		void edge(double x0, double y0, double u0, double x1, double y1, double u1,
			double x2, double y2, double u2, int colour, float alpha)
		{
			double d01 = (x0 - x1) * (x0 - x1) + (y0 - y1) * (y0 - y1);
			double d02 = (x0 - x2) * (x0 - x2) + (y0 - y2) * (y0 - y2);
			double d12 = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2);
			double ax = x1, ay = y1, bx = x2, by = y2;
			if (d01 >= d02 && d01 >= d12)
			{
				ax = x0;
				ay = y0;
				bx = x1;
				by = y1;
			}
			else if (d02 >= d12)
			{
				ax = x0;
				ay = y0;
			}
			float top = (float) Math.max(u0, Math.max(u1, u2));
			double k = s / 128.0;
			double pax = (ax - baseX * 128) * k;
			double pay = size - (ay - baseY * 128) * k;
			double pbx = (bx - baseX * 128) * k;
			double pby = size - (by - baseY * 128) * k;
			int w = Math.max(1, s / 8);
			int steps = Math.max(1, (int) Math.ceil(Math.hypot(pbx - pax, pby - pay) * 2));
			for (int i = 0; i <= steps; i++)
			{
				double t = (double) i / steps;
				int cx = (int) Math.floor(pax + (pbx - pax) * t - (w - 1) / 2.0);
				int cy = (int) Math.floor(pay + (pby - pay) * t - (w - 1) / 2.0);
				for (int dx = 0; dx < w; dx++)
				{
					for (int dy = 0; dy < w; dy++)
					{
						int px = cx + dx;
						int py = cy + dy;
						if (px >= 0 && py >= 0 && px < size && py < size)
						{
							put(px, py, top, colour, alpha);
						}
					}
				}
			}
		}

		BufferedImage toImage()
		{
			BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			int[] out = new int[rgb.length];
			for (int i = 0; i < out.length; i++)
			{
				out[i] = drawn[i] ? 0xFF000000 | rgb[i] : 0;
			}
			img.setRGB(0, 0, size, size, out, 0, size);
			return img;
		}
	}
}

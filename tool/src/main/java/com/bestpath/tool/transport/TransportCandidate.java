package com.bestpath.tool.transport;

import java.util.Collections;
import java.util.List;

/**
 * A placed object that looks like a transport (by its menu options), found
 * in the cache. Facts only; where it leads comes from {@link Suggestion}s,
 * which a person must accept. Immutable.
 */
public final class TransportCandidate
{
	/** Kind of transport, judged from the object's menu options. */
	public enum Category
	{
		CLIMB_UP("Climb up", 0x4cc9f0),
		CLIMB_DOWN("Climb down", 0x4361ee),
		CLIMB_BOTH("Climb up or down", 0x7209b7),
		ENTER("Enter / go through", 0xf77f00),
		EXIT("Exit / leave", 0xfcbf49),
		SHORTCUT("Agility shortcut", 0x80ed99),
		PASS("Pass (barrier)", 0xc77dff),
		TRAVEL("Board / travel / teleport", 0xef476f);

		private final String label;
		private final int rgb;

		Category(String label, int rgb)
		{
			this.label = label;
			this.rgb = rgb;
		}

		public String getLabel()
		{
			return label;
		}

		public int getRgb()
		{
			return rgb;
		}
	}

	private final int objectId;
	private final String name;
	private final int x;
	private final int y;
	private final int plane;
	private final int shapeType;
	private final int orientation;
	private final int sizeX;
	private final int sizeY;
	private final List<String> options;
	private final Category category;
	private final int varbit;
	private final int varp;

	public TransportCandidate(int objectId, String name, int x, int y, int plane, int shapeType, int orientation,
		int sizeX, int sizeY, List<String> options, Category category, int varbit, int varp)
	{
		this.objectId = objectId;
		this.name = name;
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.shapeType = shapeType;
		this.orientation = orientation;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.options = Collections.unmodifiableList(options);
		this.category = category;
		this.varbit = varbit;
		this.varp = varp;
	}

	/** Stable id: object id and position, e.g. "16683@3205,3208,0". */
	public String id()
	{
		return objectId + "@" + x + "," + y + "," + plane;
	}

	public int getObjectId()
	{
		return objectId;
	}

	public String getName()
	{
		return name;
	}

	/** South-west tile of the object. */
	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public int getPlane()
	{
		return plane;
	}

	public int getShapeType()
	{
		return shapeType;
	}

	/** Footprint width (east-west), already rotated. */
	public int getSizeX()
	{
		return sizeX;
	}

	/** Footprint height (north-south), already rotated. */
	public int getSizeY()
	{
		return sizeY;
	}

	public List<String> getOptions()
	{
		return options;
	}

	public Category getCategory()
	{
		return category;
	}

	/** Varbit that changes this object's form (-1 if none). */
	public int getVarbit()
	{
		return varbit;
	}

	/** Varp that changes this object's form (-1 if none). */
	public int getVarp()
	{
		return varp;
	}

	/** Chebyshev distance (tiles) between this object's footprint and a tile. */
	public int distanceTo(int tx, int ty)
	{
		int dx = tx < x ? x - tx : tx > x + sizeX - 1 ? tx - (x + sizeX - 1) : 0;
		int dy = ty < y ? y - ty : ty > y + sizeY - 1 ? ty - (y + sizeY - 1) : 0;
		return Math.max(dx, dy);
	}

	/** Chebyshev distance between two footprints (0 if they touch or overlap). */
	public int distanceTo(TransportCandidate o)
	{
		int dx = Math.max(0, Math.max(o.x - (x + sizeX - 1), x - (o.x + o.sizeX - 1)));
		int dy = Math.max(0, Math.max(o.y - (y + sizeY - 1), y - (o.y + o.sizeY - 1)));
		return Math.max(dx, dy);
	}
}

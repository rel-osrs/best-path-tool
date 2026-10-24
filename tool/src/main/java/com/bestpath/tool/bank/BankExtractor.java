package com.bestpath.tool.bank;

import com.bestpath.tool.cache.LoadedCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.cache.EntityOpsDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.region.Location;

/**
 * Finds the bank booths and bank chests placed in the cache. The banks are
 * these booths and chests, plus Wiki bankers where there is no booth;
 * deposit boxes don't count.
 *
 * <p>An object is a bank when its menu has "Bank", or "Use" with "bank" in
 * its name; anything named "deposit" is not. An object that changes with a
 * varbit or varp ("multiloc") is a bank only in the forms that are; when
 * some forms are not, the bank needs the var values that show a bank form
 * ({@link BankObject#condition}), read from the cache (the last form is the
 * one shown for every other value).
 */
public final class BankExtractor
{
	private BankExtractor()
	{
	}

	/** One placed bank booth or chest. */
	public static final class BankObject
	{
		public final int objectId;
		public final String name;
		/** "booth" or "chest" (chests by name; booths, counters, tables, the bank camel...). */
		public final String kind;
		public final int x;
		public final int y;
		/** The plane it is used on (one lower on bridge tiles, as collision). */
		public final int plane;
		public final int type;
		public final int rotation;
		/** Footprint after rotation. */
		public final int sizeX;
		public final int sizeY;
		/** The sides it can't be used from, after rotation: 1 north, 2 east, 4 south, 8 west (cache "blocking mask"). */
		public final int blockedSides;
		/** A requirement line for the var values that show a bank form, or "" when every form is a bank. */
		public final String condition;

		BankObject(int objectId, String name, String kind, int x, int y, int plane, int type, int rotation, int sizeX, int sizeY,
			int blockedSides, String condition)
		{
			this.objectId = objectId;
			this.name = name;
			this.kind = kind;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.type = type;
			this.rotation = rotation;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.blockedSides = blockedSides;
			this.condition = condition;
		}
	}

	/** Every placed bank booth and chest. */
	public static List<BankObject> scan(LoadedCache cache) throws IOException
	{
		List<BankObject> out = new ArrayList<>();
		cache.forEachRegion(r ->
		{
			for (Location l : r.getLocations())
			{
				ObjectDefinition def = cache.object(l.getId());
				if (def == null)
				{
					continue;
				}
				String condition = condition(cache, def);
				if (condition == null)
				{
					continue;
				}
				ObjectDefinition shown = bankForm(cache, def);
				String name = plainName(shown.getName());
				int type = l.getType();
				int rot = l.getOrientation() & 3;
				boolean centrepiece = type >= 9;
				int sx = centrepiece ? shown.getSizeX() : 1;
				int sy = centrepiece ? shown.getSizeY() : 1;
				if (rot == 1 || rot == 3)
				{
					int t = sx;
					sx = sy;
					sy = t;
				}
				int lx = l.getPosition().getX() - r.getBaseX();
				int ly = l.getPosition().getY() - r.getBaseY();
				int plane = l.getPosition().getZ();
				if (lx >= 0 && lx < 64 && ly >= 0 && ly < 64 && (r.getTileSetting(1, lx, ly) & 2) != 0)
				{
					plane--;
				}
				if (plane < 0)
				{
					continue;
				}
				out.add(new BankObject(l.getId(), name, name.toLowerCase(Locale.ROOT).contains("chest") ? "chest" : "booth",
					l.getPosition().getX(), l.getPosition().getY(), plane, type, rot, sx, sy,
					centrepiece || type == 22 ? rotateSides(shown.getBlockingMask(), rot) : 0, condition));
			}
		});
		return out;
	}

	/**
	 * The sides a centrepiece can't be used from, turned with the object: the cache's mask (low four bits: north,
	 * east, south, west) rotated clockwise by the orientation.
	 */
	static int rotateSides(int mask, int rot)
	{
		int m = mask & 0xF;
		return rot == 0 ? m : ((m << rot) & 0xF) | (m >> (4 - rot));
	}

	/**
	 * Null when no form of the object is a bank; "" when it always is; else a requirement line with the var
	 * values that show a bank form.
	 */
	static String condition(LoadedCache cache, ObjectDefinition def)
	{
		int[] forms = def.getConfigChangeDest();
		if (forms == null)
		{
			return isBank(def) ? "" : null;
		}
		int n = forms.length;
		boolean[] bank = new boolean[n];
		boolean any = false;
		boolean all = true;
		for (int i = 0; i < n; i++)
		{
			ObjectDefinition f = forms[i] >= 0 ? cache.object(forms[i]) : null;
			bank[i] = f != null && isBank(f);
			any |= bank[i];
			all &= bank[i];
		}
		if (!any)
		{
			return null;
		}
		if (all)
		{
			return "";
		}
		String var = def.getVarbitID() >= 0 ? "varbit " + def.getVarbitID() : "varp " + def.getVarpID();
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < n - 1; i++)
		{
			if (bank[i])
			{
				parts.add(var + " = " + i);
			}
		}
		if (bank[n - 1])
		{
			parts.add(var + " >= " + (n - 1)); // the last form is shown for every value from n - 1 on
		}
		return parts.size() == 1 ? parts.get(0) : "one of (" + String.join(" | ", parts) + ")";
	}

	/** The object itself, or its first bank form. */
	private static ObjectDefinition bankForm(LoadedCache cache, ObjectDefinition def)
	{
		int[] forms = def.getConfigChangeDest();
		if (forms != null)
		{
			for (int f : forms)
			{
				ObjectDefinition d = f >= 0 ? cache.object(f) : null;
				if (d != null && isBank(d))
				{
					return d;
				}
			}
		}
		return def;
	}

	/** This form's own menu has "Bank", or "Use" with "bank" in its name; nothing named "deposit". */
	static boolean isBank(ObjectDefinition def)
	{
		String n = plainName(def.getName()).toLowerCase(Locale.ROOT);
		if (n.contains("deposit"))
		{
			return false;
		}
		Set<String> ops = ownOptions(def);
		return ops.contains("bank") || ops.contains("use") && n.contains("bank");
	}

	/** The name without colour tags. */
	static String plainName(String name)
	{
		return name == null ? "" : name.replaceAll("<[^>]*>", "").trim();
	}

	/** Lower-case menu options of this form only. */
	static Set<String> ownOptions(ObjectDefinition def)
	{
		Set<String> ops = new LinkedHashSet<>();
		EntityOpsDefinition e = def.getOps();
		if (e == null)
		{
			return ops;
		}
		for (EntityOpsDefinition.Op op : e.ops)
		{
			if (op != null && op.text != null)
			{
				ops.add(op.text.toLowerCase(Locale.ROOT));
			}
		}
		for (List<EntityOpsDefinition.ConditionalOp> list : e.conditionalOps)
		{
			if (list != null)
			{
				for (EntityOpsDefinition.ConditionalOp op : list)
				{
					if (op != null && op.text != null)
					{
						ops.add(op.text.toLowerCase(Locale.ROOT));
					}
				}
			}
		}
		return ops;
	}

}

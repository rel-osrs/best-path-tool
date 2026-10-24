package com.bestpath.core.data;

import java.util.Locale;

/**
 * One confirmed transport as a line of {@code transports.tsv}: only the fields
 * the plugin uses. Immutable.
 *
 * <p>Columns (tab-separated): id, kind, name, category, start "x,y,plane"
 * (empty for a teleport), end "x,y,plane", ticks, flags, network,
 * requirements, requirements back, teleport category, Wilderness limit,
 * object "id@x,y,plane" (empty = none), player note, then (optional) menu and
 * menu back: what the player clicks in the game start to end and (two-way)
 * end to start, as {@link MenuText} writes it (empty = not known). Flags:
 * {@code q} quest only, {@code f} can fail, {@code c} long cooldown, {@code e}
 * ticks are an estimate, {@code b} the requirements-back column is set
 * (without it the way back needs the same as the way there), {@code s} and a
 * digit: only a boat uses it, no wider than that many tiles ({@code s2}: rafts
 * and skiffs).
 */
public final class TransportLine
{
	public enum Kind
	{
		/** Works both ways (ladder, stairs, shortcut). */
		TWO_WAY,
		/** Only from start to end. */
		ONE_WAY,
		/** No start: usable from anywhere. */
		TELEPORT,
		/** One stop of a network: a ride to any other stop of the same network. */
		NETWORK
	}

	/** Columns written (with the two menu text columns). */
	public static final int COLUMNS = 17;
	/** Columns of a line without the menu text columns (still read). */
	public static final int COLUMNS_WITHOUT_MENU = 15;
	/** The column header line written at the top of the file (after the comments). */
	public static final String HEADER = "#id\tkind\tname\tcategory\tstart\tend\tticks\tflags\tnetwork\trequirements"
		+ "\trequirements back\tteleport category\twilderness limit\tobject\tplayer note\tmenu\tmenu back";

	private final String id;
	private final Kind kind;
	private final String name;
	private final String category;
	private final int startX;
	private final int startY;
	private final int startPlane;
	private final int endX;
	private final int endY;
	private final int endPlane;
	private final int ticks;
	final boolean ticksEstimate;
	private final boolean questOnly;
	private final boolean canFail;
	private final boolean cooldown;
	private final String network;
	private final String requirements;
	/** Null when the way back needs the same as the way there. */
	final String requirementsBack;
	private final String teleportCategory;
	private final Integer wildernessLimit;
	private final int objectId;
	private final int objectX;
	private final int objectY;
	private final int objectPlane;
	private final String playerNote;
	private final MenuText menu;
	private final MenuText menuBack;
	/** Only a boat uses it: the widest boat that fits (tiles); 0 = not boat-only (see {@link #withBoats}). */
	private int largestBoat;

	private TransportLine(Builder b)
	{
		if (b.id == null || b.id.isEmpty())
		{
			throw new IllegalArgumentException("a transport needs an id");
		}
		this.id = b.id;
		this.kind = b.kind;
		this.name = text(b.name);
		this.category = text(b.category);
		this.startX = b.startX;
		this.startY = b.startY;
		this.startPlane = b.startPlane;
		this.endX = b.endX;
		this.endY = b.endY;
		this.endPlane = b.endPlane;
		this.ticks = b.ticks;
		this.ticksEstimate = b.ticksEstimate;
		this.questOnly = b.questOnly;
		this.canFail = b.canFail;
		this.cooldown = b.cooldown;
		this.network = text(b.network);
		this.requirements = text(b.requirements);
		this.requirementsBack = b.requirementsBack == null ? null : clean(b.requirementsBack);
		this.teleportCategory = text(b.teleportCategory);
		this.wildernessLimit = b.wildernessLimit;
		this.objectId = b.objectId;
		this.objectX = b.objectX;
		this.objectY = b.objectY;
		this.objectPlane = b.objectPlane;
		this.playerNote = text(b.playerNote);
		this.menu = b.menu;
		this.menuBack = b.kind == Kind.TWO_WAY ? b.menuBack : null;
	}

	/**
	 * A new line with this id and kind. Everything else starts empty: no text, tiles and ticks 0, no flags, no
	 * Wilderness limit, no object, menu texts not known.
	 */
	public static Builder builder(String id, Kind kind)
	{
		return new Builder(id, kind);
	}

	/** A builder holding all of this line's fields, to make a changed copy. */
	Builder toBuilder()
	{
		return builder(id, kind).name(name).category(category).start(startX, startY, startPlane).end(endX, endY, endPlane)
			.ticks(ticks).ticksEstimate(ticksEstimate).questOnly(questOnly).canFail(canFail).cooldown(cooldown).network(network)
			.requirements(requirements).requirementsBack(requirementsBack).teleportCategory(teleportCategory)
			.wildernessLimit(wildernessLimit).object(objectId, objectX, objectY, objectPlane).playerNote(playerNote)
			.menu(menu, menuBack);
	}

	/** Makes a {@link TransportLine} one named field at a time. Text fields may be null (= empty). */
	public static final class Builder
	{
		private final String id;
		private final Kind kind;
		private String name;
		private String category;
		private int startX;
		private int startY;
		private int startPlane;
		private int endX;
		private int endY;
		private int endPlane;
		private int ticks;
		private boolean ticksEstimate;
		private boolean questOnly;
		private boolean canFail;
		private boolean cooldown;
		private String network;
		private String requirements;
		private String requirementsBack;
		private String teleportCategory;
		private Integer wildernessLimit;
		private int objectId = -1;
		private int objectX;
		private int objectY;
		private int objectPlane;
		private String playerNote;
		private MenuText menu;
		private MenuText menuBack;

		private Builder(String id, Kind kind)
		{
			this.id = id;
			this.kind = kind;
		}

		public Builder name(String name)
		{
			this.name = name;
			return this;
		}

		public Builder category(String category)
		{
			this.category = category;
			return this;
		}

		/** Where it is used (a teleport has none). */
		public Builder start(int x, int y, int plane)
		{
			startX = x;
			startY = y;
			startPlane = plane;
			return this;
		}

		/** Where it takes the player. */
		public Builder end(int x, int y, int plane)
		{
			endX = x;
			endY = y;
			endPlane = plane;
			return this;
		}

		public Builder ticks(int ticks)
		{
			this.ticks = ticks;
			return this;
		}

		/** The ticks are an estimate. */
		public Builder ticksEstimate(boolean estimate)
		{
			ticksEstimate = estimate;
			return this;
		}

		/** Only usable during a quest. */
		public Builder questOnly(boolean questOnly)
		{
			this.questOnly = questOnly;
			return this;
		}

		/** Using it can fail (an agility shortcut). */
		public Builder canFail(boolean canFail)
		{
			this.canFail = canFail;
			return this;
		}

		/** A long cooldown teleport. */
		public Builder cooldown(boolean cooldown)
		{
			this.cooldown = cooldown;
			return this;
		}

		/** The network a stop belongs to ({@link Kind#NETWORK}). */
		public Builder network(String network)
		{
			this.network = network;
			return this;
		}

		/** What it needs, as requirement lines. */
		public Builder requirements(String requirements)
		{
			this.requirements = requirements;
			return this;
		}

		/** What the way back needs; null = the same as the way there. */
		public Builder requirementsBack(String requirementsBack)
		{
			this.requirementsBack = requirementsBack;
			return this;
		}

		public Builder teleportCategory(String teleportCategory)
		{
			this.teleportCategory = teleportCategory;
			return this;
		}

		/** The deepest Wilderness level it works up to; null = no limit. */
		public Builder wildernessLimit(Integer level)
		{
			wildernessLimit = level;
			return this;
		}

		/** The object the player clicks, and its tile; id -1 = none. */
		public Builder object(int id, int x, int y, int plane)
		{
			objectId = id;
			objectX = x;
			objectY = y;
			objectPlane = plane;
			return this;
		}

		public Builder playerNote(String note)
		{
			playerNote = note;
			return this;
		}

		/** What the player clicks: start to end, and end to start (kept for two-way transports only); null = not known. */
		public Builder menu(MenuText there, MenuText back)
		{
			menu = there;
			menuBack = back;
			return this;
		}

		public TransportLine build()
		{
			return new TransportLine(this);
		}
	}

	/**
	 * This line for boats only (such as the Wyrmscraig cave): only the sea search uses it, for boats no wider
	 * than {@code largest} tiles (1 raft, 2 skiff, 3 sloop; {@code com.bestpath.core.path.Transport#forBoats}); 0 = not
	 * boat-only. Only for a transport with a start.
	 */
	public TransportLine withBoats(int largest)
	{
		int l = kind == Kind.TELEPORT || kind == Kind.NETWORK ? 0 : Math.max(0, Math.min(9, largest));
		if (l == largestBoat)
		{
			return this;
		}
		TransportLine t = toBuilder().build();
		t.largestBoat = l;
		return t;
	}

	/** The widest boat that may use it (tiles), 0 when it is not boat-only ({@link #withBoats}). */
	public int getLargestBoat()
	{
		return largestBoat;
	}

	private static String text(String s)
	{
		return s == null ? "" : clean(s);
	}

	/** Tabs and line breaks would break the file: they become spaces. */
	static String clean(String s)
	{
		return s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
	}

	/** Reads a line of the file (see the class comment), or one without the menu text columns (15 columns). */
	public static TransportLine parse(String line)
	{
		String[] c = line.split("\t", -1);
		if (c.length != COLUMNS && c.length != COLUMNS_WITHOUT_MENU)
		{
			throw new IllegalArgumentException("expected " + COLUMNS + " (or " + COLUMNS_WITHOUT_MENU + ") columns, found "
				+ c.length + ": " + line);
		}
		boolean menus = c.length == COLUMNS;
		Kind kind = Kind.valueOf(c[1]);
		int[] start = kind == Kind.TELEPORT ? new int[3] : tile(c[4]);
		int[] end = tile(c[5]);
		String flags = c[7];
		int objectId = -1;
		int[] object = new int[3];
		if (!c[13].isEmpty())
		{
			int at = c[13].indexOf('@');
			objectId = Integer.parseInt(c[13].substring(0, at));
			object = tile(c[13].substring(at + 1));
		}
		return builder(c[0], kind).name(c[2]).category(c[3]).start(start[0], start[1], start[2]).end(end[0], end[1], end[2])
			.ticks(Integer.parseInt(c[6])).ticksEstimate(flags.indexOf('e') >= 0).questOnly(flags.indexOf('q') >= 0)
			.canFail(flags.indexOf('f') >= 0).cooldown(flags.indexOf('c') >= 0).network(c[8]).requirements(c[9])
			.requirementsBack(flags.indexOf('b') >= 0 ? c[10] : null).teleportCategory(c[11])
			.wildernessLimit(c[12].isEmpty() ? null : Integer.valueOf(c[12])).object(objectId, object[0], object[1], object[2])
			.playerNote(c[14]).menu(menus ? MenuText.parse(c[15]) : null, menus ? MenuText.parse(c[16]) : null).build()
			.withBoats(boats(flags));
	}

	/** The digit after flag {@code s} (only a boat, that wide at most), else 0. */
	private static int boats(String flags)
	{
		int at = flags.indexOf('s');
		return at >= 0 && at + 1 < flags.length() && Character.isDigit(flags.charAt(at + 1)) ? flags.charAt(at + 1) - '0' : 0;
	}

	private static int[] tile(String s)
	{
		String[] w = s.split(",");
		if (w.length != 3)
		{
			throw new IllegalArgumentException("not a tile x,y,plane: '" + s + "'");
		}
		return new int[]{Integer.parseInt(w[0].trim()), Integer.parseInt(w[1].trim()), Integer.parseInt(w[2].trim())};
	}

	public String getId()
	{
		return id;
	}

	public Kind getKind()
	{
		return kind;
	}

	public String getName()
	{
		return name;
	}

	/** The transport's category (CLIMB_UP, SHORTCUT, TRAVEL, ..., CUSTOM). */
	public String getCategory()
	{
		return category;
	}

	public boolean hasStart()
	{
		return kind != Kind.TELEPORT;
	}

	public int getStartX()
	{
		return startX;
	}

	public int getStartY()
	{
		return startY;
	}

	public int getStartPlane()
	{
		return startPlane;
	}

	public int getEndX()
	{
		return endX;
	}

	public int getEndY()
	{
		return endY;
	}

	public int getEndPlane()
	{
		return endPlane;
	}

	public int getTicks()
	{
		return ticks;
	}

	public boolean isQuestOnly()
	{
		return questOnly;
	}

	public boolean isCanFail()
	{
		return canFail;
	}

	public boolean isCooldown()
	{
		return cooldown;
	}

	/** The network's name as written (empty = none). */
	public String getNetwork()
	{
		return network;
	}

	public String getRequirements()
	{
		return requirements;
	}

	/** The requirement line for end to start (two-way): its own line if set, else the same as start to end. */
	public String requirementsEndToStart()
	{
		return requirementsBack != null ? requirementsBack : requirements;
	}

	public String getTeleportCategory()
	{
		return teleportCategory;
	}

	/** Teleports: the deepest Wilderness level it works from; null = no limit. */
	public Integer getWildernessLimit()
	{
		return wildernessLimit;
	}

	/** The cache object it belongs to, or -1. */
	public int getObjectId()
	{
		return objectId;
	}

	public int getObjectX()
	{
		return objectX;
	}

	public int getObjectY()
	{
		return objectY;
	}

	public int getObjectPlane()
	{
		return objectPlane;
	}

	public String getPlayerNote()
	{
		return playerNote;
	}

	/**
	 * What the player clicks to use it start to end (a teleport: from anywhere; a network stop: the stop's own click
	 * and the choice that leads TO this stop, joined for a ride by {@link MenuText#ride}); null = not known.
	 */
	public MenuText getMenu()
	{
		return menu;
	}

	/** Two-way only: what the player clicks to use it end to start; null = not known (or not two-way). */
	public MenuText getMenuBack()
	{
		return menuBack;
	}

	/** The menu text of one direction: start to end, or end to start (two-way); null = not known. */
	public MenuText menuFor(boolean startToEnd)
	{
		return startToEnd ? menu : menuBack;
	}

	@Override
	public String toString()
	{
		return String.format(Locale.ROOT, "%s '%s' (%s)", kind, name, id);
	}
}

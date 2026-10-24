package com.bestpath.core.sea;

/**
 * One of the player's boats (up to five): its kind, the parts that set its
 * speed and the restricted waters it may enter, and where it is moored.
 * Immutable.
 *
 * <p>Parts are tiers 0-6, as in the cache's part tables:
 * hulls (table 178) wooden, oak, teak, mahogany, camphor, ironwood,
 * rosewood (a raft's "base"); sails (table 179) the same woods; keels
 * (table 177) and helms (table 181) bronze, iron, steel, mithril, adamant,
 * rune, dragon. The values below come from each part's stats row (table 164).
 */
public final class Boat
{
	public static final String[] WOODS = {"Wooden", "Oak", "Teak", "Mahogany", "Camphor", "Ironwood", "Rosewood"};
	public static final String[] METALS = {"Bronze", "Iron", "Steel", "Mithril", "Adamant", "Rune", "Dragon"};
	/** Hull base speed per tier, tiles per tick (stats column 24 / 128; the same for all boat kinds). */
	private static final double[] HULL_SPEED = {1.5, 1.5, 2.0, 2.0, 2.5, 2.5, 3.0};
	/** Sails from oak resist storms (column 21 = 1; camphor and up 2): Tempor Tantrum. */
	private static final int SAIL_TEMPOR = 1;
	/** Keels from adamant are crystal-flecked resistant (column 30): Crystalline waters. */
	private static final int KEEL_CRYSTAL = 4;
	/** Helms from adamant are tangled-kelp resistant (column 31): the kelp area. */
	private static final int HELM_KELP = 4;

	/** No teleport focus; a teleport focus (boat comes to the player's dock); a greater one (also takes the player to the boat). */
	public enum Focus
	{
		NONE("none"),
		TELEPORT("Teleport focus"),
		GREATER("Greater teleport focus");

		private final String label;

		Focus(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	/** {@link #getDockId()} when the boat is at sea where the route starts (the player is on it). */
	public static final int AT_START = -2;
	/** {@link #getDockId()} when it is not known where the boat is. */
	public static final int NOWHERE = -1;
	/**
	 * {@link #getDockId()} when the boat has capsized (the game's {@code _PORT} 254, table 147 "Capsized"): only a
	 * shipwright brings it back, for the same coins as a normal retrieval and 10 ticks more. The game's boat lists
	 * for summoning with a teleport focus and teleporting to the boat leave capsized, lost and bottled boats out
	 * (cache table 149 rows "Summon Boat" / "Teleport to Boat", columns 9-11).
	 */
	public static final int CAPSIZED = -3;
	/** {@link #getDockId()} when the boat is lost ({@code _PORT} 253, "Lost"): as {@link #CAPSIZED}. */
	public static final int LOST = -4;
	/**
	 * {@link #getDockId()} when the boat is in a boat bottle ({@code _PORT} 255, "Bottled"): routes use it only while
	 * the player carries its bottle in the inventory; then it is released at any dock.
	 */
	public static final int BOTTLED = -5;
	/**
	 * The full boat bottle: cache item 31992 "Boat bottle (full)" (inventory options Unbottle-boat, Check,
	 * Destroy; no noted form; RuneLite's {@code ItemID.SAILING_BOAT_BOTTLE_FULL}). The same item for every boat: the
	 * item itself does not say which boat is in it (the empty bottle is 31989).
	 */
	public static final int BOTTLE_FULL = 31992;

	private final int slot;
	private final BoatType type;
	private final int hull;
	private final int sail;
	private final int keel;
	private final int helm;
	final boolean inoculation;
	final boolean brazier;
	private final Focus focus;
	private final int dockId;
	/** The player's own name for the boat as the game shows it ("Jolly Raven"), or null when not known. */
	private final String name;

	/**
	 * @param slot 1-5
	 * @param keel tier, ignored for a raft (no keel)
	 * @param inoculation an Inoculation station is built on it (fetid water)
	 * @param brazier an Eternal brazier is built on it (northern icy seas)
	 * @param dockId the dock it is moored at (table 194 id), {@link #AT_START}, {@link #NOWHERE}, {@link #CAPSIZED},
	 *               {@link #LOST} or {@link #BOTTLED}
	 */
	public Boat(int slot, BoatType type, int hull, int sail, int keel, int helm, boolean inoculation, boolean brazier,
		Focus focus, int dockId)
	{
		this.slot = slot;
		this.type = type;
		this.hull = tier(hull);
		this.sail = tier(sail);
		this.keel = type.hasKeel() ? tier(keel) : -1;
		this.helm = tier(helm);
		this.inoculation = inoculation;
		this.brazier = brazier;
		this.focus = focus;
		this.dockId = dockId;
		this.name = null;
	}

	private Boat(Boat b, String name)
	{
		this.slot = b.slot;
		this.type = b.type;
		this.hull = b.hull;
		this.sail = b.sail;
		this.keel = b.keel;
		this.helm = b.helm;
		this.inoculation = b.inoculation;
		this.brazier = b.brazier;
		this.focus = b.focus;
		this.dockId = b.dockId;
		this.name = name == null || name.trim().isEmpty() ? null : name.trim();
	}

	/** This boat with the player's own name for it as the game shows it; null or blank: not known. */
	public Boat withName(String boatName)
	{
		return new Boat(this, boatName);
	}

	/** The player's own name for the boat, or null when not known. */
	public String getName()
	{
		return name;
	}

	private static int tier(int t)
	{
		if (t < 0 || t > 6)
		{
			throw new IllegalArgumentException("part tier must be 0-6: " + t);
		}
		return t;
	}

	public int getSlot()
	{
		return slot;
	}

	public BoatType getType()
	{
		return type;
	}

	public int getHull()
	{
		return hull;
	}

	public int getSail()
	{
		return sail;
	}

	/** Keel tier, or -1 for a raft. */
	public int getKeel()
	{
		return keel;
	}

	public int getHelm()
	{
		return helm;
	}

	public Focus getFocus()
	{
		return focus;
	}

	public int getDockId()
	{
		return dockId;
	}

	/** Capsized or lost: only a shipwright can bring it to a dock. */
	public boolean needsShipwright()
	{
		return dockId == CAPSIZED || dockId == LOST;
	}

	/** In a boat bottle: released at a dock with its bottle. */
	public boolean isBottled()
	{
		return dockId == BOTTLED;
	}

	/** The place in words when it is no dock ("capsized", "lost", "bottled"), or null. */
	public String placeWord()
	{
		switch (dockId)
		{
			case CAPSIZED:
				return "capsized";
			case LOST:
				return "lost";
			case BOTTLED:
				return "bottled";
			default:
				return null;
		}
	}

	/** Top speed in tiles per tick. Acceleration and speed boosts are not modelled. */
	public double speed()
	{
		return HULL_SPEED[hull];
	}

	/** Whether the boat's parts let it sail in the area (see the part constants above). */
	public boolean canEnter(SeaArea area)
	{
		switch (area)
		{
			case TEMPOR_TANTRUM:
				return sail >= SAIL_TEMPOR;
			case NORTHERN:
				return brazier;
			case CRYSTALLINE:
				return keel >= KEEL_CRYSTAL;
			case FETID:
				return inoculation;
			case KELP:
				return helm >= HELM_KELP;
			default:
				return true;
		}
	}

	/** Bit per {@link SeaArea} ordinal: the areas it may enter. */
	public int areaMask()
	{
		int m = 0;
		for (SeaArea a : SeaArea.values())
		{
			if (canEnter(a))
			{
				m |= 1 << a.ordinal();
			}
		}
		return m;
	}

	/** How routes name the boat: the player's own name for it when known ("Jolly Raven"), else "Boat 2: Skiff". */
	public String label()
	{
		return name != null ? name : "Boat " + slot + ": " + type.getLabel();
	}

	/** "Skiff, teak hull, oak sails, ..." for tooltips. */
	public String describe()
	{
		StringBuilder sb = new StringBuilder(type.getLabel()).append(", ").append(WOODS[hull])
			.append(type == BoatType.RAFT ? " base" : " hull").append(" (").append(speed()).append(" tiles/tick), ")
			.append(WOODS[sail]).append(" sails");
		if (keel >= 0)
		{
			sb.append(", ").append(METALS[keel]).append(" keel");
		}
		sb.append(", ").append(METALS[helm]).append(" helm");
		if (inoculation)
		{
			sb.append(", Inoculation station");
		}
		if (brazier)
		{
			sb.append(", Eternal brazier");
		}
		if (focus != Focus.NONE)
		{
			sb.append(", ").append(focus.getLabel());
		}
		return sb.toString();
	}
}

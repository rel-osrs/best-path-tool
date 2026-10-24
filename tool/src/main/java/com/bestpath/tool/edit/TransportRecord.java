package com.bestpath.tool.edit;

/**
 * A transport created by hand or confirmed from a suggestion (stored inside a change
 * record). Plain data, serialised to JSON. A {@link Kind#TELEPORT} has no
 * start: it can be used from anywhere.
 */
public final class TransportRecord
{
	public enum Kind
	{
		/** Works both ways (ladder, stairs, shortcut). */
		TWO_WAY("Two-way"),
		/** Only from start to end (drop-down, one-way shortcut). */
		ONE_WAY("One-way"),
		/** No start: usable from anywhere (spell, jewellery). */
		TELEPORT("Teleport (from anywhere)"),
		/**
		 * One stop of a network (fairy rings, quetzals, charter ships): usable
		 * only at a stop, to any other stop of the same {@link #network}.
		 * Start and end are the same tile.
		 */
		NETWORK("Network stop");

		private final String label;

		Kind(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** Stable id, e.g. "t-1728458123456-3". */
	public String id;
	public Kind kind = Kind.TWO_WAY;
	public String name = "";
	/**
	 * Category for transports with an end the player cannot stand on (found by
	 * a check, drawn red). The transport still works as before; the change's
	 * note records the category it had.
	 */
	public static final String NEEDS_FIX = "NEEDS_FIX";

	/** A {@link com.bestpath.tool.transport.TransportCandidate.Category} name, "CUSTOM" or {@link #NEEDS_FIX}. */
	public String category = "CUSTOM";
	public int startX;
	public int startY;
	public int startPlane;
	public int endX;
	public int endY;
	public int endPlane;
	public int ticks = 2;
	/** True while the tick cost is a default, not a measured or chosen value. */
	public boolean ticksEstimate = true;
	/** The cache object this transport belongs to (-1 if none), with its position. */
	public int objectId = -1;
	public int objectX;
	public int objectY;
	public int objectPlane;
	/** Where it came from: "suggestion: STACKED", "manual", ... */
	public String source = "manual";
	/**
	 * The network's name, e.g. "Fairy ring" (a line of {@code data/manual/networks.tsv}; empty = none). Needed for
	 * {@link Kind#NETWORK}; other kinds may belong to one too (a charter ship ride): the ride then also needs the
	 * network's requirement and costs its extra ticks.
	 */
	public String network = "";
	/** The teleport suggestion this was confirmed from (its id), or null. */
	public String suggestionId;
	/**
	 * What the player needs to use it (a {@link com.bestpath.core.req.RequirementText} line; empty = nothing).
	 * For a two-way transport: start to end, and also end to start unless {@link #requirementsBack} is set.
	 */
	public String requirements = "";
	/** Two-way only: what end to start needs, when it differs from {@link #requirements}; null = the same. */
	public String requirementsBack;
	/** Where the requirements came from (Wiki page and revision, Shortest Path, cache). */
	public String requirementsSource = "";
	/** Only during a quest (a quest instance, a quest-only battle): never used for normal routes. */
	public boolean questOnly;
	/** The player can fail it (an Agility obstacle); still costs its normal ticks. */
	public boolean canFail;
	/** Teleports: the category whose adjustable tick cost it uses (data/manual/teleport-categories.tsv); empty = its own ticks. */
	public String teleportCategory = "";
	/** Teleports: the deepest Wilderness level it works from (20, 30; 0 = not in the Wilderness); null = no limit. */
	public Integer wildernessLimit;
	/** A long cooldown (home teleports, grouping teleports); a plugin setting can switch these off. */
	public boolean cooldown;
	/**
	 * Text the plugin shows the player on this step, for things the route search does not track (weight limits,
	 * "no weapons or armour", landing damage); empty = none.
	 */
	public String playerNote = "";
	/**
	 * Only a boat can use it (e.g. the Wyrmscraig cave): the widest boat that fits, in tiles (1 raft, 2 skiff,
	 * 3 sloop; a sloop does not fit in that cave); 0 = not boat-only. Both ends
	 * are on water; only the sailing search uses it, the player staying on the boat. Two-way and one-way only.
	 */
	public int largestBoat;

	public TransportRecord copy()
	{
		TransportRecord r = new TransportRecord();
		r.id = id;
		r.kind = kind;
		r.name = name;
		r.category = category;
		r.startX = startX;
		r.startY = startY;
		r.startPlane = startPlane;
		r.endX = endX;
		r.endY = endY;
		r.endPlane = endPlane;
		r.ticks = ticks;
		r.ticksEstimate = ticksEstimate;
		r.objectId = objectId;
		r.objectX = objectX;
		r.objectY = objectY;
		r.objectPlane = objectPlane;
		r.source = source;
		r.network = network;
		r.suggestionId = suggestionId;
		r.requirements = requirements;
		r.requirementsBack = requirementsBack;
		r.requirementsSource = requirementsSource;
		r.questOnly = questOnly;
		r.canFail = canFail;
		r.teleportCategory = teleportCategory;
		r.wildernessLimit = wildernessLimit;
		r.cooldown = cooldown;
		r.playerNote = playerNote;
		r.largestBoat = largestBoat;
		return r;
	}

	/** The requirement line for end to start (two-way): its own line if set, else the same as start to end. */
	public String requirementsEndToStart()
	{
		return requirementsBack != null ? requirementsBack : requirements;
	}

	/** " needs: ..." for lists, with the marks; empty when there is nothing. */
	public String requirementNote()
	{
		StringBuilder sb = new StringBuilder();
		if (requirements != null && !requirements.isBlank())
		{
			sb.append(" needs: ").append(requirements);
		}
		if (requirementsBack != null && kind == Kind.TWO_WAY)
		{
			sb.append(" | back: ").append(requirementsBack.isBlank() ? "nothing" : requirementsBack);
		}
		if (questOnly)
		{
			sb.append(" [quest only]");
		}
		if (canFail)
		{
			sb.append(" [can fail]");
		}
		if (network != null && !network.isEmpty() && kind != Kind.NETWORK)
		{
			sb.append(" [network ").append(network).append("]");
		}
		if (teleportCategory != null && !teleportCategory.isEmpty())
		{
			sb.append(" [").append(teleportCategory).append("]");
		}
		if (wildernessLimit != null)
		{
			sb.append(wildernessLimit == 0 ? " [not in the Wilderness]" : " [Wilderness up to " + wildernessLimit + "]");
		}
		if (cooldown)
		{
			sb.append(" [cooldown]");
		}
		if (largestBoat > 0)
		{
			sb.append(largestBoat >= 3 ? " [boats only]" : largestBoat == 2 ? " [rafts and skiffs only]" : " [rafts only]");
		}
		if (playerNote != null && !playerNote.isBlank())
		{
			sb.append(" [note for the player: ").append(playerNote).append("]");
		}
		return sb.toString();
	}

	public boolean hasStart()
	{
		return kind != Kind.TELEPORT;
	}

	/** Short description for lists. */
	public String describe()
	{
		if (kind == Kind.NETWORK)
		{
			return String.format("Network stop '%s' (%s) at %d,%d,%d (%d ticks%s)", name, network, endX, endY, endPlane,
				ticks, ticksEstimate ? ", estimate" : "");
		}
		String from = hasStart() ? startX + "," + startY + "," + startPlane : "anywhere";
		return String.format("%s '%s' %s -> %d,%d,%d (%d ticks%s)", kind.getLabel(), name, from, endX, endY, endPlane,
			ticks, ticksEstimate ? ", estimate" : "");
	}
}

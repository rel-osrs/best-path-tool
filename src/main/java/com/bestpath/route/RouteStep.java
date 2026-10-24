package com.bestpath.route;

import com.bestpath.core.path.Tiles;
import java.util.Collections;
import java.util.List;

/**
 * One step of a route as the sidebar and overlays show it: text only (no map
 * images), its ticks and, for a transport, which transport it is. Immutable.
 */
public final class RouteStep
{
	/** What kind of step (the coloured label on its card). */
	public enum Kind
	{
		WALK("Walk"),
		/** Ladder, stairs, agility shortcut... */
		TRAVERSAL("Climb"),
		DOOR("Door"),
		TELEPORT("Tele"),
		BOAT("Boat"),
		/** Sailing your own boat. */
		SAIL("Sail"),
		/** Using the bank at the start of a route: take out what later steps need. */
		BANK("Bank"),
		/**
		 * A stop the route makes first: put the items taken out of a STASH unit back ({@link RouteRequest.Stop});
		 * "Skip" on its card skips the stop.
		 */
		STASH("Stash"),
		/**
		 * A stop the route makes first: kill the NPC that drops a key clue's key, then go on to the clue's place
		 * ({@link RouteRequest.Stop}); "Skip" on its card skips the stop for this route.
		 */
		KILL("Kill");

		private final String label;

		Kind(String label)
		{
			this.label = label;
		}

		/** A short label for the card's badge (fits in ~34 px). */
		public String getLabel()
		{
			return label;
		}

		/** Anything but a walk or a sail: a transport, and also a bank visit or a first stop. */
		public boolean isTransport()
		{
			return this != WALK && this != SAIL;
		}

		/** A step that takes the player somewhere: a transport, not a bank visit or a first stop. */
		public boolean isTravel()
		{
			return this == TRAVERSAL || this == DOOR || this == TELEPORT || this == BOAT;
		}

		/** A first stop of the route ({@link RouteRequest.Stop}): its card has Skip only, no ticks to change. */
		public boolean isStop()
		{
			return this == STASH || this == KILL;
		}
	}

	private final Kind kind;
	private final String title;
	private final String detail;
	private final int ticks;
	private final List<String> notes;
	private final String transportId;
	private final int[] tiles;
	private final int markerTile;
	private final int endTile;
	/** For the sidebar's icons: the transport's sort and what it needs. */
	private final StepInfo info;
	/** The player's own tick change the route used for this step's transport (0 = none). */
	private final int tickChange;
	/**
	 * What the card's - / + changes: a network ride's whole network ({@link TransportSettings#networkKey}), else
	 * null = this transport ({@link TransportSettings#tickKey}).
	 */
	private final String tickGroup;
	/**
	 * A sail step's course is straight legs in the boat's 16 headings, drawn as arrows: indexes into the tiles where a
	 * leg starts or ends (first tile, every turn, last tile); empty for every other step.
	 */
	private final int[] corners;
	/**
	 * A sail step's corners exactly (the boat's headings are exact compass points 22.5 degrees apart): x, y pairs in
	 * world tiles (a tile's middle is its whole x / y; a turn need not be on a whole tile);
	 * empty for every other step.
	 */
	private final double[] points;
	/** What the player clicks for this step (text and glow in the game); {@link ClickHint#NONE} when nothing. */
	private final ClickHint click;
	/** The words written in the game world ({@link GameText}); null = the title. */
	private final String gameText;
	private static final int[] NO_CORNERS = new int[0];
	private static final double[] NO_POINTS = new double[0];

	/**
	 * @param title       what to do, e.g. "Climb up the ladder"
	 * @param detail      extra line (may be empty), e.g. "34 tiles to 3210, 3224"
	 * @param notes       requirement and player notes ("Needs Agility 52", "No weapons...")
	 * @param transportId the transport's stable id, or null for a walk (used for skipping / blacklisting)
	 * @param tiles       packed tiles (core Tiles.pack) the overlay draws as the path; may be empty
	 * @param markerTile  packed tile the overlay marks (where the transport is used), or -1
	 */
	public RouteStep(Kind kind, String title, String detail, int ticks, List<String> notes, String transportId,
		int[] tiles, int markerTile)
	{
		this(kind, title, detail, ticks, notes, transportId, tiles, markerTile,
			tiles.length == 0 ? -1 : tiles[tiles.length - 1]);
	}

	/**
	 * Like the other constructor, plus where the player is after this step (for a transport: its landing tile),
	 * which route progress uses to move on to the next step; -1 = not known (progress then waits until the
	 * player shows up on a later step).
	 */
	public RouteStep(Kind kind, String title, String detail, int ticks, List<String> notes, String transportId,
		int[] tiles, int markerTile, int endTile)
	{
		this.kind = kind;
		this.title = title;
		this.detail = detail == null ? "" : detail;
		this.ticks = ticks;
		this.notes = Collections.unmodifiableList(List.copyOf(notes));
		this.transportId = transportId;
		this.tiles = tiles.clone();
		this.markerTile = markerTile;
		this.endTile = endTile;
		this.info = StepInfo.NONE;
		this.tickChange = 0;
		this.tickGroup = null;
		this.corners = NO_CORNERS;
		this.points = NO_POINTS;
		this.click = ClickHint.NONE;
		this.gameText = null;
	}

	/**
	 * A changeable copy of a step: each with... method changes one part of it and makes the new step ({@link #build}).
	 * What the route found (kind, text, ticks, tiles) never changes.
	 */
	private static final class Copy
	{
		private final RouteStep from;
		private String title;
		private String gameText;
		private StepInfo info;
		private int tickChange;
		private String tickGroup;
		private int[] corners;
		private double[] points;
		private ClickHint click;

		private Copy(RouteStep s)
		{
			from = s;
			title = s.title;
			gameText = s.gameText;
			info = s.info;
			tickChange = s.tickChange;
			tickGroup = s.tickGroup;
			corners = s.corners;
			points = s.points;
			click = s.click;
		}

		private RouteStep build()
		{
			return new RouteStep(this);
		}
	}

	private RouteStep(Copy c)
	{
		RouteStep s = c.from;
		this.kind = s.kind;
		this.detail = s.detail;
		this.ticks = s.ticks;
		this.notes = s.notes;
		this.transportId = s.transportId;
		this.tiles = s.tiles;
		this.markerTile = s.markerTile;
		this.endTile = s.endTile;
		this.title = c.title;
		this.gameText = c.gameText;
		this.info = c.info == null ? StepInfo.NONE : c.info;
		this.tickChange = c.tickChange;
		this.tickGroup = c.tickGroup;
		this.corners = c.corners;
		this.points = c.points;
		this.click = c.click == null ? ClickHint.NONE : c.click;
	}

	/** This step with what the player clicks for it. */
	public RouteStep withClick(ClickHint c)
	{
		Copy copy = new Copy(this);
		copy.click = c;
		return copy.build();
	}

	/** This step with another title (a house portal's words as the player's house has it). */
	public RouteStep withTitle(String t)
	{
		Copy copy = new Copy(this);
		copy.title = t;
		copy.gameText = null;
		return copy.build();
	}

	/**
	 * This step with the words written in the game world, shorter than the title ({@link GameText}); null = the
	 * title.
	 */
	public RouteStep withGameText(String words)
	{
		Copy copy = new Copy(this);
		copy.gameText = words == null || words.equals(title) ? null : words;
		return copy.build();
	}

	/**
	 * The words written in the game world for this step: shorter than the title for some transports
	 * ({@link GameText}).
	 */
	public String getGameText()
	{
		return gameText == null ? title : gameText;
	}

	/**
	 * What the player clicks for this step: menu words, object, items, spell ({@link ClickHint#NONE}: nothing known).
	 */
	public ClickHint getClick()
	{
		return click;
	}

	/**
	 * This sail step with its course (core {@code Route.Step.getCorners()} and {@code getCoursePoints()}):
	 * the corners as indexes into its tiles (in order, the first 0 and the last the last tile's; two may share a tile
	 * when a leg is shorter than a tile) and exactly, as x, y pairs in world tiles (a turn need not be on a whole
	 * tile). Each leg between two corners is straight in one of the boat's exact headings. Invalid lists are refused.
	 */
	public RouteStep withCourse(int[] cornerIndexes, double[] cornerPoints)
	{
		int[] c = cornerIndexes.clone();
		for (int i = 0; i < c.length; i++)
		{
			if (c[i] < 0 || c[i] >= tiles.length || i > 0 && c[i] < c[i - 1])
			{
				throw new IllegalArgumentException("corners must be tile indexes in order");
			}
		}
		if (cornerPoints.length != 2 * c.length)
		{
			throw new IllegalArgumentException("one point per corner");
		}
		boolean course = c.length >= 2;
		Copy copy = new Copy(this);
		copy.corners = course ? c : NO_CORNERS;
		copy.points = course ? cornerPoints.clone() : NO_POINTS;
		return copy.build();
	}

	/** Corners of a sail step's course (0 for every other step, and for a course without them: draw its tiles). */
	public int cornerCount()
	{
		return corners.length;
	}

	/** Corner i: an index into the step's tiles ({@link #tile(int)}). */
	public int corner(int i)
	{
		return corners[i];
	}

	/** Corner i exactly: its world x in tiles (a tile's middle is its whole x; a turn need not be on a whole tile). */
	public double cornerX(int i)
	{
		return points[2 * i];
	}

	/** Corner i exactly: its world y in tiles. */
	public double cornerY(int i)
	{
		return points[2 * i + 1];
	}

	/** This step with the sidebar's icon info (what sort of transport it is and what it needs). */
	public RouteStep withInfo(StepInfo info)
	{
		Copy copy = new Copy(this);
		copy.info = info;
		return copy.build();
	}

	/**
	 * This step with the player's own tick change (the card's - / +) the route used for its transport;
	 * {@link #getTicks()} already has it.
	 */
	public RouteStep withTickChange(int change)
	{
		Copy copy = new Copy(this);
		copy.tickChange = change;
		return copy.build();
	}

	/**
	 * This network ride's step with its whole network's change (on a network ride's card, - / + changes the whole
	 * network): {@code change} is how much the network's change moved this step's ticks, {@code group} the network's
	 * key ({@link TransportSettings#networkKey}) the card's - / + changes.
	 */
	public RouteStep withGroupTickChange(int change, String group)
	{
		Copy copy = new Copy(this);
		copy.tickChange = change;
		copy.tickGroup = group;
		return copy.build();
	}

	/**
	 * The key the card's - / + changes: the network's ({@link TransportSettings#networkKey}) for a network ride, else
	 * the transport's ({@link TransportSettings#tickKey}); null for a step without a transport.
	 */
	public String getTickKey()
	{
		return tickGroup != null ? tickGroup : transportId == null ? null : TransportSettings.tickKey(transportId);
	}

	/** True when the card's - / + changes the whole network (a {@link TransportSettings#networkKey}). */
	public boolean isGroupTicks()
	{
		return tickGroup != null;
	}

	/**
	 * How much the player's own change on this transport's card moved this step's ticks (0 = none): its ticks without
	 * that change are {@code getTicks() - getTickChange()} (the data's, with any teleport category or network change
	 * the player made in the Settings tab).
	 */
	public int getTickChange()
	{
		return tickChange;
	}

	/** What sort of transport it is and what it needs ({@link StepInfo#NONE} for a walk). */
	public StepInfo getInfo()
	{
		return info;
	}

	public Kind getKind()
	{
		return kind;
	}

	public String getTitle()
	{
		return title;
	}

	public String getDetail()
	{
		return detail;
	}

	public int getTicks()
	{
		return ticks;
	}

	public List<String> getNotes()
	{
		return notes;
	}

	/** Null for a walk. */
	public String getTransportId()
	{
		return transportId;
	}

	public int tileCount()
	{
		return tiles.length;
	}

	public int tile(int i)
	{
		return tiles[i];
	}

	public int getMarkerTile()
	{
		return markerTile;
	}

	/** Packed tile where the player is after this step (a walk's last tile, a transport's landing), or -1. */
	public int getEndTile()
	{
		return endTile;
	}
}

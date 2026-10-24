package com.bestpath.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the Settings tab's "House (POH)" section shows: where the player's house is, what the house scan found built
 * in it and when it last looked. Made on the client thread from the house varbit and the remembered scan, handed to
 * Swing. Immutable; pure.
 */
public final class HouseView
{
	/** Why there may be no house shown. */
	public enum Status
	{
		/** Not logged in: the house place is read from the game once logged in. */
		NOT_LOGGED_IN,
		/** Logged in, and the game says the player has no house (POH_HOUSE_LOCATION 0). */
		NO_HOUSE,
		/** Logged in with a house. */
		HOUSE
	}

	/** One line of the section: what is built (one thing, or a portal nexus with its destinations). */
	public static final class Row
	{
		/** Which picture the row gets. */
		public enum Kind
		{
			JEWELLERY_BOX, MOUNTED_GLORY, MOUNTED_MYTHICAL_CAPE, MOUNTED_DIGSITE_PENDANT, MOUNTED_XERICS_TALISMAN,
			PORTAL_CHAMBER, PORTAL_NEXUS, GARDEN, OBELISK, ALTAR, OTHER
		}

		private final Kind kind;
		private final String text;
		private final String detail;
		/** For a portal chamber portal: its destination (the teleport's name, for its spell icon); else null. */
		private final String destination;

		Row(Kind kind, String text, String detail, String destination)
		{
			this.kind = kind;
			this.text = text;
			this.detail = detail;
			this.destination = destination;
		}

		public Kind getKind()
		{
			return kind;
		}

		/** The row's text, e.g. "Ornate jewellery box", "Portal: Varrock Teleport", "Portal nexus (3 destinations)". */
		public String getText()
		{
			return text;
		}

		/** A second, muted line (a nexus's destinations), or null. */
		public String getDetail()
		{
			return detail;
		}

		/** A portal's destination, or null. */
		public String getDestination()
		{
			return destination;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Row))
			{
				return false;
			}
			Row r = (Row) o;
			return kind == r.kind && text.equals(r.text) && Objects.equals(detail, r.detail) && Objects.equals(destination, r.destination);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(kind, text, detail, destination);
		}

		@Override
		public String toString()
		{
			return text + (detail == null ? "" : " [" + detail + "]");
		}
	}

	/** The hint shown until the house was looked at. */
	public static final String NOT_LOOKED_HINT = "Go into your house with Teleport to House, a house tablet, or the "
		+ "Construction cape (or turn on building mode) so Best Path can see what's built.";

	public static final HouseView NOT_LOGGED_IN = new HouseView(Status.NOT_LOGGED_IN, null, Set.of(), 0);

	private final Status status;
	private final String place;
	/** The "house has" names the last scan found (sorted; tests read it). */
	final Set<String> things;
	private final long scannedAt;

	private HouseView(Status status, String place, Set<String> things, long scannedAt)
	{
		this.status = status;
		this.place = place;
		this.things = Collections.unmodifiableSet(new TreeSet<>(things));
		this.scannedAt = scannedAt;
	}

	/**
	 * A logged-in player's house.
	 *
	 * @param place     where the house is ({@link SnapshotMapper#housePlace(int, String)}), null = no house
	 * @param things    what the last scan of the own house found ("house has" names)
	 * @param scannedAt when that scan was (epoch milliseconds), 0 = not looked at yet on this account
	 */
	public static HouseView of(String place, Set<String> things, long scannedAt)
	{
		return new HouseView(place == null ? Status.NO_HOUSE : Status.HOUSE, place, things, Math.max(0, scannedAt));
	}

	public Status getStatus()
	{
		return status;
	}

	/** When the house was last looked at (epoch milliseconds), 0 = never. */
	public long getScannedAt()
	{
		return scannedAt;
	}

	/** The section's first line: "Your house: Rimmington", "No house found", "Log in to see your house." */
	public String placeText()
	{
		switch (status)
		{
			case NOT_LOGGED_IN:
				return "Log in to see your house.";
			case NO_HOUSE:
				return "No house found";
			default:
				return "Your house: " + capital(place);
		}
	}

	/**
	 * The lines of what is built: one per thing (sorted as below), portal chamber portals one each with their
	 * destination, the portal nexus one line with its destinations under it. Empty when nothing was found or nothing
	 * is known yet.
	 */
	public List<Row> rows()
	{
		List<Row> out = new ArrayList<>();
		List<String> nexus = new ArrayList<>();
		List<Row> portals = new ArrayList<>();
		List<Row> rest = new ArrayList<>();
		for (String t : things)
		{
			String lower = t.toLowerCase(Locale.ROOT);
			if (lower.startsWith(HouseFurniture.NEXUS_PREFIX))
			{
				nexus.add(t.substring(HouseFurniture.NEXUS_PREFIX.length()).trim());
			}
			else if (lower.startsWith(CHAMBER_PREFIX))
			{
				String d = t.substring(CHAMBER_PREFIX.length()).trim();
				portals.add(new Row(Row.Kind.PORTAL_CHAMBER, "Portal: " + d, null, d));
			}
			else
			{
				rest.add(new Row(kindOf(lower), capital(t), null, null));
			}
		}
		rest.sort((a, b) -> a.kind != b.kind ? a.kind.compareTo(b.kind) : a.text.compareToIgnoreCase(b.text));
		out.addAll(rest);
		out.addAll(portals);
		if (!nexus.isEmpty())
		{
			out.add(new Row(Row.Kind.PORTAL_NEXUS, "Portal nexus (" + nexus.size() + (nexus.size() == 1 ? " destination)" : " destinations)"),
				String.join(", ", nexus), null));
		}
		return out;
	}

	/** The things of the portal chamber: "portal chamber: Varrock Teleport". */
	static final String CHAMBER_PREFIX = "portal chamber: ";

	static Row.Kind kindOf(String thing)
	{
		String t = thing.toLowerCase(Locale.ROOT);
		if (t.endsWith("jewellery box"))
		{
			return Row.Kind.JEWELLERY_BOX;
		}
		switch (t)
		{
			case "mounted glory":
				return Row.Kind.MOUNTED_GLORY;
			case "mounted mythical cape":
				return Row.Kind.MOUNTED_MYTHICAL_CAPE;
			case "mounted digsite pendant":
				return Row.Kind.MOUNTED_DIGSITE_PENDANT;
			case "mounted xeric's talisman":
				return Row.Kind.MOUNTED_XERICS_TALISMAN;
			case "obelisk":
				return Row.Kind.OBELISK;
			case "spirit tree":
			case "fairy ring":
			case "spirit tree & fairy ring":
				return Row.Kind.GARDEN;
			default:
				return t.endsWith("altar") ? Row.Kind.ALTAR : Row.Kind.OTHER;
		}
	}

	private static String capital(String s)
	{
		return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Same section content. */
	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof HouseView))
		{
			return false;
		}
		HouseView v = (HouseView) o;
		return status == v.status && Objects.equals(place, v.place) && things.equals(v.things) && scannedAt == v.scannedAt;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(status, place, things, scannedAt);
	}

	@Override
	public String toString()
	{
		return placeText() + " " + things + (scannedAt == 0 ? " (not looked at yet)" : " (looked at " + scannedAt + ")");
	}
}

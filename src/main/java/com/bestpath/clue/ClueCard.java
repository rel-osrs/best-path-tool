package com.bestpath.clue;

import com.bestpath.core.data.ClueData;
import com.bestpath.core.data.Tables;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.route.DestinationNeeds;
import com.bestpath.route.NeedStatus;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.StepInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;
import net.runelite.api.gameval.ItemID;

/**
 * What the clue step's own card shows (the last card of a clue route): the clue's tier and type, its text, what to
 * do there ("Dig here", "Perform the Cheer emote", "Talk to Ranael"), every item the step needs with where the player
 * has it (worn, in the inventory, in the STASH unit, in the bank, missing), the STASH unit, and one line saying whether
 * everything is ready. Pure and immutable: made on the client thread from a player snapshot ({@link #build}), shown on
 * the Swing thread. Item names come from the caller (RuneLite's item definitions in the plugin).
 *
 * <p>The items also make the route request's {@link DestinationNeeds} ({@link #destinationNeeds}): everything but
 * what is known to wait in a filled STASH unit.
 */
public final class ClueCard
{
	/** Where the player has a needed item. */
	public enum Where
	{
		WORN("Worn"),
		CARRIED("In your inventory"),
		STASH("In the STASH unit"),
		/**
		 * Not carried or banked, and the step's STASH unit is built but not known to be filled or empty (a new install,
		 * or a unit Best Path has not seen yet): it may be there, so it is shown with a question mark, never as missing.
		 */
		MAYBE_STASH("Maybe in the STASH unit"),
		BANK("In your bank"),
		MISSING("Missing");

		private final String label;

		Where(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	/** The card as a whole. */
	public enum State
	{
		/** Everything carried, worn or waiting in the STASH unit. */
		READY,
		/** Something has to come from the bank first. */
		BANK,
		/** Something is nowhere (or a level / quest is missing). */
		MISSING
	}

	/** One needed item: the item shown, its name, the item ids any of which will do. */
	public static final class ItemRow
	{
		/** The item's name (tests read it). */
		final String name;
		private final int itemId;
		private final int[] ids;
		/** How many are needed (tests read it). */
		final int count;
		private final boolean wear;
		private final Where where;

		ItemRow(String name, int itemId, int[] ids, int count, boolean wear, Where where)
		{
			this.name = name;
			this.itemId = itemId;
			this.ids = ids.clone();
			this.count = count;
			this.wear = wear;
			this.where = where;
		}

		/** The item whose picture is shown (the player's own if they have one). */
		public int getItemId()
		{
			return itemId;
		}

		/** True if it has to be worn (an emote clue's outfit). */
		public boolean isWear()
		{
			return wear;
		}

		public Where getWhere()
		{
			return where;
		}

		/** "Chef's hat", "Ecto-token x25". */
		public String label()
		{
			return name + (count > 1 ? " x" + count : "");
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof ItemRow))
			{
				return false;
			}
			ItemRow r = (ItemRow) o;
			return r.name.equals(name) && r.itemId == itemId && Arrays.equals(r.ids, ids) && r.count == count
				&& r.wear == wear && r.where == where;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(name, itemId, count, where);
		}

		@Override
		public String toString()
		{
			return label() + ": " + where;
		}
	}

	/** A level or quest the step needs, and whether the player has it. */
	public static final class OtherRow
	{
		private final String text;
		private final boolean met;
		/** Once met, met for good (a level, a quest): its met row is left off the card. */
		private final boolean lasting;

		OtherRow(String text, boolean met)
		{
			this(text, met, true);
		}

		OtherRow(String text, boolean met, boolean lasting)
		{
			this.text = text;
			this.met = met;
			this.lasting = lasting;
		}

		/** True for a level or quest (met for good); false for what can change (the slots that must be empty). */
		public boolean isLasting()
		{
			return lasting;
		}

		public String getText()
		{
			return text;
		}

		public boolean isMet()
		{
			return met;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof OtherRow && ((OtherRow) o).text.equals(text) && ((OtherRow) o).met == met;
		}

		@Override
		public int hashCode()
		{
			return text.hashCode() * 2 + (met ? 1 : 0);
		}

		@Override
		public String toString()
		{
			return text + (met ? "" : " (not met)");
		}
	}

	private final String requestKey;
	private final String title;
	private final String text;
	private final String place;
	private final String note;
	private final int clueItemId;
	private final List<ItemRow> items;
	private final List<OtherRow> others;
	private final boolean hasStash;
	/** The STASH chart / Watson's noticeboard can show this unit filled (all but {@link StashMemory#CHART_HIDES_FILLED}). */
	private final boolean chartShowsFilled;
	private final StashState stash;
	private final State state;
	private final String status;
	private final boolean playerKnown;

	private ClueCard(Builder b)
	{
		this.requestKey = b.requestKey;
		this.title = b.title;
		this.text = b.text;
		this.place = b.place;
		this.note = b.note;
		this.clueItemId = b.clueItemId;
		this.items = Collections.unmodifiableList(new ArrayList<>(b.items));
		this.others = Collections.unmodifiableList(new ArrayList<>(b.others));
		this.hasStash = b.hasStash;
		this.chartShowsFilled = b.chartShowsFilled;
		this.stash = b.stash;
		this.state = b.state;
		this.status = b.status;
		this.playerKnown = b.playerKnown;
	}

	private static final class Builder
	{
		String requestKey = "";
		String title = "";
		String text = "";
		String place = "";
		String note = "";
		int clueItemId = -1;
		List<ItemRow> items = new ArrayList<>();
		List<OtherRow> others = new ArrayList<>();
		boolean hasStash;
		boolean chartShowsFilled = true;
		StashState stash = StashState.UNKNOWN;
		State state = State.READY;
		String status = "";
		boolean playerKnown;
		/** Slots that must be empty (the game's blank item in the clue's outfit), shown as one row. */
		Set<EquipSlot> emptySlots = EnumSet.noneOf(EquipSlot.class);
		PlayerState player;
	}

	/** The beginner and master clue scrolls, each one item shared by every step of its tier. */
	static final int BEGINNER_SCROLL = ItemID.TRAIL_CLUE_BEGINNER;
	static final int MASTER_SCROLL = ItemID.TRAIL_CLUE_MASTER;

	/**
	 * The card for the carried clue, or null when there is no step to show (no clue, not read yet, not found).
	 *
	 * @param player   the player's snapshot (null: not logged in, the card then asks to log in); a player with "every
	 *                 item" ({@link PlayerState#isAllItems}) has everything carried
	 * @param stash    the step's STASH unit as read from the game ({@link StashState#UNKNOWN} without one)
	 * @param groups   the map data's item groups (to read the needs line)
	 * @param tables   the map data's tables (an item group's default picture); may be null
	 * @param itemName an item's name (RuneLite's item definition in the plugin); may give null
	 */
	public static ClueCard build(DetectedClue clue, PlayerState player, StashState stash, RequirementText.ItemGroups groups,
		Tables tables, IntFunction<String> itemName)
	{
		if (clue == null || !clue.isRoutable() || clue.getSteps().isEmpty())
		{
			return null;
		}
		ClueData.Step s = clue.getSteps().get(0);
		boolean hotCold = clue.getKind() == DetectedClue.Kind.HOT_COLD;
		Builder b = new Builder();
		b.requestKey = clue.request() == null ? "" : clue.request().getKey();
		b.title = clue.title();
		b.text = hotCold ? "" : s.getText();
		b.place = hotCold ? "" : s.getDescription();
		b.note = hotCold ? "" : s.getNote(); // the hand-written note on the step
		b.clueItemId = clue.getItemId() >= 0 ? clue.getItemId() : s.getItems().length > 0 ? s.getItems()[0]
			: "beginner".equals(clue.getTier()) ? BEGINNER_SCROLL : "master".equals(clue.getTier()) ? MASTER_SCROLL : -1;
		b.hasStash = s.getStash() != -1;
		b.chartShowsFilled = !StashMemory.CHART_HIDES_FILLED.contains(s.getStashVarbit());
		b.stash = stash == null ? StashState.UNKNOWN : stash;
		b.playerKnown = player != null;
		Requirement needs;
		try
		{
			needs = RequirementText.parse(s.getNeeds(), groups == null ? RequirementText.ItemGroups.NONE : groups);
		}
		catch (RequirementText.ParseException e)
		{
			needs = Requirement.NONE; // every line is checked when the data is generated; older data may not be
		}
		b.player = player;
		collect(needs, b, player, tables, itemName == null ? id -> null : itemName);
		status(b);
		if (player == null)
		{
			b.status = "Log in to see which of these items you have.";
		}
		return new ClueCard(b);
	}

	/** Item rows and level / quest rows, in the needs line's order. */
	private static void collect(Requirement r, Builder b, PlayerState p, Tables tables, IntFunction<String> itemName)
	{
		if (r instanceof Requirement.All)
		{
			for (Requirement part : ((Requirement.All) r).getParts())
			{
				collect(part, b, p, tables, itemName);
			}
		}
		else if (r instanceof Requirement.AnyOf)
		{
			// the choice that applies to the player, as the route search picks it; none met: the first (missing)
			Requirement.AnyOf any = (Requirement.AnyOf) r;
			int i = p == null ? 0 : any.chosen(p);
			Requirement alt = any.getAlternatives().get(Math.max(0, i));
			if (i >= 0 && !hasItem(alt))
			{
				return; // met without an item (e.g. the Fire of Eternal Light lit): nothing to bring
			}
			collect(alt, b, p, tables, itemName);
		}
		else if (r instanceof Requirement.Item)
		{
			b.items.add(itemRow((Requirement.Item) r, b, p, tables, itemName));
		}
		else if (r instanceof Requirement.SkillLevel)
		{
			Requirement.SkillLevel sk = (Requirement.SkillLevel) r;
			boolean met = p == null || p.level(sk.getSkill()) >= sk.getLevel();
			b.others.add(new OtherRow(sk.getSkill().getLabel() + " " + sk.getLevel()
				+ (met || p == null ? "" : " (you have " + p.level(sk.getSkill()) + ")"), met));
		}
		else if (r instanceof Requirement.Quest)
		{
			boolean met = p == null || r.check(p).isMet();
			b.others.add(new OtherRow("Quest: " + ((Requirement.Quest) r).getQuest(), met));
		}
		else if (r instanceof Requirement.EmptySlot)
		{
			// shown as "Wear: nothing", not as the game's blank item ("Item 6512"); one row for all such slots
			b.emptySlots.add(((Requirement.EmptySlot) r).getSlot());
		}
		// varbits, members and the like: nothing the player brings
	}

	/**
	 * The slots that must be empty, as one row: "Wear: nothing" when every slot, else "Wear nothing: feet" /
	 * "Wear nothing: neck, ring"; met when nothing is worn in any of them.
	 */
	private static OtherRow emptyRow(Set<EquipSlot> slots, PlayerState p)
	{
		boolean met = true;
		List<String> names = new ArrayList<>();
		List<String> worn = new ArrayList<>();
		for (EquipSlot s : EquipSlot.values())
		{
			if (!slots.contains(s))
			{
				continue;
			}
			names.add(s.getLabel());
			if (p != null && !p.isAllItems() && p.worn(s) != -1)
			{
				met = false;
				worn.add(s.getLabel());
			}
		}
		String text = slots.size() == EquipSlot.values().length ? "Wear: nothing" : "Wear nothing: " + String.join(", ", names);
		return new OtherRow(text + (met || p == null ? "" : " (take off: " + String.join(", ", worn) + ")"), met || p == null, false);
	}

	private static boolean hasItem(Requirement r)
	{
		boolean[] found = {false};
		r.visit(x -> found[0] |= x instanceof Requirement.Item);
		return found[0];
	}

	private static ItemRow itemRow(Requirement.Item it, Builder b, PlayerState p, Tables tables, IntFunction<String> itemName)
	{
		int[] ids = it.getItemIds();
		int shown = StepInfo.shownItem(it, p, tables);
		int count = it.getCount();
		Where where;
		if (p == null)
		{
			where = Where.MISSING;
		}
		else if (p.isAllItems())
		{
			where = Where.CARRIED;
		}
		else
		{
			int carried = 0;
			int banked = 0;
			for (int id : ids)
			{
				carried += p.carried(id);
				banked += p.banked(id);
			}
			if (carried >= count)
			{
				where = wornCount(p, ids) >= count ? Where.WORN : Where.CARRIED;
			}
			else if (it.isWorn() && b.hasStash && b.stash.holdsOutfit())
			{
				where = Where.STASH; // the outfit waits in the filled STASH unit at the spot
			}
			else if (carried + banked >= count)
			{
				where = Where.BANK;
			}
			else if (it.isWorn() && b.hasStash && b.stash.getBuilt() == StashState.Built.YES
				&& b.stash.getFilled() == StashState.Filled.UNKNOWN)
			{
				where = Where.MAYBE_STASH; // not known whether the unit holds it: never assumed empty
			}
			else
			{
				where = Where.MISSING;
			}
		}
		return new ItemRow(name(it, shown, p, itemName), shown, ids, count, it.isWorn(), where);
	}

	private static int wornCount(PlayerState p, int[] ids)
	{
		int n = 0;
		for (EquipSlot slot : EquipSlot.values())
		{
			int w = p.worn(slot);
			for (int id : ids)
			{
				if (w == id)
				{
					n++;
					break;
				}
			}
		}
		return n;
	}

	/** The shown item's name; an item group the player has none of: the group's name ("Light source"). */
	private static String name(Requirement.Item it, int shown, PlayerState p, IntFunction<String> itemName)
	{
		String label = it.getLabel();
		boolean group = !label.matches("[0-9/ ]+");
		boolean owns = false;
		if (p != null && !p.isAllItems())
		{
			for (int id : it.getItemIds())
			{
				owns |= p.carried(id) > 0 || p.banked(id) > 0;
			}
		}
		if (group && !owns)
		{
			return capital(label);
		}
		String n = itemName.apply(shown);
		return n == null || n.isEmpty() || n.equals("null") ? (group ? capital(label) : "Item " + shown) : n;
	}

	private static String capital(String s)
	{
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
	}

	/** The card's state and its one line of plain words. */
	private static void status(Builder b)
	{
		if (!b.emptySlots.isEmpty() && b.others.stream().noneMatch(o -> o.text.startsWith("Wear")))
		{
			b.others.add(0, emptyRow(b.emptySlots, b.player));
		}
		List<String> missing = new ArrayList<>();
		List<String> bank = new ArrayList<>();
		List<String> maybe = new ArrayList<>();
		boolean inStash = false;
		for (ItemRow r : b.items)
		{
			if (r.where == Where.MISSING)
			{
				missing.add(r.label());
			}
			else if (r.where == Where.BANK)
			{
				bank.add(r.label());
			}
			else if (r.where == Where.MAYBE_STASH)
			{
				maybe.add(r.label());
			}
			inStash |= r.where == Where.STASH;
		}
		// no "Maybe in the STASH unit" line: each item's row says it, and the Watson box below says what to do
		for (OtherRow o : b.others)
		{
			if (!o.met)
			{
				missing.add(o.text);
			}
		}
		if (!missing.isEmpty())
		{
			b.state = State.MISSING;
			b.status = "Missing: " + String.join(", ", missing) + "." + (bank.isEmpty() ? ""
				: " Take from the bank: " + String.join(", ", bank) + ".") + " The route still goes there.";
		}
		else if (!bank.isEmpty())
		{
			b.state = State.BANK;
			b.status = "Take from the bank: " + String.join(", ", bank) + ".";
		}
		else if (!maybe.isEmpty())
		{
			b.state = State.BANK;
			b.status = ""; // the rows say "Maybe in the STASH unit"; no line at the bottom
		}
		else
		{
			b.state = State.READY;
			b.status = b.items.isEmpty() ? "Nothing to bring." : inStash ? "Ready: search the STASH unit for your outfit."
				: "Everything is ready.";
		}
	}

	/**
	 * The quests the clue steps' needs name (e.g. Mourning's End Part I for Lletya), so the plugin reads their
	 * state like the transports' quests. Lines that can't be read are skipped.
	 */
	public static List<Requirement> questNeeds(ClueData data, RequirementText.ItemGroups groups)
	{
		Set<Requirement> out = new LinkedHashSet<>();
		for (ClueData.Step s : data.getSteps())
		{
			try
			{
				RequirementText.parse(s.getNeeds(), groups).visit(r ->
				{
					if (r instanceof Requirement.Quest)
					{
						out.add(r);
					}
				});
			}
			catch (RequirementText.ParseException e)
			{
				// every line is checked when the data is generated; older data may not be
			}
		}
		return new ArrayList<>(out);
	}

	/**
	 * What the route request carries: every needed item except those known to wait in the filled STASH unit (the
	 * route doesn't fetch what is already at the spot).
	 */
	public DestinationNeeds destinationNeeds()
	{
		List<DestinationNeeds.Item> out = new ArrayList<>();
		for (ItemRow r : items)
		{
			if (r.where != Where.STASH && r.where != Where.MAYBE_STASH)
			{
				out.add(new DestinationNeeds.Item(r.name, r.ids, r.count));
			}
		}
		return out.isEmpty() ? DestinationNeeds.NONE : new DestinationNeeds(out);
	}

	/** The STASH unit in words ("" without one). */
	public String stashLine()
	{
		if (!hasStash)
		{
			return "";
		}
		String where = "STASH unit"; // the place is already in the clue's lines above
		switch (stash.getBuilt())
		{
			case NO:
				return where + ": not built.";
			case YES:
				switch (stash.getFilled())
				{
					case YES:
						return where + ": built and filled.";
					case NO:
						return where + ": built, empty.";
					default:
						return where + ": built, filled not known.";
				}
			default:
				return where + ".";
		}
	}

	/**
	 * The hint shown for a unit that is "built, filled not known". Watson's noticeboard (object 29718, "Read") and the
	 * S.T.A.S.H chart on the study wall of the player's house (object 41434, "View") open the same STASH chart
	 * (interface 493). Not shown for a unit the chart never shows filled.
	 */
	public static final String LEARN_FILLED_HINT = "Go to Watson's house (Hosidius) and read his noticeboard to load your STASH units.";

	/**
	 * Watson's noticeboard (packed tile): object 29718 "Noticeboard" (Read), a wall decoration in Watson's house. The
	 * card offers a route there when a STASH unit's filled state is not known ({@link #learnsFromWatson}).
	 */
	public static final int WATSON_NOTICEBOARD = Tiles.pack(1644, 3575, 0);

	/**
	 * True when the card should send the player to Watson's noticeboard: the step's STASH unit is built, Best Path does
	 * not know whether it is filled, and the chart shows that unit's filled state.
	 */
	public boolean learnsFromWatson()
	{
		return hasStash && stash.getBuilt() == StashState.Built.YES && stash.getFilled() == StashState.Filled.UNKNOWN
			&& chartShowsFilled;
	}

	/** The route to Watson's noticeboard ("Take me there" on the card). */
	public static RouteRequest watsonRequest()
	{
		return RouteRequest.places("watson-noticeboard", "Read Watson's noticeboard",
			List.of("Read it once to load your STASH units, then press Use clue."),
			new int[]{WATSON_NOTICEBOARD});
	}

	/** The mouse-over of the STASH line: where "filled" comes from. */
	public static final String STASH_TOOLTIP = "Built and filled as last seen. Not known: the route gets the outfit from the bank.";

	/** The route request this card belongs to ("clue ..."). */
	public String getRequestKey()
	{
		return requestKey;
	}

	/** "Emote clue (easy)". */
	public String getTitle()
	{
		return title;
	}

	/** The clue text ("" for maps and hot/cold). */
	public String getText()
	{
		return text;
	}

	/** The cache's description of the place ("" when not known). */
	public String getPlace()
	{
		return place;
	}

	/** A hand-written note on the step (e.g. "Kill the barbarian spirit here"), or "". */
	public String getNote()
	{
		return note;
	}

	/** The clue scroll (or device) picture, or -1. */
	public int getClueItemId()
	{
		return clueItemId;
	}

	public List<ItemRow> getItems()
	{
		return items;
	}

	public List<OtherRow> getOthers()
	{
		return others;
	}

	public State getState()
	{
		return state;
	}

	/** "Everything is ready.", "Take from the bank: ...", "Missing: ...". */
	public String getStatus()
	{
		return status;
	}

	/**
	 * An item row's mark: worn, carried or waiting in the built, filled STASH unit: a check (the text still says "In the
	 * STASH unit"); in the bank: the bank symbol; missing: an X. No player: none.
	 */
	public NeedStatus mark(ItemRow r)
	{
		if (!playerKnown)
		{
			return NeedStatus.UNKNOWN;
		}
		switch (r.getWhere())
		{
			case WORN:
			case CARRIED:
			case STASH:
				return NeedStatus.HAVE;
			case BANK:
				return NeedStatus.BANK;
			case MAYBE_STASH:
				return NeedStatus.MAYBE;
			case MISSING:
			default:
				return NeedStatus.MISSING;
		}
	}

	/** A level or quest row's mark: a check when met, else an X. No player: none. */
	public NeedStatus mark(OtherRow o)
	{
		return !playerKnown ? NeedStatus.UNKNOWN : o.isMet() ? NeedStatus.HAVE : NeedStatus.MISSING;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof ClueCard))
		{
			return false;
		}
		ClueCard c = (ClueCard) o;
		return c.requestKey.equals(requestKey) && c.title.equals(title) && c.text.equals(text) && c.place.equals(place)
			&& c.note.equals(note) && c.clueItemId == clueItemId && c.items.equals(items) && c.others.equals(others)
			&& c.stash.equals(stash) && c.state == state && c.status.equals(status) && c.playerKnown == playerKnown;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(requestKey, items, stash, state);
	}

	@Override
	public String toString()
	{
		return title + ": " + items + " " + state;
	}
}

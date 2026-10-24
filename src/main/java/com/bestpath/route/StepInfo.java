package com.bestpath.route;

import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.req.BankItem;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.BoatType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.runelite.api.gameval.ItemID;

/**
 * What the sidebar needs to pick a step's icons: which sort of transport it
 * is (the exported data's category, teleport category, network and the
 * Settings tab type) and what its requirement asks for (skill levels, items,
 * runes, quests, coins), each with whether the player has it ({@link NeedStatus}:
 * a green check, the bank symbol or a red X beside it); a bank step: what to
 * take out ({@link TakeOut}); the player answers not given yet that the step
 * rests on, asked under its card ({@link #getQuestions()}). Worked out on the
 * route worker; immutable. Text and ids only: the pictures themselves are
 * chosen and loaded by the sidebar.
 */
public final class StepInfo
{
	/** A walk, a transport without exported data, or a sailing step whose boat is not known. */
	public static final StepInfo NONE = new StepInfo("", null, "", false, "", "", -1, List.of());

	/** The coins item (the coins requirement's icon). */
	public static final int COINS_ITEM = ItemID.COINS;
	/** Coins are pictured as a stack of this many (the game's picture of a 10,000 stack). */
	public static final int COINS_PICTURE_QUANTITY = 10000;

	/** One thing a step needs, as a small icon and a short text on its card. */
	public static final class Need
	{
		public enum Type
		{
			SKILL,
			QUEST,
			ITEM,
			RUNE,
			COINS,
			/** A home or minigame teleport's cooldown: "Ready" (a route only uses one that is ready). */
			COOLDOWN,
			/**
			 * A player answer: a check when answered as needed, an X when answered the other way, no mark while not
			 * answered yet (the question is under the card).
			 */
			ANSWER,
			/** The step starts or ends in the Wilderness: the game's skull and "WILDERNESS!", no mark; a red card outline. */
			WILDERNESS
		}

		private final Type type;
		private final String text;
		private final String tooltip;
		private final int itemId;
		private final Skill skill;
		private final Cooldown cooldown;
		private final NeedStatus status;
		/** A staff shown for runes: the runes it gives (empty for every other need). */
		private final List<String> staffRunes;

		private Need(Type type, String text, String tooltip, int itemId, Skill skill)
		{
			this(type, text, tooltip, itemId, skill, null, NeedStatus.UNKNOWN);
		}

		private Need(Type type, String text, String tooltip, int itemId, Skill skill, Cooldown cooldown, NeedStatus status)
		{
			this(type, text, tooltip, itemId, skill, cooldown, status, List.of());
		}

		private Need(Type type, String text, String tooltip, int itemId, Skill skill, Cooldown cooldown, NeedStatus status,
			List<String> staffRunes)
		{
			this.staffRunes = List.copyOf(staffRunes);
			this.type = type;
			this.text = text;
			this.tooltip = tooltip;
			this.itemId = itemId;
			this.skill = skill;
			this.cooldown = cooldown;
			this.status = status == null ? NeedStatus.UNKNOWN : status;
		}

		/**
		 * A home or minigame teleport's cooldown: "Ready". Routes use these only while ready and leave them out while they
		 * cool down, so no countdown is shown.
		 */
		public static Need cooldown(Cooldown c)
		{
			return new Need(Type.COOLDOWN, "Ready", c.getLabel() + ": ready (it can't be used again for "
				+ c.getMinutes() + " minutes after)", -1, null, c, NeedStatus.UNKNOWN);
		}

		/**
		 * A player answer the step needs: its words ("Spirit tree planted at Port Sarim"; {@code not}: the requirement
		 * wants it to be no, "not answer X").
		 */
		public static Need answer(String answer, boolean not)
		{
			String words = AnswerQuestions.needWords(answer, not);
			return new Need(Type.ANSWER, words, words + " (your answer; Settings, \"Your answers\")", -1, null);
		}

		/** The Wilderness: a skull like the requirements, with the text "WILDERNESS!". */
		public static Need wilderness()
		{
			return new Need(Type.WILDERNESS, "WILDERNESS!", "The Wilderness: other players can attack you here", -1, null);
		}

		/** "Agility 51". */
		public static Need skill(Skill skill, int level)
		{
			return new Need(Type.SKILL, skill.getLabel() + " " + level, "Needs " + skill.getLabel() + " " + level, -1, skill);
		}

		/** A quest done (or started, ... as {@code wanted} says). */
		public static Need quest(String quest, Requirement.Quest.Wanted wanted)
		{
			String suffix;
			switch (wanted)
			{
				case STARTED:
					suffix = " (started)";
					break;
				case NOT_STARTED:
					suffix = " (not started)";
					break;
				case NOT_DONE:
					suffix = " (not done)";
					break;
				case DONE:
				default:
					suffix = "";
					break;
			}
			return new Need(Type.QUEST, quest + suffix, "Quest: " + quest + suffix, -1, null);
		}

		/**
		 * An item; {@code label} = the item group's name or the ids joined with "/" (then there is no name to show).
		 */
		public static Need item(String label, int itemId, int count, boolean worn, boolean usedUp)
		{
			boolean named = label != null && !label.isEmpty() && !label.matches("[0-9/ ]+");
			String text = count > 1 ? "x" + count : "";
			StringBuilder tip = new StringBuilder(named ? "Item: " + label : "An item");
			if (count > 1)
			{
				tip.append(" x").append(count);
			}
			if (worn)
			{
				tip.append(", worn");
			}
			if (usedUp)
			{
				tip.append(", used up");
			}
			return new Need(Type.ITEM, text, tip.toString(), itemId, null);
		}

		/**
		 * An axe stored at the canoe stations ("canoe axe stored"): an axe's picture with "Stored". Detected by the plugin,
		 * never asked.
		 */
		public static Need storedCanoeAxe(int axeItemId)
		{
			return new Need(Type.ITEM, "Stored", "An axe stored at the canoe stations (Best Path sees you store or take"
				+ " it back at a canoe station's NPC)", axeItemId, null);
		}

		/** {@code count} runes of a kind ("air"), shown with the rune's own item. */
		public static Need rune(String rune, int runeItemId, int count)
		{
			return new Need(Type.RUNE, "x" + count, count + " " + rune + (count == 1 ? " rune" : " runes")
				+ " (or a staff that gives them)", runeItemId, null);
		}

		/**
		 * The staff that gives a spell's runes, shown instead of the runes: its picture, no count. {@code worn}: wielded
		 * now (else carried or in the bank: wield it).
		 */
		public static Need staff(int staffItemId, String rune, boolean worn)
		{
			return staff(staffItemId, List.of(rune), worn);
		}

		private static Need staff(int staffItemId, List<String> runes, boolean worn)
		{
			return new Need(Type.ITEM, "", "Staff: gives the " + joinAnd(runes) + " runes" + (worn ? "" : " (wield it)"),
				staffItemId, null, null, NeedStatus.UNKNOWN, runes);
		}

		/** "air", "air and fire", "air, fire and water". */
		private static String joinAnd(List<String> words)
		{
			int n = words.size();
			return n <= 1 ? String.join("", words) : String.join(", ", words.subList(0, n - 1)) + " and " + words.get(n - 1);
		}

		/** For a staff: the same staff giving these runes too (one picture for all). */
		Need withStaffRunes(List<String> more)
		{
			List<String> all = new ArrayList<>(staffRunes);
			for (String r : more)
			{
				if (!all.contains(r))
				{
					all.add(r);
				}
			}
			return staff(itemId, all, !tooltip.contains("(wield it)")).withStatus(status);
		}

		/** A staff shown for runes: the runes it gives; empty for any other need. */
		public List<String> getStaffRunes()
		{
			return staffRunes;
		}

		public static Need coins(int amount)
		{
			return new Need(Type.COINS, String.valueOf(amount), amount + " coins", COINS_ITEM, null);
		}

		/** The same need with another mouse-over text. */
		Need withTooltip(String newTooltip)
		{
			return new Need(type, text, newTooltip, itemId, skill, cooldown, status, staffRunes);
		}

		/** The same need with whether the player has it (its mark on the card). */
		public Need withStatus(NeedStatus newStatus)
		{
			return new Need(type, text, tooltip, itemId, skill, cooldown, newStatus, staffRunes);
		}

		/**
		 * Whether the player the route was searched for has it (a green check, the bank symbol or a red X beside it);
		 * {@link NeedStatus#UNKNOWN} without a player.
		 */
		public NeedStatus getStatus()
		{
			return status;
		}

		public Type getType()
		{
			return type;
		}

		/** The short text next to the icon (may be empty: the icon says it all). */
		public String getText()
		{
			return text;
		}

		/** The longer text for the mouse-over. */
		public String getTooltip()
		{
			return tooltip;
		}

		/** The item shown (items, runes, coins), or -1. */
		public int getItemId()
		{
			return itemId;
		}

		/** The skill (skill levels), or null. */
		public Skill getSkill()
		{
			return skill;
		}

		/** The cooldown ({@link Type#COOLDOWN}), or null. */
		public Cooldown getCooldown()
		{
			return cooldown;
		}

		/** The same thing needed (type, words, item) and the same mark. */
		@Override
		public boolean equals(Object o)
		{
			return o instanceof Need && ((Need) o).type == type && ((Need) o).tooltip.equals(tooltip)
				&& ((Need) o).itemId == itemId && ((Need) o).status == status;
		}

		@Override
		public int hashCode()
		{
			return (tooltip.hashCode() * 31 + itemId) * 5 + status.ordinal();
		}

		@Override
		public String toString()
		{
			return type + " " + tooltip + (status == NeedStatus.UNKNOWN ? "" : " [" + status + "]");
		}
	}

	/**
	 * One thing to take out of the bank on a bank step, shown in a box on its card: the item whose picture is shown (one
	 * the player has in the bank), any of {@link #getIds()} will do, how many to take out, and its mark (the bank
	 * symbol). Immutable.
	 */
	public static final class TakeOut
	{
		private final String label;
		private final int itemId;
		private final int[] ids;
		private final int count;
		private final NeedStatus status;

		private TakeOut(String label, int itemId, int[] ids, int count, NeedStatus status)
		{
			this.label = label;
			this.itemId = itemId;
			this.ids = ids.clone();
			this.count = count;
			this.status = status;
		}

		/**
		 * One of the route's take-out items for this player: the picture is an item of the group the player has in the
		 * bank (else one carried, else the first); the mark is the bank's unless the bank holds none of them.
		 */
		public static TakeOut of(BankItem b, PlayerState player)
		{
			int[] ids = b.getItemIds();
			int shown = ids[0];
			boolean banked = player == null || player.isAllItems();
			if (player != null && !player.isAllItems())
			{
				int carriedId = -1;
				for (int id : ids)
				{
					if (player.banked(id) > 0)
					{
						shown = id;
						banked = true;
						break;
					}
					if (carriedId < 0 && player.carried(id) > 0)
					{
						carriedId = id;
					}
				}
				shown = banked || carriedId < 0 ? shown : carriedId;
			}
			return new TakeOut(b.getLabel(), shown, ids, b.getCount(), banked ? NeedStatus.BANK : NeedStatus.MISSING);
		}

		/** The data's words for it ("law runes x2", "coins 30", "8007", "Iron med helm"). */
		public String getLabel()
		{
			return label;
		}

		/** The item whose picture (and name) is shown. */
		public int getItemId()
		{
			return itemId;
		}

		public int[] getIds()
		{
			return ids.clone();
		}

		/** How many to take out (what is needed minus what the player carries). */
		public int getCount()
		{
			return count;
		}

		/** The bank symbol ({@link NeedStatus#BANK}); missing only if the bank no longer holds any of them. */
		public NeedStatus getStatus()
		{
			return status;
		}

		/**
		 * A name from the data's words for when the item's own name is not known: "Law runes", "Coins", "Iron med helm",
		 * "Item 8007" (the box shows the item's name from the game when it has it).
		 */
		public String fallbackName()
		{
			String s = label.replaceAll(" x[0-9]+$", "").trim();
			if (s.matches("coins [0-9]+"))
			{
				return "Coins";
			}
			if (s.isEmpty() || s.matches("[0-9/ ]+"))
			{
				return "Item " + itemId;
			}
			return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof TakeOut))
			{
				return false;
			}
			TakeOut t = (TakeOut) o;
			return t.label.equals(label) && t.itemId == itemId && Arrays.equals(t.ids, ids) && t.count == count
				&& t.status == status;
		}

		@Override
		public int hashCode()
		{
			return (label.hashCode() * 31 + itemId) * 31 + count;
		}

		@Override
		public String toString()
		{
			return label + " (item " + itemId + " x" + count + ", " + status + ")";
		}
	}

	private final String name;
	private final TransportSettings.Category settingsCategory;
	private final String category;
	private final boolean reverse;
	private final String teleportCategory;
	private final String network;
	private final int itemId;
	private final NeedStatus itemStatus;
	private final List<Need> needs;
	private final BoatType boat;
	private final List<TakeOut> takeOut;
	/** The player answers not given yet this step rests on (the data's words). */
	private final List<String> questions;
	/** Developer details: the requirement part by part with the player's result ({@link RequirementCheck}). */
	private final List<String> checkLines;
	/** What the info was made from, to make it again for the player's items now ({@link #forPlayer}); null = fixed. */
	private final Source source;

	/** A transport line's info as made: its line, direction, requirement and tables. Immutable. */
	private static final class Source
	{
		final TransportLine line;
		final boolean reverse;
		final Requirement requirement;
		final TransportSettings.Category settingsCategory;
		final Tables tables;
		/** The cooldown's "Ready" need was taken off ({@link #withoutCooldown()}). */
		final boolean noCooldown;

		Source(TransportLine line, boolean reverse, Requirement requirement, TransportSettings.Category settingsCategory,
			Tables tables, boolean noCooldown)
		{
			this.line = line;
			this.reverse = reverse;
			this.requirement = requirement;
			this.settingsCategory = settingsCategory;
			this.tables = tables;
			this.noCooldown = noCooldown;
		}
	}

	/**
	 * A sailing step (board, sail, leave, bring the boat, release it from its bottle) done with the player's boat of
	 * this kind: its card shows the boat's picture. Null kind = {@link #NONE}.
	 */
	public static StepInfo sailing(BoatType boat)
	{
		return boat == null ? NONE : new StepInfo("", null, "", false, "", "", -1, NeedStatus.UNKNOWN, List.of(), boat, List.of(), List.of(), List.of());
	}

	/**
	 * A part of a way by boat on a "no route" card ({@link NoRouteWhy}): the boat's picture (null kind: none, the
	 * general travel picture) and what it needs (a dock's Sailing level and quest, a boat bottle), each with its mark
	 * for this player.
	 */
	public static StepInfo sailingNeeds(BoatType boat, Requirement requirement, PlayerState player)
	{
		return new StepInfo("", null, boat == null ? "TRAVEL" : "", false, "", "", -1, NeedStatus.UNKNOWN,
			StepNeeds.needs(requirement, -1, player, null), boat, List.of(), List.of(), List.of());
	}

	/**
	 * A bank step (a route may begin at a bank): the bank's name ({@link #getName()}) and what to take out, each with
	 * its picture and count ({@link #getTakeOut()}), for the card's box.
	 */
	public static StepInfo bank(String bankName, List<TakeOut> takeOut)
	{
		return new StepInfo(bankName, null, "", false, "", "", -1, NeedStatus.UNKNOWN, List.of(), null, takeOut, List.of(), List.of());
	}

	/**
	 * @param name             the transport's name in the data ("Varrock Teleport"; "" = none)
	 * @param settingsCategory the Settings tab type ({@link TransportRules#category}); null = none
	 * @param category         the exported data's category (CLIMB_UP, SHORTCUT, TRAVEL, PASS, ...; "" = none)
	 * @param reverse          a two-way transport used from its end to its start (a CLIMB_UP ladder then goes down)
	 * @param teleportCategory for a teleport: "spell", "tablet", "jewellery (infinite)", ... ("" = none)
	 * @param network          the network's name ("" = none)
	 * @param itemId           the item the step is done with (an item teleport's item), or -1
	 * @param needs            what the requirement asks for, in its order
	 */
	public StepInfo(String name, TransportSettings.Category settingsCategory, String category, boolean reverse,
		String teleportCategory, String network, int itemId, List<Need> needs)
	{
		this(name, settingsCategory, category, reverse, teleportCategory, network, itemId, NeedStatus.UNKNOWN, needs, null,
			List.of(), List.of(), List.of());
	}

	private StepInfo(String name, TransportSettings.Category settingsCategory, String category, boolean reverse,
		String teleportCategory, String network, int itemId, NeedStatus itemStatus, List<Need> needs, BoatType boat,
		List<TakeOut> takeOut, List<String> questions, List<String> checkLines)
	{
		this(name, settingsCategory, category, reverse, teleportCategory, network, itemId, itemStatus, needs, boat, takeOut,
			questions, checkLines, null);
	}

	private StepInfo(String name, TransportSettings.Category settingsCategory, String category, boolean reverse,
		String teleportCategory, String network, int itemId, NeedStatus itemStatus, List<Need> needs, BoatType boat,
		List<TakeOut> takeOut, List<String> questions, List<String> checkLines, Source source)
	{
		this.source = source;
		this.checkLines = List.copyOf(checkLines);
		this.questions = List.copyOf(questions);
		this.boat = boat;
		this.itemStatus = itemId < 0 || itemStatus == null ? NeedStatus.UNKNOWN : itemStatus;
		this.takeOut = Collections.unmodifiableList(List.copyOf(takeOut));
		this.name = name == null ? "" : name;
		this.settingsCategory = settingsCategory;
		this.category = category == null ? "" : category;
		this.reverse = reverse;
		this.teleportCategory = teleportCategory == null ? "" : teleportCategory.trim().toLowerCase(Locale.ROOT);
		this.network = network == null ? "" : network;
		this.itemId = itemId;
		this.needs = Collections.unmodifiableList(List.copyOf(needs));
	}

	/**
	 * The info of an exported transport line used by a route, without a player or tables: "one of (...)" shows its
	 * first choice and an item group its first item.
	 */
	public static StepInfo of(TransportLine line, boolean reverse, Requirement requirement,
		TransportSettings.Category settingsCategory)
	{
		return of(line, reverse, requirement, settingsCategory, null, null);
	}

	/**
	 * The info of an exported transport line used by a route.
	 *
	 * @param reverse     used from its end to its start (a two-way transport)
	 * @param requirement the requirement for the direction used (with its network's), parsed; null = unreadable
	 * @param player      the player the route was searched for (picks the "one of" choice and the item shown);
	 *                    null = none (the first choice, the group's default item)
	 * @param tables      the exported tables (item groups' default items); null = none
	 */
	public static StepInfo of(TransportLine line, boolean reverse, Requirement requirement,
		TransportSettings.Category settingsCategory, PlayerState player, Tables tables)
	{
		boolean itemTeleport = settingsCategory == TransportSettings.Category.TELEPORT_ITEMS
			|| line.getKind() == TransportLine.Kind.TELEPORT && isItemTeleportCategory(line.getTeleportCategory());
		Requirement.Item main = itemTeleport && requirement != null ? firstItemLeaf(requirement, player) : null;
		int item = main == null ? -1 : shownItem(main, player, tables);
		List<Need> needs = StepNeeds.needs(requirement, item, player, tables);
		Cooldown cooldown = Cooldown.of(line.isCooldown(), line.getTeleportCategory());
		if (cooldown != null)
		{
			// a home / minigame teleport: "Ready" (a route only uses one that is ready; the mark says so too)
			needs.add(Need.cooldown(cooldown).withStatus(player == null ? NeedStatus.UNKNOWN
				: player.cooldownReady(cooldown) ? NeedStatus.HAVE : NeedStatus.MISSING));
		}
		// the step's own item (its big picture) gets a mark like the needs
		NeedStatus itemStatus = main == null ? NeedStatus.UNKNOWN : StepNeeds.status(main, player);
		// the answers not given yet that the route rests on here: asked under the card
		List<String> questions = player == null || requirement == null ? List.of() : requirement.check(player).getUnanswered();
		return new StepInfo(line.getName(), settingsCategory, line.getCategory(), reverse, line.getTeleportCategory(),
			line.getNetwork(), item, itemStatus, needs, null, List.of(), questions, RequirementCheck.lines(requirement, player),
			new Source(line, reverse, requirement, settingsCategory, tables, false));
	}

	/**
	 * The same step's info for the items the player has now: the item pictures and marks, the staff for the runes, the
	 * developer lines; the route itself is not searched again. Itself when it was not made from a transport line (walks,
	 * sailing, bank steps).
	 */
	public StepInfo forPlayer(PlayerState player)
	{
		if (source == null || player == null)
		{
			return this;
		}
		StepInfo fresh = of(source.line, source.reverse, source.requirement, source.settingsCategory, player, source.tables);
		fresh = fresh.withoutCooldown();
		if (!source.noCooldown)
		{
			// the "Ready" mark as the route found it: casting the route's own home teleport is no reason for a red X
			for (Need n : needs)
			{
				if (n.getType() == Need.Type.COOLDOWN)
				{
					List<Need> all = new ArrayList<>(fresh.needs);
					all.add(n);
					fresh = new StepInfo(fresh.name, fresh.settingsCategory, fresh.category, fresh.reverse, fresh.teleportCategory,
						fresh.network, fresh.itemId, fresh.itemStatus, all, fresh.boat, fresh.takeOut, fresh.questions,
						fresh.checkLines, source);
				}
			}
		}
		return isWilderness() ? fresh.withWilderness() : fresh;
	}

	/** True if a card shows the two alike: the same big item and mark, needs, questions and developer lines. */
	public boolean sameCard(StepInfo o)
	{
		return o == this || o != null && o.itemId == itemId && o.itemStatus == itemStatus && o.needs.equals(needs)
			&& o.questions.equals(questions) && o.checkLines.equals(checkLines);
	}

	/**
	 * The player answers not given yet (the data's words, "Spirit tree planted at Port Sarim") the route rests on at
	 * this step: its card asks each with Yes / No. Empty without a player.
	 */
	public List<String> getQuestions()
	{
		return questions;
	}

	/**
	 * Developer details: the step's requirement part by part, each with whether the player the route was searched for
	 * meets it ("[ok] Magic 56", "[no] item 567 used up"); empty without a requirement.
	 */
	public List<String> getCheckLines()
	{
		return checkLines;
	}

	/** Teleport categories done with an item (tablets, scrolls, jewellery, capes, other items). */
	static boolean isItemTeleportCategory(String teleportCategory)
	{
		String c = teleportCategory == null ? "" : teleportCategory.trim().toLowerCase(Locale.ROOT);
		return c.equals("tablet") || c.equals("scroll") || c.startsWith("jewellery") || c.startsWith("cape or diary item")
			|| c.startsWith("other item");
	}

	/**
	 * The item whose picture stands for an item requirement: one of them the player wears, else carries, else one in
	 * the bank, else the group's default item (from the exported tables), else the first.
	 */
	public static int shownItem(Requirement.Item item, PlayerState player, Tables tables)
	{
		int[] ids = item.getItemIds();
		if (player != null && !player.isAllItems())
		{
			for (int id : ids)
			{
				if (player.wears(id))
				{
					return id;
				}
			}
			for (int id : ids)
			{
				if (player.carried(id) > 0)
				{
					return id;
				}
			}
			for (int id : ids)
			{
				if (player.banked(id) > 0)
				{
					return id;
				}
			}
		}
		int chosen = tables == null ? -1 : tables.itemGroupDefault(item.getLabel());
		return chosen >= 0 ? chosen : ids[0];
	}

	/** The first item requirement (inside "one of": the shown choice's); or null. */
	static Requirement.Item firstItemLeaf(Requirement r, PlayerState player)
	{
		if (r instanceof Requirement.Item)
		{
			return (Requirement.Item) r;
		}
		if (r instanceof Requirement.All)
		{
			for (Requirement p : ((Requirement.All) r).getParts())
			{
				Requirement.Item leaf = firstItemLeaf(p, player);
				if (leaf != null)
				{
					return leaf;
				}
			}
		}
		if (r instanceof Requirement.AnyOf)
		{
			Requirement.AnyOf any = (Requirement.AnyOf) r;
			int i = StepNeeds.choice(any, player);
			return i < 0 ? null : firstItemLeaf(any.getAlternatives().get(i), player);
		}
		return null;
	}

	/** The transport's name in the data ("" = none). */
	public String getName()
	{
		return name;
	}

	/** The Settings tab type, or null. */
	public TransportSettings.Category getSettingsCategory()
	{
		return settingsCategory;
	}

	/** The exported data's category ("" = none). */
	public String getCategory()
	{
		return category;
	}

	/** True if a two-way transport is used from its end to its start (its category's up / down then swap). */
	public boolean isReverse()
	{
		return reverse;
	}

	/** Lower case ("" = none). */
	public String getTeleportCategory()
	{
		return teleportCategory;
	}

	public String getNetwork()
	{
		return network;
	}

	/** The item the step is done with (an item teleport's), or -1. */
	public int getItemId()
	{
		return itemId;
	}

	/**
	 * Whether the player has the step's own item ({@link #getItemId()}, the card's big picture): its mark;
	 * {@link NeedStatus#UNKNOWN} without such an item or a player.
	 */
	public NeedStatus getItemStatus()
	{
		return itemStatus;
	}

	/** A bank step: what to take out, in the route's order (empty for other steps). */
	public List<TakeOut> getTakeOut()
	{
		return takeOut;
	}

	public List<Need> getNeeds()
	{
		return needs;
	}

	/** True when the step starts or ends in the Wilderness ({@link Need.Type#WILDERNESS}). */
	public boolean isWilderness()
	{
		for (Need n : needs)
		{
			if (n.getType() == Need.Type.WILDERNESS)
			{
				return true;
			}
		}
		return false;
	}

	/** The same info with the Wilderness first among its needs (no Wilderness level shown). */
	public StepInfo withWilderness()
	{
		if (isWilderness())
		{
			return this;
		}
		List<Need> all = new ArrayList<>();
		all.add(Need.wilderness());
		all.addAll(needs);
		return new StepInfo(name, settingsCategory, category, reverse, teleportCategory, network, itemId, itemStatus, all, boat,
			takeOut, questions, checkLines, source);
	}

	/** The home / minigame cooldown this step's teleport has, or null. */
	public Cooldown getCooldown()
	{
		for (Need n : needs)
		{
			if (n.getCooldown() != null)
			{
				return n.getCooldown();
			}
		}
		return null;
	}

	/**
	 * The same info without the cooldown's "Ready" need: a "no route" card of a teleport that is cooling down says when
	 * it is ready instead ({@link NoRouteWhy}).
	 */
	public StepInfo withoutCooldown()
	{
		List<Need> kept = new ArrayList<>();
		for (Need n : needs)
		{
			if (n.getType() != Need.Type.COOLDOWN)
			{
				kept.add(n);
			}
		}
		Source s = source == null ? null
			: new Source(source.line, source.reverse, source.requirement, source.settingsCategory, source.tables, true);
		return kept.size() == needs.size() ? this : new StepInfo(name, settingsCategory, category, reverse, teleportCategory,
			network, itemId, itemStatus, kept, boat, takeOut, questions, checkLines, s);
	}

	/** The kind of the player's boat a sailing step uses, or null (not a sailing step). */
	public BoatType getBoat()
	{
		return boat;
	}
}

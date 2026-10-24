package com.bestpath.core.req;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * What a player needs to use a transport: a small tree of "all of" and
 * "one of" over leaves (a skill level, a quest, a varbit / varp, an item,
 * coins, members, an empty equipment slot, a player answer, the player-owned
 * house, the spellbook, runes for a spell, an item in the collection log, a
 * grown spirit tree). Data, not code:
 * written and read as one readable line by {@link RequirementText}, checked
 * against a {@link PlayerState} by {@link #check}. Immutable.
 */
public abstract class Requirement
{
	/** No requirement: always met. Written as an empty line. */
	public static final Requirement NONE = new All(List.of());

	Requirement()
	{
	}

	/** Checks this requirement against a player. A pure function. */
	public abstract Check check(PlayerState p);

	/** Calls {@code v} for this requirement and every requirement inside it. */
	public void visit(Consumer<Requirement> v)
	{
		v.accept(this);
	}

	/** The readable line (see {@link RequirementText}). */
	public final String toText()
	{
		StringBuilder sb = new StringBuilder();
		write(sb);
		return sb.toString();
	}

	public boolean isNone()
	{
		return this instanceof All && ((All) this).parts.isEmpty();
	}

	abstract void write(StringBuilder sb);

	@Override
	public String toString()
	{
		return toText();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Requirement && ((Requirement) o).toText().equals(toText());
	}

	@Override
	public int hashCode()
	{
		return toText().hashCode();
	}

	/** Every part must be met. */
	public static Requirement all(List<Requirement> parts)
	{
		List<Requirement> flat = new ArrayList<>();
		for (Requirement r : parts)
		{
			if (r instanceof All)
			{
				flat.addAll(((All) r).parts);
			}
			else
			{
				flat.add(r);
			}
		}
		return flat.size() == 1 ? flat.get(0) : new All(flat);
	}

	/** At least one alternative must be met. */
	public static Requirement anyOf(List<Requirement> alternatives)
	{
		if (alternatives.isEmpty())
		{
			throw new IllegalArgumentException("one of: no alternatives");
		}
		return alternatives.size() == 1 ? alternatives.get(0) : new AnyOf(alternatives);
	}

	public static final class All extends Requirement
	{
		private final List<Requirement> parts;

		private All(List<Requirement> parts)
		{
			this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
		}

		public List<Requirement> getParts()
		{
			return parts;
		}

		@Override
		public Check check(PlayerState p)
		{
			Check.Outcome worst = Check.Outcome.MET;
			List<Check> checks = new ArrayList<>();
			for (Requirement r : parts)
			{
				Check c = r.check(p);
				checks.add(c);
				if (c.getOutcome().compareTo(worst) > 0)
				{
					worst = c.getOutcome();
				}
			}
			List<String> missing = new ArrayList<>();
			List<String> todo = new ArrayList<>();
			List<BankItem> bank = new ArrayList<>();
			List<String> unanswered = new ArrayList<>();
			boolean surelyNotMet = false;
			for (Check c : checks)
			{
				if (c.getOutcome() == worst)
				{
					missing.addAll(c.getMissing());
					bank.addAll(c.getBankItems());
				}
				todo.addAll(c.getTodo());
				if (worst != Check.Outcome.NOT_MET)
				{
					unanswered.addAll(c.getUnanswered()); // met only if every part's answers are as hoped
				}
				else if (c.getOutcome() == Check.Outcome.NOT_MET)
				{
					surelyNotMet |= c.getUnanswered().isEmpty();
					unanswered.addAll(c.getUnanswered());
				}
			}
			// not met whatever is answered when one part is not met without resting on an answer
			return Check.of(worst, missing, todo, bank, surelyNotMet ? List.of() : unanswered);
		}

		@Override
		public void visit(Consumer<Requirement> v)
		{
			v.accept(this);
			for (Requirement r : parts)
			{
				r.visit(v);
			}
		}

		@Override
		void write(StringBuilder sb)
		{
			for (int i = 0; i < parts.size(); i++)
			{
				if (i > 0)
				{
					sb.append("; ");
				}
				parts.get(i).write(sb);
			}
		}
	}

	public static final class AnyOf extends Requirement
	{
		private final List<Requirement> alternatives;

		private AnyOf(List<Requirement> alternatives)
		{
			this.alternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
		}

		public List<Requirement> getAlternatives()
		{
			return alternatives;
		}

		@Override
		public Check check(PlayerState p)
		{
			Check[] checks = checks(p);
			Check best = checks[best(checks)];
			if (best.getOutcome() != Check.Outcome.NOT_MET)
			{
				return best;
			}
			List<String> why = new ArrayList<>();
			List<String> unanswered = new ArrayList<>();
			for (Check c : checks)
			{
				why.add(String.join(", ", c.getMissing()));
				unanswered.addAll(c.getUnanswered());
			}
			return Check.notMet("one of: " + String.join(" or ", why), unanswered);
		}

		/**
		 * Which alternative the player uses, by the same rule as {@link #check}: the first with the best outcome
		 * (met now, else met with the bank); -1 if none is met.
		 */
		public int chosen(PlayerState p)
		{
			Check[] checks = checks(p);
			int i = best(checks);
			return checks[i].getOutcome() == Check.Outcome.NOT_MET ? -1 : i;
		}

		private Check[] checks(PlayerState p)
		{
			Check[] c = new Check[alternatives.size()];
			for (int i = 0; i < c.length; i++)
			{
				c[i] = alternatives.get(i).check(p);
			}
			return c;
		}

		/**
		 * The first check with the best outcome; of equal outcomes, one resting on no unanswered question first (a
		 * carried rope rather than "have you tied a rope?").
		 */
		private static int best(Check[] checks)
		{
			int best = 0;
			for (int i = 1; i < checks.length; i++)
			{
				if (rank(checks[i]) < rank(checks[best]))
				{
					best = i;
				}
			}
			return best;
		}

		private static int rank(Check c)
		{
			return c.getOutcome().ordinal() * 2 + (c.getUnanswered().isEmpty() ? 0 : 1);
		}

		@Override
		public void visit(Consumer<Requirement> v)
		{
			v.accept(this);
			for (Requirement r : alternatives)
			{
				r.visit(v);
			}
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("one of (");
			for (int i = 0; i < alternatives.size(); i++)
			{
				if (i > 0)
				{
					sb.append(" | ");
				}
				alternatives.get(i).write(sb);
			}
			sb.append(')');
		}
	}

	/** A real (not boosted) skill level of at least {@code level}. */
	public static final class SkillLevel extends Requirement
	{
		private final Skill skill;
		private final int level;

		public SkillLevel(Skill skill, int level)
		{
			this.skill = skill;
			this.level = level;
		}

		public Skill getSkill()
		{
			return skill;
		}

		public int getLevel()
		{
			return level;
		}

		@Override
		public Check check(PlayerState p)
		{
			if (skill.isMembers() && !p.isMembers())
			{
				return Check.notMet("members (" + skill.getLabel() + " is a members skill)");
			}
			int has = p.level(skill);
			return has >= level ? Check.MET : Check.notMet(skill.getLabel() + " " + level + " (has " + has + ")");
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append(skill.getLabel()).append(' ').append(level);
		}
	}

	/** A quest in a given state. */
	public static final class Quest extends Requirement
	{
		/** What the requirement asks for. */
		public enum Wanted
		{
			DONE(""),
			STARTED(" started"),
			NOT_STARTED(" not started"),
			NOT_DONE(" not done");

			private final String suffix;

			Wanted(String suffix)
			{
				this.suffix = suffix;
			}

			boolean accepts(QuestState s)
			{
				switch (this)
				{
					case DONE:
						return s == QuestState.DONE;
					case STARTED:
						return s != QuestState.NOT_STARTED;
					case NOT_STARTED:
						return s == QuestState.NOT_STARTED;
					default:
						return s != QuestState.DONE;
				}
			}
		}

		private final String quest;
		private final Wanted wanted;

		public Quest(String quest, Wanted wanted)
		{
			this.quest = quest.trim();
			this.wanted = wanted;
		}

		public String getQuest()
		{
			return quest;
		}

		public Wanted getWanted()
		{
			return wanted;
		}

		@Override
		public Check check(PlayerState p)
		{
			return wanted.accepts(p.quest(quest)) ? Check.MET : Check.notMet(toText());
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("quest ").append(quest).append(wanted.suffix);
		}
	}

	/** A varbit or varp compared with a number. */
	public static final class Var extends Requirement
	{
		public enum Op
		{
			EQ("="),
			NE("!="),
			GT(">"),
			GE(">="),
			LT("<"),
			LE("<="),
			/** Any of these bits set. */
			BITS("&");

			private final String symbol;

			Op(String symbol)
			{
				this.symbol = symbol;
			}

			public static Op bySymbol(String s)
			{
				for (Op o : values())
				{
					if (o.symbol.equals(s))
					{
						return o;
					}
				}
				return null;
			}

			boolean test(int v, int n)
			{
				switch (this)
				{
					case EQ:
						return v == n;
					case NE:
						return v != n;
					case GT:
						return v > n;
					case GE:
						return v >= n;
					case LT:
						return v < n;
					case LE:
						return v <= n;
					default:
						return (v & n) != 0;
				}
			}
		}

		private final boolean varp;
		private final int id;
		private final Op op;
		private final int value;

		public Var(boolean varp, int id, Op op, int value)
		{
			this.varp = varp;
			this.id = id;
			this.op = op;
			this.value = value;
		}

		public boolean isVarp()
		{
			return varp;
		}

		public int getId()
		{
			return id;
		}

		public Op getOp()
		{
			return op;
		}

		public int getValue()
		{
			return value;
		}

		@Override
		public Check check(PlayerState p)
		{
			if (p.isAllUnlocked())
			{
				return Check.MET;
			}
			int v = varp ? p.varp(id) : p.varbit(id);
			return op.test(v, value) ? Check.MET : Check.notMet(toText() + " (is " + v + ")");
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append(varp ? "varp " : "varbit ").append(id).append(' ').append(op.symbol).append(' ').append(value);
		}
	}

	/** Carrying (or wearing) {@code count} of one of these items. */
	public static final class Item extends Requirement
	{
		private final String label;
		private final int[] itemIds;
		private final int count;
		private final boolean worn;
		private final boolean usedUp;

		/** {@code label}: the item group's name, or the ids joined with "/". */
		public Item(String label, int[] itemIds, int count, boolean worn, boolean usedUp)
		{
			if (itemIds.length == 0 || count < 1)
			{
				throw new IllegalArgumentException("item: no ids or count < 1");
			}
			this.label = label;
			this.itemIds = itemIds.clone();
			this.count = count;
			this.worn = worn;
			this.usedUp = usedUp;
		}

		public String getLabel()
		{
			return label;
		}

		public int[] getItemIds()
		{
			return itemIds.clone();
		}

		public int getCount()
		{
			return count;
		}

		public boolean isWorn()
		{
			return worn;
		}

		public boolean isUsedUp()
		{
			return usedUp;
		}

		@Override
		public Check check(PlayerState p)
		{
			String what = label + (count > 1 ? " x" + count : "");
			Check met = worn && wornCount(p) < count ? Check.todo("wear " + label) : Check.MET;
			if (p.isAllItems())
			{
				return met;
			}
			int carried = 0;
			int banked = 0;
			for (int id : itemIds)
			{
				carried += p.carried(id);
				banked += p.banked(id);
			}
			if (carried >= count)
			{
				return met;
			}
			if (carried + banked >= count)
			{
				return Check.bank(what + " from the bank", new BankItem(what, itemIds, count - carried, usedUp, count));
			}
			return Check.notMet("item " + what);
		}

		/** How many equipment slots hold one of these items (one each). */
		private int wornCount(PlayerState p)
		{
			int n = 0;
			for (EquipSlot slot : EquipSlot.values())
			{
				int w = p.worn(slot);
				for (int id : itemIds)
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

		@Override
		void write(StringBuilder sb)
		{
			sb.append("item ").append(label);
			if (count > 1)
			{
				sb.append(" x").append(count);
			}
			if (worn)
			{
				sb.append(" worn");
			}
			if (usedUp)
			{
				sb.append(" used up");
			}
		}
	}

	/** Carrying at least this many coins (a fare or a toll). */
	public static final class Coins extends Requirement
	{
		private final int amount;

		public Coins(int amount)
		{
			this.amount = amount;
		}

		public int getAmount()
		{
			return amount;
		}

		@Override
		public Check check(PlayerState p)
		{
			int cap = p.getCoinCap();
			if (cap >= 0 && amount > cap)
			{
				return Check.notMet("coins " + amount + " (over your limit of " + cap + ")");
			}
			if (p.isAllItems())
			{
				return Check.MET;
			}
			int carried = p.carried(PlayerState.COINS);
			if (carried >= amount)
			{
				return Check.MET;
			}
			if (carried + p.banked(PlayerState.COINS) >= amount)
			{
				return Check.bank("coins " + amount + " from the bank", new BankItem("coins " + amount, new int[]{PlayerState.COINS},
					amount - carried, true, amount));
			}
			return Check.notMet("coins " + amount + " (has " + (carried + p.banked(PlayerState.COINS)) + ")");
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("coins ").append(amount);
		}
	}

	/** A members account. */
	public static final class Members extends Requirement
	{
		public static final Members INSTANCE = new Members();

		private Members()
		{
		}

		@Override
		public Check check(PlayerState p)
		{
			return p.isMembers() ? Check.MET : Check.notMet("members");
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("members");
		}
	}

	/** Nothing worn in this slot (always met: the player can take it off on the way). */
	public static final class EmptySlot extends Requirement
	{
		private final EquipSlot slot;

		public EmptySlot(EquipSlot slot)
		{
			this.slot = slot;
		}

		public EquipSlot getSlot()
		{
			return slot;
		}

		@Override
		public Check check(PlayerState p)
		{
			return p.worn(slot) == -1 ? Check.MET : Check.todo("take off your " + slot.getLabel() + " item");
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("empty ").append(slot.getLabel());
		}
	}

	/** Something the game can't report; the player answers it (a tick in the sidebar). */
	public static final class Answer extends Requirement
	{
		private final String answer;

		public Answer(String answer)
		{
			this.answer = answer.trim();
		}

		public String getAnswer()
		{
			return answer;
		}

		/**
		 * Answered: yes is met, no is not. Not answered yet: not met for a player whose answers are all known (test
		 * players); for the plugin's player ({@link PlayerState#isAskUnanswered()}) it counts as yes until the player
		 * says no, so it is met resting on the answer ({@link Check#getUnanswered()}; "not answer X" is then not met,
		 * resting on it too). A question that counts as no until answered ({@link PlayerState#isNoUntilAnswered}, the
		 * Prifddinas respawn point) is not met, resting on the answer.
		 */
		@Override
		public Check check(PlayerState p)
		{
			Boolean said = p.answer(answer);
			if (said != null)
			{
				return said ? Check.MET : Check.notMet(toText());
			}
			if (!p.isAskUnanswered())
			{
				return Check.notMet(toText());
			}
			return PlayerState.isNoUntilAnswered(answer) ? Check.notMet(toText(), List.of(answer)) : Check.metIfAnswered(answer);
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("answer ").append(answer);
		}
	}

	/**
	 * One of these items is in the player's collection log (e.g. the fire cape for the Mor Ul Rek hot vent doors, a
	 * Desert Treasure II tablet for a ring of shadows teleport). The plugin learns it from the
	 * collection log pages the player opens and from the game's "New item added to your collection log" message; not
	 * seen yet = not in the log.
	 */
	public static final class CollectionLog extends Requirement
	{
		final String label;
		private final int[] itemIds;

		/** {@code label}: the item group's name, or the ids joined with "/". */
		public CollectionLog(String label, int[] itemIds)
		{
			if (itemIds.length == 0)
			{
				throw new IllegalArgumentException("collection log: no ids");
			}
			this.label = label;
			this.itemIds = itemIds.clone();
		}

		public int[] getItemIds()
		{
			return itemIds.clone();
		}

		@Override
		public Check check(PlayerState p)
		{
			if (p.isAllUnlocked())
			{
				return Check.MET;
			}
			for (int id : itemIds)
			{
				if (p.inCollectionLog(id))
				{
					return Check.MET;
				}
			}
			return Check.notMet(toText());
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("collection log ").append(label);
		}
	}

	/**
	 * The player's spirit tree at this place is grown and can be travelled with. Detected, never asked: the plugin
	 * reads the patch's farming varbit while the player is at the patch and remembers it per account; not seen yet =
	 * not grown.
	 */
	public static final class SpiritTree extends Requirement
	{
		final String place;

		public SpiritTree(String place)
		{
			this.place = place.trim();
		}

		@Override
		public Check check(PlayerState p)
		{
			return p.isAllUnlocked() || p.spiritTreeGrown(place) ? Check.MET : Check.notMet(toText());
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("spirit tree ").append(place);
		}
	}

	/**
	 * An axe is stored at the canoe stations, shared by every station, so no axe is needed there. Detected, never
	 * asked: the plugin sees the station's NPC take the axe (its "Store-axe" option) or hand it back. Not seen yet =
	 * maybe stored: met, and the step card marks it with a question mark, as for stored clue items; only a known "no"
	 * is not met.
	 */
	public static final class CanoeAxeStored extends Requirement
	{
		@Override
		public Check check(PlayerState p)
		{
			return p.canoeAxeStored() ? Check.MET : Check.notMet(toText());
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("canoe axe stored");
		}
	}

	/** The player-owned house: any house, the house at a place, or something built in it. */
	public static final class House extends Requirement
	{
		public enum Kind
		{
			ANY,
			AT,
			HAS
		}

		private final Kind kind;
		private final String what;

		public House(Kind kind, String what)
		{
			this.kind = kind;
			this.what = what == null ? "" : what.trim();
		}

		public Kind getKind()
		{
			return kind;
		}

		/** The place (AT) or the built thing (HAS); empty for ANY. */
		public String getWhat()
		{
			return what;
		}

		@Override
		public Check check(PlayerState p)
		{
			boolean ok;
			switch (kind)
			{
				case AT:
					ok = p.houseAt(what);
					break;
				case HAS:
					ok = p.houseHas(what);
					break;
				default:
					ok = p.hasHouse();
			}
			return ok ? Check.MET : Check.notMet(toText());
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("house");
			if (kind == Kind.AT)
			{
				sb.append(" at ").append(what);
			}
			else if (kind == Kind.HAS)
			{
				sb.append(" has ").append(what);
			}
		}
	}

	/** The player's spellbook. */
	public static final class OnSpellbook extends Requirement
	{
		final Spellbook spellbook;

		public OnSpellbook(Spellbook spellbook)
		{
			this.spellbook = spellbook;
		}

		@Override
		public Check check(PlayerState p)
		{
			return p.onSpellbook(spellbook) ? Check.MET : Check.notMet(toText() + " (is " + p.getSpellbook().getLabel() + ")");
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("spellbook ").append(spellbook.getLabel());
		}
	}

	/**
	 * Runes for a spell: {@code count} runes of one kind, where the rune itself and the combination runes that
	 * include it count, and a staff (or other item) that gives unlimited runes of that kind covers it when worn.
	 */
	public static final class Runes extends Requirement
	{
		private final String rune;
		private final int count;
		private final int[] runeIds;
		private final int[] staffIds;

		public Runes(String rune, int count, int[] runeIds, int[] staffIds)
		{
			if (count < 1 || runeIds.length == 0)
			{
				throw new IllegalArgumentException("rune: no ids or count < 1");
			}
			this.rune = rune.trim();
			this.count = count;
			this.runeIds = runeIds.clone();
			this.staffIds = staffIds.clone();
		}

		public String getRune()
		{
			return rune;
		}

		public int getCount()
		{
			return count;
		}

		public int[] getRuneIds()
		{
			return runeIds.clone();
		}

		public int[] getStaffIds()
		{
			return staffIds.clone();
		}

		@Override
		public Check check(PlayerState p)
		{
			if (p.isAllItems())
			{
				return Check.MET;
			}
			boolean staffCarried = false;
			boolean staffBanked = false;
			for (int id : staffIds)
			{
				if (p.wears(id))
				{
					return Check.MET;
				}
				staffCarried |= p.carried(id) > 0;
				staffBanked |= p.banked(id) > 0;
			}
			int carried = 0;
			int banked = 0;
			for (int id : runeIds)
			{
				carried += p.carried(id);
				banked += p.banked(id);
			}
			if (carried >= count)
			{
				return Check.MET;
			}
			if (staffCarried)
			{
				return Check.todo("wield a staff for " + rune + " runes");
			}
			if (carried + banked >= count || staffBanked)
			{
				// what to take out: the runes when the bank has enough, else a staff (worn, it gives them all)
				String what = rune + " runes x" + count;
				BankItem item = carried + banked >= count ? new BankItem(what, runeIds, count - carried, true, count)
					: new BankItem(what, staffIds, 1);
				return Check.bank(what + " from the bank", item);
			}
			return Check.notMet(toText() + " (has " + carried + ")");
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("rune ").append(rune).append(" x").append(count);
		}
	}

	/** The opposite of one part: met when the part is not met (e.g. "not answer respawn point is Prifddinas"). */
	public static final class Not extends Requirement
	{
		private final Requirement part;

		public Not(Requirement part)
		{
			this.part = part;
		}

		public Requirement getPart()
		{
			return part;
		}

		/**
		 * Met when the part is not met. For the plugin's player an unanswered question counts as yes, so "not answer X"
		 * is not met until X is answered no; the answers the part rests on are kept.
		 */
		@Override
		public Check check(PlayerState p)
		{
			Check c = part.check(p);
			return c.getOutcome() == Check.Outcome.NOT_MET
				? Check.of(Check.Outcome.MET, List.of(), List.of(), List.of(), c.getUnanswered())
				: Check.notMet(toText(), c.getUnanswered());
		}

		@Override
		public void visit(Consumer<Requirement> v)
		{
			v.accept(this);
			part.visit(v);
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("not ");
			if (part instanceof All)
			{
				// "not A; B" would read back as "(not A) and B": keep the parts together
				sb.append("one of (");
				part.write(sb);
				sb.append(')');
			}
			else
			{
				part.write(sb);
			}
		}
	}

	/** A combat level (from the real levels) of at least {@code level}. */
	public static final class CombatLevel extends Requirement
	{
		final int level;

		public CombatLevel(int level)
		{
			this.level = level;
		}

		@Override
		public Check check(PlayerState p)
		{
			int has = p.combatLevel();
			return has >= level ? Check.MET : Check.notMet("Combat " + level + " (has " + has + ")");
		}

		@Override
		void write(StringBuilder sb)
		{
			sb.append("Combat ").append(level);
		}
	}
}

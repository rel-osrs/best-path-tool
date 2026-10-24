package com.bestpath.player;

import com.bestpath.core.data.TransportLine;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which parts of the game state the loaded transports' requirements mention:
 * the varbits, varps, quests and items that the player snapshot has to read
 * and that the route has to be searched again for when they change (only
 * what some requirement mentions is tracked).
 *
 * <p>Collected once from the transport data with {@link #collect}; until the
 * plugin has transport data it is {@link #NONE}. Immutable. With
 * {@link #withCooldowns} it also names the data's long-cooldown teleports
 * (home, minigame), whose last-use varps are then watched like the others.
 */
public final class RequirementInputs
{
	/** Nothing is mentioned (no transport data loaded yet). */
	public static final RequirementInputs NONE = new RequirementInputs(Set.of(), Set.of(), Set.of(), Set.of(), List.of(),
		Set.of(), Set.of());

	private final Set<Integer> varbits;
	private final Set<Integer> varps;
	/** Lower-case quest names, as {@link PlayerState} keys them. */
	private final Set<String> quests;
	private final Set<Integer> items;
	/** The distinct requirements these came from (empty when made from the sets alone). */
	private final List<Requirement> requirements;
	/** The long-cooldown teleports the data has (their last-use varps are in {@link #varps} too). */
	private final Set<Cooldown> cooldowns;
	/** Item ids some "collection log ..." requirement names. */
	private final Set<Integer> collectionLog;

	private RequirementInputs(Set<Integer> varbits, Set<Integer> varps, Set<String> quests, Set<Integer> items,
		List<Requirement> requirements, Set<Cooldown> cooldowns, Set<Integer> collectionLog)
	{
		this.collectionLog = Collections.unmodifiableSet(new TreeSet<>(collectionLog));
		this.cooldowns = cooldowns.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(cooldowns));
		this.varbits = Collections.unmodifiableSet(new TreeSet<>(varbits));
		this.varps = Collections.unmodifiableSet(new TreeSet<>(varps));
		TreeSet<String> q = new TreeSet<>();
		for (String s : quests)
		{
			q.add(key(s));
		}
		this.quests = Collections.unmodifiableSet(q);
		this.items = Collections.unmodifiableSet(new TreeSet<>(items));
		this.requirements = List.copyOf(requirements);
	}

	/**
	 * Everything these requirements mention: varbit / varp ids, quest names, and item ids (items, runes and the
	 * staves that replace them, coins).
	 */
	public static RequirementInputs collect(Iterable<Requirement> requirements)
	{
		Set<Integer> varbits = new TreeSet<>();
		Set<Integer> varps = new TreeSet<>();
		Set<String> quests = new TreeSet<>();
		Set<Integer> items = new TreeSet<>();
		Set<Integer> log = new TreeSet<>();
		Set<Requirement> distinct = new LinkedHashSet<>();
		for (Requirement top : requirements)
		{
			if (top == null || top.isNone() || !distinct.add(top))
			{
				continue;
			}
			top.visit(r ->
			{
				if (r instanceof Requirement.Var)
				{
					Requirement.Var v = (Requirement.Var) r;
					(v.isVarp() ? varps : varbits).add(v.getId());
				}
				else if (r instanceof Requirement.Quest)
				{
					quests.add(((Requirement.Quest) r).getQuest());
				}
				else if (r instanceof Requirement.Item)
				{
					for (int id : ((Requirement.Item) r).getItemIds())
					{
						items.add(id);
					}
				}
				else if (r instanceof Requirement.Runes)
				{
					Requirement.Runes rn = (Requirement.Runes) r;
					for (int id : rn.getRuneIds())
					{
						items.add(id);
					}
					for (int id : rn.getStaffIds())
					{
						items.add(id);
					}
				}
				else if (r instanceof Requirement.Coins)
				{
					items.add(PlayerState.COINS);
				}
				else if (r instanceof Requirement.CollectionLog)
				{
					for (int id : ((Requirement.CollectionLog) r).getItemIds())
					{
						log.add(id);
					}
				}
			});
		}
		return new RequirementInputs(varbits, varps, quests, items, new ArrayList<>(distinct), Set.of(), log);
	}

	/**
	 * These inputs and the long-cooldown teleports of the data: their last-use varps are watched
	 * too, and the player snapshot says which are cooling down ({@link Cooldown}).
	 */
	public RequirementInputs withCooldowns(Collection<Cooldown> list)
	{
		if (list.isEmpty())
		{
			return this;
		}
		Set<Cooldown> cs = EnumSet.copyOf(list);
		cs.addAll(cooldowns);
		Set<Integer> v = new TreeSet<>(varps);
		for (Cooldown c : cs)
		{
			v.add(c.getVarp());
		}
		return new RequirementInputs(varbits, v, quests, items, requirements, cs, collectionLog);
	}

	/** The cooldowns of these transport lines ({@link Cooldown#of}: the long-cooldown flag and teleport category). */
	public static Set<Cooldown> cooldownsOf(Iterable<TransportLine> lines)
	{
		Set<Cooldown> out = EnumSet.noneOf(Cooldown.class);
		for (TransportLine l : lines)
		{
			Cooldown c = Cooldown.of(l.isCooldown(), l.getTeleportCategory());
			if (c != null)
			{
				out.add(c);
			}
		}
		return out;
	}

	/** The long-cooldown teleports of the data (empty: none, or no data yet). */
	public Set<Cooldown> getCooldowns()
	{
		return cooldowns;
	}

	public Set<Integer> getVarbits()
	{
		return varbits;
	}

	public Set<Integer> getVarps()
	{
		return varps;
	}

	/** Lower-case quest names. */
	public Set<String> getQuests()
	{
		return quests;
	}

	/** Item ids some requirement asks for (the "transport items" of the ownership cache). */
	public Set<Integer> getItems()
	{
		return items;
	}

	/** Item ids some "collection log ..." requirement names (the only ones remembered from the collection log). */
	public Set<Integer> getCollectionLog()
	{
		return collectionLog;
	}

	/** The distinct requirements (to compare their outcomes before and after a change); may be empty. */
	public List<Requirement> getRequirements()
	{
		return requirements;
	}

	static String key(String questName)
	{
		return questName.trim().toLowerCase(Locale.ROOT);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof RequirementInputs))
		{
			return false;
		}
		RequirementInputs r = (RequirementInputs) o;
		return varbits.equals(r.varbits) && varps.equals(r.varps) && quests.equals(r.quests) && items.equals(r.items)
			&& cooldowns.equals(r.cooldowns) && collectionLog.equals(r.collectionLog);
	}

	@Override
	public int hashCode()
	{
		return varbits.hashCode() * 31 + quests.hashCode();
	}

	@Override
	public String toString()
	{
		return varbits.size() + " varbits, " + varps.size() + " varps, " + quests.size() + " quests, " + items.size()
			+ " items" + (cooldowns.isEmpty() ? "" : ", cooldowns " + cooldowns);
	}
}

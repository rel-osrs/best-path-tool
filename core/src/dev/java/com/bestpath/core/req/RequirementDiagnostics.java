package com.bestpath.core.req;

/**
 * What development code reads from {@link Requirement}s that the plugin itself never needs (the Data Tool puts them
 * in words).
 */
public final class RequirementDiagnostics
{
	private RequirementDiagnostics()
	{
	}

	/** The item group's name, or the ids joined with "/". */
	public static String label(Requirement.CollectionLog r)
	{
		return r.label;
	}

	public static int level(Requirement.CombatLevel r)
	{
		return r.level;
	}

	public static Spellbook spellbook(Requirement.OnSpellbook r)
	{
		return r.spellbook;
	}

	public static String place(Requirement.SpiritTree r)
	{
		return r.place;
	}
}

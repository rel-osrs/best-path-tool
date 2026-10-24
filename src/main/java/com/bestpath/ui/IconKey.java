package com.bestpath.ui;

import com.bestpath.core.req.Skill;
import java.util.Objects;

/**
 * Which picture to show: an item's inventory picture, a skill icon (big or
 * small) or a game sprite (by its public RuneLite {@code SpriteID}).
 * Immutable; a key of the sidebar's icon cache.
 */
public final class IconKey
{
	public enum Type
	{
		/** An item's inventory picture (RuneLite ItemManager). */
		ITEM,
		/** A skill's icon, the big one (RuneLite SkillIconManager). */
		SKILL,
		/** A skill's small icon (RuneLite SkillIconManager). */
		SKILL_SMALL,
		/** A game sprite: {@code id} is a public {@code net.runelite.api.gameval.SpriteID} (RuneLite SpriteManager). */
		SPRITE
	}

	private final Type type;
	private final int id;
	private final Skill skill;

	private IconKey(Type type, int id, Skill skill)
	{
		this.type = type;
		this.id = id;
		this.skill = skill;
	}

	public static IconKey item(int itemId)
	{
		return new IconKey(Type.ITEM, itemId, null);
	}

	public static IconKey skill(Skill skill)
	{
		return new IconKey(Type.SKILL, skill.ordinal(), Objects.requireNonNull(skill));
	}

	public static IconKey smallSkill(Skill skill)
	{
		return new IconKey(Type.SKILL_SMALL, skill.ordinal(), Objects.requireNonNull(skill));
	}

	public static IconKey sprite(int spriteId)
	{
		return new IconKey(Type.SPRITE, spriteId, null);
	}

	public Type getType()
	{
		return type;
	}

	/** The item id, sprite id, or the skill's ordinal. */
	public int getId()
	{
		return id;
	}

	/** The skill (skill icons), or null. */
	public Skill getSkill()
	{
		return skill;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof IconKey && ((IconKey) o).type == type && ((IconKey) o).id == id;
	}

	@Override
	public int hashCode()
	{
		return type.ordinal() * 1_000_003 + id;
	}

	@Override
	public String toString()
	{
		return type == Type.SKILL || type == Type.SKILL_SMALL ? type + " " + skill : type + " " + id;
	}
}

package com.bestpath.ui;

import com.bestpath.route.StepInfo;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The sidebar's pictures in the plugin, from RuneLite's public managers:
 * item pictures from {@link ItemManager#getImage(int)} (drawn by the client a
 * moment later), skill icons from {@link SkillIconManager} and sprites from
 * {@link SpriteManager#getSpriteAsync(int, int, Consumer)} (loaded on the
 * client thread); item names (the bank card's box) from
 * {@link ItemManager#getItemComposition(int)}, read on the client thread.
 * Every picture and name is handed to the sidebar on the Swing thread;
 * nothing here blocks the Swing thread or the client thread. Asked once per
 * picture (the sidebar keeps them, {@link IconCache}).
 */
public final class RuneLiteIcons implements IconProvider
{
	private final ItemManager items;
	private final SkillIconManager skills;
	private final SpriteManager sprites;
	private final ClientThread clientThread;

	public RuneLiteIcons(ItemManager items, SkillIconManager skills, SpriteManager sprites, ClientThread clientThread)
	{
		this.items = items;
		this.skills = skills;
		this.sprites = sprites;
		this.clientThread = clientThread;
	}

	@Override
	public void load(IconKey key, Consumer<BufferedImage> done)
	{
		switch (key.getType())
		{
			case ITEM:
			{
				// coins drawn as a big stack, without the quantity number
				AsyncBufferedImage img = key.getId() == StepInfo.COINS_ITEM
					? items.getImage(key.getId(), StepInfo.COINS_PICTURE_QUANTITY, false) : items.getImage(key.getId());
				img.onLoaded(() -> SwingUtilities.invokeLater(() -> done.accept(img)));
				break;
			}
			case SKILL:
			case SKILL_SMALL:
			{
				Skill s = skill(key);
				BufferedImage img = s == null ? null : skills.getSkillImage(s, key.getType() == IconKey.Type.SKILL_SMALL);
				if (img != null)
				{
					done.accept(img); // a resource of the client jar: already here (Swing thread)
				}
				break;
			}
			case SPRITE:
				sprites.getSpriteAsync(key.getId(), 0, img -> SwingUtilities.invokeLater(() -> done.accept(img)));
				break;
			default:
				break;
		}
	}

	@Override
	public void itemName(int itemId, Consumer<String> done)
	{
		// item definitions are read on the client thread (RuneLite's rule); the name goes back to Swing
		clientThread.invokeLater(() ->
		{
			ItemComposition c = items.getItemComposition(itemId);
			String name = c == null ? null : c.getName();
			SwingUtilities.invokeLater(() -> done.accept(name));
		});
	}

	/** Our skill as RuneLite's (same names), or null. */
	static Skill skill(IconKey key)
	{
		try
		{
			return Skill.valueOf(key.getSkill().name());
		}
		catch (IllegalArgumentException | NullPointerException e)
		{
			return null;
		}
	}
}

package com.bestpath.dev;

import com.bestpath.ui.IconKey;
import com.bestpath.ui.IconProvider;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;

/**
 * DEV ONLY: the sidebar's pictures in the UI preview, which has no game
 * client (so no ItemManager or SpriteManager). Item pictures and sprites come
 * from build/preview-icons/ (drawn from the game cache by
 * {@code gradlew previewIcons}); skill icons from RuneLite's own
 * SkillIconManager (its pictures are files in the client jar). A picture that
 * is not there yet shows as a grey placeholder square with "?" (preview only).
 */
final class PreviewIcons implements IconProvider
{
	/** Where gradlew previewIcons writes the pictures (relative to the project folder). */
	static final File DIR = new File("build/preview-icons");

	private final File dir;
	private final SkillIconManager skills = new SkillIconManager();
	private boolean warned;

	PreviewIcons(File dir)
	{
		this.dir = dir;
	}

	/** Item names (item-names.tsv, written by previewIcons from the cache), read once. */
	private java.util.Map<Integer, String> names;

	/** An item's name from the cache (the plugin asks RuneLite's item definitions); null if not listed. */
	String itemName(int id)
	{
		if (names == null)
		{
			names = new java.util.HashMap<>();
			File f = new File(dir, "item-names.tsv");
			if (f.isFile())
			{
				try
				{
					for (String line : java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8))
					{
						int tab = line.indexOf('\t');
						if (tab > 0)
						{
							names.put(Integer.parseInt(line.substring(0, tab).trim()), line.substring(tab + 1));
						}
					}
				}
				catch (IOException | NumberFormatException e)
				{
					// no names: the card shows "Item <id>"
				}
			}
		}
		return names.get(id);
	}

	/** The bank card's box: names from item-names.tsv, at once (none listed: the box keeps the data's words). */
	@Override
	public void itemName(int itemId, Consumer<String> done)
	{
		done.accept(itemName(itemId));
	}

	@Override
	public void load(IconKey key, Consumer<BufferedImage> done)
	{
		switch (key.getType())
		{
			case SKILL:
			case SKILL_SMALL:
				done.accept(skills.getSkillImage(Skill.valueOf(key.getSkill().name()), key.getType() == IconKey.Type.SKILL_SMALL));
				return;
			case ITEM:
				done.accept(fileOr(new File(dir, "item-" + key.getId() + ".png"), 32, 28));
				return;
			case SPRITE:
			default:
				done.accept(fileOr(new File(dir, "sprite-" + key.getId() + ".png"), 22, 22));
		}
	}

	private BufferedImage fileOr(File f, int w, int h)
	{
		if (f.isFile())
		{
			try
			{
				return ImageIO.read(f);
			}
			catch (IOException e)
			{
				// fall through to the placeholder
			}
		}
		if (!warned)
		{
			warned = true;
			System.out.println("  (some step pictures are placeholders: run gradlew previewIcons once to draw them from the cache)");
		}
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(110, 110, 110));
		g.fillRect(0, 0, w, h);
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, h / 2)));
		g.drawString("?", w / 2 - g.getFontMetrics().stringWidth("?") / 2, h / 2 + g.getFontMetrics().getAscent() / 2 - 1);
		g.dispose();
		return img;
	}
}

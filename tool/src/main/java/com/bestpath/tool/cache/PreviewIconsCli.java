package com.bestpath.tool.cache;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * DEV ONLY: draws the pictures the plugin's sidebar uses from the game cache,
 * for the plugin's fast UI preview (which runs without the game client, so
 * RuneLite's ItemManager and SpriteManager can't draw them there). Item
 * pictures as in the Item groups window ({@link LoadedCache#itemIcon}),
 * sprites by their RuneLite gameval SpriteID number. Nothing here goes into
 * the plugin. Called by the plugin build's {@code gradlew previewIcons}.
 *
 * <p>Arguments: cache folder, the list file (lines "item &lt;id&gt;" or "sprite &lt;id&gt; [label]"), output folder.
 * Writes item-&lt;id&gt;.png, sprite-&lt;id&gt;.png and sprites-sheet.png (every sprite with its label, to check
 * by eye that each SpriteID constant is the picture it is named for).
 */
public final class PreviewIconsCli
{
	private PreviewIconsCli()
	{
	}

	public static void main(String[] args) throws IOException
	{
		if (args.length < 3)
		{
			System.err.println("usage: PreviewIconsCli <cache folder> <list file> <output folder>");
			System.exit(2);
		}
		long t0 = System.nanoTime();
		File out = new File(args[2]);
		if (!out.isDirectory() && !out.mkdirs())
		{
			throw new IOException("cannot create " + out);
		}
		List<String> wanted = Files.readAllLines(new File(args[1]).toPath(), StandardCharsets.UTF_8);
		int items = 0;
		// the items' names too (the clue card names its items; the plugin asks RuneLite's item definitions)
		List<String> names = new ArrayList<>();
		int sprites = 0;
		List<String> missing = new ArrayList<>();
		List<BufferedImage> sheetImages = new ArrayList<>();
		List<String> sheetLabels = new ArrayList<>();
		try (LoadedCache cache = LoadedCache.open(new File(args[0])))
		{
			for (String line : wanted)
			{
				String[] w = line.trim().split("\\s+", 3);
				if (w.length < 2 || w[0].startsWith("#"))
				{
					continue;
				}
				int id = Integer.parseInt(w[1]);
				if (w[0].equals("item"))
				{
					// coins as the plugin shows them: the 10000 stack
					BufferedImage img = cache.itemIcon(id, id == 995 ? 10000 : 1);
					if (img == null)
					{
						missing.add(line);
						continue;
					}
					ImageIO.write(img, "png", new File(out, "item-" + id + ".png"));
					String name = cache.itemName(id);
					if (name != null && !name.isEmpty() && !name.equals("null"))
					{
						names.add(id + "\t" + name);
					}
					items++;
				}
				else if (w[0].equals("sprite"))
				{
					BufferedImage img = cache.spriteImage(id, 0);
					if (img == null)
					{
						missing.add(line);
						continue;
					}
					ImageIO.write(img, "png", new File(out, "sprite-" + id + ".png"));
					sprites++;
					sheetImages.add(img);
					sheetLabels.add(id + " " + (w.length > 2 ? w[2] : ""));
				}
			}
		}
		writeSheet(new File(out, "sprites-sheet.png"), sheetImages, sheetLabels);
		Files.write(new File(out, "item-names.tsv").toPath(), names, StandardCharsets.UTF_8);
		System.out.printf("Preview icons: %d items, %d sprites in %s (%d ms)%n", items, sprites, out,
			(System.nanoTime() - t0) / 1_000_000);
		for (String m : missing)
		{
			System.out.println("  not in the cache: " + m);
		}
	}

	/** Every sprite (small ones at twice their size) with its id and label, in two columns (to check the names by eye). */
	private static void writeSheet(File f, List<BufferedImage> imgs, List<String> labels) throws IOException
	{
		int rowH = 56;
		int colW = 500;
		int rows = Math.max(1, (imgs.size() + 1) / 2);
		BufferedImage sheet = new BufferedImage(2 * colW, rows * rowH, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(new Color(40, 40, 40));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		for (int i = 0; i < imgs.size(); i++)
		{
			BufferedImage img = imgs.get(i);
			int s = img.getWidth() <= 26 && img.getHeight() <= 26 ? 2 : 1;
			int x = (i / rows) * colW;
			int y = (i % rows) * rowH;
			g.drawImage(img, x + 4, y + 2, img.getWidth() * s, img.getHeight() * s, null);
			g.setColor(Color.WHITE);
			g.drawString(labels.get(i), x + 70, y + 30);
		}
		g.dispose();
		ImageIO.write(sheet, "png", f);
	}
}

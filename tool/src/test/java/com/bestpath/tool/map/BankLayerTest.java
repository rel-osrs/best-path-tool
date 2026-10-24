package com.bestpath.tool.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.data.BankList;
import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.bank.ToolBanks;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The "Banks" layer draws the banks of data/banks/banks.tsv: Varrock west bank's booths (gold) and the tiles they
 * are used from (green); the picture is kept in tool/build/bank-layer.png to look at.
 */
class BankLayerTest
{
	@Test
	void varrockWestBankIsDrawnWithItsUseTiles() throws Exception
	{
		BankList list = ToolBanks.list();
		assertFalse(list.getBanks().isEmpty(), "data/banks/banks.tsv: " + ToolBanks.problem());
		assertTrue(ToolBanks.rows().stream().anyMatch(r -> r.bank.equals("Varrock west bank") && r.use.length > 0));
		int w = 480;
		int h = 360;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.DARK_GRAY);
		g.fillRect(0, 0, w, h);
		MapViewport vp = new MapViewport(3185, 3440, 16);
		vp.setSize(w, h);
		new BankLayer().draw(g, vp, 0, (int) vp.worldX(0), (int) vp.worldY(h), (int) vp.worldX(w), (int) vp.worldY(0));
		g.dispose();
		int gold = 0;
		int green = 0;
		for (int x = 0; x < w; x++)
		{
			for (int y = 0; y < h; y++)
			{
				int rgb = img.getRGB(x, y) & 0xFFFFFF;
				gold += rgb == 0xf4c430 ? 1 : 0;
				green += rgb == 0x38e05a ? 1 : 0;
			}
		}
		File out = new File(ToolSettings.findWorkspace(), "tool/build/bank-layer.png");
		out.getParentFile().mkdirs();
		ImageIO.write(img, "png", out);
		assertTrue(gold > 100, "booths drawn: " + gold);
		assertTrue(green > 100, "use tiles drawn: " + green);
	}
}

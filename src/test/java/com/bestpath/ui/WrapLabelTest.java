package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;

public class WrapLabelTest
{
	private static FontMetrics metrics()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		FontMetrics fm = g.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.dispose();
		return fm;
	}

	@Test
	public void noLineIsWiderThanTheWidthAndNoWordIsLost()
	{
		FontMetrics fm = metrics();
		String text = "No weapons or armour may be taken into Glarial's Tomb; food and potions are fine.";
		List<String> lines = WrapLabel.wrap(text, fm, 100);
		assertTrue(lines.size() > 2);
		for (String l : lines)
		{
			assertTrue(l, fm.stringWidth(l) <= 100);
		}
		assertEquals(text, String.join(" ", lines));
	}

	@Test
	public void longWordsAreCutAndNewLinesKept()
	{
		FontMetrics fm = metrics();
		List<String> lines = WrapLabel.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\nb", fm, 40);
		for (String l : lines)
		{
			assertTrue(l, fm.stringWidth(l) <= 40);
		}
		assertEquals("b", lines.get(lines.size() - 1));
		assertEquals(40, String.join("", lines.subList(0, lines.size() - 1)).length());
	}

	@Test
	public void anArrowStaysWithTheDestination()
	{
		FontMetrics fm = metrics();
		String title = "Construct. cape -> Rimmington";
		int width = fm.stringWidth("Construct. cape ->") + 2; // "Construct. cape ->" would fit, the arrow still moves on
		assertEquals(List.of("Construct. cape", "-> Rimmington"), WrapLabel.wrap(title, fm, width));
		assertEquals(List.of(title), WrapLabel.wrap(title, fm, 1000));
		// an arrow at the end has nothing to stay with
		assertEquals(List.of("a ->"), WrapLabel.wrap("a ->", fm, 1000));
	}

	@Test
	public void shortTextIsOneLine()
	{
		assertEquals(List.of("Walk"), WrapLabel.wrap("Walk", metrics(), 200));
		assertEquals(List.of(""), WrapLabel.wrap("", metrics(), 200));
	}
}

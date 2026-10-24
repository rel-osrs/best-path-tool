package com.bestpath.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * Text that wraps at a fixed width, measured with the label's own font.
 * (Swing's HTML labels measure "width:Npx" differently from how they paint
 * RuneLite's fonts, which cut text off at the sidebar's edge.) The width is
 * the narrowest the sidebar ever gives (scrollbar showing), so nothing is
 * cut off when it is wider.
 */
final class WrapLabel extends JComponent
{
	private final int wrapWidth;
	private String text;
	private List<String> lines;

	WrapLabel(String text, int wrapWidth, Font font, Color color)
	{
		this.wrapWidth = wrapWidth;
		this.text = text == null ? "" : text;
		setFont(font);
		setForeground(color);
		setAlignmentX(LEFT_ALIGNMENT);
	}

	void setText(String text)
	{
		this.text = text == null ? "" : text;
		lines = null;
		revalidate();
		repaint();
	}

	String getText()
	{
		return text;
	}

	@Override
	public void setFont(Font font)
	{
		super.setFont(font);
		lines = null;
	}

	private List<String> lines()
	{
		if (lines == null)
		{
			lines = wrap(text, getFontMetrics(getFont()), wrapWidth);
		}
		return lines;
	}

	/**
	 * Splits text into lines no wider than {@code width} (breaking at spaces; very long words are cut). An arrow "->"
	 * stays with the word after it, so a card title wraps as "Construct. cape" / "-> Rimmington".
	 */
	static List<String> wrap(String text, FontMetrics fm, int width)
	{
		List<String> out = new ArrayList<>();
		for (String para : text.split("\n", -1))
		{
			StringBuilder line = new StringBuilder();
			for (String word : words(para))
			{
				if (word.isEmpty())
				{
					continue;
				}
				String tryLine = line.length() == 0 ? word : line + " " + word;
				if (fm.stringWidth(tryLine) <= width)
				{
					line.setLength(0);
					line.append(tryLine);
					continue;
				}
				if (line.length() > 0)
				{
					out.add(line.toString());
					line.setLength(0);
				}
				// a word wider than the line: cut it
				String w = word;
				while (fm.stringWidth(w) > width && w.length() > 1)
				{
					int n = w.length() - 1;
					while (n > 1 && fm.stringWidth(w.substring(0, n)) > width)
					{
						n--;
					}
					out.add(w.substring(0, n));
					w = w.substring(n);
				}
				line.append(w);
			}
			out.add(line.toString());
		}
		return out;
	}

	/** The words of one paragraph; an arrow joined with the word after it ("-> Rimmington"). */
	private static List<String> words(String para)
	{
		String[] w = para.split(" ");
		List<String> out = new ArrayList<>(w.length);
		for (int i = 0; i < w.length; i++)
		{
			if ("->".equals(w[i]) && i + 1 < w.length && !w[i + 1].isEmpty())
			{
				out.add(w[i] + " " + w[++i]);
			}
			else
			{
				out.add(w[i]);
			}
		}
		return out;
	}

	@Override
	public Dimension getPreferredSize()
	{
		if (text.isEmpty())
		{
			return new Dimension(0, 0);
		}
		FontMetrics fm = getFontMetrics(getFont());
		int w = 0;
		for (String l : lines())
		{
			w = Math.max(w, fm.stringWidth(l));
		}
		return new Dimension(w, fm.getHeight() * lines().size());
	}

	@Override
	public Dimension getMinimumSize()
	{
		return getPreferredSize();
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
		g2.setFont(getFont());
		g2.setColor(getForeground());
		FontMetrics fm = g2.getFontMetrics();
		int y = fm.getAscent();
		for (String l : lines())
		{
			g2.drawString(l, 0, y);
			y += fm.getHeight();
		}
		g2.dispose();
	}
}

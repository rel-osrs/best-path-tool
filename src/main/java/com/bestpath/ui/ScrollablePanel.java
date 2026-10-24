package com.bestpath.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/** A panel inside a vertical scroll pane that always takes the viewport's width (never scrolls sideways). */
final class ScrollablePanel extends JPanel implements Scrollable
{
	@Override
	public Dimension getPreferredScrollableViewportSize()
	{
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return 16;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return Math.max(16, visibleRect.height - 32);
	}

	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight()
	{
		return false;
	}
}

package com.bestpath.tool.ui;

import com.bestpath.tool.map.MapView;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 * DEVELOPER AID for {@link UiCheck}: finds the tool's real controls in a window and uses them as a user would
 * (tick boxes, buttons, lists, a mouse click on the map). Swing thread only.
 */
final class UiCheckControls
{
	private UiCheckControls()
	{
	}

	/** The Where row's container (the one with the "Move" button), or null. */
	private static Container whereContainer(Container root)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JButton && "Move".equals(((JButton) c).getText()))
			{
				return root;
			}
			if (c instanceof Container)
			{
				Container found = whereContainer((Container) c);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** The Where row's six boxes: start x, y, plane, end x, y, plane. */
	static JTextField[] whereFields(Container root)
	{
		List<JTextField> out = new ArrayList<>();
		for (Component c : whereContainer(root).getComponents())
		{
			if (c instanceof JTextField)
			{
				out.add((JTextField) c);
			}
		}
		return out.toArray(new JTextField[0]);
	}

	/** The Where row as text: boxes in [], the Move button's state, the check. */
	static String whereRow(Container root)
	{
		StringBuilder sb = new StringBuilder();
		for (Component c : whereContainer(root).getComponents())
		{
			if (c instanceof JTextField)
			{
				sb.append("[").append(((JTextField) c).getText()).append("] ");
			}
			else if (c instanceof JButton)
			{
				sb.append(((JButton) c).getText()).append(((JButton) c).isEnabled() ? " (on) " : " (off) ");
			}
			else if (c instanceof JLabel)
			{
				sb.append(((JLabel) c).getText().trim()).append(" ");
			}
		}
		return sb.toString().trim();
	}

	/** A left click (press and release) on the map at a world point, as the mouse does it. */
	static void mouseClick(MapView view, double wx, double wy)
	{
		int sx = (int) view.getViewport().screenX(wx);
		int sy = (int) view.getViewport().screenY(wy);
		long now = System.currentTimeMillis();
		for (int id : new int[]{MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED})
		{
			view.dispatchEvent(new MouseEvent(view, id, now, id == MouseEvent.MOUSE_PRESSED
				? InputEvent.BUTTON1_DOWN_MASK : 0, sx, sy, 1, false, MouseEvent.BUTTON1));
		}
		System.out.println("clicked the map at " + wx + ", " + wy);
	}

	/** Presses the first showing button with this text inside the container (a card's button on the map); its card's text, or null. */
	static String pressButton(Container root, String text)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JButton && text.equals(((JButton) c).getText()) && c.isShowing())
			{
				String card = "";
				for (Component k : c.getParent().getParent().getComponents())
				{
					card += k instanceof JLabel ? ((JLabel) k).getText().replaceAll("<[^>]+>", "") : "";
				}
				((JButton) c).doClick();
				return card;
			}
			if (c instanceof Container)
			{
				String r = pressButton((Container) c, text);
				if (r != null)
				{
					return r;
				}
			}
		}
		return null;
	}

	/** Selects the first row of the first table that has rows (the bottom transport list). */
	static boolean clickFirstRow(Container root)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JTable && ((JTable) c).getRowCount() > 0 && c.isShowing())
			{
				JTable t = (JTable) c;
				t.setRowSelectionInterval(0, 0);
				System.out.println("picked row 0: " + t.getValueAt(0, 1) + " at " + t.getValueAt(0, 2));
				return true;
			}
			if (c instanceof Container && clickFirstRow((Container) c))
			{
				return true;
			}
		}
		return false;
	}

	/** Picks an item (by its text) in every combo box that has it. */
	static void pickCombo(Container root, String text)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JComboBox)
			{
				JComboBox<?> box = (JComboBox<?>) c;
				for (int i = 0; i < box.getItemCount(); i++)
				{
					if (text.equals(String.valueOf(box.getItemAt(i))))
					{
						box.setSelectedIndex(i);
						System.out.println("picked \"" + text + "\"");
					}
				}
			}
			if (c instanceof Container)
			{
				pickCombo((Container) c, text);
			}
		}
	}

	/** Clicks the tick box with this text, as a user would (developer check of the real control). */
	static void clickBox(Container root, String text)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JCheckBox && text.equals(((JCheckBox) c).getText()))
			{
				((JCheckBox) c).doClick();
				System.out.println("clicked \"" + text + "\": now " + ((JCheckBox) c).isSelected());
				return;
			}
			if (c instanceof Container)
			{
				clickBox((Container) c, text);
			}
		}
	}
}

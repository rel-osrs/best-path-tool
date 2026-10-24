package com.bestpath.tool;

import com.bestpath.tool.ui.MainWindow;
import java.io.File;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Entry point of the BestPath Data Tool. */
public final class BestPathTool
{
	private BestPathTool()
	{
	}

	public static void main(String[] args)
	{
		File workspace = ToolSettings.findWorkspace();
		ToolSettings settings = ToolSettings.forUser();
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			}
			catch (Exception ignored)
			{
				// default look and feel is fine
			}
			new MainWindow(settings, workspace).setVisible(true);
		});
	}
}

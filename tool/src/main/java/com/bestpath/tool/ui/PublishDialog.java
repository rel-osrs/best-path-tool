package com.bestpath.tool.ui;

import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.publish.PluginPublisher;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * "Publish plugin...": copies only the plugin into the public plugin repo's folder ({@link PluginPublisher}), then
 * optionally builds that copy on its own. The folder is remembered in the tool's settings ("publish.folder").
 */
final class PublishDialog extends JDialog
{
	private static final String KEY = "publish.folder";

	private final File workspace;
	private final ToolSettings settings;
	private final JTextField folder = new JTextField(40);
	private final JTextArea log = new JTextArea(18, 80);
	private final JButton publish = new JButton("Publish");
	private final JButton build = new JButton("Publish and build check");
	private volatile boolean busy;

	PublishDialog(JFrame owner, File workspace, ToolSettings settings)
	{
		super(owner, "Publish the plugin", false);
		this.workspace = workspace;
		this.settings = settings;
		folder.setText(settings.get(KEY, PluginPublisher.defaultFolder(workspace).getPath()));

		JPanel top = new JPanel(new BorderLayout(4, 4));
		top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
		JLabel help = new JLabel("<html>Copies only the plugin (its code, data, build files, README and notices) into the"
			+ " public plugin repo's folder. That folder ends up holding exactly these files: never change it by hand,"
			+ " change things here and publish again. Then commit there and push to GitHub.</html>");
		help.setPreferredSize(new Dimension(620, 48));
		top.add(help, BorderLayout.NORTH);
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		row.add(new JLabel("Public folder:"));
		row.add(folder);
		JButton choose = new JButton("Choose...");
		choose.addActionListener(e -> choose());
		row.add(choose);
		top.add(row, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		publish.setToolTipText("Copy the plugin into the public folder");
		publish.addActionListener(e -> run(false));
		build.setToolTipText("Copy, then build the public folder on its own the way the Plugin Hub does (a few minutes)");
		build.addActionListener(e -> run(true));
		buttons.add(publish);
		buttons.add(build);
		top.add(buttons, BorderLayout.SOUTH);

		log.setEditable(false);
		log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		JPanel body = new JPanel(new BorderLayout());
		body.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
		body.add(new JScrollPane(log), BorderLayout.CENTER);

		getContentPane().add(top, BorderLayout.NORTH);
		getContentPane().add(body, BorderLayout.CENTER);
		pack();
		setLocationRelativeTo(owner);
	}

	private void choose()
	{
		JFileChooser fc = new JFileChooser(new File(folder.getText()).getParentFile());
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			folder.setText(fc.getSelectedFile().getPath());
		}
	}

	private void run(boolean andBuild)
	{
		if (busy)
		{
			return;
		}
		busy = true;
		publish.setEnabled(false);
		build.setEnabled(false);
		log.setText("");
		File target = new File(folder.getText().trim());
		settings.set(KEY, target.getPath());
		Thread t = new Thread(() ->
		{
			try
			{
				PluginPublisher.publish(workspace, target, this::show);
				if (andBuild)
				{
					PluginPublisher.buildCheck(target, this::show);
				}
			}
			catch (Exception ex)
			{
				show("Not published: " + ex.getMessage());
			}
			finally
			{
				SwingUtilities.invokeLater(() ->
				{
					busy = false;
					publish.setEnabled(true);
					build.setEnabled(true);
				});
			}
		}, "publish-plugin");
		t.setDaemon(true);
		t.start();
	}

	private void show(String line)
	{
		SwingUtilities.invokeLater(() ->
		{
			log.append(line + "\n");
			log.setCaretPosition(log.getDocument().getLength());
		});
	}
}

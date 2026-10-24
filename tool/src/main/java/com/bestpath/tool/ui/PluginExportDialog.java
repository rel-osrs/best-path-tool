package com.bestpath.tool.ui;

import com.bestpath.tool.route.PluginDataUpdate;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * "Update plugin data": makes the plugin's data from the open cache and every change in the Data Tool, the same
 * export as {@code gradlew -p tool exportPlugin} ({@link com.bestpath.tool.route.PluginExportCli}, run as a program of
 * its own so the tool's memory is not used), into a folder of its own; only when its check passes (the plugin's search
 * finds the same routes as the tool's) are the changed files put into the plugin ({@link PluginDataUpdate}). Shows
 * what changed.
 */
final class PluginExportDialog extends JDialog
{
	/** Where the export writes first (git-ignored, emptied before each export). */
	static final String WORK_FOLDER = "cache/plugin-export";

	private final File workspace;
	private final File cacheDir;
	private final File pluginDir;
	private final JTextArea log = new JTextArea(24, 100);
	private final JLabel status = new JLabel(" ");
	private final JProgressBar busy = new JProgressBar();
	private final JButton start = new JButton("Update plugin data");
	private final JButton stop = new JButton("Stop");
	private final JButton close = new JButton("Close");
	private volatile Process process;
	private long startedAt;
	private final Timer clock;
	private final List<String> recent = new ArrayList<>();
	/** How the export starts a line you must read (e.g. data kept from before because the game changed). */
	private static final String WARNING = "WARNING:";
	private static final Color WARNING_COLOUR = new Color(0xc0, 0x60, 0x00);

	/**
	 * @param pluginDir the plugin's data folder ({@code src/main/resources/com/bestpath/data}; another folder for checks)
	 */
	PluginExportDialog(Frame owner, File workspace, File cacheDir, File pluginDir)
	{
		super(owner, "Update plugin data", false);
		this.workspace = workspace;
		this.cacheDir = cacheDir;
		this.pluginDir = pluginDir;
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				closeWindow();
			}
		});

		JLabel about = new JLabel("<html>Makes the plugin's data from the open cache and every change you made in the Data Tool, "
			+ "then checks that the plugin finds the same routes as the tool. Only if that check passes are the plugin's data "
			+ "files replaced; otherwise nothing in the plugin changes.<br>It takes about a minute. Changes you make while it "
			+ "runs go into the next update.<br><br><b>Cache:</b> " + cacheDir.getAbsolutePath() + "<br><b>Plugin data:</b> "
			+ pluginDir.getAbsolutePath() + "</html>");
		about.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		log.setEditable(false);
		log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

		status.setFont(status.getFont().deriveFont(Font.BOLD));
		busy.setIndeterminate(true);
		busy.setVisible(false);
		stop.setEnabled(false);
		start.setToolTipText("Export the data and, if the check passes, put it into the plugin");
		stop.setToolTipText("Stop the export; the plugin's data stays as it is");
		start.addActionListener(e -> begin());
		stop.addActionListener(e -> halt());
		close.addActionListener(e -> closeWindow());

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.add(busy);
		buttons.add(start);
		buttons.add(stop);
		buttons.add(close);
		JPanel south = new JPanel(new BorderLayout(8, 0));
		south.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
		south.add(status, BorderLayout.CENTER);
		south.add(buttons, BorderLayout.EAST);

		setLayout(new BorderLayout());
		add(about, BorderLayout.NORTH);
		add(new JScrollPane(log), BorderLayout.CENTER);
		add(south, BorderLayout.SOUTH);
		pack();
		setLocationRelativeTo(owner);

		clock = new Timer(1000, e -> status.setText("Exporting and checking... " + elapsed()));
	}

	/** Starts the export (a program of its own) into {@link #WORK_FOLDER}. */
	private void begin()
	{
		File work = new File(workspace, WORK_FOLDER);
		try
		{
			java.nio.file.Files.createDirectories(work.toPath());
			File[] old = work.listFiles();
			for (File f : old == null ? new File[0] : old)
			{
				if (f.isFile() && !f.delete())
				{
					throw new IOException("could not empty " + work + " (" + f.getName() + ")");
				}
			}
		}
		catch (IOException ex)
		{
			status.setForeground(Color.RED);
			status.setText("Could not start: " + ex.getMessage());
			return;
		}
		String java = new File(new File(System.getProperty("java.home"), "bin"),
			System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java").getPath();
		List<String> cmd = List.of(java, "-Xmx4g", "-Dorg.slf4j.simpleLogger.defaultLogLevel=error", "-cp",
			System.getProperty("java.class.path"), "com.bestpath.tool.route.PluginExportCli", cacheDir.getAbsolutePath(),
			work.getAbsolutePath());
		log.setText("");
		recent.clear();
		status.setForeground(Color.BLACK);
		start.setEnabled(false);
		stop.setEnabled(true);
		busy.setVisible(true);
		startedAt = System.currentTimeMillis();
		clock.start();
		status.setText("Exporting and checking... 0:00");
		Thread t = new Thread(() -> run(cmd, work), "plugin-export");
		t.setDaemon(true);
		t.start();
	}

	/** Background thread: runs the export, shows its lines, then puts the data in place if it passed. */
	private void run(List<String> cmd, File work)
	{
		int exit;
		int warnings = 0;
		try
		{
			Process p = new ProcessBuilder(cmd).directory(workspace).redirectErrorStream(true).start();
			process = p;
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = r.readLine()) != null)
				{
					show(line);
					warnings += line.startsWith(WARNING) ? 1 : 0;
				}
			}
			exit = p.waitFor();
		}
		catch (IOException | InterruptedException ex)
		{
			show("Could not run the export: " + ex.getMessage());
			exit = -1;
		}
		process = null;
		int code = exit;
		if (code != 0)
		{
			SwingUtilities.invokeLater(() -> finished(false, code == 2
				? "Not updated: the check found routes that differ between the tool and the plugin (see above). The plugin's data is unchanged."
				: "Not updated: the export stopped (code " + code + "). The plugin's data is unchanged."));
			return;
		}
		install(work, warnings);
	}

	/** Background thread, after the export passed: shows what changes and puts the changed files into the plugin. */
	private void install(File work, int warnings)
	{
		String warned = warnings == 0 ? "" : " " + warnings + (warnings == 1 ? " warning" : " warnings") + ": see the lines starting "
			+ WARNING + " in the log.";
		try
		{
			PluginDataUpdate.Report report = PluginDataUpdate.compare(pluginDir, work);
			show("");
			show("---- What changes for the plugin ----");
			for (String l : report.lines())
			{
				show(l);
			}
			if (report.isNothingChanged())
			{
				SwingUtilities.invokeLater(() -> finished(true, "Done: the check passed and the plugin already has this data." + warned));
				return;
			}
			// a backup first when the plugin's data is now made from another game cache
			com.bestpath.tool.cache.Backups.beforeCache(workspace,
				com.bestpath.tool.cache.Backups.manifestCacheId(new File(work, "bestpath-data.txt")), this::show);
			PluginDataUpdate.install(work, pluginDir, report);
			show("");
			show("The plugin's data files are updated. Run or rebuild the plugin to use them. They are not committed yet.");
			SwingUtilities.invokeLater(() -> finished(true, "Plugin data updated: " + report.getTransportsChanged().size() + " transports changed, "
				+ report.getTransportsAdded().size() + " new, " + report.getTransportsRemoved().size() + " removed." + warned));
		}
		catch (IOException ex)
		{
			show("Could not put the data into the plugin: " + ex.getMessage());
			SwingUtilities.invokeLater(() -> finished(false, "Not updated: " + ex.getMessage()));
		}
	}

	private void show(String line)
	{
		SwingUtilities.invokeLater(() ->
		{
			log.append(line + "\n");
			log.setCaretPosition(log.getDocument().getLength());
			recent.add(line);
		});
	}

	private void finished(boolean ok, String message)
	{
		clock.stop();
		busy.setVisible(false);
		start.setEnabled(true);
		start.setText("Update again");
		stop.setEnabled(false);
		status.setForeground(!ok ? Color.RED : message.contains(WARNING) ? WARNING_COLOUR : new Color(0, 120, 0));
		status.setText(message + " (" + elapsed() + ")");
	}

	private void halt()
	{
		Process p = process;
		if (p != null)
		{
			p.destroy();
			show("Stopped by you.");
		}
	}

	private void closeWindow()
	{
		if (process != null)
		{
			int a = JOptionPane.showConfirmDialog(this, "The export is still running. Stop it and close?\nThe plugin's data stays as it is.",
				"Update plugin data", JOptionPane.YES_NO_OPTION);
			if (a != JOptionPane.YES_OPTION)
			{
				return;
			}
			halt();
		}
		clock.stop();
		dispose();
	}

	private String elapsed()
	{
		long s = (System.currentTimeMillis() - startedAt) / 1000;
		return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	/** UiCheck: starts the export as the button does. */
	void devStart()
	{
		begin();
	}

	/** UiCheck: true while the export runs. */
	boolean devRunning()
	{
		return process != null || !start.isEnabled();
	}

	/** UiCheck: the status line. */
	String devStatus()
	{
		return status.getText();
	}

	/** UiCheck: the log so far. */
	String devLog()
	{
		return log.getText();
	}
}

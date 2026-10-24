package com.bestpath.tool.ui;

import com.bestpath.tool.cache.CacheInfo;
import com.bestpath.tool.update.CompareFile;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * "Compare with a new cache": picks a newer game cache (or downloads one, without opening it), runs the game update
 * comparison as a program of its own ({@code CacheCompareCli}, fixed rules, the same as
 * {@code gradlew -p tool cacheCompare}), then shows the report and puts the changed regions on the map and a card for
 * everything that needs a look ({@code onDone}). Changes nothing in the data itself: only the cards' answers do.
 * <p>"Preview the plugin's data" makes the clue steps, the banks and the plugin's data from the new cache into
 * {@code cache/cache-move/<new>/} ({@code CacheMove}, three programs of their own) and shows what would change; then
 * "Move to #N" puts the new clue steps and banks into data/ (after a backup) and opens the new cache
 * ({@code openCache}), for "Update plugin data" to make the plugin's data from it.
 */
final class CacheCompareDialog extends JDialog
{
	/** A cache folder found in the workspace's cache/ folder. */
	private static final class Choice
	{
		final File folder;
		final CacheInfo info;

		Choice(File folder, CacheInfo info)
		{
			this.folder = folder;
			this.info = info;
		}

		@Override
		public String toString()
		{
			return info.label() + "  (" + folder.getName() + ")";
		}
	}

	private final File workspace;
	private final File oldCache;
	private final int oldId;
	private final Runnable onDone;
	private final CacheChangeLayer layer;
	private final JComboBox<Choice> caches = new JComboBox<>();
	private final JEditorPane report = new JEditorPane("text/html", "");
	private final JTextArea log = new JTextArea(20, 100);
	private final JTabbedPane tabs = new JTabbedPane();
	private final JLabel status = new JLabel(" ");
	private final JProgressBar busy = new JProgressBar();
	private final JButton start = new JButton("Compare");
	private final JButton firstCard = new JButton("Go to the first card");
	private final JCheckBox onMap = new JCheckBox("Show the changes on the map", true);
	private final JButton preview = new JButton("Preview the plugin's data");
	private final JButton move = new JButton("Move to this cache...");
	private final JEditorPane pluginReport = new JEditorPane("text/html", "");
	private final java.util.function.Consumer<File> openCache;
	private volatile Process process;

	CacheCompareDialog(Frame owner, File workspace, File oldCache, int oldId, CacheChangeLayer layer, Runnable onDone,
		Runnable goToFirstCard, java.util.function.Consumer<File> openCache)
	{
		super(owner, "Compare with a new cache", false);
		this.openCache = openCache;
		this.workspace = workspace;
		this.oldCache = oldCache;
		this.oldId = oldId;
		this.layer = layer;
		this.onDone = onDone;
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				closeWindow();
			}
		});

		setLayout(new BorderLayout());
		add(top(), BorderLayout.NORTH);
		add(tabs(), BorderLayout.CENTER);
		add(bottom(owner, goToFirstCard), BorderLayout.SOUTH);
		refreshCaches();
		showLast();
		updateMove();
		pack();
		setLocationRelativeTo(owner);
	}

	/** What the window does, then the row to pick the new cache and start. */
	private JPanel top()
	{
		JLabel about = new JLabel("<html>Compares the open cache (<b>#" + oldId + "</b>) with a newer one and lists what changed "
			+ "that touches your data: transports, your tile and wall changes, doors you set shut, hidden and kept areas, "
			+ "reviewed regions, map icon kinds. Fixed rules, the same every time. Nothing in your data changes: each thing "
			+ "that needs a look becomes a card on the map, and only your answer changes something.<br>"
			+ "A backup is made first if this is the first time your data meets the new cache. About 10 seconds.</html>");
		about.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
		about.setPreferredSize(new Dimension(820, 70));
		JPanel pick = pickRow();
		JPanel north = new JPanel(new BorderLayout());
		north.add(about, BorderLayout.NORTH);
		north.add(pick, BorderLayout.SOUTH);
		return north;
	}

	/** New cache, Other folder, Download, Compare, Preview, Move. */
	private JPanel pickRow()
	{
		JPanel pick = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		pick.add(new JLabel("New cache:"));
		pick.add(caches);
		JButton other = new JButton("Other folder...");
		other.setToolTipText("Choose a folder with a game cache in it");
		other.addActionListener(e -> chooseFolder());
		pick.add(other);
		JButton download = new JButton("Download a newer cache...");
		download.setToolTipText("Download a cache from the OpenRS2 Archive; it is not opened, only compared");
		download.addActionListener(e -> new DownloadDialog(this, new File(workspace, "cache"), dir ->
		{
			refreshCaches();
			select(dir);
		}).setVisible(true));
		pick.add(download);
		pick.add(start);
		start.setToolTipText("Compare the open cache with the one chosen");
		start.addActionListener(e -> begin());
		pick.add(preview);
		preview.setToolTipText("Make the clue steps, banks and the plugin's data from the chosen cache into cache/cache-move"
			+ " and show what would change (about 2 minutes; nothing changes)");
		preview.addActionListener(e -> beginPreview());
		pick.add(move);
		move.setToolTipText("After the preview: put the new cache's clue steps and banks into data/ and open the new cache"
			+ " (a backup is made first)");
		move.addActionListener(e -> moveTo());
		caches.addActionListener(e -> updateMove());
		return pick;
	}

	/** The Report, Plugin data and Log tabs. */
	private JTabbedPane tabs()
	{
		report.setEditable(false);
		log.setEditable(false);
		log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		tabs.addTab("Report", new JScrollPane(report));
		pluginReport.setEditable(false);
		tabs.addTab("Plugin data", new JScrollPane(pluginReport));
		tabs.addTab("Log", new JScrollPane(log));
		tabs.setPreferredSize(new Dimension(900, 520));
		return tabs;
	}

	/** The status line, then the map tick box, "Go to the first card" and Close. */
	private JPanel bottom(Frame owner, Runnable goToFirstCard)
	{
		status.setFont(status.getFont().deriveFont(Font.BOLD));
		busy.setIndeterminate(true);
		busy.setVisible(false);
		onMap.setToolTipText("Outline the regions that changed and fill their changed tiles");
		onMap.addActionListener(e ->
		{
			layer.setVisible(onMap.isSelected());
			owner.repaint();
		});
		onMap.setSelected(layer.isVisible());
		firstCard.setToolTipText("Move the map to the first card waiting for an answer");
		firstCard.addActionListener(e -> goToFirstCard.run());
		JButton close = new JButton("Close");
		close.addActionListener(e -> closeWindow());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.add(busy);
		buttons.add(onMap);
		buttons.add(firstCard);
		buttons.add(close);
		JPanel south = new JPanel(new BorderLayout(8, 0));
		south.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
		south.add(status, BorderLayout.CENTER);
		south.add(buttons, BorderLayout.EAST);
		return south;
	}

	/** The cache folders in cache/ other than the open one, newest first. */
	private void refreshCaches()
	{
		Object was = caches.getSelectedItem();
		caches.removeAllItems();
		List<Choice> found = new ArrayList<>();
		File[] all = new File(workspace, "cache").listFiles(File::isDirectory);
		for (File d : all == null ? new File[0] : all)
		{
			CacheInfo info = new File(d, CacheInfo.FILE_NAME).isFile() ? CacheInfo.find(d) : null;
			if (info != null && info.getOpenrs2Id() != oldId)
			{
				found.add(new Choice(d, info));
			}
		}
		found.sort((a, b) -> Integer.compare(b.info.getOpenrs2Id(), a.info.getOpenrs2Id()));
		for (Choice c : found)
		{
			caches.addItem(c);
		}
		if (was != null)
		{
			caches.setSelectedItem(was);
		}
		start.setEnabled(caches.getItemCount() > 0);
		if (caches.getItemCount() == 0)
		{
			status.setText("No other cache yet: download a newer one.");
		}
	}

	private void select(File dir)
	{
		CacheInfo info = CacheInfo.find(dir);
		for (int i = 0; info != null && i < caches.getItemCount(); i++)
		{
			if (caches.getItemAt(i).info.getOpenrs2Id() == info.getOpenrs2Id())
			{
				caches.setSelectedIndex(i);
			}
		}
	}

	private void chooseFolder()
	{
		JFileChooser fc = new JFileChooser(new File(workspace, "cache"));
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setDialogTitle("Choose a folder with the new game cache");
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		File d = fc.getSelectedFile();
		CacheInfo info = CacheInfo.find(d);
		if (info == null || info.getOpenrs2Id() < 0)
		{
			JOptionPane.showMessageDialog(this, "That folder has no cache-info.properties (the cache's number), so it can't be"
				+ " compared. Download caches with the Download button.");
			return;
		}
		Choice c = new Choice(d, info);
		caches.addItem(c);
		caches.setSelectedItem(c);
		start.setEnabled(true);
	}

	/** Shows the newest comparison already made from the open cache, if any. */
	private void showLast()
	{
		CompareFile f = CompareFile.newest(workspace, oldId);
		if (f != null)
		{
			showReport(f);
			status.setText("Last comparison: #" + f.from + " -> #" + f.to + " (" + f.made.substring(0, Math.min(16, f.made.length()))
				.replace('T', ' ') + "). " + f.looks().size() + " things need a look.");
		}
	}

	private void showReport(CompareFile f)
	{
		try
		{
			String md = new String(Files.readAllBytes(new File(workspace, f.report).toPath()), StandardCharsets.UTF_8);
			report.setText(html(md));
			report.setCaretPosition(0);
		}
		catch (IOException e)
		{
			report.setText("<html>The report " + escape(f.report) + " could not be read: " + escape(e.getMessage()) + "</html>");
		}
		tabs.setSelectedIndex(0);
	}

	private void begin()
	{
		Choice c = (Choice) caches.getSelectedItem();
		if (c == null)
		{
			return;
		}
		String java = javaCommand();
		List<String> cmd = List.of(java, "-Xmx4g", "-Dorg.slf4j.simpleLogger.defaultLogLevel=error", "-Djava.awt.headless=true",
			"-cp", System.getProperty("java.class.path"), "com.bestpath.tool.update.CacheCompareCli",
			oldCache.getAbsolutePath(), c.folder.getAbsolutePath());
		log.setText("");
		tabs.setSelectedIndex(2);
		status.setForeground(Color.BLACK);
		status.setText("Comparing #" + oldId + " with #" + c.info.getOpenrs2Id() + "...");
		start.setEnabled(false);
		busy.setVisible(true);
		int newId = c.info.getOpenrs2Id();
		Thread t = new Thread(() -> run(cmd, newId), "cache-compare");
		t.setDaemon(true);
		t.start();
	}

	/** This Java's program file, to run the comparison and the preview as programs of their own. */
	private static String javaCommand()
	{
		return new File(new File(System.getProperty("java.home"), "bin"),
			System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java").getPath();
	}

	/** Background thread: runs the comparison, then shows the report and puts the cards on the map. */
	private void run(List<String> cmd, int newId)
	{
		int code = runLogged(cmd, "Could not run the comparison: ");
		SwingUtilities.invokeLater(() -> showComparison(code, newId));
	}

	/** Swing thread: the comparison's report and cards, or why it stopped. */
	private void showComparison(int code, int newId)
	{
		busy.setVisible(false);
		start.setEnabled(true);
		if (code != 0)
		{
			status.setForeground(Color.RED);
			status.setText("The comparison stopped (code " + code + "): see the Log tab. Nothing changed.");
			return;
		}
		try
		{
			CompareFile f = CompareFile.read(CompareFile.file(workspace, oldId, newId));
			showReport(f);
			onDone.run();
			int n = f.looks().size();
			status.setForeground(n == 0 ? new Color(0, 120, 0) : new Color(180, 90, 0));
			status.setText("#" + oldId + " -> #" + newId + ": " + (n == 0 ? "nothing needs a look."
				: n + " things need a look: a card for each on the map (answered ones are not asked again)."));
		}
		catch (IOException | RuntimeException ex)
		{
			status.setForeground(Color.RED);
			status.setText("The comparison ran, but its result could not be read: " + ex.getMessage());
		}
	}

	/**
	 * Background thread: runs one program, its lines going to the Log tab; its exit code (-1 when it could not run,
	 * then the reason goes to the log after {@code failure}).
	 */
	private int runLogged(List<String> cmd, String failure)
	{
		int exit;
		try
		{
			Process p = new ProcessBuilder(cmd).directory(workspace).redirectErrorStream(true).start();
			process = p;
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = r.readLine()) != null)
				{
					String l = line;
					SwingUtilities.invokeLater(() -> log.append(l + "\n"));
				}
			}
			exit = p.waitFor();
		}
		catch (IOException | InterruptedException ex)
		{
			String m = ex.getMessage();
			SwingUtilities.invokeLater(() -> log.append(failure + m + "\n"));
			exit = -1;
		}
		process = null;
		return exit;
	}

	/** The chosen cache's preview folder, or null. */
	private File work()
	{
		Choice c = (Choice) caches.getSelectedItem();
		return c == null ? null : com.bestpath.tool.update.CacheMove.work(workspace, c.info.getOpenrs2Id());
	}

	/** "Move" only when the chosen cache's preview is complete (its clue steps, banks and plugin data are there). */
	private void updateMove()
	{
		File w = work();
		boolean ready = w != null && new File(w, "clue-steps.tsv").isFile() && new File(w, "banks.tsv").isFile()
			&& new File(w, "plugin/bestpath-data.txt").isFile();
		move.setEnabled(ready && process == null);
		preview.setEnabled(w != null && process == null);
		Choice c = (Choice) caches.getSelectedItem();
		move.setText(c == null ? "Move to this cache..." : "Move to #" + c.info.getOpenrs2Id() + "...");
	}

	/** Runs the preview's three programs one after the other, then shows what would change. */
	private void beginPreview()
	{
		Choice c = (Choice) caches.getSelectedItem();
		File w = work();
		if (c == null || w == null)
		{
			return;
		}
		try
		{
			com.bestpath.tool.update.CacheMove.clear(w);
		}
		catch (IOException ex)
		{
			status.setForeground(Color.RED);
			status.setText("Could not start: " + ex.getMessage());
			return;
		}
		String java = javaCommand();
		List<List<String>> cmds = com.bestpath.tool.update.CacheMove.commands(java, System.getProperty("java.class.path"),
			c.folder, w, 100);
		log.setText("");
		tabs.setSelectedIndex(2);
		status.setForeground(Color.BLACK);
		status.setText("Making the clue steps, banks and plugin data from #" + c.info.getOpenrs2Id() + "...");
		start.setEnabled(false);
		preview.setEnabled(false);
		move.setEnabled(false);
		busy.setVisible(true);
		int newId = c.info.getOpenrs2Id();
		Thread t = new Thread(() -> runPreview(cmds, newId, w), "cache-move-preview");
		t.setDaemon(true);
		t.start();
	}

	/** Background thread: the preview's programs one after the other, then its report. */
	private void runPreview(List<List<String>> cmds, int newId, File w)
	{
		String[] names = {"clue steps", "banks", "plugin data"};
		int failed = -1;
		int code = 0;
		for (int k = 0; k < cmds.size() && failed < 0; k++)
		{
			String step = names[k];
			SwingUtilities.invokeLater(() -> log.append("---- " + step + " ----\n"));
			code = runLogged(cmds.get(k), "Could not run it: ");
			if (code != 0)
			{
				failed = k;
			}
		}
		int stopped = failed;
		int exit = code;
		File file = com.bestpath.tool.update.CacheMove.reportFile(workspace, oldId, newId);
		String md = stopped < 0 ? writePreviewReport(file, newId, w) : null;
		SwingUtilities.invokeLater(() -> showPreview(names, stopped, exit, md, newId, file));
	}

	/** Makes the preview's report and writes it to {@code file}; its text (or why it could not be made). */
	private String writePreviewReport(File file, int newId, File w)
	{
		String text;
		try
		{
			text = com.bestpath.tool.update.CacheMove.report(workspace, oldId, newId, w);
			Files.createDirectories(file.getParentFile().toPath());
			Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException ex)
		{
			text = "# The preview's report could not be made\n\n" + ex.getMessage();
		}
		return text;
	}

	/** Swing thread: the preview's report in the Plugin data tab, or the step ({@code names[stopped]}) it stopped at. */
	private void showPreview(String[] names, int stopped, int exit, String md, int newId, File file)
	{
		busy.setVisible(false);
		start.setEnabled(true);
		if (stopped >= 0)
		{
			status.setForeground(Color.RED);
			status.setText("The preview stopped at the " + names[stopped] + " (code " + exit + (stopped == 2 && exit == 2
				? ": the check found routes that differ between the tool and the plugin" : "") + "): see the Log tab.");
			updateMove();
			return;
		}
		pluginReport.setText(html(md));
		pluginReport.setCaretPosition(0);
		tabs.setSelectedIndex(1);
		status.setForeground(new Color(0, 120, 0));
		status.setText("Preview ready, nothing changed. Next: \"Move to #" + newId + "...\" when you are happy with it.");
		status.setToolTipText("The report: docs/reports/" + file.getName());
		updateMove();
	}

	/** Moves the data to the chosen cache, after asking. */
	private void moveTo()
	{
		Choice c = (Choice) caches.getSelectedItem();
		File w = work();
		if (c == null || w == null)
		{
			return;
		}
		int id = c.info.getOpenrs2Id();
		int a = JOptionPane.showConfirmDialog(this, "<html>Move the data to cache <b>#" + id + "</b>?<br><br>"
				+ "1. A backup is made first (if this cache has none yet).<br>"
				+ "2. The clue steps and banks made from #" + id + " go into data/ (as the preview showed).<br>"
				+ "3. The Data Tool opens #" + id + "; your changes carry over, and new ones are stamped #" + id + ".<br><br>"
				+ "Then press <b>Update plugin data...</b>: it makes the plugin's data from #" + id + " and checks it.</html>",
			"Move to a new cache", JOptionPane.YES_NO_OPTION);
		if (a != JOptionPane.YES_OPTION)
		{
			return;
		}
		try
		{
			com.bestpath.tool.update.CacheMove.move(workspace, id, w, l -> log.append(l + "\n"));
		}
		catch (IOException ex)
		{
			status.setForeground(Color.RED);
			status.setText("Not moved: " + ex.getMessage());
			return;
		}
		dispose();
		openCache.accept(c.folder);
	}

	private void closeWindow()
	{
		Process p = process;
		if (p != null)
		{
			p.destroy();
		}
		dispose();
	}

	/** The report's Markdown as simple HTML: headings, nested lists, bold and code. */
	static String html(String md)
	{
		StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11pt'>");
		int depth = 0;
		for (String raw : md.split("\n", -1))
		{
			String line = raw.replace("\r", "");
			int indent = 0;
			while (line.startsWith("  ", indent * 2))
			{
				indent++;
			}
			String rest = line.substring(indent * 2);
			if (rest.startsWith("- "))
			{
				int want = indent + 1;
				for (; depth < want; depth++)
				{
					sb.append("<ul>");
				}
				for (; depth > want; depth--)
				{
					sb.append("</ul>");
				}
				sb.append("<li>").append(inline(rest.substring(2))).append("</li>");
				continue;
			}
			for (; depth > 0; depth--)
			{
				sb.append("</ul>");
			}
			if (rest.startsWith("### "))
			{
				sb.append("<h4>").append(inline(rest.substring(4))).append("</h4>");
			}
			else if (rest.startsWith("## "))
			{
				sb.append("<h3>").append(inline(rest.substring(3))).append("</h3>");
			}
			else if (rest.startsWith("# "))
			{
				sb.append("<h2>").append(inline(rest.substring(2))).append("</h2>");
			}
			else if (!rest.trim().isEmpty())
			{
				sb.append("<p>").append(inline(rest)).append("</p>");
			}
		}
		for (; depth > 0; depth--)
		{
			sb.append("</ul>");
		}
		return sb.append("</body></html>").toString();
	}

	private static String inline(String s)
	{
		String e = escape(s);
		e = e.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
		e = e.replaceAll("`(.+?)`", "<code>$1</code>");
		return e;
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** UiCheck: starts the comparison with the cache chosen, as the button does. */
	void devStart()
	{
		begin();
	}

	/** UiCheck: true while the comparison runs. */
	boolean devRunning()
	{
		return process != null || busy.isVisible();
	}

	/** UiCheck: the status line. */
	String devStatus()
	{
		return status.getText();
	}

	/** UiCheck: starts the preview as the button does. */
	void devPreview()
	{
		beginPreview();
	}

	/** UiCheck: whether "Move" can be pressed. */
	boolean devCanMove()
	{
		return move.isEnabled();
	}

	/** UiCheck: chooses the new cache by its OpenRS2 number. */
	void devChoose(int id)
	{
		for (int i = 0; i < caches.getItemCount(); i++)
		{
			if (caches.getItemAt(i).info.getOpenrs2Id() == id)
			{
				caches.setSelectedIndex(i);
			}
		}
	}
}

package com.bestpath.tool.ui;

import com.bestpath.tool.cache.CacheCatalog;
import com.bestpath.tool.cache.CacheDownloader;
import com.bestpath.tool.cache.CacheInfo;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

/**
 * Lists caches on the OpenRS2 Archive and downloads the chosen one. The
 * network is only used after the user opens this dialog.
 */
final class DownloadDialog extends JDialog
{
	private final File cacheRoot;
	private final Consumer<File> onDownloaded;
	private final CacheTableModel model = new CacheTableModel();
	private final JTable table = new JTable(model);
	private final JLabel status = new JLabel("Fetching the list of caches from archive.openrs2.org ...");
	private final JProgressBar progress = new JProgressBar();
	private final JButton download = new JButton("Download and open");
	private final JButton cancel = new JButton("Close");
	private CacheDownloader downloader;

	DownloadDialog(Window owner, File cacheRoot, Consumer<File> onDownloaded)
	{
		super(owner, "Download a game cache", ModalityType.APPLICATION_MODAL);
		this.cacheRoot = cacheRoot;
		this.onDownloaded = onDownloaded;

		JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		root.add(new JLabel("<html>Old School caches from the OpenRS2 Archive, newest first. "
			+ "Pick one and press <b>Download and open</b>.<br>Caches are saved in: " + cacheRoot + "</html>"), BorderLayout.NORTH);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener(e -> download.setEnabled(table.getSelectedRow() >= 0 && downloader == null));
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(620, 300));
		root.add(scroll, BorderLayout.CENTER);

		JPanel bottom = new JPanel(new BorderLayout(6, 6));
		bottom.add(status, BorderLayout.NORTH);
		bottom.add(progress, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		download.setEnabled(false);
		buttons.add(download);
		buttons.add(cancel);
		bottom.add(buttons, BorderLayout.SOUTH);
		root.add(bottom, BorderLayout.SOUTH);
		setContentPane(root);
		pack();
		setLocationRelativeTo(owner);

		download.addActionListener(e -> startDownload());
		cancel.addActionListener(e ->
		{
			if (downloader != null)
			{
				downloader.cancel();
			}
			dispose();
		});
		loadList();
	}

	private void loadList()
	{
		progress.setIndeterminate(true);
		new SwingWorker<List<CacheInfo>, Void>()
		{
			@Override
			protected List<CacheInfo> doInBackground() throws Exception
			{
				return CacheCatalog.fetch();
			}

			@Override
			protected void done()
			{
				progress.setIndeterminate(false);
				try
				{
					List<CacheInfo> list = get();
					model.set(list);
					status.setText(list.size() + " caches available.");
					if (!list.isEmpty())
					{
						table.setRowSelectionInterval(0, 0);
					}
				}
				catch (Exception ex)
				{
					status.setText("Could not fetch the list: " + rootMessage(ex));
				}
			}
		}.execute();
	}

	private void startDownload()
	{
		CacheInfo info = model.get(table.getSelectedRow());
		downloader = new CacheDownloader();
		download.setEnabled(false);
		cancel.setText("Cancel");
		CacheDownloader d = downloader;
		new SwingWorker<File, Void>()
		{
			@Override
			protected File doInBackground() throws Exception
			{
				return d.download(info, cacheRoot, (stage, done, total) -> SwingUtilities.invokeLater(() ->
				{
					if (total > 0)
					{
						progress.setIndeterminate(false);
						progress.setValue((int) (done * 100 / total));
						status.setText(String.format(Locale.ROOT, "%s %s: %.1f / %.1f MB", stage, info.label(), done / 1e6, total / 1e6));
					}
					else
					{
						progress.setIndeterminate(true);
						status.setText(stage + " " + info.label() + " ...");
					}
				}));
			}

			@Override
			protected void done()
			{
				progress.setIndeterminate(false);
				try
				{
					File folder = get();
					dispose();
					onDownloaded.accept(folder);
				}
				catch (Exception ex)
				{
					downloader = null;
					cancel.setText("Close");
					download.setEnabled(true);
					status.setText("Download failed: " + rootMessage(ex));
				}
			}
		}.execute();
	}

	static String rootMessage(Throwable t)
	{
		while (t.getCause() != null)
		{
			t = t.getCause();
		}
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}

	private static final class CacheTableModel extends AbstractTableModel
	{
		private static final String[] COLS = {"OpenRS2 id", "Game build", "Captured (UTC)", "Size"};
		private List<CacheInfo> rows = List.of();

		void set(List<CacheInfo> rows)
		{
			this.rows = rows;
			fireTableDataChanged();
		}

		CacheInfo get(int row)
		{
			return rows.get(row);
		}

		@Override
		public int getRowCount()
		{
			return rows.size();
		}

		@Override
		public int getColumnCount()
		{
			return COLS.length;
		}

		@Override
		public String getColumnName(int c)
		{
			return COLS[c];
		}

		@Override
		public Object getValueAt(int r, int c)
		{
			CacheInfo i = rows.get(r);
			switch (c)
			{
				case 0:
					return i.getOpenrs2Id();
				case 1:
					return i.getBuild() >= 0 ? Integer.toString(i.getBuild()) : "?";
				case 2:
					return i.getTimestamp().replace('T', ' ').replaceAll("\\..*$", "").replace("Z", "");
				default:
					return String.format(Locale.ROOT, "%.0f MB", i.getSizeBytes() / 1e6);
			}
		}
	}
}

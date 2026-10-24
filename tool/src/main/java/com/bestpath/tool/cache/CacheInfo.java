package com.bestpath.tool.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Identity of a cache: the OpenRS2 archive id, game build and capture time.
 * Stored next to downloaded caches as {@value #FILE_NAME}. Immutable.
 */
public final class CacheInfo
{
	public static final String FILE_NAME = "cache-info.properties";

	private final int openrs2Id;
	private final int build;
	private final String timestamp;
	private final long sizeBytes;
	private final String scope;

	public CacheInfo(int openrs2Id, int build, String timestamp, long sizeBytes, String scope)
	{
		this.openrs2Id = openrs2Id;
		this.build = build;
		this.timestamp = timestamp;
		this.sizeBytes = sizeBytes;
		this.scope = scope;
	}

	public int getOpenrs2Id()
	{
		return openrs2Id;
	}

	/** Game build (major), or -1 if unknown. */
	public int getBuild()
	{
		return build;
	}

	/** ISO-8601 capture time, or "" if unknown. */
	public String getTimestamp()
	{
		return timestamp;
	}

	public long getSizeBytes()
	{
		return sizeBytes;
	}

	public String getScope()
	{
		return scope;
	}

	/** Short human label, e.g. "#2735 · build 241 · 2026-10-07". */
	public String label()
	{
		String date = timestamp.length() >= 10 ? timestamp.substring(0, 10) : timestamp;
		return "#" + openrs2Id + (build >= 0 ? " · build " + build : "") + (date.isEmpty() ? "" : " · " + date);
	}

	public void save(File dir) throws IOException
	{
		Properties p = new Properties();
		p.setProperty("openrs2.id", Integer.toString(openrs2Id));
		p.setProperty("build", Integer.toString(build));
		p.setProperty("timestamp", timestamp);
		p.setProperty("size", Long.toString(sizeBytes));
		p.setProperty("scope", scope);
		try (OutputStream out = new FileOutputStream(new File(dir, FILE_NAME)))
		{
			p.store(out, "BestPath Data Tool cache identity");
		}
	}

	/** Reads cache-info.properties from {@code dir} or any of its parents; null if none. */
	public static CacheInfo find(File dir)
	{
		for (File d = dir; d != null; d = d.getParentFile())
		{
			File f = new File(d, FILE_NAME);
			if (f.isFile())
			{
				Properties p = new Properties();
				try (InputStream in = new FileInputStream(f))
				{
					p.load(in);
					return new CacheInfo(
						Integer.parseInt(p.getProperty("openrs2.id", "-1")),
						Integer.parseInt(p.getProperty("build", "-1")),
						p.getProperty("timestamp", ""),
						Long.parseLong(p.getProperty("size", "0")),
						p.getProperty("scope", "runescape"));
				}
				catch (IOException | NumberFormatException e)
				{
					return null;
				}
			}
		}
		return null;
	}
}

package com.bestpath.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Per-user tool settings (last cache, map position), stored in
 * ~/.bestpath-tool/settings.properties. Project data never goes here; it lives
 * in the workspace (the repository).
 */
public final class ToolSettings
{
	private final File file;
	private final Properties props = new Properties();

	public ToolSettings(File file)
	{
		this.file = file;
		if (file.isFile())
		{
			try (InputStream in = new FileInputStream(file))
			{
				props.load(in);
			}
			catch (IOException ignored)
			{
				// start with defaults
			}
		}
	}

	public static ToolSettings forUser()
	{
		return new ToolSettings(new File(System.getProperty("user.home"), ".bestpath-tool/settings.properties"));
	}

	public String get(String key, String def)
	{
		return props.getProperty(key, def);
	}

	public double getDouble(String key, double def)
	{
		try
		{
			return Double.parseDouble(props.getProperty(key, Double.toString(def)));
		}
		catch (NumberFormatException e)
		{
			return def;
		}
	}

	public void set(String key, String value)
	{
		if (value == null)
		{
			props.remove(key);
		}
		else
		{
			props.setProperty(key, value);
		}
	}

	public void save()
	{
		file.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(file))
		{
			props.store(out, "BestPath Data Tool user settings");
		}
		catch (IOException ignored)
		{
			// settings are a convenience; failing to save is not fatal
		}
	}

	/** A file only the workspace has: the Data Tool's own build. */
	private static final String WORKSPACE_MARKER = "tool/build.gradle";

	/**
	 * The workspace is the repository root: the nearest folder at or above the
	 * working directory that contains tool/build.gradle. Falls back to
	 * ~/BestPathData.
	 */
	public static File findWorkspace()
	{
		for (File d = new File(System.getProperty("user.dir")).getAbsoluteFile(); d != null; d = d.getParentFile())
		{
			if (new File(d, WORKSPACE_MARKER).isFile())
			{
				return d;
			}
		}
		return new File(System.getProperty("user.home"), "BestPathData");
	}
}

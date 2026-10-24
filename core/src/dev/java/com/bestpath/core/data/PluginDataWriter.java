package com.bestpath.core.data;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Writes the plugin data's manifest ({@value PluginData#MANIFEST}) that {@link PluginData} checks when it loads, and
 * the first comment line of every exported file (used when the data is made).
 */
public final class PluginDataWriter
{
	private PluginDataWriter()
	{
	}

	/** The first comment line of an exported data file: "Best Path plugin data, format 1: {@code what}." */
	public static String header(String what)
	{
		return "Best Path plugin data, format " + PluginData.FORMAT + ": " + what + ".";
	}

	/**
	 * The manifest for a set of data files: format, cache, when it was made, the fingerprint, each file's size and hash,
	 * then counts for people to read.
	 *
	 * @param files every file of {@link PluginData#FILES}
	 */
	public static byte[] write(Map<String, byte[]> files, int cacheId, int cacheBuild, String made, Map<String, String> counts)
	{
		StringBuilder mf = new StringBuilder();
		mf.append("# Best Path plugin map data, format ").append(PluginData.FORMAT).append(", made by the Best Path Data Tool.\n");
		mf.append("# Do not edit these files by hand: the plugin checks every file's size and hash and the fingerprint.\n");
		mf.append("format\t").append(PluginData.FORMAT).append('\n');
		mf.append("cache\t").append(cacheId).append('\t').append(cacheBuild).append('\n');
		mf.append("made\t").append(made).append('\n');
		mf.append("fingerprint\t").append(PluginData.fingerprint(files)).append('\n');
		for (String name : PluginData.FILES)
		{
			byte[] b = files.get(name);
			mf.append("file\t").append(name).append('\t').append(b.length).append('\t').append(PluginData.hash(b)).append('\n');
		}
		for (String name : PluginData.OPTIONAL_FILES)
		{
			byte[] b = files.get(name);
			if (b != null)
			{
				mf.append("file\t").append(name).append('\t').append(b.length).append('\t').append(PluginData.hash(b)).append('\n');
			}
		}
		counts.forEach((k, v) -> mf.append("count\t").append(TransportLine.clean(k)).append('\t').append(TransportLine.clean(v)).append('\n'));
		return mf.toString().getBytes(StandardCharsets.UTF_8);
	}
}

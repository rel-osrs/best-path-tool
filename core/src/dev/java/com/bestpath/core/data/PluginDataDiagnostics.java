package com.bestpath.core.data;

/**
 * What development code reads from {@link PluginData} that the plugin itself never needs.
 */
public final class PluginDataDiagnostics
{
	private PluginDataDiagnostics()
	{
	}

	/** Bytes of all files as stored (manifest included). */
	public static long bytes(PluginData d)
	{
		return d.bytes;
	}
}

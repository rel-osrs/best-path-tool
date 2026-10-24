package com.bestpath.core.data;

/**
 * What development code reads from {@link PlayerTransports} that the plugin itself never needs.
 */
public final class PlayerTransportsDiagnostics
{
	private PlayerTransportsDiagnostics()
	{
	}

	/** How many different requirement lines were read so far (memory checks). */
	public static int parsedLineCount(PlayerTransports t)
	{
		synchronized (t)
		{
			return t.parsed.size();
		}
	}
}

package com.bestpath.core.path;

/**
 * What development code reads from a {@link Route} that the plugin itself never needs.
 */
public final class RouteDiagnostics
{
	private RouteDiagnostics()
	{
	}

	/** Number of unconfirmed transports used. */
	public static int unconfirmedCount(Route r)
	{
		int n = 0;
		for (Route.Step s : r.getSteps())
		{
			if (s.transport != null && !s.transport.isConfirmed())
			{
				n++;
			}
		}
		return n;
	}
}

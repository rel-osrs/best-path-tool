package com.bestpath.core.data;

/**
 * Makes changed copies of {@link TransportLine}s, when the data is made.
 */
public final class TransportLineBuilder
{
	private TransportLineBuilder()
	{
	}

	/** The line with other menu texts (start to end; end to start, kept for two-way transports only). */
	public static TransportLine withMenu(TransportLine line, MenuText there, MenuText back)
	{
		return line.toBuilder().menu(there, back).build().withBoats(line.getLargestBoat());
	}
}

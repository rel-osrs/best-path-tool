package com.bestpath.core.path;

/**
 * What development code reads from {@link BankFirst} values that the plugin itself never needs.
 */
public final class BankFirstDiagnostics
{
	private BankFirstDiagnostics()
	{
	}

	/** How many tiles the banks have. */
	public static int tileCount(BankFirst.Banks banks)
	{
		return banks.tiles().length;
	}
}

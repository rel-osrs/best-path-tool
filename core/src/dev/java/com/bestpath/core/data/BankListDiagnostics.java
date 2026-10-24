package com.bestpath.core.data;

/**
 * What development code reads from a {@link BankList} that the plugin itself never needs.
 */
public final class BankListDiagnostics
{
	private BankListDiagnostics()
	{
	}

	/** The bank a booth, chest or banker belongs to ("Varrock west bank"); booths of one bank share it. */
	public static String name(BankList.Bank bank)
	{
		return bank.name;
	}

	/** "booth", "chest" or "banker". */
	public static String kind(BankList.Bank bank)
	{
		return bank.kind;
	}
}

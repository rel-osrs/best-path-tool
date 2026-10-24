package com.bestpath.core.collision;

/**
 * Makes {@link FreeLand.Builder}s that start from an existing {@link FreeLand}, so development code can change it.
 */
public final class FreeLandBuilder
{
	private FreeLandBuilder()
	{
	}

	/** A builder holding every free tile of {@code land}. */
	public static FreeLand.Builder of(FreeLand land)
	{
		FreeLand.Builder b = new FreeLand.Builder();
		for (int i = 0; i < land.rows.length; i++)
		{
			for (int k = 0; k < land.runs[i].length; k += 2)
			{
				b.set(land.runs[i][k], land.rows[i], land.runs[i][k + 1], land.rows[i], true);
			}
		}
		return b;
	}
}

package com.bestpath.tool.req;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** The real {@code data/manual/networks.tsv}: every network's requirement line can be read. */
class NetworksFileTest
{
	@Test
	void everyRequirementLineParses() throws Exception
	{
		assertFalse(Networks.all().isEmpty(), "data/manual/networks.tsv has no networks");
		for (Networks.Network n : Networks.all())
		{
			try
			{
				ItemGroupFile.parse(n.requirement);
			}
			catch (com.bestpath.core.req.RequirementText.ParseException e)
			{
				throw new AssertionError(n.name + ": " + e.getMessage(), e);
			}
		}
	}
}

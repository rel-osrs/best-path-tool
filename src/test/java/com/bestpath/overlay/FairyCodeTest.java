package com.bestpath.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

/** Clicking a fairy ring scrolls to and indicates the correct fairy ring code in the list. */
public class FairyCodeTest
{
	@Test
	public void eachCodeFindsItsEntryInTheTravelLog()
	{
		assertEquals(InterfaceID.FairyringsLog.AIP, StepTargets.fairyEntryId("AIP"));
		assertEquals(InterfaceID.FairyringsLog.AIQ, StepTargets.fairyEntryId("AIQ"));
		assertEquals(InterfaceID.FairyringsLog.AJS, StepTargets.fairyEntryId("AJS"));
		assertEquals(InterfaceID.FairyringsLog.ALP, StepTargets.fairyEntryId("alp"));
		assertEquals(InterfaceID.FairyringsLog.BIP, StepTargets.fairyEntryId("BIP"));
		assertEquals(InterfaceID.FairyringsLog.BKR, StepTargets.fairyEntryId("BKR"));
		assertEquals(InterfaceID.FairyringsLog.CKS, StepTargets.fairyEntryId("CKS"));
		assertEquals(InterfaceID.FairyringsLog.DIP, StepTargets.fairyEntryId("DIP"));
		assertEquals(InterfaceID.FairyringsLog.DLQ, StepTargets.fairyEntryId("DLQ"));
		assertEquals(InterfaceID.FairyringsLog.DLS, StepTargets.fairyEntryId("DLS"));
		// never a favourite star
		assertEquals(InterfaceID.FairyringsLog.AIP_FAVE, StepTargets.fairyEntryId("AIP") + 1);
		// not codes: other choices, wrong letters
		assertEquals(-1, StepTargets.fairyEntryId("Burthorpe"));
		assertEquals(-1, StepTargets.fairyEntryId("AXP"));
		assertEquals(-1, StepTargets.fairyEntryId(null));
		assertNull(StepTargets.fairyCode("Zanaris"));
		assertEquals("CKR", StepTargets.fairyCode("ckr"));
	}
}

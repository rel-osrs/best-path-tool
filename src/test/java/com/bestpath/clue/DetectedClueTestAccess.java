package com.bestpath.clue;

import com.bestpath.core.data.ClueData;
import java.util.List;

/** Tests outside this package: a detected clue of one known step. */
public final class DetectedClueTestAccess
{
	private DetectedClueTestAccess()
	{
	}

	public static DetectedClue step(ClueData.Step s)
	{
		int item = s.getItems().length > 0 ? s.getItems()[0] : -1;
		return new DetectedClue(DetectedClue.Kind.STEP, s.getTier(), item, List.of(s), "");
	}
}

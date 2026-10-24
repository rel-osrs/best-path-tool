package com.bestpath.tool.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.tool.review.RegionReview.Category;
import com.bestpath.tool.review.RegionReview.Overall;
import com.bestpath.tool.review.RegionReview.Status;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionReviewTest
{
	@TempDir
	Path dir;

	@Test
	void savesAndLoadsStatusesAndNotes() throws Exception
	{
		File file = dir.resolve("manual/region-review.json").toFile();
		RegionReview review = new RegionReview(file);
		review.set(12850, Category.TRANSPORTS, Status.COMPLETE, "stairs ok", "me");
		review.set(12850, Category.ITEMS, null, "needs a key?", "me");

		RegionReview again = new RegionReview(file);
		again.load();
		assertEquals(Status.COMPLETE, again.status(12850, Category.TRANSPORTS));
		assertEquals("stairs ok", again.get(12850, Category.TRANSPORTS).note);
		assertEquals(Status.NOT_CHECKED, again.status(12850, Category.ITEMS));
		assertEquals("needs a key?", again.get(12850, Category.ITEMS).note);
		assertTrue(again.hasNotes(12850));
		assertEquals(Status.NOT_CHECKED, again.status(12851, Category.VARBITS));
	}

	@Test
	void personTickIsSavedBesideTheChecks() throws Exception
	{
		File file = dir.resolve("r.json").toFile();
		RegionReview review = new RegionReview(file);
		review.set(5, Category.TRANSPORTS, Status.COMPLETE, "ok", "Tester");
		review.setHumanReviewed(5, true, "Rel");
		review.setHumanReviewed(6, true, "Rel"); // a tick alone is kept too
		assertTrue(Files.readString(file.toPath()).contains("\"HUMAN_REVIEWED\""));

		RegionReview again = new RegionReview(file);
		again.load();
		assertTrue(again.isHumanReviewed(5));
		assertEquals("Rel", again.human(5).by);
		assertTrue(again.isHumanReviewed(6));
		assertEquals(Status.COMPLETE, again.status(5, Category.TRANSPORTS));
		assertEquals("Tester", again.get(5, Category.TRANSPORTS).author);
		assertEquals(Overall.IN_PROGRESS, again.overall(5)); // the tick is not one of the four checks

		again.setHumanReviewed(6, false, "Rel");
		RegionReview third = new RegionReview(file);
		third.load();
		assertFalse(third.isHumanReviewed(6));
	}

	@Test
	void oldFilesWithoutTicksStillLoad() throws Exception
	{
		File file = dir.resolve("old.json").toFile();
		Files.writeString(file.toPath(), "{\n  \"4137\": {\n    \"ITEMS\": {\n      \"status\": \"COMPLETE\",\n      \"note\": \"\",\n"
			+ "      \"updated\": \"t\",\n      \"author\": \"Rel\"\n    }\n  }\n}\n");
		RegionReview review = new RegionReview(file);
		review.load();
		assertEquals(Status.COMPLETE, review.status(4137, Category.ITEMS));
		assertFalse(review.isHumanReviewed(4137));
		long modified = file.lastModified();
		Thread.sleep(20);
		review.set(4137, Category.ITEMS, Status.COMPLETE, "", "Rel"); // same text: nothing written
		assertEquals(modified, file.lastModified());
	}

	@Test
	void overallFollowsTheFourChecks() throws Exception
	{
		RegionReview review = new RegionReview(dir.resolve("r.json").toFile());
		assertEquals(Overall.NOT_STARTED, review.overall(1));
		review.set(1, Category.TRANSPORTS, Status.COMPLETE, null, "me");
		assertEquals(Overall.IN_PROGRESS, review.overall(1));
		review.set(1, Category.VARBITS, Status.COMPLETE, null, "me");
		review.set(1, Category.ITEMS, Status.COMPLETE, null, "me");
		review.set(1, Category.SUBREGIONS, Status.NEEDS_WORK, null, "me");
		assertEquals(Overall.NEEDS_WORK, review.overall(1));
		review.set(1, Category.SUBREGIONS, Status.COMPLETE, null, "me");
		assertEquals(Overall.COMPLETE, review.overall(1));
	}

	@Test
	void clearingEverythingLeavesNoEntryAndUnchangedStateIsNotWritten() throws Exception
	{
		File file = dir.resolve("r.json").toFile();
		RegionReview review = new RegionReview(file);
		review.set(7, Category.ITEMS, Status.NEEDS_WORK, "x", "me");
		review.set(7, Category.ITEMS, Status.NOT_CHECKED, "", "me");
		assertEquals("{}", Files.readString(file.toPath()).trim());
		assertFalse(review.hasNotes(7));

		long modified = file.lastModified();
		Thread.sleep(20);
		review.set(7, Category.ITEMS, Status.NOT_CHECKED, "", "me"); // no change: no write
		assertEquals(modified, file.lastModified());
	}
}

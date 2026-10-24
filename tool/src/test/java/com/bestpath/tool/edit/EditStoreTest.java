package com.bestpath.tool.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditStoreTest
{
	@TempDir
	Path dir;

	private static Edit tile(int x, int y, boolean on)
	{
		Edit e = new Edit();
		e.type = Edit.Type.TILE;
		e.x = x;
		e.y = y;
		e.on = on;
		return e;
	}

	private EditStore store()
	{
		EditStore s = new EditStore(dir.resolve("manual/changes.jsonl").toFile());
		s.setStamp(() -> "tester", () -> 7, () -> 241, ex ->
		{
			throw new AssertionError(ex);
		});
		return s;
	}

	@Test
	void aChangeIsSavedAtOnceAndStamped() throws Exception
	{
		EditStore s = store();
		s.add(tile(1, 2, true));
		File f = s.getFile();
		assertEquals(1, Files.readAllLines(f.toPath()).size());

		EditStore again = new EditStore(f);
		again.load();
		assertEquals(1, again.active().size());
		Edit e = again.active().get(0);
		assertEquals("tester", e.author);
		assertEquals(7, e.cacheId);
		assertFalse(e.time.isEmpty());
		assertTrue(again.getUnbuilt().isEmpty(), "loaded changes are the ones the data is built from");
	}

	@Test
	void undoTakesBackTheLastActionAndIsSaved() throws Exception
	{
		EditStore s = store();
		s.add(tile(1, 1, true));
		s.add(tile(2, 2, true));
		assertTrue(s.undo());
		assertEquals(1, s.active().size());
		assertEquals(1, s.active().get(0).x);

		EditStore again = new EditStore(s.getFile());
		again.load();
		assertEquals(1, again.active().size(), "the undo is a saved REVERT");
		assertTrue(s.undo());
		assertTrue(s.active().isEmpty());
		assertFalse(s.undo(), "nothing left to undo");
	}

	@Test
	void oneGestureIsOneUndoStep()
	{
		EditStore s = store();
		s.add(tile(0, 0, true));
		s.beginGesture();
		for (int i = 1; i <= 5; i++)
		{
			s.add(tile(i, 0, true)); // a brush stroke
		}
		s.endGesture();
		assertEquals(6, s.active().size());
		s.undo();
		assertEquals(1, s.active().size());
		assertEquals(0, s.active().get(0).x);
	}

	@Test
	void undoingACancelBringsTheChangeBack()
	{
		EditStore s = store();
		s.add(tile(3, 3, true));
		assertTrue(s.cancel(tile(3, 3, false)));
		assertTrue(s.active().isEmpty());
		s.undo(); // undoes the cancel: a REVERT of the REVERT
		assertEquals(1, s.active().size());
		assertEquals(3, s.active().get(0).x);
	}

	@Test
	void unbuiltFollowsTheBuilds()
	{
		EditStore s = store();
		s.add(tile(1, 0, true));
		assertEquals(1, s.getUnbuilt().size());
		assertTrue(s.needsBuild());
		Set<String> snapshot = Set.copyOf(s.activeIds());
		s.add(tile(2, 0, true)); // made while the build runs
		s.markBuilt(snapshot);
		List<Edit> unbuilt = s.getUnbuilt();
		assertEquals(1, unbuilt.size());
		assertEquals(2, unbuilt.get(0).x);
		assertTrue(s.needsBuild());
		s.markBuilt(Set.copyOf(s.activeIds()));
		assertFalse(s.needsBuild());
		assertNull(s.findUnbuilt(tile(2, 0, true)));
	}

	@Test
	void revertingAChangeMarksItReverted()
	{
		EditStore s = store();
		s.add(tile(5, 5, true));
		Edit e = s.active().get(0);
		s.revert(e);
		assertTrue(s.isReverted(e));
		s.undo();
		assertFalse(s.isReverted(e));
	}
}

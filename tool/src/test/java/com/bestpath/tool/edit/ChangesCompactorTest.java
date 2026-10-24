package com.bestpath.tool.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangesCompactorTest
{
	@TempDir
	Path dir;

	private EditStore store()
	{
		EditStore s = new EditStore(dir.resolve("data/manual/changes.jsonl").toFile());
		s.setStamp(() -> "tester", () -> 7, () -> 241, ex ->
		{
			throw new AssertionError(ex);
		});
		return s;
	}

	private static Edit tile(int x, int y, boolean on)
	{
		Edit e = new Edit();
		e.type = Edit.Type.TILE;
		e.x = x;
		e.y = y;
		e.on = on;
		return e;
	}

	private static Edit transport(String id, int ticks)
	{
		TransportRecord r = new TransportRecord();
		r.id = id;
		r.ticks = ticks;
		Edit e = new Edit();
		e.type = Edit.Type.TRANSPORT;
		e.transport = r;
		return e;
	}

	private static Edit delete(String id)
	{
		Edit e = new Edit();
		e.type = Edit.Type.TRANSPORT_DELETE;
		e.target = id;
		return e;
	}

	private static Edit placeKind(String icon, boolean remove, String name)
	{
		Edit e = new Edit();
		e.type = Edit.Type.PLACE_KIND;
		e.target = icon;
		e.on = remove;
		e.name = name;
		return e;
	}

	private ChangesCompactor.Plan plan(EditStore s) throws Exception
	{
		return ChangesCompactor.plan(Files.readAllLines(s.getFile().toPath()));
	}

	private static List<Edit> parse(List<String> lines)
	{
		List<Edit> out = new ArrayList<>();
		for (String l : lines)
		{
			out.add(new com.google.gson.Gson().fromJson(l, Edit.class));
		}
		return out;
	}

	private static List<String> ids(List<Edit> edits)
	{
		List<String> out = new ArrayList<>();
		edits.forEach(e -> out.add(e.id));
		return out;
	}

	@Test
	void undoneAndReplacedChangesGoTheRestStaysWordForWord() throws Exception
	{
		EditStore s = store();
		s.add(tile(1, 1, true));
		s.add(tile(2, 2, true));
		s.undo(); // tile 2 undone
		s.add(tile(1, 1, false)); // replaces the first
		s.add(transport("t-a", 5));
		s.add(transport("t-a", 7));
		ChangesCompactor.Plan p = plan(s);
		assertEquals(6, p.linesBefore);
		assertEquals(2, p.kept.size());
		assertEquals(2, p.undone, "the undo line and the change it undid");
		assertEquals(2, p.replacedTotal());
		List<String> original = Files.readAllLines(s.getFile().toPath());
		assertTrue(original.containsAll(p.kept), "kept lines are the original text");
		ChangesCompactor.verify(s.active(), parse(p.kept));
	}

	@Test
	void aTransportKeepsItsPlaceInTheOrder() throws Exception
	{
		EditStore s = store();
		s.add(transport("t-a", 5));
		s.add(transport("t-b", 5));
		s.add(transport("t-a", 9)); // a stays before b in the export
		s.add(transport("t-c", 5));
		s.add(delete("t-c"));
		s.add(transport("t-c", 6)); // back after a delete: at the end
		s.add(transport("t-d", 5));
		s.add(transport("t-c", 8));
		ChangesCompactor.Plan p = plan(s);
		List<Edit> after = parse(p.kept);
		ChangesCompactor.verify(s.active(), after);
		List<String> order = new ArrayList<>();
		after.forEach(e -> order.add(e.transport.id + ":" + e.transport.ticks));
		assertEquals(List.of("t-a:9", "t-b:5", "t-c:8", "t-d:5"), order);
	}

	@Test
	void aDeletedTransportKeepsOnlyItsDelete() throws Exception
	{
		EditStore s = store();
		s.add(transport("t-a", 5));
		s.add(transport("t-a", 6));
		s.add(delete("t-a"));
		List<Edit> after = parse(plan(s).kept);
		assertEquals(1, after.size());
		assertEquals(Edit.Type.TRANSPORT_DELETE, after.get(0).type);
		ChangesCompactor.verify(s.active(), after);
	}

	@Test
	void blockedAfterWalkableKeepsTheWalkable() throws Exception
	{
		// "walkable" clears every blocking flag, "blocked" adds one: both still matter
		EditStore s = store();
		s.add(tile(5, 5, true));
		s.add(tile(5, 5, false));
		s.add(tile(5, 5, true));
		List<Edit> after = parse(plan(s).kept);
		assertEquals(List.of(false, true), List.of(after.get(0).on, after.get(1).on));
		ChangesCompactor.verify(s.active(), after);
	}

	@Test
	void aPlaceKindKeepsTheLastNameGiven() throws Exception
	{
		EditStore s = store();
		s.add(placeKind("12", false, "Altars"));
		s.add(placeKind("12", true, null));
		List<Edit> after = parse(plan(s).kept);
		assertEquals(2, after.size());
		ChangesCompactor.verify(s.active(), after);
		assertEquals("Altars", ManualState.placeKinds(after).names.get(12));
	}

	@Test
	void theCheckStopsAPlanThatWouldChangeTheData() throws Exception
	{
		EditStore s = store();
		s.add(transport("t-a", 5));
		s.add(transport("t-b", 5));
		s.add(transport("t-a", 9));
		List<Edit> wrongOrder = new ArrayList<>(List.of(s.active().get(1), s.active().get(2)));
		assertThrows(IllegalStateException.class, () -> ChangesCompactor.verify(s.active(), wrongOrder));
		List<Edit> lostWalkable = new ArrayList<>();
		EditStore t = new EditStore(dir.resolve("other.jsonl").toFile());
		t.add(tile(1, 1, false));
		t.add(tile(1, 1, true));
		lostWalkable.add(t.active().get(1));
		assertThrows(IllegalStateException.class, () -> ChangesCompactor.verify(t.active(), lostWalkable));
	}

	@Test
	void compactBacksUpThenReplacesTheFile() throws Exception
	{
		EditStore s = store();
		s.add(tile(1, 1, true));
		s.add(tile(1, 1, false));
		s.add(transport("t-a", 5));
		List<String> activeBefore = ids(s.active());
		File file = s.getFile();
		List<String> original = Files.readAllLines(file.toPath());

		ChangesCompactor.Plan p = ChangesCompactor.compact(dir.toFile(), file, line -> { });
		assertEquals(2, p.kept.size());
		assertEquals(p.kept, Files.readAllLines(file.toPath()));
		File[] backups = dir.resolve(ChangesCompactor.BACKUP_DIR).toFile().listFiles();
		assertEquals(1, backups.length);
		assertTrue(backups[0].getName().endsWith("-before-compact"));
		assertEquals(original, Files.readAllLines(new File(backups[0], "changes.jsonl").toPath()));

		s.reload();
		assertEquals(List.of(activeBefore.get(1), activeBefore.get(2)), ids(s.active()));
		assertTrue(ChangesCompactor.compact(dir.toFile(), file, line -> { }).nothingToDo(), "a second time: nothing to do");
		assertEquals(1, dir.resolve(ChangesCompactor.BACKUP_DIR).toFile().listFiles().length, "no backup when nothing changes");
	}
}

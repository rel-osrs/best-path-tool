package com.bestpath.tool.update;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The comparison's result file, read back by the Data Tool. */
class CompareFileTest
{
	@TempDir
	File ws;

	private static CompareFile file(int from, int to, String made)
	{
		CompareFile f = new CompareFile();
		f.from = from;
		f.to = to;
		f.made = made;
		f.report = "docs/reports/x.md";
		CompareFile.Item look = new CompareFile.Item();
		look.key = to + "/OBJECT_GONE/t-1/1,2,0";
		look.kind = "OBJECT_GONE";
		look.look = true;
		look.tiles = new long[]{CacheFacts.tile(1, 2, 0)};
		CompareFile.Item info = new CompareFile.Item();
		info.key = to + "/DOOR_ADDED//1,2,0";
		info.kind = "DOOR_ADDED";
		f.items.add(look);
		f.items.add(info);
		return f;
	}

	@Test
	void writtenAndReadBack() throws Exception
	{
		CompareFile f = file(2735, 2740, "2026-10-19T18:00:00Z");
		f.write(CompareFile.file(ws, 2735, 2740));
		CompareFile back = CompareFile.read(CompareFile.file(ws, 2735, 2740));
		assertEquals(2740, back.to);
		assertEquals(1, back.looks().size());
		assertEquals("2740/OBJECT_GONE/t-1/1,2,0", back.looks().get(0).key);
		assertArrayEquals(new long[]{CacheFacts.tile(1, 2, 0)}, back.looks().get(0).tiles);
	}

	@Test
	void theNewestFromTheOpenCacheIsUsed() throws Exception
	{
		assertNull(CompareFile.newest(ws, 2735));
		file(2735, 2740, "2026-10-19T18:00:00Z").write(CompareFile.file(ws, 2735, 2740));
		file(2735, 2745, "2026-10-26T18:00:00Z").write(CompareFile.file(ws, 2735, 2745));
		file(2740, 2745, "2026-10-27T18:00:00Z").write(CompareFile.file(ws, 2740, 2745));
		assertEquals(2745, CompareFile.newest(ws, 2735).to);
		assertEquals(2740, CompareFile.newest(ws, 2740).from);
	}

	@Test
	void aFindingsKeyNamesTheUpdateWhatAndWhere()
	{
		CacheCompare.Report r = CacheCompare.compare(new CacheFacts(), new CacheFacts(), new OurData());
		r.add(CacheCompare.Kind.OBJECT_GONE, "x", 3200, 3201, 1, "t-9");
		CompareFile f = CompareFile.of(r, "docs/reports/x.md", "now");
		assertEquals("-1/OBJECT_GONE/t-9/3200,3201,1", f.items.get(f.items.size() - 1).key);
	}
}

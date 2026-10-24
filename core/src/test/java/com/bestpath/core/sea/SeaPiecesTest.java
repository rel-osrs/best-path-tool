package com.bestpath.core.sea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.MovementRules;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeaPiecesTest
{
	/** Plane-0 flags: everything open unless set. */
	private static MovementRules.Flags flags(Map<Long, Integer> set)
	{
		return (x, y) -> set.getOrDefault((long) x << 32 | y, 0);
	}

	private static long key(int x, int y)
	{
		return (long) x << 32 | y;
	}

	@Test
	void blockedLineSplitsTheSea()
	{
		// a 10 x 5 sea across a region border (x 3200), with a blocked line at x = 3198
		SeaMap.Builder b = new SeaMap.Builder();
		Map<Long, Integer> f = new HashMap<>();
		for (int x = 3190; x < 3200 + 10; x++)
		{
			for (int y = 3000; y < 3005; y++)
			{
				b.set(x, y, x < 3198 ? 131 : 176);
				if (x == 3198)
				{
					f.put(key(x, y), CollisionFlags.FLOOR);
				}
			}
		}
		SeaPieces p = SeaPieces.build(b.build(), flags(f));
		assertEquals(2, p.count());
		assertEquals(-1, p.pieceAt(3198, 3002, 0)); // the blocked line is in no piece
		assertNotEquals(p.pieceAt(3190, 3000, 0), p.pieceAt(3209, 3004, 0));
		assertEquals(40, p.size(p.pieceAt(3190, 3000, 0)));
		assertEquals(55, p.size(p.pieceAt(3209, 3004, 0)));
		assertEquals(p.pieceAt(3209, 3004, 0), p.mainPiece()); // the biggest
		assertEquals(-1, p.pieceAt(3190, 3000, 1));
	}

	@Test
	void wallsSplitTooButAreaBordersDoNot()
	{
		// two tiles side by side in different areas: one piece; a wall between them: two
		SeaMap sea = new SeaMap.Builder().set(3000, 3000, 131).set(3001, 3000, 176).build();
		assertEquals(1, SeaPieces.build(sea, flags(new HashMap<>())).count());
		Map<Long, Integer> f = new HashMap<>();
		f.put(key(3000, 3000), CollisionFlags.EAST);
		f.put(key(3001, 3000), CollisionFlags.WEST);
		assertEquals(2, SeaPieces.build(sea, flags(f)).count());
	}

	@Test
	void noSea()
	{
		SeaPieces p = SeaPieces.build(SeaMap.empty(), flags(new HashMap<>()));
		assertEquals(0, p.count());
		assertEquals(-1, p.mainPiece());
		assertEquals(-1, p.pieceAt(3000, 3000, 0));
	}

	@Test
	void areasByTextureFamily()
	{
		assertNull(SeaMap.areaOf(-1));
		assertEquals(SeaArea.OPEN, SeaMap.areaOf(1)); // coastal shallows
		assertEquals(SeaArea.OPEN, SeaMap.areaOf(130));
		assertEquals(SeaArea.TEMPOR_TANTRUM, SeaMap.areaOf(138));
		assertEquals(SeaArea.OPEN, SeaMap.areaOf(140));
		assertEquals(SeaArea.NORTHERN, SeaMap.areaOf(164));
		assertEquals(SeaArea.CRYSTALLINE, SeaMap.areaOf(174));
		assertEquals(SeaArea.FETID, SeaMap.areaOf(176));
		assertEquals(SeaArea.KELP, SeaMap.areaOf(184));
		assertEquals(SeaArea.OPEN, SeaMap.areaOf(189));
		assertEquals(SeaArea.WORLD_EDGE, SeaMap.areaOf(208));
	}
}

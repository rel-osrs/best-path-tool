package com.bestpath.tool.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import java.util.List;
import org.junit.jupiter.api.Test;

class DockExtractorTest
{
	@Test
	void idPattern()
	{
		assertEquals(59769, DockExtractor.buoyId(0));
		assertEquals(59827, DockExtractor.buoyId(58));
		assertEquals(62396, DockExtractor.buoyId(60));
		assertEquals(59835, DockExtractor.landId(0));
		assertEquals(59893, DockExtractor.landId(58));
		assertEquals(62397, DockExtractor.landId(59));
	}

	private static Dock dock(int id, int buoyX)
	{
		return new Dock(id, "D" + id, 1, null, Dock.Kind.GANGPLANK, buoyX, 3011, 0, 1, buoyX, 3004, 2, List.of());
	}

	@Test
	void copiesOffTheMainOceanAreDroppedOnlyWhenTheDockIsAlsoOnIt()
	{
		// main ocean x 3000-3019, a separate pool x 3030-3033
		SeaMap.Builder b = new SeaMap.Builder();
		for (int y = 3000; y < 3010; y++)
		{
			for (int x = 3000; x < 3020; x++)
			{
				b.set(x, y, 131);
			}
			for (int x = 3030; x < 3034; x++)
			{
				b.set(x, y, 131);
			}
		}
		SeaPieces pieces = SeaPieces.build(b.build(), (x, y) -> (y == 3004 || y == 3005) && (x == 3005 || x == 3006 || x == 3031 || x == 3032) ? CollisionFlags.OBJECT : 0);
		Dock real = dock(29, 3005);
		Dock copy = dock(29, 3031); // Prifddinas: also on the main ocean, so the copy goes
		Dock onlyInPool = dock(60, 3031); // Wyrmscraig Cavern: its only copy, so it stays
		assertEquals(List.of(real, onlyInPool), DockExtractor.withoutDuplicates(List.of(real, copy, onlyInPool), pieces));
	}
}

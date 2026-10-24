package com.bestpath.core.sea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DockTest
{
	@Test
	void buoyOnTheMainOceanOrNot()
	{
		// a big sea (x 3000-3019) and a small pool (x 3030-3033), all open; buoys are blocked 2x2 objects
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
		SeaPieces pieces = SeaPieces.build(b.build(), (x, y) ->
			(x == 3005 || x == 3006 || x == 3031 || x == 3032) && (y == 3004 || y == 3005) ? CollisionFlags.OBJECT : 0);
		Dock big = new Dock(0, "Big", 1, null, Dock.Kind.GANGPLANK, 3000, 3011, 0, 1, 3005, 3004, 2, List.of(new int[]{3000, 3012, 0}));
		Dock pool = new Dock(1, "Pool", 62, "Some Quest", Dock.Kind.MOORING_POINT, 3030, 3011, 0, 3, 3031, 3004, 2, new ArrayList<>());
		assertTrue(DockDiagnostics.onMainOcean(big, pieces));
		assertFalse(DockDiagnostics.onMainOcean(pool, pieces));
		assertEquals("level 1", DockDiagnostics.requirementText(big));
		assertEquals("level 62, Some Quest", DockDiagnostics.requirementText(pool));
	}

	@Test
	void landTilesAreCopied()
	{
		int[] t = {3000, 3012, 0};
		List<int[]> list = new ArrayList<>();
		list.add(t);
		Dock d = new Dock(0, "A", 1, null, Dock.Kind.GANGPLANK, 3000, 3011, 0, 1, 3005, 3004, 2, list);
		t[0] = 1;
		list.clear();
		assertEquals(1, d.getLandTiles().size());
		assertEquals(3000, d.getLandTiles().get(0)[0]);
	}
}

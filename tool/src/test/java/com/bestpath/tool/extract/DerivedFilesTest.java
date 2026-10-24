package com.bestpath.tool.extract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.CollisionMapWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DerivedFilesTest
{
	/** Two regions side by side with a wall of rocks and one gap. */
	private static FlagGrid grid()
	{
		FlagGrid g = new FlagGrid(Set.of(50 << 8 | 50, 51 << 8 | 50));
		for (int x = 3200; x < 3328; x++)
		{
			for (int y = 3200; y < 3264; y++)
			{
				g.add(x, y, 0, 0); // creates the blocks
			}
		}
		for (int y = 3200; y < 3264; y++)
		{
			if (y != 3230)
			{
				g.add(3260, y, 0, CollisionFlags.OBJECT);
			}
		}
		return g;
	}

	private static byte[] bytes(CollisionMap m) throws Exception
	{
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		CollisionMapWriter.write(m, b);
		return b.toByteArray();
	}

	@Test
	void savedDataComesBackOnlyForTheSameData(@TempDir File dir) throws Exception
	{
		FlagGrid g = grid();
		Set<Integer> regions = Set.of(50 << 8 | 50, 51 << 8 | 50);
		long fp = DerivedFiles.fingerprint(g.blockHashes(), g.getDoors());
		CollisionMap m = ExportBuilder.fullCollision(g, regions, 0, 0);
		DerivedFiles d = new DerivedFiles(dir);
		assertNull(d.loadMovement(fp));
		d.saveMovement(fp, m);
		assertArrayEquals(bytes(m), bytes(d.loadMovement(fp)));

		// an edit changes the fingerprint; the old file is replaced when the new one is saved
		g.add(3230, 3230, 0, CollisionFlags.OBJECT);
		Map<Integer, Long> after = g.blockHashes();
		long fp2 = DerivedFiles.fingerprint(after, g.getDoors());
		assertNotEquals(fp, fp2);
		assertNull(d.loadMovement(fp2));
		d.saveMovement(fp2, ExportBuilder.fullCollision(g, regions, 0, 0));
		assertNull(d.loadMovement(fp));
		assertNotNull(d.loadMovement(fp2));
	}

	@Test
	void changedRegionsAndIncrementalMovementData()
	{
		FlagGrid g = grid();
		Set<Integer> regions = Set.of(50 << 8 | 50, 51 << 8 | 50);
		Map<Integer, Long> before = g.blockHashes();
		CollisionMap m1 = ExportBuilder.fullCollision(g, regions, 0, 0);
		g.add(3300, 3240, 0, CollisionFlags.OBJECT);
		Set<Integer> changed = FlagGrid.changedRegions(before, g.blockHashes());
		assertEquals(Set.of(51 << 8 | 50), changed);
		CollisionMap inc = ExportBuilder.fullCollision(g, regions, 0, 0, m1, CollisionMapBuilder.withNeighbours(changed));
		CollisionMap full = ExportBuilder.fullCollision(g, regions, 0, 0);
		for (int x = 3290; x < 3310; x++)
		{
			for (int y = 3230; y < 3250; y++)
			{
				assertEquals(full.canStep(x, y, 0, 1, 0), inc.canStep(x, y, 0, 1, 0));
				assertEquals(full.canStep(x, y, 0, 1, 1), inc.canStep(x, y, 0, 1, 1));
			}
		}
	}
}

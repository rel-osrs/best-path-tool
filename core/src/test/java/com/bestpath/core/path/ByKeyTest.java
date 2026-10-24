package com.bestpath.core.path;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The coarse stage's sorted "values by key" (replacing maps
 * of boxed lists): each key's values come back in the order added, as the maps' lists did, so the search
 * relaxes nodes in the same order and finds the same routes.
 */
class ByKeyTest
{
	private static List<Integer> values(ByKey k, int key)
	{
		List<Integer> out = new ArrayList<>();
		for (int e = k.first(key); e >= 0 && e < k.size() && k.key(e) == key; e++)
		{
			out.add(k.value(e));
		}
		return out;
	}

	@Test
	void eachKeysValuesInTheOrderAdded()
	{
		ByKey.Builder b = new ByKey.Builder(2);
		b.add(7, 0);
		b.add(3, 1);
		b.add(7, 2);
		b.add(1 << 29, 3); // a packed tile as a key
		b.add(3, 4);
		b.add(7, 5);
		ByKey k = b.build();
		assertEquals(List.of(1, 4), values(k, 3));
		assertEquals(List.of(0, 2, 5), values(k, 7));
		assertEquals(List.of(3), values(k, 1 << 29));
		assertEquals(3, k.count(k.first(7)));
		assertEquals(-1, k.first(4));
		assertEquals(-1, k.first(0));
		assertEquals(-1, k.first(Integer.MAX_VALUE));
		assertEquals(-1, new ByKey.Builder(1).build().first(3));
	}
}

package com.bestpath.core.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where a route search's time went, part by part: each part's total time and how many times it ran, in the order
 * first seen. Shown when the "Developer details" setting is on. Not thread-safe (the router fills it inside its own
 * lock).
 */
public final class RouteTimes
{
	final Map<String, long[]> parts = new LinkedHashMap<>();

	/** Adds {@code nanos} to a part (and one more run of it). */
	public void add(String part, long nanos)
	{
		long[] p = parts.computeIfAbsent(part, k -> new long[2]);
		p[0] += nanos;
		p[1]++;
	}

	/** A copy (what the router hands out). */
	public RouteTimes copy()
	{
		RouteTimes c = new RouteTimes();
		parts.forEach((k, v) -> c.parts.put(k, v.clone()));
		return c;
	}

	/** All parts together, in milliseconds. */
	public long totalMillis()
	{
		long n = 0;
		for (long[] p : parts.values())
		{
			n += p[0];
		}
		return n / 1_000_000;
	}

	/** One line per part: "Walking search: 31 ms (2 times)". */
	public List<String> lines()
	{
		List<String> out = new ArrayList<>();
		parts.forEach((k, v) -> out.add(String.format(Locale.ROOT, "%s: %s%s", k, millis(v[0]), v[1] > 1 ? " (" + v[1] + " times)" : "")));
		return out;
	}

	private static String millis(long nanos)
	{
		double ms = nanos / 1e6;
		return ms < 10 ? String.format(Locale.ROOT, "%.1f ms", ms) : String.format(Locale.ROOT, "%.0f ms", ms);
	}
}

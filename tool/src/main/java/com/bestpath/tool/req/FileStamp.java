package com.bestpath.tool.req;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A data file's change stamp (time and length), asked of the disk at most every 2 seconds. The route search reads
 * the data files once per transport (10,000+ per rebuild); a disk check each time would make every edit take seconds
 * and could freeze the tool during a long list of remote-link commands.
 */
public final class FileStamp
{
	private static final long CHECK_NANOS = 2_000_000_000L;
	private static final Map<String, long[]> CACHE = new ConcurrentHashMap<>();

	private FileStamp()
	{
	}

	/** {@code lastModified ^ length}, or -1 without the file; at most 2 s old. */
	public static long of(File f)
	{
		long now = System.nanoTime();
		long[] c = CACHE.get(f.getPath());
		if (c != null && now - c[1] < CHECK_NANOS)
		{
			return c[0];
		}
		long stamp = f.isFile() ? f.lastModified() ^ f.length() : -1;
		CACHE.put(f.getPath(), new long[]{stamp, now});
		return stamp;
	}
}

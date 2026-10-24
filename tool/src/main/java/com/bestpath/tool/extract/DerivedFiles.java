package com.bestpath.tool.extract;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapCodec;
import com.bestpath.core.collision.CollisionMapWriter;
import com.bestpath.tool.cache.LoadedCache;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Data the tool works out from the movement data and keeps on disk, so the
 * next start reuses it: the movement data in its compact form. Each file is
 * named by a fingerprint of the movement data it was made from (every
 * region's flags with the manual changes, and the doors), so a file never
 * fits other data. Only the newest file is kept. In a "build" folder inside
 * "bestpath-derived" beside the cache (the cache folder itself stays as
 * downloaded). Tool only.
 */
public final class DerivedFiles
{
	private final File dir;

	public DerivedFiles(LoadedCache cache)
	{
		this(new File(CollisionExtractor.savedFile(cache).getParentFile(), "build"));
	}

	DerivedFiles(File dir)
	{
		this.dir = dir;
	}

	/**
	 * Bump when the way the movement data is made from the flags changes (not
	 * seen in the region fingerprints), so older saved files are not reused.
	 * 2: sea tiles made walkable by hand stay open.
	 */
	static final int RULES_VERSION = 2;

	/** A fingerprint of the movement data: the region fingerprints of {@link FlagGrid#blockHashes} and the doors. */
	public static long fingerprint(Map<Integer, Long> blockHashes, List<int[]> doors)
	{
		long h = (0xcbf29ce484222325L ^ RULES_VERSION) * 0x100000001b3L;
		for (Map.Entry<Integer, Long> e : new TreeMap<>(blockHashes).entrySet())
		{
			h = (h ^ e.getKey()) * 0x100000001b3L;
			h = (h ^ e.getValue()) * 0x100000001b3L;
		}
		for (int[] d : doors)
		{
			for (int v : d)
			{
				h = (h ^ v) * 0x100000001b3L;
			}
		}
		return h;
	}

	private File movementFile(long fp)
	{
		return new File(dir, String.format(Locale.ROOT, "movement-%016x.bin", fp));
	}

	/** The saved movement data for this fingerprint, or null. */
	public CollisionMap loadMovement(long fp)
	{
		File f = movementFile(fp);
		if (!f.isFile())
		{
			return null;
		}
		try (InputStream in = new BufferedInputStream(new FileInputStream(f), 1 << 16))
		{
			return CollisionMapCodec.read(in);
		}
		catch (IOException | RuntimeException ex)
		{
			return null; // a damaged file: work it out again
		}
	}

	public void saveMovement(long fp, CollisionMap map)
	{
		save(movementFile(fp), "movement-", out -> CollisionMapWriter.write(map, out));
	}

	private interface Writer
	{
		void write(OutputStream out) throws IOException;
	}

	/** Writes beside the target and then renames (no half-written file), and deletes older files of the same kind. */
	private void save(File target, String prefix, Writer w)
	{
		try
		{
			dir.mkdirs();
			File tmp = new File(dir, target.getName() + ".tmp");
			try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp), 1 << 16))
			{
				w.write(out);
			}
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
			File[] old = dir.listFiles((d, n) -> n.startsWith(prefix) && !n.equals(target.getName()));
			for (File f : old == null ? new File[0] : old)
			{
				f.delete();
			}
		}
		catch (IOException ex)
		{
			// not saved: the next start works it out again
		}
	}
}

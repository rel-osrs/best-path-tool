package com.bestpath.tool.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Unzips an archive, refusing entries that would escape the target folder. */
public final class ZipExtractor
{
	private ZipExtractor()
	{
	}

	public static void extract(File zip, File targetDir) throws IOException
	{
		String root = targetDir.getCanonicalPath() + File.separator;
		try (ZipFile zf = new ZipFile(zip))
		{
			Enumeration<? extends ZipEntry> entries = zf.entries();
			while (entries.hasMoreElements())
			{
				ZipEntry e = entries.nextElement();
				File out = safeTarget(root, targetDir, e.getName());
				if (e.isDirectory())
				{
					mkdirs(out);
					continue;
				}
				mkdirs(out.getParentFile());
				try (InputStream in = zf.getInputStream(e); OutputStream os = new FileOutputStream(out))
				{
					in.transferTo(os);
				}
			}
		}
	}

	/** Resolves an entry name inside targetDir, rejecting "../" and absolute paths. */
	static File safeTarget(String canonicalRoot, File targetDir, String entryName) throws IOException
	{
		File out = new File(targetDir, entryName);
		String path = out.getCanonicalPath();
		if (!path.startsWith(canonicalRoot))
		{
			throw new IOException("Unsafe path in zip: " + entryName);
		}
		return out;
	}

	private static void mkdirs(File dir) throws IOException
	{
		if (!dir.isDirectory() && !dir.mkdirs())
		{
			throw new IOException("Cannot create " + dir);
		}
	}
}

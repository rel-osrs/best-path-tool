package com.bestpath.tool.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** Downloads and unpacks an OpenRS2 cache into {@code <cacheRoot>/openrs2-<id>/}. */
public final class CacheDownloader
{
	public interface Progress
	{
		/** Called from the download thread. total is -1 when unknown. */
		void update(String stage, long done, long total);
	}

	private final AtomicBoolean cancelled = new AtomicBoolean();

	public void cancel()
	{
		cancelled.set(true);
	}

	/** Returns the folder that contains main_file_cache.dat2. */
	public File download(CacheInfo info, File cacheRoot, Progress progress) throws IOException
	{
		File dir = new File(cacheRoot, "openrs2-" + info.getOpenrs2Id());
		File disk = new File(dir, "disk");
		File existing = LoadedCache.findCacheFolder(disk);
		if (existing != null)
		{
			return existing;
		}
		if (!dir.isDirectory() && !dir.mkdirs())
		{
			throw new IOException("Cannot create " + dir);
		}

		File part = new File(dir, "disk.zip.part");
		File zip = new File(dir, "disk.zip");
		HttpURLConnection c = (HttpURLConnection) new URL(CacheCatalog.diskZipUrl(info)).openConnection();
		c.setRequestProperty("User-Agent", CacheCatalog.USER_AGENT);
		c.setConnectTimeout(15_000);
		c.setReadTimeout(60_000);
		if (c.getResponseCode() != 200)
		{
			throw new IOException("OpenRS2 returned HTTP " + c.getResponseCode());
		}
		long total = c.getContentLengthLong();
		try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part))
		{
			byte[] buf = new byte[1 << 16];
			long done = 0;
			int n;
			while ((n = in.read(buf)) > 0)
			{
				if (cancelled.get())
				{
					throw new IOException("Download cancelled");
				}
				out.write(buf, 0, n);
				done += n;
				progress.update("Downloading", done, total);
			}
		}
		catch (IOException e)
		{
			part.delete();
			throw e;
		}
		if (!part.renameTo(zip))
		{
			throw new IOException("Cannot rename " + part);
		}

		progress.update("Unpacking", 0, -1);
		ZipExtractor.extract(zip, disk);
		zip.delete();
		info.save(dir);

		File found = LoadedCache.findCacheFolder(disk);
		if (found == null)
		{
			throw new IOException("Downloaded archive did not contain a cache");
		}
		return found;
	}
}

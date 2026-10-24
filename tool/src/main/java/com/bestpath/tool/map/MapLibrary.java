package com.bestpath.tool.map;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The saved map-picture library, shared by every cache: {@code
 * <workspace>/map-pictures/}. Each region has a fingerprint of everything its
 * picture depends on; a picture is reused for a new cache when the
 * fingerprint is unchanged, so a game update only redraws changed regions.
 *
 * <pre>
 * index.tsv          region, fingerprint, planes-with-picture mask, empty-planes mask
 * z{plane}/{id}.png  full 256x256 pictures
 * thumbs-v2.bin.gz   32x32 thumbnails (v2: made by repeated halving) of every picture, for an instant overview
 * </pre>
 *
 * Not thread-safe; used from the map worker thread only (plus load/save).
 */
public final class MapLibrary
{
	static final class Entry
	{
		volatile long fingerprint;
		volatile int pictureMask;
		volatile int emptyMask;

		boolean isDone(int plane)
		{
			return ((pictureMask | emptyMask) >> plane & 1) != 0;
		}

		boolean isEmpty(int plane)
		{
			return (emptyMask >> plane & 1) != 0;
		}
	}

	private final File dir;
	private final Map<Integer, Entry> entries = new HashMap<>();
	private final Map<Long, int[]> thumbs = new HashMap<>();

	public MapLibrary(File dir)
	{
		this.dir = dir;
	}

	public File getDir()
	{
		return dir;
	}

	public void load()
	{
		File idx = new File(dir, "index.tsv");
		if (idx.isFile())
		{
			try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(idx), StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = r.readLine()) != null)
				{
					if (line.startsWith("#") || line.isEmpty())
					{
						continue;
					}
					String[] f = line.split("\t");
					Entry e = new Entry();
					e.fingerprint = Long.parseUnsignedLong(f[1], 16);
					e.pictureMask = Integer.parseInt(f[2]);
					e.emptyMask = Integer.parseInt(f[3]);
					entries.put(Integer.parseInt(f[0]), e);
				}
			}
			catch (IOException | RuntimeException e)
			{
				entries.clear(); // unreadable index: everything gets redrawn
			}
		}
		File tb = new File(dir, "thumbs-v2.bin.gz");
		if (tb.isFile())
		{
			try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(tb), 1 << 16)))
			{
				int n = in.readInt();
				int px = MapTileStore.THUMB * MapTileStore.THUMB;
				for (int i = 0; i < n; i++)
				{
					long key = in.readLong();
					int[] argb = new int[px];
					for (int j = 0; j < px; j++)
					{
						argb[j] = in.readInt();
					}
					thumbs.put(key, argb);
				}
			}
			catch (IOException | RuntimeException e)
			{
				thumbs.clear();
			}
		}
	}

	/** Writes index and thumbnails atomically (temp file, then rename). */
	public synchronized void save() throws IOException
	{
		dir.mkdirs();
		File idxTmp = new File(dir, "index.tsv.tmp");
		try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(idxTmp), StandardCharsets.UTF_8)))
		{
			w.write("# BestPath Data Tool map-picture library: region\tfingerprint\tpictureMask\temptyMask\n");
			for (Map.Entry<Integer, Entry> e : entries.entrySet())
			{
				Entry v = e.getValue();
				w.write(e.getKey() + "\t" + Long.toHexString(v.fingerprint) + "\t" + v.pictureMask + "\t" + v.emptyMask + "\n");
			}
		}
		Files.move(idxTmp.toPath(), new File(dir, "index.tsv").toPath(), StandardCopyOption.REPLACE_EXISTING);

		File tbTmp = new File(dir, "thumbs-v2.bin.gz.tmp");
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(tbTmp), 1 << 16)))
		{
			out.writeInt(thumbs.size());
			for (Map.Entry<Long, int[]> e : thumbs.entrySet())
			{
				out.writeLong(e.getKey());
				for (int v : e.getValue())
				{
					out.writeInt(v);
				}
			}
		}
		Files.move(tbTmp.toPath(), new File(dir, "thumbs-v2.bin.gz").toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	synchronized Entry get(int regionId)
	{
		return entries.get(regionId);
	}

	/**
	 * Starts a fresh entry for a region whose fingerprint changed. The old
	 * thumbnails stay visible until each plane is redrawn.
	 */
	synchronized Entry reset(int regionId, long fingerprint)
	{
		Entry e = new Entry();
		e.fingerprint = fingerprint;
		entries.put(regionId, e);
		return e;
	}

	synchronized void markPicture(int regionId, int plane, BufferedImage thumb)
	{
		Entry e = entries.get(regionId);
		e.pictureMask |= 1 << plane;
		e.emptyMask &= ~(1 << plane);
		int[] argb = new int[thumb.getWidth() * thumb.getHeight()];
		thumb.getRGB(0, 0, thumb.getWidth(), thumb.getHeight(), argb, 0, thumb.getWidth());
		thumbs.put(MapTileStore.key(regionId, plane), argb);
	}

	synchronized void markEmpty(int regionId, int plane)
	{
		Entry e = entries.get(regionId);
		e.emptyMask |= 1 << plane;
		e.pictureMask &= ~(1 << plane);
		thumbs.remove(MapTileStore.key(regionId, plane));
	}

	synchronized BufferedImage thumb(long key)
	{
		int[] argb = thumbs.get(key);
		if (argb == null)
		{
			return null;
		}
		BufferedImage img = new BufferedImage(MapTileStore.THUMB, MapTileStore.THUMB, BufferedImage.TYPE_INT_ARGB);
		img.setRGB(0, 0, MapTileStore.THUMB, MapTileStore.THUMB, argb, 0, MapTileStore.THUMB);
		return img;
	}

	File picture(int regionId, int plane)
	{
		return new File(dir, "z" + plane + "/" + regionId + ".png");
	}

	private File jpegPicture(int regionId, int plane)
	{
		return new File(dir, "z" + plane + "/" + regionId + ".jpg");
	}

	/** The saved picture of a region-plane (PNG or JPEG), or null if there is none. */
	File savedPicture(int regionId, int plane)
	{
		File png = picture(regionId, plane);
		if (png.isFile())
		{
			return png;
		}
		File jpg = jpegPicture(regionId, plane);
		return jpg.isFile() ? jpg : null;
	}

	/**
	 * Saves a picture. With {@code jpegIfOpaque}, a picture without see-through
	 * pixels is saved as JPEG (about a third of the PNG size for the 3D map);
	 * otherwise as PNG. The other format's old file is removed.
	 */
	void savePicture(int regionId, int plane, BufferedImage img, boolean jpegIfOpaque) throws IOException
	{
		File png = picture(regionId, plane);
		File jpg = jpegPicture(regionId, plane);
		png.getParentFile().mkdirs();
		if (jpegIfOpaque && isOpaque(img))
		{
			BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D g = rgb.createGraphics();
			g.drawImage(img, 0, 0, null);
			g.dispose();
			javax.imageio.ImageWriter w = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
			javax.imageio.ImageWriteParam p = w.getDefaultWriteParam();
			p.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
			p.setCompressionQuality(0.88f);
			File tmp = new File(jpg.getPath() + ".tmp");
			try (javax.imageio.stream.ImageOutputStream out = javax.imageio.ImageIO.createImageOutputStream(tmp))
			{
				w.setOutput(out);
				w.write(null, new javax.imageio.IIOImage(rgb, null, null), p);
			}
			finally
			{
				w.dispose();
			}
			Files.move(tmp.toPath(), jpg.toPath(), StandardCopyOption.REPLACE_EXISTING);
			png.delete();
		}
		else
		{
			javax.imageio.ImageIO.write(img, "png", png);
			jpg.delete();
		}
	}

	private static boolean isOpaque(BufferedImage img)
	{
		int w = img.getWidth();
		int[] row = new int[w];
		for (int y = 0; y < img.getHeight(); y++)
		{
			img.getRGB(0, y, w, 1, row, 0, w);
			for (int v : row)
			{
				if ((v >>> 24) != 0xFF)
				{
					return false;
				}
			}
		}
		return true;
	}
}

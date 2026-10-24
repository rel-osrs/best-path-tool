package com.bestpath.tool.extract;

import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaGridBuilder;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.EditStore;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.wiki.Shipwrights;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The map as the Data Tool builds it, for the programs that run without the window: the cache's movement data with
 * the manual changes ({@code data/manual/changes.jsonl}) applied, and the sea closed for walking (the player goes to
 * sea at a dock). {@link #withSea} also makes the boats' side: the sea split into pieces, the main ocean's search grid
 * and the docks with their shipwrights. The window builds the same map, but region by region as edits come in
 * ({@code MainWindow}).
 */
public final class ToolMap
{
	/** The manual changes, loaded. */
	public final EditStore edits;
	/** The changes applied to {@link #grid}: transports, opened tiles, ignored regions. */
	public final ManualState manual;
	/** The walking data: the cache's, with the manual changes, the sea blocked. */
	public final FlagGrid grid;
	public final SeaMap sea;
	/** The sea split along blocked tiles (boats cannot cross them); null from {@link #walking}. */
	public final SeaPieces pieces;
	/** The main ocean as the boats' search grid; null from {@link #walking}. */
	public final SeaGrid seaGrid;
	/** The docks (duplicates dropped) with their shipwrights; null from {@link #walking}. */
	public final List<Dock> docks;

	private ToolMap(EditStore edits, ManualState manual, FlagGrid grid, SeaMap sea, SeaPieces pieces, SeaGrid seaGrid,
		List<Dock> docks)
	{
		this.edits = edits;
		this.manual = manual;
		this.grid = grid;
		this.sea = sea;
		this.pieces = pieces;
		this.seaGrid = seaGrid;
		this.docks = docks;
	}

	/** The walking map only (for checks that never sail). */
	public static ToolMap walking(LoadedCache cache, File ws) throws IOException
	{
		return build(cache, ws, edit -> true, false);
	}

	/** The walking map with only the changes {@code use} accepts (the others are left out). */
	public static ToolMap walking(LoadedCache cache, File ws, Predicate<Edit> use) throws IOException
	{
		return build(cache, ws, use, false);
	}

	/** The walking map and the boats' side, as "Update plugin data" builds them. */
	public static ToolMap withSea(LoadedCache cache, File ws) throws IOException
	{
		return build(cache, ws, edit -> true, true);
	}

	private static ToolMap build(LoadedCache cache, File ws, Predicate<Edit> use, boolean withSea) throws IOException
	{
		EditStore edits = new EditStore(new File(ws, "data/manual/changes.jsonl"));
		edits.load();
		List<Edit> applied = new ArrayList<>();
		for (Edit e : edits.active())
		{
			if (use.test(e))
			{
				applied.add(e);
			}
		}
		FlagGrid cacheGrid = CollisionExtractor.loadOrExtract(cache);
		FlagGrid grid = cacheGrid.copy();
		ManualState manual = new ManualState(applied, grid);
		SeaMap sea = SeaExtractor.extract(cache);
		SeaPieces pieces = null;
		SeaGrid seaGrid = null;
		List<Dock> docks = null;
		if (withSea)
		{
			// made while the sea is still open; the ocean grid includes the water the boat-only transports lead to
			pieces = SeaPieces.build(sea, grid.plane(0));
			seaGrid = SeaGridBuilder.build(sea, pieces, grid.plane(0), SeaExtractor.boatEnds(manual.getTransports().values()));
			// docks from the cache's own movement data (without the manual changes)
			docks = Shipwrights.assign(DockExtractor.withoutDuplicates(DockExtractor.extract(cache, cacheGrid, sea).docks, pieces),
				new File(ws, "data/wiki/shipwrights.json"), new ArrayList<>());
		}
		SeaExtractor.blockForWalking(grid, sea, (x, y) -> manual.isOpenedByOwner(x, y, 0));
		return new ToolMap(edits, manual, grid, sea, pieces, seaGrid, docks);
	}
}

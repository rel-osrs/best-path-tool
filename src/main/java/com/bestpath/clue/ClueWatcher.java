package com.bestpath.clue;

import com.bestpath.core.data.ClueData;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.ItemID;

/**
 * Our own clue detection, from the clue items, the clue text and the game
 * cache's clue tables (RuneLite's Clue Scroll plugin is not used). Fed by game
 * events on the CLIENT THREAD only:
 *
 * <ul>
 * <li>{@link #onInventory}: the inventory changed (ItemContainerChanged).
 * Easy, medium, hard and elite clues (and the quest clues) have their own
 * item per step: the clue data's item list names the step; an item missing
 * from it is looked up by its item param 623 ({@code ParamID.CLUE_SCROLL},
 * public API {@code ItemComposition.getIntValue}), which holds the step's
 * cache row. Beginner and master clues use one item for every step: they
 * need the text. A strange device means a hot/cold clue.</li>
 * <li>{@link #onClueText}: the text read off the clue scroll's interface
 * (InterfaceID.TrailCluetext.TEXT) when the player reads it.</li>
 * </ul>
 *
 * <p>Each call returns the new detection when it changed (else null). Pure
 * apart from RuneLite's item id constants.
 */
public final class ClueWatcher
{
	/** Beginner and master clue scrolls (one item for all steps of the tier). */
	public static final int BEGINNER_CLUE = ItemID.TRAIL_CLUE_BEGINNER;
	public static final int MASTER_CLUE = ItemID.TRAIL_CLUE_MASTER;
	/** Strange devices of beginner and master hot/cold clues. */
	public static final int BEGINNER_DEVICE = ItemID.BEGINNER_DEVICE;
	public static final int MASTER_DEVICE = ItemID.MASTER_DEVICE;

	private ClueData data;
	/** Clue items in the inventory at the last change, in slot order. */
	private Set<Integer> carried = new LinkedHashSet<>();
	private DetectedClue current = DetectedClue.NONE;

	/** @param data the clue steps of the map data; null until the map data is loaded */
	public ClueWatcher(ClueData data)
	{
		this.data = data;
	}

	/**
	 * The clue data arrived (the map data loads after start). Looks at the
	 * clue items already carried again; returns the new detection or null.
	 */
	public DetectedClue setData(ClueData d, IntUnaryOperator clueParam)
	{
		data = d;
		List<Integer> again = new ArrayList<>(carried);
		carried = new LinkedHashSet<>();
		return onInventory(again, clueParam);
	}

	public DetectedClue getCurrent()
	{
		return current;
	}

	/**
	 * The inventory's item ids changed.
	 *
	 * @param itemIds   every item id in the inventory (any order, repeats allowed)
	 * @param clueParam an item's param 623 (the step's cache row), or -1 / 0 if it has none; only asked for items the
	 *                  clue data doesn't list
	 * @return the new detection, or null if it didn't change
	 */
	public DetectedClue onInventory(Iterable<Integer> itemIds, IntUnaryOperator clueParam)
	{
		Set<Integer> now = new LinkedHashSet<>();
		for (int id : itemIds)
		{
			if (isClue(id, clueParam))
			{
				now.add(id);
			}
		}
		Set<Integer> added = new LinkedHashSet<>(now);
		added.removeAll(carried);
		carried = now;
		if (!added.isEmpty())
		{
			// the newest clue item wins (a device arrives while its scroll is still carried)
			int id = -1;
			for (int a : added)
			{
				id = a;
			}
			return change(detect(id, clueParam));
		}
		if (current.getItemId() != -1 && !now.contains(current.getItemId()))
		{
			// the clue item is gone (step done, dropped, banked): another one still carried, or none
			int other = -1;
			for (int c : now)
			{
				other = c;
			}
			return change(other == -1 ? DetectedClue.NONE : detect(other, clueParam));
		}
		return null;
	}

	/**
	 * The clue scroll's text was read (the clue interface opened). Matches it
	 * against the steps of the carried beginner / master clue's tier (any tier
	 * when no such clue is carried).
	 *
	 * @return the new detection, or null if it didn't change
	 */
	public DetectedClue onClueText(String text)
	{
		if (text == null || text.trim().isEmpty())
		{
			return null;
		}
		String tier = carried.contains(MASTER_CLUE) ? "master" : carried.contains(BEGINNER_CLUE) ? "beginner" : null;
		int item = "master".equals(tier) ? MASTER_CLUE : "beginner".equals(tier) ? BEGINNER_CLUE : -1;
		if (tier == null)
		{
			// an easy-elite scroll: its item already names the step; the text adds nothing
			if (current.getKind() == DetectedClue.Kind.STEP || current.getKind() == DetectedClue.Kind.HOT_COLD)
			{
				return null;
			}
		}
		if (data == null)
		{
			return change(new DetectedClue(DetectedClue.Kind.NO_DATA, tier, item, List.of(), ""));
		}
		List<ClueData.Step> steps = data.matchText(text, tier);
		if (steps.isEmpty())
		{
			if (tier == null)
			{
				return null; // not a clue we carry: ignore
			}
			return change(new DetectedClue(DetectedClue.Kind.NOT_FOUND, tier, item, List.of(), "text: " + shortText(text)));
		}
		return change(new DetectedClue(DetectedClue.Kind.STEP, steps.get(0).getTier(), item, steps, ""));
	}

	/** What one clue item says. */
	private DetectedClue detect(int id, IntUnaryOperator clueParam)
	{
		if (id == BEGINNER_DEVICE || id == MASTER_DEVICE)
		{
			String tier = id == BEGINNER_DEVICE ? "beginner" : "master";
			if (data == null)
			{
				return new DetectedClue(DetectedClue.Kind.NO_DATA, tier, id, List.of(), "");
			}
			List<ClueData.Step> spots = data.hotCold(tier);
			return spots.isEmpty() ? new DetectedClue(DetectedClue.Kind.NOT_FOUND, tier, id, List.of(), "no hot/cold spots")
				: new DetectedClue(DetectedClue.Kind.HOT_COLD, tier, id, spots, "");
		}
		if (id == BEGINNER_CLUE || id == MASTER_CLUE)
		{
			String tier = id == BEGINNER_CLUE ? "beginner" : "master";
			// the same scroll as before (another step of it was read): keep what its text said
			if (current.getItemId() == id && current.getKind() != DetectedClue.Kind.NONE)
			{
				return current;
			}
			return new DetectedClue(data == null ? DetectedClue.Kind.NO_DATA : DetectedClue.Kind.NEEDS_TEXT, tier, id, List.of(), "");
		}
		if (data == null)
		{
			return new DetectedClue(DetectedClue.Kind.NO_DATA, "", id, List.of(), "");
		}
		ClueData.Step s = data.byItem(id);
		if (s == null)
		{
			int row = clueParam.applyAsInt(id);
			s = row > 0 ? data.byRow(row) : null;
			if (s == null)
			{
				return new DetectedClue(DetectedClue.Kind.NOT_FOUND, "", id, List.of(), "item " + id
					+ (row > 0 ? ", step row " + row : ""));
			}
		}
		return new DetectedClue(DetectedClue.Kind.STEP, s.getTier(), id, List.of(s), "");
	}

	/** True for an item that is a clue scroll or a hot/cold device. */
	private boolean isClue(int id, IntUnaryOperator clueParam)
	{
		if (id <= 0)
		{
			return false;
		}
		if (id == BEGINNER_CLUE || id == MASTER_CLUE || id == BEGINNER_DEVICE || id == MASTER_DEVICE)
		{
			return true;
		}
		if (data != null && data.isClueItem(id))
		{
			return true;
		}
		return clueParam.applyAsInt(id) > 0;
	}

	private DetectedClue change(DetectedClue d)
	{
		if (d.identity().equals(current.identity()) && d.getItemId() == current.getItemId())
		{
			return null;
		}
		current = d;
		return d;
	}

	private static String shortText(String t)
	{
		String n = t.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
		return n.length() > 60 ? n.substring(0, 57) + "..." : n;
	}
}

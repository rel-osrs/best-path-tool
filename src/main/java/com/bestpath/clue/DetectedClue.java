package com.bestpath.clue;

import com.bestpath.core.data.ClueData;
import com.bestpath.route.RouteRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The clue the player carries, as far as the plugin could tell, and what the
 * sidebar says about it. Immutable; made on the client thread, shown on the
 * Swing thread, routed on the route worker.
 */
public final class DetectedClue
{
	/** How far the detection got. */
	public enum Kind
	{
		/** No clue carried. */
		NONE,
		/** The step is known (several steps only when they share the same text). */
		STEP,
		/** A hot/cold clue: one of the tier's dig spots, not known which. */
		HOT_COLD,
		/** A beginner or master clue whose text has not been read yet (one item for all its steps). */
		NEEDS_TEXT,
		/** A clue item or text that matches no step in the plugin's data. */
		NOT_FOUND,
		/** A clue item, but the plugin has no clue data (the map data is not loaded or has no clue steps). */
		NO_DATA
	}

	public static final DetectedClue NONE = new DetectedClue(Kind.NONE, "", -1, List.of(), "");

	private final Kind kind;
	private final String tier;
	private final int itemId;
	private final List<ClueData.Step> steps;
	/** What was looked up (the text read, or the item id) for "not found" messages. */
	private final String shown;

	DetectedClue(Kind kind, String tier, int itemId, List<ClueData.Step> steps, String shown)
	{
		this.kind = kind;
		this.tier = tier == null ? "" : tier;
		this.itemId = itemId;
		this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
		this.shown = shown == null ? "" : shown;
	}

	public Kind getKind()
	{
		return kind;
	}

	/** beginner ... master, quest; "" if not known. */
	public String getTier()
	{
		return tier;
	}

	/** The clue item (scroll or device) in the inventory, or -1 (found by text only). */
	public int getItemId()
	{
		return itemId;
	}

	public List<ClueData.Step> getSteps()
	{
		return steps;
	}

	/** Every place of every step, packed, without repeats, in the data's order (the step's own target first). */
	public int[] getTargets()
	{
		Set<Integer> out = new LinkedHashSet<>();
		for (ClueData.Step s : steps)
		{
			for (int i = 0; i < s.targetCount(); i++)
			{
				out.add(s.target(i));
			}
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	/** True if there is somewhere to route to. */
	public boolean isRoutable()
	{
		return (kind == Kind.STEP || kind == Kind.HOT_COLD) && getTargets().length > 0;
	}

	/** Same clue, same step(s): no new detection. */
	public String identity()
	{
		StringBuilder sb = new StringBuilder(kind.name()).append(':').append(tier).append(':');
		if (kind == Kind.HOT_COLD)
		{
			return sb.append(steps.size()).append(" spots").toString(); // every spot of the tier
		}
		for (ClueData.Step s : steps)
		{
			sb.append(s.getKey()).append(',');
		}
		return sb.toString();
	}

	/** "Cryptic clue (beginner)", "Hot/cold clue (master)", "Beginner clue", "No clue". */
	public String title()
	{
		switch (kind)
		{
			case NONE:
				return "No clue";
			case STEP:
				return typeName(steps.get(0).getType()) + " clue" + (tier.isEmpty() ? "" : " (" + tier + ")");
			case HOT_COLD:
				return "Hot/cold clue" + (tier.isEmpty() ? "" : " (" + tier + ")");
			default:
				return tier.isEmpty() ? "Clue scroll" : capital(tier) + " clue";
		}
	}

	/** The lines the sidebar shows under the title (plain language). */
	public List<String> lines()
	{
		List<String> out = new ArrayList<>();
		switch (kind)
		{
			case NONE:
				break;
			case NEEDS_TEXT:
				out.add("Read the clue scroll so Best Path can see which step it is (" + tier + " clues use one scroll for"
					+ " every step).");
				if ("beginner".equals(tier))
				{
					out.add("Map clues show a picture, not text: Best Path can't tell those apart yet.");
				}
				break;
			case NOT_FOUND:
				out.add(shown.isEmpty() ? "Best Path doesn't know this clue step." : "Best Path doesn't know this clue step ("
					+ shown + ").");
				break;
			case NO_DATA:
				out.add("The plugin has no clue data yet, so it can't route to clues.");
				break;
			case HOT_COLD:
				out.add("The dig spot isn't known: Best Path can't read the strange device's hints yet. The route goes to the"
					+ " nearest of the " + getTargets().length + " possible spots; feel the device there and follow its"
					+ " hints.");
				break;
			case STEP:
			default:
				stepLines(out);
				break;
		}
		return out;
	}

	private void stepLines(List<String> out)
	{
		ClueData.Step s = steps.get(0);
		if (!s.getText().isEmpty())
		{
			out.add(("music".equals(s.getType()) ? "Track: " : "") + "\"" + s.getText() + "\"");
		}
		if (!s.getDescription().isEmpty() && !s.getDescription().equals(s.getText()))
		{
			out.add(s.getDescription());
		}
		if (steps.size() > 1)
		{
			out.add("This text belongs to " + steps.size() + " steps; the route goes to the nearest of their places.");
		}
		int[] t = getTargets();
		if (t.length == 0)
		{
			out.add("Best Path has no place for this step, so it can't route to it.");
		}
		else if (steps.size() == 1 && t.length > 1)
		{
			out.add("kill".equals(s.getTarget())
				? "The monsters are found in " + t.length + " areas (places from the OSRS Wiki); the route goes to the nearest."
				: "This step has " + t.length + " possible places; the route goes to the nearest.");
		}
		if (s.getStash() != -1)
		{
			// no coordinates: most players can't use them
			out.add("This step has a STASH unit"
				+ " (built or not, and your outfit: on the clue's card at the end).");
		}
	}

	/** The route request for this clue (null if there is nowhere to go). */
	public RouteRequest request()
	{
		if (!isRoutable())
		{
			return null;
		}
		return RouteRequest.places("clue " + identity(), title(), lines(), getTargets());
	}

	static String typeName(String type)
	{
		switch (type)
		{
			case "fairyring":
				return "Fairy ring";
			case "falo":
				return "Falo the Bard";
			case "hotcold":
				return "Hot/cold";
			case "skillchallenge":
				return "Challenge";
			default:
				return capital(type);
		}
	}

	private static String capital(String s)
	{
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
	}

	@Override
	public String toString()
	{
		return identity();
	}
}

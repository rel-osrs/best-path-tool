package com.bestpath.tool.transport;

import java.util.Collections;
import java.util.List;

/**
 * A suggested destination for a {@link TransportCandidate}. Only a
 * suggestion: it is shown with its rule and evidence and is never added to
 * the data unless a person accepts it. Immutable.
 */
public final class Suggestion
{
	/** The rule that produced the suggestion; lower ordinal = stronger rule. */
	public enum Rule
	{
		STACKED("Matching object directly above or below"),
		STACKED_NEAR("Matching object above or below, a few tiles away"),
		UNDERGROUND("Matching object about 6,400 tiles north or south (surface / underground)"),
		CACHE_TABLE("Destination stated in a cache table"),
		ACROSS("Over the obstacle to the first walkable tile beyond it"),
		PLANE_PARTNER("Shortcut up to another plane: the next shortcut there, a few tiles away"),
		OTHER_PLANE("Shortcut to another plane: the first walkable tile there beyond the obstacle");

		private final String description;

		Rule(String description)
		{
			this.description = description;
		}

		public String getDescription()
		{
			return description;
		}
	}

	private final int destX;
	private final int destY;
	private final int destPlane;
	private final Rule rule;
	private final String partner;
	private final List<String> evidence;
	private final int score;
	/** Where the player stands to use it {x, y, plane}, or null (then: next to the object). */
	private final int[] start;

	public Suggestion(int destX, int destY, int destPlane, Rule rule, String partner, List<String> evidence, int score)
	{
		this(null, destX, destY, destPlane, rule, partner, evidence, score);
	}

	public Suggestion(int[] start, int destX, int destY, int destPlane, Rule rule, String partner, List<String> evidence, int score)
	{
		this.start = start == null ? null : start.clone();
		this.destX = destX;
		this.destY = destY;
		this.destPlane = destPlane;
		this.rule = rule;
		this.partner = partner;
		this.evidence = Collections.unmodifiableList(evidence);
		this.score = score;
	}

	/** Start tile {x, y, plane} if the rule knows it (traversals: the near side), else null. */
	public int[] getStart()
	{
		return start == null ? null : start.clone();
	}

	/** Suggested landing tile. */
	public int getDestX()
	{
		return destX;
	}

	public int getDestY()
	{
		return destY;
	}

	public int getDestPlane()
	{
		return destPlane;
	}

	public Rule getRule()
	{
		return rule;
	}

	/** Id of the matching object at the other end, or null. */
	public String getPartner()
	{
		return partner;
	}

	/** Human-readable reasons, e.g. "reverse direction also found". */
	public List<String> getEvidence()
	{
		return evidence;
	}

	/** Ranking only (higher first). Never used to accept anything. */
	public int getScore()
	{
		return score;
	}
}

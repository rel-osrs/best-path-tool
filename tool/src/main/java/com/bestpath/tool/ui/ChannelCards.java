package com.bestpath.tool.ui;

import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.SeaArea;
import com.bestpath.core.sea.SeaChannelsBuilder;
import com.bestpath.core.sea.SeaGraph;
import com.bestpath.tool.edit.Edit;
import java.util.ArrayList;
import java.util.List;

/**
 * The sea channel cards: one Keep / Delete card per channel, its way drawn on the map. The answer is saved as a
 * {@code SEA_CHANNEL} change.
 */
final class ChannelCards
{
	/** Card ids of sea channels start with this, then the channel's key. */
	static final String ID = "ch-";

	private ChannelCards()
	{
	}

	/** The card for channel {@code c}: it points at the channel's middle point. */
	static QuestionCards.Question question(SeaGraph g, int c)
	{
		int[] p = g.channelPoints(c);
		String key = SeaChannelsBuilder.key(p);
		int mid = p[p.length / 2];
		QuestionCards.Question q = new QuestionCards.Question();
		q.id = ID + key;
		q.action = QuestionCards.Action.CHANNEL;
		q.x = mid >>> 14;
		q.y = mid & 0x3FFF;
		q.plane = 0;
		q.channel = key;
		q.points = p;
		q.text = text(g, c);
		return q;
	}

	/** Region by region (north first, then west to east), then within the region: neighbouring cards come one after the other. */
	static int mapOrder(int[] p)
	{
		int mid = p[p.length / 2];
		int x = mid >>> 14;
		int y = mid & 0x3FFF;
		return ((255 - (y >> 6)) << 8 | (x >> 6)) << 12 | (x & 63) << 6 | (63 - (y & 63));
	}

	/** The card's words: where, who fits, how narrow, what water. */
	private static String text(SeaGraph g, int c)
	{
		int[] p = g.channelPoints(c);
		boolean every = g.channelClearance(c) >= BoatType.SLOOP.hardClearance();
		List<String> areas = new ArrayList<>();
		for (SeaArea a : SeaArea.values())
		{
			if ((g.channelAreas(c) & 1 << a.ordinal()) != 0)
			{
				areas.add(a.getLabel() + (a.getNeeds() != null ? " (" + a.getNeeds() + ")" : ""));
			}
		}
		boolean crystal = (g.channelAreas(c) & 1 << SeaArea.CRYSTALLINE.ordinal()) != 0;
		String who = every ? "every boat fits"
			: crystal ? "only a raft fits, and a raft can not sail Crystalline waters (no keel): no boat can use it"
			: "only a raft fits";
		return String.format(java.util.Locale.ROOT, "Sea channel from %d, %d to %d, %d (%.0f tiles; %s; narrowest %d; %s)."
				+ " The line in this card's colour is the way boats would sail. Keep it, or delete it?",
			p[0] >>> 14, p[0] & 0x3FFF, p[p.length - 1] >>> 14, p[p.length - 1] & 0x3FFF, g.channelLength(c), who,
			g.channelClearance(c), String.join(", ", areas));
	}

	/** The change a channel card's answer makes ({@code word} KEEP or DELETE): a SEA_CHANNEL change either way, so the card is not made again. */
	static Edit answer(QuestionCards.Question q, String word)
	{
		Edit e = new Edit();
		e.type = Edit.Type.SEA_CHANNEL;
		e.target = q.channel;
		e.on = word.equals("DELETE");
		e.x = q.x;
		e.y = q.y;
		e.plane = 0;
		e.note = e.on ? "Card: deleted" : "Card: kept (approved)";
		return e;
	}
}

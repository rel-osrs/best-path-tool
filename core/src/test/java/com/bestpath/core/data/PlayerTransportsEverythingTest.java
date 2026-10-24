package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.PlayerStateDiagnostics;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Skill;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * "No route" explained: {@link PlayerTransports#everything} makes every transport whatever the
 * player has, and those it marks usable now are exactly the route search's ({@link PlayerTransports#build}), with the
 * same ids, ends, order and ticks (tick changes too). Without this the explanation could name a way the routes don't have.
 */
class PlayerTransportsEverythingTest
{
	static TransportLine line(String id, TransportLine.Kind kind, int sx, int sy, int ex, int ey, String req, String back,
		String network, String teleportCategory, boolean cooldown, boolean questOnly)
	{
		return TransportLine.builder(id, kind).name("Name " + id).category("CUSTOM").start(sx, sy, 0).end(ex, ey, 0)
			.ticks(3).questOnly(questOnly).cooldown(cooldown).network(network).requirements(req)
			.requirementsBack(back).teleportCategory(teleportCategory).build();
	}

	/** One of each kind and each way two directions can differ; one quest-only; x >= 3232 is members land. */
	static List<TransportLine> lines()
	{
		List<TransportLine> l = new ArrayList<>();
		l.add(line("free-way", TransportLine.Kind.ONE_WAY, 3201, 3201, 3210, 3210, "", null, "", "", false, false));
		l.add(line("agility-way", TransportLine.Kind.ONE_WAY, 3202, 3201, 3211, 3210, "Agility 30", null, "", "", false, false));
		l.add(line("both", TransportLine.Kind.TWO_WAY, 3203, 3201, 3212, 3210, "", null, "", "", false, false));
		l.add(line("back-agility", TransportLine.Kind.TWO_WAY, 3204, 3201, 3213, 3210, "", "Agility 40", "", "", false, false));
		l.add(line("back-coins", TransportLine.Kind.TWO_WAY, 3205, 3201, 3214, 3210, "", "coins 50", "", "", false, false));
		l.add(line("both-quest", TransportLine.Kind.TWO_WAY, 3206, 3201, 3215, 3210, "quest Dragon Slayer", null, "", "", false, false));
		l.add(line("to-members", TransportLine.Kind.ONE_WAY, 3207, 3201, 3240, 3210, "", null, "", "", false, false));
		l.add(line("quest-only", TransportLine.Kind.ONE_WAY, 3208, 3201, 3216, 3210, "", null, "", "", false, true));
		l.add(line("tele", TransportLine.Kind.TELEPORT, 0, 0, 3220, 3220, "Magic 25", null, "", "spell", false, false));
		l.add(line("home", TransportLine.Kind.TELEPORT, 0, 0, 3221, 3221, "", null, "", "home teleport", true, false));
		l.add(line("ring-a", TransportLine.Kind.NETWORK, 0, 0, 3201, 3230, "", null, "Rings", "", false, false));
		l.add(line("ring-b", TransportLine.Kind.NETWORK, 0, 0, 3210, 3230, "quest Fairytale", null, "Rings", "", false, false));
		l.add(line("ring-c", TransportLine.Kind.NETWORK, 0, 0, 3220, 3230, "coins 10", null, "Rings", "", false, false));
		return l;
	}

	static final FreeLand FREE_LAND = new FreeLand.Builder().set(3200, 3200, 3231, 3263, true).build();

	static List<PlayerState> players()
	{
		return List.of(PlayerStateBuilder.fresh(), PlayerStateBuilder.maxed(),
			new PlayerState.Builder("Agile banker").members(true).level(Skill.AGILITY, 45).bank(PlayerState.COINS, 1000)
				.quest("Fairytale", QuestState.DONE).build(),
			new PlayerState.Builder("Rich, home teleport used").members(true).carry(PlayerState.COINS, 1000).level(Skill.MAGIC, 30)
				.coolingDown(Cooldown.HOME, 99).build(),
			new PlayerState.Builder("Free").members(false).carry(PlayerState.COINS, 1000).build());
	}

	@Test
	void theTransportsUsableNowAreExactlyTheRouteSearchs()
	{
		PlayerTransports pt = new PlayerTransports(lines(), Tables.empty());
		PlayerTransports.TickChanges changes = new PlayerTransports.TickChanges()
		{
			@Override
			public int transport(String id)
			{
				return id.equals("both") ? 7 : id.startsWith("Rings:") ? -1 : 0;
			}

			@Override
			public int teleportCategory(String category)
			{
				return 0;
			}

			@Override
			public int network(String network)
			{
				return 0;
			}
		};
		for (PlayerState p : players())
		{
			FreeLand land = p.isMembers() ? null : FREE_LAND;
			for (PlayerTransports.TickChanges c : new PlayerTransports.TickChanges[]{null, changes})
			{
				List<Transport> routes = pt.build(p, 0, land, c).getTransports();
				List<Transport> now = new ArrayList<>();
				for (PlayerTransports.Candidate k : pt.everything(p, land, c).getCandidates())
				{
					if (k.isUsableNow())
					{
						now.add(k.getTransport());
					}
				}
				assertEquals(describe(routes), describe(now), PlayerStateDiagnostics.name(p) + (c == null ? "" : ", tick changes"));
			}
		}
	}

	@Test
	void everyTransportIsMadeWithWhyItIsOut()
	{
		PlayerTransports pt = new PlayerTransports(lines(), Tables.empty());
		PlayerState fresh = new PlayerState.Builder("Fresh, home teleport used").members(true).coolingDown(Cooldown.HOME, 0).build();
		List<String> ids = new ArrayList<>();
		for (PlayerTransports.Candidate c : pt.everything(fresh, null, null).getCandidates())
		{
			ids.add(c.getTransport().getId() + (c.isUsableNow() ? "" : " out:" + c.getOutcome() + (c.isCoolingDown() ? ",cooling" : "")));
		}
		assertTrue(ids.contains("agility-way out:NOT_MET"), ids.toString());
		// the two ways differ: split as the route search splits them
		assertTrue(ids.contains("back-agility") && ids.contains("back-agility:back out:NOT_MET"), ids.toString());
		assertTrue(ids.contains("back-coins") && ids.contains("back-coins:back out:NOT_MET"), ids.toString());
		// the same both ways (not met both ways): one transport
		assertTrue(ids.contains("both-quest out:NOT_MET") && !ids.contains("both-quest:back out:NOT_MET"), ids.toString());
		assertTrue(ids.contains("home out:MET,cooling"), ids.toString());
		assertTrue(ids.contains("tele out:NOT_MET"), ids.toString());
		assertFalse(ids.toString().contains("quest-only"), "quest-only transports are never made");
		// rides: every stop to every other; out when either stop is out
		assertTrue(ids.contains("Rings:Name ring-a>Name ring-b out:NOT_MET"), ids.toString());
		assertTrue(ids.contains("Rings:Name ring-b>Name ring-a out:NOT_MET"), ids.toString());
		assertTrue(ids.contains("Rings:Name ring-a>Name ring-c out:NOT_MET"), ids.toString());
		PlayerTransports.Candidate ride = null;
		for (PlayerTransports.Candidate c : pt.everything(fresh, null, null).getCandidates())
		{
			ride = c.getTransport().getId().equals("Rings:Name ring-a>Name ring-b") ? c : ride;
		}
		assertEquals("ring-b", ride.getLine().getId(), "a ride's line is the stop it goes to");
		assertEquals("ring-a", ride.getFromStop().getId());

		// a free world: only free land to free land
		List<String> free = new ArrayList<>();
		for (PlayerTransports.Candidate c : pt.everything(new PlayerState.Builder("Free").members(false).build(), FREE_LAND, null)
			.getCandidates())
		{
			free.add(c.getTransport().getId());
		}
		assertFalse(free.contains("to-members"));
		assertTrue(free.contains("agility-way"));
		// met only with the bank
		PlayerState banker = new PlayerState.Builder("Banker").members(true).bank(PlayerState.COINS, 1000).build();
		for (PlayerTransports.Candidate c : pt.everything(banker, null, null).getCandidates())
		{
			if (c.getTransport().getId().equals("back-coins:back"))
			{
				assertEquals(Check.Outcome.NEEDS_BANK, c.getOutcome());
				assertFalse(c.isUsableNow());
			}
		}
	}

	private static String describe(List<Transport> list)
	{
		StringBuilder sb = new StringBuilder();
		for (Transport t : list)
		{
			sb.append(t.getId()).append(' ').append(t.hasStart() ? t.getStartX() + "," + t.getStartY() + "," + t.getStartPlane() : "-")
				.append('>').append(t.getEndX()).append(',').append(t.getEndY()).append(',').append(t.getEndPlane())
				.append(t.isTwoWay() ? " two-way" : "").append(t.isUsedFromBeside() ? " beside" : "").append(" cost ").append(t.cost(2))
				.append('\n');
		}
		return sb.toString();
	}
}

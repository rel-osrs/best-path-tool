package com.bestpath.tool.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.Skill;
import com.bestpath.tool.edit.TransportRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteRequirementsTest
{
	private static TransportRecord rec(String id, TransportRecord.Kind kind, String needs)
	{
		TransportRecord r = new TransportRecord();
		r.id = id;
		r.name = id;
		r.kind = kind;
		r.startX = 3200;
		r.startY = 3200;
		r.endX = 3205;
		r.endY = 3200;
		r.ticks = 4;
		r.requirements = needs;
		return r;
	}

	private static List<Transport> build(List<TransportRecord> records, RequirementFilter needs)
	{
		return RouteTransports.build(records, Collections.emptyList(), null, false, c -> true, id -> true, (c, x, y) -> null, needs);
	}

	private static Transport find(List<Transport> list, String id)
	{
		for (Transport t : list)
		{
			if (t.getId().equals(id))
			{
				return t;
			}
		}
		return null;
	}

	@Test
	void requirementsDecideWhatAPlayerCanUse()
	{
		List<TransportRecord> records = new ArrayList<>();
		records.add(rec("wall", TransportRecord.Kind.TWO_WAY, "Agility 50"));
		TransportRecord vines = rec("vines", TransportRecord.Kind.TWO_WAY, "item 1351; Woodcutting 22");
		vines.requirementsBack = "";
		records.add(vines);
		TransportRecord battle = rec("battle", TransportRecord.Kind.ONE_WAY, "");
		battle.questOnly = true;
		records.add(battle);
		records.add(rec("ship", TransportRecord.Kind.ONE_WAY, "coins 30"));
		records.add(rec("tele", TransportRecord.Kind.TELEPORT, "Magic 25"));
		records.add(rec("plain", TransportRecord.Kind.TWO_WAY, ""));

		// everything (as before requirements existed)
		assertEquals(6, build(records, null).size());

		RequirementFilter maxed = new RequirementFilter(PlayerStateBuilder.maxed());
		List<Transport> m = build(records, maxed);
		assertEquals(5, m.size());
		assertTrue(find(m, "wall").isTwoWay());
		assertTrue(find(m, "vines").isTwoWay());
		assertEquals(null, find(m, "battle"));
		assertEquals("for Maxed: 0 transports left out (requirements), 1 quest only", maxed.summary());

		PlayerState fresh = new PlayerState.Builder("Fresh").members(true).bank(PlayerState.COINS, 100).build();
		RequirementFilter f = new RequirementFilter(fresh);
		List<Transport> l = build(records, f);
		assertEquals(null, find(l, "wall"));
		assertEquals(null, find(l, "tele"));
		assertEquals(null, find(l, "vines"));
		Transport back = find(l, "vines:back");
		assertFalse(back.isTwoWay());
		assertEquals(3205, back.getStartX());
		assertEquals(3200, back.getEndX());
		// the coins are only in the bank: usable after a bank visit only (a route may begin at a bank)
		assertEquals("ship", find(l, "ship").getName());
		assertEquals(List.of("coins 30"), f.itemsFromBank(find(l, "ship")));
		assertEquals(null, find(f.carried(l), "ship"), "not with what the player carries");
		assertEquals(l.size() - 1, f.carried(l).size());
		assertTrue(find(l, "plain").isTwoWay());
		assertEquals("for Fresh: 2 transports left out (requirements), 1 quest only, 1 usable one way only", f.summary());

		PlayerState cutter = new PlayerState.Builder("Cutter").level(Skill.WOODCUTTING, 22).carry(1351, 1).build();
		assertTrue(find(build(records, new RequirementFilter(cutter)), "vines").isTwoWay());
	}

	@Test
	void anUnreadableLineCountsAsMetAndIsReported()
	{
		RequirementFilter f = new RequirementFilter(PlayerStateBuilder.fresh());
		List<Transport> l = build(List.of(rec("odd", TransportRecord.Kind.ONE_WAY, "Agilty 5")), f);
		assertEquals(1, l.size());
		assertTrue(f.summary().contains("1 unreadable requirement line ignored"), f.summary());
	}

	@Test
	void teleportsUseTheirCategoryTicksAndWildernessLimit()
	{
		TransportRecord glory = rec("glory", TransportRecord.Kind.TELEPORT, "");
		glory.ticks = 9;
		glory.teleportCategory = "jewellery (consumable)";
		glory.wildernessLimit = 30;
		TransportRecord spell = rec("spell", TransportRecord.Kind.TELEPORT, "");
		spell.ticks = 7;
		spell.wildernessLimit = 20;
		TransportRecord chronicle = rec("chronicle", TransportRecord.Kind.TELEPORT, "");
		chronicle.wildernessLimit = 0;
		List<TransportRecord> records = List.of(glory, spell, chronicle);

		int jewellery = com.bestpath.tool.req.TeleportCategories.get("jewellery (consumable)").ticks;
		List<Transport> outside = build(records, new RequirementFilter(PlayerStateBuilder.maxed(), 0));
		assertEquals(jewellery, find(outside, "glory").getTicks());
		assertEquals(7, find(outside, "spell").getTicks());
		assertEquals(3, outside.size());

		RequirementFilter deep = new RequirementFilter(PlayerStateBuilder.maxed(), 25);
		List<Transport> at25 = build(records, deep);
		assertEquals(1, at25.size());
		assertEquals("glory", at25.get(0).getId());
		assertTrue(deep.summary().contains("2 teleports too deep in the Wilderness"), deep.summary());
		assertEquals(2, build(records, new RequirementFilter(PlayerStateBuilder.maxed(), 5)).size());
	}

	@Test
	void networkRidesNeedTheNetworkAndCostTheirExtra(@org.junit.jupiter.api.io.TempDir java.io.File dir) throws Exception
	{
		java.io.File f = new java.io.File(dir, "networks.tsv");
		java.nio.file.Files.write(f.toPath(), "# test\nCharter ship\t3\tcoins 10\tships\nFairy ring\t2\tAgility 50\trings\n"
			.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		com.bestpath.tool.req.Networks.useFile(f);
		try
		{
			TransportRecord ship = rec("ship", TransportRecord.Kind.ONE_WAY, "Sailing 5");
			ship.ticks = 6;
			ship.network = "charter ship";
			TransportRecord a = rec("a", TransportRecord.Kind.NETWORK, "");
			a.network = "Fairy ring";
			a.ticks = 4;
			TransportRecord b = rec("b", TransportRecord.Kind.NETWORK, "");
			b.network = "Fairy ring";
			b.ticks = 4;
			b.endX = 3300;
			List<TransportRecord> records = List.of(ship, a, b);

			List<Transport> all = build(records, new RequirementFilter(PlayerStateBuilder.maxed()));
			assertEquals(9, find(all, "ship").getTicks());
			assertEquals(3, all.size()); // ship, a>b, b>a
			for (Transport t : all)
			{
				if (!t.getId().equals("ship"))
				{
					assertEquals(6, t.getTicks());
				}
			}

			// the network's own requirement counts: no coins, no Agility -> nothing
			PlayerState sailor = new PlayerState.Builder("Sailor").members(true).level(Skill.SAILING, 5).build();
			assertEquals(0, build(records, new RequirementFilter(sailor)).size());
			PlayerState rich = new PlayerState.Builder("Rich").members(true).level(Skill.SAILING, 5).carry(PlayerState.COINS, 10).build();
			assertEquals(1, build(records, new RequirementFilter(rich)).size());

			com.bestpath.tool.req.Networks.put(new com.bestpath.tool.req.Networks.Network("Canoe", 1, "", "canoes"));
			assertEquals(3, com.bestpath.tool.req.Networks.all().size());
			assertEquals("coins 10; Sailing 5", com.bestpath.tool.req.Networks.requirement("Charter ship", "Sailing 5"));
			assertEquals("Sailing 5", com.bestpath.tool.req.Networks.requirement("", "Sailing 5"));
		}
		finally
		{
			com.bestpath.tool.req.Networks.useFile(com.bestpath.tool.req.Networks.FILE);
		}
	}
}

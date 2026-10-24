package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.path.Transport;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Long-cooldown teleports (usable so long as the plugin tracks whether they are available): which
 * teleports share which cooldown, the game's own "cooling down" test, and that a teleport whose cooldown runs is
 * left out of the player's transports (the same rule as the Data Tool's requirement filter).
 */
class CooldownTransportsTest
{
	private static TransportLine teleport(String id, String teleportCategory, boolean cooldown)
	{
		return TransportLine.builder(id, TransportLine.Kind.TELEPORT).name(id).category("TELEPORT")
			.end(3222, 3218, 0).ticks(24).cooldown(cooldown).teleportCategory(teleportCategory).build();
	}

	private static List<String> ids(PlayerTransports.Result r)
	{
		List<String> out = new ArrayList<>();
		for (Transport t : r.getTransports())
		{
			out.add(t.getId());
		}
		return out;
	}

	@Test
	void theCooldownComesFromTheFlagAndTheTeleportCategory()
	{
		assertEquals(Cooldown.HOME, Cooldown.of(true, "home teleport"));
		assertEquals(Cooldown.MINIGAME, Cooldown.of(true, " Grouping Teleport "));
		assertNull(Cooldown.of(false, "home teleport"), "no long-cooldown flag: no cooldown");
		assertNull(Cooldown.of(true, "spell"), "a flagged teleport of another category: no known cooldown var");
		assertNull(Cooldown.of(true, null));
		assertEquals(892, Cooldown.HOME.getVarp());
		assertEquals(30, Cooldown.HOME.getMinutes());
		assertEquals(888, Cooldown.MINIGAME.getVarp());
		assertEquals(20, Cooldown.MINIGAME.getMinutes());
	}

	@Test
	void coolingDownIsTheGamesOwnTest()
	{
		int used = 29_000_000; // a minute since 1970
		assertFalse(Cooldown.HOME.coolingDown(0, used), "0 = never used");
		assertTrue(Cooldown.HOME.coolingDown(used, used));
		assertTrue(Cooldown.HOME.coolingDown(used, used + 29), "29 minutes later: still cooling down");
		assertFalse(Cooldown.HOME.coolingDown(used, used + 30), "now - last < 30 is the game's test");
		assertTrue(Cooldown.MINIGAME.coolingDown(used, used + 19));
		assertFalse(Cooldown.MINIGAME.coolingDown(used, used + 20));
		assertEquals(used + 30L, Cooldown.HOME.readyAt(used));
	}

	@Test
	void aTeleportCoolingDownIsLeftOut()
	{
		List<TransportLine> lines = List.of(teleport("home", "home teleport", true),
			teleport("castle wars", "grouping teleport", true),
			teleport("varrock", "spell", false),
			teleport("odd", "spell", true));
		PlayerTransports pt = new PlayerTransports(lines, Tables.parse(List.of(), List.of(), List.of(), List.of(), List.of()));

		PlayerState ready = new PlayerState.Builder("Ready").members(true).build();
		assertTrue(ready.cooldownReady(Cooldown.HOME) && ready.cooldownReady(Cooldown.MINIGAME), "ready unless told");
		assertEquals(List.of("home", "castle wars", "varrock", "odd"), ids(pt.build(ready, 0, null)));

		PlayerState home = new PlayerState.Builder("Home used").members(true).coolingDown(Cooldown.HOME, 29_000_030L).build();
		PlayerTransports.Result r = pt.build(home, 0, null);
		assertEquals(List.of("castle wars", "varrock", "odd"), ids(r));
		assertEquals(1, r.leftOut);
		assertEquals(29_000_030L, home.getCoolingDown().get(Cooldown.HOME));

		PlayerState both = new PlayerState.Builder("Both used").members(true).coolingDown(Cooldown.HOME, 0)
			.coolingDown(Cooldown.MINIGAME, 0).build();
		assertEquals(List.of("varrock", "odd"), ids(pt.build(both, 0, null)),
			"a flagged teleport without a known cooldown var stays as before");
		assertTrue(PlayerTransports.coolingDown(lines.get(1), both));
		assertFalse(PlayerTransports.coolingDown(lines.get(3), both));

		// "every unlock" does not make a cooldown ready
		PlayerState maxedUsed = PlayerStateBuilder.allItems(new PlayerState.Builder("Maxed, home used").members(true).allUnlocked(true), true)
			.coolingDown(Cooldown.HOME, 0).build();
		assertFalse(ids(pt.build(maxedUsed, 0, null)).contains("home"));
		assertTrue(ids(pt.build(PlayerStateBuilder.maxed(), 0, null)).contains("home"));
	}
}

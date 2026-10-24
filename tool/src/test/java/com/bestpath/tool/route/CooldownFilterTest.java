package com.bestpath.tool.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.path.Transport;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.req.TestPlayer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Long-cooldown teleports in the Data Tool: test players count them as ready, and a
 * test player with "Cooldowns used" leaves out the home and minigame teleports, the same rule as the plugin's
 * PlayerTransports (so exportPlugin can compare the two with a cooldown running).
 */
class CooldownFilterTest
{
	private static TransportRecord teleport(String id, String teleportCategory, boolean cooldown)
	{
		TransportRecord r = new TransportRecord();
		r.id = id;
		r.name = id;
		r.kind = TransportRecord.Kind.TELEPORT;
		r.endX = 3222;
		r.endY = 3218;
		r.ticks = 24;
		r.teleportCategory = teleportCategory;
		r.cooldown = cooldown;
		return r;
	}

	private static List<String> ids(RequirementFilter needs, TransportRecord... records)
	{
		List<String> out = new ArrayList<>();
		for (Transport t : RouteTransports.build(List.of(records), Collections.emptyList(), null, false, c -> true,
			id -> true, (c, x, y) -> null, needs))
		{
			out.add(t.getId());
		}
		return out;
	}

	@Test
	void cooldownsUsedLeavesOutTheHomeAndMinigameTeleports()
	{
		TransportRecord home = teleport("home", "home teleport", true);
		TransportRecord minigame = teleport("castle wars", "grouping teleport", true);
		TransportRecord spell = teleport("varrock", "spell", false);

		TestPlayer p = new TestPlayer();
		PlayerState ready = p.toState();
		assertTrue(ready.cooldownReady(Cooldown.HOME) && ready.cooldownReady(Cooldown.MINIGAME), "ready by default");
		RequirementFilter f = new RequirementFilter(ready);
		assertEquals(List.of("home", "castle wars", "varrock"), ids(f, home, minigame, spell));
		assertFalse(f.summary().contains("cooling down"), f.summary());

		p.cooldownsUsed = true;
		PlayerState used = p.copy().toState();
		assertFalse(used.cooldownReady(Cooldown.HOME));
		assertFalse(used.cooldownReady(Cooldown.MINIGAME));
		RequirementFilter g = new RequirementFilter(used);
		assertEquals(List.of("varrock"), ids(g, home, minigame, spell));
		assertTrue(g.summary().contains("2 teleports cooling down"), g.summary());

		// Maxed and the fresh account stay ready
		assertTrue(TestPlayer.maxed().toState().cooldownReady(Cooldown.HOME));
		assertTrue(TestPlayer.fresh().toState().cooldownReady(Cooldown.MINIGAME));
	}
}

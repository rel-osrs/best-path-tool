package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Skill;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * The transports for a player are worked out again only when the player state changed: the same
 * state gets the very same transports, so the searches' indexes
 * of them are used again; any change works them out afresh.
 */
class PlayerTransportsReuseTest
{
	private static final TransportLine HOME = TransportLine.builder("t-home", TransportLine.Kind.TELEPORT)
		.name("Home").category("TELEPORT").end(3222, 3218, 0).ticks(24).cooldown(true)
		.teleportCategory("home teleport").build();
	private static final TransportLine GATE = TransportLine.builder("t-gate", TransportLine.Kind.TWO_WAY)
		.name("Gate").category("DOOR").start(3200, 3200, 0).end(3201, 3200, 0).ticks(2).requirements("Agility 10")
		.build();

	private static PlayerState player(int agility)
	{
		return new PlayerState.Builder("P").members(true).level(Skill.AGILITY, agility).build();
	}

	@Test
	void theSameStateGetsTheSameTransports()
	{
		PlayerTransports t = new PlayerTransports(List.of(HOME, GATE), Tables.empty());
		PlayerTransports.Result a = t.build(player(10), 0, null);
		assertSame(a, t.build(player(10), 0, null), "an equal state (another object, as each snapshot is): the same result");
		assertEquals(2, a.getTransports().size());
	}

	@Test
	void anyChangeWorksThemOutAgain()
	{
		PlayerTransports t = new PlayerTransports(List.of(HOME, GATE), Tables.empty());
		PlayerTransports.Result a = t.build(player(10), 0, null);
		PlayerTransports.Result lower = t.build(player(9), 0, null);
		assertNotSame(a, lower);
		assertEquals(1, lower.getTransports().size(), "Agility 9: no gate");
		PlayerState cooling = new PlayerState.Builder("P").members(true).level(Skill.AGILITY, 10).coolingDown(Cooldown.HOME, 0).build();
		assertEquals(List.of("t-gate"), List.of(t.build(cooling, 0, null).getTransports().get(0).getId()), "the home teleport cools down");
		assertNotSame(t.build(player(10), 0, null), t.build(player(10), 25, null), "another Wilderness level");
	}

	@Test
	void theSameTickChangesGiveTheSameTransports()
	{
		PlayerTransports t = new PlayerTransports(List.of(HOME, GATE), Tables.empty());
		PlayerTransports.Result a = build(t, player(10), 0, null, id -> id.equals("t-gate") ? 3 : 0);
		assertSame(a, build(t, player(10), 0, null, id -> id.equals("t-gate") ? 3 : 0));
		PlayerTransports.Result b = build(t, player(10), 0, null, id -> id.equals("t-gate") ? 4 : 0);
		assertNotSame(a, b);
		assertEquals(6, b.getTransports().get(1).cost(0), "the gate's 2 ticks plus 4");
	}

	/** The transports with the player's own tick changes by transport id (null: none). */
	private static PlayerTransports.Result build(PlayerTransports t, PlayerState player, int wilderness, FreeLand land,
		ToIntFunction<String> changes)
	{
		PlayerTransports.Result r = t.build(player, wilderness, land);
		return changes == null ? r : r.withTickChanges(changes);
	}
}

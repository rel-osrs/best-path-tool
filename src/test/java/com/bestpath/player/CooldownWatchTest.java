package com.bestpath.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Home and minigame teleports in the plugin (used only while ready), with made-up game values: the
 * player snapshot says ready or "cooling down until", and {@link CooldownWatch} notices the moment one turns ready
 * again (the game's minute clock, or the computer's clock when that one stops) without polling.
 */
public class CooldownWatchTest
{
	/** A minute since 1970 (some day in 2025), and that minute in ms on the computer's clock. */
	private static final int USED = 29_000_000;
	private static final long MS = 60_000L;

	private static long clockAt(long minute)
	{
		return minute * MS + 30_000L; // half a minute into it
	}

	/** True if this cooldown was cooling down at the watch's last decision. */
	private static boolean coolingDown(CooldownWatch w, Cooldown c)
	{
		return (w.cooling & 1 << c.ordinal()) != 0;
	}

	private static PlayerState state(int homeUsed, int minigameUsed, int gameMinute, long clock)
	{
		GameReading r = new GameReading();
		r.cooldownLastUse[Cooldown.HOME.ordinal()] = homeUsed;
		r.cooldownLastUse[Cooldown.MINIGAME.ordinal()] = minigameUsed;
		r.gameMinute = gameMinute;
		r.clockMillis = clock;
		return SnapshotMapper.toSnapshot(r).getState();
	}

	@Test
	public void theSnapshotSaysReadyOrCoolingDownUntil()
	{
		// never used: ready
		PlayerState never = state(0, 0, USED, clockAt(USED));
		assertTrue(never.cooldownReady(Cooldown.HOME) && never.cooldownReady(Cooldown.MINIGAME));
		assertTrue(never.getCoolingDown().isEmpty());

		// home used 10 minutes ago, minigame 25 minutes ago: home cooling down until USED + 30, minigame ready
		PlayerState s = state(USED - 10, USED - 25, USED, clockAt(USED));
		assertFalse(s.cooldownReady(Cooldown.HOME));
		assertEquals(Long.valueOf(USED - 10 + 30), s.getCoolingDown().get(Cooldown.HOME));
		assertTrue(s.cooldownReady(Cooldown.MINIGAME));

		// the game's test exactly: 29 minutes after = cooling down, 30 = ready
		assertFalse(state(USED - 29, 0, USED, clockAt(USED)).cooldownReady(Cooldown.HOME));
		assertTrue(state(USED - 30, 0, USED, clockAt(USED)).cooldownReady(Cooldown.HOME));
		assertFalse(state(0, USED - 19, USED, clockAt(USED)).cooldownReady(Cooldown.MINIGAME));
		assertTrue(state(0, USED - 20, USED, clockAt(USED)).cooldownReady(Cooldown.MINIGAME));
	}

	@Test
	public void theGamesClockCountsAndTheComputersClockIsTheBackstop()
	{
		assertEquals(USED, CooldownWatch.nowMinute(USED, clockAt(USED)));
		assertEquals("a minute ahead: still the game's clock", USED, CooldownWatch.nowMinute(USED, clockAt(USED + 1)));
		assertEquals("the game's clock stopped", USED + 4, CooldownWatch.nowMinute(USED, clockAt(USED + 5)));
		assertEquals("the game's clock not sent", USED - 1, CooldownWatch.nowMinute(0, clockAt(USED)));
		// the computer's clock behind: the game's own clock decides, on time
		assertTrue(state(USED - 30, 0, USED, clockAt(USED - 3)).cooldownReady(Cooldown.HOME));
	}

	@Test
	public void becomesReadyOnTheGamesMinuteOnceThenQuiet()
	{
		CooldownWatch w = new CooldownWatch();
		assertFalse(w.read(new int[]{0, 0}, USED, clockAt(USED)));
		assertEquals(Long.MAX_VALUE, w.lookAgainAt);

		// the player casts the home teleport: it starts cooling down (a change)
		assertTrue(w.lastUse(Cooldown.HOME, USED, clockAt(USED)));
		assertTrue(coolingDown(w, Cooldown.HOME));
		assertFalse(coolingDown(w, Cooldown.MINIGAME));
		assertEquals((USED + 31) * MS, w.lookAgainAt);

		// the game's minute clock ticks on: nothing to say until the 30th minute
		for (int m = 1; m < 30; m++)
		{
			assertFalse("minute " + m, w.gameMinute(USED + m, clockAt(USED + m)));
			assertFalse(w.tick(clockAt(USED + m)));
		}
		assertTrue("ready again: say it once", w.gameMinute(USED + 30, clockAt(USED + 30)));
		assertFalse(coolingDown(w, Cooldown.HOME));
		assertEquals(Long.MAX_VALUE, w.lookAgainAt);
		assertFalse(w.gameMinute(USED + 31, clockAt(USED + 31)));
		assertFalse(w.tick(clockAt(USED + 40)));
	}

	@Test
	public void becomesReadyOnTheComputersClockWhenTheGamesClockStops()
	{
		CooldownWatch w = new CooldownWatch();
		w.read(new int[]{0, USED}, USED, clockAt(USED)); // minigame teleport just used, game clock at USED
		assertTrue(coolingDown(w, Cooldown.MINIGAME));
		// the game never sends its minute clock again; ticks pass on the computer's clock
		long ready = w.lookAgainAt;
		assertEquals((USED + 21) * MS, ready);
		assertFalse(w.tick(ready - 1));
		assertTrue("one minute late at most", w.tick(ready));
		assertFalse(coolingDown(w, Cooldown.MINIGAME));
		assertFalse(w.tick(ready + 600));
	}

	@Test
	public void bothCooldownsAndAnotherAccount()
	{
		CooldownWatch w = new CooldownWatch();
		w.read(new int[]{USED, USED + 5}, USED + 5, clockAt(USED + 5)); // home at USED, minigame at USED + 5
		assertTrue(coolingDown(w, Cooldown.HOME) && coolingDown(w, Cooldown.MINIGAME));
		assertEquals("the earlier one first (minigame ready at USED + 25)", (USED + 26) * MS, w.lookAgainAt);
		assertTrue(w.gameMinute(USED + 25, clockAt(USED + 25)));
		assertTrue(coolingDown(w, Cooldown.HOME));
		assertFalse(coolingDown(w, Cooldown.MINIGAME));
		assertEquals((USED + 31) * MS, w.lookAgainAt);
		w.clear();
		assertFalse(coolingDown(w, Cooldown.HOME));
		assertEquals(Long.MAX_VALUE, w.lookAgainAt);
	}

	@Test
	public void theInputsWatchTheLastUseVarps()
	{
		TransportLine home = TransportLine.builder("home", TransportLine.Kind.TELEPORT)
			.name("Lumbridge Home Teleport").category("TELEPORT").end(3222, 3218, 0).ticks(24).cooldown(true)
			.requirements("spellbook standard").teleportCategory("home teleport").wildernessLimit(20).build();
		TransportLine glory = TransportLine.builder("glory", TransportLine.Kind.TELEPORT).name("Glory")
			.category("TELEPORT").end(3087, 3496, 0).ticks(4).teleportCategory("jewellery (consumable)")
			.wildernessLimit(30).build();
		Set<Cooldown> cs = RequirementInputs.cooldownsOf(List.of(home, glory));
		assertEquals(Set.of(Cooldown.HOME), cs);
		RequirementInputs in = RequirementInputs.NONE.withCooldowns(cs);
		assertEquals(Set.of(Cooldown.HOME), in.getCooldowns());
		assertTrue(in.getVarps().contains(892));
		assertFalse(in.getVarps().contains(888));
		assertTrue(RequirementInputs.NONE.withCooldowns(Set.of()) == RequirementInputs.NONE);
	}
}

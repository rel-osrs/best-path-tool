package com.bestpath.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.route.TransportSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import org.junit.Test;

/**
 * The player-state fixes from a code review: quest re-reads only when a quest's own var changed,
 * login vs scene load, per-account settings without a profile and the bank /
 * ownership cache written once changes settle, to the right account.
 */
public class PluginFixesPlayerTest
{
	private static QuestWatch watch()
	{
		Map<String, List<int[]>> vars = new HashMap<>();
		vars.put("Cook's Assistant", List.of(new int[]{1, 29}));           // varp 29
		vars.put("Zogre Flesh Eaters", List.of(new int[]{0, 487}));        // varbit 487
		vars.put("Recipe for Disaster", List.of(new int[]{0, 1850}, new int[]{0, 1892})); // two varbits
		return new QuestWatch(List.of("Cook's Assistant", "Zogre Flesh Eaters", "Recipe for Disaster", "Unknown Quest"),
			vars::get);
	}

	@Test
	public void questsAreReadOnceAtTheStartThenOnlyWhenTheirOwnVarChanges()
	{
		QuestWatch w = watch();
		assertEquals(Set.of("Unknown Quest"), w.getUnmapped());
		assertEquals(4, w.toRead(0).size()); // all at the start
		assertTrue(w.toRead(1).isEmpty());

		assertTrue(w.onVarChanged(-1, 29)); // Cook's Assistant's varp
		assertEquals(Set.of("Cook's Assistant"), w.toRead(2));
		assertTrue(w.onVarChanged(1892, 1000)); // one of Recipe for Disaster's varbits
		assertTrue(w.onVarChanged(487, 1001));
		assertEquals(Set.of("Recipe for Disaster", "Zogre Flesh Eaters"), w.toRead(3));

		// a varbit inside varp 29 is not the varp itself; an unrelated var doesn't count as "may matter"
		assertFalse(w.onVarChanged(5000, 29));
		assertFalse(w.onVarChanged(-1, 300));
		assertTrue(w.toRead(4).isEmpty());
	}

	@Test
	public void unrelatedVarsReadEveryQuestAtMostOncePerMinute()
	{
		QuestWatch w = watch();
		w.toRead(0);
		w.onVarChanged(-1, 300); // music, timers...
		assertTrue(w.toRead(50).isEmpty()); // not yet: the last full read was at tick 0
		assertEquals(4, w.toRead(QuestWatch.FULL_REREAD_TICKS).size());
		assertTrue(w.toRead(QuestWatch.FULL_REREAD_TICKS + 1).isEmpty()); // nothing changed since
		w.all(); // account switch
		assertEquals(4, w.toRead(QuestWatch.FULL_REREAD_TICKS + 2).size());
	}

	@Test
	public void questVarTableInTheJarIsQuestHelpersAndNamesRuneLiteQuests() throws Exception
	{
		Map<String, List<int[]>> t = QuestWatch.loadTable();
		assertTrue("quest-vars.tsv missing or empty", t.size() > 150);
		for (String name : t.keySet())
		{
			assertNotNull(name, Quest.valueOf(name));
		}
		// spot checks against Quest Helper's QuestVarPlayer.java / QuestVarbits.java (commit a526461)
		assertEquals(1, t.get("COOKS_ASSISTANT").get(0)[0]);
		assertEquals(29, t.get("COOKS_ASSISTANT").get(0)[1]);
		assertEquals(2, t.get("SHIELD_OF_ARRAV").size()); // both gangs' varps (145, 146)
		assertTrue(t.get("RECIPE_FOR_DISASTER").size() >= 9); // the main var and the sub-quests
		// every line names its source
		String text = new String(PluginFixesPlayerTest.class.getResourceAsStream(QuestWatch.RESOURCE).readAllBytes(),
			StandardCharsets.UTF_8);
		for (String line : text.split("\n"))
		{
			if (!line.startsWith("#") && !line.isBlank())
			{
				assertTrue(line, line.split("\t")[3].startsWith("Quest Helper "));
			}
		}
	}

	@Test
	public void questVarTableSkipsBadLines() throws Exception
	{
		String text = "# comment\nCOOKS_ASSISTANT\tvarp\t29\tQuest Helper x\nBAD\tvarthing\t1\nBAD2\tvarbit\tnope\n";
		Map<String, List<int[]>> t = QuestWatch.readTable(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
		assertEquals(Set.of("COOKS_ASSISTANT"), t.keySet());
	}

	@Test
	public void sceneLoadsAreNotLogins()
	{
		LoginEdges e = new LoginEdges();
		assertTrue(e.onState(GameState.LOGGED_IN)); // plugin started while logged in
		assertFalse(e.onState(GameState.LOADING));
		assertFalse(e.onState(GameState.LOGGED_IN)); // scene load
		assertFalse(e.onState(GameState.HOPPING));
		assertFalse(e.onState(GameState.LOADING));
		assertTrue(e.onState(GameState.LOGGED_IN)); // world hop
		e.onState(GameState.LOGIN_SCREEN);
		e.onState(GameState.LOGGING_IN);
		assertTrue(e.onState(GameState.LOGGED_IN)); // login
	}

	/** A fake RuneLite profile store: one profile key at a time; writing while logged in without one creates it. */
	private static final class FakeStore implements AccountSettings.Store
	{
		String key;
		boolean loggedIn;
		final Map<String, String> saved = new HashMap<>();
		final List<String> writes = new ArrayList<>();

		@Override
		public String profileKey()
		{
			return key;
		}

		@Override
		public String read()
		{
			return key == null ? null : saved.get(key);
		}

		@Override
		public boolean write(String text)
		{
			if (key == null)
			{
				if (!loggedIn)
				{
					return false;
				}
				key = "new-profile"; // RuneLite creates the profile
			}
			saved.put(key, text);
			writes.add(key);
			return true;
		}
	}

	@Test
	public void settingsChangedWhileLoggedOutAreKeptAndSavedAtTheNextLogin()
	{
		FakeStore store = new FakeStore();
		TransportSettings noBoats = TransportSettings.defaults().withCategory(TransportSettings.Category.BOATS, false);
		store.saved.put("A", noBoats.encode());
		AccountSettings a = new AccountSettings(store);

		store.key = "A";
		assertEquals(noBoats, a.onProfileChanged());
		store.key = null; // logout: what is in use stays
		assertEquals(noBoats, a.onProfileChanged());

		TransportSettings both = noBoats.withAvoid(TransportSettings.Avoid.RUNES, true);
		a.change(both);
		assertEquals(both, a.current);
		assertNotNull(a.pending);
		assertTrue(store.writes.isEmpty());

		store.key = "B"; // the next login saves it there
		store.loggedIn = true;
		assertEquals(both, a.onProfileChanged());
		assertNull(a.pending);
		assertEquals(both.encode(), store.saved.get("B"));
		assertEquals(noBoats.encode(), store.saved.get("A")); // the other account is untouched

		// switching back loads A's own settings
		store.key = "A";
		assertEquals(noBoats, a.onProfileChanged());
	}

	@Test
	public void anAccountWithoutAProfileGetsOneWhenSettingsChange()
	{
		FakeStore store = new FakeStore();
		store.loggedIn = true;
		AccountSettings a = new AccountSettings(store);
		assertEquals(TransportSettings.defaults(), a.onProfileChanged());
		TransportSettings s = TransportSettings.defaults().withAvoid(TransportSettings.Avoid.COST, true);
		a.change(s);
		assertNull(a.pending);
		assertEquals(List.of("new-profile"), store.writes);
		a.change(s); // same again: no write (diff before persisting)
		assertEquals(1, store.writes.size());
	}

	@Test
	public void bankIsWrittenOnceTheChangesSettleToItsOwnAccount()
	{
		DelayedSave s = new DelayedSave();
		s.loaded("old");
		s.changed("A", "old", 0);
		assertNull(s.pending()); // same as saved

		for (int t = 0; t < 8; t++)
		{
			s.changed("A", "deposit " + t, t); // a deposit every tick
			assertFalse(s.due(t));
		}
		assertFalse(s.due(7 + DelayedSave.QUIET_TICKS - 1));
		assertTrue(s.due(7 + DelayedSave.QUIET_TICKS));
		DelayedSave.Write w = s.pending();
		assertEquals("A", w.getProfile());
		assertEquals("deposit 7", w.getText());
		s.written();
		assertNull(s.pending());

		// endless small changes are still written now and then
		for (int t = 100; t < 100 + DelayedSave.MAX_WAIT_TICKS; t++)
		{
			s.changed("A", "x" + t, t);
		}
		assertTrue(s.due(100 + DelayedSave.MAX_WAIT_TICKS));

		// loading another account forgets what waited (the plugin writes it first)
		s.loaded("B's bank");
		assertNull(s.pending());
	}
}

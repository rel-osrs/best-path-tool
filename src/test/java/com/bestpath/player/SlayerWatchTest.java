package com.bestpath.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;

import com.bestpath.route.SlayerTask;
import com.bestpath.route.SlayerTaskTest;
import com.bestpath.core.data.WildernessZones;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** The Slayer task is read on the tick after one of its vars changed, and handed out only when new. */
public class SlayerWatchTest
{
	private final Map<Integer, Integer> varps = new HashMap<>();
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private int reads;
	private final java.util.List<String> saved = new java.util.ArrayList<>();

	private SlayerWatch watch()
	{
		return new SlayerWatch(id ->
		{
			reads++;
			return varps.getOrDefault(id, 0);
		}, id -> varbits.getOrDefault(id, 0), saved::add);
	}

	private void task(int id, int count, int master)
	{
		varps.put(SlayerTask.TASK_VARP, id);
		varps.put(SlayerTask.COUNT_VARP, count);
		varbits.put(SlayerTask.MASTER_VARBIT, master);
	}

	/** A task put in storage is shown as the stored task, if any. */
	@Test
	public void aTaskPutInStorageIsShownAndSaved()
	{
		SlayerWatch w = watch();
		w.setData(SlayerTaskTest.DATA, WildernessZones.NONE);
		task(53, 87, 3);
		assertNull(w.changedTask().getStored());
		// stored: the task is gone, the stored master is Vannaka
		task(0, 0, 3);
		varbits.put(SlayerTask.STORED_MASTER_VARBIT, 3);
		w.onVarChanged(SlayerTask.STORED_MASTER_VARBIT, 0);
		SlayerTask t = w.changedTask();
		assertEquals(SlayerTask.State.NO_TASK, t.getState());
		assertEquals("Kalphites", t.getStored().getName());
		assertEquals(87, t.getStored().getLeft());
		assertEquals("Vannaka", t.getStored().getMaster());
		assertEquals(4144, t.getStored().getIcon());
		assertEquals(java.util.List.of("53,0,87,3"), saved);
		// a new task: the stored one stays
		task(79, 10, 3);
		w.onVarChanged(-1, SlayerTask.TASK_VARP);
		t = w.changedTask();
		assertEquals("Ankou", t.getName());
		assertEquals("Kalphites", t.getStored().getName());
		// taken out again: storage empty
		task(53, 87, 3);
		varbits.put(SlayerTask.STORED_MASTER_VARBIT, 0);
		w.onVarChanged(SlayerTask.STORED_MASTER_VARBIT, 0);
		assertNull(w.changedTask().getStored());
		assertEquals(java.util.List.of("53,0,87,3", ""), saved);
	}

	/** Swapping tasks shows the swapped task, not just "a task is stored". */
	@Test
	public void aSwapWithTheSameMasterIsSeen()
	{
		SlayerWatch w = watch();
		w.setData(SlayerTaskTest.DATA, WildernessZones.NONE);
		// Ankou in storage from before the plugin, Kalphites now; both Vannaka's
		task(53, 87, 3);
		varbits.put(SlayerTask.STORED_MASTER_VARBIT, 3);
		assertFalse(w.changedTask().getStored().isKnown());
		// swapped: Ankou now, Kalphites stored; the stored master stays Vannaka
		task(79, 12, 3);
		w.onVarChanged(-1, SlayerTask.TASK_VARP);
		SlayerTask t = w.changedTask();
		assertEquals("Ankou", t.getName());
		assertEquals("Kalphites", t.getStored().getName());
		assertEquals(87, t.getStored().getLeft());
		// swapped back: Ankou stored with the 12 left
		task(53, 87, 3);
		w.onVarChanged(-1, SlayerTask.TASK_VARP);
		t = w.changedTask();
		assertEquals("Kalphites", t.getName());
		assertEquals("Ankou", t.getStored().getName());
		assertEquals(12, t.getStored().getLeft());
	}

	@Test
	public void anotherMastersNewTaskIsNotTakenForASwap()
	{
		SlayerWatch w = watch();
		w.setData(SlayerTaskTest.DATA, WildernessZones.NONE);
		task(53, 87, 3);
		varbits.put(SlayerTask.STORED_MASTER_VARBIT, 8); // Konar's task in storage
		w.changedTask();
		task(79, 12, 7); // a task straight from Krystilia (not the stored master)
		w.onVarChanged(-1, SlayerTask.TASK_VARP);
		assertFalse(w.changedTask().getStored().isKnown());
	}

	@Test
	public void theStoredMasterCanShowATickBeforeTheTaskLeaves()
	{
		SlayerWatch w = watch();
		w.setData(SlayerTaskTest.DATA, WildernessZones.NONE);
		task(53, 87, 3);
		w.changedTask();
		varbits.put(SlayerTask.STORED_MASTER_VARBIT, 3);
		w.onVarChanged(SlayerTask.STORED_MASTER_VARBIT, 0);
		assertFalse("not known yet", w.changedTask().getStored().isKnown());
		task(0, 0, 3);
		w.onVarChanged(-1, SlayerTask.TASK_VARP);
		assertEquals("Kalphites", w.changedTask().getStored().getName());
	}

	@Test
	public void afterALoginTheSavedTaskCountsWhileTheVarsFitIt()
	{
		SlayerWatch w = watch();
		w.setData(SlayerTaskTest.DATA, WildernessZones.NONE);
		w.loadStored("53,0,87,3");
		varbits.put(SlayerTask.STORED_MASTER_VARBIT, 3);
		assertEquals("Kalphites", w.changedTask().getStored().getName());
		assertTrue(saved.isEmpty());
		// another master's task in storage (stored without the plugin): not known, only the master
		varbits.put(SlayerTask.STORED_MASTER_VARBIT, 8);
		w.onLogin(true);
		SlayerTask.Stored s = w.changedTask().getStored();
		assertFalse(s.isKnown());
		assertEquals("Konar", s.getMaster());
		assertEquals(java.util.List.of(""), saved);
		// a stored boss task names its boss even unseen
		varbits.put(SlayerTask.STORED_BOSS_VARBIT, 7);
		w.onLogin(true);
		s = w.changedTask().getStored();
		assertEquals("The Kalphite Queen", s.getName());
		assertEquals("how many: not known", 0, s.getLeft());
	}

	@Test
	public void readsOnceAfterAChangeAndOnlyHandsOutNews()
	{
		SlayerWatch w = watch();
		assertSame("before the map data", SlayerTask.NO_DATA, w.changedTask());
		assertNull("nothing changed", w.changedTask());
		w.setData(SlayerTaskTest.DATA, WildernessZones.NONE);
		assertSame(SlayerTask.NO_TASK, w.changedTask());
		varps.put(SlayerTask.TASK_VARP, 53);
		varps.put(SlayerTask.COUNT_VARP, 87);
		reads = 0;
		w.onVarChanged(1234, 0); // another varbit
		w.onVarChanged(-1, 999); // another varp
		assertNull(w.changedTask());
		assertEquals("no var read for other vars", 0, reads);
		w.onVarChanged(-1, SlayerTask.TASK_VARP);
		SlayerTask t = w.changedTask();
		assertEquals("Kalphites", t.getName());
		assertEquals(87, t.getLeft());
		w.onVarChanged(SlayerTask.MASTER_VARBIT, 0);
		assertNull("the same task again", w.changedTask());
		varps.put(SlayerTask.COUNT_VARP, 86);
		w.onVarChanged(-1, SlayerTask.COUNT_VARP);
		assertEquals(86, w.changedTask().getLeft());
	}

	@Test
	public void loggingOutShowsAtOnceLoggingInReadsOnTheNextTick()
	{
		SlayerWatch w = watch();
		w.setData(SlayerTaskTest.DATA, WildernessZones.NONE);
		varps.put(SlayerTask.TASK_VARP, 53);
		varps.put(SlayerTask.COUNT_VARP, 5);
		assertEquals(5, w.changedTask().getLeft());
		assertSame(SlayerTask.LOGGED_OUT, w.onLogin(false));
		assertNull("logged in: read on the next tick", w.onLogin(true));
		assertEquals("Kalphites", w.changedTask().getName());
	}
}

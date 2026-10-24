package com.bestpath.player;

import com.bestpath.core.data.SlayerPlaces;
import com.bestpath.core.data.WildernessZones;
import com.bestpath.route.SlayerTask;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

/**
 * Reads the player's Slayer task for the Functions tab. Event driven: a change of one of the task's
 * vars (varps 394, 395, 2096; varbits 4067, 4723, and the stored task's 12443, 12444), a login or the map data arriving
 * marks it; the next game tick reads the vars once and hands out the task when it differs from the one shown. Client
 * thread only.
 * <p>
 * Task storage: the game's vars name only the stored task's master and boss (no public var or game script holds its
 * monster or count). So the watch remembers the task the player had when the stored master went from
 * none to a master (or changed): that task went into storage. It is saved per account and dropped when storage is
 * empty again or the vars no longer match it (another master, another boss).
 */
public final class SlayerWatch
{
	private final IntUnaryOperator varp;
	private final IntUnaryOperator varbit;
	private SlayerPlaces data;
	private WildernessZones wilderness = WildernessZones.NONE;
	private boolean dirty = true;
	private SlayerTask shown;
	private final Consumer<String> save;
	/** The stored task as remembered ({@link Seen}), or null when not known. */
	private Seen stored;
	/** The current task at the last read (null: none), and the last one there was. */
	private Seen before;
	private Seen lastTask;
	/** The stored master and boss at the last read; -1 after a login (nothing to compare with). */
	private int storedMaster = -1;
	private int storedBoss = -1;
	/** The stored master appeared while the task was still there: the next read takes the task that left. */
	private boolean waiting;

	/** A task as the vars named it: varp 395, varbit 4723, varp 394, varbit 4067. */
	static final class Seen
	{
		final int task;
		final int boss;
		final int count;
		final int master;

		Seen(int task, int boss, int count, int master)
		{
			this.task = task;
			this.boss = boss;
			this.count = count;
			this.master = master;
		}

		/** The same task (not how many are left). */
		boolean same(Seen o)
		{
			return o != null && task == o.task && boss == o.boss && master == o.master;
		}

		/** Matches the stored task's vars: its master, and its boss for a boss task. */
		boolean fits(int masterId, int bossId)
		{
			return master == masterId && (task != SlayerPlaces.BOSS_TASK || boss == bossId);
		}

		String toText()
		{
			return task + "," + boss + "," + count + "," + master;
		}

		/** From {@link #toText}, or null. */
		static Seen fromText(String s)
		{
			if (s == null)
			{
				return null;
			}
			String[] p = s.trim().split(",");
			if (p.length != 4)
			{
				return null;
			}
			try
			{
				return new Seen(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}
	}

	/**
	 * {@code varp} / {@code varbit}: the game's values ({@code client::getVarpValue}, {@code client::getVarbitValue});
	 * {@code save}: keeps the remembered stored task for this account ("" for none).
	 */
	public SlayerWatch(IntUnaryOperator varp, IntUnaryOperator varbit, Consumer<String> save)
	{
		this.varp = varp;
		this.varbit = varbit;
		this.save = save;
	}

	/** This account's remembered stored task (its saved text, null for none): start, login, account switch. */
	public void loadStored(String text)
	{
		stored = Seen.fromText(text);
		dirty = true;
	}

	/** The map data's Slayer places and Wilderness (loaded on the route worker, handed over on the client thread). */
	public void setData(SlayerPlaces places, WildernessZones zones)
	{
		data = places;
		wilderness = zones == null ? WildernessZones.NONE : zones;
		dirty = true;
		shown = null;
	}

	/** A var changed ({@code VarbitChanged}: varbit id, or -1 and the varp id). */
	public void onVarChanged(int varbitId, int varpId)
	{
		if (varbitId == -1 ? varpId == SlayerTask.TASK_VARP || varpId == SlayerTask.COUNT_VARP || varpId == SlayerTask.AREA_VARP
			: varbitId == SlayerTask.BOSS_VARBIT || varbitId == SlayerTask.MASTER_VARBIT
			|| varbitId == SlayerTask.STORED_MASTER_VARBIT || varbitId == SlayerTask.STORED_BOSS_VARBIT)
		{
			dirty = true;
		}
	}

	/** Logged in or out: the task to show now when logged out (null: nothing new), read on the next tick when logged in. */
	public SlayerTask onLogin(boolean loggedIn)
	{
		dirty = true;
		storedMaster = -1;
		storedBoss = -1;
		before = null;
		lastTask = null;
		return loggedIn ? null : show(SlayerTask.LOGGED_OUT);
	}

	/** Each game tick (logged in): the task when something it reads changed and it differs from the one shown, else null. */
	public SlayerTask changedTask()
	{
		if (!dirty)
		{
			return null;
		}
		dirty = false;
		int taskId = varp.applyAsInt(SlayerTask.TASK_VARP);
		int bossId = varbit.applyAsInt(SlayerTask.BOSS_VARBIT);
		int count = varp.applyAsInt(SlayerTask.COUNT_VARP);
		int masterId = varbit.applyAsInt(SlayerTask.MASTER_VARBIT);
		SlayerTask t = data == null ? SlayerTask.NO_DATA : SlayerTask.of(data, wilderness, taskId, bossId, count,
			varp.applyAsInt(SlayerTask.AREA_VARP), masterId);
		Seen now = taskId > 0 && count > 0 ? new Seen(taskId, bossId, count, masterId) : null;
		readStored(now, varbit.applyAsInt(SlayerTask.STORED_MASTER_VARBIT), varbit.applyAsInt(SlayerTask.STORED_BOSS_VARBIT));
		if (data != null)
		{
			int sm = storedMaster;
			t = t.withStored(sm <= 0 ? null : stored != null
				? SlayerTask.Stored.of(data, stored.task, stored.boss, stored.count, stored.master)
				: SlayerTask.Stored.of(data, 0, storedBoss, 0, sm));
		}
		return show(t);
	}

	/**
	 * Works out the stored task from this read: storage empty forgets it; a stored master that appeared or changed since
	 * the last read (not just after a login) means the task the player had went into storage; a remembered task that no
	 * longer fits the vars is forgotten.
	 */
	private void readStored(Seen now, int masterId, int bossId)
	{
		Seen was = stored;
		boolean compare = storedMaster >= 0;
		boolean changed = compare && (masterId != storedMaster || bossId != storedBoss);
		// a swap with storage: the task changed straight to another with kills left, given by the stored master; with
		// both from the same master the stored master doesn't change, so this is the only sign. (A Turael skip gives
		// Turael's task: it fits only when storage holds one of his too.)
		boolean swapped = compare && before != null && now != null && !before.same(now) && now.master == storedMaster;
		if (masterId <= 0)
		{
			stored = null;
		}
		else if (changed || swapped || compare && stored != null && stored.same(now) && !stored.same(before))
		{
			// stored now, or taken out and another put in: the task the player had a moment ago (if it fits)
			Seen gone = before != null && !before.same(now) ? before : lastTask != null && !lastTask.same(now) ? lastTask : null;
			stored = gone != null && gone.fits(masterId, bossId) ? gone : null;
			// the stored master can show a tick before the task leaves: the next read looks again
			waiting = stored == null && now != null;
		}
		else if (waiting && stored == null && before != null && !before.same(now) && before.fits(masterId, bossId))
		{
			stored = before;
			waiting = false;
		}
		else if (stored != null && !stored.fits(masterId, bossId))
		{
			stored = null;
		}
		if (!changed)
		{
			waiting = false;
		}
		storedMaster = masterId;
		storedBoss = bossId;
		before = now;
		if (now != null)
		{
			lastTask = now;
		}
		if (!Objects.equals(was == null ? null : was.toText(), stored == null ? null : stored.toText()))
		{
			save.accept(stored == null ? "" : stored.toText());
		}
	}

	/** The task shown last (null: none yet). */
	public SlayerTask current()
	{
		return shown;
	}

	private SlayerTask show(SlayerTask t)
	{
		if (t.equals(shown))
		{
			return null;
		}
		shown = t;
		return t;
	}
}

package com.bestpath.route;

import com.bestpath.core.path.GamePath;
import com.bestpath.core.path.LineRedraw;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Redraws the walk ahead with the game's own pathing from the tile the player
 * actually stands on (rules in core {@link LineRedraw}), so a click on the
 * drawn line is walked as drawn.
 *
 * <p>Threads: {@link #playerAt} runs on the client thread at a
 * game tick and only notes the player's tile and destination flag; the
 * redraw itself runs on the route worker ({@link RouteService#runOnWorker}),
 * at most once per change of tile or flag (requests made meanwhile are
 * merged: only the newest counts). A changed line is published as a new
 * immutable {@link DrawPath} that the scene, minimap and world map overlays
 * read; when nothing differs, nothing is published. The route itself (steps,
 * ticks, sidebar cards) is never changed.
 */
public final class RouteRedraw
{
	/** One noted position: the route, its current walk step, where the player is and the game's flag. */
	private static final class Request
	{
		final RouteResult result;
		final int step;
		final int from;
		final int tile;
		final int flag;

		Request(RouteResult result, int step, int from, int tile, int flag)
		{
			this.result = result;
			this.step = step;
			this.from = from;
			this.tile = tile;
			this.flag = flag;
		}
	}

	private final RouteService service;
	private final AtomicReference<DrawPath> drawn = new AtomicReference<>(DrawPath.NONE);
	private final AtomicReference<Request> pending = new AtomicReference<>();
	private final AtomicBoolean scheduled = new AtomicBoolean();
	private volatile BiConsumer<RouteResult, DrawPath> listener = (r, d) ->
	{
	};
	/** Client thread only: the last position noted. */
	private Request last;
	/** Worker thread only: the redraw rules' memory for the walk being followed. */
	private LineRedraw lines;
	private long linesGeneration = -1;
	private int linesStep = -1;

	public RouteRedraw(RouteService service)
	{
		this.service = service;
	}

	/** Told on the worker thread when a line was redrawn (the world map rebuilds its lines there). */
	public void setListener(BiConsumer<RouteResult, DrawPath> listener)
	{
		this.listener = listener;
	}

	/** The latest draw list (never null; check {@link DrawPath#isFor}). Any thread; overlays read this. */
	public DrawPath current()
	{
		return drawn.get();
	}

	/**
	 * Client thread: the draw list for this result, made from its own tiles when it is a new route (and published),
	 * else the latest one. {@link DrawPath#NONE} for results without a route.
	 */
	public DrawPath forResult(RouteResult r)
	{
		if (r == null || r.getStatus() != RouteStatus.READY)
		{
			return DrawPath.NONE;
		}
		DrawPath d = drawn.get();
		if (d.isFor(r))
		{
			return d;
		}
		DrawPath fresh = DrawPath.of(r);
		return drawn.updateAndGet(old -> newer(fresh, old) ? fresh : old);
	}

	/**
	 * Client thread, once per game tick: the player is on {@code tile} (packed) at walk index {@code from} of step
	 * {@code step}, and the game is walking them to {@code flag} (packed; -1 = standing still). Asks the worker for a
	 * redraw when the tile or the flag changed.
	 */
	public void playerAt(RouteResult r, int step, int from, int tile, int flag)
	{
		if (r == null || r.getStatus() != RouteStatus.READY || step < 0 || step >= r.getSteps().size()
			|| r.getSteps().get(step).getKind() != RouteStep.Kind.WALK)
		{
			return;
		}
		Request l = last;
		if (l != null && l.result == r && l.step == step && l.tile == tile && l.flag == flag)
		{
			return; // nothing new: no redraw (and nothing made)
		}
		// a little behind the progress: a player beside the line may be a tile or two back along it
		Request q = new Request(r, step, Math.max(0, from - RouteProgress.NEAR), tile, flag);
		last = q;
		pending.set(q);
		if (scheduled.compareAndSet(false, true))
		{
			service.runOnWorker(this::work);
		}
	}

	/** Worker thread: redraws for the newest noted position. */
	private void work()
	{
		scheduled.set(false); // first: a position noted from now on schedules another run
		Request q = pending.getAndSet(null);
		if (q == null || service.getCurrent() != q.result)
		{
			return; // nothing new, or that route is no longer shown
		}
		GamePath game = service.gamePath();
		if (game == null)
		{
			return;
		}
		DrawPath d = drawn.get();
		if (!d.isFor(q.result))
		{
			d = DrawPath.of(q.result);
		}
		if (!d.hasTiles(q.step))
		{
			return;
		}
		if (lines == null || linesGeneration != q.result.getGeneration() || linesStep != q.step)
		{
			lines = new LineRedraw();
			linesGeneration = q.result.getGeneration();
			linesStep = q.step;
		}
		int[] line = d.tiles(q.step);
		int[] out = lines.redraw(game, line, q.from, q.tile, q.flag);
		if (out == line)
		{
			return; // nothing differs: nothing changes
		}
		DrawPath next = d.withTiles(q.step, out);
		DrawPath now = drawn.updateAndGet(old -> newer(next, old) ? next : old);
		if (now == next)
		{
			listener.accept(q.result, next);
		}
	}

	/** True if {@code a} should replace {@code b}: a later route, or a later redraw of the same one. */
	private static boolean newer(DrawPath a, DrawPath b)
	{
		return a.getGeneration() > b.getGeneration()
			|| (a.getGeneration() == b.getGeneration() && a.getVersion() > b.getVersion());
	}
}

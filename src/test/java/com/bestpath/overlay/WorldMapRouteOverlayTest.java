package com.bestpath.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStep;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import org.junit.Test;

/** The config box "Show route on world map": off, the overlay draws nothing and does not even look at the game. */
public class WorldMapRouteOverlayTest
{
	/** A stand-in game client that answers nothing (null / 0 / false) and writes down what was asked. */
	private static Client recordingClient(List<String> calls)
	{
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, m, args) ->
			{
				calls.add(m.getName());
				Class<?> r = m.getReturnType();
				if (r == boolean.class)
				{
					return false;
				}
				if (r.isPrimitive() && r != void.class)
				{
					return r == long.class ? 0L : r == float.class ? 0f : r == double.class ? 0d
						: r == char.class ? (Object) '\0' : r == byte.class ? (Object) (byte) 0
						: r == short.class ? (Object) (short) 0 : 0;
				}
				return null;
			});
	}

	private static RouteResult route()
	{
		int[] t = new int[11];
		for (int i = 0; i < t.length; i++)
		{
			t[i] = Tiles.pack(3200 + i, 3200, 0);
		}
		return RouteResults.ready(3, new RouteRequest(3210, 3200, 0),
			List.of(new RouteStep(RouteStep.Kind.WALK, "Walk", "", 10, List.of(), null, t, -1)));
	}

	private static int[] render(WorldMapRouteOverlay o)
	{
		BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		assertNull(o.render(g));
		g.dispose();
		return img.getRGB(0, 0, 50, 50, null, 0, 50);
	}

	@Test
	public void switchedOffItDrawsNothingAndAsksTheGameNothing()
	{
		List<String> calls = new ArrayList<>();
		WorldMapRouteOverlay o = new WorldMapRouteOverlay(recordingClient(calls), () -> Progress.NONE, () -> false, Runnable::run);
		o.routeChanged(route());
		int[] px = render(o);
		for (int p : px)
		{
			assertEquals(0xFF000000, p); // untouched (black)
		}
		assertTrue(calls.toString(), calls.isEmpty());
	}

	@Test
	public void switchedOnItLooksForTheOpenMap()
	{
		List<String> calls = new ArrayList<>();
		WorldMapRouteOverlay o = new WorldMapRouteOverlay(recordingClient(calls), () -> Progress.NONE, () -> true, Runnable::run);
		o.routeChanged(route());
		render(o); // the stand-in has no world map open: nothing is drawn, but the overlay did ask
		assertTrue(calls.toString(), calls.contains("getWidget"));
	}

	@Test
	public void theSwitchIsReadEveryFrame()
	{
		List<String> calls = new ArrayList<>();
		AtomicBoolean on = new AtomicBoolean(false);
		WorldMapRouteOverlay o = new WorldMapRouteOverlay(recordingClient(calls), () -> Progress.NONE, on::get, Runnable::run);
		o.routeChanged(route());
		render(o);
		assertTrue(calls.isEmpty());
		on.set(true); // ticked in the config panel: no restart needed
		render(o);
		assertTrue(calls.contains("getWidget"));
	}
}

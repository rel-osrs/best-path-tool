package com.bestpath.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.route.NoMapDataEngine;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.RouteEngine;
import com.bestpath.route.RouteJob;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.TransportSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** The plugin works without map data ("No map data yet") and with broken data (says why), never crashing. */
public class MapDataLoaderTest
{
	private static RouteResult ask(RouteEngine e)
	{
		PlayerSnapshot me = new PlayerSnapshot(3222, 3218, 0, PlayerStateBuilder.fresh(), List.of());
		return e.route(new RouteJob(1, new RouteRequest(3210, 3210, 0), me, TransportSettings.defaults(), Set.of()));
	}

	@Test
	public void noDataAnswersNoMapDataYet()
	{
		RouteEngine e = MapDataLoader.engine(name -> null);
		assertTrue(e instanceof NoMapDataEngine);
		RouteResult r = ask(e);
		assertEquals(RouteStatus.NO_DATA, r.getStatus());
		assertEquals(NoMapDataEngine.MESSAGE, r.getMessage());
	}

	@Test
	public void brokenDataSaysWhy()
	{
		byte[] manifest = ("format\t" + PluginData.FORMAT + "\nfingerprint\t0\n").getBytes(StandardCharsets.UTF_8);
		RouteEngine e = MapDataLoader.engine(name -> PluginData.MANIFEST.equals(name) ? new ByteArrayInputStream(manifest) : null);
		assertTrue(e instanceof NoMapDataEngine);
		RouteResult r = ask(e);
		assertEquals(RouteStatus.NO_DATA, r.getStatus());
		assertTrue(r.getMessage(), r.getMessage().startsWith("The map data could not be loaded"));
	}
}

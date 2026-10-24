package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Skill;
import com.bestpath.data.MapDataLoader;
import com.bestpath.overlay.StepTargets;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * On the REAL exported data: what the player clicks for each step (the text and the
 * glow in the game come from it): an object's id and tile, an item teleport's items (any charge), a spell.
 */
public class ClickHintRealDataTest
{
	private static RouteEngine engine;

	@BeforeClass
	public static void load()
	{
		engine = MapDataLoader.engine();
	}

	private static RouteResult route(PlayerState p, int sx, int sy, int sp, int dx, int dy, int dp)
	{
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		RouteResult r = engine.route(new RouteJob(1, new RouteRequest(dx, dy, dp), new PlayerSnapshot(sx, sy, sp, p, List.of()),
			TransportSettings.defaults(), Set.of()));
		assertEquals(RouteStatus.READY, r.getStatus());
		return r;
	}

	private static RouteStep first(RouteResult r, RouteStep.Kind kind)
	{
		for (RouteStep s : r.getSteps())
		{
			if (s.getKind() == kind)
			{
				return s;
			}
		}
		return null;
	}

	/** True when something in the world is clicked (an object or NPC near where the step is used). */
	private static boolean inWorld(ClickHint h)
	{
		return h.getNear() != -1 && !h.isSpell() && h.itemIds.length == 0;
	}

	@Test
	public void aStaircaseIsAnObjectNearWhereItIsUsed()
	{
		// Lumbridge Castle, ground floor to the first floor: the staircase
		PlayerState p = new PlayerState.Builder("Walker").build();
		RouteResult r = route(p, 3212, 3215, 0, 3206, 3214, 1);
		RouteStep up = null;
		for (RouteStep s : r.getSteps())
		{
			if (s.getKind().isTransport() && s.getClick().getObjectId() >= 0)
			{
				up = s;
				break;
			}
		}
		assertNotNull(r.getSteps().toString(), up);
		ClickHint h = up.getClick();
		assertFalse(h.getOption().isEmpty());
		assertEquals(up.getTitle(), h.getOption() + " " + h.getTarget());
		assertTrue(inWorld(h));
		int near = h.getNear();
		int off = Math.max(Math.abs(Tiles.x(near) - Tiles.x(h.objectTile)), Math.abs(Tiles.y(near) - Tiles.y(h.objectTile)));
		assertTrue("the data's object is by where the step is used: " + off, off <= 4);
		// walking to the staircase, the next thing to do is that staircase (the walk itself has no text)
		int k = r.getSteps().indexOf(up);
		assertEquals(RouteStep.Kind.WALK, r.getSteps().get(k - 1).getKind());
		assertEquals(k, StepTargets.nextAction(r, new Progress(r.getGeneration(), k - 1, 0)));
	}

	@Test
	public void anAmuletOfGloryGlowsWhateverItsCharge()
	{
		// glory (4) carried: Lumbridge to Edgeville
		PlayerState p = new PlayerState.Builder("Glory").members(true).carry(1712, 1).build();
		RouteStep t = first(route(p, 3222, 3218, 0, 3087, 3496, 0), RouteStep.Kind.TELEPORT);
		assertNotNull(t);
		ClickHint h = t.getClick();
		assertTrue(h.usesItem(1712));
		assertTrue("glory (3) too", h.usesItem(1710));
		assertFalse(h.isSpell());
		assertFalse("nothing in the world to click", inWorld(h));
	}

	@Test
	public void houseFurnitureIsFoundByWhatIsBuilt()
	{
		// the furniture glows with text: in the house with a fancy jewellery box, to Castle Wars
		PlayerState p = new PlayerState.Builder("Builder").members(true).house("Rimmington").houseThing("fancy jewellery box").build();
		RouteResult r = route(p, 1857, 7065, 0, 2442, 3089, 0);
		RouteStep box = null;
		for (RouteStep s : r.getSteps())
		{
			if (!s.getClick().getHouseThings().isEmpty())
			{
				box = s;
				break;
			}
		}
		assertNotNull(r.getSteps().toString(), box);
		ClickHint h = box.getClick();
		assertTrue(h.getHouseThings().toString(), h.usesFurniture("fancy jewellery box"));
		assertFalse(h.usesFurniture("portal nexus"));
		assertFalse(h.usesFurniture(null));
		// a portal nexus does every "portal nexus: ..." destination
		ClickHint nexus = new ClickHint("", "", null, -1, -1, null, false, -1, Set.of("portal nexus: varrock teleport"));
		assertTrue(nexus.usesFurniture("portal nexus"));
		assertFalse(nexus.usesFurniture("portal chamber: Lumbridge Teleport"));
	}

	@Test
	public void aFairyRingRideIsClickedAtTheRingItLeavesFrom()
	{
		// from the house ring: the boarding stop's furniture and tile
		PlayerState p = new PlayerState.Builder("Rings").members(true).allUnlocked(true).house("Rimmington")
			.houseThing("fairy ring").build();
		RouteResult r = route(p, 1857, 7065, 0, 2996, 3114, 0);
		RouteStep ring = null;
		for (RouteStep s : r.getSteps())
		{
			if (s.getTitle().startsWith("Fairy ring"))
			{
				ring = s;
				break;
			}
		}
		assertNotNull(r.getSteps().toString(), ring);
		assertTrue(ring.getClick().getHouseThings().toString(), ring.getClick().usesFurniture("fairy ring"));
		assertEquals("Fairy ring", ring.getClick().getTarget());
	}

	@Test
	public void aSpellGlowsInTheSpellbook()
	{
		// runes for Varrock Teleport: Lumbridge to the square
		PlayerState p = new PlayerState.Builder("Mage").level(Skill.MAGIC, 99).carry(563, 10).carry(556, 30).carry(554, 10).build();
		RouteStep t = first(route(p, 3222, 3218, 0, 3213, 3424, 0), RouteStep.Kind.TELEPORT);
		assertNotNull(t);
		ClickHint h = t.getClick();
		assertTrue(t.getTitle(), h.isSpell());
		assertEquals("Varrock Teleport", h.getTarget());
		assertEquals(0, h.itemIds.length);
	}
}

package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.sea.BoatType;
import com.bestpath.route.BoatCard;
import com.bestpath.route.BoatMode;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStep;
import com.bestpath.route.TransportSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The Route tab's boat card: its buttons hand the choice to the plugin. */
public class BoatChoiceCardTest
{
	private final List<BoatMode> chosen = new ArrayList<>();

	private RouteTab tab()
	{
		PanelActions actions = new PanelActions()
		{
			@Override
			public void findRoute(RouteRequest request)
			{
			}

			@Override
			public void clearRoute()
			{
			}

			@Override
			public void useClue()
			{
			}

			@Override
			public void skipForThisRoute(String transportId)
			{
			}

			@Override
			public void clearSkips()
			{
			}

			@Override
			public void settingsChanged(TransportSettings settings)
			{
			}

			@Override
			public void boatMode(BoatMode mode)
			{
				chosen.add(mode);
			}
		};
		return new RouteTab(actions, TransportSettings::defaults, new IconCache(IconProvider.NONE));
	}

	private static RouteResult result(BoatCard card)
	{
		RouteStep tele = new RouteStep(RouteStep.Kind.TELEPORT, "Teleport west", "", 10, List.of(), "tele-west", new int[0], -1, -1);
		return RouteResults.ready(1, new RouteRequest(3250, 3250, 0), List.of(tele)).withBoatCard(card);
	}

	@Test
	public void parkMyBoatAndOceanOnly() throws Exception
	{
		RouteTab tab = tab();
		SwingUtilities.invokeAndWait(() -> tab.show(result(new BoatCard(BoatMode.NORMAL, "Boat 1: Raft", BoatType.RAFT, true, null)), Set.of()));
		BoatChoiceCard c = tab.boatChoice;
		assertNotNull(c);
		assertNull(c.back);
		assertEquals(BoatChoiceCard.PARK, c.park.getText());
		assertTrue(c.ocean.isEnabled());
		SwingUtilities.invokeAndWait(() -> c.park.doClick());
		SwingUtilities.invokeAndWait(() -> c.ocean.doClick());
		assertEquals(List.of(BoatMode.PARK, BoatMode.OCEAN_ONLY), chosen);
		// the step cards still number from 1
		assertEquals(1, tab.cards.size());
	}

	@Test
	public void oceanOnlyGreyedOutWhenNothingIsNearTheSea() throws Exception
	{
		RouteTab tab = tab();
		SwingUtilities.invokeAndWait(() -> tab.show(result(new BoatCard(BoatMode.NORMAL, "Boat 1: Raft", BoatType.RAFT, false, null)), Set.of()));
		BoatChoiceCard c = tab.boatChoice;
		assertFalse(c.ocean.isEnabled());
		assertEquals(BoatCard.notNearTip(), c.ocean.getToolTipText());
		assertTrue(c.park.isEnabled());
	}

	@Test
	public void aModeOnHasTheWayBack() throws Exception
	{
		RouteTab tab = tab();
		SwingUtilities.invokeAndWait(() -> tab.show(result(new BoatCard(BoatMode.OCEAN_ONLY, "Boat 1: Raft", BoatType.RAFT, true, null)), Set.of()));
		BoatChoiceCard c = tab.boatChoice;
		assertNull(c.park);
		assertNull(c.ocean);
		assertEquals(BoatChoiceCard.OCEAN_OFF, c.back.getText());
		SwingUtilities.invokeAndWait(() -> c.back.doClick());
		assertEquals(List.of(BoatMode.NORMAL), chosen);
		SwingUtilities.invokeAndWait(() -> tab.show(result(new BoatCard(BoatMode.PARK, "Boat 1: Raft", BoatType.RAFT, true, null)), Set.of()));
		assertEquals(BoatChoiceCard.NORMAL, tab.boatChoice.back.getText());
		// off the boat: no card
		SwingUtilities.invokeAndWait(() -> tab.show(result(null), Set.of()));
		assertNull(tab.boatChoice);
	}
}

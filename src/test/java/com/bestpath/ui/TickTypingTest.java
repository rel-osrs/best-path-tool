package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteStep;
import com.bestpath.route.TransportSettings;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;

/**
 * Every tick setting (cards, teleport categories and networks, the bank stop) goes from 0 to 10000
 * total ticks, and a click on the total lets the player type it (a whole number, kept between 0 and 10000); the
 * Settings tab's "Teleport and travel ticks" folds (folded unless opened, remembered per account, the route stays).
 */
public class TickTypingTest
{
	/** Records what the Settings tab sends: route-changing settings and display choices apart. */
	private static final class Recorder implements PanelActions
	{
		final List<TransportSettings> changed = new ArrayList<>();
		final List<TransportSettings> saved = new ArrayList<>();

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
			changed.add(settings);
		}

		@Override
		public void settingsSaved(TransportSettings settings)
		{
			saved.add(settings);
		}
	}

	@Test
	public void typedTotalsAreWholeNumbersFrom0To10000()
	{
		assertEquals(Integer.valueOf(25), TickStepper.parse("25"));
		assertEquals(Integer.valueOf(25), TickStepper.parse(" 25 ticks"));
		assertEquals(Integer.valueOf(1200), TickStepper.parse("1,200"));
		assertEquals(Integer.valueOf(0), TickStepper.parse("-5"));
		assertEquals(Integer.valueOf(10000), TickStepper.parse("123456"));
		assertEquals(Integer.valueOf(10000), TickStepper.parse("999999999"));
		assertNull(TickStepper.parse(""));
		assertNull(TickStepper.parse("abc"));
		assertNull(TickStepper.parse("2.5"));
	}

	@Test
	public void aCardsTotalCanBeTypedAndStaysWithin0To10000() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			List<Integer> sent = new ArrayList<>();
			RouteStep ladder = new RouteStep(RouteStep.Kind.TRAVERSAL, "Climb-up Ladder", "to 3200, 3200, 1", 25, List.of(), "t-ladder",
				new int[0], 0, 1);
			StepCard card = new StepCard(1, ladder, new IconCache(IconProvider.NONE), s -> { }, s -> { },
				new StepCard.TickControl()
				{
					@Override
					public int change(RouteStep s)
					{
						return 0;
					}

					@Override
					public void changed(RouteStep s, int change)
					{
						sent.add(change);
					}
				}, null);
			TickStepper t = card.stepper;
			assertEquals("25 ticks", t.total.getText());
			PanelDriver.type(t, "40");
			assertEquals("40 ticks", t.total.getText());
			assertEquals(List.of(15), sent);
			assertFalse("the box closes", t.field.isVisible());
			PanelDriver.type(t, "not a number");
			assertEquals("nothing changes", "40 ticks", t.total.getText());
			assertEquals(1, sent.size());
			PanelDriver.type(t, "20000");
			assertEquals("10000 ticks", t.total.getText());
			assertFalse("no more than 10000", t.plus.isEnabled());
			assertEquals(Integer.valueOf(10000 - 25), sent.get(sent.size() - 1));
			t.plus.doClick(0);
			assertEquals("10000 ticks", t.total.getText());
			t.minus.doClick(0);
			assertEquals("9999 ticks", t.total.getText());
			assertTrue(t.plus.isEnabled());
			PanelDriver.type(t, "0");
			assertEquals("0 ticks", t.total.getText());
			assertFalse(t.minus.isEnabled());
		});
	}

	@Test
	public void categoriesNetworksAndTheBankStopCanBeTyped() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			SettingsTab s = new SettingsTab(r, new IconCache(IconProvider.NONE));
			s.showTickGroups(TickGroupSettingsTest.groups());
			TickGroupList list = s.tickGroups;
			PanelDriver.type(PanelDriver.stepper(list, "teleport:spell"), "100");
			assertEquals("100 ticks", PanelDriver.totalText(list, "teleport:spell"));
			// a network: the quickest ride takes the typed ticks, the others keep their difference
			PanelDriver.type(PanelDriver.stepper(list, "network:fairy ring"), "30");
			assertEquals("30-45 ticks", PanelDriver.totalText(list, "network:fairy ring"));
			PanelDriver.type(PanelDriver.stepper(list, "network:fairy ring"), "10000");
			assertEquals("the slowest ride stops at 10000", "9985-10000 ticks", PanelDriver.totalText(list, "network:fairy ring"));
			assertFalse(PanelDriver.stepper(list, "network:fairy ring").plus.isEnabled());
			s.tickGroups.sendChanges();
			assertEquals(1, r.changed.size());
			assertEquals(96, r.changed.get(0).teleportCategoryTickChange("spell"));
			assertEquals(10000 - 20, r.changed.get(0).networkTickChange("Fairy ring"));
			// the bank stop
			PanelDriver.type(s.bankTicks.stepper, "1234");
			assertEquals("1234 ticks", s.bankTicks.stepper.total.getText());
			s.bankTicks.send();
			assertEquals(1234, r.changed.get(r.changed.size() - 1).getBankTicks());
			PanelDriver.type(s.bankTicks.stepper, "50000");
			assertEquals("10000 ticks", s.bankTicks.stepper.total.getText());
			assertFalse(s.bankTicks.stepper.plus.isEnabled());
		});
	}

	@Test
	public void theTicksListIsFoldedUntilOpenedAndRemembered() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Recorder r = new Recorder();
			SettingsTab s = new SettingsTab(r, new IconCache(IconProvider.NONE));
			assertFalse("folded by default", s.ticksBody.isVisible());
			s.toggleTicksList();
			assertTrue(s.ticksBody.isVisible());
			assertEquals("saved, without searching the route again", 1, r.saved.size());
			assertTrue(r.changed.isEmpty());
			assertTrue(r.saved.get(0).isTicksListOpen());
			// saved per account with the other settings
			TransportSettings back = TransportSettings.decode(r.saved.get(0).encode());
			assertTrue(back.isTicksListOpen());
			assertFalse(TransportSettings.defaults().isTicksListOpen());
			assertFalse(TransportSettings.defaults().encode().contains("tickslist"));
			// another account's settings: its own fold
			s.show(TransportSettings.defaults());
			assertFalse(s.ticksBody.isVisible());
			s.show(back);
			assertTrue(s.ticksBody.isVisible());
			s.toggleTicksList();
			assertFalse(s.ticksBody.isVisible());
			assertFalse(r.saved.get(1).isTicksListOpen());
		});
	}
}

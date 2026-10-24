package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bestpath.route.TransportSettings;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The Avoid section's coin limit: "a tick box to enable, and something that says dont use travel that costs over this much". */
public class SettingsCoinCapTest
{
	@Test
	public void theTickBoxAndTheAmountChangeTheSettings() throws Exception
	{
		List<TransportSettings> sent = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsTab s = new SettingsTab(new PanelActions()
			{
				@Override
				public void findRoute(com.bestpath.route.RouteRequest request)
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
					sent.add(settings);
				}
			}, new IconCache(IconProvider.NONE));
			assertFalse(s.coinCap.isSelected());
			assertEquals(TransportSettings.DEFAULT_COIN_CAP, s.coinAmount.getValue());
			s.coinCap.doClick();
			assertTrue(sent.get(sent.size() - 1).isCoinCapOn());
			// the amount is sent after a short pause (arrow clicks add up first); here at once
			s.coinAmount.setValue(2500);
			s.sendCoinCap();
			TransportSettings last = sent.get(sent.size() - 1);
			assertTrue(last.isCoinCapOn());
			assertEquals(2500, last.getCoinCap());
			// another account's settings: shown, nothing sent
			int before = sent.size();
			s.show(TransportSettings.defaults().withCoinCap(700));
			assertFalse(s.coinCap.isSelected());
			assertEquals(700, s.coinAmount.getValue());
			s.sendCoinCap();
			assertEquals(before, sent.size());
		});
	}
}

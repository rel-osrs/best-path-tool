package com.bestpath;

import com.bestpath.route.QuestHelperPath;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteService;
import net.runelite.client.events.PluginMessage;

/**
 * Quest Helper's current step. Quest Helper, with its "Use 'Shortest Path' plugin" setting on, sends each step's place
 * as a message to the Shortest Path plugin ({@link QuestHelperPath}). Best Path keeps the step for the "Quest" buttons,
 * and with "Routes from other plugins" on routes there; the step's end clears that route. Client thread only.
 */
final class QuestHelperSteps
{
	private final BestPathConfig config;
	private final PluginContext plugin;
	/** The step Quest Helper sent last (null: none, or it ended). */
	private RouteRequest step;

	QuestHelperSteps(BestPathConfig config, PluginContext plugin)
	{
		this.config = config;
		this.plugin = plugin;
	}

	/** A message sent to the Shortest Path plugin: a step's place, or the step's end. */
	void onMessage(PluginMessage m)
	{
		if (QuestHelperPath.PATH.equals(m.getName()))
		{
			RouteRequest sent = QuestHelperPath.request(m.getData());
			if (sent != null)
			{
				stepSent(sent);
			}
		}
		else if (QuestHelperPath.CLEAR.equals(m.getName()))
		{
			stepEnded();
		}
	}

	/** Quest Helper sent a step's place. */
	private void stepSent(RouteRequest sent)
	{
		if (sent.equals(step))
		{
			return; // the same step again: the route already goes there
		}
		step = sent;
		show();
		if (config.allowPluginRoutes())
		{
			plugin.requestRoute(sent);
			plugin.remember(sent);
		}
	}

	/** Quest Helper's step ended (the next step's place, if any, follows). */
	private void stepEnded()
	{
		if (step == null)
		{
			return;
		}
		step = null;
		show();
		RouteService service = plugin.routes();
		if (service != null && config.allowPluginRoutes() && QuestHelperPath.isQuestRoute(service.getCurrent().getRequest()))
		{
			service.clear();
		}
	}

	/** The sidebar's "Quest" button: a route to the step's place. */
	void routeToStep()
	{
		if (step != null)
		{
			plugin.requestRoute(step);
			plugin.remember(step);
		}
	}

	/** The Functions tab and the Route tab's "Quest" button show the step, and whether routes follow it. */
	void show()
	{
		RouteRequest s = step;
		boolean following = config.allowPluginRoutes();
		plugin.toSidebar(sidebar -> sidebar.showQuestStep(s, following));
	}
}

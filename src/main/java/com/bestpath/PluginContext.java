package com.bestpath;

import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteService;
import com.bestpath.route.TransportSettings;
import com.bestpath.ui.BestPathPanel;
import java.util.function.Consumer;

/**
 * What the plugin's parts (clue routes, Slayer routes, Quest Helper steps, saved destinations) need from the plugin:
 * the services of the current start, the sidebar and the way a route is asked for.
 */
interface PluginContext
{
	/** The route search service; null once the plugin stopped. */
	RouteService routes();

	/** Player state from game events; null once the plugin stopped. */
	PlayerTracker tracker();

	/** The Settings tab's choices in use. */
	TransportSettings settings();

	/** Any thread: runs {@code show} on the Swing thread with the sidebar, when there is one. */
	void toSidebar(Consumer<BestPathPanel> show);

	/** Queues work for the client thread (game state is read and changed there only). */
	void onClient(Runnable job);

	/** Client thread: a new destination; the player snapshot is taken here, the search runs on the route worker. */
	void requestRoute(RouteRequest request);

	/** Client thread: a destination the player picked goes to the top of the recent destinations. */
	void remember(RouteRequest request);

	/**
	 * Any thread: opens the sidebar by itself when {@code wanted} (that case's switch in "Open the sidebar"), on the
	 * Slayer page ({@code slayer}) or the Route tab.
	 */
	void openSidebar(boolean wanted, boolean slayer);
}

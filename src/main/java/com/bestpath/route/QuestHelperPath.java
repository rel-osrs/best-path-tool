package com.bestpath.route;

import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * Quest Helper's step, as it hands it to other plugins. With its setting "Use 'Shortest Path' plugin" on, Quest
 * Helper posts RuneLite's {@code PluginMessage} with namespace {@value #NAMESPACE}: {@value #PATH} with
 * {@code "target"} (a {@link WorldPoint}) when a step with a place starts, and {@value #CLEAR} when it ends. Any other
 * plugin posting the same message is read the same way.
 */
public final class QuestHelperPath
{
	public static final String NAMESPACE = "shortestpath";
	public static final String PATH = "path";
	public static final String CLEAR = "clear";
	/** The route key of a Quest Helper step's route. */
	public static final String KEY = "quest helper";
	/** The title of these routes: any plugin may send them (the setting "Routes from other plugins"). */
	public static final String TITLE = "Plugin route";

	private QuestHelperPath()
	{
	}

	/**
	 * The route to a {@value #PATH} message's {@code "target"}: one {@link WorldPoint}, or several (the nearest is
	 * taken); null when it holds no tile Best Path can route to.
	 */
	public static RouteRequest request(Map<String, Object> data)
	{
		Object target = data == null ? null : data.get("target");
		List<Integer> tiles = new ArrayList<>();
		if (target instanceof WorldPoint)
		{
			add(tiles, (WorldPoint) target);
		}
		else if (target instanceof Collection)
		{
			for (Object o : (Collection<?>) target)
			{
				if (o instanceof WorldPoint)
				{
					add(tiles, (WorldPoint) o);
				}
			}
		}
		if (tiles.isEmpty())
		{
			return null;
		}
		int[] targets = tiles.stream().mapToInt(Integer::intValue).toArray();
		String where = targets.length == 1 ? RouteRequest.tileLabel(Tiles.x(targets[0]), Tiles.y(targets[0]), Tiles.plane(targets[0]))
			: "the nearest of " + targets.length + " places";
		return RouteRequest.places(KEY, TITLE, List.of("Sent by another plugin: " + where + "."), targets);
	}

	/**
	 * True when a {@value #NAMESPACE} message is left alone: while a clue is carried and Best Path routes clues itself.
	 * The message can come from any plugin, not only Quest Helper: the Shortest Clue plugin sends each clue's place the
	 * same way, and a route straight to the place would replace Best Path's clue route (the kill for a key, the STASH
	 * and bank stops). The messages carry no sender, so none is followed (nor kept, nor saved) during a clue.
	 *
	 * @param clueCarried a clue scroll, of any kind, is in the inventory
	 * @param clueRouting Best Path routes the clue: "Automatic clue routes" is on, or the current route is the clue's
	 *                    ("Use clue" pressed)
	 */
	public static boolean heldBack(boolean clueCarried, boolean clueRouting)
	{
		return clueCarried && clueRouting;
	}

	/** True for the route to a Quest Helper step. */
	public static boolean isQuestRoute(RouteRequest r)
	{
		return r != null && KEY.equals(r.getKey());
	}

	private static void add(List<Integer> tiles, WorldPoint p)
	{
		if (p.getX() >= 0 && p.getX() <= RouteRequest.MAX_COORD && p.getY() >= 0 && p.getY() <= RouteRequest.MAX_COORD
			&& p.getPlane() >= 0 && p.getPlane() <= 3)
		{
			int t = Tiles.pack(p.getX(), p.getY(), p.getPlane());
			if (!tiles.contains(t))
			{
				tiles.add(t);
			}
		}
	}
}

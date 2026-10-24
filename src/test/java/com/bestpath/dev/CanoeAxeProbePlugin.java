package com.bestpath.dev;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * DEV ONLY, TEMPORARY: finds out what the game tells the client when an axe is stored at or taken
 * back from a canoe station, so "Axe stored at the canoe stations" can be detected instead of asked. Lives in src/test,
 * so it is never in the plugin jar; only the dev launcher ({@code BestPathPluginTest}) loads it.
 *
 * <p>While the player is within {@link #NEAR} tiles of a canoe station it appends to
 * {@code ~/.runelite/bestpath-canoe-probe.txt}: every var change (varp and varbit ids, old and new values), every chat
 * message, every menu click, inventory / equipment changes, and the texts of any interface that opens (dialogues, the
 * canoe interfaces).
 *
 * <p><b>To remove:</b> delete this file and take {@code CanoeAxeProbePlugin.class} out of
 * {@code BestPathPluginTest}'s {@code loadBuiltin(...)}.
 */
@PluginDescriptor(
	name = "Best Path canoe axe probe (dev)",
	description = "Dev only: logs what the game sends near canoe stations"
)
public class CanoeAxeProbePlugin extends Plugin
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CanoeAxeProbePlugin.class);

	/** Canoe station objects: the station on each river (multilocs) and every stage they change into. */
	private static final Set<Integer> STATIONS = new HashSet<>();

	static
	{
		for (int id = 12144; id <= 12158; id++)
		{
			STATIONS.add(id);
		}
		for (int id = 12163; id <= 12166; id++)
		{
			STATIONS.add(id);
		}
		STATIONS.add(39638);
		for (int id = 60845; id <= 60849; id++)
		{
			STATIONS.add(id);
		}
	}

	/** How close (tiles, same plane) the player must be to a station for anything to be written. */
	private static final int NEAR = 15;
	private static final File OUT = new File(RuneLite.RUNELITE_DIR, "bestpath-canoe-probe.txt");

	@Inject
	private Client client;

	private ExecutorService writer;
	/** Stations in the loaded scene. Client thread. */
	private final Set<WorldPoint> stations = new HashSet<>();
	/** Varp values when the player came near, kept up to date, to write each change's old value. Client thread. */
	private int[] varps;
	private boolean near;
	private boolean greeted;
	/** Inventory (93) and equipment (94) last seen: item id -> count. Client thread. */
	private final Map<Integer, Map<Integer, Integer>> containers = new HashMap<>();
	/** Interfaces opened while near, and their texts last written. Client thread. */
	private final Map<Integer, List<String>> openGroups = new HashMap<>();

	@Override
	protected void startUp()
	{
		writer = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "bestpath-canoe-probe");
			t.setDaemon(true);
			return t;
		});
		write("=== probe started ===");
	}

	@Override
	protected void shutDown()
	{
		write("=== probe stopped ===");
		writer.shutdown();
		stations.clear();
		containers.clear();
		openGroups.clear();
		varps = null;
		near = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOADING)
		{
			stations.clear();
		}
		if (e.getGameState() == GameState.LOGGED_IN && !greeted)
		{
			greeted = true;
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Canoe probe] on: recording near canoe stations to " + OUT.getAbsolutePath(), null);
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		GameObject o = e.getGameObject();
		if (STATIONS.contains(o.getId()))
		{
			stations.add(o.getWorldLocation());
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		boolean now = playerNear();
		if (now != near)
		{
			near = now;
			if (near)
			{
				varps = client.getVarps().clone();
				containers.clear();
				openGroups.clear();
				write("--- came near a canoe station at " + where() + " ---");
				snapshotContainer(93);
				snapshotContainer(94);
			}
			else
			{
				write("--- left the canoe station (" + where() + ") ---");
			}
		}
		if (near)
		{
			dumpInterfaces();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		if (!near || varps == null)
		{
			return;
		}
		if (e.getVarbitId() != -1)
		{
			write("varbit " + e.getVarbitId() + " (varp " + e.getVarpId() + ") = " + e.getValue());
			return;
		}
		int id = e.getVarpId();
		int old = id >= 0 && id < varps.length ? varps[id] : 0;
		if (id >= 0 && id < varps.length)
		{
			varps[id] = e.getValue();
		}
		write("varp " + id + ": " + old + " -> " + e.getValue());
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if (!near || e.getType() == ChatMessageType.PUBLICCHAT || e.getType() == ChatMessageType.PRIVATECHAT
			|| e.getType() == ChatMessageType.CLAN_CHAT || e.getType() == ChatMessageType.FRIENDSCHAT)
		{
			return;
		}
		if (e.getMessage().startsWith("[Canoe probe]"))
		{
			return;
		}
		write("chat " + e.getType() + " [" + e.getName() + "]: " + e.getMessage());
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if (!near)
		{
			return;
		}
		write("click \"" + e.getMenuOption() + "\" on \"" + e.getMenuTarget() + "\" (action " + e.getMenuAction()
			+ ", id " + e.getId() + ", item " + e.getItemId() + ", params " + e.getParam0() + "," + e.getParam1() + ")");
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (!near || (e.getContainerId() != 93 && e.getContainerId() != 94))
		{
			return;
		}
		Map<Integer, Integer> before = containers.getOrDefault(e.getContainerId(), new HashMap<>());
		Map<Integer, Integer> after = counts(e.getItemContainer());
		Set<Integer> ids = new HashSet<>(before.keySet());
		ids.addAll(after.keySet());
		List<String> changes = new ArrayList<>();
		for (int id : ids)
		{
			int b = before.getOrDefault(id, 0);
			int a = after.getOrDefault(id, 0);
			if (a != b)
			{
				changes.add(client.getItemDefinition(id).getName() + " (" + id + ") " + b + " -> " + a);
			}
		}
		containers.put(e.getContainerId(), after);
		if (!changes.isEmpty())
		{
			write((e.getContainerId() == 93 ? "inventory: " : "worn: ") + String.join("; ", changes));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (near)
		{
			write("interface " + e.getGroupId() + " opened");
			openGroups.put(e.getGroupId(), new ArrayList<>());
		}
	}

	private void snapshotContainer(int id)
	{
		ItemContainer c = client.getItemContainer(id);
		containers.put(id, c == null ? new HashMap<>() : counts(c));
	}

	private static Map<Integer, Integer> counts(ItemContainer c)
	{
		Map<Integer, Integer> out = new HashMap<>();
		if (c != null)
		{
			for (Item i : c.getItems())
			{
				if (i.getId() > 0)
				{
					out.merge(i.getId(), i.getQuantity(), Integer::sum);
				}
			}
		}
		return out;
	}

	/** Writes an opened interface's texts (and item ids) whenever they change, until it closes. */
	private void dumpInterfaces()
	{
		for (Map.Entry<Integer, List<String>> g : new ArrayList<>(openGroups.entrySet()))
		{
			List<String> texts = texts(g.getKey());
			if (texts == null)
			{
				openGroups.remove(g.getKey());
				write("interface " + g.getKey() + " closed");
				continue;
			}
			if (!texts.equals(g.getValue()))
			{
				g.setValue(texts);
				write("interface " + g.getKey() + " texts: " + String.join(" | ", texts));
			}
		}
	}

	/** The visible texts of an interface group, or null when it is not open. */
	private List<String> texts(int group)
	{
		Widget root = client.getWidget(group, 0);
		if (root == null)
		{
			return null;
		}
		List<String> out = new ArrayList<>();
		int misses = 0;
		for (int child = 0; child < 1000 && misses < 50; child++)
		{
			Widget w = client.getWidget(group, child);
			if (w == null)
			{
				misses++;
				continue;
			}
			misses = 0;
			add(out, w, child + "");
			Widget[] dyn = w.getDynamicChildren();
			if (dyn != null)
			{
				for (Widget d : dyn)
				{
					add(out, d, child + "." + d.getIndex());
				}
			}
		}
		return out;
	}

	private static void add(List<String> out, Widget w, String at)
	{
		if (w == null || w.isHidden())
		{
			return;
		}
		String t = w.getText();
		if (t != null && !t.isBlank())
		{
			out.add(at + "=\"" + t + "\"");
		}
		if (w.getItemId() > 0)
		{
			out.add(at + "=item " + w.getItemId() + " x" + w.getItemQuantity());
		}
	}

	private boolean playerNear()
	{
		Player p = client.getLocalPlayer();
		if (p == null || stations.isEmpty())
		{
			return false;
		}
		WorldPoint at = p.getWorldLocation();
		for (WorldPoint s : stations)
		{
			if (s.getPlane() == at.getPlane() && s.distanceTo2D(at) <= NEAR)
			{
				return true;
			}
		}
		return false;
	}

	private String where()
	{
		Player p = client.getLocalPlayer();
		return p == null ? "?" : p.getWorldLocation().toString();
	}

	/** Appends a line (time, game tick) on the writer thread. */
	private void write(String line)
	{
		String text = LocalTime.now().withNano(0) + " tick " + client.getTickCount() + "  " + line;
		log.info("[canoe probe] {}", line);
		ExecutorService w = writer;
		if (w == null || w.isShutdown())
		{
			return;
		}
		w.execute(() ->
		{
			try (BufferedWriter out = Files.newBufferedWriter(OUT.toPath(), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND))
			{
				out.write(text);
				out.newLine();
			}
			catch (IOException ex)
			{
				log.warn("canoe probe: cannot write {}", OUT, ex);
			}
		});
	}
}

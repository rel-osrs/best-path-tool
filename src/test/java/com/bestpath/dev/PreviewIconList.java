package com.bestpath.dev;

import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.route.CoreRouteEngine;
import com.bestpath.route.RouteEngine;
import com.bestpath.route.StepInfo;
import com.bestpath.ui.IconLists;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * DEV ONLY: writes the list of pictures the sidebar can show (every item a
 * transport's requirement names, the coins, and every sprite of
 * {@link IconLists}) for {@code gradlew previewIcons}, which then draws
 * them from the game cache with the Data Tool. Argument: the list file.
 */
public final class PreviewIconList
{
	private PreviewIconList()
	{
	}

	public static void main(String[] args) throws IOException
	{
		File out = new File(args[0]);
		File dir = out.getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs())
		{
			throw new IOException("cannot create " + dir);
		}
		TreeSet<Integer> items = new TreeSet<>();
		items.add(StepInfo.COINS_ITEM);
		RouteEngine engine = RealData.engine();
		if (engine instanceof CoreRouteEngine)
		{
			PluginData data = ((CoreRouteEngine) engine).getRouter().getData();
			Tables tables = data.getTables();
			for (TransportLine l : data.getTransports())
			{
				for (String own : new String[]{l.getRequirements(), l.requirementsEndToStart()})
				{
					Requirement req;
					try
					{
						req = RequirementText.parse(tables.networkRequirement(l.getNetwork(), own), tables.itemGroups());
					}
					catch (RequirementText.ParseException e)
					{
						continue;
					}
					StepInfo info = StepInfo.of(l, false, req, null, null, tables);
					if (info.getItemId() >= 0)
					{
						items.add(info.getItemId());
					}
					for (StepInfo.Need n : info.getNeeds())
					{
						if (n.getItemId() >= 0)
						{
							items.add(n.getItemId());
						}
					}
					// every "one of" choice too (a test player may get another choice than the first)
					req.visit(r ->
					{
						if (r instanceof Requirement.Item)
						{
							Requirement.Item it = (Requirement.Item) r;
							items.add(it.getItemIds()[0]);
							int d = tables.itemGroupDefault(it.getLabel());
							if (d >= 0)
							{
								items.add(d);
							}
						}
						else if (r instanceof Requirement.Runes)
						{
							items.add(((Requirement.Runes) r).getRuneIds()[0]);
						}
					});
				}
			}
		}
		if (engine instanceof CoreRouteEngine)
		{
			// the clue cards: each step's clue scroll and the first choice of every item its needs name
			PluginData data = ((CoreRouteEngine) engine).getRouter().getData();
			for (com.bestpath.core.data.ClueData.Step s : data.getClues().getSteps())
			{
				if (s.getItems().length > 0)
				{
					items.add(s.getItems()[0]);
				}
				try
				{
					RequirementText.parse(s.getNeeds(), data.getTables().itemGroups()).visit(r ->
					{
						if (r instanceof Requirement.Item)
						{
							items.add(((Requirement.Item) r).getItemIds()[0]);
							int d = data.getTables().itemGroupDefault(((Requirement.Item) r).getLabel());
							if (d >= 0)
							{
								items.add(d);
							}
						}
					});
				}
				catch (RequirementText.ParseException e)
				{
					// the export checks every line
				}
			}
			items.addAll(List.of(23182, 19835, 19939, 23183, 952)); // shared scrolls, devices, the spade
		}
		else
		{
			System.out.println("No exported map data (gradlew -p tool exportPlugin): only the sample route's pictures.");
			items.addAll(List.of(563, 556, 554));
		}
		items.addAll(IconLists.allItems()); // the Settings tab's items
		List<String> lines = new ArrayList<>();
		lines.add("# Pictures for the UI preview (gradlew previewIcons): item <id> | sprite <id> <what it should show>");
		for (int id : items)
		{
			lines.add("item " + id);
		}
		for (Map.Entry<Integer, String> e : IconLists.allSprites().entrySet())
		{
			lines.add("sprite " + e.getKey() + " " + e.getValue());
		}
		if (engine instanceof CoreRouteEngine)
		{
			// the Functions tab's "Nearest" kinds: their map icons
			for (com.bestpath.core.data.PlaceKinds.Kind k : ((CoreRouteEngine) engine).getRouter().getData().getPlaces().getKinds())
			{
				if (k.getSprite() >= 0 && !IconLists.allSprites().containsKey(k.getSprite()))
				{
					lines.add("sprite " + k.getSprite() + " map icon: " + k.getName());
				}
			}
		}
		Files.write(out.toPath(), lines, StandardCharsets.UTF_8);
		System.out.printf("%d items and %d sprites listed in %s%n", items.size(), IconLists.allSprites().size(), out);
	}
}

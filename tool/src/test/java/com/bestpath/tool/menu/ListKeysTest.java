package com.bestpath.tool.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.MenuTextBuilder;
import com.bestpath.core.data.MenuTextDiagnostics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.cache.EntityOpsDefinition;
import org.junit.jupiter.api.Test;

/**
 * List keys stored with the data, not read at run time: a choice's key is its place in the list's order from the
 * cache, only for lists a person confirmed.
 */
class ListKeysTest
{
	private final MenuFacts facts = new MenuFacts();

	ListKeysTest()
	{
		for (int n = 1; n <= 9; n++)
		{
			facts.listKeys.add(String.valueOf(n));
		}
		for (char c = 'A'; c <= 'Z'; c++)
		{
			facts.listKeys.add(String.valueOf(c));
		}
		facts.spiritTrees.addAll(List.of("Tree Gnome Village", "Gnome Stronghold", "Battlefield of Khazard", "Grand Exchange",
			"Feldip Hills", "Prifddinas", "Port Sarim", "Etceteria", "Brimhaven", "Hosidius", "Farming Guild", "Your house",
			"Poison Waste", "Laguna Aurorae"));
		games(3853, "Games necklace(8)");
		games(3855, "Games necklace(7)");
	}

	private void games(int id, String name)
	{
		item(id, name, "Rub", List.of("Burthorpe", "Barbarian Outpost", "Corporeal Beast", "Tears of Guthix", "Wintertodt Camp"));
	}

	private void item(int id, String name, String op, List<String> sub)
	{
		List<List<String>> subs = new ArrayList<>(Arrays.asList(null, null, null, sub, null));
		List<List<String>> worn = new ArrayList<>();
		for (int i = 0; i < 8; i++)
		{
			worn.add(null);
		}
		facts.items.put(id, new MenuFacts.Item(id, name, new String[]{null, "Wear", null, op, "Drop"}, subs, new String[8], worn));
	}

	private static ListKeys rules(String... lines)
	{
		return ListKeys.parse(List.of(lines));
	}

	@Test
	public void aConfirmedListGivesEachChoiceTheKeyOfItsPlace()
	{
		ListKeys k = rules("# comment", "", "Rub\tGames necklace\tsub-menu\tyes\tcache", "Travel\tSpirit tree\tspirit trees\tyes\tcache");
		assertEquals("Games necklace → Tears of Guthix (4)",
			k.apply(MenuTextBuilder.choice("Rub", "Games necklace", "Tears of Guthix"), facts).title(MenuText.ARROW));
		// spirit trees: 1-9, then letters (the Wiki's picture: Hosidius A, Laguna Aurorae E); either spelling of the tree
		assertEquals("A", MenuTextDiagnostics.key(k.apply(MenuTextBuilder.choice("Travel", "Spirit Tree", "Hosidius"), facts)));
		assertEquals("C", MenuTextDiagnostics.key(k.apply(MenuTextBuilder.choice("Travel", "Spirit tree", "Your house"), facts)));
		assertEquals("E", MenuTextDiagnostics.key(k.apply(MenuTextBuilder.choice("Travel", "Spirit tree", "Laguna Aurorae"), facts)));
		assertEquals("2", MenuTextDiagnostics.key(k.apply(MenuTextBuilder.choice("Travel", "Spirit tree", "Gnome Stronghold"), facts)));
	}

	@Test
	public void noKeyWithoutAConfirmedRuleOrAPlace()
	{
		MenuText t = MenuTextBuilder.choice("Rub", "Games necklace", "Burthorpe");
		// not confirmed yet: no key
		assertSame(t, rules("Rub\tGames necklace\tsub-menu\task\tcache").apply(t, facts));
		// no rule for this click
		assertSame(t, rules("Rub\tAmulet of glory\tsub-menu\tyes\tcache").apply(t, facts));
		// a choice not in the list
		MenuText other = MenuTextBuilder.choice("Rub", "Games necklace", "Burthorpe Games Room");
		assertSame(other, rules("Rub\tGames necklace\tsub-menu\tyes\tcache").apply(other, facts));
		// a key the cache fixed already (the house jewellery box) is kept; other styles are left alone
		MenuText box = MenuTextBuilder.choice("Rub", "Games necklace", "Burthorpe", "9");
		assertSame(box, rules("Rub\tGames necklace\tsub-menu\tyes\tcache").apply(box, facts));
		MenuText verb = MenuText.verb("Climb-up", "Ladder");
		assertSame(verb, rules("Climb-up\tLadder\tsub-menu\tyes\tcache").apply(verb, facts));
		assertNull(rules().apply(null, facts));
	}

	@Test
	public void formsThatListDifferentPlacesGiveNoKey()
	{
		item(3857, "Games necklace(6)", "Rub", List.of("Barbarian Outpost", "Burthorpe"));
		MenuText t = MenuTextBuilder.choice("Rub", "Games necklace", "Burthorpe");
		assertSame(t, rules("Rub\tGames necklace\tsub-menu\tyes\tcache").apply(t, facts));
	}

	@Test
	public void theWorkspacesFileHasOnlyTheListsKept() throws Exception
	{
		// the confirmed lists; most Rub lists are left out (players use the item's right-click options instead)
		ListKeys k = ListKeys.load(new java.io.File(".."));
		List<String> used = new ArrayList<>();
		for (ListKeys.Rule r : k.rules())
		{
			if (r.confirmed)
			{
				used.add(r.option + " " + r.target);
			}
		}
		assertEquals(List.of("Travel Spirit tree", "Travel Minecart", "Rub Pendant of Ates"), used);
		// the Lovakengj minecart's list (sent by the server; written from the Wiki's picture)
		assertEquals("Minecart → Arceuus (1)", k.apply(MenuTextBuilder.choice("Travel", "Minecart", "Arceuus"), facts).title(MenuText.ARROW));
		assertEquals("Minecart → Shayzien West (C)", k.apply(MenuTextBuilder.choice("Travel", "Minecart", "Shayzien West"), facts).title(MenuText.ARROW));
		MenuFacts lent = new MenuFacts();
		k.lend(lent);
		assertEquals(12, lent.listed("Travel", "Minecart").size());
		assertEquals("Port Piscarilius", lent.listed("travel", "minecart").get(9));
		assertEquals("Games necklace → Burthorpe", k.apply(MenuTextBuilder.choice("Rub", "Games necklace", "Burthorpe"), facts).title(MenuText.ARROW));
	}

	@Test
	public void theFileMustBeWellFormed()
	{
		assertThrows(IllegalArgumentException.class, () -> rules("Rub\tGames necklace\tsub-menu\tyes"));
		assertThrows(IllegalArgumentException.class, () -> rules("Rub\tGames necklace\talphabetical\tyes\tx"));
		assertThrows(IllegalArgumentException.class, () -> rules("Travel\tMinecart\tlisted: \tyes\tx"));
	}

	@Test
	public void theSpiritTreesOrderIsTheLastDestinationTextsByVarbitValue()
	{
		EntityOpsDefinition e = new EntityOpsDefinition();
		e.setOp(0, "Travel");
		e.setOp(1, "Talk-to");
		e.setOp(2, "Last-destination");
		// stored out of order: sorted by the varbit's value
		e.setConditionalOp(2, "Gnome Stronghold", 65535, 20252, 2, 2);
		e.setConditionalOp(2, "Tree Gnome Village", 65535, 20252, 1, 1);
		e.setConditionalOp(2, "Battlefield of Khazard", 65535, 20252, 3, 3);
		assertEquals(List.of("Tree Gnome Village", "Gnome Stronghold", "Battlefield of Khazard"), MenuFactsLoader.lastDestinations(e));
		// another varbit, or a gap in the values: not this list
		EntityOpsDefinition gap = new EntityOpsDefinition();
		gap.setOp(0, "Last-destination");
		gap.setConditionalOp(0, "Tree Gnome Village", 65535, 20252, 1, 1);
		gap.setConditionalOp(0, "Battlefield of Khazard", 65535, 20252, 3, 3);
		assertNull(MenuFactsLoader.lastDestinations(gap));
		EntityOpsDefinition other = new EntityOpsDefinition();
		other.setOp(0, "Last-destination");
		other.setConditionalOp(0, "Somewhere", 65535, 1234, 1, 1);
		assertNull(MenuFactsLoader.lastDestinations(other));
	}
}

package com.bestpath.tool.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The house scan's furniture rules (names and options as in cache #2735). */
public class HouseObjectsCliTest
{
	private static String thing(String name, String ops)
	{
		return HouseObjectsCli.thing(name, List.of(ops.split(",")));
	}

	@Test
	public void builtFurnitureIsRecognised()
	{
		assertEquals("ornate jewellery box", thing("Ornate Jewellery Box", "Teleport Menu,Edgeville,Remove"));
		assertEquals("mounted glory", thing("Amulet of Glory", "Edgeville,Karamja,Draynor Village,Al Kharid,Remove"));
		assertEquals("mounted mythical cape", thing("Mythical cape", "Teleport,Remove"));
		assertEquals("mounted mythical cape", thing("Mounted Mythical Cape", "Admire,Teleport,Take"));
		assertEquals("mounted xeric's talisman", thing("Xeric's Talisman", "Glade,Teleport menu,Configuration,Remove"));
		assertEquals("spirit tree", thing("Christmas-Spirit Tree", "Travel,Last-destination,Remove-decorations"));
		assertEquals("spirit tree & fairy ring",
			thing("Spiritual Fairy Tree", "Tree,Ring-Zanaris,Ring-configure,Ring-last-destination,Remove"));
		assertEquals("portal nexus", thing("Portal Nexus", "Teleport,Teleport Menu,Configuration,Remove"));
		assertEquals("portal:Grand Exchange", thing("Grand Exchange Portal", "Grand Exchange,Varrock,Toggle,Remove"));
		assertEquals("portal:Weiss", thing("Weiss Portal", "Enter,Remove"));
	}

	@Test
	public void theSameNamesOutsideTheHouseAreNot()
	{
		assertNull(thing("Fairy ring", "Zanaris,Configure,Last-destination,Favourites"));
		assertNull(thing("Spirit Tree", "Travel,Talk-to,Last-destination,Guide,Clear"));
		assertNull(thing("Obelisk", "Activate,Teleport to Destination,Set Destination"));
		assertNull(thing("Ornate Jewellery Box", ""));
		assertNull(thing("Portal", "Enter,Home,Build mode,Friend's house"));
		assertNull(thing("Jewellery box space", "Build"));
		assertNull(thing("Mounted Max Cape", "Admire,Teleport,Take"));
		assertNull(thing(null, "Remove"));
	}
}

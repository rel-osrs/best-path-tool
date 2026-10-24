# Transport networks: adding a stop yourself (fairy rings and others)

A **network** is a set of stops where you can travel from any stop to any other
(fairy rings, spirit trees, gnome gliders, quetzals...). The networks are listed in
`data/manual/networks.tsv` and shown in the Data Tool's **Networks** tab. Every
ride of a network needs the network's own requirement (for fairy rings: Fairytale I,
the Fairy Godfather's permission in Fairytale II, and a dramen or lunar staff unless
the elite Lumbridge & Draynor Diary is done) and costs the stop's ride ticks plus the
network's "extra ticks".

This page says how to add a new stop, step by step, and how to check that routes
use it.

## How routes use a fairy ring

A fairy ring stop sits on the **middle of the ring** (the "Fairy ring" object, which
is solid on our map). Routes:

- **get on** from one of the four tiles beside the middle (north, east, south or
  west; not the corners, and never through a wall);
- **arrive** on the middle of the other ring (as in the game) and then take one
  step off it (1 tick) to any of the eight tiles around it that can be walked on.

This works for every network stop on a solid tile, not only fairy rings. A stop on a
tile that can be walked on is used right on that tile, as before. So for a new fairy
ring, put the stop on the **middle of the ring**.

## Adding a new stop (example: a new fairy ring)

You need the ring's code and place (e.g. "XYZ, near the new town") and where it is
on the map.

1. **Open the tab.** In the Data Tool, click the **Networks** tab at the bottom.
2. **Pick the network.** In the list on the left, click **Fairy ring**.
   The right side now lists its stops and rides.
3. **Find the ring on the map.** Move the main map to the new ring. Turn on the
   "3D map" checkbox if it helps you see the ring.
4. **Click the middle of the ring.** Press **V** (the Pointer tool) and click once on
   the tile in the middle of the ring. The tile's numbers show at the bottom of the
   window ("Tile x, y, plane").
5. **Open the form.** In the Networks tab, under the list of stops, click
   **Add a stop...**. A window opens.
6. **Fill it in:**
   - **Name:** the network's name is already there. Type the code and the place
     after it, for example `Fairy ring: XYZ (near the new town)`.
   - **Tile:** x, y and plane are filled in from the tile you clicked in step 4.
     Check them. The line under them tells you what routes will do with this tile:
     - green "This tile is solid (e.g. the middle of a fairy ring): routes board the
       stop from the north, east, south, west side ... Fine." means all is well;
     - green "This tile can be walked on ..." is fine too (the stop is used right
       there), but for a fairy ring it usually means you clicked next to the middle;
     - orange "... none of its four sides can be walked on ..." means routes can't
       use the stop: check that you clicked the right tile.
   - **Ride:** the ticks of one ride to this stop. It starts with the usual value of
     the network (5 for fairy rings). Leave it unless you know better.
   - **Also needs:** only what THIS stop needs on top of the network's requirement,
     for example `quest Lost City` or `varbit 1234 >= 5`. The line under the box
     shows it in words (red if it can't be read). Leave it empty if nothing more is
     needed. The window also shows what every ride already needs.
   - **Comes from:** where you got it, e.g. `Wiki: Fairy ring` or `seen in game`.
7. **Click OK.** The stop is saved at once (like every edit in the tool; **Ctrl+Z**
   undoes it). The map jumps to it and selects it, and it appears in the stop list.
   The route data is rebuilt by itself about a second later.

To change a stop later: click it in the Networks tab's stop list (or on the map with
the Pointer tool). Its requirement is in the yellow **Needs** box (edit the line and
press **Apply**); its name and ticks are in the bar above the map (change them, then
click **Set on selected**).

Other networks work the same way: pick the network in step 2 and click the stop's
tile in step 4. For stops where the player stands next to an NPC or an object that
can be walked past (a glider, a minecart), click the tile where the player stands.

## Checking that it works (Route tab)

1. Click the **Route** tab at the bottom.
2. Next to **Player:**, pick **Maxed** (Maxed can use everything; a player without
   the fairy ring quests can't use any ring).
3. Press **R** (the Route tool). **Left-click** a start somewhere far away, then
   **right-click** a destination a few tiles from your new ring.
4. The route is drawn at once and listed in the Route tab with its ticks. A route
   that uses your ring has a step called "Fairy ring: XYZ (...)": the player walks to
   a ring, rides to yours, and steps off.
5. If the route doesn't use your ring, try a start next to another ring and a
   destination right next to yours (a ring ride is 5 ticks, so the route takes it only
   when walking or a teleport is slower). Still nothing? Check the stop's Needs line,
   and the tile line in the form (step 6 above).

## For developers

- Core: `Transport.usedFromBeside()` marks network rides (`RouteTransports` in the
  tool, `PlayerTransports` in the plugin); `core/path/Beside` decides the boarding
  and stepping-off tiles from the walking map and the walls; `FastSearch`,
  `TileSearch` (brute-force checker), the coarse bounds and the reachability check in
  `Pathfinder` all follow it. `Pathfinder` takes the walls (tool: tile flags; plugin:
  the export's line-of-sight walls). Tests: `BesideTest`, `NetworkStopTest`.
- Nothing in the data files changed: a stop is still one tile (start = end) of kind
  NETWORK. The plugin export format is unchanged.

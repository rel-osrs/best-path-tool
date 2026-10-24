# Sailing spec (rules + cache findings)

The Sailing rules the code follows. Findings were checked in cache #2735, or are ideas
from the Turning Circles plugin (no code copied). Undecided points are at the end.

## Goal
- The ocean is its own part of the pathfinder, separate from walking land.
- Destinations can be on land **or at sea**, so the sea must be pathable to
  any sea tile (not only dock-to-dock).
- The player switches between land and sea only at docks and mooring points
  (more may be added by hand). Those objects have boat-specific options.

## The sea in the cache (checked)
- Sea tiles are **not blocked** (tile setting 0), so today's walking search
  crosses the ocean. That must change.
- Sea = plane-0 tiles whose floor overlay has a sailing water texture:
  130-189 (families of about five shades, one family per sea area) and 208
  (water beyond the world edge); plus the coastal shallows, overlays 442 and
  444 (old water texture 1), a ring along the main sea's coasts.
  About 4.7 million tiles. Rivers and lakes use other overlays.
- The texture families split the sea into about 12 areas. Some area borders
  (and the world edge) are one-tile-wide lines of blocked sea tiles.
- In the tool: layer "Sea (sailing)"; the Inspector shows each tile's water
  texture.

## Sea areas and pieces
- **Areas** (`core/sea/SeaArea`): open water (every family not listed under
  Restricted waters, plus the coastal shallows), the five restricted areas,
  and the water beyond the world edge (texture 208). The layer colours by area.
- **Pieces** (`core/sea/SeaPieces`): sea tiles a boat can sail between, split
  by blocked tiles and walls (boats cannot cross the white lines). Built with
  the manual changes applied (about 0.6 s per data rebuild).
- Checked in cache #2735: the restricted areas touch open water directly (no
  border line), so boats sail into them; the border lines separate whole seas
  instead. Result: 1,895 pieces. Main ocean 2,457,987 tiles (all five
  restricted areas are in it); then a sea east of the main continent
  (1,002,036 tiles, x 2950-3975), the world-edge ring (514,638), a north-west
  sea (313,005, x 1016-1693, y 3039-4167), a south-west sea (249,286,
  x 1016-1599, y 2104-2744), a few small seas (9,098 near 2516-2667,
  3483-3621; 6,521 and 1,134 far north) and about 1,840 pools under 50 tiles
  (rocks and piers enclosing water).
- On the layer, sea outside the main ocean is striped dark; the Inspector says
  "main ocean" or "cut off from the main ocean".
- 174 shallows tiles touch restricted water; they count as open water.

## Docks (checked)
- Sea side: `Buoy` with option "Dock" (ids 59769-59827, 62395-62396).
- Land side: `Gangplank` (59832 Board / Board-previous / Board-friend,
  59831 Disembark) or `Mooring point` (59834 / 59833), placed as multilocs
  59835-59893, 62397-62398.
- About 61 placed buoys, each a few tiles from its gangplank or mooring point.
- **Requirements are in the cache** (docks need quests and/or levels):
  DB table 194 (`SailingDock`, RuneLite gameval names) has 61 rows: dock id,
  name, Sailing level (column 4), quest (column 5, a row of the quest table,
  e.g. 57 = The Fremennik Trials for Rellekka). No coordinates. Buoy object id
  = 59769 + dock id for docks 0-58, and 62395 / 62396 for docks 59 / 60
  (Wyrmscraig, Wyrmscraig Cavern); spot-checked by place (Port Sarim, the
  Pandemonium, Land's End, Musa Point, Hosidius).
- Levels range from 1 (Port Sarim, the Pandemonium) to 87 (Grimstone).
  Quests named: Children of the Sun, Corsair Curse, Fremennik Trials, Royal
  Trouble, Regicide, Fremennik Isles, Song of the Elves, Swan Song, Lunar
  Diplomacy, Making Friends with My Arm.

## Named seas (cache, not used yet)
- DB table 201 (`SailingSea`): 6 oceans and about 70 seas, each with a
  "required level" (column 5) and a centre tile (column 7); no outlines.
  Example: Kharidian Sea, level 1, centre 3103, 2968.
- DB table 200: sea hazard names (Stormy seas, Fetid waters, Fetid pool,
  Crystal-flecked waters, Profane waters, Sunbaked seas, Tangled kelp, Icy
  seas, Scalding seas, Endless winter, Jagged reefs). No table links boat
  parts to hazards.

## Boat movement (modelled on the Turning Circles plugin)
- 16 headings, 22.5 degrees apart. The heading turns at most one step per tick.
- Speed in tiles per tick (multiples of 0.5). Acceleration and speed boosts
  are ignored: the boat moves at its normal top speed.
- Positions move in quarter tiles (so a sea grid of 4x4 sub-tiles per tile).
- Boat stats come from varbits (`SAILING_SIDEPANEL_BOAT_BASESPEED` etc.,
  value / 128) in the plugin; the boat's outline is a rectangle (its world
  entity bounds) that turns with the boat. Our cache library cannot read the
  boat outlines, so the tool needs fixed sizes.

## Collisions
- The boat hits land and objects in the sea (rocks). Use the blocked tiles we
  already have.

## Restricted waters (areas shown on the "Sea (sailing)" layer)
| Area | Water textures (family) | The boat needs |
|---|---|---|
| Tempor Tantrum | 135-139 | at least an oak mast (weaker masts cannot enter); oak to mahogany pass with a warning, camphor and up without one; routes do not avoid it when the mast is oak or better |
| Fremennik / northern waters | 160-164 | a torch |
| Crystalline waters | 170-174 | an adamant keel |
| Fetid water | 175-179 | an inoculation station |
| Kelp area | 180-184 | an adamant helm (not a hull) |

All other water textures are open water. The Inspector names the area.

## Rules
- Boats cannot cross the blocked border lines (so the blocked tiles stop them).
- Boats can sail in the coastal shallows.
- Docks need quests and/or Sailing levels (found in the cache, see Docks).
- Seas have no entry level: where a boat may go depends only on the boat's parts (the table 201 "required level" does not gate entry).

## Docks paired, sea closed to walking
- `tool/extract/DockExtractor` -> `core/sea/Dock`: 64 entries for the 61 docks
  (Wyrmscraig and Wyrmscraig Cavern have two gangplanks each; Prifddinas has a
  second copy at 3182, 6076, far north, on water cut off from the main ocean).
  Each has name, Sailing level, quest name (table 0 column 2), land object
  (gangplank or mooring point), buoy, and the land tiles the player stands on.
- Pairing: no dock id on the objects (no params), so by the id pattern above,
  checked by distance (every pair within 12 tiles; pairs over 20 are refused).
- Gangplanks (docks 0-31, 59, 60) stand on piers stored one plane up over
  the sea (bridged; walked on plane 0) with exactly one walkable pier tile next
  to them. Mooring points (docks 32-58) are 3x3 objects on the shore, partly
  on sea tiles, with 2-6 walkable land tiles beside them.
- Every buoy is a solid 2x2 object; the open sea around it is the boat's side.
  All buoys are on the main ocean except the Prifddinas copy and Wyrmscraig
  Cavern (a 276-tile cave pool at 2578, 8608).
- Walking: every sea tile is now blocked floor in the walking data (the
  player goes to sea only at docks). 4,566,333 tiles closed; sub-regions
  40,593 -> 38,311, entrances 12,396 -> 6,874. Done after the manual changes
  and after the sea is split into pieces (boats still use the open sea).
  The same block is applied in the route check, speed test and export CLIs.
- Checked after blocking: every dock's stand-on tile is still connected to
  the land (one of Ynysdail's three is a 2-tile pocket; the other two are
  fine). None of the 23 confirmed transports has an end on the sea. 84 hidden
  sub-regions were on the sea and now match nothing (harmless).
  Four route examples have both ends at sea and cannot be walked any more.
- In the tool: layer "Docks (sailing)"; the Inspector shows a dock's details
  on or next to its buoy or land object. Check: `uiCheck --args=docks`.

## More rules
- Players cannot enter the seas cut off from the main ocean (east,
  north-west, south-west, world edge, small pools).

- Wyrmscraig Cavern (dock 60): its buoy is in a cave pool no sailable water
  joins to the main ocean; boats get there by the boat-only transport "Cave"
  (see "Boat-only transports" below).
- The Prifddinas dock at 3182, 6076 is a duplicate; the duplicated water
  around it (dungeon area) cannot be reached. The tool drops a dock copy off
  the main ocean when the same dock is also on it
  (`DockExtractor.withoutDuplicates`): 63 docks shown.
- Boarding and leaving the boat take 10 ticks each (`Dock.BOARD_TICKS`).

## Boats and shipwrights: rules
- The boat stays at the dock where it was left, so where each boat is must be
  tracked. The player has up to 5 boats.
- Some docks let the boat be moved to the dock the player is at (docks
  with a shipwright nearby, for players with access to it).
- Boat sizes and speeds: from Turning Circles if it has them, else research.
  Keep the boat half its width away from obstacles unless that is needed to
  pass a chokepoint. Speed applies once the boat is chosen.
- A coarser sea graph is fine. Sea destinations end on the boat at that tile.

## Boats (checked in cache #2735)
Turning Circles has no table of sizes or speeds; it reads the live boat's
outline and speed varbits in the game. The cache has them:
- DB table 166 (`SailingBoat`): Raft "Tiny (1x3)", Skiff "Small (2x5)",
  Sloop "Medium (3x10)" (column 7; column 6 is the size class 1/2/3); level
  1 / 15 / 50. A 4th row is a quest boat (Will and Anne's, temporary).
  Rafts have no keel option.
- Speed comes from the hull (table 178, column 13 -> stats row in table 164):
  base speed (column 24) and speed cap with boosts (column 25), value / 128
  tiles per tick, as the Turning Circles varbits. Base speed rises from
  1.5 (wooden / oak) to 2.0 (teak / mahogany), 2.5 (camphor / ironwood) and
  3.0 (rosewood), the same for all three boat types.
- Restricted waters in the stats rows match the table above:
  sails storm resistance (column 21) 1 from oak, 2 from camphor (Tempor
  Tantrum); keel crystal-flecked resistant (column 30) from adamant; helm
  tangled kelp resistant (column 31) from adamant; Inoculation station
  facility (table 176) fetid water resistant (column 29); Eternal brazier
  (table 185, Sailing 78) icy seas resistant (column 32).
- Varbits per boat slot 1-5 (RuneLite names): `SAILING_BOAT_n_TYPE`,
  `_PORT` (where it is docked), `_HULL`, `_KEEL`, `_SAIL`, `_STEERING`,
  `_TELEPORT_FOCUS`, facility hotspots. `SAILING_BOARDED_BOAT_LAST_DOCK`.
- Teleport focus (facility, Sailing 55, table 176): "teleport the boat to the
  dock or mooring point you are at". Greater teleport focus (Sailing 75):
  that, and teleporting the player to where the boat is docked.
  Boat options (table 149) include "Summon Boat" twice: one needing a teleport
  focus, one not.
- 16 NPCs named "Shipwright ..." (no places in the cache), and exactly 16
  docks have a "Shipwrights' Workbench" within 25 tiles: Port Sarim, the
  Pandemonium, Land's End, Musa Point, Catherby, Port Piscarilius,
  Brimhaven, Port Khazard, Civitas illa Fortis, Corsair Cove, Aldarin, Port
  Roberts, Red Rock, Rellekka, Deepfin Point, Prifddinas.

## Teleport focus, shipwright and the northern waters
- A teleport focus brings the boat to any dock. Without one, a shipwright
  brings it for coins (the boat's retrieval cost) and **10 more ticks**.
  Each shipwright belongs to a dock.
- A greater teleport focus is also a teleport point (to the boat's dock).
- The northern waters need the **Eternal brazier**, built on the boat.

## Shipwrights
- 16 NPCs have "Recover-boat" (cache): the 15 "Shipwright ..." NPCs and
  Junior Jim (the Pandemonium). Shipwright Seb only repairs boats. The cache
  has no NPC places; their tiles come from their Wiki pages
  (`data/wiki/shipwrights.json`, development time, see DATA_SOURCES §4).
- Each is within 40 tiles of exactly one dock, and these are exactly the 16
  docks with a "Shipwrights' Workbench": Port Sarim (Sam), the Pandemonium
  (Junior Jim), Land's End (Sasha), Musa Point (Sally), Catherby (Sophie),
  Port Piscarilius (Spencer), Brimhaven (Steve), Port Khazard (Sarah),
  Civitas illa Fortis (Sennia), Corsair Cove (Soraya), Aldarin (Silas),
  Port Roberts (Scott), Red Rock (Sylvia), Rellekka (Sigrun), Deepfin Point
  (Sornik), Prifddinas (Silrien).
- The fee is the boat's retrieval cost (cache table 166 column 18; the game's
  script checks "You need N coins"): raft 250, skiff 3,750, sloop 50,000.

## The sea search and mixed routes
- **Boats** (`core/sea/Boat`, `BoatType`): kind, hull (speed), sails, keel,
  helm, Inoculation station, Eternal brazier, teleport focus, where moored
  (a dock, "at sea where the route starts", or unknown). In the tool: Route
  tab, "Boats ..." (five slots); the plugin reads the game's varbits.
- **The ocean grid** (`SeaGrid`): every main-ocean tile with its area, the
  steps a boat can take (8 directions, walls respected, no cutting a corner
  of land) and its clearance (tiles to the nearest obstacle, up to 5).
- **One sea graph for every boat** (`SeaGraph`: no work
  per boat, all the boat's headings used, turning punished): waypoints
  every 4 tiles on the main ocean, straight links in 16 headings (straight,
  diagonal and 2:1, the boat's 22.5-degree headings; 2:1 is 26.6). Each link
  keeps its smallest clearance, the areas it crosses and whether it touches
  a harbour (5 tiles round each buoy). Built once per sea, about 0.2 s.
  Docks, starts and destinations at sea join waypoints by straight lines
  within 10 tiles.
- **A boat's rules** switch links on or off: never below its hard clearance
  (raft 1, skiff 2, sloop 2: half its width must fit) outside harbours
  (every buoy stands by a pier or the shore; 4 tiles was enough for every
  dock in cache #2735); restricted areas only with the right parts. Water
  below the preferred clearance (raft 2, skiff 3, sloop 4) counts 3 times
  (only for a chokepoint; 3 is a tuning value).
- **The search** (`SeaRouter`): over waypoint and heading. Sailing time =
  length / speed. Each 22.5 degrees of turning costs extra (Route tab
  "Turn cost", default 0.5 ticks): this only steers the course to straight
  runs; the route's ticks are the real sailing time.
- **Routes** (`core/path/Sailing.route`), in three parts (exact for a route
  that sails once, as the boat stays where it is left):
  1. the ticks from the start to each dock a boat can be boarded at (one
     Fast search per dock, transports and teleports included; simple until
     sailing is reworked);
  2. per boat, one sea search from every dock it can be boarded at (where
     it is moored; each shipwright's dock +10 ticks and coins; every dock
     with a teleport focus, +5) to every dock and a destination at sea;
  3. the normal search to the destination, each dock offered as a starting
     point at its arrival ticks (leaving included).
  A greater teleport focus is a teleport (5 ticks) to the boat's dock. A
  destination at sea ends on the boat; a start at sea is on the boat marked
  "at sea where the route starts", else the first one. Where the boat does
  not fit (tight water, a rock) it uses the nearest tile within 10 where it
  fits, and the route "stops near the destination" as on land. (See
  "Boats end only at docks or on open sea" below.) Docks need
  their Sailing level and quest (see "Docks check their quests"). A route that sails
  shows: (walk), (bring the boat), Board, Sail (pink on the map), Leave,
  (walk).
- **Speed in the tool** (cache #2735): about 0.2-1 s per route with sailing
  on; walking-only routes as before.
- Not modelled: the boat's length and turning circle (see below).

## Sailing in the plugin: decisions
- Turning circles and boat length are NOT modelled (too difficult and not valuable enough). The turn cost per 22.5 degrees stays the only turning rule.
- Greater teleport focus: the player lands on the dock (then boards, 10 ticks), as the tool already assumed.
- A route asked for while the player is on a boat at sea starts from the boat, where it is.
- Plugin Settings tab, Sailing section: choose which of the player's boats routes may use; allow or forbid a
  shipwright bringing a boat. (Not offered: a Sailing on/off switch, avoiding Tempor Tantrum.)

## Sailing in the plugin, part 1: the sea
- The plugin export has the sea (`sea.bin`: main-ocean tile mask, areas, 12 odd boat steps, 63 docks with levels,
  quests, shipwrights; docs/DATA_FORMATS.md). The plugin works out clearance and the sea graph when it loads (0.17 s)
  and routes sail exactly as the Data Tool's Route tab with Sailing ticked (`exportPlugin` checks it: 0 differ).
- The ocean grid, sea graph and boat search are compact now, in the tool too (same answers, checked tile by tile,
  link by link and route by route on the real map): sailing takes about 5 MB in the plugin instead of about 91 MB.
- Sailing routes do less work for the same answer: walking searches to docks that lower bounds rule out are skipped,
  docks the destination can't be reached from are left out, the sea search stops once it can't change anything.
- Plugin API (part 2 plugs in here): the player's boats go on `PlayerSnapshot` (core `Boat`; the boat the player is on
  is `Boat.AT_START` and the snapshot's tile is the boat's), the Settings choices on `TransportSettings`
  (`withBoatAllowed`, `withShipwrights`, saved as `boatoff=` / `shipwrights=off`). Reading the boats from the game's
  varbits and the Settings tab's Sailing section are part 2. Dev preview: `gradlew uiPreview -PrealData` has a player
  "Maxed + test sloop at Port Sarim".

## More decisions: teleport focus and dock quests
- A teleport focus bringing the boat: 5 ticks (a teleport).
- Docks check their quests (the player's quest progress in the plugin, the test player's quests in the tool).

## Sailing in the plugin, part 2: the player's boats
The plugin reads the player's boats from the game and the Settings tab chooses which ones routes may use.

**What each boat varbit means** (RuneLite gameval names, checked in RuneLite API 1.13.1; meanings read from the client
scripts of cache #2735 that use them, and the DB tables those scripts look in; nothing guessed):

| Varbit (per slot n = 1-5) | Meaning | Where it was read |
|---|---|---|
| `SAILING_BOAT_n_OWNED` | 1 = the slot holds a boat | script 9011 counts slots with value 1 |
| `_TYPE` | boat kind: table 166 column 0 (0 raft, 1 skiff, 2 sloop, 3 Will and Anne's boat, temporary) | script 9010 (`db_find` on that column) |
| `_PORT` | a dock id (table 194 column 0); 253 Lost, 254 Capsized, 255 Bottled (table 147) are no dock | scripts 9094, 8973, 8997 |
| `_HULL`, `_SAIL`, `_KEEL`, `_STEERING` | index into the kind's option list (table 166 columns 25, 26, 24, 28); the rows (tables 178, 179, 177, 181) are wooden ... rosewood / bronze ... dragon in that order for all three kinds, checked by name and stats | scripts 9067, 9069 |
| `_BRAZIER` | 0 none, else value - 1 indexes column 31 (the kind's Eternal brazier, icy seas) | scripts 9067, 9069 |
| `_HOTSPOT_0..12` | 0 nothing built, else value - 1 indexes the option list (table 175 column 2) of the kind's hotspot k (table 166 column 32); the Inoculation station (fetid water) is skiff hotspot 5 / sloop hotspot 9, value 1 | scripts 8805, 9067, 9069 |
| `_TELEPORT_FOCUS` | 0 none; 1 Teleport focus; 2 (or more) Greater teleport focus | script 8997 asks for object 59715 ("Teleport focus", table 176) when 0 and 59716 ("Greater teleport focus") when below 2 |
| `_NAME_1..3` | the boat's name: one word per part from table 187 rows 8545-8547 (value - 1; 0 = the row's default), joined with spaces | scripts 9085, 9086, 9088 |
| `SAILING_BOARDED_BOAT_TYPE`, `_NAME_1..3` | the kind and name of the boat the player stands on | scripts 8757, 9033, 9087 |

The meanings are in the export as `boats.tsv` (docs/DATA_FORMATS.md), made from the cache's tables each export.
Not used: `SAILING_BOARDED_BOAT` (1 bit, read by no client script: only as a "something changed" signal),
`SAILING_BOARDED_BOAT_LAST_DOCK` (read by no client script, meaning not verified), sail patterns, flags, trims,
names of the facilities other than the fetid-water one.

**The boat the player is on**: RuneLite's public `WorldEntity` (the top-level world view's entity with the player's
world view id, as RuneLite's own `Perspective` finds it). Only the player's own (`getOwnerType() ==
OWNER_TYPE_SELF_PLAYER`), matched to a slot by kind and name; it becomes `Boat.AT_START` and the route starts from the
boat's world tile. On someone else's boat, or when two of the player's boats share kind and name,
routes from the sea don't sail (the Settings tab says why). Following the route still pauses on a boat.

**Not used for routes, but listed**: a boat at a place that is no dock, an unknown kind or part value, the temporary
quest boat. (Bottled, capsized and lost boats were listed here at first; since part 3 routes use them, see below.)

**Sailing level**: RuneLite's `Skill.SAILING` (API 1.13.1), real level, read with the other levels.

**Docks check their quests**: `Sailing.Options` takes the player; a dock whose quest is not
finished is not used to board (shipwright, teleport focus) or leave a boat. The game's own dock check (script 9137 over
table 194 column 5) asks each listed quest to be finished (script 4136: quest state 2); each dock lists at most one.
16 docks have a quest (Children of the Sun, The Corsair Curse, The Fremennik Trials, Royal Trouble, Regicide, The
Fremennik Isles, Song of the Elves, Swan Song, Lunar Diplomacy, Making Friends with My Arm), all names RuneLite knows.
A boat moored at a dock is still boarded there. The plugin reads those quests (they are among the requirements it
watches); the Data Tool uses its test player's quests. `exportPlugin`: 0 of 636 routes differ (new set-up: Sailing
99 without quests).

**Settings tab, Sailing**: one row per boat (the game's boat sprite, kind and name, where it is, a "use for routes"
box; greyed when routes can't use it), "A shipwright may bring my boat (costs coins)". No boats: a short note.

## Sailing in the plugin, part 3: capsized, lost and bottled boats
**Rules**: capsized and lost boats may be used through a shipwright, exactly like a normal retrieval (same
coins, 10 ticks more), only when "A shipwright may bring my boat" is ticked. A bottled boat is used only when the
player has its bottle in the INVENTORY; it can then be released at any dock, counted like boarding (10 ticks), no
coins. Boat and Sail step cards show the boat's picture. "Use for routes" stays per slot.

**What the cache shows about the bottles** (cache #2735, read with a dev-only probe; nothing guessed):
- Items: 31989 "Boat bottle (empty)" (options Bottle-boat, Drop; noted form 31990, placeholder 31991) and 31992
  "Boat bottle (full)" (options Unbottle-boat, Check, Destroy; no noted form, no placeholder; params 295 = 1 and
  2655 = 1; item opcode 160, which RuneLite's cache loader stores as `stackable = 2`, meaning not documented).
  RuneLite names them `ItemID.SAILING_BOAT_BOTTLE_EMPTY` / `SAILING_BOAT_BOTTLE_FULL`. No other item has "bottle"
  with "boat" or "ship" in its name.
- **One full-bottle item for every boat**: the item itself does not say which boat is in it. The boat's slot says
  it is bottled (`SAILING_BOAT_n_PORT` = 255, table 147 "Bottled"). Each slot also has
  `SAILING_BOAT_n_BOTTLE_PREVIOUS_PORT` (varbits 19261, 19299, 19337, 19375, 19413; 8 bits; by its name the dock the
  boat was at before bottling): client script 9005 reads it, but no client script calls 9005. Not used.
- The game's boat lists (table 149, "boat selection types"; script 8996 picks the row from varbit 18553, script
  8997 says why a boat is left out): "Boat Bottling" (type 12, "Bottle") leaves out bottled, capsized and lost boats
  and boats not at the current dock (columns 9-12). "Summon Boat" (types 6 and 7, the teleport focus) and "Teleport
  to Boat" (type 8, the greater focus) leave out bottled, capsized and lost boats (columns 9-11): so routes never use
  a focus for them. "Recover Boat" (type 5, the shipwright) leaves out none and shows the retrieval cost.
- There is **no "unbottle" boat list** in the table: the client data does not show how the game chooses the boat to
  release when several boats are bottled (it may ask on the server's side).
- Script 8997's messages: a capsized boat "... You'll need to speak to a shipwright to recover it."; a bottled boat
  "... You'll need to unbottle or recover it first." (a shipwright can also recover a bottled boat; routes don't use
  that: bottled boats only with the bottle).

**How routes use them** (core `Sailing`, the same in the Data Tool and the plugin):
- Capsized / lost (`Boat.CAPSIZED`, `Boat.LOST`): boarded only at a dock with a shipwright, when shipwrights are
  allowed: "Shipwright Sam brings Boat 1: Sloop to Port Sarim (Recover-boat, 50,000 coins; it is capsized)", 10
  ticks at the shipwright, then the walk to the dock and Board (see "Routes walk to the shipwright" below). No teleport focus (see the lists above).
- Bottled (`Boat.BOTTLED`): **choice made because the bottles can't be told apart**: the bottled boats are used
  only when the inventory holds at least as many full boat bottles (item 31992) as the player has bottled boats,
  counting boats the Settings tab switched off (a bottle carried could hold one of those). Then each bottled boat's
  bottle is surely carried. With fewer bottles none of them is used (never a guess which boat a bottle holds).
  Released at any dock the player may use (level, quest): "Release Boat 1: Sloop from its bottle at Port Sarim
  (Unbottle-boat)", 10 ticks, no coins, then Board (10 ticks). A bottle in the bank does not count.
- A route that starts at sea is never on a capsized, lost or bottled boat.
- The boat the player is on: if the route's first step is not to sail it somewhere, it is considered lost and
  recoverable. A route may sail it from where it is, or leave it at sea (a teleport first);
  then it is lost: a shipwright may bring it to their dock like any lost boat (coins, 10 ticks more, only when
  shipwrights are allowed), never a teleport focus. Before, a boat left at sea was gone for the rest of the route,
  so a route that teleported off it could only summon another boat.
- Settings, Sailing: "Never leave my boat at sea" (off by default, so the faster path is not blocked): when ticked,
  a route from the boat the player is on starts by sailing it to a dock with a shipwright (the rule is in the sea
  search's ends, as it stops at the best end) or to an open-sea destination and goes on from there any way
  (teleports too); no route leaves it at sea. Saved as `keepboat=on`. Coins are not counted either way.
- Sailing steps' ids name the boat's slot (`sail-board:n`, `sail:n`, `sail-leave:n`, `sail-bring:n`,
  `sail-release:n`, `sail-teleport:n`; `Sailing.boatSlot`), so the plugin's cards show that boat's picture.
- The plugin reads such boats from the game (`SnapshotMapper`, by the boat table's status labels), watches the full
  bottle in the inventory (an input like a requirement item) and searches again when the bottled boats become
  usable or not. The Data Tool: Route tab, Boats ..., "Moored at" Capsized / Lost / Bottled and "Full boat bottles in
  the inventory"; `uiCheck` name suffixes `-capsized`, `-bottled` (bottle carried). `exportPlugin` compares both.

## Sailing needs: Sunbleak Island, Ynysdail, the Shipyard
Checked on the exported sea (cache #2735) and the Wiki (development time): nothing new was needed in the model.
- **Sunbleak Island** (dock 49): already covered. The cache gives the dock Sailing 72 (table 194), and its buoy lies
  in the Kelp area (textures 180-184, the tangled kelp), which only a boat with an adamant helm or better may enter
  (`Boat.canEnter`). Without the kelp area no water joins Sunbleak or Rainbow's End to the ocean. Wiki "Sunbleak
  Island" rev 15356377 agrees (Sailing 72, not boostable; adamant helm). Checked with real routes from Deepfin Point
  (`SailingNeedsRealDataTest`): steel helm or Sailing 71 never leaves the boat there, adamant helm and Sailing 72 do.
- **Ynysdail** (dock 54): the rules are covered (Sailing 73 from the cache, Wiki "Ynysdail" rev 15356378 says 73;
  its buoy is in the Crystalline waters, which need an adamant keel). The same keel rule is checked on Lledrith Island
  (dock 53, same waters, Sailing 66): steel keel no, adamant keel yes. No boat reached Ynysdail until the sea graph's
  channels (below).
- **The Shipyard**: not a route destination; nothing added. Wiki "Shipyard" rev 15303173: an island south-west of the
  Isle of Souls (about 2078, 2720) with no dock (an invisible barrier stops boats outside its harbour); the player is
  taken there by talking to a shipwright ("Customise-boat", bank PIN asked) while their boat is docked at that
  shipwright's port, and logging out there puts them back beside that shipwright. It is where boats are customised,
  an interface trip, not a place a route leads through. Making it a destination would need a way in that depends on
  where the boat is moored during the route (today only `Sailing.route` knows the boats) and a known way out; both
  are open design questions, below.

## Boat-only transports
The custom transport t-1791778058879-1927 "Cave" (the inlet at Wyrmscraig 2559,2203 <-> the Wyrmscraig
Cavern pool 2571,8606) is a sailing transport that only a boat can use, so the sailing search has to be able to use
transports. In the game the boat goes in by clicking or by sailing into it, comes out in the pool by the Wyrmscraig
Cavern dock, works both ways, the player stays on the boat; 10 ticks; it cannot be sloop sized (rafts and skiffs only). The cache's
"Cave" object at the inlet's end (62217 at 2562,2203) has no menu options.
- **Data**: a transport's `largestBoat` (Data Tool: Requirements..., "Only a boat can use it": rafts only / rafts and
  skiffs / every boat); `transports.tsv` flag `s` and the width (`s2`). Requirements, the Settings tab, skipping and the
  blacklist work as for any transport. -1927 is set up so: two-way, 10 ticks, rafts and skiffs.
- **Water**: the water at a boat-only transport's ends belongs to the boats' grid even when cut off from the main ocean
  (core `SeaGrid.build(..., joinedAt)`, `sea.bin`): the cave pool (276 tiles) and so dock 60 can now be sailed to.
- **The search** (core `Sailing`): the walking search never sees such a transport (`PathQuery` keeps them apart). The
  sea search goes in stages, which is exact: from the boardings to the ends and to each such transport's start; then,
  quickest first, on from each transport's end reached (its cost so far plus the transport's ticks). Each stage sees
  only the ends in the part of the sea graph it starts in. The lower bounds that rule docks out count the way through
  these transports (straight lines to and from them plus their ticks), so a dock far away in a straight line but near
  through the cave is still tried.
- **Route**: Sail (to the transport), the transport's own step (its id and name: "Cave"), Sail on, Leave (or the boat
  stops on an open-sea destination). A start on a boat in the pool sails out the same way.

## Channels: crooked water the links miss
A destination at 2220,3452 (open water in the Ynysdail bay) used to say "No route"; instead the boat is tried in there
if it can fit.
- **Why it said no route**: the Ynysdail water (about 6,500 tiles, x 2156-2282, y 3427-3585; Crystalline waters, so an
  adamant keel) joins the ocean only through a crooked channel among rocks west of it (about 2139-2199, 3452-3455)
  where no tile is more than 2 from an obstacle. A skiff or sloop fits tile by tile (hard clearance 2), but none of the
  sea graph's straight links between waypoints 8 tiles apart threads it.
- **Channels** (core `SeaChannels`): for each hard clearance boats have (2: skiffs and sloops, then 1: rafts) the
  waypoints are put in pieces joined by the links such a boat may use (and the channels found before); the water such
  a boat fits is flooded tile by tile from every waypoint at once, and where two floods from pieces not yet joined
  meet, the way between the two waypoints through that spot is a channel (straightened into a few stretches, each a
  straight line a boat may sail both ways). One channel per place where two pieces meet. Cache #2735: 102 channels.
- **Search**: a channel is used like a link by boats that fit it (its narrowest water at least their hard clearance)
  and may enter its areas; its cost is its length at the boat's speed, three times in tight water (below the boat's
  preferred clearance), as a link's. The course follows its stretches, pulled straight as usual. The "no route"
  explanation tries the player's boats with parts that enter every area too, and names what was missing ("Its parts
  can't sail into Crystalline waters (an adamant keel).").
- **Where they are made**: flooding the sea takes about 1 s and 25 MB, so the Data Tool finds them and the plugin
  export stores them in `sea.bin` (version 2); the plugin only reads them. The tool's own sailing finds the same ones
  (the export checks they read back the same).
- **Not covered**: pieces joined only by links through a restricted area (a boat without that part could still have a
  crooked open-water way round); a raft is not told that it has no keel.
- Real routes (`SailingNeedsRealDataTest`): a sloop or skiff with an adamant keel reaches Ynysdail (Sailing 73) and the
  bay at 2220,3452 from Port Piscarilius; a steel keel or Sailing 72 does not.

## Sea charting (checked in cache 2735)
- 358 tasks in DB table 154, counted in sets: 70 seas, 4 "Bonus" groups (Drinks, Currents, Dives, Weather) and 7 oceans
  (table 201). Each has a done varbit (18574 + task number). Kinds: find (generic), spyglass, current duck, drink crate,
  weather troll, mermaid guide.
- Sailing level per task (client script 9001): the highest of crate 12 / current duck 22 / mermaid 38 / weather 57, the
  hazard's (Stormy seas 24, Fetid waters 40, Crystal-flecked waters 66, Tangled kelp 72, Icy seas 78) and the needed
  dock's level. The seas' own "required level" (table 201 column 5) is not used for tasks.
- Experience: per task by kind (35 to 250), per sea / bonus group by its level (enum 5870), per ocean (enum 5869).
- Tools: Spyglass, Current duck, Crowbar, diving gear (script 8706 / 8868).
- Not in the cache: where each task is (Quest Helper's list is used); which boat part each hazard needs
  (not guessed; see below).
- Hazards and boat parts: each charting hazard is one of the restricted waters
  above: Stormy seas = Tempor Tantrum, Fetid waters = Fetid water, Crystal-flecked waters = Crystalline waters, Tangled
  kelp = Kelp area, Icy seas = Fremennik / northern waters. A task in a hazard counts as doable now only when one of
  the player's boats may enter that water (`ChartingProgress.hazardArea`, `Boat.canEnter`).

## Open points
- **Prifddinas portals**: boat-only
  transports for the Prifddinas ocean's portals are not entered yet, and not guessed: which portals, which water, where
  each one's ends are, its ticks and which boats fit (entered in the Data Tool as boat-only transports, "Only a boat
  can use it"; the search needs nothing more).
- **Shipyard as a destination?** Only if wanted: a way in from each shipwright's dock that needs the
  player's boat moored there (a boat-state requirement the transport model doesn't have), and the way out (only
  "logging out puts you beside the shipwright" is documented).
- **Shipwrights' own requirements** (Wiki "Shipyard" rev 15303173, for taking the player to the Shipyard): Land's End
  and Port Piscarilius "must have visited Kourend", Red Rock "partial completion of The Red Reef" (more than the docks'
  table 194 quests). Whether the same holds for "Recover-boat" (a shipwright bringing the boat, which routes use) is
  not documented; not applied.
- Clue steps with several places: the place is chosen on foot, then the route to it may sail; when no place can be
  reached on foot the first four are tried with the boats. Whether sailing should take part in choosing is undecided.
- Several bottled boats: how the game picks the boat a full bottle releases is not in the client data (in-game test).
  Until then routes need one bottle per bottled boat.
- Releasing a bottled boat: routes release it at any dock, for 10 ticks; whether the game allows every dock (or only
  some, like the docks a boat can be bottled at) is not known.

## Routes walk to the shipwright
When a shipwright is used, the player is pathed to them (the boat used to be brought at the dock's land tile, 10
ticks there, without the route going to the shipwright NPC).
- **Where the shipwright is**: its spawn tile from its Wiki page (`data/wiki/shipwrights.json`, development time,
  DATA_SOURCES §4; no new web requests: all 16 have a tile). Junior Jim's page shows two Pandemonium tiles and one far
  away: the tile nearest the dock is used (`tool/.../wiki/Shipwrights`). Carried in core `Dock`
  (`withShipwright(name, x, y, plane)`, `getShipwrightTile()`) and in `sea.bin` version 1 (docs/DATA_FORMATS.md; old
  files still load, without places).
- **Where the player walks to** (core `Sailing.standTile`): NPCs wander a little and players talk to them from a tile
  next to them, so the route goes to the tile west, east, south or north of the Wiki tile that has a walking-map
  component (not a closed-off pocket) and is nearest the dock's land tile (Chebyshev, then straight-line distance);
  if none, the Wiki tile itself; if that can't be walked on either, the boat is brought at the dock as before. Picked
  from the walking map alone, so the Data Tool and the plugin choose the same tile (`exportPlugin` checks it and
  prints the 16 tiles).
- **The route** (core `Sailing`, the same in the Data Tool's Route tab with Sailing ticked and in the plugin): (walk to
  the shipwright, transports and teleports allowed as before), "Shipwright Sam brings Boat 1: Sloop to Port Sarim
  (Recover-boat, 50,000 coins)" there (`RECOVER_TICKS`, 10, unchanged), the walk on foot from the shipwright to the
  dock's land tile, Board, Sail, Leave, (walk). Costs: the two real walks plus the 10 ticks. Nothing else changed:
  the boat is still brought to the shipwright's own dock, coins are still not checked, a teleport focus (5 ticks, no
  walk) still wins where it is cheaper. The docks' lower bounds that skip searches stay lower bounds (going by the
  shipwright is never shorter than the shortest way to the dock), so no route changes because of them.
- **Plugin**: the bring-the-boat card ("Recover-boat" with the shipwright's name, the boat's picture as before) sits
  where the walk reaches the shipwright (its marker tile), and the walk is drawn up to there; then a walk to the dock
  and the Board card.
- Open: the shipwright's tile is a Wiki spawn tile (NPCs wander); the walk from the shipwright to the dock is on foot
  only (they are at most about 40 tiles apart).

## Faster sailing, 16-direction courses drawn as arrows
Sailing searches had to be fast, and courses are drawn as vector pointers (arrows) rather than tiles, because a tile
path drifts from the true travel angle. A boat travels in 16 directions (N, NNE, NE, ENE, E, ...), courses are
straight lines in those directions; speed comes first, even if sea routes get a little coarser.

**Where the time went** (measured before): the sea search (about 0.5 s each time
it ran, it searched the whole ocean whenever no walk bounded it), and walking searches to docks (7.5 per route with a
moored sloop, 29 with a teleport focus, about 17 ms and 7.5 MB each).

**What the search does now** (core `SeaRouter`, `Sailing`; the Data Tool and the plugin alike):
- **The sea search is aimed** (A*): each end says what at least follows it (leaving the boat, and the least walk on
  from that dock to the destination: straight walking, or a transport's landing, no search: `Sailing.lowerBoundsOn`);
  a waypoint's estimate is the straight distance to the nearest end's water at the boat's speed plus that. The
  estimate never overestimates, so every leg's cost is still its least cost. Legs that cannot end the route before
  walking does are not searched; ends out of reach even in a straight line are left out at once; with the
  destination at sea the docks are no ends (no walk ends at sea).
- **Walking searches to docks only when needed**: each boarding starts at a lower bound of its ticks (no search).
  Whenever the best leg to an end that may still end the route in time starts at a boarding whose real ticks are
  not known, those ticks are searched (the same Fast search as before, also the walk to a shipwright) and the sea
  searched again. In the end every leg that matters starts at real ticks: the same legs as searching every dock
  first. 100 routes needed 124 walking searches to docks instead of 834.
- **Coarser ocean** (reduces the search set): waypoints every 8 tiles instead of 4, a quarter of them
  (38,448 instead of 153,688); links are 8 tiles (straight), 11.3 (diagonal) and 17.9 (2:1). Checked on the real sea:
  every dock reaches exactly the docks it reached before, for rafts, skiffs and sloops with and without parts (all
  63 x 63 docks); legs are about 1 % longer. Since links are longer, a link's harbour mark now means its tight water
  is all inside harbours (before: a link touching a harbour anywhere could pass a narrow gap elsewhere on it); this
  changed no dock's reach either.

**Courses in 16 directions** (`SeaGraph.join`, `SeaRouter.Result.course`):
- The links already go in the boat's 16 headings (east, 2:1, diagonal, 1:2, north, ...). The joins from a dock's
  water, a start or a destination at sea to the first and last waypoints were straight lines at any angle; now
  they are at most two legs in neighbouring headings (every step splits into whole steps of two neighbouring
  headings), each leg checked tile by tile (clearance, areas, walls); of the two corners the one with wider water.
- After the search the course is **pulled straight**: from each corner, the furthest later corner that at most two
  such legs reach where this boat may go, and no tighter than the water they replace, is joined directly (with
  waypoints 8 tiles apart a way may otherwise jog from one row of waypoints to the next). This never makes the
  course longer. The Sail step's ticks are the course's length at the boat's speed (the same speed rule as before:
  tiles per tick from the hull, the straight length of each leg).
- A Sail step keeps its tiles (the boat's centre, tile by tile along each leg, as the walking-style progress needs)
  and now also its corners (`Route.Step.getCorners()`: tile indexes where a leg starts or ends).

**Drawing** (plugin): the game scene, the minimap and the world map draw a Sail step as straight lines corner to
corner with arrow heads pointing the way (at each leg's end and every 8 tiles in the scene, 12 on the minimap,
70 pixels on the world map; `overlay/CourseArrows`); walks stay tiles. The current step is drawn from the player's
tile on (route progress still follows the course's tiles). The Data Tool's Route tab still draws the sea tiles.
(Superseded the same day: exact 22.5-degree headings, one arrow head per leg, the Route tab draws arrows too; see
"Boat headings exactly 22.5 degrees apart" below.)

**Boat names on the cards** (the boat cards always use the player's boat name in the title):
core `Boat.label()` is the player's own boat name as the game shows it (the name words read from the boat varbits),
else "Boat 1: Sloop" as before. Cards: "Recover-boat Shipwright Sylvia -> Ancient Age", "Board Ancient Age", "Sail
Ancient Age to Remote Island", "Leave Ancient Age at Remote Island", "Summon Boat -> Ancient Age (teleport focus)",
"Unbottle-boat -> Ancient Age", "Greater teleport focus: to Ancient Age at Port Sarim"; the "no route" cards name
the boat the same way. "Board" no longer says "Gangplank" / "Mooring point" (the boat's name instead).

## Boat headings exactly 22.5 degrees apart; one arrow per leg; scene lines clipped
Seen in play: a boat sailing north-north-east diverged from the path shown, there should be one arrow head per line, and
the lines in the game world disappeared when looking away from where they start. The Data Tool's Route tab draws
courses as arrows too; the card titles "Summon Boat -> <boat> (teleport focus)" and "Unbottle-boat -> <boat>" stay;
routes up to about 3 % longer from the coarser ocean are fine.

**Cause**: our 16 headings were tile ratios (east, 2:1, diagonal, 1:2, north, ...): north-north-east was 1 east for 2
north, 26.6 degrees from north. A boat's 16 headings are exact compass points 22.5 degrees apart (north-north-east =
22.5 degrees), so a 47-tile leg drifted about 3 tiles off the boat's real line. Source: observed in play. Not
confirmed from the cache or RuneLite: the public API has `WorldEntity.getOrientation()` without units in its
javadoc; Turning Circles (ideas only) reads it as 2048 units per turn (so 16 headings would be multiples
of 128). Turning Circles also rounds each tick's movement to a quarter tile (its `SailingMath.getVelocity`), which
would bend the real per-tick angle a little depending on speed; that is not modelled (undocumented mechanics are not guessed; open
point below).

**What changed** (core, so the Data Tool and the plugin alike):
- `SeaGraph.EX` / `EY`: the 16 headings as exact unit vectors (built from one octant, so north-east has exactly equal
  steps); `compassDegrees(d)`; `exactHeadingOf(dx, dy)` (only east, north-east, north, ... are exact for whole-tile
  steps).
- `SeaGraph.exactLine`: an exact straight line between real points (a tile's middle is its whole x / y), walked over
  EVERY tile it passes (a supercover; where it passes a tile corner exactly the step is diagonal and the two tiles
  beside the corner count too); the boat's clearance, area and harbour rules are those of the tile-by-tile `line`.
- `SeaGraph.exactTwoLegs`: from one tile to another in at most two legs in the exact headings either side of the
  way (the corner is a real point, not a whole tile); of the two corners the one with wider water; a tie: the one
  that goes on in the leg before (fewer turns).
- `SeaRouter.Result.course` (`exactCourse`): the coarse sea search, its waypoints and links are unchanged (the links
  are still the search's 2:1 grid; only the course's geometry changed). The way found is pulled straight as before,
  now with exact legs; a stretch that two exact legs can't follow (tight water: the exact corner strays up to about a
  tile from the grid's straight line) is split along its tiles until each part fits, down to single steps along east,
  north-east, north... (exact headings themselves). Legs in one heading one after the other are one leg.
- `SeaRouter.Course`: `points` (the exact corners), `headings` (per leg), `tiles` (the legs sampled at most a tile
  apart, rounded: route progress and following), `corners` (tile indexes, in order; two may share a tile when a leg
  is shorter than a tile), `length` (exact). The Sail step's ticks are still the course's length at the boat's speed.
  `Route.Step.getCoursePoints()` carries the exact corners; the plugin's `RouteStep.withCourse` / `cornerX` /
  `cornerY`.

**Drawing** (head place superseded the same day: on the leg's end, see "Arrow heads at the turns"): exactly ONE
arrow head per leg, in the middle of the part of the leg that shows (on the world map: inside
the map's area; in the scene and on the minimap: the longest run of the leg on the screen; in the Route tab: inside the
view), pointing the way. The game scene and minimap no longer draw a leg only between its first and last tiles on
screen: `overlay/CourseScene` cuts each leg to the loaded scene (`WorldView` base and size) and samples it about a
tile apart as local points, made only when the route or the scene's base changes (`overlay/SceneCourses`); each frame
projects the samples (`Perspective.localToCanvas` may be null) and draws each run of neighbouring samples that all land
on the screen as one straight line (a straight leg stays straight on the flat sea in perspective and on the minimap).
The world map draws the exact corners (no simplification for courses) and splits the current leg at the player's exact
place on it. The Data Tool's Route tab draws a Sail step's exact legs with one arrow head each (`RouteOverlay
.drawCourse`), the old tile line only for a course without corners.

**Effect on routes** (memoryCheck's sailing pass, Maxed with a sloop at Port Sarim, the same routes compared one by one before and after
this change): 400 routes, 119 sail before and after, none switches between sailing
and walking, none found / not found differently; total ticks -3..+2 (at most 2.2 % longer), 50 the same; sail ticks
13,068 -> 13,066 in all; legs 509 -> 541 (a stretch between two points that is not along an exact heading takes two
legs). `exportPlugin`: 0 of 999 routes differ between the Data Tool and the plugin; the exported files are the same
(only the date line).

**Open point**: Turning Circles rounds each tick's movement to a quarter tile: if the game does that, the real
track at some speeds is not exactly 22.5 degrees (e.g. at 1 tile per tick, north-north-east would move 0.5 east and 1
north per tick, 26.6 degrees). Is the green heading line in the game the boat's real track? A side-by-side screenshot
of a long north-north-east leg would settle it.

## Arrow heads at the turns

The arrow head goes at the end of a leg (only turns need an arrow). About 6 % more legs from exact headings are
accepted, choosing whatever has the least performance impact.

**Drawing**: each leg's one arrow head now has its tip on the leg's END: the turn into the next leg, or the course's
end. When that end does not show, the leg has no head (the next turn that shows has its own). Everywhere the same
rule (core `CourseLines.endHead`, plugin `CourseArrows.endHead`):
- Game scene and minimap (`CourseScene`): a leg can have a head only when its end lies in the loaded scene
  (`endsInScene`, kept when the scene is made: the clipped part reaches the leg's end) and its last sample lands on the
  screen. The head points back along the leg's unbroken run of samples on the screen, walking back until a sample is
  a head's length away; samples behind the boat are projected then only if needed, so the leg the boat is on keeps its
  head up to the turn. Per frame this adds at most a few projections (no allocation); still nothing but projecting.
- World map (`WorldMapPainter`): a head where the leg's end is inside the map's area; the player's leg measures from
  the corner where it began (so its head stays until the turn); the done part up to the player has none.
- Data Tool Route tab (`RouteOverlay.drawCourse`): a head where the leg's end is inside the view.
- Unchanged: a leg shorter than the head gets none.

**Course building**: kept as it is (exact 22.5-degree legs, about 6 % more legs than before). Nothing cheaper was
obvious: fewer legs would need an extra merging pass after each sea search (more work per route), and the drawing
cost per leg is a few points, made once per route.

## Boats end only at docks or on open sea

A coastal tile nobody can stand on ends on land in sight of it rather than on the boat beside it. The boat only paths to
places the player can get off, or the open ocean: if the destination is on land at all, the boat is considered only if
the search finds a docking point near it (in the natural search algorithm, not as a special-case fix). A blocked spot out
in the open ocean (a rock, nothing to stand on in sight within 10 tiles) is not "no route": it ends on the nearest open
water.

The route's real destination is still settled once, before any search (core `RouteDestination.resolve`; the plugin's
`PluginRouter` and the Data Tool's Route tab both use it):
1. **Open sea** (a main-ocean tile, `Sailing.isOpenSea`): the tile itself, on the boat. The boat stops with its hull over
   it: the tile itself when the boat fits there (its hard clearance), else the nearest tile within half the boat's width
   where it fits (raft 0, skiff 1, sloop 1.5 tiles; `Sailing.destinationFit`); none: that boat can't end there. Before:
   the nearest fitting tile within 10.
2. **Anything else is on land**: the walking rule's tile (the tile itself, else the nearest reachable tile within 10 that
   sees it, else the nearest reachable one), worked out with the land tile of every dock the player may use counted as
   reachable (`Sailing.routeEndByDocks`: each dock is a place the player can be, exactly as part 3 of the route search
   offers it). So the boat takes part only through docks: sail to a dock, leave the boat, walk on. A tile on an island
   only a dock leads to, or a shore tile seen from a rock by an island, is reached that way; the plain walking rule's
   tile is its fallback when no route by boat is found.
3. **A rock far out at sea** (a tile no one can stand on, not ocean, and no tile that can be stood on within 10 tiles has
   it in sight, when rule 2 gives no tile in sight): the nearest main-ocean tile (straight distance, within 10; ties:
   lower x, then lower y) that one of the player's boats can be at by the sea search's own data (fits there, its parts
   allow the sea area, joins the sea graph; `Sailing.openWaterNear`). The route ends there on the boat; the last card
   says "The spot itself is blocked: this stops N tiles away, on open water." (`RouteDestination.OPEN_WATER` as the
   stop's 4th value; Data Tool status line "(the spot itself is blocked: on open water)", map label "Stops here (open
   water)"). Its fallback is rule 2's tile, if any.

The old case "an unwalkable tile in the water with nothing reachable near: the tile itself, the boat stopping beside it"
is gone; so is `Sailing.landReachableBySea` (rule 2 covers it). `Sailing.route` ends a boat's course only at a dock
(Leave, then the walk) or on an open-sea destination; `Sailing.atSea` (open sea, or an unwalkable tile with ocean within
10) is now only used for STARTS (a player on a boat). "No route" explained (`NoRouteReasons` / `SeaWay`): an open-sea
place, or a rock out at sea, is explained by boat to the same tile as the route (`SeaWay.destination` uses
`openWaterNear`); a place with nothing to stand on within 10 tiles and no ocean near it says "Nobody can stand there".

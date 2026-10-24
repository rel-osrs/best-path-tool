# Data formats

This document covers the files the BestPath Data Tool produces and the
plugin reads. Each format has a version number. Readers refuse versions they
don't know.

## Collision (movement) data — `collision.bin`, format version 1

**Code:** `core/src/main/java/com/bestpath/core/collision/` (`CollisionMap`,
`CollisionMapCodec`). The plugin and the tool share it.

**What it stores.** For every walkable region-plane (64×64 tiles on one
plane), each tile has 4 "can step" bits:

| Bit | Meaning |
|---|---|
| 1 | north: (x, y) → (x, y+1) |
| 2 | east: (x, y) → (x+1, y) |
| 4 | north-east: (x, y) → (x+1, y+1) |
| 8 | north-west: (x, y) → (x−1, y+1) |

**The other four directions** are read from a neighbour's bit:
- south = north bit of (x, y−1)
- west = east bit of (x−1, y)
- south-west = north-east bit of (x−1, y−1)
- south-east = north-west bit of (x+1, y−1)

This works because steps are symmetric in our model (see `MovementRules`).
The diagonal bits already include the "no corner cutting" rule.

**Layout.** The whole file is gzip-compressed. Big-endian, as written by Java
`DataOutputStream`:

| Field | Type |
|---|---|
| magic `"BPCM"` | 4 bytes |
| format version (1) | int |
| cache id (OpenRS2 archive id) | int |
| cache game build | int |
| count of region-planes N | int |
| N × { key = `regionId << 2 \| plane`, keys strictly ascending | int |
|       step bits: tile index `localX << 6 \| localY`, 4 bits each, 16 tiles per long, low bits first } | 256 × long |
| CRC32 of every byte above | int |

- **Pockets left out:** closed-off pockets (no entrance, at most 16 tiles)
  are unwalkable in the exported file. The tool still shows them, in grey.
- **Region-planes left out:** a region-plane with no possible step is
  omitted. Tiles in it, and tiles outside the cache's regions, are not
  walkable.
- **Size** (cache #2735, doors open, no-floor tiles and pockets left out):
  3,962 region-planes, **1.07 MB** compressed (2 KB each uncompressed).
- **Regenerate:** the Data Tool's "Update plugin data..." (or `gradlew -p tool exportPlugin`).

## How the flags are derived (extractor)

`tool/src/main/java/com/bestpath/tool/extract/CollisionExtractor.java` first
builds full per-tile flags, using the game's bit layout (RuneLite's
`CollisionDataFlag` values). It derives them from these cache inputs:
- floor tiles with the "blocked" setting;
- the bridge setting, which shifts a tile down one plane;
- walls and corners by shape type and orientation;
- the footprint of solid objects;
- blocking floor decorations.

Doors and gates (wall-shaped objects with an "Open" option) count as **open**
Tiles with no floor are not walkable.

**These rules describe the game client's behaviour**; they are not stated in
the cache. They were checked once against the game's own collision data
(106,028 of 106,032 tiles matched; the check tooling has
since been removed).

## Region review — `data/manual/region-review.json` (tool only)

The manual check of each 64x64 region, kept by the data tool's
Region review tab. Bookkeeping only: the export never reads it, and it is
not a change record (it is not in `changes.jsonl`, Ctrl+Z does not undo it
and it does not appear in the Change log).

```json
{
  "12850": {
    "TRANSPORTS": {"status": "COMPLETE", "note": "Castle stairs checked.", "updated": "2026-10-10T12:00:00Z", "author": "Rel"},
    "VARBITS": {"status": "NEEDS_WORK", "note": "...", "updated": "...", "author": "..."}
  }
}
```

- Keys: region id (`regionX << 8 | regionY`), sorted; categories `TRANSPORTS`,
  `VARBITS`, `ITEMS`, `SUBREGIONS`.
- `status`: `NOT_CHECKED`, `NEEDS_WORK` or `COMPLETE`. A category that is not
  checked and has no note is left out; a region with nothing left is left out.
- A region is complete when all four categories are complete, needs work if
  any category needs work.

## Compacting the change list — `data/manual/changes.jsonl` (tool only)

The change list only grows: every change, undo and re-save is a new line. The Change log tab's **Compact...**
button (`ChangesCompactor`) rewrites it with only what is needed:
- undo lines (`REVERT`) and the changes they undid are taken out;
- of the changes to one thing (same transport id, tile, door, wall side, hidden or kept area, region, candidate,
  test-list entry, sea channel, place kind or charting task), only the last is kept; two exceptions keep one more
  line: a tile made walkable and later blocked again keeps its last "walkable" (it clears more flags than
  "blocked" adds back), and a place kind answer without a name keeps the last answer that gave a name;
- a transport whose last change is a delete keeps only the delete;
- `FREE_LAND` and `CACHE_CHECK` lines are never taken out.

Kept lines are the original text, unchanged (same id, who, when, cache, note). Each goes where the FIRST line about
the same thing was (for a transport or test-list entry: the first since it was last deleted or taken off), because
the order things were first added is the order of the exported transports. The Change log sorts by time, so it
still reads newest first.

Before writing, it checks the compacted list builds exactly the same data as the old one, and stops with the file
untouched if anything differs. That covers transports and their order, walls and tiles, doors, areas, every card
answer and the latest change of each thing. The old file is copied first to
`cache/backups/<date>-before-compact/` (with a README saying how to put it back). The file is replaced in one step,
and only if no change was added to it meanwhile. Lost on purpose: the rows of undone and replaced changes in the
Change log, the notes written on replaced versions, and Ctrl+Z for changes made before compacting. Git keeps every
earlier version of the file.

## Transport requirements (tool only for now)

Each transport record in `data/manual/changes.jsonl` may carry:

| Field | Meaning |
|---|---|
| `requirements` | what the player needs, one requirement line (below); empty = nothing. For a two-way transport: start to end, and end to start too unless `requirementsBack` is set |
| `requirementsBack` | two-way only: the line for end to start when it differs (empty string = nothing needed that way); absent = the same as `requirements` |
| `requirementsSource` | where they came from (Wiki page + revision, Shortest Path, cache) |
| `questOnly` | true: only during a quest (instance, quest battle); never used for normal routes |
| `canFail` | true: the player can fail it (Agility obstacle); still its normal tick cost |
| `teleportCategory` | teleports: the category whose adjustable tick cost it uses (`data/manual/teleport-categories.tsv`: `category <tab> ticks <tab> description`); empty = its own `ticks` |
| `wildernessLimit` | teleports: deepest Wilderness level it works from (20, 30; 0 = not in the Wilderness); absent = no limit |
| `cooldown` | true: a long cooldown (home / grouping teleports). With teleport category "home teleport" or "grouping teleport" the routes leave it out while the player's cooldown runs (core `req/Cooldown`: game varps 892 / 888, 30 / 20 minutes; docs/PLUGIN_PLAYER_STATE.md); with another category it is always ready (no known game var) |
| `playerNote` | text the plugin shows the player on this step, for what the route does not track (weight, "no weapons", landing damage); empty = none |
| `largestBoat` | only a boat can use it: the widest boat that fits, in tiles (1 raft, 2 skiff, 3 sloop); 0 or absent = not boat-only (walked to as usual). Two-way and one-way only; both ends on water. Only the sailing search uses it (the player stays on the boat); the water at its ends is the boats' even when cut off from the main ocean (`sea.bin`). Data Tool: Requirements..., "Only a boat can use it". |
| `network` | the network it belongs to (a name in `data/manual/networks.tsv`); needed for network stops, optional for rides (a charter ship trip): the ride also needs the network's requirement and costs its own `ticks` plus the network's extra ticks |

Older records have none of these fields and need nothing.

**Requirement line** (`core/.../req/RequirementText`): parts joined with `;` are all needed;
`one of (A | B)` needs one alternative (an alternative may hold several `;` parts). Parts:
`Agility 61` (real level), `quest Regicide` (done; or `started`, `not started`, `not done` after the
name), `varbit 1990 > 39` / `varp 139 >= 7` (`=` `!=` `>` `>=` `<` `<=` `&` = any of these bits),
`item light source` (an item group) or `item 1755/1756` (ids) with options `x2` (count), `worn`,
`used up`, `coins 200`, `members`, `empty head` (slot: head, cape, neck, weapon, body, shield, legs,
hands, feet, ring, ammo), `answer <text>` (something only the player can say; a sidebar tick),
`house` (owns a house), `house at <place>`, `house has <thing>` (names from `data/manual/house-things.tsv`).
`spellbook <standard|ancient|lunar|arceuus>`, `rune <kind> x<count>` (kinds and their items in `data/manual/runes.tsv`:
the rune, combination runes and sunfire runes count; a worn staff, wand or charged tome of that kind covers it).
`collection log 6570` (an item in the player's collection log; ids or an item group like `item`) and
`spirit tree <place>` (the player's spirit tree there is grown; places: Port Sarim, Etceteria, Brimhaven,
Hosidius, Farming Guild) and `canoe axe stored` (an axe is stored at the canoe stations) are detected by
the plugin, never asked (see
docs/PLUGIN_PLAYER_STATE.md). Test players meet them only with `allUnlocked`.
A check gives met / needs the bank (items or coins only in the bank) / not met, with the reasons.

## Item groups — `data/manual/item-groups.tsv`

One group per line: `name <tab> item ids (comma-separated, from the cache) <tab> source <tab> item
names (for reading only)`. Requirement lines name a group with `item <name>`. Each group is
approved by hand. `gradlew -p tool items --args="<cache folder> <out.tsv>"` lists every cache item to build them.
You can also make and delete groups and add or remove items by id in the Data Tool (Item groups...);
each change is noted at the end of the group's source as `<author> <date>: added / removed <name> <id>`.
A group that a requirement uses can't be deleted or emptied there (the requirement could no longer be read).

## Item group defaults — `data/manual/item-group-defaults.tsv`

One line per group that has a default: `group <tab> item id <tab> chosen by <tab> time <tab> item name (for
reading only)`. The default is the item whose picture the plugin shows when a step needs the group and the player
has none of it. Chosen in the Data Tool (toolbar: Item groups...), which shows every item of each group
as its inventory picture drawn from the cache; a click saves at once.

## Test players — `data/manual/test-players.json` (tool only)

The test players for the route check (Route tab, "Player"), a JSON list. Maxed and Fresh
account are built in and not saved. Each player: `name`, `members`, `allUnlocked` (every quest,
varbit/varp check and answer met), `allItems` (carries everything), `levels` (skill name to real
level; missing = 1, Hitpoints 10), `quests` (name to `done` / `started`), `varbits` / `varps` (id to
value), `inventory` / `bank` (item id to count), `worn` (slot to item id), `answers` (list), `house`
(where the player-owned house is; absent = no house), `houseThings` (what is built in it), `spellbook`
(absent = standard), `pouch` (rune pouch contents, item id to count), `pouchInBank`, `cooldownsUsed`
(true: the home and minigame teleports were just used, so routes leave them out; absent or false = ready).

## Player-owned house list — `data/manual/house-things.tsv`

`kind <tab> name <tab> source`; kinds `place` (the 9 house locations), `thing` (built things that move
the player: jewellery boxes, garden spirit tree / fairy ring / obelisk, mounted glory / mythical cape /
digsite pendant / Xeric's talisman, spellbook altars) and `destination` (portal destinations: each
gives the things `portal chamber: <destination>` and `portal nexus: <destination>`). From the Wiki.

## Transport networks — `data/manual/networks.tsv`

`network <tab> extra ticks <tab> requirement line <tab> description`. A transport whose `network` field
names a line needs that line's requirement as well as its own, and costs its own ride `ticks` plus the
network's extra ticks (the real ride time plus an adjustable extra; the extras are
changed in the file or in the Data Tool, Route tab, "Teleport and travel costs ..."). Two shapes:
**network stops** (kind `NETWORK`, start = end = the stop's tile; every stop rides to every other stop
of the network, a ride costs the destination stop's `ticks` plus the extra; a stop's own requirement is
needed to board or to arrive there) for networks where every stop reaches every other (fairy rings,
spirit trees, quetzals...), and **rides** (ordinary one-way / two-way transports with `network` set) where
a trip has its own price or only some stops connect (charter ships, canoes, magic carpets...).

## Free-to-play land — `data/manual/free-land.tsv`

Where a player on a free world can stand (the free areas are marked; the rest of the map is
members only). The same on every plane (upper floors of a free building are free; dungeons have their own
coordinates). One row per line, `<y> <x1>-<x2> <x3>-<x4> ...` (runs of free tiles, ascending); `#` lines are
comments (the header says how it was drawn and lists every place added by hand). Core class
`com.bestpath.core.collision.FreeLand`.

It is changed in the Data Tool with the **Free land** tool (F): drag a rectangle, left button = free,
right button = members only. Each rectangle is a `FREE_LAND` change in `changes.jsonl` (`x`, `y`, `toX`,
`toY`, `on` = free), applied on top of the file, oldest first. Remote link: `free x1 y1 x2 y2 on|off`,
`isfree x y`. Layer "Free-to-play land" shows it (green).

Route search for a player without members: walking only between free tiles (`CollisionMap.restrictTo`), and
only transports whose ends are on free land (a teleport: its landing). Members skills (Agility, Thieving,
Herblore, ...; Wiki Free-to-play lists the free ones) need members, wherever they appear in a requirement.

## Plugin export v0 (provisional)

> **Provisional.** The format may still change.

Made by the Data Tool's **Update plugin data...** button (toolbar; below) or by
`gradlew -p tool exportPlugin --args="<cacheDir> [outDir] [check routes, default 100]"`
(`tool/.../route/PluginExportCli.java`). Default folder: `src/main/resources/com/bestpath/data/`, so the
plugin jar contains it. The data is built exactly like the Data Tool's data build (cache + every change in
`data/manual/changes.jsonl`, sea closed for walking); afterwards the program reads the files back the way
the plugin does and checks that routes on them are the same as the tool's own search (route examples +
random routes, seven test players: four plain ones, two with a bank, and one with a bank
whose home and minigame teleports are cooling down). It ends with `check: 0 of N routes differ` or stops
with an error.
Reader: `core/.../data/PluginData.java` (pure Java, shared by tool and plugin).

| File | What | Encoding | Size (cache 2735) |
|---|---|---|---:|
| `bestpath-data.txt` | manifest: `format 1`, `cache <id> <build>`, `made`, `fingerprint`, one `file <name> <bytes> <sha256-16>` line per file, `count` lines for people | text, tab-separated | 1 KB |
| `movement.bpcm` | the export movement table (closed-off pockets and hidden sub-regions unwalkable, ignored regions dropped) | collision format 1 (above), gzip | 747 KB |
| `doors.bin` | steps that go through a door or gate | gzip of `"BPDR"`, int version 0, int count, per tile: int cell key, short local index, byte N/NE/E/SE bits (the other 4 directions are the same steps backwards) | 9 KB |
| `walls.bin` | line-of-sight walls (only for choosing where a route stops when the destination can't be reached) | gzip of `"BPWL"`, int version 0, int count, per cell: int key + 128 longs, 2 bits per tile at `(localX<<6|localY)*2`: bit 0 wall on the north edge, bit 1 on the east edge | 244 KB |
| `transports.tsv` | confirmed transports | one line each, below | 569 KB |
| `item-groups.tsv`, `runes.tsv`, `networks.tsv`, `teleport-categories.tsv` | copies of `data/manual/` as they are (line ends written as LF whatever the checkout has) | as described above | 55 KB |
| `free-land.tsv` | free-to-play land with the manual changes | free-land text form | 98 KB |
| `clues.tsv` (optional) | clue steps: places, STASH units, clue items, texts, needs | one line each, below | 140 KB |
| `item-group-defaults.tsv` (optional) | each item group's default item (the picture the sidebar shows for a group when the player has none of it): a copy of `data/manual/item-group-defaults.tsv` | `group <tab> item id <tab> chosen by <tab> time <tab> item name`; the plugin reads the first two columns | 2 KB |
| `sea.bin` (optional) | the main ocean (tile mask, sea areas, the few tiles whose boat steps are not the mask's rule) and the docks | below | 53 KB |
| `boats.tsv` (optional) | what the game's boat varbits mean (boat kinds, part tiers per value, braziers, fetid-water facilities, boat statuses, name words, dock names), from the cache's DB tables | below | 4 KB |
| `banks.tsv` (optional) | the banks a route may start at: the lines of `data/banks/banks.tsv` that have a tile to use them from, first four columns | below | 18 KB |
| `walks.bin` (optional) | the search's walking tables worked out ahead of time: per sub-region the distances between its entrance sides, per transport end its distances to them and to the other ends of its sub-region | below | 456 KB |
| `world-maps.tsv` (optional) | where the game's world maps draw each area: per map (the surface and each underground map) rectangles of game tiles with their shift and planes, from the cache's world map data | below | 9 KB |
| `wilderness.tsv` (optional) | the Wilderness zones: which tiles are in it and which side of its level-20 and level-30 lines, from the game's client script 384 run for every tile of its boxes, safe areas left out | below | 2 KB |
| `places.tsv` (optional) | the places by kind for "go to nearest" (Functions tab): per map icon kind its name, icon picture and every tile the game shows that icon on; the banks not repeated | below | 41 KB |
| `slayer.tsv` (optional) | the Slayer tasks (cache DB tables 113-116), master names and where to kill each task's monsters (Wiki, development time): one line per place | below | 166 KB |
| `charting.tsv` (optional) | the sea charting tasks and sets: text, set, sea, kind, hazard, dock, "done" varbit, Sailing level, experience and tool from the cache; places and tips from `data/charting/charting-places.tsv` (Quest Helper's list) | below | 60 KB |

Cell key = `regionId << 2 | plane`. Walls are kept for every cell of the movement table and its 8
neighbours on the same plane (not in ignored regions): the stop-tile choice looks at most 10 tiles from
the destination, so it sees exactly the walls the tool sees, except inside ignored regions.

**Fingerprint**: SHA-256 over each data file (manifest order: movement, doors, walls, transports, item
groups, runes, networks, teleport categories, free land, then clues, item group defaults, the sea, the boat table, the banks, the walking tables, the world maps, the Wilderness and the places only if the export has them) as
`name 0 size 0 bytes`, first 16 hex digits. The plugin refuses the data (and says why in the sidebar)
when the format is not `1`, a file is missing, or a size, hash or the fingerprint does not
match. `clues.tsv` is optional so that exports made before it stay readable (same fingerprint): when the
manifest lists it, it must be there and match; without it the plugin has no clue steps.
`item-group-defaults.tsv` is optional the same way; without it a group shows its first item. `sea.bin` too:
without it the plugin has no sea routes (walking only, as before). `boats.tsv` too: without it the plugin can't
read the player's boats (the Settings tab says so; no sea routes). `banks.tsv` too: without it routes never start
at a bank. `walks.bin` too: without it the routes are the same, only the first ones through each part of the map
are slower (they work the tables out as they go). `world-maps.tsv` too: without it the plugin takes a spot on the world map as
the game tile with the same numbers (right for the surface, wrong for many underground areas).

**`walks.bin`** (`core/.../path/WalkTables`). The search's slow-to-redo tables (walks inside one 64x64 cell, on the walking map only, so they never
depend on the player): per sub-region the walking distance between each pair of its entrance sides; per transport
end tile (and per set of tiles beside a solid end, fairy rings) its distance to each entrance side of its
sub-region; per sub-region the distances between all its transport ends. The export reads the other files back as
the plugin does, builds the plugin's members search on them and works the tables out for every transport in the
data (`PlayerTransports.everything`, every player's transports are some of these); about 2 s, 2,965 sub-regions,
7,078 end tiles. Kept packed in the plugin (a few arrays of 2-byte steps; a sub-region's side table once for both
directions when it is the same both ways), and the search unpacks the part a route needs into its own caches,
which count in its cache budget. The plugin's members search uses them; a free-world search works its own out as
before. They are made for one numbering of the sub-regions: the file carries a key of the search's layout (cells,
sub-regions, entrance sides and their tiles, `Pathfinder.graphKey`); a search with another layout ignores it, and
the export stops with an error when the plugin's search would not use them. gzip of, big-endian: `BPWK`, int
version 1, long layout key; side tables: int n, n int sub-regions (ascending), n int k, n bytes (1 = one triangle
i < j for both directions, 0 = all k*k), n+1 int offsets, int m, m shorts; tile rows: int n, n int tiles
(ascending), n+1 int offsets, int m, m shorts; beside rows: int n, n long keys (solid tile << 32 | first tile beside
it, ascending), n+1 int offsets, int m, m shorts; end walks: int n, n int sub-regions (ascending), n+1 int tile
offsets, int m, m int tiles, n+1 int row offsets, int m, m shorts (per sub-region its n x n rows); set rows: int n,
n long keys (ascending), n int sub-regions, n+1 int offsets, int m, m shorts. Shorts are steps, -1 = none.

**`wilderness.tsv`** (`core/.../data/WildernessZones`; used for Wilderness teleport limits and for avoiding the
Wilderness). The game works out the Wilderness level it shows ("Level: N") in client script 384 from
the player's tile: nine boxes in order (script 247 tests one: x, plane, y between two packed coordinates), each a
constant level or one counted from y (surface: (y - 3520) / 8 + 1 for x 2944-3391, y 3520-4351; the dungeons below:
(y - 9920) / 8 + 1 for x 2944-3455, y 9920-10879; a few caves a fixed level), 0 outside. The Data Tool runs the script
itself for every tile of its boxes (`tool/.../extract/WildernessScript`: only the instructions those scripts use; an
unknown one stops the export, so a changed script is noticed) on all four planes (a level that differs by plane stops
the export). Only the zone is kept, since only the level 20 and 30 lines matter: 1 = levels 1-20, 2 = 21-30, 3 = 31 and deeper (the teleports' Wilderness limits in the data are 0, 20 and
30). Safe areas are left out (`data/manual/wilderness-safe.tsv`: `name <tab> x,y,plane <tab> source`, one tile inside
each; the area is the walled ground around it on the export's walking map, barriers not walked through, holes closed;
`tool/.../extract/SafeAreas`): Ferox Enclave, from the tile in front of its bank chest, 443 tiles (it is a
safe area, and its chest was the only bank on a Wilderness tile). Rectangles of one
zone, rows with the same runs merged: `x <tab> y <tab> width <tab> height <tab> zone` (x, y the lowest tile), comment
lines `#`. Cache 2735: 518 rectangles (108 before the safe areas were left out: the Slayer Tower basement, the Falador museum underground, the room under Ferox Enclave and Bryophyta's Lair). Without the file no tile is in the Wilderness.

**`charting.tsv`** (`core/.../data/ChartingTasks`; the tasks that help the player chart
seas for bonus experience; anything that gives bonus experience is its own set). From the cache (`tool/.../extract/ChartingExtractor`, checked in cache 2735):
- **Tasks**: DB table 154 (`SailingChartingCore`, 358 rows): column 0 the task number (0-357), 1 its text, 2 the set the
  game counts it in (a row of table 201 `SailingSea`: a sea, or for the 33 bonus tasks one of the four "Bonus Drinks /
  Currents / Dives / Weather" groups, type 2, whose tasks the Captain's Log marks "(Bonus)"), 3 the sea a bonus task is
  in, 4 "gives repeat experience", 5 the kind as a table id (155 generic "Find ...", 156 spyglass, 157 current duck, 158
  drink crate, 159 weather troll, 160 mermaid guide; those tables only point back at 154), 6 a hazard (table 200, e.g.
  "Icy seas"), 7 a needed dock (table 194). The "done" varbit is 18574 + the task number (all 358 checked against
  RuneLite's gameval names `SAILING_CHARTING_*_COMPLETE`; task 167's name says Vagabonds Rest though its text is Porth
  Gwenith).
- **Sailing level** (the Captain's Log's "Sailing requirement", client script 9001): the highest of the kind's level
  (crate 12, current duck 22, mermaid 38, weather 57, otherwise 1), the hazard's (Stormy seas 24, Fetid waters 40,
  Crystal-flecked waters 66, Tangled kelp 72, Icy seas 78, otherwise 1) and the dock's (table 194 column 4). The export
  reads the two switches from the script (a script shaped otherwise stops it). Same as Quest Helper's level for 357
  tasks; task 126 (a mermaid task): Quest Helper 40, the game's rule 38.
- **Experience** (the Captain's Log's "XP awarded", "Sea Completion" and "Ocean Completion"): a task enum 5868 by kind
  table / 10 (generic 35, spyglass 50, crate 75, current duck 125, mermaid 175, weather 250); a sea or bonus group enum
  5870 at its experience level (table 201 column 6 when above 1, else column 5) / 10; an ocean enum 5869 by its row / 10
  (Ardent 5,000, Unquiet 6,000, Sunset 2,000, Western 20,000, Shrouded 30,000, Northern 35,000, Miscellaneous (the four
  bonus groups) 15,000).
- **Tool** (the Captain's Log's "Tool needed", script 8706): spyglass tasks the Spyglass (item 31803), current duck tasks
  the Current duck (31805), crate tasks the Crowbar (31807), mermaid tasks "Diving gear" (the game checks several
  outfits, script 8868: named, not checked); the items from table 161 column 2.

Not in the cache: any task's tile (the 92 placed "Chart" objects 59284-59375 have no link to their task). Those come
from **`data/charting/charting-places.tsv`**, made by `gradlew -p tool chartingPlaces --args="<quest-helper folder>
<cache folder>"` (`ChartingPlacesCli`) from Quest Helper's charting list (used as data with permission): `task <tab> places <tab> source <tab> tip`, places `x,y,plane` separated by spaces (the start,
and for a current duck or weather task its other place), source "Quest Helper ChartingTasksData.java a526461", tip
Quest Helper's text past the game's ("You'll take 5 damage drinking it."). Joined by the done varbit's RuneLite name;
three tasks whose Quest Helper text differs from the game's keep the place but get no tip (281, 284, 286; 281: the game's
text says Wyrmscraig, its set and Quest Helper's place the Lonely Sea).

The plugin file: `#` comment lines; `set <tab> name <tab> type <tab> ocean <tab> xp` lines in the game's order (each
ocean, then its seas and groups; type `sea`, `bonus` or `ocean`; ocean empty for an ocean; xp for completing it); the
header; then `task <tab> varbit <tab> kind <tab> set <tab> sea <tab> level <tab> xp <tab> hazard <tab> dock <tab> repeat
<tab> places <tab> text <tab> tip <tab> tool <tab> tool items <tab> drink <tab> drink name` (sea empty unless a bonus
task; `repeat` is `yes` or empty; places empty = not known; tool items: item ids separated by spaces, empty for no tool
or an unchecked one; drink: a drink crate task's drink, the item the crate gives (empty for other tasks), from
`data/manual/charting-drinks.tsv`, RuneLite's gameval names, checked in the cache; older files without
the two columns read as no drink).
Cache 2735: 81 sets (7 oceans, 70 seas, 4 bonus groups; 74 with tasks), 358 tasks. Without the file the Functions tab
has no sea charting.

**`world-maps.tsv`** (`core/.../data/WorldMaps`). The game
has several world maps, picked in the map list at the world map's bottom left: "Gielinor Surface" and one per
underground area ("Unquiet Ocean Underground", ...). Many underground maps draw an area somewhere else than its game
tiles, sometimes each plane of one area side by side (Dorgesh-Kaan); the surface draws everything where it is.
From the cache's index 19 (WORLDMAP): archive 0 "details" (each map's name), archive 1 "compositemap" (per map its
64x64 map squares and 8x8 zones, each with game place, drawn place, lowest plane and number of planes; RuneLite's
`WorldMapCompositeLoader`). The export merges neighbouring 8x8 blocks with the same shift and planes into
rectangles and leaves out (and prints) blocks that clash within one map: two places drawn on one spot, or one tile
and plane drawn at two spots (cache 2735: none; across maps there are such clashes, so the plugin always names the
map). `#` comment lines, then per map, in the cache's order: `map <tab> id <tab> name` (ids count from 0), then
its lines `area <tab> game x <tab> game y <tab> width <tab> height <tab> shift x <tab> shift y <tab> lowest plane
<tab> highest plane` (drawn = game + shift; width and height in tiles, multiples of 8). Cache 2735: 52 maps, 252
rectangles. The plugin finds the open map by the name its map list button shows (`InterfaceID.Worldmap.
MAPLIST_DISPLAY`, a text of its dynamic children) and converts both ways: a
right-click spot to the game tile drawn there (and its plane when the spot shows only one), the route's tiles to
where that map draws them.

**`slayer.tsv`** (`core/.../data/SlayerPlaces`; every Slayer master, with different places to kill each task's monsters to
compare; one entry per group of monsters). Made
by the export (`tool/.../slayer/SlayerExport`) from the cache's Slayer DB tables (`tool/.../slayer/SlayerTables`) and
`data/wiki/slayer-places.tsv` + `data/wiki/slayer-masters.tsv` (below). `#` comment lines, then tab-separated lines,
each starting with its kind:
- `master <number> <name>`: varbit 4067 (`VarbitID.SLAYER_MASTER`) value -> name. The cache has no names for the
  numbers; they are matched by the Wiki's amounts (cache 2735: 1 Turael, 2 Mazchna, 3 Vannaka, 4 Chaeldar, 5 Duradel,
  6 Nieve, 7 Krystilia, 8 Konar, 9 Spria, 10 Mortimer; 11, which gives only cows and birds, not named).
- `task <row> <id> <name>`: DB table 113 (`DBTableID.SlayerTask`) row, its column 0 `ID` (the value of varp 395
  `VarPlayerID.SLAYER_TARGET`; -1 for a boss) and column 10 `NAME_UPPERCASE`. Cache 2735: 163 tasks.
- `boss <number> <row>`: a boss task (varp 395 = 98) names the boss in varbit 4723 (`VarbitID.SLAYER_TARGET_BOSSID`):
  DB table 116 (`SlayerTaskSublist`) rows with column 0 = 1, column 1 = that value, column 4 = the task row.
- `area <id> <name>`: DB table 115 (`SlayerArea`) column 0 `AREA_ID` (the value of varp 2096 `VarPlayerID.SLAYER_AREA`,
  the area Konar named) and column 3 `AREA_NAME_IN_HELPER`. Cache 2735: 56 areas.
- `place <row> <name> <area> <amount> <multi> <cannon> <safespot> <points> <map> <monsters> <source> <notes>`: one place
  of the task with that row (one entry per group of monsters). `area` the Konar area it is in (`-1` none; matched by
  name to the areas table 114 column 5 lets Konar name for the task); `amount`, `multi`, `cannon`, `safespot` from the
  Wiki's location table (`yes`, `no`, `?` unknown, or the Wiki's words); `points` `x,y,plane` separated by spaces: the
  place's spawn tiles moved onto the export's walking map (a tile with no step out moves to the nearest one within 3
  tiles, or to the same spot on another plane that has ground: the Wiki often gives plane 0 for dungeons the game has on
  another plane, or to the game tile the game's world map draws at that spot, `world-maps.tsv`: the Wiki
  gives many underground areas where the world map draws them; when one tile of a place needs this, every tile of the
  place that can be is put back this way), grouped like the clue kill areas (spawns within 12 tiles, chained), one point per group: its spawn
  tile nearest the group's middle; `map` `yes`, or `no` when none of the place's tiles is on the walking map (an area
  the map data leaves out; its points are the Wiki's own; the plugin shows the place without a Go); `source` the
  Wiki page and revision (`page@revision`); `notes` the Wiki's notes (`-` none). Cache 2735: 897 places for 155
  tasks, 5 off the map data (instances), 1,810 points (with `data/manual/slayer-overrides.tsv` and
  `data/manual/slayer-extra-places.tsv`, below).
- `icon <row> <item>`: the task's picture (`data/manual/slayer-icons.tsv`).
- `loot <kind> <key> <items> <note>`: special loot shown on a place's card (item numbers separated by spaces, `note`
  `-` none). `area <name>`: every place whose name starts with it; `master <number>`: every task from that master
  (both from `data/manual/slayer-loot.tsv`); `place <task row>:<place name>` (special loot that tells places apart): that place of that task, made by the export from
  `data/wiki/slayer-place-monsters.tsv` and `slayer-drops.tsv` (below): the game's collection log items (the cache's
  log, enum 2102) the place's monsters that are not bosses drop, without long bones, curved bones and champion
  scrolls, and only those not every place of the task (whose monsters are known) has; none for a boss task; one line
  per group of monsters, note "From Spiritual mage.". Cache 2735: 82 place lines.
Without the file the Functions tab's Slayer part says so.

**`data/manual/slayer-overrides.tsv`** (hand-authored; read by the export before the places are put
on the map). `#` lines, then the header `task place change value source`, tab-separated; a line matches the
`slayer-places.tsv` line with that task row and place name (a line matching none is reported by the export).
`place-shift` `<dx>,<dy>,<plane>`: every Wiki tile of the place moved by dx, dy and put on that plane (the Chasm of
Fire: the Wiki draws it 64 tiles west of its game tiles, levels stacked south, matching no world map of the cache);
`skip-place` `-`: the place is left out (the Isle of Serpents, which no one can reach).

**`data/manual/slayer-extra-places.tsv`** (hand-authored; read by the export after
`data/wiki/slayer-places.tsv`, in its columns, below, and put on the map the same way): places the Wiki's location
tables leave out. First use: the 11 Wilderness spider places (Krystilia's spider task had none; the Spiders task page,
rev 15250979, lists every spider kind as counting), tiles from the spider monster pages' `{{LocLine}}`s as saved in
`cache/wiki/slayer` (`tile_pages` names each page and revision), `amount` = how many spawns they list. Second use:
38 places for the monsters that count for a task (their page's
`{{HasTask}}`) but are in no place of it, one per `{{LocLine}}` location (made with a scratch run of the tool's own
spawn reader, `WikiSlayerCli.fromSpawns`; `page` / `revision` the monster's page): cow calves, the tormented demons'
Ancient Guthixian Temple, earthen nagua, scarab mages, three scorpion kinds, undead ones, crypt / jungle / Ape Atoll /
Ungael spiders, vampyre juveniles at Crombwick Manor, desert / ice / jungle wolves. Not added: bosses, superiors,
locations a place of the task already covers, Mogre (sea), Scorpia's offspring, Scarab Swarm.

**`data/wiki/slayer-places.tsv`** (generated by `gradlew -p tool wikiSlayer --args="<cacheDir> <main checkout>/cache/wiki"`,
`tool/.../wiki/WikiSlayerCli`, development time only; CC BY-NC-SA 3.0). `#` lines, then the header
`task task_id task_name place konar_area amount multi cannon safespot monsters tiles page revision tile_pages notes`:
one line per place with every Wiki spawn tile (`x,y,plane` separated by spaces, `-` none), the task page and revision
the place comes from, and `tile_pages` the monster pages (`page@revision`) whose `{{LocLine}}`s gave the tiles (`-`:
the tiles are the location table's own map pins). **`data/wiki/slayer-masters.tsv`**: `master name wiki_key matched
compared pages`: which master number is which master, and how many of the cache's amounts (table 114 columns 3-4)
matched the Wiki's.

**`data/wiki/slayer-place-monsters.tsv`** and **`data/wiki/slayer-drops.tsv`** (generated by `gradlew -p tool
wikiSlayerDrops --args="<cacheDir> <main checkout>/cache/wiki"`, `tool/.../wiki/WikiSlayerDropsCli`, development time
only; CC BY-NC-SA 3.0). `slayer-place-monsters.tsv`: `task place monsters`, one line per line of
`slayer-places.tsv` and `slayer-extra-places.tsv`: the monster pages there (` / ` between, `-` none known): the
monsters the place's `amount` names ("6 Vampyre Juvinates, 8 Vyrewatch, 19 Vyrewatch Sentinels"; on a dragon task
"Adults", "Babies", "Brutals") and the pages its tiles came from (`tile_pages`), else its `monsters` column (many
task pages repeat the task's whole list there). `slayer-drops.tsv`: `monster revision version item rarity plain_drops
only_in`: each drop line of those monsters' pages (the page's own `{{DropsLine}}`s and its page-specific
`{{Uniques/...}}` table; the shared rare, gem, herb and seed tables are not read) whose item is in the game's
collection log; `version` the drop table's (`Ancient Prison` for "Drops (Ancient Prison)", `-` the plain one),
`plain_drops` whether the page has a plain table, `only_in` the place a note limits the drop to ("Only dropped in
Ruins of Tapoyauik", `-` none). The export decides where a version counts: the plain table everywhere the monster
is; "Drops (X)" at the places whose name has X; an X no place has everywhere only when the page has no plain table
(levels), else nowhere (the Ancient Prison's spiritual creatures have no Slayer place).

**`banks.tsv`** and **`data/banks/banks.tsv`** (`core/.../data/BankList`; made by `gradlew -p tool banks`,
`tool/.../route/BanksCli`). A route may BEGIN with a bank visit (never part-way). `#` comment
lines, the header, then one tab-separated line per bank booth, bank chest or banker:
`bank <tab> kind <tab> needs <tab> use`, where `bank` is the bank's name (booths of one bank share it: one "bank"
for the route search's "3 cheapest banks"), `kind` is `booth`, `chest` or `banker`, `needs` a requirement line
(empty = nothing; e.g. `members; quest Lost City`, or the var values that show the bank form of a booth that
changes with a varbit), `use` the tiles a player stands on to use it, `x,y,plane` separated by spaces. Columns after
the fourth are read past: the Data Tool's file adds `name x y plane object source note` (the object's or banker's
own name and tile, the cache object id or NPC page, where the line came from with Wiki page and revision, and
notes such as a Wiki requirement the model can't say). Lines without a use tile are in the tool's file (listed,
red on the "Banks" layer) but not exported.

**`places.tsv`** (`core/.../data/PlaceKinds`; made on every export by `tool/.../extract/MapPlaces`). The places by kind for the Functions tab's "Nearest". From the cache: the game shows a map icon
wherever an invisible marker object carrying that icon is placed (the object definition's map icon id, config
archive 35 "area"; cache 2735: 369 icon kinds placed). The cache has each kind's picture (a sprite) but **no name**,
so the names come from **`data/manual/map-icon-kinds.tsv`** (hand-written, `icon <tab> kind <tab> why`; only the
kinds named there are exported). `#` comment lines, the header `kind <tab> sprite <tab> tiles`,
then one line per kind, by name: `kind` as the sidebar shows it, `sprite` the icon's game sprite id (RuneLite
`SpriteID`, -1 none), `tiles` every tile showing that icon, `x,y,plane` separated by spaces. The kind `Bank` has
no tiles here: the plugin takes the banks from `banks.tsv` (the tiles a player banks from; `PlaceKinds.withBanks`),
and "nearest bank" keeps only the banks the player may use now (their needs met; a free world: free land only).
An icon often sits on a tile no one can stand on (a furnace, the middle of a building); the route search already
ends on the nearest tile that can be reached and has it in sight (core `RouteDestination`).
The Data Tool's Keep / Remove cards (one card per named kind): each answer is a
`PLACE_KIND` change in `changes.jsonl`: `target` = the map icon id, `on` true = removed (left out of `places.tsv`),
false = kept, `name` = the new name when it was changed on the card (absent: the name stays). The last answer
for an icon decides; the last name given stays; Ctrl+Z or the Change log takes an answer back. A kind with no answer
is exported. In the names file, a `why` starting with `Name not sure:` makes its card ask for the name to be checked.

**Sea charting boat / land spots**: the export decides per task whether its place in `charting.tsv` is a
boat spot (the nearest main ocean tile to Quest Helper's place, so the route ends on the boat) or a land spot (Quest
Helper's place, walked to). Quest Helper's place on main ocean water: boat. Otherwise the Boat / Land card
answer, a `CHARTING_SPOT` change in `changes.jsonl` (`target` = the task number, `on` true = boat, false = land; the
last answer counts; Ctrl+Z or the Change log takes it back), else the guess: land when ground to stand on is within 2
tiles of the place, else boat (`tool/extract/ChartingSpots`).


**`transports.tsv`** (comment lines start with `#`), 17 tab-separated columns: id, kind (`TWO_WAY`,
`ONE_WAY`, `TELEPORT`, `NETWORK`), name, category, start `x,y,plane` (empty for a teleport), end
`x,y,plane`, ticks, flags (`q` quest only, `f` can fail, `c` long cooldown, `e` ticks are an estimate,
`b` the requirements-back column is set, `s` and a digit only a boat uses it, no wider than that many tiles: `s2`
rafts and skiffs), network, requirements, requirements back, teleport category,
Wilderness limit (empty = none), object `id@x,y,plane` (empty = none), player note, menu, menu back (the
last two below; files with the first 15 columns still read, without menu texts). Tabs and
line breaks inside text become spaces.

**Menu texts** (columns `menu` and `menu back`; core `MenuText`, `TransportLine.getMenu()`,
`getMenuBack()`, `menuFor(startToEnd)`). The step card says the text the minimenu click would be (for example
"construction cape -> rimmington"). Made from the cache on every export by
`tool/.../menu/MenuMatcher` (facts read by `MenuFactsLoader`), nothing hand-made. `menu` is what the player
clicks to go start to end (a teleport: from anywhere), `menu back` end to start (two-way transports only);
empty = not known (never guessed). Forms, parts separated by `|` (game names never contain it; the export
refuses one that does):

| Form | Means | Card title (`MenuText.title()`) |
|---|---|---|
| `v\|<option>\|<target>` | the option is a verb; the game's menu line | `Climb-up Ladder`, `Cast Varrock Teleport`, `Commune Royal seed pod` |
| `n\|<option>\|<target>` | the option names the destination (menu line "Edgeville Amulet of glory") | `Amulet of glory → Edgeville`, `Varrock Teleport → Grand Exchange` |
| `c\|<option>\|<target>\|<choice>` | the click ("Teleport Construct. cape") opens a sub-menu or list; then the choice | `Construct. cape → Rimmington`, `Fairy ring → CKQ` |
| `c\|<option>\|<target>\|<choice>\|<key>` | as above, and the keyboard key that picks the choice in the game's list (where the cache fixes it, the house jewellery box, and the lists confirmed in `data/manual/list-keys.tsv`: spirit trees, Pendant of Ates) | `Jewellery Box → Burthorpe (5)` |
| `c\|\|\|<choice>` | a network stop whose click was not found: only the choice that leads to it | (used in rides only) |

House rides: the jewellery box is `c|Teleport Menu|Jewellery Box|<the
list's own entry>|<its key>` (client script 1685 builds the list, interface 590; script 1688 keys entry n "1".."9"
then A-Z from enum 1401, the same in every house); the house portals are the portal nexus's list,
`c|Teleport Menu|Portal Nexus|<entry>` (enum 1377 param 660; an alternate such as "Grand Exchange" in param 679),
without a key: the nexus's keys follow the player's own slots, so the plugin adds them from its house scan
(`player/HouseFurniture.nexusKeys`, the `nexus*` rows of `house-objects.tsv`), and says the portal chamber's portal
instead ("Enter Senntisten Portal", the `chamber` rows) when only the chamber has the destination.

Lists the server fills (cached ahead of time, not worked out at runtime): `data/manual/list-keys.tsv`
has one row per click that opens such a list (option, target, order, confirmed, source). For a row confirmed "yes"
the export gives each choice of that click the key of its place in the order, "1".."9" then A-Z (enum 1401, as
client script 218 numbers the generic list): `sub-menu` = the item's right-click sub-menu of that option in the cache
(every charge must give the same list), `spirit trees` = the spirit trees' "Last-destination" texts by varbit 20252
(1 Tree Gnome Village ... 14 Laguna Aurorae). "ask" rows are not confirmed yet and get no key; a key already set (jewellery box) stays.

Item names leave out a charge count or trim mark ("Amulet of glory(4)" → "Amulet of glory", "Construct. cape(t)"
→ "Construct. cape"): any charge works, so the card names none (the game's menu line has the held item's full
name). A network stop's text is the stop's own click plus the choice that picks THIS stop; a ride from stop A to
stop B is A's click with B's choice (`MenuText.ride(a, b)`). The plugin's cards write the arrow as "->"
(`MenuText.title("->")`): RuneLite's RuneScape fonts have no "→" (docs/PLUGIN_UI.md "Card titles").

Where the texts come from (checked in cache #2735):
- **objects**: the object's name and options (`EntityOpsDefinition`: plain options, conditional options, sub-menus;
  a multiloc's forms too, the name of the form that has the option). The option for the way: the object's only
  transport option; else up a plane (or out of an area at y ≥ 6400) "Climb-up"-like, then "Climb", then leaving;
  down the reverse; a shortcut's, barrier's or ride's own kind first ("Cross Gangplank" both ways); else the
  category's. The way back of a two-way transport uses the same object when the end is within 2 tiles of it on
  the same plane, else the best placed object within 6 tiles of the end with an option for that way (the other end
  of a ladder, a cave's exit; the same name preferred; a shut trapdoor: "Open Trapdoor"). Options that only look
  ("Look-inside", "Investigate"...) are never a transport's click.
- **items** (the item ids of the requirement): an inventory sub-menu option, inventory option, worn option (item
  params 451-458, RuneLite `ParamID.OC_ITEM_OP1..8`) or worn sub-menu option (`ParamID.OC_ITEM_OPn_SUBOPm`) that
  names the destination (or the name's bracket, "(outside)"); quetzal whistles' "Signal" opens the quetzal map
  (Wiki: Basic quetzal whistle rev 15356472); else the item's teleport verb ("Break", "Commune", "Empty"...) when
  the item has only this destination. Several destinations and no option naming them (a "Rub" dialogue): unknown.
  Achievement diary cape records are named after each diary's master ("Two-pints"): `MenuMatcher.DIARY_CAPE_OPTIONS`
  maps the 15 masters to the cape's options ("Ardougne": the menu name wins); the pairs follow
  the cape's sub-menu order, the same as Shortest Path's numbered rows "1. Two-pints" .. "G. Twiggy O'Korn".
- **spells**: spell objs (param 601 `SPELL_NAME`) and their spellbook button's options (param 596 `SPELL_BUTTON`,
  interface 218; `*` is "Cast", set by client script 2615): "Cast <spell>", or the button's other option when it
  names the destination ("Grand Exchange", "Seers'", "Yanille", "Outside"). Minigame teleports: "Cast Minigame
  Teleport", then the minigame as DB table 214 names it (casting opens the minigame choice: Wiki, Minigame
  Teleport rev 15359028).
- **destination lists**: fairy ring codes (DB table 89), quetzal landing sites (table 64), charter ship ports
  (table 206), gnome glider buttons (interface 138), canoe stations (client scripts 7884 and 7134), spirit trees
  (their "Last-destination" option texts). Mounted house jewellery (glory, digsite pendant, Xeric's talisman): the
  furniture object's options, found by name. Other house furniture (jewellery boxes, portal nexus or chambers,
  obelisk) is left unknown: which one the player built is not known.
- **Shortest Path** rows (`data/shortest-path`, menuOption menuTarget objectID) are hints only: an NPC or object id
  at a ride's start or a stop (within 6 tiles), or at a transport without object, used only after the cache
  confirms its name and option; and a cross-check of object texts (differences are listed as doubts).

`exportPlugin` prints how many are known per kind and writes every transport's texts, the reason for each
unknown one and the doubts to `tool/build/menu-texts.tsv`.

**What is in it**: every transport record in use (today 4,621: 2,992 two-way, 1,084 one-way, 416
teleports, 129 network stops), in `changes.jsonl` order (the order decides between equally short routes).
**Left out**: records with category `NEEDS_FIX` or on the test list (none today), transport candidates
and suggestions, rejected suggestions, Shortest Path rows, the coarse graph (built at plugin start,
0.3-0.5 s), the sea's clearance and sea graph (built at plugin start from `sea.bin`, 0.2 s), the player's
boats (read from the game), `house-things.tsv`. Quest-only
transports are shipped but never used for routes (as in the tool).

**How the plugin uses it** (`core/.../data/PluginRouter`, `PlayerTransports`; the Route tab's rules with
sailing off): doors +5 ticks, plane change +5, route-end step (10 tiles, in sight; above y 4224 only in
the destination's region), requirement lines checked against the player (unreadable line = met; items
only in the bank count only on a route that begins at a bank, `BankFirst`), network rides = own ticks + the
network's extra and the network's requirement, teleports = their category's ticks, start Wilderness level
0 (the tile rule is not known yet), free-to-play players walk on free land only. The search drops its
caches after a route when they pass the budget (15 MB; routes stay exactly the same).

**`sea.bin`** (`core/.../data/SeaBits`): what the Data Tool's sailing needs, in a compact form. gzip of, big-endian:

| Field | Type |
|---|---|
| magic `"BPSE"`, version 2 (versions 0 and 1 still load: see below) | 4 bytes, int |
| ticks to board a boat (and again to leave it); this version reads only 10 (`Dock.BOARD_TICKS`) | int |
| region count, then per region with main-ocean tiles (ascending id): region id; 1 = all 4,096 tiles are main ocean, or 0 and 64 longs (one per local x, bit = local y); the sea area for the whole region (`SeaArea` ordinal) or 255 and 2,048 bytes (4 bits per local tile, index `localX << 6 \| localY`, even index in the low bits) | int, byte, [64 longs], byte, [2,048 bytes] |
| odd boat steps: count, then per tile `x << 14 \| y` and its steps (bit per direction W, E, S, N, SW, SE, NW, NE); every other tile steps to each main-ocean neighbour, diagonals only when both tiles round the corner are main ocean | int, (int, byte) each |
| docks: count, then per dock id, name, Sailing level, quest (`""` = none), kind (0 gangplank, 1 mooring point), land x, y, plane, size, buoy x, y, size, the tiles the player stands on (count, then x, y, plane each), shipwright (`""` = none); version 1, only after a shipwright's name: 1 and where it stands (x, y, plane: its Wiki tile, `data/wiki/shipwrights.json`), or 0 (not known) | int, UTF, int, UTF, byte, 7 ints, int + 3 ints each, UTF, [byte, [3 ints]] |
| version 2: the sea graph's channels (core `SeaChannels`): count, then per channel its point count and points `x << 14 \| y` (the first and last are waypoints' tiles, each stretch a straight line a boat may sail both ways) | int, (int, ints) each |

Version 1 added the shipwright's place (so the player is pathed to the shipwright when one is used); a version 0 file still loads, its shipwrights without a place (the boat is then brought at the
dock's land tile, as before). Routes walk to the tile next to the shipwright's place that core `Sailing` picks from
the walking map (docs/SAILING_SPEC.md, "Routes walk to the shipwright"), so the file stores the Wiki tile only.

Version 2 added the channels (a destination in the Ynysdail bay had no route without them): crooked water the
sea graph's straight links miss (docs/SAILING_SPEC.md, "Channels"). Finding them floods the whole sea (about 1 s,
25 MB), so the Data Tool does it and the plugin only reads them; older files load with none. Cache #2735: 102
channels, sea.bin 53 KB -> 55 KB. Channels deleted by hand are left out of the file: each deletion is a
`SEA_CHANNEL` change in `changes.jsonl` (`target` = the channel's ends `"x,y-x,y"`, lower packed tile first, as core
`SeaChannels.key`; `on` = deleted, false = restored; `x`, `y` = a tile on it).

The mask also holds the water a boat-only transport leads to (`largestBoat`; core
`SeaGrid.build(..., joinedAt)`), even when it is cut off from the main ocean: the Wyrmscraig cave pool (276 tiles).
Today: 829 regions (2,457,987 main-ocean tiles), 12 odd boat steps (walls between sea tiles), 63 docks (16 with
a shipwright). The plugin works out the clearance (from the mask) and builds the sea graph when it loads
(about 0.2 s; `PluginRouter`), then drops the clearance table (the graph keeps each link's clearance; the
few lines joining docks and points at sea to the graph work it out from the mask again). In memory: about 5 MB in all
(`gradlew memoryCheck`: 26.3 MB land-only -> 31.4 MB after 100 Maxed routes).

**Sailing in the plugin** (`PluginRouter.route(..., SailingSetup)`, `CoreRouteEngine`): exactly the Data
Tool's Route tab with Sailing ticked (`RouteController.search`, core `Sailing`): the player's boats (core
`Boat`: kind, parts, the dock it is moored at, or `AT_START` = the player is on it and the route starts
from the boat's tile, or `CAPSIZED` / `LOST` = only a shipwright brings it, or `BOTTLED` = used only with a full
boat bottle per bottled boat in the inventory, released at any dock), the boats the Settings tab
allows and whether a shipwright may bring one; the
player's Sailing level and finished quests for docks (the tool uses its test player's quests); members only. Steps: walk, bring
the boat, board (10 ticks), Sail (the boat's course tiles, plane 0), leave (10), walk; or a route ending at
sea on the boat. Sailing steps have no transport id (they can't be skipped or blacklisted). Several places
(clue steps): the place is chosen on foot as before, then the route to it may sail (provisional; when no
place can be reached on foot the first four are tried with the boats). `exportPlugin` compares sailing
routes for test boats (a skiff, sloop and raft moored at Port Sarim, a boat at sea, a teleport focus, a
greater focus, no shipwrights, Sailing 40, Sailing 99 without quests, a capsized boat, a bottled boat with its
bottle carried) with the tool's own: `check: 0 of N routes differ`. No file format changed for capsized and bottled
boats: `boats.tsv`'s `port` records already name the statuses, and the bottle item (31992) is a constant in core.

**`boats.tsv`** (`core/.../data/BoatTable`, written by `tool/.../extract/BoatTableExtractor`): what the
values of the game's per-boat varbits (`SAILING_BOAT_1..5_*`) mean, read from the cache's DB tables at export time.
How the game uses each varbit was read from the client scripts of cache #2735 (docs/SAILING_SPEC.md, "Sailing in the
plugin, part 2"). Comment lines start with `#`; tab-separated records:

| Record | Columns |
|---|---|
| `type` | kind id (`_TYPE` value = table 166 column 0), our kind (`RAFT`, `SKIFF`, `SLOOP`, or `-` = routes don't use it, e.g. the temporary quest boat), the game's name, then four comma lists: the part tier (0-6, as core `Boat`) for each `_HULL`, `_SAIL`, `_KEEL`, `_STEERING` value (index = value; `-1` = an option not matched; `-` = no list, e.g. a raft's keel), the `_BRAZIER` values that are an icy-seas brazier, the `hotspot:value` pairs of `_HOTSPOT_k` that are a fetid-water facility (`-` = none) |
| `port` | a `_PORT` value that is not a dock, and the game's label (table 147: 253 Lost, 254 Capsized, 255 Bottled) |
| `name` | name part 1-3, then the word for value 0, 1, 2, ... (table 187 rows 8545-8547) |
| `dock` | dock id (table 194 column 0, the `_PORT` value) and name, every dock of the table |

Part options are matched to tiers by the first word of their name (`Boat.WOODS` / `Boat.METALS`) and checked against
their stats row (table 164): hull base speed, sail storm resistance, keel crystal-flecked and helm tangled-kelp
resistance must give what core `Boat` assumes; an option that does not is written as `-1` and the export prints a
`BOAT TABLE:` note (none for cache #2735). Today: 4 kinds (raft, skiff, sloop, Will and Anne's boat), 3 statuses,
61 docks, 3.6 KB.

**`clues.tsv`** (`core/.../data/ClueData`; written by `tool/.../route/ClueExport`): comment lines start
with `#`, then the header line, then one line per clue step (1,000 today), 18 tab-separated columns (older exports have
the first 10, without `needs`, the first 11, without `note`, the first 12, without the clue-card columns, or the
first 15, without the key-NPC columns; all still read):

| Column | Meaning |
|---|---|
| `key` | `<type>-<cache row>` as in `data/clues/clue-steps.tsv` (the row = the value of item param 623) |
| `type`, `tier` | as in `data/clues/README.md` (tier `beginner` ... `master`, `quest`) |
| `target` | `npc`, `loc`, `coord`, `key`, `kill`, `none` (how the place is used: talk, search, dig ...) |
| `items` | clue item ids that name this step, comma list (empty for beginner, master, hot/cold) |
| `targets` | places to go to, `x,y,plane` joined with `;`, any one will do: the cache target, alternative targets (`alt-target`), and for kill steps one tile per group of Wiki spawns (the group's spawn tile nearest its middle; groups: spawn tiles of one Wiki area within 12 tiles of each other; Slayer-task-only areas left out). Empty: no known place (3 steps) |
| `stash` | the STASH unit `x,y,plane`, or empty |
| `text` | the clue text from the cache (beginner and master steps are found by it) |
| `description` | the cache's description of the target (shown with the route) |
| `wiki` | for kill steps: the Wiki pages of the areas, `page@revision` comma list (CC BY-NC-SA 3.0); else empty |
| `needs` | what the player needs besides the clue, a requirement line (syntax above); empty = nothing. First the cache's own needs (`requirement` of `data/clues/clue-steps.tsv`: the emote outfit as `item a/b worn` per slot, items to bring, skill levels, quests, a key step's key), then the hand-authored ones (`data/clues/clue-overrides.tsv`: `item 952` spade on dig steps, a light source in dark areas). The export checks that every line reads with the exported item groups |
| `note` | a short note for the player about the step, in words (e.g. "Kill the barbarian spirit here"; the shellbane gryphon is inside the cave); empty = none. Hand-authored (`clue-overrides.tsv` `note` lines; `ClueData.Step.getNote()`) |
| `action` | what the player does there, as the game's clue helper says it: `dig`, `emote <id> <name>` (several joined with ` \| `, in order), `music`, `talk`, `search`, `key`, `kill`, `none` (`data/clues/README.md`); talk, search, key, kill and music are followed by the target's name from the cache (`talk Ranael`, `key Drawers`, `music Cecilia`) |
| `stashVarbit` | the STASH unit's form varbit (RuneLite `VarbitID.HH_CONSTRUCTED_*`; 1 = built), empty without a STASH unit |
| `stashPlace` | the STASH unit's place as the game's STASH list names it (enum 1531), empty without one |
| `keyNpc` | for key steps: where the NPC that drops the key is, `x,y,plane`: the cache's key-NPC tile (clue-helper table 19 `TargetKey`, the `key-npc` place of `data/clues/clue-steps.tsv`); empty for other steps. The plugin routes there first while the player has no key |
| `keyNpcName` | that NPC's name from the cache, the first of several ("Barbarian" of "Barbarian / Hunding"); empty without `keyNpc` |
| `keyItem` | for key steps: the key's item id (table 19, the step's "(the key)" need); empty without `keyNpc` |

Older exports have 10 (no `needs`), 11 (no `note`), 12 columns (no `action`, `stashVarbit`, `stashPlace`) or 15 (no `keyNpc`, `keyNpcName`, `keyItem`); all still read.

Made from `data/clues/clue-steps.tsv`, the manual corrections in `data/clues/clue-overrides.tsv` (retired steps
left out, fixed tiles, needs, notes, task-only kill areas, kill-area planes and moved Wiki layers; `data/clues/README.md`) and
`data/wiki/clue-kill-spawns.tsv` (read by column name). Key
chests keep the chest as the target; the key's NPCs are only named in the description. The export checks
every listed clue item against the cache (param 623 = the step's row: 732 items, all match) and that the
items the plugin uses for beginner / master clues and hot/cold devices (19835, 23182, 19939, 23183) are
named as expected. About 169 KB (cache 2735, with the notes and the clue-card columns; 139,853 bytes before them).

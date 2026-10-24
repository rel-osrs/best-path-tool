# Clues: data and pathability

This page covers the cache survey, the clue-step dataset (`data/clues/`), the Wiki places for
kill steps and the pathability check. The plugin's own clue detection (from clue items, clue text and
the cache tables) is described in `docs/PLUGIN_CLUES.md`.

Commands (repository root; `cache/openrs2-2735` is the cache folder):

```
gradlew -p tool clueExtract --args="cache/openrs2-2735"     # data/clues/clue-steps.tsv
gradlew -p tool wikiClueKills --args="cache/wiki"           # data/wiki/clue-kill-spawns.tsv (dev time; Wiki)
gradlew -p tool clueCheck   --args="cache/openrs2-2735 4"   # cache/clue-check/report.tsv + summary.md (4 threads)
```

## 1. What the cache has

Jagex's own "clue helper" DB tables. The cache stores
only numbers; table and column names are RuneLite's public gameval names
(`DBTableID.Cluehelper*`, names only). Row
ids are global across tables, so a `dbrow` column points straight at the
target / requirement row. Tiles are packed the game's way (plane in bits
28-29, x in 14-27, y in 0-13). Cache 2735 (build 241):

### Clue tables (one row = one clue step)

| Table | Name | Rows | Columns (gameval) | Example |
|---|---|---:|---|---|
| 3 | Cluetype | 3 | name; quest / beginner / easy / medium / hard / elite / master clue row lists | "Hot/Cold Clues": beginner = 5 rows, master = 136 rows |
| 4 | ClueAnagram | 104 | id, difficulty, clue text, target, challenge, region, requirements, all-regions, restricted | "An Earl" -> NPC Ranael 3315,3163 |
| 5 | ClueMap | 41 | id, difficulty, target, requirements, region, ... | map 101 -> "Near the tree west of the Champions' Guild" 3167,3360 |
| 6 | ClueCipher | 18 | id, difficulty, text, target, challenge, requirements, ... | "BMJ UIF LFCBC TFMMFS" -> Ali the Kebab seller, question "133 kebabs = 399" |
| 7 | ClueCoordinate | 169 | id, difficulty, text ("00.05S 01.13E"), target, requirements, combat encounter, ... | -> dig at 2479,3158 |
| 8 | ClueCryptic | 289 | id, difficulty, text, target, requirements, challenge, outfit text/outfit, STASH loc + tile, region, ..., league text/target | "Talk to Charlie the Tramp in Varrock." |
| 9 | ClueEmote | 126 | id, difficulty, text, target, emote, outfit text, outfit, STASH loc + tile, combat encounter, requirements, ... | "Blow a raspberry at Aris ..." emote 19, STASH bush 34736 at 3206,3422 |
| 10 | ClueFairyring | 11 | id, difficulty, text ("A I R 2 3 3 1"), ring, steps (4 ints), target | -> dig 2702,3246 |
| 11 | ClueFalobard | 20 | id, difficulty, text (the song), target (Falo), requirements (the item) | "A blood red weapon ..." -> any dragon scimitar |
| 12 | ClueHotcold | 142 | id, difficulty, target (dig spot), requirements, combat encounter, ... | beginner "Patch of mushrooms just north-west of Draynor Manor" |
| 13 | ClueMusic | 25 | id, difficulty, music (row of the music table), unlock text, target (Cecilia) | track "Alone", unlocked at Ice Mountain |
| 14 | ClueSkillchallenge | 60 | id, difficulty, text, requirements, target (Charlie / Sherlock) | "I need to give Charlie a cooked trout." |

Difficulty numbers, read from the cache itself (table 3's columns and the
Hot/Cold row's lists): 0 beginner, 1 easy, 2 medium, 3 hard, 4 elite,
5 master, 6 quest (the cache's "quest clues").

### Target, requirement and helper tables

| Table | Name | Rows | Columns | Notes |
|---|---|---:|---|---|
| 15 | TargetNpc | 222 | npc, fallback npc, tile, description | every row has a tile (208 plane 0, 9 plane 1, 5 plane 2) |
| 16 | TargetLoc | 105 | loc (object), fallback loc, tile, description | every row has a tile |
| 17 | (no gameval name) | 0 | - | empty |
| 18 | TargetCoord | 535 | tile, description, short description (area) | dig / emote / hot-cold / map spots; all have a tile |
| 19 | TargetKey | 12 | chest loc + tile, NPCs that drop the key, key item, key-NPC tile, inv, count, description | "Kill any Guard ... for a medium key. Then search the drawers ..." |
| 20 | TargetKill | 14 | NPCs, tile, description | **the tile column is empty in all 14 rows** |
| 21 | RequirementObj | 23 | description, items (alternatives), inv, count | "A ring of visibility" = 4657 / 28327 / 28329 |
| 22 | RequirementObjParamTrailItem | 7 | description, trail item group | "Any Dragon Scimitar" = group 39 |
| 23 | RequirementQuest | 1 | description, quest row, state | "Access to Lletya required." |
| 24 | RequirementStat | 62 | stat, level | stat numbers = RuneLite `Skill` order (16 = Agility) |
| 25 | ChallengeQuestion | 90 | (question, answer) pairs | "How many stalls are there in Varrock Square? = 5" |
| 26 | ChallengeBox | 3 | description | light box / puzzle box |
| 27 | CombatEncounter | 9 | description, NPCs | "A double agent." |
| 28 | Outfit | 134 | description, item per equipment slot (11 slots; several items = alternatives), trail item group per slot | "Equip a gold ring and a gold necklace." |

### How a clue item finds its row

Every clue item carries **item param 623 = the row id of its step** (checked:
652 distinct values, every one a clue-table row). Easy, medium, hard and
elite clues have their own item per step (steps with an item: 166 / 167 /
186 / 124). Beginner and master clues have one shared item per tier ("Clue scroll (beginner)" / "(master)"), so
the item does not say which step it is; hot/cold steps use a device. The
challenge scrolls (44 medium, 22 hard, 2 elite) carry param 623 too. So the
plugin can tell easy-elite steps from the item id alone; beginner, master
and hot/cold steps need the clue text (or the device); the plugin's own
detection from the clue text covers those.

### Clue types covered

All the location types: anagram, cipher, coordinate, cryptic (talk, search,
dig, key-and-chest, kill), emote with STASH units and outfits, fairy ring,
Falo the Bard, hot/cold (beginner and master), map, music (Cecilia),
skill challenges (Charlie for beginner; Sherlock for elite and master), plus
challenge questions / puzzle boxes / light boxes and double-agent fights as
attributes of steps. Count per type and tier (1,005 steps):

| Type | beginner | easy | medium | hard | elite | master | quest |
|---|---:|---:|---:|---:|---:|---:|---:|
| anagram | 9 | 0 | 48 | 18 | 12 | 16 | 1 |
| cipher | 0 | 0 | 6 | 11 | 0 | 0 | 1 |
| coordinate | 0 | 0 | 44 | 55 | 41 | 28 | 1 |
| cryptic | 6 | 120 | 27 | 58 | 44 | 33 | 1 |
| emote | 6 | 33 | 25 | 17 | 19 | 25 | 1 |
| fairyring | 0 | 0 | 0 | 11 | 0 | 0 | 0 |
| falo | 0 | 0 | 0 | 0 | 0 | 20 | 0 |
| hotcold | 5 | 0 | 0 | 0 | 0 | 136 | 1 |
| map | 5 | 9 | 12 | 7 | 6 | 0 | 2 |
| music | 0 | 6 | 7 | 10 | 2 | 0 | 0 |
| skillchallenge | 8 | 0 | 0 | 0 | 25 | 27 | 0 |

### What is missing or odd

- **Kill steps have no tile** (14 elite cryptic steps: Ankou, waterfiend,
  basilisk, rock crab, bloodveld, aviansie, green dragon, crocodile, hellhound,
  aberrant/deviant spectre, frost / earthen nagua, shellbane gryphon, the
  ferocious barbarian spirit), and NPC spawns are not in the cache at all.
  Filled from the Wiki at development time (section 3); 2 of them have no
  Wiki places either.
- One step has no target: `skillchallenge-1868` "Combine the torn clue scroll
  parts." (master).
- 4 steps have **2-3 target rows**: alternatives, all kept (`alt-target`):
  Mysterious ghost x3 (master cryptic), Cirr Jad / Fossil Island barge,
  Mus Kil Reader (two NPC ids), Outside Lletya / Prifddinas.
- 14 newer rows (ids 4963, 7041-7059: the Great Conch, Laguna Aurorae,
  Lledrith, Wintumber, Brittle Isle, Drumstick Isle, the Pandemonium,
  Buccaneers' Haven, Summer Shore, nickel, dragon keel parts) have
  **"restricted content" = 1**. Every one is Sailing-era content, so it
  probably marks Sailing; not confirmed, kept as a check note.
- Rows no item points at (beyond beginner/master/hot-cold and the Sherlock
  challenges): anagram-1552 (medium "VESTE"), map-2260/2261 (easy),
  coordinate-2016 (hard "14.03N 32.11W", Lake Molch), music-1888. Possibly
  retired steps; kept, nothing decided. `coordinate-2083` is master in the
  table but item 3550 "Clue scroll (hard)" points at it.
- Things the cache doesn't state and the dataset therefore doesn't either:
  that dig steps need a spade, light sources for dark places, the Wiki's
  "requirements" prose, which quest the four elven-land quest clue items
  (23814-23817) belong to.
- League text/target columns of table 8 are skipped (temporary game mode).

## 2. Cross-check against the Wiki

The Wiki's per-tier list pages (`Treasure Trails/Full guide/<tier>` and the 31
`Treasure Trails/Guide/<type>/<tier>` subpages they include; 2 requests,
dev-time, cached) were counted and their texts compared with the cache's
(normalised text; coordinates as strings). They have no tiles, so they are a
completeness check only:

- Anagrams, ciphers, emotes, maps (beginner/hard/elite), hot/cold beginner:
  **same counts**.
- Cryptic: every Wiki clue is in the cache; the differences are wording
  ("Talk to Turael in Burthorpe" = cache "Talk to the slayer master in
  Burthorpe", Port Khazard crate, "home" vs "house") and the Wiki listing the
  Cecilia music clue and "Show this to Sherlock" under cryptics.
- Coordinates: the cache has 3 more than the Wiki lists (2016, 2074, 2083,
  see above); the rest match, apart from zero padding and two Fossil Island
  islet clues whose minutes differ a little between the Wiki and the cache.
- Maps: the cache has 2 more easy and 1 more medium row than the Wiki lists.
- Master hot/cold: cache 136, Wiki 135.

Conclusion: **the cache is the complete source for clue locations**; the Wiki
is needed only for the kill steps.

## 3. Dataset

- `data/clues/clue-steps.tsv` — 1,005 steps, generated by `ClueExtractCli`
  (`tool/src/main/java/com/bestpath/tool/clue/`). Format and columns:
  `data/clues/README.md`. Source per step: cache table + row and target row.
  28 steps carry check notes (14 kill steps without tile, 14 restricted
  content rows, 1 without target; one overlap).
- `data/wiki/clue-kill-spawns.tsv` — 726 spawn tiles for 12 of the 14 kill
  steps, from the `{{LocLine}}` templates of the NPCs' Wiki pages (one request
  for all 27 pages; page + revision per line; CC BY-NC-SA 3.0, see
  `docs/DATA_SOURCES.md` §4). The Wiki's `plane` belongs to its own map layer
  and is sometimes not the game plane (God Wars Dungeon, Slayer Tower
  basement, Myths' Guild basement): fixed by `area-plane` overrides (section 5.2).
- `data/clues/clue-overrides.tsv` — manual corrections on top of the generated
  file, hand-authored with a source per line: fixed tiles, retired steps, needs
  (spade, light), task-only kill areas left out, kill-area planes and shifts.
  Read by the clue check and the plugin export (`ClueOverrides`).
  `data/clues/dark-areas.tsv` holds the dark-area boxes used for the light
  needs. Format and rules: `data/clues/README.md`.
- Kill steps get one route point per **group** of Wiki spawns (`KillAreas`:
  spawn tiles of one Wiki area within 12 tiles of each other, chained; two
  groups of dogs = two points, the route search picks the fastest). 81 groups
  for the 12 kill steps with Wiki places. Slayer-task-only areas are left out
  (`skip-area`: the Ankou and the bloodveld / hellhounds in the Stronghold Slayer
  Cave, waterfiends in Kraken Cove, Cerberus' Lair). The ferocious barbarian
  spirit and the shellbane gryphon have no Wiki places; they have a `tile` and a
  `note` override.
- Retired steps (`retired` overrides, no clue item points at them): anagram-1552,
  map-2260, map-2261, coordinate-2016, music-1888. 1,000 steps remain.
- Every dig step needs a spade (`item 952`; 407 steps at the time); the Lumbridge
  Swamp Caves emote and the genie need a light (`data/clues/dark-areas.tsv`).

## 4. Pathability check

`ClueCheckCli` loads the real map as the Data Tool does (manual edits,
sea blocked for walking, doors open at 2 ticks, the **confirmed
transports only** — no unconfirmed suggestions, no Shortest Path set; the
**Maxed** player also sails, fresh players walk), then for every location —
the target, alternative targets, STASH units, key NPCs and one tile per Wiki
kill area — it:

1. classifies the tile (`ClueCheck`): walkable / blocked / pocket / hidden /
   ignored region / no floor / at sea / off the map, plus the nearest
   standable tile (10 tiles) and another plane that is standable at that x,y;
2. runs the core Pathfinder the Route tab's way (route end: the tile, or the
   nearest reachable tile within 10 that sees it, then the search) from
   Lumbridge 3222,3218,0, the Grand Exchange 3164,3487,0 and Kourend Castle
   1639,3673,0 (members only), for three test players: **Maxed** (all
   unlocked), **Fresh account** (members, nothing done) and **Fresh
   free-to-play** (only tiles on free land, from the two free starts, on the
   free-world map);
3. counts a step as reached when a route ends on the tile (dig/emote/hot-cold
   spots), within 1 tile (objects, chests, STASH units — measured to the
   placed object's **whole footprint**, e.g. all 4x4 tiles of a spirit tree)
   or within 2 (NPCs, e.g. across a counter) of its target or of an
   alternative / kill area. A dig/emote spot whose own tile is blocked on our
   map (inside a rock, tree or STASH unit) is reached from the **tile next to
   it** (the game accepts it there);
4. also counts, separately as **"with suggestions"** (the clue data is never
   changed): a tile with no floor on its plane that is reached on the *only*
   other plane with floor at that x,y (the check searches that plane too). A
   Wiki kill group whose tiles have no floor prefers a tile that has such a
   plane.

The check first applies `data/clues/clue-overrides.tsv` (retired steps
dropped, fixed tiles, task-only kill areas left out, kill-area planes and
shifts) and checks one tile per group of Wiki spawns.

The Maxed player sails with three boats (raft, skiff, sloop; every part at the
top tier, Inoculation station, Eternal brazier, greater teleport focus; all at
Port Sarim), using the Route tab's search with Sailing ticked
(`RouteController.search`): first to the end tile found without boats (so a
tile next to a crate stays the end), and when that is not close enough or
nothing is reached on foot, to the place itself and its nearest standable tile.
Sailing makes the Maxed searches slower.

Latest run: 1,000 steps, 1,202 locations (1,071 distinct tiles), 6,994
searches in about 415 s on 4 threads. Output `cache/clue-check/report.tsv`
(git-ignored; per location: tile class, nearest standable, object footprint,
suggested tile, ticks/steps/stop distance per player and start, cause) and
`summary.md`. Maxed player: **999 of 1,000** steps (every step with a tile);
fresh members account **709**; fresh free-to-play all 39 beginner steps.

By type, in an earlier run of the same check (maxed 993 of 1,000, fresh members 707; the
Barrows, Gnome Stronghold, Agility Pyramid, Pyramid Plunder, Tarn's Lair and crystalline
maples places were reached only after their ways in were built, which gave the totals above):

| Type | Steps | With a tile | Reached (maxed) | Reached (maxed, with suggestions) | Reached (fresh members) | Beginner steps | Reached (fresh F2P) |
|---|---:|---:|---:|---:|---:|---:|---:|
| anagram | 103 | 103 | 103 | 103 | 73 | 9 | 9 |
| cipher | 18 | 18 | 18 | 18 | 8 | 0 | 0 |
| coordinate | 168 | 168 | 168 | 168 | 83 | 0 | 0 |
| cryptic | 289 | 289 | 288 | 288 | 223 | 6 | 6 |
| emote | 126 | 126 | 121 | 121 | 84 | 6 | 6 |
| fairyring | 11 | 11 | 11 | 11 | 6 | 0 | 0 |
| falo | 20 | 20 | 20 | 20 | 20 | 0 | 0 |
| hotcold | 142 | 142 | 142 | 142 | 96 | 5 | 5 |
| map | 39 | 39 | 39 | 39 | 31 | 5 | 5 |
| music | 24 | 24 | 24 | 24 | 24 | 0 | 0 |
| skillchallenge | 60 | 59 | 59 | 59 | 59 | 8 | 8 |
| **all** | **1000** | **999** | **993** | **993** | **707** | **39** | **39** |

The one step without a tile is a skill challenge (`skillchallenge-1868`, no target).

The fresh members account reaches fewer because it has no quests or unlocks
(fairy rings, quest areas, agility shortcuts); that is expected and not a
data fault. Every beginner step is reachable for a free-to-play player.

## 5. Why places were not reached, and what was done

A first check run listed 59 places (locations, not steps: a step can have a
target and a STASH unit) that the maxed player could not reach. Each one was
looked at in the cache (floor and tile settings per plane, placed objects),
on our walking map (which walking area the tile is in, whether any route
reaches that area, what separates it from a reached area), in the manual
transports and edits (`data/manual/changes.jsonl`, `region-review.json`), and
once on the Wiki (Vannaka's page). The causes, grouped by what fixes them:

**None of the "no floor" places is a bridge.** The suspicion was that Vannaka's
tile is a bridge tile that our map keeps one plane up. The cache shows no
bridge flag there: Vannaka's tile is solid rock, and the clue table's tile is
simply 17 tiles away from where he stands. Bridges are already handled the
game's way on our map (the clue at the CKP fairy ring sits on a bridge tile and
is on the right plane).

### 5.1 A mistake in the check itself (fixed)

cryptic-1371 Spirit tree, Tree Gnome Village (2544,3170): the clue names a tile
*inside* the spirit tree, which is a 4x4 object (object 1293 placed at
2543,3168, covering 2543-2546 x 3168-3171). The route stops at 2542,3170, right
next to the tree, but the check measured to the named tile (2 away) instead of
to the tree. Objects are now measured to their whole footprint.

### 5.2 Wrong plane in the source

The tile has no floor on its plane, and exactly one other plane has floor at
the same x,y, which our map reaches. These are fixed with `tile` /
`area-plane` overrides in `data/clues/clue-overrides.tsv`:

| Place | What is wrong | Plane |
|---|---|---|
| cryptic-1382 Guardian mummy (1934,4427,0) | Cache plane 0 at that x,y: blocked and nothing to stand on. The only floor there is plane 3 (the Pyramid Plunder lobby), and the Pharaoh's sceptre teleport to Jalsavrah lands at 1934,4428,**3**. | 3 |
| emote-2184 Well of Voyage (2011,4712,0) | Plane 0 is empty there (no floor, no bridge flag); the floor is on plane 1, and the Temple door transports (Wiki/Shortest Path) land at 2014,4712,**1**. | 1 |
| cryptic-1460 bloodveld, God Wars Dungeon (Wiki 2880,5321,0) | The Wiki's God Wars Dungeon map layer says plane 0; the game's floor there is plane 2 (the entrance rope lands at 2882,5311,**2**). | 2 |
| cryptic-1461 aviansie and cryptic-1464 hellhound, God Wars Dungeon | Same Wiki map layer. | 2 |
| cryptic-1460 bloodveld, Slayer Tower basement (Wiki 3403,9934,0) | The basement ladder lands at 3412,9932,**3**; plane 3 is the only floor. | 3 |
| cryptic-1462 green dragon, Myths' Guild basement (Wiki 1939,8991,0) | The basement's ladder and cave are on plane **1**, the only floor there. | 1 |

The kill steps among these were already reached through other places; the fix
only removes false alarms.

### 5.3 Dig/emote spot inside an object

On our map the spot is inside an object, so the route stops on the tile next to
it. The game accepts digging or the emote from the next tile, so these count as
reached:

| Place | Object on the spot (cache) |
|---|---|
| cryptic-1488 Saradomin's encampment, God Wars Dungeon (2914,5300,1) | a solid unnamed object (26371) whose look changes with varbit 3967 |
| emote-2167 near a runite rock, Fremennik Isles (2375,3850) | Runite rocks (11376) |
| hotcold-697 centre of the Rimmington mine (2976,3239) | a STASH unit (28969, "Build/Search") |
| hotcold-794 Lovakengj blast mine (1504,3859) | Hard rock (28579, "Excavate") |
| hotcold-814 north of Tithe Farm (1807,3523) | Willow tree (10833, 2x2 at 1806,3523) |

### 5.4 Clue data tile that is wrong or not a game tile

| Place | What is wrong | Handling |
|---|---|---|
| cryptic-1252 and cryptic-1430 Vannaka (3164,9913,0) | Not a bridge. The cache tile is solid rock on plane 0 (tile setting 1 = blocked, no floor). The "floor" on plane 1 there is a cave-top layer (tile setting 8, "visible below") that nothing leads to. The Wiki (Vannaka, rev 15367772) puts Vannaka at **3147,9914,0**, 17 tiles west, which our map reaches. | `tile` override with the Wiki's tile |
| emote-2099 upstairs in the Ardougne windmill (2635,3385,**3**) | The windmill has floors on planes 0, 1 and 2 only (the top floor, plane 2, is reached); plane 3 has nothing. "Upstairs" could be 1 or 2, so the check doesn't guess. | Reached in the latest run |
| emote-2196 top floor of the Yanille watchtower (2930,4717,2) and its STASH unit (2930,4718,2) | The clue table points into a **copy** of the watchtower (2925-2932, 4711-4718; same floor plan as the main-map tower at 2544-2551, 3111-3118, shifted by +381, +1605). Every way up (ladders, Watchtower Teleport and tablet) leads to the main-map tower, whose top floor is reached; nothing leads into the copy. The STASH unit object (29057) exists only in the copy. | Reached in the latest run |
| cryptic-1455 Ankou, Stronghold of Security: Sepulchre of Death (Wiki map 18) | The Wiki's tiles for this map layer (1963-1990, 4900-4945) are not game tiles: no floor on any plane there. In the cache the Sepulchre of Death is around 2344,5214 (its Cradle of Life, object 23731), and our map reaches it. | The step is reached through the Ankou's other places; `area-shift` moves the layer onto game tiles |
| cryptic-1461 aviansie and cryptic-1460 bloodveld, Wilderness God Wars Dungeon (Wiki map 26) | No floor on any plane at the Wiki's tiles; the cache has no map at all for the 64x64 region of 2950,10085. | `area-shift`; the steps are reached elsewhere |
| cryptic-1464 hellhound, Karuulm Slayer Dungeon (Wiki map 33, 1190,10264) | The Wiki's tile falls in a 46-tile room reached only by the A Kingdom Divided trapdoor (quest-only). The Wiki layer's tiles are probably not game tiles here either (not verified). | `area-shift` |

Wiki map layers drawn away from the game's own tiles (Stronghold of Security,
Wilderness God Wars Dungeon, Karuulm Slayer Dungeon) are moved by `area-shift`
overrides, found by moving the layer by whole 8x8 chunks until every spawn tile
stands on the cache's floor.

### 5.5 Fairy rings (fixed in the route search)

A fairy ring stop is the ring's own tile (the 1x1 "Fairy ring" object, e.g.
29495). That tile is solid on our map (55 of the 56 stops; only BLQ is on a free
tile). The route search used to link a transport that ends on a solid tile only to
transports that start on that same tile, so a route could board the ring network
but never step off at the other end. It now boards a ring from the four tiles
beside its middle and steps off it on arrival (`docs/NETWORKS.md`). All 13 places
that were only reachable by a ring are now reached by the maxed player:

| Place | Ring |
|---|---|
| coordinate-1967 small island (CLP), 3081,3209 | CLP 3082,3206 |
| coordinate-1969 little island (AIR), 2699,3251 | AIR 2700,3247 |
| fairyring-1901 near fairy ring AIR, 2702,3246 | AIR 2700,3247 |
| coordinate-2007 island north-west of Miscellania (AJS), 2505,3899 | AJS 2500,3896 |
| coordinate-2058 Poison Waste island (DLR), 2217,3092 | DLR 2213,3099 |
| cryptic-1453 Broken Handz's island (DLR), 2221,3091 | DLR 2213,3099 |
| fairyring-1905 near fairy ring BJR, 2648,4729 | BJR 2650,4730 |
| fairyring-1908 near fairy ring CKP, 2073,4846 | CKP 2075,4848 |
| fairyring-1909 near fairy ring DIP, 3041,4770 | DIP 3037,4763 (Abyssal Nexus) |
| cipher-2275 Fairy Queen, 2347,4435 (Fairy Resistance Hideout) | AIR-DLR-DJQ-AJS 2328,4426 |
| cipher-2271 King Percival, 2634,4682,1 (Fisher Realm, upstairs) | BJR 2650,4730, then the staircase 2633,4679 |
| emote-2195 north-western corner of the Enchanted Valley, 3030,4522, and its STASH unit 3022,4517 | BKQ 3041,4532 |

### 5.6 Ways in that were missing

| Place | What was missing (evidence) | Now |
|---|---|---|
| anagram-1559 Dark Mage (3039,4835) and cryptic-1444 the law rift (3049,4839), inner Abyss | The inner Abyss and its obstacles are built, but at first nothing led into the outer Abyss: the Mage of Zamorak's teleport lands on a random tile and was left out on purpose (rides with a random landing). | Reached in the latest run |
| emote-2148 top of the Agility Pyramid (3043,4697,3) | The pyramid's top levels are a separate area (region 12105). | One line of the course is built; reached |
| emote-2132 Gnome Stronghold balancing rope (2473,3420,2) and its STASH unit (2473,3418,2) | The way up ("Obstacle net" 23134, "Tree branch" 23559) was missing. | The whole course is built; reached |
| emote-2198 7th chamber of Jalsavrah (1944,4427) and its STASH unit (1951,4431) | Inside Pyramid Plunder, which is not built. | Routes to the Jalsavrah lobby (1934,4420,3) with a player note |
| coordinate-7044 Lledrith Island, emote-7048 Wintumber Island, emote-7049 Brittle Isle, hotcold-7053 Drumstick Isle (with their STASH units) | Sailing islands: the only link is a boat dock (buoys 59822, 59815, 59826, 59825). The Brittle Isle spot is also behind rockslides, and the Drumstick Isle spot is inside an oak tree (10820, 3x3 at 2139,3561), as in 5.3. | Reached by the Maxed player since the check sails |
| cryptic-1464 hellhound, Charred Dungeon (Wiki 2626,8861) | The only way in is the dark hole on Charred Island (2637,2414), an island whose only other link is a boat dock (buoy 59809). | Reached by sailing |
| cryptic-1459 rock crab, Brine Rat Cavern (Wiki 2748,10166) | A dig entrance (to 2690,10125) works, but a "Picture wall" (23156, "Search", a wall at 2707,10147) cuts the cavern in two and has no transport; past it a gate needs an item (a key each time at the second coastal area). | Reached through another place; this one still not |
| cryptic-1447 Tarn's Lair, under the toad batta (3139,4554,0) | The dig spot is in a room closed by a wall (object 3550 on the east side of 3147,4556), with a searchable "Wall" (20594, "Search") next to it: a hidden passage. | Wall trap passage built; reached |
| cryptic-1490 Ping and Pong's room, the Iceberg (2671,10396) | The rooms (443 tiles) are cut off from the reached corridor by a "Door" (21068, 5x1 at 2656,10409) that has an "Open" option, but it is shaped as scenery (shape type 10), and our map only opens wall-shaped doors, so it is solid there. | Reached in the latest run |
| cryptic-5180 earthen nagua, Tonali Cavern Sun Chamber (Wiki 1326,9450,1) | The chamber (255 tiles) is walled off (wall 32380 at 1318,9446-9448 next to "Strange Tile" objects, maybe a puzzle). | Left out (`skip-area`); the step is reached elsewhere |
| emote-2177 crystalline maple trees (2211,3427) and its STASH unit (2213,3427) | The spot is a 13-tile closed-off pocket ringed by four maple trees (36681, 2x2, solid), a rockslide and blocked water (tile setting 1). Pockets are left out of the plugin's data. | A custom transport 2209,3420 <-> 2209,3424 into the pocket; reached |

### 5.7 Ignored regions and a hidden sub-region

| Place | Why |
|---|---|
| emote-2186 Barrows chest (3551,9694) and its STASH unit (3547,9690) | Region 14231 was ignored in the region review. The step now routes to the middle of the six mounds (3567,3291,0) with a player note (`tile` + `note` overrides); the STASH unit stays unrouted. |
| cryptic-1464 Cerberus, Cerberus' Lair (Wiki 1238,1250) | Region 4883 is ignored. The step is reached elsewhere. |
| hotcold-817 south-west of the Kebos Swamp (1187,3580) | The spot is in an 80-tile sub-region (anchor 1191,3577) that was hidden. It touches reached land; the sub-region is no longer hidden and the tile is reached. |

## 6. Restricted content rows

The cache marks 14 rows with "restricted content" = 1 (cache 2735; all
Sailing-era places). They are normal clue steps: the flag stays only as a
`checks` note in the generated `clue-steps.tsv`, and the clue check and the
plugin export do not treat these rows differently. The Sailing islands among
them are reached by the Maxed player because the check sails (section 4).
Place = the target tile; the kill step has none.

| Key | Type | Tier | Place (x,y,plane) | Text |
|---|---|---|---|---|
| anagram-7041 | anagram | medium | 3143,2423,0 | NOT RAG (In the Summer Shore) |
| coordinate-7042 | coordinate | medium | 3183,2453,0 | 22.07S 23.13E (Centre of the Great Conch) |
| coordinate-7043 | coordinate | hard | 1193,2774,0 | 12.05S 38.58W (On Laguna Aurorae) |
| coordinate-7044 | coordinate | elite | 2081,3184,0 | 0.43N 11.13W (On Lledrith Island) |
| cryptic-7045 | cryptic | hard | 2324,2772,0 | I sit on the beach and cry for those that have passed on. |
| cryptic-7046 | cryptic | elite | - (kill step) | Their bane awaits, but don't be going in there without permission. (Kill the shellbane gryphon) |
| emote-7047 | emote | easy | 3049,2966,0 | Dance a jig behind the bar on the Pandemonium. Equip a right eye patch and a bronze scimitar. |
| emote-7048 | emote | elite | 2069,2608,0 | Do the crab dance by the monument on Wintumber Island. Equip a crab helmet and a crab claw. |
| emote-7049 | emote | master | 1946,4074,0 | Bow in front of the cave on Brittle Isle. Beware of double agents! Equip a Medallion of the Deep and a rosewood blowpipe. |
| falo-7050 | falo | master | 2689,3550,0 | You won't bring me to heel, unless you have a bright red keel. |
| hotcold-4963 | hotcold | master | 2078,3665,0 | (On Buccaneers' Haven) |
| hotcold-7051 | hotcold | master | 3249,2349,0 | (In the south-eastern mine of the Great Conch) |
| hotcold-7053 | hotcold | master | 2139,3562,0 | (On Drumstick Isle) |
| skillchallenge-7059 | skillchallenge | elite | 2733,3415,0 | Mine some nickel. |

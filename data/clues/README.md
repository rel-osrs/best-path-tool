# Clue steps (`data/clues/`)

`clue-steps.tsv` is GENERATED from the game cache. Don't edit it by hand:
run the extractor again and compare with Git.

```
gradlew -p tool clueExtract --args="cache/openrs2-2735"        # this file, from the cache
gradlew -p tool wikiClueKills --args="cache/wiki"              # data/wiki/clue-kill-spawns.tsv (dev time, Wiki)
gradlew -p tool clueCheck --args="cache/openrs2-2735 4"        # pathability report (cache/clue-check/)
```

Where it comes from, what the cache has and what it lacks: `docs/CLUES.md`.

## Format

Tab-separated, UTF-8. Lines starting with `#` are comments; the first other
line names the columns. One line per clue step (one row of a cache clue
table). An empty cell is `-`. List cells separate entries with ` ; ` (a `;`
inside an entry is written as `,`).

| Column | Meaning |
|---|---|
| `key` | `<type>-<cache row id>`, e.g. `cryptic-1252`. Stable while Jagex keeps the row. |
| `type` | `anagram`, `cipher`, `coordinate`, `cryptic`, `emote`, `fairyring`, `falo`, `hotcold`, `map`, `music`, `skillchallenge` (the cache table). |
| `tier` | `beginner`, `easy`, `medium`, `hard`, `elite`, `master`, `quest` (cache difficulty 0-6; 6 = the cache's "quest clues": the Treasure scroll / Mysterious orb steps of X Marks the Spot, items 23067-23070, and four "Clue scroll" items 23814-23817 in the elven lands whose quest was not checked). |
| `items` | Clue item ids whose item param 623 points at this row (comma list). Empty for beginner and master steps (one shared item per tier) and hot/cold (a device). Challenge scrolls also carry param 623 and are listed here too. |
| `text` | The clue text as the cache has it (`<br>` made a space). Map and hot/cold steps have none; music steps give the track name. |
| `target` | What the step points at: `npc`, `loc` (an object), `coord` (a tile: dig, emote or hot/cold spot), `key` (a chest opened with a key from an NPC), `kill`, `none`. |
| `targetIds` | NPC or object ids (kill: every NPC id that counts). |
| `targetName` | Names from the cache (several forms joined with ` / `; `?` = no name). |
| `x`, `y`, `plane` | The target tile from the cache. Empty for kill steps (the cache gives none). |
| `description` | The cache's own description of the target. |
| `places` | Other places, `role:ids:name@x,y,plane`: `stash` (STASH unit), `key-npc` (where the key's NPCs are), `alt-target` (a second target row: the NPC stands elsewhere, e.g. after a quest). |
| `needs` | What the step needs, in words: `wear ...` (emote outfits; `one of` = alternatives in one slot; `any trail item of group N` = the game's item group), `item ...` (with the cache's description), skill levels (`Agility 60`), `quest: ...`. Only what the cache states; the spade and light sources are hand-authored in `clue-overrides.tsv` (below). |
| `notes` | Other facts: `emote N` (the game's emote number), `fight: ...` (double agents, wizards), `question: ... = answer`, `puzzle: ...`, `fallback npc ...`, `area: ...`, `fairy ring N, steps a,b,c,d`, `unlocked at: ...` (music). |
| `source` | `cache 2735 table T row R -> table T2 row R2` (the clue row and its target rows). |
| `checks` | What to look at: kill steps without a tile, rows the cache marks as restricted content, missing targets, unknown values. Empty = nothing in doubt. |
| `action` | What the player does there, in the game's own clue helper's terms (client scripts of the cache, numbers below): `dig`, `emote <id> <name>` (several joined with ` \| `, in the table's order; names from enum 1000), `music` (play the track near the NPC), `talk`, `search`, `key` (kill for the key, then search), `kill`, `none`. |
| `requirement` | `needs` as a requirement line (`docs/DATA_FORMATS.md`), straight from the tables: each outfit slot as `item a/b/c worn` (the slot's items and every item of its trail item group: alternatives), the "any slot" group too; items to bring (table 21, with the count; table 22's group as ids); skill levels (table 24); `quest <name>` (table 23, the quest table's display name, state 2 = done); a key step's key. Trail item groups are item param 258. Empty = nothing. |
| `stash` | The STASH unit's state as the game reads it: `varbit V ; tier ; place` (V = the STASH object's own form varbit: 0 = "Build", 1 = built "STASH" with "Search"; tier and place from enums 1368 and 1531). Empty without a STASH unit. |

How the new columns were checked in cache 2735 (client scripts disassembled
with RuneLite's cache library, dev time only):

- **Dig or not**: script 6651 is the game's own rule: fairy ring, coordinate
  and hot/cold steps; map, cryptic and cipher steps whose target is a tile
  (table 18, or the empty table 17); the one anagram row 2299. Map clues that
  are not digs say "search the marked location" (script 6644).
- **Emotes**: script 6640 lists a step's emotes from the emote column with
  enum 1000 (emote number -> name; -1 would be "Bull roarer"). 12 steps have
  two emotes.
- **Outfit**: script 6484 compares `oc_param 258` of the player's items with
  the outfit table's PARAM columns, so those columns are trail item groups of
  item param 258 (the same param table 22 names).
- **STASH built**: script 6472 maps a STASH object through enum 1368 to a tier
  code (1 easy ... 5 master, 6 beginner) and a bit, and tests that bit of the
  tier's varp (1365-1369, 2202; RuneLite `VarPlayerID.HH_CONSTRUCTED_*`). For
  all 119 STASH units the object's own form varbit is exactly that bit
  (`VarbitID.HH_CONSTRUCTED_*`), and form 1 is the built "STASH (tier)" with
  "Search" (form 0 "Build"). The extractor checks this per unit.
- **STASH filled**: the game keeps no per-unit "filled" var. The clue helper
  shows "Filled" / "Built" / "Not built" for the clue it tracks (script 6624):
  "Filled" when varbit 14164 (`CLUEHELPER_HIDEYHOLE_FILLED`) is 1, for the
  clue row in varp 3546 (`CLUEHELPER_INFOBOX_CLUE`, set by the server through
  script 6631). The STASH chart (interface 493, scripts 1475-1479) gets the
  filled bits only as script arguments while it is open, and some units'
  contents as inventory 576 (`HH_INV`). docs/PLUGIN_CLUES.md says what the
  plugin uses.

**Restricted content.** The 14 rows the cache marks "restricted content = 1" (all Sailing-era places) are normal
clue steps: the flag is ignored. It stays only as a `checks` note in this generated
file; the clue check and the plugin export do not treat these steps differently (they never did).

## Kill steps

The 14 elite "kill" steps name NPCs but give no tile, and NPC spawns are not
in the cache. `data/wiki/clue-kill-spawns.tsv` (Wiki-derived, CC BY-NC-SA 3.0,
development time only, page and revision per line) lists where those NPCs
are found, from the `{{LocLine}}` templates of their Wiki pages. Two steps
have no Wiki places: the ferocious barbarian spirit (it appears when the
pyre ship burns) and the shellbane gryphon (its page pins only the cave
entrance); both have a `tile` and a
`note` override (a hand-chosen tile; the gryphon's cave entrance). The Wiki's
`plane` is the plane of its own map layer and is sometimes not the game plane
(God Wars Dungeon, Slayer Tower basement): `area-plane` overrides put those on
the game plane. Some Wiki map layers are drawn away from
the game's own tiles (Stronghold of Security, Wilderness God Wars Dungeon,
Karuulm Slayer Dungeon): `area-shift` moves them onto the game's tiles.
`skip-area` leaves out the Slayer-task-only areas and areas nobody can reach.

## Manual corrections (`clue-overrides.tsv`)

`clue-steps.tsv` stays exactly as generated; the manual corrections live in
`clue-overrides.tsv` (hand-authored) and are applied by the clue
check (`clueCheck`) and the plugin export (`exportPlugin`) through one loader,
`tool/.../clue/ClueOverrides.java` (unit tests: `ClueOverridesTest`,
`KillAreasTest`). Tab-separated, UTF-8, `#` comment lines, then the header
`key  change  value  source`. Every line needs a source (a manual decision, Wiki
page + revision, what the check found). Changes:

| Change | Value | Effect |
|---|---|---|
| `tile` | `x,y,plane` | replaces the step's target tile (e.g. Vannaka at the Wiki's tile) |
| `retired` | `-` | the step is dropped (no clue item points at it) |
| `needs` | a requirement line (`docs/DATA_FORMATS.md`) | what the player needs besides the clue; several lines are joined with `; ` |
| `skip-area` | a Wiki location name of `data/wiki/clue-kill-spawns.tsv` | a kill step does not route there (Slayer-task-only areas) |
| `area-plane` | `<plane> <Wiki location name>` | that Wiki area's spawn tiles are on this game plane (the Wiki's dungeon map layers say 0) |
| `area-shift` | `<dx>,<dy>,<plane> <Wiki location name>` | that Wiki area's spawn tiles are moved by dx, dy onto the game plane (a Wiki map layer drawn elsewhere). Found by moving the layer by whole 8x8 chunks until every spawn tile stands on the cache's floor; the source says how |
| `note` | text | a short note for the player about the step (what to do there), carried to the plugin export (`clues.tsv` column `note`); several lines are joined with a space |

The key `*dig` applies a line to **every dig step**: the
steps whose `action` is `dig`, the game's own rule (client script 6651, see
above): every coordinate, hot/cold and fairy ring step, and every map, cryptic
or cipher step whose target is a tile (a map to a crate is searched, not dug),
plus the anagram row 2299. Emote spots are tiles too but nobody digs there.
Before that the rule was ours (`ClueOverrides.isDigStep`, still used for steps
without an action) and left out the two quest-tier steps anagram-2299 and
cipher-2262, which the game's rule now shows are digs. 409 steps today (412
before the retired ones are dropped); `*dig needs item 952` gives them the spade.

Light: a spot inside a dark area (`dark-areas.tsv`: name, box `x0 y0 x1 y1
plane`, the requirement, source) gets that area's requirement as a `needs`
line, the same line as the ways into the area (Fire of Eternal Light varbit
where it exists). A test checks every clue target and STASH unit inside a box
has it. Today: the Lumbridge Swamp Caves emote (emote-2135) and the genie
(cryptic-1439).

Kill steps: the check and the export use one point per **group** of Wiki
spawns (`KillAreas`: tiles of one Wiki area within 12 tiles of each other,
chained), after `skip-area`, `area-plane` and `area-shift`.

A blocked dig / emote spot (inside a rock, tree or STASH unit) is done from
the tile next to it; the target tile is not changed for that.

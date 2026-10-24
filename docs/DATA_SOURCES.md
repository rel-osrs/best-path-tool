# Data sources and network policy

This document covers where each kind of data comes from, the Wiki's licence
terms, and a proposal (not implemented) for optional GitHub-hosted updates.

**Permitted sources:**
- the OSRS game cache;
- the public RuneLite API;
- hand-authored data;
- other plugins' data used with their developers' permission (below);
- Wiki data gathered at **development time only**, by a dev-only tool (polite,
  rate-limited requests, cached locally, provenance recorded per record).

The plugin never contacts the Wiki, and it makes no web requests at all: everything
ships inside the jar.

**Other plugins' data.** Other plugins' code is not copied. Their data is used where
permission exists:
- Shortest Path's transport `.tsv` files, copied unchanged into `data/shortest-path/`
  (see `data/shortest-path/SOURCE.md`), used with its developers' permission. They are a
  switchable set, never ground truth.
- Quest Helper's data (BSD-2-Clause, commit a526461): quest varbit / varp numbers and the
  stage values of its quest steps (requirement values), and its sea charting list
  (`ChartingTasksData.java`) for each charting task's places and tips. The record's source
  says "Quest Helper <file>".
- RuneLite's Slayer plugin task-to-item picture list (BSD-2-Clause), used as data in
  `data/manual/slayer-icons.tsv` (the commit is recorded there).

See also `THIRD_PARTY_NOTICES.md`.

**Provenance:** every record in a hand-maintained data file carries a
`source` column. Values:
- `cache:<table/obj id>`
- `observed:<date>`
- `hand:<initials/date>`
- `wiki:<page>@<revid>`

## 1. Source per data kind

| Data | Source | Notes |
|---|---|---|
| Collision (directional, per plane) | **cache** (extractor) | Derived from terrain, walls and objects; validated against the live client `CollisionData`. |
| Map imagery | **cache** (tool only) | No map images in the plugin. |
| Transport candidates (doors, ladders, stairs, shortcuts) | **cache** | Object options plus placements. |
| Transport endpoints for those candidates | **inferred from cache, then verified** | Pairing heuristics flag unverified edges. They are verified by the observation recorder (below) or by hand in the editor. Unverified edges are **not exported** (no automatic guessing). |
| Fairy rings, quetzals, Pendant of Ates, charter ports | **cache** DB tables | Arrival tiles verified in game. |
| Spell and jewellery teleport destinations | **hand-authored**, optionally checked against **dev-time Wiki** | Not in the cache. |
| Tick costs | **observed** in game (recorder), or hand-authored defaults | Not in the cache. |
| Transport requirements | **hand-authored**, optionally assisted by **dev-time Wiki** | Some quest requirements are in the cache `Quest` table. |
| "Unlocked" varbits | **RuneLite gameval names** plus hand-authored mapping | |
| Clue steps (targets, keys, kills, items, STASH units) | **cache** `Cluehelper*` tables | Remaps (wrong floor, unreachable) are hand-authored overrides. |
| Quest destinations | Runtime messages from Quest Helper; quest start points from the cache `Quest` table | |
| Quest progress vars (which varbit / varp holds each quest's progress; the plugin re-reads a quest only when its var changes) | **Quest Helper data** (BSD-2-Clause, commit a526461): `QuestHelperQuest.java`, `QuestVarbits.java`, `QuestVarPlayer.java` | `src/main/resources/com/bestpath/player/quest-vars.tsv`, made by `gradlew questVarsExtract`; "Quest Helper <file> <entry>" recorded per line; VarbitID names turned into numbers with RuneLite's public gameval. Their code is not used. |
| Sea charting tasks | **cache**: DB tables 154 / 161 / 194 / 200 / 201 (tasks, tools, docks, hazards, sets), client script 9001 (Sailing level), enums 5868-5870 (XP); places and tips: **Quest Helper data** (BSD-2-Clause, commit a526461) `ChartingTasksData.java` | `data/charting/charting-places.tsv`, made by `gradlew -p tool chartingPlaces`; "Quest Helper ChartingTasksData.java a526461" per line; joined by the done varbit's RuneLite gameval name. Their code is not used. |
| POH furniture and portals | Runtime scan plus cache `Furniture`/`PohHotspot` tables | |
| Item families (charged variants) | **cache** item names plus hand-authored grouping | |
| Sailing | The written Sailing spec only (`docs/SAILING_SPEC.md`) | |

### The observation recorder (proposal)

A **dev-only** mode of the plugin, enabled by a hidden developer
config. While a developer plays normally, it uses only public RuneLite APIs
(menu option clicked, object id, the player's position before and after,
and tick timestamps) to write candidate records:

> transport X at A led to B, took N ticks, while these items and varbits
> were present.

The records are saved to a local file that the editor imports. **No input
is automated:** the player performs every action, which keeps it within
Jagex's third-party client rules. It turns "ask the Wiki" into "observed in
game", the most trustworthy provenance.

The recorder would be shipped disabled, or stripped from release builds.

## 2. The OSRS Wiki: licence and implications

This is a summary, not legal advice.

- The OSRS Wiki's text is published under **Creative Commons
  Attribution-NonCommercial-ShareAlike 3.0** (CC BY-NC-SA 3.0).
- Game content itself (names, images) is Jagex's intellectual property.
- **Implications for a shipped dataset that contains Wiki-derived records:**
  1. **Attribution:** credit the Wiki and the specific pages, ideally per
     record. The `source` column (page and revision) supports this.
  2. **NonCommercial:** the plugin is free, so this is compatible as far as
     can be told. But anyone reusing the dataset is bound by it too.
  3. **ShareAlike:** a dataset *derived from* Wiki content probably has to
     be distributed under CC BY-NC-SA. That is **incompatible with putting
     that data under the BSD-2 licence.** The practical approach:
     - keep Wiki-derived records identifiable (page and revision per record,
       and in `data/wiki/` their own licence notice, CC BY-NC-SA 3.0, with
       attribution);
     - keep the code and the cache/hand/observed data under BSD-2.
     The Wiki-derived parts of the data shipped in the plugin are shared under
     CC BY-NC-SA 3.0 (see `THIRD_PARTY_NOTICES.md`).
  4. **Facts vs expression:** bare facts (a coordinate, a level number) may
     not be copyrightable in some places, but database rights differ by
     country (e.g. the EU). This is uncertain and is best checked with someone
     qualified.
- **Scraping etiquette** (for the dev-only tool):
  - use the MediaWiki API, not HTML scraping;
  - send a descriptive User-Agent;
  - make one request at a time with delays;
  - cache responses locally and never re-fetch unchanged pages;
  - record the page revision id with each record.

**Approach:**
- Prefer **cache + observed** data.
- Use Wiki data only to *cross-check* or *fill gaps* (spell destinations,
  requirements).
- With the cache's clue tables, clue data needs no Wiki except for the kill
  steps.
- Confirmed Wiki-derived records go into the normal change records
  (`data/manual/changes.jsonl`), each keeping its source (page and revision)
  for attribution; there is no separate main-data file for them.

## 3. Proposal: optional GitHub-hosted data updates (not implemented)

Today the plugin makes **no network requests**. This section describes how an
*optional* update channel could work, if it is wanted later.

**What could be hosted:**
- Only small **data** files that change more often than plugin releases:
  `transports-overrides`, clue remaps, and the `manifest.json`.
- **Never code.** RuneLite forbids downloading or dynamically loading code.
- Collision stays in the jar, because it is large and changes with game
  updates anyway. (No map imagery ships in the plugin.)

**Hosting:** the project's GitHub repository, served from `raw.githubusercontent.com`
at a **tagged path**, never from a branch head. For example:
`.../bestpath-data/v3/manifest.json`.

**Manifest:**
- `schemaVersion`, `dataVersion`, the minimum and maximum plugin version, and
  for each file its path, size and **SHA-256**.
- The plugin only accepts files whose hash matches the manifest, and
  ignores schemas it doesn't understand.

**Update frequency:**
- At most once per client start, and no more than once every 24 hours.
- Uses `If-None-Match`/ETag.
- No request at all while the toggle is off.

**Caching:**
- Downloaded files are kept in the plugin's data folder, through RuneLite's
  file utility (all file I/O goes through `Filepath`).
- They are validated, then used. If validation fails, they are deleted.

**Offline fallback:**
- The bundled jar data is always present.
- The downloaded copy is used only if it is valid and newer.
- A failed request is silent, apart from a log line at debug level.

**Config toggle:**
- `Fetch data updates from GitHub`, **off by default**.
- It must carry RuneLite's exact warning text: *"This feature submits your
  IP address to a 3rd-party server not controlled or verified by RuneLite
  developers"* (from RuneLite's plugin guidelines).

**Implementation constraints** (RuneLite's plugin guidelines):
- use the injected `OkHttpClient` with `enqueue()`, never on the client
  thread;
- use the injected `Gson`;
- no new dependencies.

### What is known about Plugin Hub policy

- **Verified** (from RuneLite's official template guidelines):
  - Third-party-server features must be **opt-in**, with the warning text
    above.
  - HTTP must go through OkHttp on its own thread pool.
  - Downloading or dynamically loading **code** is forbidden.
- **Verified:** the Plugin Hub README requires third-party dependencies to
  be hash-verified and reviewed (this slows reviews).
- **Not verifiable from the repo:**
  - whether reviewers accept a plugin that downloads **data** updates;
  - any jar-size limit;
  - whether a multi-module Gradle build is accepted.

  These would need to be asked of the RuneLite maintainers, for example on their
  Discord, before relying on them.

## 4. Dev-time Wiki data in use

All of this is gathered at development time only; the plugin never contacts the Wiki.
The Wiki-derived data keeps its page and revision per record and its CC BY-NC-SA 3.0
licence (section 2).

- **Teleport destinations** (spells, jewellery and other teleport items) come
  from the Wiki, because the cache has only their names.
- Gathered by `tool/src/main/java/com/bestpath/tool/wiki/WikiTeleportsCli`
  (`gradlew -p tool wikiTeleports`), development time only:
  - MediaWiki API only, descriptive User-Agent (no personal contact), one
    request at a time with a 2 s pause, page text 50 pages per request;
  - raw responses kept in `cache/wiki/` (not in Git); a full run is 8 requests;
  - pages: those using `{{TeleportLocationLine}}` plus the categories
    "Teleport spells" and "Teleportation items";
  - read only from `{{TeleportLocationLine}}` and from `{{Map}}` under a
    heading containing "destination" or "teleport"; area outlines become one
    point in the middle, noted as "lands somewhere in an area".
- Output: `data/wiki/teleports.json`, with `data/wiki/LICENSE-WIKI.txt`
  (CC BY-NC-SA 3.0, attribution per record: page and revision).
- In the tool they are **suggestions only**: each one is confirmed by hand.
  Spell/item pages become teleports (from anywhere); other pages become
  networks. Groups the cache provides (e.g. Pendant of Ates) are skipped.
  Confirmed Wiki-derived records go into the normal change records, each keeping
  its source (page and revision) for attribution.
- **Shipwright places**: the cache
  names the NPCs that can bring a player's boat ("Recover-boat") but has no
  NPC places. `tool/src/main/java/com/bestpath/tool/wiki/WikiShipwrightsCli`
  (`gradlew -p tool wikiShipwrights --args="<cache folder>"`) takes the NPC
  names and options from the cache and reads only the map pins of their
  Wiki pages: one request for all 17 pages (same politeness rules, raw
  response in `cache/wiki/`). Output `data/wiki/shipwrights.json` (CC BY-NC-SA
  3.0, page and revision per record). The tool uses it only to say which dock
  has a shipwright (nearest dock within 40 tiles); the result matches the 16
  docks that have a Shipwrights' Workbench in the cache.
- **Clue kill-step places**: the cache's clue
  tables name the NPCs of the 14 elite "kill" steps but give no tile, and NPC
  spawns are not in the cache. `tool/src/main/java/com/bestpath/tool/wiki/WikiClueKillsCli`
  (`gradlew -p tool wikiClueKills --args="<repo>/cache/wiki"`) reads the NPC
  names from `data/clues/clue-steps.tsv` and only the `{{LocLine}}` templates
  of their Wiki pages: one request for all 27 pages, at least 3 s between
  requests, counted in the shared daily request budget
  (`cache/wiki/review/_state.json`), raw
  response in `cache/wiki/clues/`. Output `data/wiki/clue-kill-spawns.tsv`
  (CC BY-NC-SA 3.0, page and revision per line). The clue check uses it, and
  the plugin export ships the places in `clues.tsv` (the `wiki` column names
  each page and revision; `docs/CLUES.md`).
- **Banks** (routes that begin at a bank): bank booths and
  chests come from the cache; the Wiki gives what the cache can't: each
  bank's name and requirements, and where the bankers stand at the six banks
  with no booth or chest (NPC spawns are not in the cache).
  `tool/src/main/java/com/bestpath/tool/wiki/WikiBanksCli`
  (`gradlew -p tool wikiBanks --args="<repo>/cache/wiki"`) reads the page
  "List of banks" (its main table) and the six bankers' pages: 2 requests,
  same politeness rules, counted in the shared daily request budget, raw responses in
  `cache/wiki/banks/`. Output `data/wiki/banks.tsv` and `data/wiki/bankers.tsv`
  (CC BY-NC-SA 3.0, page and revision per line); `gradlew -p tool banks`
  combines them with the cache into `data/banks/banks.tsv`, which the plugin
  export ships (bank names, needs and the six bankers' tiles are Wiki-derived;
  section 2 applies). The Wiki's Treasure Trails list pages (2 requests) were
  read once, by hand, only to cross-check the cache's completeness
  (docs/CLUES.md §2); nothing from them is stored.
- **Slayer task places**: the cache has
  the tasks, which master gives which (with the amounts) and Konar's areas
  (DB tables 113-116), but no NPC spawns and no names for the master
  numbers. `tool/src/main/java/com/bestpath/tool/wiki/WikiSlayerCli`
  (`gradlew -p tool wikiSlayer --args="<cache folder> <repo>/cache/wiki"`)
  reads the page "Slayer task" (its list of assignments, for the task pages
  and, for tasks without one, the monsters), each task's page (its
  `{{Infobox Slayer}}` amounts per master, its monster variants and its
  location table) and the monsters' pages (only their `{{LocLine}}`s): about
  10 requests for a full run (13 the first time, then 8 for the
  pages added), 50 pages per request, at least 3 s between requests, counted
  in the shared daily request budget, raw responses in `cache/wiki/slayer/`, a
  User-Agent naming the purpose ("Slayer monster places"). Output
  `data/wiki/slayer-places.tsv` (one line per place, page and revision per
  line, the monster pages behind the tiles) and `data/wiki/slayer-masters.tsv`
  (master names, matched by the amounts); the plugin export ships
  `slayer.tsv` made from them and the cache (place names, amounts, combat
  facts, notes and tiles are Wiki-derived; section 2 applies).
  `data/manual/slayer-extra-places.tsv`:
  11 Wilderness spider places and
  38 places for task monsters the location tables leave out, made from those
  same saved monster pages (no new requests), page and revision per line;
  same licence.
- **Slayer special loot per place**:
  `tool/src/main/java/com/bestpath/tool/wiki/WikiSlayerDropsCli`
  (`gradlew -p tool wikiSlayerDrops --args="<cache folder> <repo>/cache/wiki"`)
  reads the monster pages `wikiSlayer` already saved in `cache/wiki/slayer/`
  (their drop tables) and asks only for what is missing: pages not saved yet
  and page-specific drop tables kept on another page (`Template:Uniques/...`;
  2 requests the first time: "Chilled Jelly", "Suqah (single-way
  combat)", and the Vyrewatch Sentinel's and the lizardman shaman's tables).
  Which items count comes from the game cache: the collection log (enum 2102).
  Output `data/wiki/slayer-place-monsters.tsv`
  and `data/wiki/slayer-drops.tsv` (page and revision per line); the export
  ships item numbers and "From <monster>." notes per place in `slayer.tsv`
  (Wiki-derived like the Slayer places). **Grand Exchange prices**
  are not used. A possible source to decide on
  later is the Wiki's real-time prices API (prices.runescape.wiki, run with
  RuneLite), read once at development time and saved with its date; nothing is
  fetched for it now.

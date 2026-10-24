# Best Path: plugin and Data Tool

The development home of the Best Path RuneLite plugin and the **BestPath Data Tool**, the
desktop program that makes and maintains the plugin's map and transport data from the Old School RuneScape game
cache.

The plugin itself is also published on its own (just the plugin, the way the RuneLite Plugin Hub builds it). This
repository holds everything behind it: the Data Tool, the hand-maintained data, the shared pathfinding code, the
tests and the design docs.

## What's here

| Folder | What it is |
|---|---|
| `src/` | The RuneLite plugin (root Gradle build). |
| `core/` | Pure-Java logic shared by the plugin and the tool: collision data, the coarse graph, the pathfinder. No RuneLite client dependencies. |
| `api/` | The public API names other plugins use (see `docs/API.md`). |
| `tool/` | The BestPath Data Tool (Swing): cache reading and extraction, map editing, transports, route checks, the plugin data export, cache comparison after game updates. |
| `data/` | Hand-maintained data: manual edits (`manual/changes.jsonl`), region review notes, clue overrides, Wiki data gathered at development time (`wiki/`), and the Shortest Path transport files used as a comparison set (`shortest-path/`). |
| `docs/` | Data formats, data sources and licences, plugin UI and feature specs, the after-a-game-update checklist. |

Not in the repository (git-ignored): `cache/` (game caches and tool output), `map-pictures/` and `reference/`.

## Building

Java 11 target, built with JDK 21, through the Gradle wrapper:

- Plugin: `./gradlew build` (tests: `./gradlew test`)
- Core: `./gradlew -p core test`
- Data Tool: `./gradlew -p tool run` (or double-click `tool/run-tool.bat` on Windows); tests `./gradlew -p tool test`

Some tests and most Data Tool features need a game cache in `cache/` (the tool's **Download a newer cache...**
fetches one from the OpenRS2 Archive). After a game update, follow `docs/GAME_UPDATE.md`.

## Data sources and licences

The code is under the BSD 2-Clause licence (`LICENSE`). The plugin never contacts the OSRS Wiki or any other site:
Wiki facts are gathered only at development time by the Data Tool, politely and cached, and each record names its
source. See `docs/DATA_SOURCES.md` and `THIRD_PARTY_NOTICES.md`, plus `data/wiki/LICENSE-WIKI.txt` (Wiki data,
CC BY-NC-SA 3.0) and `data/shortest-path/SOURCE.md` (Shortest Path's transport files, used with its developers'
permission).

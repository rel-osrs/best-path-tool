# After a game update: step by step

What to do when Jagex updates the game and the plugin's data should follow. Everything is in the Data Tool unless a
step says "command line" (run those from the workspace folder; `<cache>` is the new cache's folder, e.g.
`cache/openrs2-2741`).

## 1. Compare the new cache with the current one (changes nothing)

1. Toolbar: **Compare with a new cache...**
2. **Download a newer cache...** (from the OpenRS2 Archive), or **Other folder...** for a cache you already have.
3. **Compare**. The report is saved as a Markdown file named `<date>-cache-<old>-to-<new>.md`.
4. Tick **Show the changes on the map** and press **Go to the first card**. The cards ask about what matters:
   transports whose objects moved or vanished, ground under transport ends that changed, and walls that changed in
   regions you reviewed in the Region review (changes in regions you never reviewed are only listed in the report).
   Answer each card.

## 2. Preview the plugin's data from the new cache (changes nothing)

1. In the same window: **Preview the plugin's data**. The clue steps, the banks and the plugin's data are made from the
   new cache into `cache/cache-move/<new>/`.
2. The **Plugin data** tab lists what would change, line by line. Look for anything unexpected.

## 3. Move to the new cache

1. **Move to this cache...** A backup of the data and the old cache is made first (`cache/backups/<date>-before-cache-<new>/`).
2. The new clue steps and banks go into `data/`, and the tool opens the new cache.
3. Toolbar: **Update plugin data...** makes the plugin's data from the new cache and checks routes on it
   (the check must say 0 routes differ).

## 4. Steps the move does not do yet (command line)

- **House objects** (the house things the plugin scans for):
  `gradlew -p tool houseObjects --args="<absolute cache folder>"` (writes
  `src/main/resources/com/bestpath/player/house-objects.tsv`).
- **Sea charting places**, only if Quest Helper's charting list changed:
  `gradlew -p tool chartingPlaces --args="<quest-helper folder> <cache>"`.
- **Wiki data**, only when something on the Wiki changed that the data uses (each fetch is polite and cached):
  `wikiBanks`, `wikiTeleports`, `wikiShipwrights`, `wikiClueKills`, `wikiSlayer`, `wikiSlayerDrops`
  (see `tool/build.gradle` for each one's arguments).

## 5. Check, then publish

1. Command line checks: `gradlew -p tool clueCheck --args="<cache> 4"` (can every clue place be reached?) and
   `gradlew -p tool groundCheck --args="<cache>"` (do transports start and end on real ground?).
2. Tests: `gradlew test` and `gradlew -p tool test`.
3. Try the plugin in the game.
4. Toolbar: **Publish plugin...**, then **Publish and build check**.

## If "Update plugin data" warns about sea charting

When a game update changes the sea charting tables or the script that gives each task's level, the export can't read
the charting tasks from the new cache. It then keeps the plugin's charting file as it was and finishes with an orange
result line ("1 warning: see the lines starting WARNING: in the log"). Everything else is made from the new cache as
usual. The charting import (`tool/.../extract/ChartingExtractor.java`) then needs a look against the new cache.

## The cache numbers the tool reads by number

The game cache has no names for its tables, scripts and enums, so the tool's import code uses their numbers. After a
game update these are the places that can break. They were found in cache #2735; the plugin's data, the clue steps
and the banks are made from #2740 with them (house objects not yet). If a number moves, the file named here is where
to change it.

| What | Numbers | File (`tool/src/main/java/com/bestpath/tool/`) |
|---|---|---|
| Clue helper | DB tables 3-28 (one per clue part), params 623 and 258, enums 1000, 1368 and 1531 | `clue/ClueTables.java`, `clue/ClueExtractor.java`, `route/ClueExport.java` |
| Sea charting | DB tables 154, 161, 194, 200, 201; varbits from 18574; client script 9001; enums 5868-5870 | `extract/ChartingExtractor.java` |
| Boats | DB tables 147, 164, 166, 175-179, 181, 185, 187 (rows 8545-8547), 194; client script 9085 | `extract/BoatTableExtractor.java` |
| Docks | DB tables 0 and 194 | `extract/DockExtractor.java` |
| Teleport destinations | DB tables 64, 72, 89, 206 | `transport/CacheTableTeleports.java` |
| Menu texts | DB tables 64, 89, 206; interface 138; client scripts 1685, 1688; enums 1377, 1401; params 596, 601, 660, 679, 680; varbit 20252 | `menu/MenuFactsLoader.java` |
| House objects | enum 1377; params 660, 672, 673, 679, 680; client scripts 2664, 2671, 2674; varbit 6670 | `extract/HouseObjectsCli.java` |
| Wilderness levels | client scripts 247, 384 | `extract/WildernessScript.java` |
| Slayer | DB tables 113-116; enum 2102 and params 682, 683, 689, 690 (the collection log) | `slayer/SlayerTables.java`, `slayer/CollectionLog.java` |
| Quest names | DB table 0 | `req/CacheNames.java` |

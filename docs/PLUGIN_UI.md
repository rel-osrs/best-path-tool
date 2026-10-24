# Plugin UI: quick preview and pictures

The Best Path sidebar can be looked at **without starting RuneLite**. Two developer
tasks open only our sidebar, with RuneLite's own colours, fonts and look, a fake player
and made-up routes. Nothing here goes into the plugin jar (the code is in
`src/test/java/com/bestpath/dev/`).

| Task | What it does | Time on this PC |
|---|---|---|
| `uiPreview` | Opens a window: developer controls on the left, the real sidebar (242 px wide, like RuneLite's) on the right. | window after ~1.4 s (~2.9 s including Gradle) |
| `uiShot` | Saves pictures of the sidebar states to `build/ui-shots/*.png`. No window opens. | ~4 s including Gradle |
| `run` (for comparison) | The full RuneLite client with the plugin. | client window after ~19 s (before login) |

## In IntelliJ (click by click)

1. Open the **Gradle** tool window (the elephant icon on the right edge).
2. Expand **bestpath** > **Tasks** > **bestpath**.
3. Double-click **uiPreview** (window) or **uiShot** (pictures).
4. For `uiShot`, the pictures are in the project's `build/ui-shots/` folder
   (in the Project view: `build` > `ui-shots`; double-click a `.png` to look at it).

## From a terminal (Git Bash)

```
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.11"
./gradlew uiPreview
./gradlew uiShot
```

Extras: `./gradlew uiPreview -PexitAfterShow` opens the window and closes it at once (prints how long
it took); `./gradlew uiPreview -Pshot=build/ui-shots/0-preview-window.png` finds the sample route, saves
a picture of the whole window and closes.

### With the real map data (`-PrealData`)

Both tasks can use the **real** map data instead of made-up routes: the plugin's own loader and route
search, for the Data Tool's test players (Maxed, Fresh account, a mid-level member, free-to-play, and
your own players from `data/manual/test-players.json` if that file exists). Make the data once first
(about 45 s; it also checks that the routes match the Data Tool's):

```
./gradlew -p tool exportPlugin --args="cache/openrs2-2735"
./gradlew uiPreview -PrealData
./gradlew uiShot -PrealData
```

In the window the players stand in Lumbridge (3222, 3218, 0); the **Player stands at** box (left)
moves them (type a tile, press Enter). `uiShot -PrealData` also saves `real-1-maxed.png` (Lumbridge to
the Varrock sewers ladder: Varrock Teleport, walk, manhole, walk), `real-2-fresh.png` (the same for a
fresh account), `real-3-f2p.png` (to Draynor bank on free land), `real-4-unreachable.png` (a tile no
one can stand on: the route stops next to it) and the `-full` versions, plus real clue steps detected
the plugin's way and routed as the Maxed player (`real-5-clue-emote-*`: an easy emote clue with its
STASH unit; `real-6-clue-kill-*`: an elite kill step, nearest of 6 Wiki areas; `real-7-clue-master-text-*`:
a master scroll before and after its text is read; `real-8-clue-hotcold-*`: a master strange device,
nearest of 136 spots). `-1-detected` = the clue line and "Use clue" on, `-2-route` = after pressing it.
`real-10-sailing(-full).png`: the Maxed player with the test sloop at Port Sarim (made-up boat varbits read through the
real `boats.tsv`) from Lumbridge to Remote Island: teleport, a shipwright brings the sloop, board, sail, leave;
`real-10-sailing-settings-full.png`: its Settings tab with the sloop. The sailing cards (Board, Sail, Leave, a
shipwright bringing the boat, releasing it from its bottle) show the boat's own picture, as the Settings tab does.
`real-11-sailing-capsized(-full).png`: the same sloop capsized, the same destination: a shipwright brings it
("Recover-boat Shipwright Sylvia", then "Board Gangplank").
`real-40-no-route-shipwrights-off.png`: the same with "A shipwright may bring my boat" switched off: the "no route"
card with that tick box; `real-41-after-shipwright-tick.png` after ticking it on the card (the route again);
`real-42-no-route-boat-off.png`: the moored sloop switched off: the card with the boat's tick box.
`real-12-sailing-bottled(-full).png`: the same sloop bottled and the player carrying one full boat bottle: it is
released at a dock (10 ticks, no coins); `real-12-sailing-bottled-settings-full.png` its Settings row ("Bottled").
The uiPreview window's player list has both too ("Maxed + test sloop capsized", "... bottled, bottle carried").
`real-50-house-settings-not-looked.png` / `real-51-house-settings-scanned.png`: the Settings tab's
"House (POH)" section for a member with a Rimmington house, before the house was looked at and after a scan found an
ornate jewellery box, a portal nexus (Varrock, Camelot), a mounted glory and a spirit tree & fairy ring;
`real-52-house-jewellery-box.png`: that player from Lumbridge to Castle Wars (tablet into the house, the jewellery
box out, 18 ticks); `real-53-house-inside-nexus.png`: standing inside the house, to Varrock (the nexus, 14 ticks).
`real-9-toll-coins.png` / `real-9-toll-quest.png`: Lumbridge to Al Kharid bank through the toll gate for a
free-to-play player with 1000 coins (the card shows the coins) and one with Prince Ali Rescue done (the
card shows the quest). Both players are also in the uiPreview window's player list.

`./gradlew clueRouteCheck` detects every clue step the plugin's way and routes to each from Lumbridge
(counts and times; see `docs/PLUGIN_CLUES.md`).

`./gradlew memoryCheck` loads the real data like the plugin and prints the memory it keeps and the
time per route (see docs/DATA_FORMATS.md, "Plugin export v0").

## What the preview window can do

- Type a tile in the sidebar (for example `3233, 9860, 0`) and press **Go**: the fake search takes
  0.4 s ("Calculating..."), then shows an 8-step sample route (4 cards: walks have no card). Plane 3
  answers "No route".
- **Skip** on a card skips that transport for this route; **Never use** puts it on the blacklist
  (shown in the Settings tab with **Restore**).
- **-** / **+** on a card change that transport's ticks for this player by 1 (see "Tick changes on step
  cards" below); the fake route uses them too. The Settings tab's **Teleport and travel ticks** change a
  whole teleport category or network (the real categories and networks from `data/manual`; the rides' ticks
  from the export when there is one).
- **Fake player** (left): switch between a mid-level member, a fresh account, maxed and free-to-play;
  the sample route changes (no Magic 25: no teleport; no Agility 51: no pipe).
- **Show a state** (left): jump straight to Empty, Calculating, No route, No map data, Not logged in,
  either tab, **Fake boats** (three made-up boats in the Settings tab's Sailing section) or **No boats**.
- **Fake walking player** (left): **Walk along** walks the shown route one tile every 0.15 s (a fast
  "game tick"); the sidebar frames the current step (2 px, white; a bank card's in its yellow) (while walking: the next card, with
  "Next, after walking N tiles") and greys the steps already done, and
  at the end it says "Arrived". **Step off** puts the player 15 tiles away (past the default re-route distance
  of 10): after 3 ticks the route is
  searched again from there (the fake route then starts with a walk back). This is the plugin's real
  route follower; only the player's tiles are made up.

The sidebar, the route service and its worker thread are the real plugin code; only the player and the
route engine are fake. The pictures from `uiShot`:

| File | State |
|---|---|
| `1-empty.png` | nothing asked for |
| `2-calculating.png` | search running |
| `3-route-8-steps.png`, `3-route-8-steps-full.png` | sample route: 8 steps, 4 cards (walks have none), with their pictures |
| `4-route-skipped.png` | after pressing Skip on the teleport |
| `4b-walk-only.png` | a route that is only walking: one line "Walk there (35 tiles)" |
| `4c-ticks-clicked.png` | + three times on the teleport card ("7 ticks" above its - / +), - three times on the manhole card (2 ticks: "0 ticks", "-" greyed); only totals, the cards change at once, the route not yet |
| `4d-ticks-rerouted.png` | the clicks sent (after a short pause in the plugin): the route searched again, total 236 ticks (235 + 3 - 2) |
| `4e-ticks-settings-full.png` | the Settings tab's "Your tick changes" list (each transport's total ticks) with **Reset** |
| `4f0-category-ticks-folded-full.png` | the whole Settings tab as it opens: "Teleport and travel ticks" folded at its end ("Show") |
| `4f-category-ticks-settings-full.png` | the same unfolded (a click on its header; "Hide"): every teleport category and network, its icon, its total ticks above - / + |
| `4g-category-ticks-clicked.png` | - twice on Spell (4 -> 2 ticks), + once on Fairy ring: the rows change at once, nothing sent yet |
| `4h-category-ticks-sent-full.png` | the clicks sent: saved, the route searched again; "Your tick changes" lists "Spell (all): 2 ticks" and "Fairy ring (all): 6-21 ticks" with **Reset** |
| `4i-category-ticks-rerouted.png` | the Route tab after it: the Varrock Teleport card (a spell) takes 2 ticks, total 233 |
| `4j-ticks-typing.png` | the door card's total clicked: a box to type the ticks, "200" typed |
| `4k-ticks-typed.png` | Enter: the card shows "200 ticks" at once (nothing sent yet) |
| `4l-ticks-typed-rerouted.png` | sent: the route searched again, total 433 ticks (235 + 198) |
| `5-no-route.png` | no route |
| `6-no-map-data.png` | what the plugin shows when its jar has no map data |
| `7-bad-input.png` | a tile that can't be read |
| `8-not-logged-in.png` | asked while logged out |
| `9-settings.png`, `9-settings-full.png` | Settings tab with two blacklisted transports (Sailing: "Log in to see your boats here.") |
| `9b-settings-sailing-full.png` | Settings tab, Sailing: three made-up boats read the plugin's way (a sloop at Port Sarim, a skiff at Rellekka switched off, a bottled raft: used only while its bottle is carried) and the shipwright box |
| `9c-settings-config.png`, `9c-settings-config-full.png` | Settings tab, the config panel's settings at its top (Minimal mode on, re-route at 15 tiles, Arrows at turns, orange text set as from the config panel); the full one with Advanced unfolded at the bottom |
| `10-walking-step-4.png` | a fake player walks to the manhole after the teleport (step 4, a walk): the manhole's card (#3) framed in white with "Next, after walking 35 tiles", cards 1-2 greyed |
| `11-arrived.png` | the fake player reached the destination: route cleared, "Arrived" |
| `12-clue-detected.png` | a (sample) clue was detected: the orange clue line, "Use clue" switched on |
| `13-clue-route.png` | after pressing "Use clue": the route's header (clue title, text, place) above the status |
| `13-clue-route-full.png` | the same, whole: the clue step's own card at the end ("Dig here", the spade missing) |
| `14-clue-needs-text.png` | a beginner clue carried but not read yet: "Read the clue scroll ...", "Use clue" off |
| `9d-functions-empty.png` | the Functions tab before anything happened: the function select screen, a card per function with its summary line |
| `9d-functions.png` | the same with three saved locations, Quest Helper's step and four recent destinations |
| `9d-functions-saved.png` | the "Saved locations" page: "< Back" and the title, a button per place with x, then Name, Tile and Save |
| `9d-functions-recent.png` | the "Recent destinations" page (each with x, then "Clear the list") |
| `9d-functions-integrated(-full).png` | the "Integrated plugins" page: the plugins that work with Best Path, Quest Helper's current step and "Go to the quest step" under its entry |
| `3b-other-plugins-in-the-way.png` | the box above the tabs naming other plugins to turn off (their Shortest Path messages replace Best Path's routes) |
| `9e-slayer-timing.png` | the Functions tab's Slayer task (sample data): Kalphites, 87 left, while the travel times are worked out |
| `9e-slayer.png` | the same with travel times: the task's card (picture, name, left, master, Wiki), then the places fastest first with check / cross lines, special loot and Wiki buttons; the place outside the map data last without a Go |
| `9e-slayer-stored.png` | the same with a stored task (Ankou, 12 left, from Vannaka) in its own grey-edged card under the task's, no Go |
| `9e-slayer-filtered.png` | the same with "Only places with a safespot" ticked: "Showing 1 of 3 places.", the cave only |
| `9e-slayer-preferred.png` | the same with the lair starred (preferred places): at the top, "Preferred", a filled yellow star, and the yellow line "Go to the nearest place and Automatic Slayer routes pick among your preferred place." |
| `9e-slayer-konar.png` | a Konar task: only her area ("Konar's area"); the others are not shown |

| `9e-functions-charting.png` | the sea charting page at Sailing 20: the three big routes, "All oceans" with "Chart all oceans" under it, the two boxes, a row per ocean (red: can't be completed now) |
| `9e-functions-charting-sea.png` | the Ardent Ocean chosen: its seas (a Go each, red when they can't be completed now) and the Bay of Sarim opened (no Go per task; the rock needing Sailing 24 in red) |
| `9e-functions-charting-everything.png` | "Everything" chosen: every sea and bonus set under its ocean's name |

With `-PrealData`, the clue step's card in its states (real clue steps, routed from Lumbridge by made-up players
with every quest and unlock and only the items named; `-full` = the whole tab):

| Picture | What it shows |
|---|---|
| `real-13-clue-card-stash-filled` | emote-2098 (clap on the causeway to the Wizards' Tower): the outfit waits in the built, filled STASH unit: every item "In the STASH unit", "Search the STASH unit (...) to take your outfit", green "Everything is ready" |
| `real-14-clue-card-bank` | the same with the outfit in the bank and the STASH unit built (filled not known): every item "In your bank", orange "Take from the bank: ..."; the route begins at a bank whose card lists the outfit (148 ticks; this "every unlock" player may also use the PvP-world chest beside Lumbridge, varbit 3984) |
| `real-20-clue-bank-first` | the same for a player without "every unlock" (STASH unit not built): staircases up, "Bank at Lumbridge Castle bank: take out" the outfit, staircases down, then the clue card (194 ticks; straight there 132) |
| `real-15-clue-card-dig` | coordinate-1923, the spade in the inventory: "Dig here" with the spade, green |
| `real-16-clue-card-missing` | emote-2098: helmet worn, apron in the inventory ("wear it there"), ring nowhere: red "Missing: Emerald ring. The route still goes there." |
| `real-17-clue-card-key-missing` | cryptic-2278 without its key: "Search the Drawers with the key", the key "Missing" |
| `real-18-clue-card-note` | cryptic-1458 (the ferocious barbarian spirit): "Kill: Ferocious barbarian spirit", then the step's note as a plain line "Note: Kill the barbarian spirit here" |
| `real-23-clue-card-marks` | emote-2098 with the helmet worn (check), the apron in the bank (bank symbol) and no ring (X); the route begins at a bank whose box lists the apron |

## The clue step's card

- A clue route ends with one more card, the clue step itself (also when the route is only walking, and it stays
  after arriving: that is when "Dig here" matters). It shows: the clue scroll's picture and "Emote clue (easy)";
  the clue text and the place; what to do with its picture ("Dig here" + spade, "Perform the Cheer emote" + the
  emote's own icon, "Talk to Ranael", "Search the Drawers with the key" + the key, "Play "Alone" in the music
  player near Cecilia", "Kill: Ankou", "Swing the bull roarer"); the step's own note, if any, as a plain
  line ("Note: ...", data/clues/clue-overrides.tsv); every item with its picture, "Wear:" for an
  outfit, and where the player has it (Worn / In your inventory / In the STASH unit / In your bank / Missing, in
  green, orange or red); levels and quests the step needs; the STASH unit (built, filled, not known); one
  coloured line: everything ready, take from the bank (which items), or missing (which items: the route still
  goes there). The card's left edge has the same colour.
- Everything on it comes from the export (`clues.tsv`: needs, action, STASH varbit and place; from the cache,
  data/clues/README.md) and the player: inventory, worn items, the bank as last seen, levels, quests, and the
  STASH unit's vars. Item names come from RuneLite's item definitions.
- The card is made on the client thread from a player snapshot (`clue/ClueCard`) at most once per game tick,
  only after something it shows changed: the clue, the inventory, worn items, the bank, or one of the STASH
  vars (`clue/StashVars`: the step's own varbit, the six STASH varps, the clue helper's filled bit and clue).
  Shown with `BestPathPanel.showClueCard` (`ui/ClueStepCard`).
- Filled or not: the game tells it only for the clue its own clue helper is showing (docs/PLUGIN_CLUES.md); for
  other clues the card says "built, filled not known" (the mouse-over explains), and items count as not in the
  STASH unit (a route then fetches them from the bank if they are there).

## Tick changes on step cards

- Every card with a transport (the ones with Skip / Never use) has a small **-** and **+** at the right end
  of its button row, with the step's **total ticks right above them** (not broken down anywhere). Each click
  changes that transport's ticks for this player by 1 and the total at once. It can't go below 0 ticks ("-" is
  then greyed), nor above 10000 ("+" greyed; every tick setting runs from 0 to 10000). A click on the
  total opens a small box to type it: Enter (or clicking away) takes a whole number, kept between 0 and 10000;
  Escape, or anything that is not a whole number, keeps the old total. Back at the usual ticks the change is
  removed. The card's mouse-over says what - / + do. Cards
  without - / + (sailing steps, the bank card) keep their ticks in the detail line ("10 ticks"; no coordinates,
  see "No coordinates on the cards").
- Saved per account with the other Settings choices (`TransportSettings`, line `ticks=<id> <change> <name>`;
  saved only when the text differs). Quick clicks are sent together 0.7 s after the last one: one save,
  one quiet re-route (the old route stays shown until the new one is ready).
- Routes use the changed ticks: the player's transports are built with them (core `PlayerTransports`,
  `Transport.withTickChange`), so the search, its lower bounds and the route's total all use them.
- One change per transport: a two-way transport's way back shares it. A network ride's card (fairy ring,
  spirit tree, charter ship, house ride...) changes the whole network
  instead: the same number as the network's row in Settings, "Teleport and travel ticks", so every ride of
  the network moves together and the cards of other rides of it follow a click at once. The card shows the
  network's change; a ride changed on its own before this (saved per ride) keeps that change until reset in
  the Settings tab.
- The Settings tab lists the changes under **Your tick changes**, each with **Reset** (a transport the
  route no longer uses, e.g. after +20, has no card to change it back on). Each row shows the transport's
  total ticks ("Varrock Teleport: 7 ticks"), never the change; the teleport categories and networks changed
  are listed there too ("Spell (all): 2 ticks"). Until the map data has loaded, only the names.
- The route summary shows only the total: "4 steps, 235 ticks (2 min 21 s)".

## Teleport and travel ticks (Settings tab)

- Folded unless the player opens it: a click on its header ("Show" / "Hide" at the right)
  folds or unfolds it; remembered per account (`TransportSettings`, line `tickslist=open`) and saved without
  searching the route again (`PanelActions.settingsSaved`).
- At the end of the Settings tab (a long list): every teleport category, then every network ("Rides"), the
  same list as the Data Tool's "Teleport and travel costs ..." (`data/manual/teleport-categories.tsv`,
  `networks.tsv`, shipped in the export as they are). Each row: the icon of the Settings type row its
  transports are in (spells: the spell book; teleport items: the glory amulet; the house; rides: the
  transportation icon), its name, and its **total ticks above - / +**: a category "4 ticks", a network its
  rides' lowest to highest ticks ("5-20 ticks"; a ride's own time plus the network's extra). No breakdown.
- A click changes the whole category or network by 1 tick for this player at once on screen; "-" greys when
  all of them are at 0, "+" when one of them is at 10000. A click on the total lets the player type it (a
  network: its quickest ride's ticks; the others keep their difference). Quick clicks are sent together 0.7 s after the last one: one save, one quiet re-route,
  as on the cards. Saved per account (`TransportSettings`, line `groupticks=<key> <change>`, key
  `teleport:<category>` or `network:<network>`, lower case; saved only when the text differs).
- A transport's ticks in a route = its shipped ticks + its category's or network's change (which on its own
  never takes it below 0) + its own card change, never below 0 (plane changes still add their 2 ticks unless
  the card's change takes them off). Core: `TickGroups` (the list, and one transport's ticks for the
  Settings tab), `PlayerTransports.build(..., TickChanges)`; the search's lower bounds use `Transport.cost`,
  so they stay admissible (core test `CategoryTickChangeTest`, against the brute-force checker).
- The card of a transport whose category was changed shows the changed total; its own - / + count on top.
- Code: `ui/TickGroupList`, `ui/TickStepper` (the total above - / +, also on the cards); tests
  `TickGroupSettingsTest`, `CategoryTickChangesTest`, `TickCardsTest`.

## Marks on the needs and the bank card's box

Every need on EVERY card (items, runes, coins, levels, quests, the cooldown's "Ready") has a mark, even when all
are green; items in a built STASH unit get a check. When the bank card says to take out an item, a box shows it.

- **The marks**: a green check (the player has it now), the world map's bank symbol (not carried, but in the
  bank), a red X (neither). The check and the X are drawn as small shapes (no picture files; `ui/NeedMark`, 12 px,
  `Ui.GOOD` / `Ui.BAD`); the bank symbol is the same sprite as the bank card's (`SpriteID.Mapfunction.BANK`). The
  mouse-over says it in words: "Needs Agility 51: you have it", "...: in your bank", "...: you don't have it".
- **Worked out on the route worker** with the requirement model's own checks (`Requirement.check` per need, the
  same rules as the route search): worn or carried items, the rune pouch, combination runes and a staff (worn, or
  carried: wield it on the way) count; a members skill on a free account is an X; "every item" (the Data Tool's
  Maxed) is a check. Part of the immutable `StepInfo.Need` (`getStatus()`, in equals). Without a player
  (tests, previews) there is no mark. A route only uses what is met, so on a route an X shows only where a card
  needs something the route did not check (e.g. the sample route of `uiPreview`); the clue card shows X's often.
- **The step's own item** (an item teleport's: the card's big picture) has its mark on the picture's lower right
  corner, over a small dark disc (pictures `real-24-item-teleport-worn`, `real-25-item-teleport-banked`).
- **The cooldown's "Ready"** has a check (a route uses home and minigame teleports only while ready).
- **The bank card's box**: the card (yellow outline) has the title "Bank at <bank>" and a framed
  box lists everything to take out (no heading), one row each: the bank symbol, the item's full-size
  picture (36 x 32) (one of the group the player has in the bank), its name and how
  many ("Law rune x2", "20 coins", in the normal font). How many = what is needed minus what the player already carries. The name is
  the game's (RuneLite's item definition, read on the client thread and handed to the sidebar; until it arrives,
  or in tests, the data's words: "Law runes", "Item 563"). Core carries each item's ids and count through the
  route (`core/req/BankItem`, made by `Check` when a requirement is met only with the bank; `Route.Step.
  getTakeOutItems()`); the old words stay (`getTakeOut()`, the card's full title in `RouteStep.getTitle()`, the
  Data Tool's Route tab and route checks).
- **The clue card**: each item row and level / quest row has the same mark in front of its picture (worn,
  carried or in the built, filled STASH unit: check; bank: bank symbol; missing: X); the coloured lines stay.
- Pictures (`uiShot -PrealData`): `real-19-bank-first-full` (the bank box; the teleport card's runes with the bank
  symbol), `real-23-clue-card-marks-full` (a check, a bank symbol and an X on the clue card, the box with the
  apron), `real-21-home-teleport-ready` ("Ready" with a check), `3-route-8-steps` (the sample teleport: check,
  check, bank, X).

## No coordinates on the cards

The cards and the route header show no tile coordinates, because players don't understand them: a transport's card
has no "to 3212, 3424, 0" line and the bank card no "at x, y, plane" (cards without - / + show only their ticks);
the route header says "Nearest of 5 places.", "To your destination" while calculating, "You have arrived.", and the
clue header's "This step has a STASH unit (...)". Coordinates stay in the destination box and in logs and dev output.

## Card titles: the in-game menu text

A card's title is the exact text the in-game menu click would show ("Construct. cape -> Rimmington"), not a long
description.

- **Title** = the transport's menu text from the export (`transports.tsv` columns `menu` / `menu back`, core
  `MenuText`, docs/DATA_FORMATS.md "Menu texts"), the way the step uses it: "Construct. cape -> Rimmington",
  "Amulet of glory -> Edgeville", "Cast Varrock Teleport", "Climb-up Ladder" / "Climb-down Ladder" (a two-way
  transport: the direction used), "Spirit tree -> Battlefield of Khazard" and "Fairy ring -> BIS" (a network ride:
  the boarding stop's click with the landing stop's choice, core `TransportMenus.of`), "Achievement diary cape ->
  Ardougne". The game's own spelling ("Construct. cape", "Pay-toll(10gp) Gate"); item charge counts and trim marks
  are left out by the export ("(4)", "(t)"; "(i)" stays: a different item).
- **Unknown** (about 9 % of directions, e.g. custom transports, dialogue choices not in the cache): the old title,
  the transport's name, a teleport's with "Teleport: " when its name lacks the word.
- **Where**: route cards (`CoreRouteEngine.toStep`: transports and teleports) and the "no route" cards
  (`CoreRouteEngine.blocker`; a ride from its boarding stop, a two-way transport the way the route would go).
  Sailing steps have no menu text in the data. The shipwright card follows the menu text like the
  others: **"Recover-boat Shipwright Sam"** (the option and the NPC's name as the game shows them; cache 2735: the 16
  NPCs with "Recover-boat", the 15 "Shipwright ..." and Junior Jim, are named exactly as the docks' shipwrights).
  Boarding is one click the cache shows too: the dock's land object (a multiloc on varbit 19104) is "Gangplank"
  59832 or "Mooring point" 59834 with the first option "Board", so the card says **"Board Gangplank"** / **"Board
  Mooring point"** (the dock's kind from `sea.bin`). `CoreRouteEngine.sailingTitle`. The others keep their names:
  leaving ("Leave Boat 1: Sloop at Remote Island": the cache shows the buoy's "Dock" and the other form's
  "Disembark", but not whether leaving takes one or both, so not verifiable), sailing, a teleport focus bringing the
  boat, releasing it from its bottle. The "no route" cards' "Board at <dock>" / "Leave the boat at <dock>" also stay
  (they are about that dock's level and quest, so the dock's name matters there).
- **The arrow is "->"**, not "→": RuneLite's RuneScape fonts (small, normal, bold) have no glyph for U+2192; Java
  borrows one from a system font (font slot 1 of the composite font, checked by rendering), so it looks unlike the text and depends on the
  computer's fonts. "->" is in the RuneScape fonts, is what the game's own menus write ("Use Coins -> Bank booth").
  `CoreRouteEngine.ARROW`.
- **Wrapping**: an arrow stays with the word after it (`WrapLabel`): "Construct. cape" / "-> Rimmington".
- Pictures (`uiShot -PrealData`): `real-34-title-item-teleport` (construction cape worn, Lumbridge to Rimmington),
  `real-35-title-spirit-tree`, `real-36-title-fairy-ring`, `real-37-title-diary-cape`; the report lists each
  picture's card titles ("cards: ..."). Tests: `CardTitlesTest`, `WrapLabelTest`.

## Routes that begin at a bank

A route may BEGIN with a bank visit, never stops at a bank part-way (core `BankFirst`, the same in the Data Tool).

- When the player keeps something in the bank that opens a quicker way (runes for a teleport, coins for a boat,
  a charged jewellery item...), the route may start: walk to a bank, **Bank at <place>: take out X, Y**, then on.
  Every bank is compared exactly: one search gives the ticks to every bank, then one search
  starts from all of them at once, each with its own head start (the way there + the bank stop).
  It does so only when that is quicker than the route with what the player carries (a tie keeps the plain route),
  or when the destination needs something that is only in the bank (a clue's items: the clue card passes them,
  `PluginRouter.BankSetup.fromBank`).
- The bank card: the world map's bank symbol (`SpriteID.Mapfunction.BANK`), gold label "Bank", what to take out
  for the later steps and the destination (in a box with pictures, see "Marks on the needs and the
  bank card's box"); no Skip / Never use / - / + (it is not a transport). No "Bank" button.
- Settings tab, section **Bank**: a "Bank stop" row (the bank symbol) with its total ticks ("30 ticks") above **-** / **+**
  (0 to 10000, or click the total to type it), like the "Teleport and travel ticks" rows: the ticks a route counts for
  using the bank. Saved per account (line `bank=<ticks>`, only when not 30); clicks are sent together 0.6 s after
  the last one and the route is searched again.
- Settings tab, section **Sailing**: a "Board or leave a boat" row (a skiff) built the same way,
  10 ticks by default: what a route counts for boarding a boat at a dock, again for leaving it, and for releasing a
  boat from its bottle. Saved per account (line `board=<ticks>`, only when not 10).
- Settings tab, section **Avoid**: "The Wilderness (unless there's no other way)" (the skull), off by
  default, per account (`avoid=WILDERNESS`). Ticked, a route is first searched with the Wilderness's tiles closed and every
  transport into, out of or inside it off (core `PathQuery.Closed`, `WildernessZones.closed()`, `PluginRouter`); only when
  that finds no route is it searched again as usual. When the player stands in the Wilderness or the destination is in
  it, one search as usual. Ferox Enclave is not the Wilderness.
- Step cards in the Wilderness: a card whose start, transport tile, last tile or landing is in the
  Wilderness has "WILDERNESS!" in red first among its needs with the game's skull (sprite 1046), mouse-over "The
  Wilderness: other players can attack you here" (no level), and a red outline (over a bank's yellow). Walks have no card.
- A teleport the player is too deep in the Wilderness for (its limit 20 or 30, or 0: not in the Wilderness at all) is not
  used from the start: the normal search offers it from every tile it reaches that is shallow enough, walked to or by any
  transport (a lever, an obelisk...), and keeps the best (core `PathQuery.Limits`, `FastSearch.offerLimited`). The teleport's
  card is marked where it is used.
- The **boat card** (`BoatChoiceCard`, `route.BoatCard`): at the top of the Route tab,
  above the step cards, when the player is on their boat at sea. With the normal route it shows only when the route's
  first step leaves the boat at sea (a teleport, not sailing it): "This route leaves your boat at sea", the boat's
  picture, and two buttons. **Park my boat**: this route only (a new destination forgets it) sails the boat to a dock
  with a shipwright first, then goes on any way, as the Settings choice "Never leave my boat at sea" does for every
  route. **Ocean only path**: the player stays on the boat the whole way, through boat-only transports too, to the
  destination's open sea, else the nearest dock within 15 tiles (the boat stops there, no "Leave" step), else the
  nearest open sea within 15 tiles; greyed out, mouse-over "No dock or open sea within 15 tiles of the destination",
  when there is none. It stays on for new destinations while the player is on the boat; a destination that is not near
  the sea, or that the boat can't sail near, gets the normal route and the card says why. With either on, the card says
  which ("Parking your boat first", "Ocean only path is on") with **Normal route** / **Turn off ocean only**. Off the
  boat (teleported away, left it at a dock) both are over and the card is gone (the boats changing searches again).
  The choice is kept by `RouteService` beside the route's skipped transports (`BoatMode`). A blue outline (`Ui.OUTLINE_BOAT`).
- `real-19-bank-first(-full).png` (`uiShot -PrealData`): a player with runes, coins and jewellery in the bank, from
  Lumbridge to Varrock's square.
- A clue step whose items are only in the bank (outfit, spade...; not those in a filled STASH unit): the route always
  begins at a bank (never straight there), and its bank card lists those items after what the way on needs. Only
  when no bank can be reached first does it go straight there, with the header line "No bank can be reached first;
  only in the bank: ...". Pictures `real-14-clue-card-bank`, `real-20-clue-bank-first`.

## Home and minigame teleports: "Ready"

Home and minigame teleports are used only while they are available. The plugin leaves a home or minigame
teleport out of the route while its cooldown runs (30 / 20 minutes, read from the game: docs/PLUGIN_PLAYER_STATE.md,
"Home and minigame teleport cooldowns"), and searches the route again once, quietly, when it is ready again.

- Step card: a home or minigame teleport shows the stopwatch (`ItemID.GIANT_STOPWATCH`, the same picture as the
  Settings tab's "Long-cooldown teleports") with **Ready**; the mouse-over says "Home teleport: ready (it can't be
  used again for 30 minutes after)". No countdown: a route only uses one that is ready.
- Settings tab: nothing new. "Long-cooldown teleports" (avoid) still means "use them only when there is no route
  without them", now only while they are ready.
- Pictures (`uiShot -PrealData`): `real-21-home-teleport-ready` (the mid-level player from Fossil Island to the
  Varrock sewers: Lumbridge Home Teleport, "Ready"); `real-22-cooldowns-used` (the same player having just used
  both teleports: the route goes without them). The uiPreview window's player list has the player "Mid-level, home
  and minigame teleports used".

## Player answers: Yes / No on the card, "Your answers" in Settings

The first time a route rests on a player-answered option, a Yes / No question appears under the card. Rules in
docs/PLUGIN_PLAYER_STATE.md, "Player answers".

- **On the card**: a step whose route rests on answers not given yet has a strip under the card (a thin line, then
  each question in white with **Yes** and **No** under it), as soon as the route is shown: "Have you planted a spirit
  tree at Port Sarim?". A spirit tree ride asks about both trees. A click greys both buttons and says "Saved: yes.
  Updating the route..." (or "Saved: no. Finding a way without it..."); the same question on another card is greyed
  too; the route is searched again quietly and its new cards no longer ask (Yes) or go another way (No).
- **The answer as a need** (with the other needs, no picture): its words ("Spirit tree planted at Port Sarim";
  for a "not answer": "Respawn point not set to Prifddinas"), with a check when answered as needed, an X when
  answered the other way, no mark while not answered (the question is under the card).
- **Settings tab, "Your answers"** (after "Your tick changes"): a folding section like "Teleport and travel ticks"
  (click the title; "Show" / "Hide"; the title counts them, "Your answers (2)"). One row per answer given: the
  question, then a green "Yes" or red "No", **Change** (to the other answer) and **Clear** (forget it: the card asks
  again). The list is sorted by question. Starts folded (not saved: the ticks list's fold is saved in the
  settings, this one is not, to keep the settings format unchanged).
- **Not answered yet** (they can be answered in advance): under the answers, "Not answered yet (25)" and
  one row per question the map data asks that has no answer: the question, "Not answered", **Yes** and **No**
  (the same as Yes / No under a card: saved, the route follows). The title then counts "Your answers (2 of 27)".
  The questions are every `answer ...` in the data's requirements (`AnswerQuestions.answersIn`, made when the map
  data loads, on the route worker; the same words in another case count once). Answers are never asked again or
  expired.
- **Questions**: written by hand in `route/AnswerQuestions` for all 27 answers in the export (a test fails when a
  new answer appears without one); an unknown answer would be asked as "<words>: is that so for you?".
- **STASH put-back card**: only "Skip" (it ends that reminder); no "Never
  use" and no - / + (it is not a transport).
- Pictures (`uiShot -PrealData`): `real-26-answer-question` (Port Sarim to Brimhaven's spirit tree, a member with
  Tree Gnome Village done and no answers: the ride's card asks about both trees), `real-27-answer-after-yes` (after
  Yes to Port Sarim: a check on that need, only Brimhaven asked), `real-28-answers-settings-full` (the Settings tab,
  "Your answers" unfolded with that Yes and a No).

## "No route": why

When a destination has no route, the sidebar shows why the player can't get there: short reasons, and a card for
each thing the way would need, with a check, bank symbol or X on each of its requirements. The way explained is the
one needing the fewest things the player can't use yet (counting requirements, not transports); boats and sailing
items are included. No coordinates are shown.

**What the player sees** under the red "No route": "Calculated in 420 ms" (the search and the explanation together,
as for a found route), then the main reason in plain words, then cards (pictures `real-26` .. `real-33`). While the
reason is worked out
the status reads "No route, finding out why..." with the engine's own line under it; the config
item "No-route explanations" (on by default) switches the reasons off: then only that line.

| Reason (core kind) | Text | Cards |
|---|---|---|
| `MEMBERS_LAND` | "This is members-only land, and you're on a free-to-play world." | none |
| `NOT_STANDABLE` | "Nobody can stand there (water, a wall...)." | none |
| `BLOCKED` | "The way there needs 2 things you can't use yet:" | one per transport the player can't use |
| `BY_BOAT` | "The way there is by boat, and needs 1 thing you can't use yet:" (open sea: "That's open sea, so the way there is by boat. It needs ...") | the boat and / or the docks |
| `NO_KNOWN_WAY` | "There's no known way there in our map data." (free world: "... on free-to-play land ...") | none |
| `REACHABLE` | "A way there exists with what you can use now. Try searching again." (should not happen) | none |

Several places (a clue's): "The way to the nearest place needs ...", "Nobody can stand at any of the 4 places ...".
Core's extra lines follow when they apply ("2 places are on members land.", "Looked at the nearest 4 of 7 places.").

**The cards** (`ui/BlockedCard`) look like route cards: the same picture, title and needs row, each need with its
mark (green check / bank symbol / red X, `NeedMark`), a red outline; no Skip, Never use, - / + or ticks.
Usable steps of the way are not shown. Under the needs:
- needs without a small picture (a members world, a spellbook, quest progress varbits, an answer) as a line with a
  red X ("A members world");
- why else, in words: "Turned off in Settings: Shortcuts." with **that type's Settings tick box** under it (an on/off toggle like the
  Settings tab's tick box: it shows the current state, and clicking flips it, saves it and re-routes; the same row
  as in the Settings tab: box, picture, label; it reads the settings now, a click calls
  the same `withCategory` the Settings tab does, which then shows it too; shown when the whole type is switched off;
  pictures `real-38-no-route-turn-on`, `real-39-after-turn-on`), "You chose never to use this." with a **Restore** button
  (takes it off the never-use list, like the Settings tab's Restore, and searches again), "You skipped this for this
  route (Undo above allows it again).", "Cooling down: ready again in about 12 min.", "Needs things from your bank";
- a "one of (...)" none of whose choices is met shows the choice nearest to being met;
- by boat: "A boat" (the raft's picture) "You have no boat."; "Boat 1: Sloop" "Turned off in Settings (your
  boats)." (with the boat's tick box, `withBoatAllowed`) / "A shipwright must bring it, and shipwrights are turned off
  in Settings." (with the "A shipwright may bring my boat (costs coins)" tick box, `withShipwrights`; both when both
  are off; pictures `real-40` .. `real-42`) / "It is in a boat bottle:
  carry the full bottle in your inventory." (with the bottle's X); "Board at Port Khazard" / "Leave the boat at
  Dognose Island" with the dock's Sailing level and quest marked.

**How** (route worker, inside the timed engine call): `CoreRouteEngine`'s NO_ROUTE branch calls
`PluginRouter.explainNoRoute(player, start, targets, job::isExcluded, the filter of the last search made (the loose
one when avoiding), the player's boats with the Settings choices)`, then `why(...)` turns core's `NoRouteReasons`
into an immutable `route/NoRouteWhy` (headline, lines, `Blocker` cards: a `RouteStep` with `StepInfo` marks, the
lines with an X, the reasons, the id to restore), carried by `RouteResult.noRoute(...)` / `getNoRouteWhy()` (kept by
`withSearchMillis`). If the explanation throws, the old one-line message is shown.

**Core** (`core/.../data/NoRouteReasons`, `SeaWay`):
- `PluginRouter.explainNoRoute(player, sx, sy, sp, targets, excluded, filter[, boats])`: kinds as above,
  `getReasons()` (core's own short lines), `getThings()` (distinct things), `getSteps()` / `getBlocked()` (`WayStep`:
  transport, line, `getRequirement()`, `getWhy()` REQUIREMENTS / BANK / FILTER / EXCLUDED / COOLDOWN, `getUnmet()`,
  `getInBank()`, cooldown), `getSailSteps()` (`SailStep`: BOAT / BOARD / LEAVE, the boat, the dock, `getWhy()`
  NO_BOAT / BOAT_OFF / SHIPWRIGHTS_OFF / BOTTLE / REQUIREMENTS, `getRequirement()`, `getUnmet()`).
- Fewest things: a thing is a requirement leaf not met (by its words: "Agility 30" of two gates is one thing), one
  met only from the bank, a cooldown, or one transport turned off / skipped / blacklisted. Up to 3 searches per place,
  each counting its way's things exactly, the best kept: (1) 10,000 extra ticks per thing of each blocked transport;
  (2) a thing many blocked transports share costs less in each (10,000 / how many need it); (3) the best way's things
  free, every other thing 10,000. A heuristic: among ways with the same count the quicker is not always found.
- By boat (only when there is no way on land, or the place is open sea; members; sea data): with the land transports
  usable now, at most two sailing searches (the route search's own `Sailing` rules): the player's boats with every
  boat and shipwrights allowed and the player's own level, quests and bottles; then also every dock's level and quest
  met and the bottles carried; with no boat at all, one search with a made-up lost raft a shipwright brings. The
  first way found that needs something is explained (not a search for the fewest things over every boat and dock).
- Check on the real data: `gradlew noRouteCheck [-Proutes=100]`.

## Step cards: no walks, pictures

- **Walks have no card.** The cards are numbered 1..n; the summary keeps the route's total ticks ("4 steps,
  235 ticks (2 min 21 s)"; only the total, see "Tick changes on step cards"). A route that is
  only walking shows one line, "Walk there (35 tiles)". The overlays still draw the whole path.
- **Following a route:** while the player walks to a transport (the walk has no card), that transport's
  card is framed (white, or its own outline colour) with "Next, after walking N tiles" (N = the walk's length: the sidebar hears of
  progress once per step, not per tile). At the transport it is the current step (yellow number).
- **Pictures** (code `ui/IconChooser`, tests `IconChooserTest`): only RuneLite's public API:
  `ItemManager.getImage` (items), `SkillIconManager` (skills), `SpriteManager` with named constants of
  `net.runelite.api.gameval.SpriteID` (each one checked in RuneLite's source and drawn from the game cache:
  `build/preview-icons/sprites-sheet.png` after `gradlew previewIcons`). Pictures load in the background and
  fill in when they arrive; each is asked for once and kept (`ui/IconCache`).

| Step | Big picture on the card |
|---|---|
| Spell or home teleport | the spell's icon (by name); Battlefront and the Ancient, Lunar and Arceuus home teleports: the Magic skill icon (RuneLite names no sprite for them) |
| Teleport item (tablet, scroll, jewellery, cape, other item) | the item (the first item its requirement names; for an item group: the one the player carries, else one in the bank, else the default item for the group, `data/manual/item-group-defaults.tsv`, exported as `item-group-defaults.tsv`) |
| Minigame teleport | the world map's minigame icon |
| Anything at or to the house | the world map's house portal icon (Teleport to House keeps its spell icon) |
| Transport needing a skill level (agility shortcuts, a cave needing Farming 65...) | the skill's icon |
| Boats, fairy rings, carts, any network ride | the world map's transportation icon |
| Shortcut without a level | the world map's agility shortcut icon |
| Ladders and stairs | the world map's up / down link icon (by the plane change, else the data's CLIMB_UP / CLIMB_DOWN, swapped when used the other way) |
| Cave entrances / exits (ENTER, EXIT) | the world map's dungeon icon |
| Gates and barriers that need something (PASS; their label is "Door") | what they need (quest icon, coins, item) |
| Sailing steps: Board, Sail, Leave, a shipwright or teleport focus bringing the boat, releasing it from its bottle, a greater-focus teleport to it | the player's boat's picture, the same as its Settings row (`SpriteID.IconSailingBoats35x35`, by kind) |
| Doors without data, CUSTOM transports | the coloured kind label as before |

Under the title, **what the step needs** as small icons: skill icon + "Agility 51", rune + "x3", the
quest icon + the quest's name, coins + amount, items (+ "x2"; an item group's picture is chosen as for a
teleport item above). The mouse-over says each in words. Members, spellbook and varbits have no icon.
Each one has its **mark** in front (see "Marks on the needs" below).

**"one of (A | B)"**: only the choice that applies to the player is shown, with the same
icons and text as other needs; its mouse-over adds every choice ("10 coins (one of: 10 coins or Quest: Prince
Ali Rescue)"). The choice is the one the route search used: the first alternative the player meets, preferring
met now over met with an item from the bank (`Requirement.AnyOf.chosen`, the same rule as its `check`). If
none is met (the route would not use the transport) nothing is shown; without a player (tests, previews) the
first choice. Example: the Al Kharid toll gate shows coins for a player with 10 coins, else Prince Ali Rescue
for a player who has done it.

### Pictures in the preview (`gradlew previewIcons`)

The preview has no game client, so the Data Tool draws the item pictures and sprites from the game cache
once (dev only, nothing goes into the plugin; about 5 s plus Gradle):

```
./gradlew previewIcons                      # cache/openrs2-2735; or -Pcache=<cache folder>
./gradlew uiShot -PrealData
```

It lists every item the exported transports can need plus every sprite above (`previewIconList`), then
runs the Data Tool's `previewIcons` (the Item groups window's item drawing, and sprites by id) into
`build/preview-icons/`. It also lists each clue step's scroll and the first choice of every
item its needs name, the emote icons, and writes the items' names (`item-names.tsv`: the clue card names its
items; the plugin asks RuneLite). Skill icons come from RuneLite's own files. Without that folder the preview shows
grey "?" squares for items and sprites (the preview only; the plugin itself always uses RuneLite).

## What the plugin itself does today

- Config panel (RuneLite settings; laid out as: Show sidebar, Route-only sidebar, Minimal mode at the top; sections
  Routes, World map and right-click, Graphics, Transport types, Avoid, Bank, Sailing, Advanced; the Settings tab
  shows the same): show sidebar, sidebar only with a route, automatic clue routes, "Routes from other plugins" (on;
  see "Functions tab" below), "Clear route on arrival" (on; off keeps an arrived route shown), "Show route on
  world map" (on), "Right-click world map" (on) and "Right-click game tiles" (off); see "World map" below; "Remind
  to put STASH items back" (on; see docs/PLUGIN_CLUES.md "Put the items back"); "Re-route when off by (tiles)"
  (10, 1-50) and "Arrived within (steps)" (10, 0-20; steps walked to the destination or the route's last tile, never through
  a wall; 0 = only those tiles).
- World map: the route drawn on it, right-click "Set destination" / "Clear destination" (see below).
- Sidebar Route tab and Settings tab as in the pictures. Settings tab choices are saved per account
  (RuneLite profile) and routes use them (see "Settings tab: what each choice uses" below). A change
  searches the current route again at once, quietly (the old route stays shown until the new one is
  ready), the same way a change of the player's items or levels does.
- Player state and route progress (see `docs/PLUGIN_PLAYER_STATE.md`): the plugin reads the player
  from game events (levels, quests, items, bank, rune pouch, spellbook, house...), follows the
  player along a found route (current step highlighted in the sidebar, on the ground and on the
  minimap), searches again when the player wanders off or something that matters changes, and
  clears the route on arrival.
- Routes are real when the jar has the map data (`./gradlew -p tool exportPlugin` writes it to
  `src/main/resources/com/bestpath/data/`, git-ignored for now; then `./gradlew run` or a jar build
  includes it). It loads on the route worker about a second after the plugin starts. Without the files
  every route request answers **"No map data yet"**. Routes may sail with the player's own boats (read from the
  game, docs/SAILING_SPEC.md "Sailing in the plugin, part 2"); the Settings tab chooses which.
- Clues (see `docs/PLUGIN_CLUES.md`): the plugin notices a clue scroll (or a strange device) in the
  inventory and, for beginner and master clues, reads the scroll's text when the player reads it; then
  "Use clue" routes to the step's place (the nearest one when there are several). With the config
  setting "Automatic clue routes" on, a newly detected clue becomes the destination by itself.

## Functions tab

The sidebar has three tabs:
Route, **Functions**, Settings. A click on a function starts the route and shows the Route tab.

The tab opens on the **function select screen**: a titled card per function (`FunctionCard`) with the title, a grey summary line ("3 of 10 saved", "Kalphites: 87
left (from Vannaka).", "Charted 1 of 358 tasks...") and an arrow on the right. A click on a card opens that function's
**own page**, with "< Back" and the title at the top to get back to the function select screen (the cards are not dropdowns). A destination clicked on a page
starts the route and shows the Route tab; the page stays open for when the player comes back. Top to bottom: Saved
locations, Recent destinations, Nearest, Quest Helper, Slayer task, Sea charting.

- **Saved locations** (`SavedLocations`, `SavedSection`): up to 10 named places per account
  (RuneLite profile key `savedLocations`), in the order saved. Each is a button (a click routes there; the recent
  destinations keep it under its name) with an x to remove it. Below: Name and Tile boxes and Save. An empty tile
  saves where the player stands (logged out: "Log in to save where you stand, or type a tile."); a typed tile is read
  like the Route tab's ("x, y" or "x, y, plane"). Saving a tile already in the list renames it, also when the list is
  full; a new tile with 10 saved says to remove one first. An empty name becomes the tile; names are cut at 40
  letters.

- **Quest Helper**: Quest Helper, with its own setting "Use 'Shortest Path' plugin" turned on, sends each quest
  step's place to the Shortest Path plugin as a RuneLite plugin message (namespace `shortestpath`, `path` with a
  `target` tile when a step starts, `clear` when it ends). Best Path listens to the same message
  (`QuestHelperPath`). It keeps the step for "Go to the quest step" here and the Route tab's "Quest" button (both
  greyed out until a step arrives). With the config item **Routes from other plugins** on (the default;
  key `allowPluginRoutes`, Routes and world map, mirrored in the Settings tab) each new step becomes the route, and
  the step's end clears that route (only a Quest Helper route). The same step sent again does nothing. If the
  Shortest Path plugin is installed too, it draws its own path as well. Another plugin sending the same message is
  treated the same way.
- **Routes from other plugins** (the public API, `docs/API.md`): other plugins send the same kind of
  messages to namespace `bestpath` and hear back what happened to their route. Their route's header is "Destination
  from" the plugin's name (or the title it sends); it replaces the route shown like any destination, and isn't kept in
  Recent destinations. Config item **Routes from other plugins** (on; key `allowPluginRoutes`, Routes and world map,
  under Automatic Slayer routes, mirrored in the Settings tab): off, they can't show or clear routes (they can still
  ask a `query`).
- **Nearest** (`NearestSection`): a drop-down of place kinds, each with its map icon
  (bank, altar, anvil, furnace, cooking range, ... 70 kinds; the Bank picked at first), and **Go**: the route goes to
  whichever place of that kind is quickest to reach (`RouteRequest.places("nearest altar", "Nearest altar", ...)`
  with every place of the kind; the route card says "Nearest of 79 places"). The places are the game's map icons
  (plugin data `places.tsv`; the banks from `banks.tsv`, only those the player may use now). The route ends beside
  the place when no one can stand on it (a furnace). Until the map data has loaded the drop-down is greyed out
  ("Loading the map data..."). Every kind found is offered. Nearest
  routes are kept in the recent destinations. UI pictures `9e-functions-nearest.png`, `9e-nearest-kinds.png`
  (every kind with its icon and number of places) and, with the exported data, `9e-nearest-anvil-route.png`.
- **Recent destinations**: the last 10 places routed to, newest first, saved per account (RuneLite profile key
  `recentDestinations`): typed tiles, world map and game tile right-clicks, Quest Helper steps and recent ones
  clicked again (they move to the top). Clue routes are not kept ("Use clue" covers them). Each has an x to take it
  off the list, and "Clear the list" below.
- **Slayer task** (`SlayerSection`, `route/SlayerTask`, `route/SlayerTimes`, `player/SlayerWatch`). Every slayer
  master is covered, and the page lets the player compare places to kill the task's monsters at (some places have
  advantages). The task is read
  from the game's vars on the tick after one of them changed (varp 395 the task, 394 how many are left, 2096 Konar's
  area; varbit 4723 the boss of a boss task, 4067 the master), and at login. At the top the task in its own card with
  a yellow edge: the RuneLite Slayer plugin's picture for the task (32 px), the name in yellow, "87 left", "From Konar" and
  a **Wiki** button (the task's Wiki page, "Slayer task/Kalphites"). Then **Go to the nearest place** (every place
  where kills count; the search picks the cheapest) and **Update times**, then one short card per place (`slayer.tsv`,
  one entry per group of monsters): its name with
  **Wiki** (the place's page: its name before any ":") and **Go**, then
  - a mark: "Konar's area"; "Doesn't count for this task"; "Not in the map data yet: no route there";
  - **Travel: 236 ticks (2 min 22 s)** (yellow-orange), "No route from here", "Travel time: working it out..." or "not worked
    out (further away)";
  - a line each: the task's picture and "43 monsters"; a green check or a red cross before
    "Multi-combat", "Cannon" and "Safespot" (a question mark for the Wiki's other words, e.g. "Cannon: some spots";
    facts the Wiki doesn't give are left out); "Wilderness" in red;
  - **Special loot:** the items' pictures, their names on hover, and a note ("Ghosts don't drop the totem pieces."):
    ancient shards and dark totem pieces in the Catacombs of Kourend, the brimstone key on Konar's tasks, Larran's
    key on Krystilia's (`data/manual/slayer-loot.tsv`, from the Wiki). Each place also shows the collection log items its own monsters drop that not every place of the
    task has (only the differences; long / curved bones and champion scrolls are left out; for example some
    spiritual creatures drop dragon boots and some do not), one group per monster with its note ("From Spiritual mage." under the dragon boots on the spiritual mage places;
    "From Vyrewatch Sentinel." under the blood shard in Darkmeyer; "From Basilisk Knight." under the basilisk jaw);
    bosses' drops never (not on a boss task's card, not from a boss among a task's monsters); pictures wrap to the
    card's width;
  - the Wiki's notes, one line per point, without links; hovering
    shows the monsters there.
  A Wiki button opens the page in the player's own browser (RuneLite's `LinkBrowser`); the plugin reads nothing from
  the Wiki.
  Order: Konar's area, the other places where kills count, those that don't; in each the
  places a route can reach, fastest first, then those without a route, then those not timed, then those outside the
  map data. The travel times: when a task shows, the route worker searches each place a route can reach (at most 12,
  the nearest as the crow flies first; abyssal demons from Varrock: 5 places in about 240 ms), from where the player
  stood; a kill doesn't time them again, **Update times** does (from where the player stands now). The current route
  is not touched. Konar: only her area counts. Krystilia: only Wilderness places count, and a place partly in the
  Wilderness leads only to its Wilderness part (Go, travel time, nearest). On their tasks the places where kills don't
  count are not shown at all; with none left the page says "No place known for this task where kills count."
  (master by varbit 4067; the Wiki's list of assignments: "Tasks assigned by Konar or Krystilia must be killed in their
  assigned locations"). A task the data has no place for says so ("it may be fought in an instance"). Logged out:
  "Log in to see your Slayer task."; no task: "No Slayer task. A Slayer master gives you one."
  **Stored task**: under the task's card (or the "No Slayer task" line), a grey-edged card: "Stored task",
  the task's picture, its name, how many were left, "From <master>", "Stored tasks are not routed to." and a Wiki
  button; never a Go, and the nearest place's Go ignores it. The Functions tab's card adds "Stored: Ankou.". The
  game's vars name only the stored task's master and boss (varbits 12443 `SLAYER_MASTER_STORED`, 12444
  `SLAYER_TARGET_BOSSID_STORED`, on varp 3113 `SLAYER_STORED_VARP`); no public var and no game script in the cache
  holds its monster or count (checked). So `SlayerWatch` remembers the task the player had when the stored
  master appeared or changed (also when it shows a tick before the task leaves), or the task that was there when it
  changed straight to another with kills left from the stored master (a swap; with both from the same master the
  stored master doesn't change), saved per account (profile key
  `slayerStored`, "task,boss,count,master"); storage empty forgets it, and so does a stored master or boss that no
  longer fits. A task stored while the plugin was off: "A task is in storage", the master, and "BestPath sees which
  task it is when you put one in storage while the plugin is on."; a stored boss task names its boss.
  **Automatic Slayer routes** (config item in Routes and world map, under Automatic clue routes, and the same in the
  sidebar Settings tab; off by default): when a task is just given (after no task,
  or another task: a swap with storage, a Turael skip), a route starts at once to the nearest place where kills count,
  as **Go to the nearest place** would without the filters. Not after a login, the plugin starting or the map data
  arriving, never for a kill (the same monster with as many or fewer left is a kill, whatever else the vars say; the same rule keeps the
  sidebar from opening), never to the stored task. It replaces the route shown.
  **Filters**: above the places, "Only multi-combat places", "Only places for a cannon", "Only places with a
  safespot". Each ticked box keeps only the places where the Wiki's comparison says yes or a partial answer ("some
  spots"); a no or not known leaves the place out. "Showing 1 of 3 places." (or "No place has everything ticked.");
  **Go to the nearest place** picks among the places shown. The ticks stay for other tasks until RuneLite closes.
  **Preferred places** (`route/SlayerPreferred`): a star button left of each place's Wiki
  marks the place preferred for this task (a filled yellow star; click again to unmark). Per task: preferring the
  Stronghold Slayer Cave for Hellhounds leaves it unmarked for other monsters there. Saved per account (profile key
  `slayerPreferred`, one line per mark "task TAB place name@first point"), so it stays after logging out and in.
  Preferred places go to the top of the list (then the usual order) with "Preferred" on the card, and a yellow line
  under the filters says "Go to the nearest place and Automatic Slayer routes pick among your preferred place" (or
  "your 2 preferred places"). **Go to the nearest place** and **Automatic Slayer routes** then go only to the nearest
  preferred place among those shown ("Slayer: Kalphites (nearest preferred place)", note "Picked among your preferred
  places."). They fall back to all places when none is preferred, when the filters hide every preferred place, when
  the travel times say no route reaches any of them, or when the route search finds no way to one (the plugin then
  searches all places at once; that route's note says "No route reaches your preferred places from here: the nearest
  of all places."). A place's own **Go** and **Update times** are unchanged; preferred places are timed first, so
  they get a time even when more than 12 places are nearer. The sidebar Settings tab lists every mark under
  **Preferred Slayer places** ("Kalphites: Kalphite Lair"), each with **Unmark** (a list, so sidebar only, like the
  blacklist).

- **Sea charting** (`ChartingSection`): "Charted N of 358 tasks. Sailing level L." Then three routes:
  - **Chart every sea I can complete (N)**: every sea and bonus set with a task left whose tasks left can all be done
    now (Sailing level, the tool carried or banked, a known place);
  - **Chart every ocean I can complete (N)**: the same for oceans;
  - **Chart everything**: every task left, only when all of them can be done now (greyed with the reason otherwise).
  Each tooltip says what it covers ("2 seas: 3 tasks left, 195 XP, and 700 XP for completing them."). Below, the list
  holds **All oceans**, **Everything** and each ocean (Everything lists every sea and bonus set under its ocean's
  name and charts the same tasks as All oceans; the bonus sets' ocean shows as "Miscellaneous (bonus sets)"), right
  under it **Chart all oceans** / **Chart this ocean**, the rewards in orange, and the boxes **Only what I can do
  now** and **Hide completed** (done tasks and completed seas / oceans left out). Only what I can do now shows only
  the seas / oceans the player can finish now, with their tasks left (those only partly finishable are hidden). The
  XP in the rewards line counts only what can be done: the tasks the player can do now (or whose drink they hold),
  and a completion bonus only for a sea / ocean they can complete ("1 of 4 done. 2 of the 3 tasks left can be done
  now: 70 XP. The ocean can't be completed yet."); logged out, everything counts. Then, for All oceans, a row per
  ocean (a click picks it in the list); for one ocean, its seas and bonus sets; each row with "done / all" and its
  own **Go** (each category has a Go; individual tasks do not). A click on a sea opens its tasks ("Find: ... (35
  XP)", with the Sailing level when too low, the tool, the hazard and, when none of the player's boats may enter it,
  what the boat needs: "Tangled kelp: your boat needs an adamant helm or better"; "Done" when done; Quest Helper's
  tip as the tooltip). Red: a task the player can't do now (Sailing level, a hazard none of their boats may enter,
  or no known place) and a sea / ocean with such a task left; green: done / complete. Hazards: Stormy seas = Tempor
  Tantrum (an oak mast or better), Fetid waters = fetid water (an inoculation station), Crystal-flecked waters =
  crystalline waters (an adamant keel or better), Tangled kelp = the kelp area (an adamant helm or better), Icy seas =
  the northern waters (an Eternal brazier); one boat with the part is enough; boats not read yet: every hazard counts
  as passable. The tools are taken as owned when the player has the relevant quests and levels: no bank stop for
  them and they don't make a task red. Drink crates: the player just needs to get the drinks. Once the crate's drink
  is in the inventory, worn or in the bank, routes skip that crate (on to the next task; a route with only drinks
  left ends) and never get stuck telling the player to drink, the task row says "Drink it" in orange (not red; the
  drink's name on hover), a sea with only drinks left still counts as completable, and a grey line under the
  progress lists them: "Drinks to drink when you like (each finishes its task): Bottle of almost smuggled rum, ...
  (in your bank)." Drinking it marks the task done.
  A set's route does its tasks in a planned order: planned once from where the player is
  (`core/.../data/ChartingOrder`, on the route worker, a fraction of a second; until it is back the route
  goes to the quickest task), then task by task; once the game says a task is done the route goes on to the next in
  the order, and it ends when none is left. A task that becomes doable later (a level gained) has the order planned
  again. The route's notes say what to do there and how many tasks are left ("Next of the 12 tasks left, in the
  planned order."). Weather tasks are done all together, straight back to the meteorologist: the meteorologist, with
  the empty weather station the measuring spot, with the full one straight back.
  Current ducks (the duck leaves the inventory at the start and enters again at the end): the start, and once the
  duck has left the inventory there (within 40 tiles) the pick-up spot; the next task is planned from the pick-up spot.
  Duck tasks need the quest Current Affairs finished: until then
  they are red with ", needs the quest Current Affairs" and set routes leave them out. Each task is done from a
  boat or on land (see docs/DATA_FORMATS.md "Sea charting boat / land spots"): a boat spot's place is
  the nearest open sea, so the route ends on the player's boat there; a land spot's route walks to the place.

## World map: the route and right-click destinations

Code: `overlay/WorldMapPath`, `WorldMapPainter`,
`WorldMapRouteOverlay`, `MapView`; `menu/MenuChoice`, `DestinationMenu`.

**On the world map** (only while it is open, and only while the config box **Show route on world map** is ticked;
it is on by default, and unticked the overlay draws nothing):

| What | Looks like |
|---|---|
| Walking | solid line along the tiles |
| Sailing leg | dashed line along the sea tiles |
| Boat or other ride (charter ship, cart, fairy ring...) | dashed straight line from where it starts to where it lands, orange square at the start |
| Ladder, stairs, door, shortcut | orange square where it is used; a dotted line to where it lands only if that is within 16 tiles (also up or down a plane; none when it lands on the very same spot, like most ladders) |
| Teleport | no line at all; a small ring where it lands |
| Destination | red ring with a dot, always full strength (whatever its plane) |

Colours are the scene and minimap ones: the route ahead cyan, the current step yellow (a walk turns grey up to
the player's tile and yellow from there), steps already done grey. A ride whose start or end is not on the map
being shown (a dungeon, Zanaris...) gets no line, so nothing runs off into the black.

Planes: the world map shows the ground floor and is flat, so **every** line and mark of the
route is drawn at its x / y, whatever its plane; nothing is hidden. Lines and marks on another plane than the one
the player is on are drawn a little fainter (55 % opacity) and underneath, so a walk upstairs over a walk
downstairs stays readable; they keep their done / current / ahead colours.

Speed: the lines are built once when a route is published, on the route worker: each walk is cut down to its
corners, and simplified copies are kept for zooming out (an error of at most 1 pixel). Drawing only picks the copy
for the zoom, skips lines outside the visible part, transforms the points and draws.

**Right-click menu** (two config panel boxes, "Right-click world map" on and "Right-click game tiles" off):

- On the world map: **Set destination** (the tile under the cursor; the target text shows it, e.g.
  `3210, 3424, 0`) and, while a route is active, **Clear destination**. The map gives no plane, so the route
  search picks it: plane 0 when plane 0 has ground (a tile someone can stand on) within 10 tiles (the ground floor
  the map shows, whatever plane the player is on); otherwise the plane whose ground
  is nearest (the lowest on a tie), e.g. the Sisterhood Sanctuary under Slepe on plane 1. Until the route is found
  the sidebar shows the tile without a plane (`3738, 9709`), then with the plane picked.
- In the game (off by default): **Set destination** just below **Walk here** for the right-clicked tile, on that
  tile's own plane.
- The entries are RuneLite-only: clicking one sends nothing to the game. It only asks for a route (the search runs
  on the route worker), exactly as typing the tile in the sidebar and pressing Go. The sidebar's destination box
  then shows the tile.

**Pictures without the game:** `./gradlew worldMapShot` saves `build/world-map-shots/wm-*.png` (sample routes over a
plain grid; no map pictures): route ahead, the sewers part, progress (done grey / current yellow), a boat + shortcut
+ sailing leg, zoomed out, the player on another plane (route fainter, not hidden), and a route up two planes
inside a building seen from plane 0 and from plane 1 (`wm-7`, `wm-8`).

**Try it in the game** (`./gradlew run`, log in with the Jagex-account steps):
1. Open the world map, right-click a spot in Varrock: **Set destination** with the tile. Click it: the sidebar's
   destination box shows the tile, "Calculating..." then the route; the map shows the line.
2. Walk a few steps: the start of the line turns grey and the current walk yellow. Use a teleport on the route:
   no line joins the two places.
3. Drag and zoom the map out and in: the line stays on the roads; it is never drawn over the overview box (bottom
   left) or the map list, and it disappears when the map is closed.
4. Right-click the map again: **Clear destination** clears the route.
5. Config panel: switch on **Right-click game tiles**, right-click the ground: **Set destination** under **Walk
   here** (the left-click stays Walk here). Switch **Right-click world map** off: the map menu has no Best Path entries.
6. Go upstairs (plane 1) with a route on plane 0 and open the map: the plane-0 lines are still there, a little
   fainter. Right-click the map there: the target text ends in `, 0` (ground floor).
7. Config panel: untick **Show route on world map**: the map shows no Best Path lines or rings (the right-click
   entries still work); tick it again: they come back at once.

### Choices

1. Plane: map clicks ask for plane 0 (the ground floor the map shows); every plane of the route is drawn (other
   planes than the player's slightly fainter, never hidden); the destination ring is always full strength.
2. Both right-click boxes exist (world map on, game tiles off). Map entries stay at the top of the open menu.
3. The "Show route on world map" box is on by default.

## Settings tab: what each choice uses

**Open the sidebar**: a folded section right under the sidebar switches, in the config panel and
the Settings tab, three switches, each on by default:
- **New clue route**: a clue's route starts by itself (Automatic clue routes): the sidebar opens on the Route tab.
  Not for the "Use clue" button (the sidebar is already open then).
- **New Slayer task**: a task just given (`SlayerTask.isNewTask`: after no task or another task; not after a login):
  the sidebar opens on the Functions tab's Slayer page, whether or not Automatic Slayer routes is on.
- **Right-click destination**: "Set destination" on the world map or a game tile: the Route tab.
The sidebar opens only where its button is shown: never with "Show sidebar" off or in minimal mode; with "Route-only
sidebar" the button shows once the route is set (a new Slayer task without a route opens nothing). RuneLite's
`ClientToolbar.openPanel` does it (Swing thread). Picture: `9c-settings-config`.

**Transport ticks**: search, filter (Show / Type / Tag) and sort (recently
changed, name, ticks, biggest change, nearest to me) every transport and change its ticks with - / +. Every section heading folds with an arrow on its left
(`FoldArrow`, no Show / Hide). Pictures: `real-70-transport-ticks-recent`, `real-71-transport-ticks-glory`,
`real-72-transport-ticks-nearest-ladders`; the Wilderness ditch card: `real-47-wilderness-ditch`.

**The config panel's settings** (the two places mirror each other, and a setting changed in one carries over to the
other): the tab starts with every setting of the RuneLite config
panel, with the same names (the config panel's description on mouse-over), order and sections: Show sidebar,
Route-only sidebar and Minimal mode with no heading, then **Routes**, **World map and right-click** and **Graphics**
(open; click a heading to fold it, as "Your answers"). **Advanced** (Developer details, Record performance) is the last thing in the tab,
folded at first as in the config panel. The config panel's transport sections (Transport types, Avoid, Bank, Sailing)
are this tab's own sections further down, so they are not shown twice. Controls: a tick box for on / off; a number box
with arrows for a number, kept in the config panel's range (Re-route when off by 1-50 tiles, Arrived within 0-20
steps, thicknesses, Text size 8-40); a list for Path style (Tiles, Line, Arrows at turns); a colour square for a colour
that opens RuneLite's own colour picker (the colour is taken when the picker closes). Both places read and write the
same RuneLite config values (`ConfigList`: the items, kept as RuneLite stores them; `ConfigMirror`: the controls), so a
change in either shows in the other (`BestPathPanel.configChanged()` reads them again; nothing is written back then).
These are RuneLite settings, the same for every account (the transport sections are per account). Unticking "Show
sidebar" here hides the sidebar button at once, as it does in the config panel. Pictures: `9c-settings-config.png`
(the top of the tab with a few values changed), `9c-settings-config-full.png` (the whole tab, Advanced unfolded).
`ConfigListTest` fails when a config item is added to `BestPathConfig` without its line in `ConfigList`.

**Icons**: every transport type and "avoid" rule has an icon between its box and its
label, from the same picture provider as the step cards (`IconChooser.category` / `IconChooser.avoid`). Labels
wrap to a second line beside the icon (never under the box); a click on the icon or the label ticks the box.
A rule the data can't support is greyed (icon faded).

| Row | Icon |
|---|---|
| Spells, home and minigame teleports | the spell book (`SpriteID.SideIcons.MAGIC`, the Magic tab's icon) |
| Teleport items, jewellery | Amulet of glory(6) (the default item of the glory group) |
| House (POH) teleports and portals | the world map's house portal icon |
| Stairs and ladders | the world map's "upstairs" icon |
| Shortcuts | the world map's agility shortcut icon |
| Gates and barriers that need something | a door (`SpriteID.PohDoors.CLOSED`, the house viewer's door icon) |
| Boats, fairy rings, carts, other rides | the world map's transportation icon |
| Avoid: cost | coins (as on the cards' coins needs) |
| Avoid: runes | a law rune |
| Avoid: known bugs | a red "!" (`SpriteID.EXCLAMATION_MARK`) |
| Avoid: long cooldown | a stopwatch (item Giant stopwatch, `ItemID.GIANT_STOPWATCH`) |
| Sailing: a boat | the game's own boat sprite for its kind, the one the cache's boat table names (table 166 column 41): `SpriteID.IconSailingBoats35x35._0` raft, `._1` skiff, `._2` sloop (no picture for a kind routes don't know) |
| Sailing: a shipwright may bring my boat | coins (it costs coins), as the "cost" row |

**House (POH) section** (scanned only, nothing is ticked by hand): where the
house is ("Your house: Rimmington", "No house found", "Log in to see your house."), what the house scan found
("Built:", one line per thing with a picture: mounted items their item, portal chamber portals their spell's icon,
altars the spell book, the garden's spirit tree / fairy ring the transportation icon, the rest the house portal
icon; the portal nexus one line with its destinations under it), "Last looked: <date, time>" and how to make it
look again; before the first look the hint ("Go into your house with Teleport to House, a house tablet, or
the Construction cape (or turn on building mode) so Best Path can see what's built."). Details:
docs/PLUGIN_PLAYER_STATE.md "The Settings tab's House (POH) section".

**Sailing section** (choose which of the player's boats routes may use, and whether a shipwright may bring a
boat; there is no Sailing switch, no boat size and no Tempor Tantrum avoid): one row per boat the player owns, box +
boat picture + "Sloop: Jolly Raven" + where it is ("At Port Sarim", "At sea, you are on it", "Bottled"...); the
mouse-over lists its parts or why routes can't use it (unknown place, kind or part, a temporary boat: box greyed).
Capsized and lost boats (a shipwright brings them, if allowed) and bottled ones (only while a full boat bottle per
bottled boat is in the inventory) are not greyed: their mouse-over says when routes use them. Unticked boats are saved per account by slot (`boatoff=<slot>`), the shipwright box as
`shipwrights=off`; a change searches the current route again quietly, as the other settings. Without boats a short
note: "Log in to see your boats here.", "You have no boats yet. Routes sail once you own one." or "Your boats can't
be read yet (the map data is still loading, or has no boat table)." Code: `ui/SettingsTab`, `player/PlayerBoats`;
tests `SettingsTabSailingTest`, `BoatReadingTest`.

Each Settings choice is matched to
fields the exported transport data really has (`core/data/TransportLine`, `Tables`, `PlayerTransports`,
`data/manual/teleport-categories.tsv`, `data/manual/networks.tsv`). Code: `route/TransportRules.java`;
tests: `TransportRulesTest`; on the real data: `./gradlew settingsCheck` (needs the export).

**Transport types** (untick = never used, even when there is then no route). Each transport is in at
most one type; the first row that fits wins. Counts are lines of the real export (4,621 lines).

| Settings box | Transports it covers (data fields) | Lines |
|---|---|---|
| House (POH) teleports and portals | network "Player-owned house" (rides out of the house), and every transport that starts or lands on the house spot (house portals, Teleport to House, house tablet, Construction / max cape house teleports, the house spirit tree and fairy ring stops) | 112 |
| Spells, home and minigame teleports | kind TELEPORT with teleport category `spell`, `home teleport` or `grouping teleport` | 91 |
| Teleport items, jewellery | kind TELEPORT with teleport category `tablet`, `scroll`, `jewellery (infinite / consumable)`, `cape or diary item (infinite / consumable)`, `other item (infinite / consumable)` | 321 |
| Boats, fairy rings, carts, other rides | Data Tool category TRAVEL, or any network (boats and ships, charter ships, fairy rings, spirit trees, gliders, minecarts, canoes, balloons, quetzals, carpets, eagles, Soul Wars portals...) | 743 |
| Stairs and ladders | Data Tool category CLIMB_UP, CLIMB_DOWN, CLIMB_BOTH | 1,633 |
| Shortcuts | Data Tool category SHORTCUT (agility shortcuts and the other short cuts the data marks so) | 899 |
| Gates and barriers that need something | Data Tool category PASS (toll gates, guild doors, magic barriers...). Ordinary doors are part of the walking map (opened on the way, +2 ticks), not transports, and can't be switched off | 97 |
| (none: always used) | ENTER, EXIT (caves, trapdoors, portals in or out), CUSTOM, and a teleport with no known category | 725 |

**Avoid** (used only when there is no route without them; the route then says so in its header and on
the step: "No route without the transports you chose to avoid"):

| Settings box | Transports it covers | Lines |
|---|---|---|
| Transports that cost something | the requirement line (with its network's) has a `coins N` part or an item `used up`, or the teleport category is a `(consumable)` one (charges, limited uses) | 763 |
| Teleports that use runes | the requirement line has a `rune ...` part (all 65 are spells) | 65 |
| Long-cooldown teleports | the long-cooldown flag `c` (home and minigame teleports; while one cools down it is never used, avoided or not) | 27 |
| Transports with known bugs | **disabled**: the data has no known-bug mark (none was invented); the box is greyed, its tooltip says so, and a saved tick is ignored | 0 |

**Coin limit** (a player-adjustable cap on a fare's coin cost): at the end of Avoid, the tick box "Don't use
travel that costs over" (off) and "Coin limit" (1,000 coins; typed or 100 a click, 0 to 100,000,000), per account
(`coincap=on`, `coins=<n>`) and in the config panel's Avoid section (`coinCap`, `coinCapAmount`). Unlike the boxes
above it is strict: a fare over the limit is never used, even when nothing else works. Each fare on its own (not the
route's total). How: `CoreRouteEngine.withCoinCap` puts the limit in the search's player state
(`PlayerState.withCoinCap`), and a `coins N` part above it counts as not met (`Requirement.Coins`: "coins 500 (over
your limit of 100)"), so a choice like "coins or a ticket" still works with the ticket, each way of a two-way
transport counts on its own, no bank trip is made for such coins, and "no route" explanations name it. Not covered:
a shipwright bringing a boat (Sailing has its own switch).

How it works: the route search leaves out the switched-off types and the avoided transports; if that
finds no route and something is avoided, it searches again with the avoided ones allowed. Session
exclusions and the blacklist ("Never use") work by transport id.

Effect on 60 random routes as the Maxed player (`settingsCheck`): items off changes 37 (3 then have no
route), stairs off 14 (12 no route), shortcuts off 9, avoid costs 17 (none lost: the costly ones are
then used only where nothing else works), spells off / avoid runes 1 each, house 0 (Maxed has no
house), boats 4.

### Notes on the choices

1. Minigame (grouping) teleports and home teleports are in "Spells, home and minigame teleports".
2. Cave entrances, trapdoors and other ENTER / EXIT transports, and CUSTOM ones, are in no box (always used).
3. Everything touching the house is "House", even a spell (Teleport to House) or a cape teleport; otherwise
   switching the house off would still leave Teleport to House usable.
4. "Cost": a requirement that mentions coins counts even when a quest or diary makes it free for this player (toll
   gate, Lovakengj minecart, magic carpets). Jewellery with charges counts as a cost; eternal (infinite) items don't.
5. "Avoid" means never, unless no route at all exists without them (it is not a tick penalty).
6. Known bugs: the data has no such mark, so that box is disabled.

## Developer details
A mode that shows more details, to analyse a route that looks wrong. RuneLite config panel "Developer details" (`developerDetails`,
off by default; not in the sidebar). It shows tile numbers, which the sidebar otherwise never does.
- **Route tab, top**: a box with where the player stands, filled each game tick (client thread; sent to the sidebar
  only when it changed, `PlayerTracker.whereLines`): "Game tile" (`Player#getWorldLocation`: in an instance, the
  copy's tile), "Map tile" (`WorldPoint.fromLocalInstance`: what routes use), each with its region; in an instance
  the copied chunk and its rotation (`WorldView#getInstanceTemplateChunks`, read as RuneLite's own
  `fromLocalInstance` does); "Routes use" inside a house; the boat's tile on a boat; the world. "Copy" puts the lines
  and the destination on the clipboard.
- **Every card** (step cards and "no route" cards): small grey lines at the end: the transport id, its data name and
  category, the step's kind, ticks and landing tile, then the requirement part by part with the result for the player
  the route was searched for (`RequirementCheck`): `[ok]` met, `[bank]` met with the bank's items, `[no]` not met,
  `[?]` no player; "one of:" / "all of:" groups indented; "(not answered yet)" on a part resting on an open question.
- Switching it re-makes the shown cards. uiShot: `real-19-bank-first-developer.png`.

## Record performance
For profiling the plugin in real use. Advanced setting "Record performance"
(`recordPerformance`, off by default; config panel and Settings tab). Off, each timed piece costs one volatile read.
On (`perf/PerfRecorder`, `perf/PerfStat`), while the player plays normally:
- **Timed**: every game event Best Path handles (game tick, varbits, items, scripts, menus, objects spawned, ...),
  each overlay's drawing, the client thread jobs Best Path queues (as one piece), the map data load and every route
  search (route worker), and the sidebar showing a route (Swing). Per piece: runs, total, mean, median, 99 %, worst
  (doubling microsecond buckets, no list of samples) and, on the client thread, runs of 2 ms or more.
- **Frames** (`BeforeRender`): frame to frame for the whole game, Best Path's client thread time between two frames,
  stutters (a frame 50 ms or more after the last) and how many had 5 ms or more of Best Path before them, the 10
  worst; gaps of 2 s or more (minimised, loading) are pauses, left out.
- **Memory**: once a minute the Java heap in use (the whole client: Java can't count one plugin) and the garbage
  collectors' runs and time.
- **Best Path's own memory** (an estimate): after each route, on the route worker, the bytes the
  search keeps between routes (`CoreRouteEngine.cacheBytes`: the search's caches, budget 15 MB, and the redraw's game
  path), last and highest, plus the fixed part measured outside the game by `gradlew memoryCheck` (map data and search
  set-up, `PerfRecorder.FIXED_MB` = 20.3 MB; measure again when the data grows), against the 40 MB limit.
- **Report**: `.runelite/bestpath/performance/<start time>.txt`, written on the route worker every 5 minutes, at
  logout and when switched off (or Best Path stops), each time with everything so far. Switching on again starts a
  new file and new counts.

## In the game world
RuneLite config panel, section **Graphics**: besides the items below, **Path thickness** (1-10, 3; `overlay.PathStrokes`),
**Glow thickness** (1-10, 3), **Item and UI glow thickness** (1-5, 1), **Text size** (8-40, 16) and **Bold text** (off).
- **Route colour** (light blue 0,200,255 by default; `overlay.RouteColours`): the walk, the transport tiles, the boat's
  course, the minimap and the world map are all this colour. The current step is the same colour mixed 40% towards
  white and drawn thicker. On the world map, steps done stay grey and the destination
  pin red.
- **Path style** (`overlay.PathStyle`): Tiles (a square per tile, as before), Line (one line through the tiles'
  middles), Arrows at turns (that line with an arrow head only where the walk turns; `overlay.PathTurns`: a turn counts when the new heading holds two steps, so a staircase
  of straight / diagonal steps is no turn). Transport tiles stay tiles; the boat's course stays its arrows.
- **Text in the game** (on): the next step that is not a walk (`StepTargets.nextAction`), its card title made
  shorter where it was over 40 characters (`route.GameText`, `RouteStep.getGameText`: "Boat ->
  Land's End", "Wilderness obelisk: Level 19", "Quetzal whistle -> Fortis Colosseum", "Sail -> Rellekka", "Leave the
  boat"; our own words use "->", the game's own text keeps its "to"; the card keeps the full title; 7 of the game's own menu texts, 41-42 characters, stay as they are), above the
  thing to click, in **Text colour** (light blue by default); with nothing to click in the world (a teleport, sailing) above the player,
  without "Next:", in **Text above you colour** (white by default). A bank step says only what to take out, by the items' game names ("Take out: Spade, Feather
  x5"; no bank name), and with more than three items "Take out 5 items (see the side panel)" (or "the box" when the
  next-step box shows). A house step shows no words until its furniture is found, then right on it (always on the jewellery box; a search that finds nothing is not kept, so it is found on the first
  tick the house is loaded).
- **Path under objects** (off): the path is always drawn behind the player's character (the character's outline is
  left out of it); ticked, also behind the standing objects on the route's
  tiles (the objects are looked up when the route or scene changes,
  their outlines each frame).
- **Glow what to click** (on) and **Glow colour** (light blue): the object to click (the data's
  object id within 4 tiles of where the step is used; else an object named as the menu's target with that option),
  else the NPC named so within 15 tiles; a network ride (fairy ring, spirit tree) at the stop it leaves from (its
  object, or in the house its furniture); the step's item in the inventory and worn equipment (any variant: every charge
  of an amulet of glory); the spell in the open spellbook (a frame); in the player's house the furniture the step uses
  (what its requirement says is built: "ornate jewellery box", the portal nexus...; the house table from the cache).
  Found once a game tick on the client thread (`overlay.StepTargets`), drawn every frame (`StepHintOverlay`,
  `StepItemOverlay`, `StepSpellOverlay`; RuneLite's `ModelOutlineRenderer` and item outlines).
- **The walk inside the house** (drawn only, never in the route): when the next step is
  house furniture, the walk from the player to it on the house's own walls as the game has loaded them, drawn as the
  current step in the path style (`overlay.HousePath`). A closed door counts as a wall; on another floor nothing is drawn.
- **Next-step box** (off): a box at the top left of the game screen (it can be dragged): "Step 2 of 5" (card numbers,
  as the sidebar) and the step's words (a bank step: "Take out:" and every item); "Calculating...", "No route" with its
  first line. No distance; gone once arrived.
- **Minimal mode** (off): no side panel at all; the next-step box is shown, and both right-click menus (world map, game
  tiles) offer "Set destination" / "Clear destination" whatever their own switches say.

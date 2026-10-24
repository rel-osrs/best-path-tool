# Plugin: player state and route progress

How the plugin knows what the player can use (for the requirement checks on transports) and how it
follows the player along a route. Code in `src/main/java/com/bestpath/`
(`PlayerTracker`, `player/`, `route/RouteProgress`, `route/RouteFollower`).

## What is read, and when

Everything comes from RuneLite's public API, on the client thread. Nothing is polled: game events say
when something changed, and a full copy (a "snapshot") is made only when a route is searched or when a
change may matter.

| Part | Where it comes from | Notes |
|---|---|---|
| Position | the local player, instance-safe (real-world tile) | |
| Levels | `getRealSkillLevel` | real levels only, never boosted (the requirement model uses real levels); Sailing too (RuneLite `Skill.SAILING`), for the docks' levels |
| Members | the world type (members world or not) | free accounts can't log in to members worlds, and a member on a free world can't use members things, so the world is exactly what routing needs; membership days are not read |
| Inventory, worn items | the inventory and equipment containers | |
| Bank | the bank container, while the bank is open | remembered and saved per account (RuneLite profile); written once its changes settle (6 s without a change, at the latest 60 s after the first), to the account it was seen on, and before another account's values load or the plugin stops: a banking session of 30 deposits is one write; until the bank was opened once with the plugin on, the bank counts as empty |
| Varbits / varps | only the ones some transport requirement mentions | read fresh at each snapshot |
| Quests | RuneLite's quest list, only quests some requirement mentions | reading a quest runs a small game script, so states are kept and re-read only when that quest's own progress var changed (see "Quest re-reads" below); quest names are matched to RuneLite's (exact, then without "The"/punctuation, then a single RuneLite name starting with ours) |
| Spellbook | varbit `SPELLBOOK` (4070): 0 standard, 1 ancient, 2 lunar, 3 arceuus | values from Quest Helper `requirements/util/Spellbook.java` |
| House place | varbit `POH_HOUSE_LOCATION` (2187): 1 Rimmington, 2 Taverley, 3 Pollnivneach, 4 Rellekka, 5 Brimhaven, 6 Yanille, 8 Hosidius, 9 Prifddinas, 13 Aldarin | checked in the game cache: enum 252 has exactly these keys and names (the Shortest Path data and Quest Helper agree); the game's script 3809 says "You must own a house" when the value is 0. 0 = no house. Any other value above 0 (a place added later) = a house at a place the table doesn't know: its name is read from the game's enum 252 in game (`client.getEnum`), else "an unknown place"; Teleport to House into the house and the rides out of it work, "house at <place>" (the outside teleports, the house portals) does not |
| What is built in the house | the house scan: the furniture in the scene while the player is in THEIR OWN house, plus the portal nexus slot varbits `POH_NEXUS_TELE_1..45` | remembered per account (key `houseThings`); until the first visit with the plugin on nothing is known = nothing built; see "The house scan" below |
| Rune pouch | varbits `RUNE_POUCH_TYPE_1..6` / `RUNE_POUCH_QUANTITY_1..6`, rune types turned into item ids with the game's rune pouch list (enum 982), the same way RuneLite's own rune pouch overlay does | the runes count as carried while a rune pouch (any of the 4 kinds) is in the inventory, otherwise as in the bank |
| Player answers | saved per account (key `answers`): one per line, a yes as the answer's words (as always), a no as `no`, a tab, the words | given with Yes / No under a step card or changed / cleared in the Settings tab's "Your answers"; written at once when given (see "Player answers" below) |
| Home / minigame teleport cooldowns | varp 892 (gameval `AIDE_TELE_TIMER`: the minute the home teleport was last used), varp 888 (`SLUG2_REGIONUID`: the minigame teleport's), varp 3078 (`DATE_MINUTES`: the game's minute clock) | at each snapshot: "ready" or "cooling down until minute N" (see "Home and minigame teleport cooldowns" below) |
| Boats (Sailing) | the boat varbits `SAILING_BOAT_1..5_*` (kind, dock, hull, sails, keel, helm, teleport focus, brazier, facilities, name) and the boarded boat's kind and name; meanings from the export's `boats.tsv` (docs/SAILING_SPEC.md, part 2) | read again only after one of them changed (`VarbitChanged`) or the player got on or off a boat; the Settings tab is updated when its rows change; a route asked for on the player's own boat starts from the boat's world tile (RuneLite `WorldEntity`) |
| Ownership cache | requirement items ever seen in the inventory, equipment or bank, saved per account (key `ownedItems`) | kept up to date and written like the bank (once changes settle); NOT put into the snapshot: the requirement model (`core/req`) knows carried, worn and banked items only, and whether "owned once" should count as "in the bank" is undecided (see "Known limitations") |

Saved per account (RuneLite profile config, group `bestpath`): `transportSettings` (Settings tab),
`bank`, `ownedItems`, `answers`, `houseThings`, `houseScanned` (when the own house was last looked at).

Without an account profile nothing is lost:
- Logged out: the Settings tab keeps the settings in use (not reset to the defaults). A change made
  while logged out is used at once and saved to the next account that logs in (it replaces that
  account's saved settings, being the newest choice). Not saved if RuneLite is closed before a login.
- Logged in to an account that has no RuneLite profile yet (no plugin wrote one): the first save
  creates the profile (RuneLite does this when asked to write while logged in). The plugin ignores
  the "profile changed" event its own write causes (`ProfileStore.isWriting`).

### Quest re-reads

Each quest's progress var (varbit or varp) comes from Quest Helper's data (used as data with permission): `src/main/resources/com/bestpath/player/quest-vars.tsv`, 207 vars for
198 RuneLite quests, each line with its source ("Quest Helper QuestHelperQuest.java ..., QuestVarbits.java
..."). Made by `gradlew questVarsExtract -PquestHelper=<reference/quest-helper> -PquestHelperCommit=a526461`
(dev only; VarbitID names become numbers with RuneLite's gameval). `player/QuestWatch` then decides:
- a change of a mentioned quest's own var: that quest is read again with the next snapshot, and the
  change counts as "may matter" (the route may be searched again);
- any other var change: nothing is searched; every mentioned quest is read again with the next
  snapshot, but at most once a minute (a safety net for quests whose state also depends on other
  vars, and for quests without a known var);
- all quests are read at the start, after an account switch and when the transport data arrives.

On the real export 109 of the 112 quests the requirements mention have a var; the other 3 (the
Recipe for Disaster parts "King Awowogei" and "Pirate Pete", which Quest Helper files under the main
quest, and the miniquest The Frozen Door) are kept fresh by the once-a-minute re-read only
(`gradlew settingsCheck` prints the list). Before, every var change in the game marked every quest
for a re-read: about 112 quest scripts per snapshot every few seconds while a route was shown.

A `LOGGED_IN` after a scene load (`LOADING` -> `LOGGED_IN`) no longer counts as "something changed";
a login or world hop still does (`player/LoginEdges`).

Until the plugin loads transport data, no requirement mentions anything, so no varbits, varps or
quests are read. When the data arrives the plugin searches the current route again (a search asked
for in the first second used a snapshot without quests or varbits).

The data loader passes what the requirements mention to `PlayerTracker.setInputs(RequirementInputs.collect(
<every transport's requirements>))`.

### Home and minigame teleport cooldowns

Home and minigame teleports are used only while they are available, which is tracked. All 27
teleports with the long-cooldown flag `c` are covered: the 4 home teleports (teleport category "home
teleport") and the 23 minigame teleports ("grouping teleport"). Code: core `req/Cooldown`, plugin
`player/CooldownWatch`.

What the game keeps (checked in cache #2735's client scripts, the RuneLite gameval names and the Wiki;
nothing guessed):
- the minute (counted from 1 January 1970, UTC) the player last used a home teleport is in varp 892, a
  minigame teleport in varp 888 (0 = never). RuneLite's older API names them `LAST_HOME_TELEPORT` and
  `LAST_MINIGAME_TELEPORT`; its Timers plugin reads the same two.
- the game itself greys out the four spellbooks' home teleports while `varp 3078 - varp 892 < 30`, and
  the four Minigame Teleport spells (and the Grouping tab's teleport button) while
  `varp 3078 - varp 888 < 20` (scripts 2614 and 9145); its buff bar counts down the same minutes
  (scripts 5923, 5941). Varp 3078 (`DATE_MINUTES`) is the game's minute clock, set only by the server.
- the Wiki agrees: 30 minutes, shared by every home teleport ("Home Teleport" rev 15348879); 20 minutes,
  shared by every minigame teleport, not with the home teleports ("Grouping" rev 15364976).

The plugin:
- **Snapshot**: reads the three varps; a teleport is cooling down while `now - last use < 30` (home) or
  `< 20` (minigame), the game's own test, and is then left out of the player's transports (core
  `PlayerTransports`; the Data Tool has the same rule). "Now" is the game's minute clock, but never more
  than a minute behind the computer's clock (in case the game stops sending it).
- **Ready again, at that moment**: the game sends its minute clock once a minute; that event alone does
  nothing unless a cooling-down teleport turns ready, then the route is checked once (quietly, as any
  availability change). As a backstop each game tick compares the computer's clock with the stored ready
  time (one comparison; at most a minute late if the game's clock stops). No polling loop.
- **Just used**: the last-use varps are among the watched inputs (`RequirementInputs.withCooldowns`).
  Using one starts its cooldown; that searches again only when the rest of the route still needs that
  teleport. Casting the route's own home teleport (the next transport step) does not search again while
  the player is still casting it.
- **Step card**: a home or minigame teleport shows "Ready" with the stopwatch (it is only used when
  ready, so no countdown is shown).

Not modelled: the game's one exception (script 9712: the home teleport is not greyed out while varbit
15482 `IN_VAMPYRIUM` is 1 and varbit 15464 `MYQ6` is 70 to 125, a part of a quest).

## The house scan

When the player is inside their own house, the plugin looks at the furniture, works
out which house things are built and remembers that per account. It is not asked with Yes / No. Code: plugin
`HouseScanner` (client glue), `player/HouseFurniture`, `player/OwnHouseWatch`, `player/HouseMemory`; tests
`HouseScanTest` (plugin) and `HouseObjectsCliTest` (tool).

**Which furniture is which thing** comes from the game cache (verified there), written by the dev aid
`gradlew -p tool houseObjects --args="<abs cache folder>"` into
`src/main/resources/com/bestpath/player/house-objects.tsv` (282 objects, 41 nexus destinations, cache #2735;
run it again after a cache update and read its notes). The names are the `house has <thing>` names of
`data/manual/house-things.tsv`. What the cache says:
- built furniture has a "Remove" option (the decorated Christmas 2020 copies "Remove-decorations", mounted capes
  on the cape hanger "Take"); it is recognised by name and options:

| Thing | Objects (cache #2735) |
|---|---|
| basic / fancy / ornate jewellery box | 29154 / 29155 / 29156 (placed with a state varbit 2308 = the left-click teleport), their states 37492-37546, and the Fortis Colosseum states 50710-50712 |
| mounted glory | 13523 "Amulet of Glory" (Edgeville, Karamja, Draynor Village, Al Kharid, Remove) |
| mounted mythical cape | 31986 "Mythical cape" (quest hall trophy: Teleport, Remove) and 31983 "Mounted Mythical Cape" (cape hanger: Admire, Teleport, Take) |
| mounted digsite pendant / xeric's talisman | 33416-33418, 33420 / 33411-33415, 33419 (the amulet spaces by the portal nexus) |
| obelisk | 31554 |
| spirit tree / fairy ring / spirit tree & fairy ring | 29227 (+ 40778 Christmas, 44936 Leagues) / 29228 / 29229 (+ 40779 Christmas, 27097 Leagues) |
| portal chamber: <destination> | every "<X> Portal" with Enter or Toggle and Remove (4 per destination, 39 destinations), and the toggling portals' state objects (Varrock / Grand Exchange, Camelot / Seers' Village, Watchtower / Yanille) |
| portal nexus: <destination> | a "Portal Nexus" (33408-33410, 56075 Leagues) is built, and the destination is in one of its 45 slots |
| ancient / lunar / dark / occult altar | 29147 / 29148 / 29149 / 29150 and 31858-31861 (no transport needs them yet) |

- **Portal nexus destinations** are varbits, not objects: `POH_NEXUS_TELE_1..45` (RuneLite gameval; the game's
  own script 2671, "is this destination in my nexus", reads exactly these 45, checked by the tool) each hold a
  destination key, 0 = empty. The keys are cache enum 1377 (key -> struct, param 660 = the destination's name,
  param 680 on an alternate such as Grand Exchange = its main destination). The cache names were matched by hand
  to the requirement names (Marim = Ape Atoll Teleport (standard): its runes include a banana; Ape Atoll Dungeon =
  the Arceuus one; Lunar Isle = Moonclan Teleport; Troll Stronghold and Weiss = "Stony basalt" and "Icy basalt",
  the struct's item). The slots are read only during a scan of the own house with a nexus in it.
- **The nexus list's keyboard keys**: the same scan works out the key
  of each line of the nexus's Teleport Menu (`HouseFurniture.nexusKeys`, saved per account as `houseNexusKeys`,
  "Senntisten=M" per line) and the step text shows it: "Portal Nexus -> Senntisten (M)". The game's scripts
  (cache #2735): script 2674 lists slot by slot (4 slots for a marble nexus, 8 for POH_NEXUS_ID 2, 41 for 3), one
  line per destination, an empty slot none; script 2675 keys the lines 1-9 then A-Z; an alternate (Grand Exchange,
  Seers' Village, Yanille, Last Boat) gets its own line right under its main one when script 2664 says it is
  unlocked: the base Magic level of its struct and its diary reward (Varrock medium / hard / elite, varbits
  4520-4522; Kandarin hard / elite 4517 / 4518; Ardougne hard / elite 4501 / 4502; Last Boat: varp 4959 >= 50). The
  `nexus*` rows of `house-objects.tsv` hold this, written by the tool from those scripts. **Not seen in the game
  yet**: the menu reads the `POH_NEXUS_TELE_*_TEMP` copies of the slots; the plugin reads the saved slots, taken to
  be what the menu shows. On mobile (or with the OSM_SIMULATE varbit 6352) the game shows no keys at all.
- **The portal chamber's portals** for the step text (`chamber` rows): "Enter Senntisten Portal"; the three that
  toggle have no Enter, each place is an option: "Grand Exchange" on "Varrock Portal" (the name follows the toggle).
- Left out on purpose (stays "not built"): the Mythical max cape on the cape hanger (39621, "Teleport": where it
  goes is not in the cache), and plain "Portal" objects without a destination.

**Whose house.** A friend's house has the same furniture ids, and scanning it would give routes teleports the
player doesn't have, so only certain signals count (`player/OwnHouseWatch`):
1. The way in: a click that always leads into the player's own house, and the next scene load is a house: the
   house portal's "Home" or "Build mode", the Teleport to House spell ("Cast"), the "Teleport to house" tablet
   (Break, Inside), the Construction cape's "Tele to POH", or the Max cape's "Teleports" -> "Home" (options
   checked in the cache). The portal's "Enter" asks in a dialogue whose house, so it doesn't
   count. Any other click (walking excepted) cancels it, it is forgotten after 30 ticks, and the first scene load
   uses it up. Only these exact option + target pairs count (checked against every object, NPC, item
   and spell option in cache #2735): the tablet's other clicks (Outside, Group, Use, Drop, the bank's Withdraw /
   Deposit...) cancel, "Tele to POH" counts only on the Construction capes, "Home" / "Build mode" only on a
   "Portal" (the nine house portals are the only objects with them).
   - **The Max cape** (cache #2735): the carried cape (item 13280, RuneLite `ItemID.SKILLCAPE_MAX`) has
     "Teleports" with the sub-options Warrior's Guild, Fishing Guild, Crafting Guild, Farming Guild, Otto's Grotto,
     Feldip Hills, Black chinchompas, Hunter Guild, The Pandemonium, **Home**, the nine house portal places, Boat.
     The worn cape (13342, `SKILLCAPE_MAX_WORN`) has worn options Home, Crafting Guild, Guild Teleports, Skilling
     Areas, **POH Portals** (Home, Rimmington, Taverley, Pollnivneach, Hosidius, Aldarin, Rellekka, Brimhaven,
     Yanille, Prifddinas), Spellbook, Features. A sub-option click reads just "Home" on "Max cape", so the menu
     entry's item id tells them apart: "Home" on item 13280 counts. "POH Portals" -> "Home" (and the worn option
     "Home") is left out: the cache does not say where item options lead (the game's server decides), and on the
     worn cape both read "Home" on "Max cape", so one can't be told from the other. If the click's item id is not
     13280 (e.g. RuneLite gives -1 for a sub-option), nothing is scanned: the safe side.
2. Building mode: in a house with the player's building-mode setting on (varbit `POH_BUILDING_MODE`) and empty
   build spaces in the scene (objects with a "Build" option): guests never see build spaces.

A house loaded again from inside a house (building mode switched) keeps the answer. "A house" = the scene is an
instance copied from the house room templates (cache: regions 7534, 7535, 7790, 7791, 8046, 8047, 8302, 8303, the
only ones with thousands of build spaces). Missed on purpose (nothing is scanned, the last result stays): "Enter"
then "Go to your house", the worn Max cape's "Home" and "POH Portals" -> "Home", the Construction cape's "Teleport"
-> "Home", a spirit tree's "Your house", the plugin switched on while already in the house, and any other way in not
listed above.

**When.** Event-driven: the known furniture is tracked from the object spawn / despawn events (all four object
kinds). Entering the own house, a piece of furniture appearing or going, or a nexus slot / building mode changing
while inside starts a scan once things settle (2 ticks without news, at most 10). The first scan of a visit also
reads the whole scene once. The result replaces what was known (furniture removed is gone), is saved only when it
differs, and then the route is checked again like any other change (`AvailabilityDiff` compares the house things).

### Routes through the house

Routes at first never used Teleport to House, the jewellery box, the portal nexus, the house fairy ring...
The causes were found with tests that go the plugin's way (game reading ->
`SnapshotMapper` -> player state -> the plugin's route engine on the real export, `HouseRealDataTest`):

1. **No route ever went through the house.** The house is one spot, 1857,7065,0, a tile no one walks on. The Data
   Tool's map has its cell (the house-room template area has floors), but the plugin export leaves out cells with
   no walkable tile, and the route search only uses a transport end that lies in a stored cell: every teleport into
   the house was dropped, and the rides out of it never started. Fix (core `PluginRouter.searchMap` / `hubCells`):
   the plugin's search adds an empty cell for every tile off the walking map where one transport can arrive and
   another can leave (a "hub": the house spot; also a staircase chain at 1943,6069 that no walk reaches, which
   changes nothing). The export check (`exportPlugin`) compares against the same map. No data file changed.
2. **A player inside the house had no route.** RuneLite gives a player inside a house a tile of the house room
   templates (regions 7534 ... 8303), not on the walking map: after "Teleport to house" the route was searched
   again from there and found nothing, and the jewellery box step never became current. Fix
   (`HouseFurniture.routeTile`, `PlayerTracker.routePosition`): inside a house, routes start (and progress is
   followed) from the house spot. That is also so in a friend's house (see "Known limitations").
3. **Unknown house place values** (none today) read as "no house"; now a house at an unknown place (see the table).

Checked and found right (tests): the nine place values and the data's `house at <place>` names are the same set;
the house rides' requirements with a house and with scanned things (every `house has <thing>` the data needs can be
found by the scan, and everything the scan finds is used except the altars, Battlefront and Teleport to Boat);
the Settings tab's house type is on by default and switching it off leaves every house transport out; the house
rides cost their 4 ticks plus the "Player-owned house" network's 10 walk ticks (and follow a tick change of that
network); free-to-play worlds get no house; the scanned things reach the player state.

What only the game can show is in the test list below.

### The Settings tab's "House (POH)" section

Shows (no ticking by hand: the house is found by scanning only):
- where the house is: "Your house: Rimmington", "No house found" (with "Routes use house teleports once you own a
  house.") or, logged out, "Log in to see your house.";
- before the house was looked at on this account, this hint: "Go into your house with Teleport to House, a
  house tablet, or the Construction cape (or turn on building mode) so Best Path can see what's built.";
- after a look: "Built:" with one line per thing (jewellery box, mounted glory / mythical cape / digsite pendant /
  Xeric's talisman with the item's picture, garden spirit tree / fairy ring, obelisk, altars, each portal chamber
  portal with its spell's icon, the portal nexus as one line with its destinations under it), "Last looked: 14 Oct
  2026, 20:15", and how to make it look again. "Nothing found that routes can use (...)" when the look found none.

Code: `player/HouseView` (what the section shows, pure), `PlayerTracker.houseForSidebar` (each tick, only when it
changed; one varbit read), `SettingsTab.showHouse`. The time of the last look is saved once per scene load of the
own house (`houseScanned`), the things only when they change (as before). Pictures: `gradlew uiShot -PrealData`
saves `real-50-house-settings-not-looked.png`, `real-51-house-settings-scanned.png`,
`real-52-house-jewellery-box.png` (Lumbridge to Castle Wars: tablet into the house, the jewellery box out, 18
ticks) and `real-53-house-inside-nexus.png` (from inside the house to Varrock: the nexus, 14 ticks).

### In-game test list for the house scan

1. Log in and ask for a route to a place a house teleport you have built would help (e.g. Castle Wars with an
   ornate or basic jewellery box). Note the route.
2. Teleport into your own house (spell, tablet "Break", or the house portal's "Home"). Wait 2 seconds. The route
   should now be able to use your jewellery box (a step through your house).
3. Check a few things you have: a fairy ring / spirit tree in the garden, a portal nexus (a destination you set in
   it), a portal in the portal chamber, a mounted glory, a mounted xeric's talisman / digsite pendant. Routes to
   their destinations should use them after the visit.
4. Leave, log out and back in: routes still use them (remembered per account). Another account on the same
   computer does not get them.
5. Visit a FRIEND's house (portal "Friend's house"): nothing changes for your routes.
6. Use the portal's "Enter" then "Go to your house": nothing is scanned (known gap); "Home" works.
7. Building mode: remove a piece of furniture (or change a nexus destination with "Configuration"), wait 2
   seconds: routes stop using it (or use the new destination).
8. With RuneLite's log at debug level, "House: in the player's own house" and "House scan: [...]" show what was
   found.

More checks (what to do, what you should see):

9. Settings tab, "House (POH)", before going into the house with the plugin on: "Your house: <your place>" and the
   hint "Go into your house with Teleport to House, ...". With no house: "No house found".
10. Teleport into your house with the tablet's "Break" or "Inside" (or the spell, or the Construction cape). Within
    about 2 seconds the section lists what is built ("Built:", your jewellery box, nexus destinations...) and
    "Last looked: <now>".
11. Standing in the house, ask for a route to Castle Wars (2440, 3090, 0) with a jewellery box built: the route is
    ONE step, the jewellery box (14 ticks), not "no route". The ground/minimap markings are not shown in the house
    (expected: the house spot is not a real tile); note it if something odd is drawn inside the house.
12. Outside, far away (e.g. Ardougne market), carrying house tablets, route to Castle Wars: "Teleport to house ->
    Inside" then "House jewellery box: Castle Wars Arena" (18 ticks). Follow it: after the teleport the box card
    becomes the current step (not "searching again").
13. With a nexus set to Varrock, from Ardougne to Varrock square (3213, 3424, 0): tablet, then the nexus's Varrock
    portal (unless something quicker exists for you).
14. Settings tab: untick "House (POH) teleports and portals": the same routes no longer use the house.
15. Log out: the section says "Log in to see your house."; log back in: the things and the time come back.

Max cape:

16. Carry the Max cape (not worn), right-click it, "Teleports" -> "Home": within about 2 seconds "Last looked" in
    the Settings tab's "House (POH)" section is now. If it is not, RuneLite does not give the sub-option's item, and
    nothing was scanned (note it; the safe side).
17. Wear the Max cape and use its "POH Portals" -> "Home" (or the worn "Home"): "Last looked" does NOT change
    (left out on purpose until we know where they go).

## Player answers

Some transports need something the game can't report: an `answer <words>` requirement ("Spirit tree planted at
Port Sarim", "Respawn point set to Prifddinas"; 13 different answers remain, now that spirit trees, Enakhra's Temple exits, the Mor Ul Rek vents, the
Temple of Light rope, the ring of shadows and the Slayer task questions are detected or replaced; see the next sections).
An unanswered question does not stop a route; the step card asks it with Yes / No.

- **Yes, no, or not answered yet.** Core `PlayerState` keeps each answer as yes or no (`answer(words)`: true,
  false, null). The plugin's snapshot is made with `askUnanswered(true)`: a question not answered yet counts as
  yes until the player says no (until then it could be either), so `answer X` is met and `not answer X` is
  not; each such check
  says which unanswered answers it rests on (`Check.getUnanswered()`). Answered, it is exact (yes: `answer X` met,
  `not answer X` not; no: the other way round). A "not" checks its part as it is, so any mix of "all of", "one of"
  and "not" gives the outcome with every open answer taken as yes. One exception, for every player: "Respawn point set to
  Prifddinas" counts as no until answered (core `PlayerState.isNoUntilAnswered`), so while unanswered the Lumbridge
  respawn teleports are used and the Prifddinas one is not; a yes swaps them. A part not met whatever is answered (Agility 99
  with Agility 1) makes nothing to ask. Of a "one of", a choice that needs no question wins over one that does (a
  carried rope rather than "Have you tied a rope?"), and one met now asking a question over one needing the bank.
- **The Data Tool's test players are unchanged**: without `askUnanswered` not answered means no (as before), so the
  tool's routes, its test players and the export's route comparison give the same results.
- **"No route" explanation** (core `NoRouteReasons`) uses the same checks, so it agrees with the routes: an
  unanswered answer is never a reason; one answered no is ("answer Spirit tree planted at ...").
- **Saved** per account (key `answers`): one line per answer; a yes is the words alone, exactly as files saved
  before (yes only) were written, so they load unchanged; a no is `no`, a tab, the words (a tab can't be part of an
  answer: the data is tab-separated). Sorted lines, the same words twice (any case): the last wins. Written at once
  when an answer is given (a few bytes), to the account logged in (kept in memory if none).
- **Searched again**: an answer given marks the player state changed (as an item or var change does); the usual
  availability check then compares the snapshots. Besides the outcome (met / bank / not met) and to-dos, it now also
  compares the unanswered answers each requirement rests on, so a Yes to a question the route already used searches
  again too (the new route's card no longer asks); a No leaves the transport out.
- **The card's questions**: `StepInfo.getQuestions()`, worked out on the route worker from the step's requirement
  (a network ride: both stops', e.g. a spirit tree at each end); the questions' words are a hand-written table in
  the plugin (`route/AnswerQuestions`, one per answer in the export; a test checks every answer has one).

## Detected instead of asked: collection log and spirit trees

Spirit trees, the Mor Ul Rek hot vent doors and the ring of shadows teleports do not ask the
player. Two requirement kinds replace those answers; the plugin's `UnlockWatch` detects them and remembers them per
account (`UnlockMemory`, keys `collectionLog` and `spiritTrees`). Neither is ever asked or listed in "Your answers".

- **`collection log <items>`** (fire cape 6570 for the hot vent doors; Frozen 28333, Sirenic 28331, Strangled 28330,
  Scarred 28332 tablet for the ring of shadows; item names checked in cache #2735). Learnt two ways, both
  event-driven: (1) after the game's script that draws a collection log page (`ScriptPostFired`,
  `ScriptID.COLLECTION_DRAW_LIST`), the page's items widget (`InterfaceID.Collection.ITEMS_CONTENTS`): an obtained
  item is drawn fully opaque (opacity 0), the way RuneLite's own chat commands plugin reads the pet list; skipped
  inside a house (the adventure log there may be another player's); (2) the game's chat message "New item added to
  your collection log: <name>" (only when the game's collection log notification setting is on), matched by item
  name. Only items some requirement names are kept. Not seen yet = not in the log: **the player opens the boss's
  collection log page once** (e.g. Fight Caves for the fire cape).
- **`spirit tree <place>`** (Port Sarim, Etceteria, Brimhaven, Hosidius, Farming Guild). Cache #2735: each patch is a
  multiloc on a farming varbit (4771 Port Sarim and Farming Guild, 4772 Etceteria and Brimhaven, 7904 Hosidius);
  only state 20 (object 8355 "Spirit Tree", options Travel / Talk-to / ...) can be travelled from (44 = grown but
  its health not checked yet; 8-19 growing; 21-31 diseased; 32-43 dead; the rest an empty patch). The farming
  varbits are shared between places, so a value is read for a patch only while the player is in the patch's map
  region: when the player enters the region and whenever the varbit changes there. A patch the plugin has not seen
  yet is taken from RuneLite's Time Tracking plugin's own record for the account (config group `timetracking`, key
  `<its region>.<varbit>` = `<value>:<time>`; Hosidius is its region 6967), so trees planted before Best Path was
  installed count as soon as Time Tracking has seen them. Read only (`ProfileStore.readOther`).
- Both go into the snapshot (`GameReading.collectionLog` / `spiritTrees`), and a change searches the route again
  (`AvailabilityDiff` compares them).
- In-game test list (nothing tried in the game yet):
  1. Open the Fight Caves collection log page with a fire cape logged: the hot vent doors in Mor Ul Rek are used
     without carrying a fire cape.
  2. Open the Duke Sucellus page with the frozen tablet logged: "Ring of shadows: Ghorrock Dungeon" is used.
  3. Walk to your grown Port Sarim spirit tree: routes ride from it; let it die / clear it: they stop.
  4. With Time Tracking on and a tree planted long ago, Best Path uses the tree before you visit it.

### The stored canoe axe

The axe stored at canoe stations is tracked. Requirement `canoe axe stored` (the canoe network: `one of (item axe | canoe axe stored)`); detected by
`UnlockWatch` with `player/CanoeAxe`, remembered per account (key `canoeAxe`: "stored" / "not stored"); never asked.

- Cache #2735 holds nothing for it: the canoe varps (674, 675, 2742, 5479-5481) are only station states, canoe type,
  start station and river; no client script mentions it; the station objects have no store option. Found with the
  dev-only canoe probe (`src/test/.../dev/CanoeAxeProbePlugin`) in the game: each station has an NPC with a
  "Store-axe" option (Tarquin, Sigurd, Hari, Barfy Bill, Marten, Kaiquir, Amergin, Pete, Porkai, Lara; cache NPCs
  1323-1326, 10370, 15601-15605). The same option stores the axe or hands it back; no var changes.
- Learnt from: (1) the NPC's dialogue chat message (`ChatMessageType.DIALOG`, "name|words"): "I'll hold onto that axe
  for you." = stored, "Here's your axe." = taken back; (2) "Chop-down" or "Shape-Canoe" clicked on a canoe station,
  then within 50 ticks its station varbit (1839-1842, 10527, 20254-20258) turns to a chopped (9, 10) or shaped
  (1-4) state while no "axe" group item is carried or worn = stored (the game used the stored axe).
- Not seen yet = **not known** (shown as a question mark, like stored clue
  items): like an item maybe in a STASH unit it is never assumed absent, so routes may ride canoes without an axe
  carried, and the step card shows an axe picture, "Stored" and a question mark (`NeedStatus.MAYBE`; a check once
  seen stored, and a ride is not used once seen taken back). Saved as "stored" / "not stored"; nothing saved = not
  known. The Data Tool's test players are a known "no" (`PlayerState.Builder` default), so their routes are as before.
- In the snapshot (`GameReading.canoeAxeStored`); a change searches the route again (`AvailabilityDiff`).
- In-game test list: (1) take the axe back at a station: canoe routes want an axe carried; store it: they don't;
  (2) with the axe stored and none carried, chop a canoe: still stored (no change); (3) log out and in: remembered.

## When a route is searched again

- **A home or minigame teleport turned ready again** (see above): once, at that moment.
- **Something that matters changed.** Inventory, equipment, bank, a mentioned var, a real level,
  a world hop... mark "changed". After the events settle (nothing new for 2 ticks, or at the latest 10
  ticks after the first), a snapshot is compared with the one the route was searched with. Only the
  parts some requirement mentions are compared (plus members, levels, spellbook, house, answers and
  where the rune pouch is), and only a change that gives some requirement another result (met /
  needs the bank / not met, or other to-dos) counts. So eating food or spending 1 of 500 coins does
  nothing; dropping below a toll's 10 coins searches again.
- **The player wandered off.** More than the re-route distance (config panel "Re-route when off by
  (tiles)", 10 by default) from everything still ahead for 3 ticks in a row.
  Also: off the route and more than that distance from where the player was last on it (at first
  where the route was found) for 3 ticks, even while near the route ahead, and also while a teleport is next.
- The new search runs on the route worker like any other; the old route stays on screen until the
  new one is ready (no "Calculating..." flash).

## Following the player

Each game tick the player's tile is compared with the route (a few thousand tile comparisons, never in
the overlay drawing):

- On a walk: the walk stays current while the player is within 2 tiles of it; within 1 tile of its
  last tile, a following transport becomes current ("now climb the ladder").
- On a transport: it stays current while the player is within 1 tile of where it is used; on (or next
  to) its landing, closer to the landing than to the start, the next step becomes current. When a
  transport's landing is not known, the next step becomes current when the player shows up on it.
- Anywhere on a later step (a teleport, a shortcut): that step becomes current. Never backwards.
- Wandering-off counting waits longer while a transport is current (3 + 10 + its own ticks: boats and
  cutscenes move the player around) and never counts while a teleport (usable anywhere) is next.
- Arrived: on the destination or the route's last tile, or at most the arrival range (config panel
  "Arrived within (steps)", 10 by default, 0-20) of legal steps from either (walls and no corner cutting,
  from the movement data; `ArriveArea`, worked out with the route on the worker). Never through a wall or
  fence. Counting from the route's last tile too is for a
  destination no one can stand on (a table, scenery): no tile is one legal step from it, so before only
  the exact last tile counted and the route did not dismiss itself in the game. Routes made without the
  map (the UI preview) count that many tiles from the destination only while the last step is current.
  The route is cleared and the sidebar says "Arrived".
- On a Sailing boat (the player stands in a world view other than the top-level one, RuneLite's
  public `WorldView.isTopLevel()`), following pauses: the tile RuneLite gives there is not a map tile,
  so wandering-off counting would search again from a wrong tile. A route
  asked for while on the player's own boat starts from the boat's tile in the world
  (RuneLite's `WorldEntity`), with that boat as the one the player is on (docs/SAILING_SPEC.md, part 2).
- The boats: a change of a boat varbit counts as "may matter"; the route is searched again only when
  something routes use changed (a boat's kind, speed, the waters it may enter, its teleport focus or its
  dock; a new name or cannon does not). The docks' quests are among the watched quests (docks check
  their quests).

The current step is framed in yellow in the sidebar (steps done are greyed) and drawn in yellow on
the ground and the minimap; steps done, and the walked part of the current walk, are not drawn.

### The walk ahead, redrawn from where the player stands

Every walk is found in the game's own click shape (core `GameWalk`), so a click on the drawn line is
walked along it. As the player moves, the line ahead is redrawn with the game's pathing from the tile
they stand on (`route/RouteRedraw`, rules in core `LineRedraw`), on the route worker, at most once per
change of the player's tile or of the game's destination flag (RuneLite's public
`Client.getLocalDestinationLocation()`):

- walking to a tile of the line (the flag is on it): the line up to the flag is the game's path from
  here, which is what the game walks; nothing beyond the flag changes;
- walking somewhere else: nothing changes until the player stops;
- standing still: the line from here is the game's path to the furthest tile of the walk it reaches with
  no extra steps (kept while it is at least 32 steps ahead, so a long walk's far part does not shift at
  every stop).

Only the drawn line moves (a new immutable `DrawPath` for the scene, minimap and world map overlays;
nothing is published when nothing differs). The route, its ticks and the sidebar cards stay as they
are. Route progress follows the line as drawn (a redrawn line keeps the walk's length), so a player
walking it is never "off the route". A player further than the re-route distance from the line is left
to the wandering-off check.

All other distances and tick counts are constants at the top of `RouteProgress` and `RouteFollower`.

## Known limitations

1. What is built in the house is read from the furniture while the player is in their own house (see "The house
   scan"). Not covered: the portal's "Enter" -> "Go to your house" (it needs reading the dialogue choice), and the
   Mythical max cape's teleport as a mounted mythical cape. "Civitas illa Fortis Teleport" is a nexus / portal
   chamber destination in the cache but has no row in data/manual/house-things.tsv (no transport uses it yet).
2. Saved answers are never asked again. "On a Slayer task for Fossil Island wyverns" and "Respawn point set to
   Prifddinas" can change in the game, but the saved answer does not expire.
3. The bank counts as empty until it is opened once with the plugin on. Items in the ownership cache (seen before,
   e.g. in a bank that was later emptied) do not count as banked when the bank was never seen.
4. Other storage (POH costume room, STASH units, group storage, the looting bag...) is not counted.
5. A house place value the table does not know is read as a house at that place (its name from the game's enum),
   not as "no house".
6. Some quest names in old requirement lines may not match RuneLite's names ("Recipe for Disaster - Freeing King
   Awowogei" vs RuneLite's "Recipe for Disaster - King Awowogei"). Such quests count as not started (logged once).
7. While the player is on a boat, following pauses; a route asked for there starts from the boat, where it is.
8. Changes to the Settings tab made while logged out go to the next account that logs in (replacing its saved
   settings).
9. Inside ANY house (also a friend's) routes start at the own house spot, so in a friend's house a route may say
   "use the jewellery box" (yours, not theirs) or leave through "your" portal. The game doesn't say whose house it
   is, so the own house is always assumed. Scanning stays own-house only.
10. The house portal's card says "Enter Portal" (its menu text). "Enter" asks whose house and does not let Best Path
    look at what's built; the portal's "Home" option goes straight into the own house and does.
11. Max cape: it is not known whether the worn Max cape's "Home" (worn option 1) or "POH Portals" -> "Home" always
    go into the own house, and the worn clicks cannot be told apart in game (they read the same "Home" on "Max
    cape"). The Construction cape's "Teleport" -> "Home" and a spirit tree's "Your house" are not counted either.

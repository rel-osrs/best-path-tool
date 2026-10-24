# Clues in the plugin: detection and routes

Data side: `docs/CLUES.md` (cache survey, `data/clues/clue-steps.tsv`); export: `docs/DATA_FORMATS.md`,
"Plugin export v0", `clues.tsv`.

The detection is the plugin's own (clue items, clue text, the cache's clue tables), not RuneLite's Clue Scroll
plugin. Only the public RuneLite API is used; nothing is downloaded.

## How the plugin finds the step

Code: `src/main/java/com/bestpath/clue/ClueWatcher` (pure Java, unit tested), wired in `BestPathPlugin`.
Everything runs on the client thread from game events; nothing polls.

| What the player has | How the step is found | Needs |
|---|---|---|
| Easy, medium, hard or elite clue scroll, challenge scroll, quest clue items | The item id. The export lists the clue items of every step (732 items, each checked against the cache's item param 623). An item the export doesn't know is looked up by its param 623 (`ParamID.CLUE_SCROLL`, public API `ItemComposition.getIntValue`), which holds the step's cache row. | `ItemContainerChanged` (inventory) only |
| Beginner or master clue scroll (`ItemID.TRAIL_CLUE_BEGINNER` 23182, `TRAIL_CLUE_MASTER` 19835) | One item for every step of the tier, so the item can't say which step. When the player reads the scroll, the plugin reads the text of `InterfaceID.TrailCluetext.TEXT` (event `WidgetLoaded` for group `TRAIL_CLUETEXT`, then tries for up to 3 game ticks) and matches it against that tier's step texts. | The player reads the scroll once per step |
| Strange device (`ItemID.BEGINNER_DEVICE` 23183, `MASTER_DEVICE` 19939) | A hot/cold clue of that tier. The spot is NOT known: the device's hints are not read (yet). The route goes to the nearest of the tier's spots (5 beginner, 136 master). | `ItemContainerChanged` only |

Text matching (`core/.../data/ClueData.matchText`): tags such as `<br>` and punctuation are ignored;
coordinates are compared as numbers in both wordings ("01.30N 08.11W" and "01 degrees 30 minutes
north 08 degrees 11 minutes west"); otherwise the step text equal to the scroll's text, or the longest
step text found inside it as whole words (anagrams: "This anagram reveals ... AN EARL" finds "An Earl"),
or as a last try the first sentence of a step's text when only one step has it. Steps with the very same
text are all kept: their places become alternatives.

What can't be found yet (said plainly in the sidebar):

- **Beginner map clues** (5 steps): the scroll shows a picture, not text. The map interface ids are known
  (`InterfaceID.TRAIL_MAP*`) but which picture is which step is not in our data; not guessed.
- **Elite skill challenges** for Sherlock (25 steps): no item names them and elite clues are not read by
  text (their place is always Sherlock). Master skill challenges
  and Falo the Bard's songs (master) are matched by text IF the scroll shows that text; whether it does
  (or Sherlock / Falo only say it) is not checked in game. Every such step leads to Sherlock or Falo anyway.
- **Steps no item points at** (anagram-1552, map-2260/2261, coordinate-2016, music-1888; docs/CLUES.md §1).
- The exact wording of the scroll interface could not be checked in game (no game automation is used).
  The cache's own texts and both coordinate wordings are matched; `gradlew clueRouteCheck` shows that
  every beginner and master step with text is found from its own cache text.

When the clue item leaves the inventory the detection drops to the next clue carried, or to none; a
route to a clue that is no longer carried is cleared. A beginner/master scroll keeps its step until the
player reads a new text.

## Routes

- "Use clue" (Route tab) is on when a clue with a place was detected. With the config setting
  **Automatic clue routes** on, every NEW detection becomes the destination at once (the same clue read
  again does not search again).
- Several places (alternative targets, the Wiki kill areas of elite kill steps, hot/cold spots, steps
  sharing a text): the route goes to whichever is cheapest (`PluginRouter.routeNearest`). The core
  search has one destination per search, so each place gets the route-end step and a coarse lower bound
  of its ticks; full searches run cheapest bound first and stop once the next bound is above the best
  route found; at most 24 full searches (`CoreRouteEngine.MAX_SEARCHES`, never reached on the real data).
  Equal routes: the earlier place in the data wins (the cache's own target first).
- The route's header shows the clue's title ("Emote clue (easy)"), its text, the cache's description of
  the place, how many places there were, places that can't be reached, and for emote clues that the step has a
  STASH unit (without tiles, which players don't understand). **No automatic STASH detour.** (The clue step's own
  card shows the STASH unit and the items; see "The clue step's card" below.)
- No place known (3 steps), or no route: the sidebar says so ("Best Path has no place for this step",
  "No route to any of the 6 places with the transports allowed").

## The clue step's card, the STASH unit and the items

The route's last card is the clue step itself (docs/PLUGIN_UI.md, "The clue step's card"): what to do, every item
the step needs and where the player has it, the STASH unit, and whether everything is ready. Each item row and level /
quest row has a mark in front of its picture (docs/PLUGIN_UI.md "Marks on the needs"): worn,
carried or waiting in the built, filled STASH unit: a green check (the text still says "In the STASH unit"); in the
bank: the bank symbol; missing (or a level / quest not met): a red X. Logged out: no marks (`ClueCard.mark`).

**What each step needs** (export `clues.tsv`, column `needs`): the cache's own needs (the emote outfit per slot as
worn items, any one of the slot's items or its trail item group; items to bring; skill levels; quests; a key step's
key) and hand-added ones (spade on dig steps, light in dark places). All 1,000 lines read as requirements.

**STASH unit: built** — read from the game: the step's own varbit (`clues.tsv` `stashVarbit`, a RuneLite
`VarbitID.HH_CONSTRUCTED_*`), 1 = built. Checked in cache 2735 for all 119 units (the STASH object's form varbit is
the bit the game's script 6472 tests).

**STASH unit: filled** — the game keeps no per-unit var for it (checked: no client script reads one). So the plugin
**remembers each unit, filled or empty, per account** (`clue/StashMemory`, kept by `StashTracker`; RS profile config
keys `stashFilled` = "unit:1,unit:0" by the unit's built varbit, and `stashTaken`; written like the bank, once
changes settle, only when the text changed). Three sources; the newest news wins:

1. **The game's clue helper**: it shows "Filled" (script 6624) from `VarbitID.CLUEHELPER_HIDEYHOLE_FILLED` for the
   clue in `VarPlayerID.CLUEHELPER_INFOBOX_CLUE` (the clue's cache row, which the server sets). One tick after either
   var changes, that row's unit is remembered as the bit says (any step with a STASH unit, not only the carried one).
2. **The STASH chart** (interface 493, `InterfaceID.HIDEY_HOLES`: the S.T.A.S.H chart in a house's study, and
   Watson's noticeboard, which opens the same chart (OSRS Wiki "Noticeboard (Watson's house)" rev
   15101341: object 29718, "Read", in Watson's house in Hosidius; "S.t.a.s.h chart" rev 15194550: object 41434,
   "View", a wall chart in the study, Construction 40; cache 2735 has both objects with those options, and no other
   object named like a STASH chart; which interface an object opens is decided by the server, so not checkable in the
   cache)), read when it opens, all units at once. Checked in cache 2735: the server runs script 1475 with three ints; it draws the lists
   (1477, 1478) and sets 1476 (same ints) to redraw when a STASH varp (1365-1369, 2202) or inventory 576
   (`InventoryID.HH_INV`) changes. Script 1479 decides each unit's ticks from its object: enum 1368 -> (tier, bit of
   the built varp); enum 1525 -> a slot of inventory 576 (42 units: filled when the slot holds an item; six of them
   also from one bit of the ints); enum 1440 -> (which int, bit) (the other units); object 29057 (Yanille Watchtower)
   from bit 10 or 12 of the third int. `clue/StashChart` repeats script 1479; the plugin gets the three ints from
   the public `ScriptPreFired` event (scripts 1475 and 1476; as RuneLite's own LootManager reads server-run scripts),
   the enums with `Client.getEnum`, inventory 576 with `getItemContainer`, and turns each object into its unit with
   the object definition's varbit. Units of enum 1525 are left as they were if inventory 576 never arrived. The
   Brittle Isle master unit: the chart's "empty" is not believed (OSRS Wiki "STASH" rev 15372398: the chart never
   marks it filled); its "filled" is.
3. **Putting items in or taking them out**: OSRS Wiki "STASH" (rev 15372398): "Search" on a built unit stashes the
   full set from the inventory (and worn items with the game's "STASH units take equipped items" setting); "Search"
   on a filled unit takes everything out. The cache has no client script or interface for it (the server does it).
   So (`clue/StashSearch`): a "Search" click on a built STASH unit (the clicked object's varbit is a unit's, value 1)
   arms a watch; within 50 ticks, while the player stands within 2 tiles of the unit (where the game has the player,
   `Actor#getWorldLocation`: the drawn player trails behind while running, and a take-out at Seers' was lost that
   way), the next change of what they carry plus wear decides it: items only gone = put in (filled), items only come = taken out (empty). A mixed
   change, any other click, or the 50 ticks forget the click. Chat messages are not used (their text is not in the
   cache, so it can't be checked).

The card uses the remembered value first; with none, the clue helper when it tracks this very step; otherwise
"Filled or not: not known." followed by the hint "Read Watson's noticeboard in Hosidius, or view
the S.T.A.S.H chart in your house's study, once so Best Path learns which STASH units are filled." (not for the
Brittle Isle master unit, which the chart never shows filled). A unit that isn't built holds nothing.

**Put the items back.** After an emote clue step, the route to the next clue step starts with a stop at the STASH
unit, so the player doesn't forget the items; when the clue is completed, a route to the unit is made too. This
happens only when the items came FROM that unit (taken out for that step; not when filling an empty unit from the
bank); the STASH stop stays the first card of the following routes until the items are put back or the player
presses Skip on that card.

- A take-out while carrying a clue step that uses that unit is remembered (`stashTaken`: the unit, the clue detection
  it was for, the items). It is due once that step is over: another clue step detected, or no clue carried.
- Due: the clue route asks for a **first stop** (`RouteRequest.Stop`, searched by `route/FirstStopRoute`): player ->
  the STASH unit (a card of the new kind `RouteStep.Kind.STASH`, "Stash": "Put the items back in the STASH unit
  (place)", the items, "Search the STASH unit with them in your inventory.") -> the clue's place (with its needs, so
  a bank stop for them comes after the STASH unit). The stop's card has no ticks (putting the items in isn't timed).
- No clue carried any more (the clue is done) and "Automatic clue routes" on: a route to the STASH unit only, ending
  with that card. Only at that moment (the clue went away), never at login or on routes the player sets.
- The stop is only on clue routes, and the STASH-only route only when the clue is done (both as above; routes the
  player types or picks on the map never get it). Switchable: RuneLite config panel
  "Remind to put STASH items back" (`remindStashPutBack`, default on; not in the sidebar). Off: no first stop and no
  STASH-only route (a route showing one is searched again without it / dropped at once). Take-outs are still
  remembered while it is off, so turning it on again brings back a reminder that is still open.
- Any source saying the unit is filled (the deposit seen, the chart, the clue helper) ends it: the stop leaves the
  route (a STASH-only route is dropped).
- "Skip" on the card (for good): the reminder for those items is forgotten and saved for the
  account (`stashTaken` becomes empty; `StashMemory.forgetTaken(unit)`); the unit stays remembered as empty. A
  later take-out for a clue step starts a new reminder.
  "Never use" on the card puts the stop's id (`stash-return:<unit>`) on the blacklist: routes then never stop there
  (restorable in Settings like a transport). The card's - / + tick buttons do nothing for it (UI follow-up: hide
  them, and maybe "Never use", for this card kind).
- The stop can't be reached: the route goes straight on with a header note.

**Kill for the key.** The route goes first to the NPC to kill, then to the clue location. The 12 key steps ("Kill a MALE
Barbarian ... to receive the key", then search a chest or drawers) have, in the cache's clue-helper table 19
(`TargetKey`), the NPCs that drop the key, the key item and one tile for those NPCs; the export now ships that tile,
the first NPC name and the key (`clues.tsv` `keyNpc`, `keyNpcName`, `keyItem`). While the player has no such key (not
carried, worn or in the bank), the clue route gets a first stop at that tile (`clue/KeyStop`; `RouteStep.Kind.KILL`,
badge "Kill"): "Kill for the key: Barbarian", the cache's description, "Pick up the Key (medium) it drops; then the
route goes on to the clue." Picking the key up (an inventory change) drops the stop and the route goes on to the chest.
A key in the bank: no stop (the bank stop for the step's needs gets it). "Skip" on the card skips it for this route
(no "Never use", no ticks). A STASH put-back stop, when both are due, comes first (`RouteRequest.getFirstStops`, one
search per part in `FirstStopRoute`); a kill stop that can't be reached is left out with a header note.

**Reading them**: event-driven. A var change marks the card to be made again only when it is one of the carried
step's STASH vars (`clue/StashVars`: its varbit, the six STASH varps 1365-1369 and 2202, the filled varbit's varp
2855, the clue helper's clue 3546); item and bank changes and a new clue do too; the card is then made once on the
next game tick (only while a clue is carried).

**Routing to the items** (`DestinationNeeds` on the route request, `RouteRequest.withNeeds`): the clue's items go
with its route request, except those known to wait in a filled STASH unit. Carried or worn: nothing changes. Only in
the bank: the route must start at a bank (banking only at the start; a clue whose items are only
in the bank always banks). Missing: the route still goes there and the card says what is missing (no routes to buy
things). Wired in `CoreRouteEngine.bank` (bank-detour work): `r.getNeeds().fromBank(player)` non-empty = the
bank-first route only (to the bank with what the player carries, the bank stop, then on with the bank's items),
never the faster of the two; the bank card lists those items after what the way on needs ("Bank at Draynor Village
bank" and a box with each item's picture, name and how many to take out). Only when no bank this player may use can be reached first does the
route go straight there, with the header "No bank can be reached first; only in the bank: ...". The route follower
searches again when those bank-only items change (taken out, put in).

## Numbers (cache 2735, Maxed / Fresh account test players from Lumbridge, `gradlew clueRouteCheck`)

- `clues.tsv`: 1,005 steps, 135,667 bytes, 42 KB inside the jar (jar 1.54 MB); 0.36 MB of heap.
- Detection: all 149 master and 29 of 34 beginner steps found from their own text (the 5 maps not);
  every easy-elite step with an item found from it.
- 827 detections with a place: Maxed reaches 799, Fresh account 609 (no quests, unlocks or levels).
- Search time: one place median 22-24 ms, p95 143-355 ms, worst 0.9 s (fairy-ring islands where the
  route ends near the place); several places median 84-176 ms; master hot/cold (136 spots) 0.8-1.3 s,
  of which the full searches are 1-2 per clue; the rest is the per-spot lower bound.

## In-game checks for the STASH memory

1. Open the S.T.A.S.H chart in your house's study: the clue card of a carried emote clue then says "built and
   filled" or "built, empty" for its unit (no longer "not known"), and it stays so after logging out and in.
2. Put an emote clue's outfit into its STASH unit ("Search"): the card turns to "built and filled" without opening
   the chart; take it out again: "built, empty".
3. With an emote clue whose outfit is in its unit: take it out, do the emote, get the next clue step. The next
   route's first card is "Put the items back in the STASH unit (...)" with the items; put them back there: the card
   leaves the route.
4. Same, but finish the clue (casket): the route goes to the STASH unit only.
5. Press "Skip" on that card: it goes away for the rest of the game session.
6. Brittle Isle master unit (if built and filled): opening the chart must not make it "empty".
7. Not checked in game yet: whether Watson's noticeboard uses the same chart (then it counts too), and whether the
   clue helper vars arriving at login could ever be older than a deposit the plugin saw.

## Not done yet

1. Hot/cold: the route goes to the nearest possible spot. The device's hints are not read; reading them (chat
   messages / `VarPlayerID.TRAIL_HOTCOLD_LAST_CHECK_COORD`) needs checking in game first.
2. Beginner map clues: there is no "map interface -> step" table (5 rows would need checking in game).
3. The game has its own clue helper varps (`VarPlayerID.CLUEHELPER_INFOBOX_CLUE` 3546 and others). If one
   holds the current step's row it would identify beginner/master steps without reading the scroll; its
   meaning is not documented, so it is not used (undocumented mechanics are not guessed).
4. Automatic clue routes on login: a clue already carried counts as newly detected and routes at once.
5. Kill steps: the route goes to the nearest Wiki area (one tile per area) as in docs/CLUES.md §5 Q1; task-only
   areas are not left out yet.
6. The search for many places is one full search per place (cheapest bound first), not one multi-destination
   search in core; a multi-destination search would be faster for hot/cold but changes the core search.

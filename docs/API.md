# Best Path API for other plugins

Other RuneLite plugins can ask Best Path to show a route, clear it, find a route
without showing it, and hear what happened to their route. This page is for
those plugins' developers.

**API version 1.** The names are in
`api/src/main/java/com/bestpath/api/BestPathApi.java`. It is plain Java with a
BSD-2-Clause licence, so you can copy it into your plugin.

## How it works

Plugin Hub plugins can't use each other's classes. They talk through RuneLite's
`PluginMessage` event instead: you post one on the `EventBus`, and every plugin
that subscribes to `PluginMessage` receives it. Best Path's namespace is
**`bestpath`**.

**The messages are the Shortest Path plugin's.** They have the same names,
keys and kinds of values as its plugin messages (Shortest Path 1.20.8).

- If your plugin already talks to Shortest Path (namespace `shortestpath`), it
  works with Best Path when you post the same message with the namespace
  `bestpath`.
- To support both plugins, post the message to both namespaces.
- Best Path adds extra keys (`source`, `title`, `notes`, `steps`, ...) and
  extra messages (`status`, `progress`, `ping`). Shortest Path callers just
  ignore them.

You need nothing but RuneLite itself: no reflection and no dependency on Best
Path. If Best Path isn't installed, or is switched off, nothing happens.

Separately, Best Path also listens to Quest Helper's `shortestpath` `path` and
`clear` messages (switched by the same "Routes from other plugins" setting).
That is not part of this API.

## Places

A place is one of:

- a RuneLite `WorldPoint`;
- a whole number packed the way Shortest Path packs it: x in bits 0-14, y in
  bits 15-29 and plane in bits 30-31 (`BestPathApi.pack(x, y, plane)`). -1
  means "no place".

A **target** can be one place, or a `Set` or `List` of places. With several
places, Best Path goes to whichever is quickest to reach. One message may hold
at most 5,000 places. If any place in a message isn't a place, the whole
message is refused; nothing is done halfway.

Best Path's answers always give places as `WorldPoint`s.

## Showing a route: `path`

```java
@Inject private EventBus eventBus;

Map<String, Object> data = new HashMap<>();
data.put("target", new WorldPoint(3222, 3218, 0));   // or a Set of places
data.put("source", "My Plugin");                     // optional, recommended
data.put("id", "step-12");                           // optional, sent back to you
eventBus.post(new PluginMessage("bestpath", "path", data));
```

| Key | Meaning |
|---|---|
| `target` | Where to go (see "Places"). Required. |
| `source` | Your plugin's name. It is shown with the route ("Destination from My Plugin"), and `clear` only clears this source's route. Default: "another plugin". |
| `id` | Any value. It comes back unchanged in every answer about this route. |
| `title` | The route header instead of "Destination from ...". Up to 100 characters. |
| `notes` | One text line, or a list of up to 5, shown under the header. |
| `start` | Read for Shortest Path compatibility, not used: a shown route always starts where the player stands. A `start` that isn't one place makes the message invalid. |
| `config` | Shortest Path's per-request settings: ignored. Best Path always uses the player's own transport settings. |

The route replaces whatever route is shown, as any new destination does. A
later destination replaces it the same way, whether the player sets one or a
clue or another plugin does. Sending the same places from the same source
again while that route is shown does nothing, so it is safe to send each step's
place more than once.

The player can turn off **Routes from other plugins** ("Routes and world map",
on by default, in both the config panel and the sidebar's Settings tab). When
it is off, `path` and `clear` change nothing, and `path` is answered
`DISABLED`.

## Clearing it: `clear`

```java
eventBus.post(new PluginMessage("bestpath", "clear", Map.of("source", "My Plugin")));
```

This clears the route shown only if your source set it. It never clears the
player's own route or another plugin's. Without a `source` it clears only a
route set without a `source`.

## Hearing about your route: `status` and `progress`

Best Path sends these only to the plugin whose route it is (same `id` and
`source`).

```java
@Subscribe
public void onPluginMessage(PluginMessage m)
{
    if (!"bestpath".equals(m.getNamespace())) return;
    Map<String, Object> d = m.getData();
    if ("status".equals(m.getName()) && "step-12".equals(d.get("id")))
    {
        String state = (String) d.get("status");   // FOUND, NO_ROUTE, ARRIVED, ...
    }
}
```

`status` has these keys: `id`, `source`, `status`, `message` (text for
people) and `apiVersion`. A `FOUND` status also carries the route keys listed
under `query` below.

| `status` | When |
|---|---|
| `FOUND` | The route was found and is shown. It is sent again after each new search, for example when the player leaves the route or their items change what they can use. |
| `NO_ROUTE` | No route with the transports the player can use and allows. `message` says why. |
| `NO_DATA` | Best Path's map data hasn't loaded yet (the first second or so). |
| `ERROR` | The search failed, or the player isn't logged in (`message`). |
| `INVALID` | The message wasn't understood (`message`). Nothing changed. |
| `DISABLED` | The player turned off "Routes from other plugins". Nothing changed. |
| `ARRIVED` | The player arrived. Sent once, even if the player keeps the route shown after arriving. |
| `CLEARED` | The route was cleared, by the player or by your `clear`. |
| `REPLACED` | Another destination replaced it. |

After `ARRIVED`, `CLEARED` or `REPLACED` you hear nothing more about that
route.

`progress` (`id`, `source`, `stepIndex`, `apiVersion`) is sent when the player
moves on to another step of your route. `stepIndex` indexes the `steps` list
of the last `FOUND`.

## Finding a route without showing it: `query`

```java
Map<String, Object> data = new HashMap<>();
data.put("id", 42);
data.put("target", new WorldPoint(3093, 3493, 0));
data.put("start", new WorldPoint(3222, 3218, 0));   // optional; default: where the player stands
eventBus.post(new PluginMessage("bestpath", "query", data));
```

This is answered by exactly one `result` message with the same `id`. The keys
of a `result` (and of a `FOUND` status):

| Key | Meaning |
|---|---|
| `id` | As sent. |
| `reached` | `true` when a route was found. |
| `start` | Where the search started (`WorldPoint`). |
| `target` | The place the route goes to; with several targets, the one chosen. |
| `path` | The route's tiles in order (`List<WorldPoint>`): walked tiles and where each transport is used and lands. |
| `transports` | The transports used, in order: a `List` of `Map`s with `origin`, `destination`, `objectInfo` ("Climb-up Ladder 16683" when an object is clicked, otherwise null) and `displayInfo` (Best Path's step text, "Climb up the ladder"). Teleports, ladders, stairs, shortcuts, doors and boats. |
| `closest` | With a route, the place reached; otherwise null (Best Path doesn't work out the nearest place it could reach). |
| `cost` | The route's length in game ticks (walking is 1 tile per tick); -1 without a route. |
| `reason` | `TARGET_REACHED`, `SEARCH_EXHAUSTED` (no route), `INVALID`, `ERROR`, `NO_DATA`, `NOT_LOGGED_IN` or `BUSY` (too many queries waiting; ask again later). |
| `steps` | Best Path's steps: a `List` of `Map`s with `kind` (WALK, TRAVERSAL, DOOR, TELEPORT, BOAT, SAIL, BANK, STASH, KILL), `title`, `ticks` and `end` (where the player is after the step). |
| `message` | Text for people. |
| `apiVersion` | 1. |

The search uses the player's own items, levels, quests and transport settings,
even when it starts somewhere else. Queries wait behind the route being
searched, and at most 4 run or wait at once.

## Where does the route go: `getTarget`

```java
eventBus.post(new PluginMessage("bestpath", "getTarget", Map.of("id", 1)));
```

This is answered by `currentTarget`:

| Key | Meaning |
|---|---|
| `id` | As sent. |
| `set` | `true` when a route is shown, whoever set it. |
| `start` | Where the route was searched from. Only when `set` is true. |
| `target` | A list of every place the route may go to. Only when `set` is true. |

Unlike Shortest Path, there is no `config` key here: Best Path doesn't take
per-request settings.

## Is Best Path there: `ping`

```java
eventBus.post(new PluginMessage("bestpath", "ping", Map.of("id", "hello")));
```

This is answered by `pong` (`id`, `apiVersion`).

## Threads

- You can post from any thread.
- Best Path reads your message on the client thread and searches on its own
  worker thread, never the client thread.
- It posts its answers on the client thread, so your `onPluginMessage` runs
  there, as it does for other RuneLite events.
- The maps Best Path sends can't be changed.

## Stability and versioning

- Every answer carries `apiVersion` (now 1).
- Adding a key, a message or a value (a new `status` or `reason`) keeps the
  version. Ignore what you don't know.
- Removing one, or changing what one means, raises the version. The old
  messages would keep working alongside the new ones for a while, and this
  page would say until when.
- The Shortest Path names (`path`, `clear`, `query`, `result`, `getTarget`,
  `currentTarget`, `target`, `start`, `id`, `reached`, `path`, `transports`,
  `closest`, `cost`, `reason`) are kept as Shortest Path has them.

## Not offered

These may change in a later version:

- per-request transport settings (`config`);
- a shown route that starts somewhere other than the player;
- stops along the way;
- Shortest Path's `transports` debug message (Best Path puts the transports in
  `result` and in `FOUND`).

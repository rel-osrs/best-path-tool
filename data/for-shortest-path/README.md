# Transports for the Shortest Path developers

`new-transports.tsv` lists the transports in our data (`data/manual/changes.jsonl`) that the
Shortest Path files in `data/shortest-path/` do not have, one row per direction, in their
`transports.tsv` column layout. It is meant to be given to the Shortest Path developers for
their own review, to thank them for letting us use their data.

- Made by a separate development script that is not published (it is run with the Data Tool open, so the menu
  options can be read from the cache). It is rewritten in full every time; do not edit it by hand.
- A row matches Shortest Path when both ends are within 2 tiles of one of their rows. A comment
  line flags it when they have the same object nearby with other ends.
- The comment above each transport gives our source (wiki page and revision, Shortest Path or the
  tool's suggestion) and the region's requirement notes. Requirement columns are left empty:
  ours are notes, not data yet.
- `?` as menu option: the way back is another object (e.g. the staircase on the plane above) that
  our record does not name.
- Tick costs marked "estimated" are the tool's defaults, not measured.
- Rows taken from the OSRS Wiki name the page and revision; Wiki content is CC BY-NC-SA, so what
  is passed on needs care (see `docs/DATA_SOURCES.md`).

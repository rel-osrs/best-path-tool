# Transports from the Shortest Path plugin

Copied unchanged from the Shortest Path RuneLite plugin:

- Repository: https://github.com/Skretzo/shortest-path
- Commit: `cbfc16acc96ea54360c67ea9f0a1f0b42ff9592b` (copied 2026-10-10)
- Folder: `src/main/resources/transports/` (24 `.tsv` files; their Leagues-only
  `seasonal_transports.tsv` left out)
- Rows left out when read (files unchanged): those needing only the Trinket of
  fairies (item 28564, a Leagues item; 108 rows in `teleportation_items.tsv`).
  See `ShortestPathTransports.LEFT_OUT_ITEMS`.
- Licence: BSD 2-Clause, kept beside the files as `LICENSE` (the licence
  requires keeping it with any copy).
- Permission: the plugin's developers gave permission for BestPath to use
  their transports.

These are NOT ground truth. They are a separate transport set that can be
switched on or off; our own confirmed transports stay separate. Do not edit
these files by hand: to update, copy a newer commit over them and record the
commit here. Corrections belong in our own data (changes.jsonl).

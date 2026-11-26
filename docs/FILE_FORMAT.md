# Export / import file format

SerieusTracker exports your list to a small JSON file and imports the same format. The format is designed to be:

- **easy for a human to read**, with one show per line,
- **easy to write by hand** or to generate from another app's data,
- **minimal**: it stores only what you created. Posters, episode lists, titles and air dates are downloaded again after an import.

This file is the specification. The reference implementation is [`ShowListFile.kt`](../app/src/main/java/com/mlw/serieustracker/data/ShowListFile.kt).

> This is **not** the app's internal storage format (`library.json`), which also caches posters and episode data and may change between versions. This format is the stable, documented one.

---

## Quick example

```json
{
  "app": "SerieusTracker",
  "version": 1,
  "shows": [
    {"name": "Frieren: Beyond Journey's End", "source": "anilist", "id": 154587, "seen": {"1": "1-12, 14"}},
    {"name": "Severance", "source": "plex", "id": "5d9c0866a1b2c3d4", "seen": {"1": "1-9", "2": "1-4, 6"}, "dropped": true, "lastWatched": "2026-11-20T21:14:00Z"},
    {"name": "Cowboy Bebop", "source": "anilist", "seen": "1-26"}
  ]
}
```

The smallest useful file:

```json
{"shows": [{"source": "anilist", "name": "Cowboy Bebop"}]}
```

---

## File structure

The file is UTF-8 encoded JSON. A leading byte order mark is tolerated.

| Part      | Type   | Required | Description                                                             |
|-----------|--------|----------|-------------------------------------------------------------------------|
| root      | object | yes      | An object with a `shows` array. A bare array of shows is also accepted. |
| `app`     | string | no       | Written on export (`"SerieusTracker"`). Ignored on import.              |
| `version` | number | no       | Written on export (currently `1`). Ignored on import.                   |
| `shows`   | array  | yes      | One object per show.                                                    |

Unknown fields are ignored everywhere, so you can add your own notes to a file (for example `"comment"`).

---

## Show entries

| Field         | Type                    | Required             | Description                                                                          |
|---------------|-------------------------|----------------------|--------------------------------------------------------------------------------------|
| `source`      | string                  | yes                  | `"anilist"` for anime, `"plex"` for other series. Case does not matter.              |
| `id`          | number or string        | one of `id` / `name` | The show's id at the source. See [Ids](#ids).                                        |
| `name`        | string                  | one of `id` / `name` | Title. Informational when an `id` is given; used to look the show up when it is not. |
| `seen`        | object, string or array | no                   | The seen episodes. See [Seen episodes](#seen-episodes). Default: nothing seen.       |
| `dropped`     | boolean                 | no                   | `true` if the show is dropped. Default `false`.                                      |
| `lastWatched` | string                  | no                   | When you last watched an episode. See [Dates](#dates). Default: unknown.             |

An entry that cannot be used is **skipped** and counted in the message shown after the import (see [Errors](#errors)).

### Ids

| Source    | What the id is                                             | Where to find it                                                                                                                            |
|-----------|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `anilist` | A positive whole number, no leading zero, at most 9 digits | The number in the show's AniList address: `https://anilist.co/anime/154587` → `154587`                                                      |
| `plex`    | Plex's own metadata id, a hexadecimal string               | The `metadata/` part of the `key` parameter in the show's page address on app.plex.tv. The easiest way to get it is to export from the app. |

For AniList the id is written as a bare number, for Plex as a string. When reading, a number is accepted for either and converted to text.

You do not have to find ids by hand: [give a name instead](#looking-up-names).

### Seen episodes

`seen` describes which episodes you have seen. The normal form maps a **season number** to the **episodes of that season**:

```json
"seen": {"1": "1-12, 14", "2": "1-4, 6"}
```

Episodes are written as **ranges**:

| Written             | Means                     |
|---------------------|---------------------------|
| `"7"`               | episode 7                 |
| `"1-12"`            | episodes 1 to 12          |
| `"1-12, 14, 16-18"` | 1 to 12, 14, and 16 to 18 |

Details of the range syntax:

- Parts are separated by commas, semicolons or spaces.
- The dash can be `-`, `–` or `—`, with or without spaces around it (`1 - 12` is fine).
- Episode numbers start at 1. A part that is not a number or a range, a reversed range such as `5-3`, or a range with a number below 1 is ignored.
- The highest accepted episode number is 20,000. A range is cut off there and a single number above it is ignored, so a typo cannot use up the phone's memory.

Other accepted shapes, all meaning **season 1**:

| Shape                                 | Example                |
|---------------------------------------|------------------------|
| A range string                        | `"seen": "1-26"`       |
| An array of numbers                   | `"seen": [1, 2, 3, 5]` |
| An array of numbers and range strings | `"seen": [1, "3-5"]`   |

Inside the season object, the values can also be a single number or an array: `{"1": 5, "2": [1, 2, 3]}`.

Season keys must be positive whole numbers. Other keys are ignored.

AniList shows only have season 1 in the app (AniList treats each season as a separate show), so `"seen": "1-26"` is the natural form for anime. Plex shows use the season numbers shown in Plex. Specials (season 0) are not tracked.

### Dates

`lastWatched` is optional and is used to order **Watch Next** (most recently watched first). It is written as text and accepts:

| Form                                          | Example                       |
|-----------------------------------------------|-------------------------------|
| Date and time in UTC (what the app writes)    | `"2026-11-20T21:14:00Z"`      |
| Date and time with an offset                  | `"2026-11-20T22:14:00+01:00"` |
| Date only (midnight in the phone's time zone) | `"2026-11-20"`                |

A value that cannot be read is treated as unknown.

---

## What export writes

- One show per line, sorted by name, so the file is easy to scan and to compare between exports.
- `id` is a number for AniList and a string for Plex.
- Seasons are in ascending order and episodes are written as compact ranges.
- `dropped` is written only when it is `true`, and `lastWatched` only when it is known.
- Nothing else is written: no posters, episode titles, air dates or update times.

---

## What import does

Import **only adds**. Nothing is removed and no episode is unmarked.

| Situation                               | Result                                                                                                                                                                                   |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| The show is not in your list yet        | It is added with the `seen`, `dropped` and `lastWatched` from the file.                                                                                                                  |
| The show is already in your list        | The seen episodes from the file are **added** to yours, and `lastWatched` becomes the more recent of the two. Its status (dropped or not), poster and episode data are left as they are. |
| The same show appears twice in the file | The entries are merged one after the other.                                                                                                                                              |

Shows are recognised by source and id, so the same show is never added twice.

After the import the app downloads poster, title and episode data for the new shows in the background. Until then a new show is displayed with the `name` from the file (or its id if there is none).

When the import is done, the app reports how many shows were added and how many episodes were newly marked as seen, how many entries could not be read, and which names could not be found.

### Looking up names

If an entry has a `name` but no `id`, the app finds the id for you:

1. It searches the entry's `source` (AniList or Plex) for the name.
2. It accepts the first search result whose title **matches exactly**, ignoring upper/lower case and punctuation. `Attack on Titan`, `attack on titan` and `Attack on Titan!` all match the same title.
3. If nothing matches exactly, the entry is **not imported** and its name is listed afterwards. The app never guesses, so a wrong show never gets your progress.

Things to know:

- Plex lookups need a Plex sign-in. Without one the entries are reported as not found, together with the error.
- AniList limits how fast its public API can be used. Large lists with names only can take a while, and the app shows the progress.
- **Current limitation:** for AniList the title that is compared is the English title if the show has one, otherwise the romaji title. A romaji name for a show that also has an English title (for example `Sousou no Frieren` for *Frieren: Beyond Journey's End*) is not matched. Use the English title, or give the `id`. Matching against all title variants is planned.

When a name has been looked up, the title of the matched result replaces the name from the file.

---

## Errors

| Message                                          | Cause                                                                                                                                                                |
|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| "The file could not be read as a list of shows." | The file is not valid JSON, or has no `shows` array. Nothing is imported.                                                                                            |
| "N entries in the file could not be read."       | Those entries have an unknown or missing `source`, have neither `id` nor `name`, or have an AniList `id` that is not a plain number. The other entries are imported. |
| "Could not find: …"                              | These names did not match a search result exactly. The other entries are imported.                                                                                   |

A malformed `seen` or `lastWatched` does not make an entry fail: the unreadable parts are ignored. If a number of marked episodes looks too low after an import, check the `seen` text for typos.

---

## Converting a list from another app

Most apps can export a list as CSV or JSON with at least a title and the number of watched episodes. Converting that to this format takes a few lines. This Python example turns a CSV file with the columns `title` and `watched` into an import file for anime:

```python
import csv
import json

shows = []
with open("my_list.csv", newline="", encoding="utf-8") as f:
    for row in csv.DictReader(f):
        watched = int(row["watched"])
        entry = {"name": row["title"], "source": "anilist"}
        if watched > 0:
            entry["seen"] = f"1-{watched}"
        shows.append(entry)

with open("serieustracker-import.json", "w", encoding="utf-8") as f:
    json.dump({"shows": shows}, f, ensure_ascii=False, indent=2)
```

Copy the file to the phone and use **Settings → Data → Import**. Shows that were not found are listed afterwards, and you can follow those from the search screen.

If your other app uses AniList, its export contains AniList ids. Using those as `id` avoids the name lookup altogether and is more reliable.

---

## Compatibility

- Fields may be added in future versions. Because unknown fields are ignored on import, adding a field does not break reading of the file.
- `version` is `1`. It is reserved for a future incompatible change and is not checked on import today.

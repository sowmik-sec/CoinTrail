# CoinTrail backup format

CoinTrail stores a full, human-readable backup of your data as a versioned JSON file. The same
document is used for the timestamped snapshots written to your own Google Drive (`Backup now` and a
weekly automatic backup) and for the manual **Export to file** / **Import from file** actions in
Settings, which can save it anywhere via the system document picker.

The format is designed to satisfy CoinTrail's ten-year rule (SPEC §7, §8): plain JSON, integer money,
ISO timestamps, no compression and no binary blobs, so a future version of the app — or a person with
a text editor — can still read it. **Never** change this format in a way that makes an existing file
unreadable; add a new version instead.

## Envelope

```json
{
  "format": "cointrail-backup",
  "version": 1,
  "exportedAt": "2026-10-02T21:30",
  "tables": {
    "expenses": [ ... ],
    "categories": [ ... ],
    "paymentMethods": [ ... ],
    "budgets": [ ... ],
    "recurring": [ ... ]
  }
}
```

| Field | Type | Meaning |
|---|---|---|
| `format` | string | Always `cointrail-backup`. A reader must refuse a document whose `format` is missing or different. |
| `version` | integer | Format version. This document describes version `1`. A reader must refuse a version it does not understand rather than partially applying it. |
| `exportedAt` | string | When the file was written, as an ISO-8601 local date-time (`yyyy-MM-ddTHH:mm:ss`, with trailing zero components omitted). Informational only — restore does **not** use it for ordering. |
| `tables` | object | The five data tables, keyed by name. A missing table is treated as empty; unknown fields and unknown tables are ignored, so a newer app can add fields without breaking an older reader. |

## Conventions

- **Money** is always an **integer number of paisa** (`1 taka = 100 paisa`). A `৳12.50` expense is
  stored as `1250`. Floating point is never used.
- **Timestamps** are ISO-8601 local date-times, e.g. `"2026-10-02T21:30:00"`. Dates that carry no
  time (a recurring series' start month) use `yyyy-MM`.
- **IDs** are UUID strings generated on the device. Preset rows use stable ids (`preset-food`,
  `pm-cash`, …) so importing into another device never duplicates them.
- **Tombstones.** Deletable rows are never physically removed: they carry `deletedAt` (and an updated
  `updatedAt`) so the deletion can be replayed. A null `deletedAt` means the row is live.
- **`updatedAt` on every row** is the sort key used by restore's last-write-wins merge (see below).

## Tables

### `expenses`

| Field | Type | Nullable |
|---|---|---|
| `id` | string | no |
| `amountPaisa` | integer | no (positive) |
| `categoryId` | string | no |
| `note` | string | yes |
| `paymentMethodId` | string | yes |
| `occurredAt` | ISO date-time | no |
| `createdAt` | ISO date-time | no |
| `updatedAt` | ISO date-time | no |
| `deletedAt` | ISO date-time | yes |

### `categories` and `paymentMethods`

Same shape; categories and payment methods are hidden, never deleted, so they have no `deletedAt`.

| Field | Type | Nullable |
|---|---|---|
| `id` | string | no |
| `name` | string | no |
| `isPreset` | boolean | no |
| `isHidden` | boolean | no |
| `sortOrder` | integer | no |
| `updatedAt` | ISO date-time | no |

### `budgets`

| Field | Type | Nullable |
|---|---|---|
| `id` | string | no |
| `categoryId` | string | yes (null = the overall monthly budget) |
| `monthlyLimitPaisa` | integer | no (positive) |
| `updatedAt` | ISO date-time | no |
| `deletedAt` | ISO date-time | yes |

### `recurring`

| Field | Type | Nullable |
|---|---|---|
| `id` | string | no |
| `amountPaisa` | integer | no (positive) |
| `categoryId` | string | no |
| `note` | string | yes |
| `paymentMethodId` | string | yes |
| `dayOfMonth` | integer | no (1–31) |
| `startMonth` | `yyyy-MM` | no |
| `lastGeneratedMonth` | `yyyy-MM` | yes |
| `isPaused` | boolean | no |
| `updatedAt` | ISO date-time | no |
| `deletedAt` | ISO date-time | yes |

## Restore semantics

Importing a file — whether from Drive or the document picker — does **not** overwrite your data.
Every row is merged **last-write-wins per record**, keyed as follows:

- `expenses`, `categories`, `paymentMethods`, `recurring`: by `id`.
- `budgets`: by scope (`categoryId`, or the overall scope), because a budget's identity is the scope
  it governs.

For a given key the winner is, in order: the row with the greater `updatedAt`; then a tombstone over
a live row; then the lexicographically greater serialized row (a stable, symmetric tie-break). Rows
present on only one side survive.

This makes restore **idempotent** and safe on both a **fresh** device (the file fills the empty
database) and an **existing** device (newer local edits win, nothing is lost). Importing the same
file twice changes nothing the second time. (See `domain/sync/SyncMerge.kt`.)

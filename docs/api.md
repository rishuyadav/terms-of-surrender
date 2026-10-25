# API contract

The browser calls `/api` through the Next.js same-origin proxy. All responses containing session/report data are `Cache-Control: no-store, private`. API errors use RFC 9457-style Problem Details with a stable `code` and safe `detail`.

## Session

`GET /api/session`

Creates or refreshes a private anonymous browser cookie. Returns:

```json
{
  "mode": "RULE_BASED_PREVIEW",
  "retentionHours": 24,
  "legalContextAvailable": false,
  "maxCharacters": 80000
}
```

The cookie is HttpOnly, SameSite=Lax, scoped to `/api`, and Secure when configured. Only its SHA-256 hash is stored with private records. Clearing cookies loses access to anonymous reports; this is intentional.

## Submit

`POST /api/analyses` with `multipart/form-data`

| Field | Values |
| --- | --- |
| `text` | At least 80 and at most 80,000 characters; mutually exclusive with file |
| `file` | One text-based PDF, up to 10 MB and 40 pages |
| `jurisdiction` | `US`, `INDIA`, `UNSPECIFIED` (default) |
| `category` | `CONSUMER`, `FREELANCE`, `OTHER` (default) |
| `region` | Optional descriptive text, up to 80 characters |

Requires the session cookie and an `Origin` exactly matching `ALLOWED_ORIGINS`. Origin-less CLI mutations are intentionally rejected; explicitly set the trusted origin when testing.

Returns `202 Accepted`, a `Location` header, and an analysis snapshot. Snapshot fields:

- `id`, `status`, `stage`, `version`.
- `document`: canonical text, source blocks, page count, extraction warnings, fingerprint.
- `context`: user-supplied location and category; not a legal applicability determination.
- `report`: null until complete, then mode, engine version, findings, limitations.
- `errorCode`: safe error code for failed jobs.
- `createdAt`, `expiresAt`: ISO 8601 timestamps.

Status transitions: `QUEUED → PROCESSING → COMPLETED | FAILED`. Processing has a ten-minute lease. Abandoned jobs fail explicitly rather than being retried indefinitely and incurring additional model charges.

## Read and watch

- `GET /api/analyses/{uuid}` retrieves the current persisted snapshot.
- `GET /api/analyses/{uuid}/events` opens a bounded SSE connection.

SSE sends named `snapshot` events when the persisted version changes, with the version as the event ID. Connections heartbeat and terminate on final states; clients can reconnect or retrieve the snapshot directly. An `unavailable` event signals deletion or expiry. This initial release streams state snapshots, not speculative token output or clause-by-clause findings.

All operations verify ownership, including streams. Unknown, expired, and other-session reports return the same `404` to avoid revealing existence.

## Delete

`DELETE /api/analyses/{uuid}`

Requires the session cookie and trusted Origin. Returns `204`. Deletes the database report and canonical source; in-flight workers cannot recreate a deleted row. A worker that has already loaded the document may finish its current processing in memory. Deletion cannot retract content already sent to an external provider or erase downloaded copies. Minimal hashed-session admission timestamps remain for at most one hour for rate limiting and contain no document content.

## Evidence offsets

Offsets are absolute UTF-16 code-unit positions in the canonical document text, matching Java `String` and browser JavaScript indexing. A finding references a stable source block, exact quote, and half-open `[start, end)` span. Model proposals with nonexistent or ambiguous quotes are withheld. Canonical text is normalized extraction, not a byte-identical reproduction of the uploaded file.

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

| Field          | Values                                                                  |
| -------------- | ----------------------------------------------------------------------- |
| `text`         | At least 80 and at most 80,000 characters; mutually exclusive with file |
| `file`         | One text-based PDF or `.docx`, up to 10 MB; PDFs up to 40 pages         |
| `jurisdiction` | `US`, `INDIA`, `UNSPECIFIED` (default)                                  |
| `category`     | `CONSUMER`, `FREELANCE`, `OTHER` (default)                              |
| `region`       | Optional descriptive text, up to 80 characters                          |

Requires the session cookie and an `Origin` exactly matching `ALLOWED_ORIGINS`. Origin-less CLI mutations are intentionally rejected; explicitly set the trusted origin when testing.

Returns `202 Accepted`, a `Location` header, and an analysis snapshot. Snapshot fields:

- `id`, `status`, `stage`, `version`.
- `document`: canonical text, source blocks, page count, extraction warnings, fingerprint.
- `context`: user-supplied location and category; not a legal applicability determination.
- `report`: null until complete, then mode, engine version, findings, limitations.
- `errorCode`: safe error code for failed jobs.
- `createdAt`, `expiresAt`: ISO 8601 timestamps.

Status transitions: `QUEUED → PROCESSING → COMPLETED | FAILED`. Processing has a ten-minute lease. Abandoned jobs fail explicitly rather than being retried indefinitely and incurring additional model charges.

### DOCX import limits

DOCX files are selected by a case-insensitive `.docx` filename and verified to contain a transitional WordprocessingML body in a ZIP package; MIME claims are not trusted. Strict OOXML documents must first be exported as ordinary Word DOCX or text-based PDF. The importer reads body paragraphs and tables only. It never downloads relationships, executes macros, or writes archive entries to disk. Macro-bearing packages and body tracked changes are rejected with `UNREADABLE_DOCX`; accept or reject revisions in Word before uploading.

Extraction budgets: 256 ZIP entries, 16 MiB total expanded data, 4 MiB body XML, XML depth 128, 500,000 XML events, and the existing 80,000-character text limit. DTDs and XML entities are rejected. Expansion budgets are enforced while reading, not from untrusted ZIP size metadata. Unsupported, malformed, encrypted, or over-budget DOCX input returns a safe `400` Problem Detail. This is bounded text import, not full OOXML conformance validation or a hostile-file sandbox.

Every DOCX snapshot includes warnings about omitted layout/content: headers, footers, footnotes, comments, images, text boxes, automatic numbering, and embedded/alternate content are not reconstructed. Field results may be stale and hidden text may be included. External hyperlinks contribute only visible body text. Compare with the original before relying on findings.

`document.sourceType` can now be `TEXT`, `PDF`, or `DOCX`. For DOCX, the retained `pageCount: 1` and block `page: 1` identify a single logical extraction segment for compatibility with the current evidence schema—not a Word page. Only PDF references have physical page numbers; clients must label DOCX/TEXT references as extracted text. Original uploads are not retained.

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

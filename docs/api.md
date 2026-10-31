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

Session `mode` describes deployment capability, not an individual quota balance. Remaining demo balances are never returned. The report snapshot is authoritative about the mode actually used.

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

The browser supplies a UUID `Idempotency-Key`, reused when retrying the same submission. In public demo mode the server-side Vercel Proxy attaches a short-lived HMAC attestation bound to the visitor IP, method, path, hashed session, nonce and request key. Render never trusts a caller-supplied `X-Forwarded-For` or Vercel-looking IP header alone. Replayed attestations return `409 REQUEST_REPLAYED`; retries through Vercel receive fresh attestations. Reusing a request key with different input returns `409 REQUEST_KEY_REUSED`.

PDF/DOCX parsing runs outside the API JVM. File extraction has two slots per API instance and a 20-second worker deadline. A busy parser returns `429 PARSER_BUSY`, deadline expiry returns `422 PARSER_TIMEOUT`, and a failed/unavailable worker returns `503 PARSER_UNAVAILABLE`. No analysis record or admission-ledger entry is created when extraction fails. Plain-text analysis remains independent of file workers. See [the process boundary](parser-isolation.md) for budgets and limitations.

Returns `202 Accepted`, a `Location` header, and an analysis snapshot. Snapshot fields:

- `id`, `status`, `stage`, `version`.
- `document`: canonical text, source blocks, page count, extraction warnings, fingerprint.
- `context`: user-supplied location and category; not a legal applicability determination.
- `report`: null until complete, then mode, engine version, findings, limitations.
- `progress`: optional/null; in AI mode, `completedGroups`, `totalGroups`, and cumulative accepted `findings` from fully received, validated text groups. These are incomplete results, not a final report. Failed jobs may retain early findings; completed jobs clear progress in favor of the final report.
- `errorCode`: safe error code for failed jobs.
- `createdAt`, `expiresAt`: ISO 8601 timestamps.
- `selectedMode`: persisted `MODEL_ASSISTED` or `RULE_BASED_PREVIEW` decision, available while queued.
- `previewReason`: null in ordinary AI/keyword mode; otherwise `FREE_ANALYSES_EXHAUSTED`, `DOCUMENT_TOO_LARGE`, `VISITOR_UNVERIFIED`, `AI_UNAVAILABLE`, or `AI_DISABLED`. These are non-blocking fallback explanations, not failed-job codes.
- `qaAvailable`: deployment Q&A capability, independent of remaining question credits; never used to disable controls based on exhaustion.

Status transitions: `QUEUED → PROCESSING → COMPLETED | FAILED`. Processing has a ten-minute lease. Abandoned jobs fail explicitly rather than being retried indefinitely and incurring additional model charges.

### DOCX import limits

DOCX files are selected by a case-insensitive `.docx` filename and verified to contain a transitional WordprocessingML body in a ZIP package; MIME claims are not trusted. Strict OOXML documents must first be exported as ordinary Word DOCX or text-based PDF. The importer reads body paragraphs and tables only. It never downloads relationships, executes macros, or writes archive entries to disk. Macro-bearing packages are rejected with `UNREADABLE_DOCX`; body tracked changes use the more specific `DOCX_TRACKED_CHANGES`. Accept or reject revisions in Word before uploading.

Extraction budgets: 256 ZIP entries, 16 MiB total expanded data, 4 MiB body XML, XML depth 128, 500,000 XML events, and the existing 80,000-character text limit. DTDs and XML entities are rejected. Expansion budgets are enforced while reading, not from untrusted ZIP size metadata. Unsupported, malformed, encrypted, or over-budget DOCX input returns a safe `400` Problem Detail. This is bounded text import, not full OOXML conformance validation or a hostile-file sandbox.

Every DOCX snapshot includes warnings about omitted layout/content: headers, footers, footnotes, comments, images, text boxes, automatic numbering, and embedded/alternate content are not reconstructed. Field results may be stale and hidden text may be included. External hyperlinks contribute only visible body text. Compare with the original before relying on findings.

`document.sourceType` can now be `TEXT`, `PDF`, or `DOCX`. For DOCX, the retained `pageCount: 1` and block `page: 1` identify a single logical extraction segment for compatibility with the current evidence schema—not a Word page. Only PDF references have physical page numbers; clients must label DOCX/TEXT references as extracted text. Original uploads are not retained.

## Read and watch

- `GET /api/analyses/{uuid}` retrieves the current persisted snapshot.
- `GET /api/analyses/{uuid}/events` opens a bounded SSE connection.

SSE sends named `snapshot` events when the persisted version changes, with the version as the event ID. Connections heartbeat and terminate on final states; clients can reconnect or retrieve the snapshot directly. An `unavailable` event signals deletion or expiry. AI snapshots include source-checked findings after each completed text group, never speculative tokens. Summary, priorities, checklist, Q&A, and full-report exports remain completion-only. Quote validation establishes source location, not interpretation accuracy.

Event responses additionally use `Cache-Control: no-store, private, no-transform` and `X-Accel-Buffering: no` to avoid compression/proxy buffering. Normal API responses retain their existing caching policy. Ingress must still be configured to stream SSE promptly.

All operations verify ownership, including streams. Unknown, expired, and other-session reports return the same `404` to avoid revealing existence.

## Delete

`DELETE /api/analyses/{uuid}`

Requires the session cookie and trusted Origin. Returns `204`. Deletes the database report and canonical source; in-flight workers cannot recreate a deleted row. A worker that has already loaded the document may finish its current processing in memory. Deletion cannot retract content already sent to an external provider or erase downloaded copies. Minimal hashed-session admission timestamps remain for at most one hour for rate limiting and contain no document content.

Cached Q&A responses and idempotency records are deleted with the report. Lifetime keyed network usage counters remain; unstarted AI reservations are released but provider-started credits are not refunded.

## Ask the contract

`POST /api/analyses/{uuid}/ask` with JSON `{"question":"Can I cancel?"}`. Requires ownership, a completed report, trusted Origin and a 1–300-character question. Supply a UUID `Idempotency-Key` and reuse it when retrying the same question.

A grounded answer returns the existing `question`, `answer`, `blockId`, `quote`, `abstain`, and `abstainReason` fields. Abstention is a paid attempt. In demo mode at most eight provider attempts per network are admitted, independently of the analysis allowance. Successful duplicate requests return the same cached answer; pending duplicates return `409 QUESTION_ALREADY_SUBMITTED`. Concurrent questions from one network return `429 QUESTION_IN_PROGRESS` without spending another credit.

Expected service conditions return **200** with a distinct service-message shape, not a fabricated AI answer:

```json
{
  "question": "Can I cancel?",
  "code": "FREE_QUESTIONS_EXHAUSTED",
  "message": "Free AI questions exhausted. You can still review this report and its cited clauses."
}
```

Other service codes: `QA_REQUIRES_AI_REPORT` for preview-only reports, `VISITOR_UNVERIFIED` for unsigned public requests and `QA_UNAVAILABLE` for a failed paid provider attempt. These responses do not hide/disable the input, erase previous answers, or trigger another model call. When the deployment has no provider configured at all, Q&A retains the existing `503 QA_UNAVAILABLE` Problem Detail.

## Evidence offsets

Offsets are absolute UTF-16 code-unit positions in the canonical document text, matching Java `String` and browser JavaScript indexing. A finding references a stable source block, exact quote, and half-open `[start, end)` span. Model proposals with nonexistent or ambiguous quotes are withheld. Canonical text is normalized extraction, not a byte-identical reproduction of the uploaded file.

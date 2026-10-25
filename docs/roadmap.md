# Delivery roadmap

## 1. Evidence-first vertical slice — implemented

- Persistent Spring Boot API, private anonymous sessions, bounded text/PDF extraction.
- Canonical source blocks and validated findings.
- Durable analysis jobs and resumable report delivery.
- Explicit offline-preview mode and configurable model-backed analysis.
- Next.js intake, sample agreement, responsive report, evidence navigation, deletion.
- Unit/integration tests, frontend checks, CI, reproducible configuration.

This milestone delivers text and native-text PDF ingestion, keyword-preview and optional model modes, and source-linked reports. It is not a public-beta safety certification. Model calls use a small provider adapter; a separate orchestration framework is intentionally unnecessary at this stage.

## 2. Ingestion reliability — in progress

Delivered in the first increment:

- DOCX body paragraph/table import with bounded ZIP inflation and XML parsing; no relationship fetching or file extraction.
- Explicit partial-import warnings, tracked-change rejection, and source references that do not invent Word page numbers.
- Synthetic extraction regressions and browser coverage for import, evidence navigation, refresh, and correction of rejected drafts.

Remaining:

- Sandboxed OCR with page quality signals and coordinate mapping.
- Safe public URL ingestion with DNS/redirect SSRF defenses; no unrestricted browser fetcher.
- Extraction benchmarks against representative real-world PDFs and Word documents, including more complex layouts and omission detection.
- Move native PDF parsing out of the API process before accepting untrusted public uploads.

Next implementation order: isolate file parsing with hard process/resource limits; establish extraction benchmarks; then add OCR and safe URL ingestion as independently verified increments. DOCX text import does not complete this milestone or make the upload API ready for unrestricted public use.

## 3. Jurisdiction and contract depth

- Expert-reviewed, versioned US and India legal packs.
- Freelance analysis under either jurisdiction; state-specific applicability where needed.
- Whole-document exception/cross-reference evaluation and calibrated impact classification.

## 4. Actions and conversation

- Document-grounded Q&A with citations and abstention.
- Evidence-backed cancellation and opt-out workflows with user-confirmed facts.
- Negotiation proposals and change-table exports; later true DOCX tracked changes.

## 5. Beta hardening and polish

- Independent security/privacy review, ingress abuse prevention, backups and recovery.
- Expert-reviewed evaluation dataset and release thresholds.
- Heatmap minimap, optional swipe cards, explicit opt-in share images.
- Usage budgets, operational dashboards, accessibility and browser automation.

## Explicitly deferred

Autonomous legal agents, automatic notice sending, universal risk grades, unrestricted legal web search, perfect PDF redlining, multilingual guarantees, accounts/billing, and cross-user private-report caching.

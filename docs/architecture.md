# Architecture and trust model

## Product contract

Terms of Surrender answers: what does the agreement say, why might it matter, where is the evidence, and what can the reader consider next? It distinguishes document explanation, practical implications, and reviewed legal context. The first release implements the first two only.

## Structure

- `frontend`: Next.js/React/TypeScript; accessible intake, source-linked reports, responsive navigation.
- `backend`: Java 21 / Spring Boot modular monolith; private sessions, bounded ingestion, persistent jobs, evidence validation, streaming, retention.
- PostgreSQL for deployment; file-backed H2 for zero-infrastructure local development.
- Model access is behind an analysis interface. The deterministic preview works offline and must never be labeled AI analysis.
- Parsing happens before analysis. Text and PDF input are initially supported; OCR, URL ingestion, and DOCX need additional security and quality controls.

## Document model

The canonical text is split into ordered blocks with stable IDs, page numbers, and absolute character offsets. Every finding carries an exact quote and offsets into one block. Quotes are validated against the canonical source, not trusted because they came from a model. No original upload is retained in the first slice.

## Jobs

Jobs are durable database records with explicit state transitions. Workers atomically claim queued jobs; a lease bounds processing ownership. Reports and status are persisted. Browser streaming is an enhancement: refreshing or reconnecting retrieves the current snapshot. Processing does not depend on an open browser connection.

## Security and privacy

- Anonymous sessions use random HttpOnly SameSite cookies; only token hashes are stored.
- Every private-resource operation is scoped to the session, including SSE and deletion.
- Mutations require a trusted browser Origin to prevent cross-site requests. Production must configure the exact public origin and secure cookies.
- File sizes, page counts, extracted text, queued jobs, and model calls are bounded.
- Uploaded instructions are untrusted data, never system instructions.
- No text, model bodies, credentials, or session tokens belong in logs.
- Retention removes reports and source text; backups need an independently documented expiration policy in production.
- All private responses are non-cacheable. Private documents are never deduplicated across sessions.

## Legal context

User location, governing-law clauses, and contract category are different facts. A future legal-rule record must carry an official source, jurisdiction, applicability conditions, effective date, reviewer, and review status. Until that review exists, UI context is descriptive only and no legal corpus is represented as available.

## Operational limits

Local H2 is not a multi-replica deployment database. Deploy PostgreSQL for multiple instances. The local preview is intentionally conservative and incomplete; it cannot resolve all definitions, exceptions, missing attachments, or enforceability issues. A real public beta requires legal/privacy review, tested deletion/backups, abuse controls at the ingress, and benchmarked model behavior.

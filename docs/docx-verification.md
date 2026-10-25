# DOCX ingestion increment — 2026-10-25

This increment adds bounded DOCX body-text import. It does **not** complete the ingestion-reliability milestone or certify unrestricted public uploads.

## Verified checks

- **45 backend tests passed on H2 and 45 on PostgreSQL 17.11**, including ten DOCX extraction tests.
- **14 frontend component tests passed**, including DOCX selection/submission with independent location and contract-category context.
- **115 production-server browser checks passed**: 23 scenarios across five browser/device profiles, covering both the existing functionality and new DOCX flows.
- Java formatting and Checkstyle, frontend formatting, ESLint, TypeScript, and the production frontend build passed.
- **10 DOCX browser checks passed against the PostgreSQL/container stack**: two new scenarios across Chromium desktop/mobile, Firefox desktop, and WebKit desktop/mobile.
- Both container images built successfully, and all **six container HTTP/persistence checks passed**, including API restart, exact text boundaries, and multipart upload-limit handling.
- The disposable verification containers and database volume were removed after testing; unrelated containers were left untouched.

## New regression coverage

- Paragraph/run joining, visible hyperlink text, tables, tabs, line breaks, Unicode, and exact UTF-16 source spans.
- Exact 80,000-character input, including a table cell; oversized canonical text rejection.
- Tracked insertions/deletions/moves, formatting/numbering revisions, and ambiguous draft rejection.
- Macro-bearing packages, incomplete archives, duplicate bodies, excessive entries, oversized inflation, oversized XML, deeply nested XML, malformed XML, DTDs, and external entities.
- Omitted drawings/text boxes/instructions and alternate shape representations; tab-stop metadata cannot inject text tabs.
- Browser scenarios cover a real generated OPC/ZIP DOCX through the API, report warnings, evidence navigation, accessible source labels, reload, and replacement of a rejected draft while retaining US/India and freelance context.

## Explicit limits

- Test agreements are synthetic. These cases are not an extraction-quality benchmark for arbitrary Word layouts, a parser sandbox, or a security audit.
- Only transitional WordprocessingML body text is supported. Footnotes, headers/footers, comments, images/text boxes, numbering, and embedded/alternate content are not reconstructed. Field results may be stale and hidden text may be included.
- DOCX source references identify extracted text, not physical Word pages. The existing page fields retain one logical extraction segment for schema compatibility.
- The importer performs no external relationship fetching and uses no office suite or new dependency. File parsing still runs in the API process; hard process isolation remains the next milestone increment.
- Live AI-provider quality and legal accuracy were not evaluated. OCR, URL ingestion, reviewed legal packs, and document-grounded conversation remain separate work.

See [the API contract](api.md#docx-import-limits), [test commands](testing.md), and [remaining milestones](roadmap.md).

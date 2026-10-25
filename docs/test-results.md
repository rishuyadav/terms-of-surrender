# Verification results — 2026-10-25

Scope: the implemented evidence-first preview, not the unimplemented features in the original brainstorm or a certification of legal accuracy.

## Completed checks

| Check                                                        | Result                                                             |
| ------------------------------------------------------------ | ------------------------------------------------------------------ |
| Backend unit/integration suite, clean H2 build               | 35 passed                                                          |
| Same backend suite against PostgreSQL 17.11                  | 35 passed                                                          |
| Frontend component/unit suite                                | 13 passed                                                          |
| Production standalone browser matrix                         | 105 passed: 21 scenarios across five browser/device profiles       |
| Same browser matrix against the PostgreSQL/container stack   | 105 passed                                                         |
| Container build and PostgreSQL startup                       | Passed with Podman Compose on Linux ARM64                          |
| Container HTTP/persistence verifier                          | All six checks passed, including API restart and oversized uploads |
| Fresh-volume bootstrap and test database initializer         | Passed; disposable stack and its data removed afterwards           |
| Java formatting / Checkstyle                                 | Passed                                                             |
| Frontend formatting / ESLint / TypeScript / production build | Passed                                                             |
| npm dependency audit                                         | No reported vulnerabilities                                        |
| Compose and CI YAML parsing                                  | Passed                                                             |

Browser profiles: Chromium desktop, Chromium mobile, Firefox desktop, WebKit desktop, and WebKit mobile. Mobile profiles emulate devices; these results are not physical-device certification.

## Feature coverage

| Implemented feature         | Verification                                                                                                                                       |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Text ingestion              | Normal input, empty/short input, exact 80,000-character boundary, oversized text, Unicode, stable offsets                                          |
| PDF ingestion               | Actual generated PDF, forged file, scanned-only PDF, partially unreadable PDF, file/page limits, extraction in the Linux runtime image             |
| Location/category selection | US and India independent of consumer/freelance category; context retained through submission                                                       |
| Findings and evidence       | Exact quotes, ambiguous/invented quote rejection, invalid model-field rejection, duplicate findings, overlapping highlights, source navigation     |
| Report interface            | Topic filters, empty report, failed job, model-mode presentation, exact wording, question copying/fallback, JSON download                          |
| Async processing            | Durable records, exclusive worker claiming, concurrent admission limits, provider failure without silent downgrade, SSE completion/deletion/limits |
| Recovery                    | Refresh, session retry, submission retry, report-read retry, disconnected SSE falling back to persisted-state polling                              |
| Privacy/security controls   | HttpOnly/Secure/SameSite settings, session ownership, private headers, CSRF denial, inert rendering of source HTML, deletion and expiry            |
| Persistence                 | Original document and ownership retained after API-container restart; deletion remains effective afterwards                                        |
| Accessibility               | Automated intake/upload/report checks, high-impact/favorable states, failed jobs, keyboard-accessible source scrolling, contrast                   |

## Defects corrected during verification

- Long-block segmentation inserted artificial paragraph separators, rejecting an otherwise valid exact-limit document. Segmentation now preserves canonical text and maps blocks onto it.
- A UTF-16 surrogate pair could be split at a block boundary. Splits now preserve complete code points.
- Rejecting a replacement upload left the previous PDF selected. Invalid replacements now clear the old selection and cannot accidentally submit it.
- AI-mode banners, favorable badges, and failed-job text needed stronger contrast.
- A failed report with no clickable highlights left the scrollable source inaccessible by keyboard. The source is now a named, focusable region.
- Added bounded rejected-body draining and a real-servlet HTTP test for clean upload-limit errors. The container verifier explicitly reads early `413` responses rather than mistaking a proxy's early response for a failed request.
- Test configuration now uses an explicit test profile, preserving production defaults while isolating test databases. Container worker data and host PostgreSQL integration-test data use different databases.

## Limits of these results

- Live AI-provider credentials were not configured. Adapter success/error handling, malformed/truncated responses, evidence validation, response limits, and timeout cancellation are tested with controlled responses. Live-provider behavior and interpretation quality are **not** certified.
- Model-mode browser scenarios use controlled report responses; they test presentation, not legal correctness.
- URL ingestion, OCR, reviewed US/India legal packs, chat, opt-out drafts, redlines, and share images remain unimplemented and are not represented as passing features.
- Automated accessibility checks do not replace assistive-technology testing. The suite is not a penetration test or hostile-PDF sandbox evaluation.
- Container execution was verified through Podman Compose locally; the CI workflow also exercises Docker Compose. Production deployment and jurisdiction-specific legal review remain separate release requirements.

Reproduction commands and test locations are in [testing.md](testing.md).

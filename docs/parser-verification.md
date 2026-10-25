# Parser process isolation verification — 2026-10-25

Scope: PDF/DOCX extraction outside the API JVM, bounded worker execution and protocol, recovery, and container resource settings. This is not a claim of hostile-code sandboxing or public-beta readiness.

## Completed checks

| Check                                                     | Result                                                                      |
| --------------------------------------------------------- | --------------------------------------------------------------------------- |
| Backend suite on H2                                       | 62 passed                                                                   |
| Backend suite on PostgreSQL 17.11                         | 62 passed                                                                   |
| Frontend unit/component suite                             | 15 passed                                                                   |
| Production-server browser matrix                          | 125 passed across five browser/device profiles                              |
| PostgreSQL/container browser matrix                       | 125 passed across five browser/device profiles                              |
| Existing container HTTP/persistence verifier              | Six checks passed                                                           |
| Packaged parser/runtime verifier                          | Five checks passed: applied limits, PDF, DOCX, safe rejection, idle cleanup |
| Java formatting and Checkstyle                            | Passed                                                                      |
| Frontend formatting, ESLint, TypeScript, production build | Passed                                                                      |
| Packaged worker main-class bootstrap                      | Passed with the executable Spring Boot jar                                  |
| Compose/CI YAML and verification-script syntax            | Passed                                                                      |

## New tests

- **10 process-boundary tests**, using real JVM subprocesses rather than mocked process completion. Fixtures cover a real PDF, environment and heap restrictions, private working directory, crash, Java heap exhaustion, malformed/trailing/unknown/duplicate JSON fields, oversized output, invalid evidence spans, parent-owned errors, blocked stdin, wall-clock timeout, saturation/recovery, interruption, shutdown, and early size rejection.
- **Three routing tests** ensure plain text and invalid/ambiguous input do not consume file-parser capacity and uploaded files cross the worker boundary.
- **Three API failure tests** verify `422`, `429`, and `503` responses, no analysis/admission rows on extraction failure, and continued plain-text availability after worker failure.
- Existing PDF/DOCX browser scenarios now exercise actual short-lived workers, including file/page limits, partial-document warnings, tracked-change rejection, retry, source navigation, and report reload.

The fixtures deliberately exhaust only a child JVM's configured heap. They do not intentionally exhaust the API/container or host memory. Request cancellation tests interrupt the service request thread; they do not assert that every servlet/client disconnect is detected immediately.

## Additional defects found during regression testing

- A WebKit mobile rerun exposed an intake hydration race: text filled before React attached handlers could be cleared, causing an empty submission. Stateful intake controls now remain disabled in the server-rendered HTML until React hydrates. A component regression (`renderToString`) and a browser regression (raw HTTP fetch of the server HTML, deterministic regardless of server speed) check the server attributes. The guard does not depend on a successful backend session request, so drafts remain editable after a connection failure.
- Expected async/SSE disconnects reached the generic Problem Details handler, which then attempted to write JSON on a closed event-stream response. A dedicated no-write handler and regression test prevent that secondary failure.
- CI browser runs now fail on flaky results even when a retry succeeds; retries retain diagnostics but cannot turn a flaky run green. Playwright's `retain-on-failure` trace mode races with browser-process exit on macOS (webkit and firefox both show `ENOENT` on the trace zip during cleanup of a retried test), triggering `failOnFlakyTests`. Switched to `on-first-retry`, which writes the trace during the retry pass rather than cleaning up after the initial run, eliminating the race.

## Limits and follow-up

The [process-boundary documentation](parser-isolation.md) distinguishes Java-region limits from aggregate container limits and explains why workers sharing the API's OS identity/network are not an exploit-containment boundary. In particular, native/RSS exhaustion and arbitrary same-user code need stronger per-worker OS controls. Independent sandbox/security review remains mandatory before unrestricted public uploads.

Tests use synthetic agreements, not a representative extraction-quality benchmark or reviewed legal evaluation set. OCR, URL ingestion, model quality, jurisdiction-specific legal packs, and arbitrary-code containment remain unverified/unimplemented as applicable. Remote GitHub CI is separate from these locally observed outcomes.

Reproduction commands are in [testing.md](testing.md). Historical [vertical-slice](test-results.md) and [DOCX-import](docx-verification.md) results remain available for context.

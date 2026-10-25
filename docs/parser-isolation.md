# File parser process boundary

PDF and DOCX extraction now run in short-lived JVMs, never in the Spring Boot API JVM. This is **failure isolation with resource budgets**, not a complete hostile-code security sandbox.

## Request lifecycle

1. `DocumentIngestion` validates mutually exclusive inputs. Pasted text uses the bounded local canonicalizer; uploaded files go only through `IsolatedFileParser`.
2. The API acquires one of two per-instance parser slots without queuing. Input is read with a 10 MiB byte ceiling, independent of the multipart object's reported size.
3. A worker starts in a fresh private temporary directory. Only the allowlisted format (`PDF` or `DOCX`) is passed as an argument. File contents travel through stdin, never through command arguments, environment variables, or worker input files.
4. `ParserWorker` performs a single extraction without starting Spring, loading application configuration, or accessing the database/model adapter. The inherited environment is cleared; only a fixed locale is supplied. Platforms may add their own locale metadata (for example, macOS `__CF_USER_TEXT_ENCODING`). Java-option injection variables and application credentials are not forwarded.
5. The API reads at most 4 MiB of JSON and validates document type, length, fingerprint, page bounds, unique block IDs, ordered exact source spans, whitespace-only gaps, and warning bounds. The protocol rejects unknown/duplicate fields and accepts exactly one result or a recognized error code, with no trailing JSON. Worker exception messages are not accepted as client messages. These checks establish structural consistency, not proof that extraction accurately reproduces the original file.
6. Only successful extraction creates an analysis job. Failure never falls back to parsing inside the API or to analyzing an empty document. The worker is terminated if necessary, temporary files are removed, and capacity is released. A slot is retained until its process exits; graceful shutdown and request-thread interruption also terminate active workers.

## Fixed budgets

| Budget                                        | Limit                                                                           |
| --------------------------------------------- | ------------------------------------------------------------------------------- |
| Concurrent file workers                       | 2 per API instance; no waiting queue                                            |
| Worker wall-clock budget                      | 20 seconds, shared across startup, input writing, output reading, and exit wait |
| File input                                    | 10 MiB                                                                          |
| Protocol response                             | 4 MiB                                                                           |
| Worker Java heap                              | 128 MiB                                                                         |
| Worker metaspace / code cache / direct memory | 96 / 32 / 16 MiB                                                                |
| Worker thread stack                           | 512 KiB                                                                         |
| JVM processor ergonomics                      | 1 active processor, serial GC                                                   |
| Document                                      | Existing 80,000 canonical UTF-16 characters; PDFs up to 40 pages                |

`ActiveProcessorCount` affects JVM ergonomics; it is **not** an OS CPU quota. Java memory-region ceilings do not cap all native allocations or total RSS. The deadline covers child execution and pipe I/O, not browser upload time or the whole HTTP lifecycle. Per-instance limits multiply when replicas are added; enforce shared ingress limits separately.

Workers use the current JRE's absolute Java executable and an absolute classpath. Development uses the ordinary main-class launcher. Packaged Spring Boot jars use `PropertiesLauncher` with an explicit worker main class; no separate parser artifact or installation is required. Failed launches are errors, not a reason to use an in-process fallback.

## Container-level limits

The supplied Compose backend adds aggregate cgroup limits for the API **and** children: 1536 MiB memory, two CPUs, and 192 PIDs. Its root filesystem is read-only, `/tmp` is a 128 MiB tmpfs, Linux capabilities are dropped, and privilege escalation is disabled. The API heap is capped at 256 MiB to reserve memory for children. Re-budget before changing concurrency or JVM settings; do not remove limits to make oversized inputs pass.

Local `mvn spring-boot:run` and `java -jar` use the process/JVM budgets but do not automatically gain Compose cgroup, PID, filesystem, or disk limits. A writable private temporary directory and the bundled JRE executable are required. Sandbox policies that prohibit subprocess execution will intentionally cause `PARSER_UNAVAILABLE` for uploads; plain-text analysis remains available. Deadlines depend on the live API supervisor: abruptly killing a non-container API process can orphan a worker or leave temporary directories. Production isolation must also provide process-group/container teardown and external cleanup; Java shutdown hooks cannot handle `SIGKILL`.

## Failure contract

| HTTP | Code                      | Meaning                                                                                  |
| ---- | ------------------------- | ---------------------------------------------------------------------------------------- |
| 400  | Existing extraction codes | Unsupported/unreadable/protected/oversized-content input                                 |
| 400  | `DOCX_TRACKED_CHANGES`    | Resolve revisions in Word before retrying                                                |
| 422  | `PARSER_TIMEOUT`          | Extraction exceeded the worker deadline; use a smaller/simpler document                  |
| 429  | `PARSER_BUSY`             | All file slots are occupied; retry later                                                 |
| 503  | `PARSER_UNAVAILABLE`      | Launch failure, crash, heap failure, malformed/oversized output, or invalid source spans |

Ordinary unreadable DOCX errors retain `UNREADABLE_DOCX` with a generic parent-owned explanation; raw worker details are deliberately withheld. Worker stderr is discarded, and incidental stdout is suppressed before parsing. Application diagnostics record only safe failure classes or numeric exit codes, never filenames, source text, worker bodies, or exception messages. Monitor cleanup-failure warnings: cleanup is attempted on every exit path but filesystem errors must not silently strand admission capacity.

## Remaining security boundary

The worker still shares the API's OS user and network namespace. It is **not** protected from reading accessible files, inspecting same-user processes, or opening network connections if a parser exploit executes arbitrary code. Clearing environment variables alone does not protect credentials from arbitrary same-user code. The read-only filesystem reduces writes but is not filesystem access isolation, and aggregate cgroups do not guarantee that a hostile child cannot affect the API.

Before unrestricted public uploads, use a separately reviewed OS/container sandbox with a distinct identity, denied network access, narrowly scoped mounts, per-worker resource enforcement, and a constrained protocol. This increment contains ordinary crashes, hangs, and Java heap failures; it does not certify exploit containment. Extraction-quality benchmarks, OCR, safe URL ingestion, and independent security/privacy review remain separate release gates.

## Verification

- `IsolatedFileParserTest` launches real child JVMs for successful PDF parsing, sanitized environment/heap/private-directory checks, crash and heap exhaustion, output overflow, malformed/trailing JSON, invalid source spans, safe error mapping, blocked stdin, timeout, saturation/recovery, shutdown, interruption, and pre-launch size rejection.
- `DocumentIngestionTest` checks routing and that invalid inputs/plain text do not consume file-worker capacity.
- `ParserFailureApiTest` checks HTTP failure codes, no persisted jobs/admission charges on extraction failure, and continued plain-text availability.
- `scripts/verify-parser-runtime.py` checks applied container memory/CPU/PID/read-only settings, packaged PDF/DOCX extraction, safe tracked-change rejection, and temporary-directory cleanup. Run it after browser tests while the verification stack is idle.

See [testing](testing.md) for commands. Synthetic failure fixtures are test-only classes, not production worker modes or user-selectable options.

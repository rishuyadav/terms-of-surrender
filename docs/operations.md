# Operations and release checklist

## Configuration

| Variable            | Default                             | Purpose                                                    |
| ------------------- | ----------------------------------- | ---------------------------------------------------------- |
| `PORT`              | `8081` locally, `8080` in container | Backend listener                                           |
| `DATABASE_URL`      | file-backed H2 in `.data/`          | JDBC connection; use PostgreSQL for deployment             |
| `DATABASE_USER`     | `sa`                                | Database username                                          |
| `DATABASE_PASSWORD` | empty locally                       | Database password; required by Compose                     |
| `ALLOWED_ORIGINS`   | `http://localhost:3000`             | Exact comma-separated trusted browser origins              |
| `SECURE_COOKIES`    | `false` locally                     | Set true behind production HTTPS                           |
| `RETENTION_HOURS`   | `24`                                | 1–168 hours; report access expires immediately at deadline |
| `WORKER_ENABLED`    | `true`                              | Run background worker in this process                      |
| `MODEL_ENABLED`     | `false`                             | Explicit opt-in to external model processing               |
| `MODEL_BASE_URL`    | `https://api.openai.com/v1`         | HTTPS OpenAI-compatible API base                           |
| `MODEL_API_KEY`     | empty                               | Server-side provider credential                            |
| `MODEL_NAME`        | empty                               | Required when model mode is enabled                        |
| `API_BASE_URL`      | `http://localhost:8081`             | Frontend build-time proxy destination                      |
| `WEB_PORT`          | `3000`                              | Compose loopback frontend port                             |

Spring Boot reads exported environment variables, not the root `.env` file automatically. Docker Compose reads root `.env`. Next.js can read `frontend/.env.local`; never put a model key in a `NEXT_PUBLIC_` variable.

## Persistence and limits

- Flyway owns schema changes. After a migration is committed, add a new migration rather than editing an applied one.
- H2 is for one local process. API replicas and worker replicas must share PostgreSQL.
- Admission checks use a database guard row to serialize quotas across replicas.
- Default allowance: 2 active jobs and 10 submissions per hour per anonymous session; 50 pending jobs globally.
- Report deletion does not reset hourly allowances. Admission timestamps expire after one hour.
- Anonymous quotas are not abuse prevention by themselves: anyone can start a new session. Enforce independent IP/ingress/global spending limits before public exposure.
- Expired rows and admission metadata are purged every minute. A live worker can briefly retain an in-memory copy after deletion. Database files, WAL, backups, model providers, and local downloads have separate retention properties; deletion is not cryptographic erasure.
- Original PDF/DOCX uploads are not retained. Both file formats are parsed in short-lived worker JVMs, with two slots per API instance, 128 MiB child heaps, a 20-second deadline, and bounded pipe input/output. Failures never fall back to in-process parsing. Plain-text analysis does not use these slots. See [parser isolation](parser-isolation.md) for deployment budgets, failure codes, and remaining security limits.
- DOCX uses the JDK ZIP/StAX readers with bounded expansion and disabled XML external entities; no office suite, network fetcher, or new runtime dependency is required. See [DOCX limits](api.md#docx-import-limits) for omissions and rejection behavior.
- Compose reserves 1536 MiB aggregate backend memory, two CPUs, and 192 PIDs; the API heap is 256 MiB. The backend root is read-only with a 128 MiB `/tmp` tmpfs, dropped capabilities, and no privilege escalation. Workers still share the API's OS identity and network; this is not a distinct-identity hostile-code sandbox. Local non-container runs do not inherit cgroup/disk limits.
- The servlet accepts at most 10 MB per file and 11 MB per multipart request. Tomcat drains at most 12 MB on rejection to deliver a clean `413` for nearby over-limit requests without enabling unbounded body draining. Much larger malicious requests may be disconnected; enforce a hard limit at the public ingress too.
- Failed jobs are not automatically retried. Persisted queued jobs survive restarts; abandoned processing jobs fail when their lease expires.
- `/actuator/health` exposes liveness/database health without private details. Put it behind the internal network in production.

## Before a public beta

- [ ] Run container and PostgreSQL integration tests in the target environment.
- [x] Move file parsing outside the API JVM, with bounded I/O, concurrency, Java memory regions, and a processing deadline.
- [ ] Add and independently review distinct-identity, filesystem/network-restricted file-worker sandboxing; verify per-worker OS resource limits in the target environment.
- [ ] Enforce HTTPS, secure cookies, exact origins, and ingress request/rate limits.
- [ ] Apply provider-level spending caps and review document retention/training terms.
- [ ] Configure SSE proxy timeouts and disable proxy buffering for event streams.
- [ ] Review encryption at rest, database access, logs, backups, restoration, and deletion timelines.
- [ ] Add independent legal/privacy review and a user-facing privacy policy.
- [ ] Benchmark model precision, recall, exception handling, and prompt-injection resilience on reviewed fixtures.
- [ ] Test accessibility with assistive technology, not just automated checks.
- [ ] Add metrics/alerts for queue age, failures, latency, provider costs, and cleanup failures without recording text.
- [ ] Supply reviewed legal packs before enabling any jurisdiction-specific legal assertions.

## Model limitations

The model adapter is deliberately small and replaceable. It analyzes bounded groups of blocks, not an autonomous graph of agents. It validates exact quotes but cannot mechanically validate the truth of every interpretation. Cross-group exceptions and definitions can be missed. The report exposes these limits. Do not market this as a comprehensive legal review, a safety score, or a determination of enforceability.

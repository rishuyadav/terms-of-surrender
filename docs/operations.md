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
- No original PDFs are retained. Extraction runs in the API process in this slice. Strict upload limits are not a substitute for sandboxing hostile parsers.
- The servlet accepts at most 10 MB per file and 11 MB per multipart request. Tomcat drains at most 12 MB on rejection to deliver a clean `413` for nearby over-limit requests without enabling unbounded body draining. Much larger malicious requests may be disconnected; enforce a hard limit at the public ingress too.
- Failed jobs are not automatically retried. Persisted queued jobs survive restarts; abandoned processing jobs fail when their lease expires.
- `/actuator/health` exposes liveness/database health without private details. Put it behind the internal network in production.

## Before a public beta

- [ ] Run container and PostgreSQL integration tests in the target environment.
- [ ] Put file parsing in a resource-limited isolated worker with a hard processing deadline.
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

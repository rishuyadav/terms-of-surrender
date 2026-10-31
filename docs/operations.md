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
| `AI_DEMO_ENABLED` | `false` | Enable persistent lifetime IP quotas; required for the protected public AI demo |
| `AI_DEMO_PROXY_SECRET` | empty | Stable 32+ character shared server-only Vercel/Render secret |
| `AI_DEMO_ANALYSES_PER_IP` | `5` | Lifetime AI analysis allowance |
| `AI_DEMO_QUESTIONS_PER_IP` | `8` | Independent lifetime AI question allowance |
| `AI_DEMO_MAX_CHARACTERS` | `30000` | Larger documents use full-text keyword preview without spending a credit |
| `AI_DEMO_MAX_COMPLETION_TOKENS` | `4000` | Maximum completion tokens per AI analysis text-group request (800–8000) |
| `AI_DEMO_EXEMPT_VISITOR_HASHES` | empty | Render-only comma-separated network HMACs exempt from lifetime AI quotas |
| `API_BASE_URL`      | `http://localhost:8081`             | Frontend build-time proxy destination                      |
| `WEB_PORT`          | `3000`                              | Compose loopback frontend port                             |

Spring Boot reads exported environment variables, not the root `.env` file automatically. Docker Compose reads root `.env`. Next.js can read `frontend/.env.local`; never put a model key in a `NEXT_PUBLIC_` variable.

## Public AI demo: Vercel → Render

### Deployment order

1. Back up the persistent PostgreSQL database. Keep `MODEL_ENABLED=false` during setup.
2. Generate a strong random secret, for example `openssl rand -hex 32`. Set `AI_DEMO_PROXY_SECRET` to the **same value** in Render and Vercel's **Production** server-side environment. Set `AI_DEMO_ENABLED=true` on Render and deploy the backend migration/code. Never commit the secret or expose it with `NEXT_PUBLIC_`. Keep it stable: changing it creates new quota identities and resets allowances in practice.
3. On Render set `ALLOWED_ORIGINS=https://terms-of-surrender.vercel.app`, `SECURE_COOKIES=true`, and confirm the JDBC PostgreSQL settings. Defaults are five analyses and eight questions; neither resets automatically.
4. On Vercel set `API_BASE_URL=https://terms-of-surrender.onrender.com` and redeploy the frontend. Vercel supplies `VERCEL=1` and its ingress-overwritten `x-vercel-forwarded-for` client IP; our Next.js Proxy signs that identity and strips incoming forged `x-tos-visitor-*` headers. Do not manually set `VERCEL=1` on an untrusted non-Vercel public server.
5. Verify text, PDF/DOCX, session cookies and SSE through the Vercel URL. Proxy keeps the existing external rewrite instead of introducing a Function Route Handler for uploads; its body clone is bounded at 12 MB. Confirm actual Vercel plan/ingress limits with a near-10-MB test upload before release.
6. Configure `MODEL_BASE_URL`, `MODEL_NAME`, and `MODEL_API_KEY` **only on Render**, then enable `MODEL_ENABLED=true`. Verify a small synthetic document gets AI mode through Vercel and an unsigned direct Render submission gets `VISITOR_UNVERIFIED` preview, never a paid call. Do not disable `AI_DEMO_ENABLED` while the public provider is enabled.
7. Test the boundary in a separate staging database (or dedicated test network): the fifth analysis uses AI and the sixth uses preview; the eighth question calls AI and the ninth returns `FREE_QUESTIONS_EXHAUSTED`. Refreshing, new browser sessions, report deletion after provider start and redeploys must not reset usage. Keep production/staging database and provider secrets isolated.

`MODEL_ENABLED=false` is the emergency spending kill switch. Pending AI jobs then use clearly labeled `AI_DISABLED` preview, releasing reservations if provider work never started. Do not roll back to old application code with the model enabled: old workers do not enforce these quotas. The additive V3 migration need not be rolled back.

### Private network exemption

To exempt your current network from the five-analysis/eight-question lifetime quotas:

1. Find your current **public** IP locally (not your Wi-Fi/LAN address). Use the same network and VPN state as your browser. Do not paste the IP or secret into chat, Git, or shell arguments.
2. From the repository root, run `python3 scripts/generate-visitor-hash.py` in an interactive terminal. It prompts for the deployed `AI_DEMO_PROXY_SECRET` and public IP with echo disabled, then prints only the matching HMAC. Python 3 is required; the helper uses only its standard library and makes no network requests.
3. Set `AI_DEMO_EXEMPT_VISITOR_HASHES=<printed hash>` on **Render only**, then redeploy. Multiple hashes may be comma-separated. Leave `AI_DEMO_ENABLED=true`; no additional Vercel setting is needed for the exemption.
4. Submit a new small document through Vercel. Verified matching requests bypass both lifetime quota checks without incrementing counters. No exemption flag or network hash is returned to the browser.

Signed identity verification, replay protection, idempotency, document/output limits, per-session admission limits, and Q&A concurrency limits still apply. Previously generated preview reports stay previews; submit a new document to obtain an AI report. Existing counters are not erased, so removing the exemption restores the earlier quota balance. Removing a hash takes effect for new admissions after redeployment; already admitted jobs may still finish.

This is a **network exemption**, not owner authentication: everyone sharing the IPv4 address or IPv6 `/64` is exempt. If your ISP/VPN changes your public network, regenerate the hash. IPv4 and IPv6 networks require separate entries if your browser uses both. Changing the shared secret requires regenerating exemptions and also changes all public quota identities. Raw IPs are not stored by this feature, but hosting infrastructure has its own IP/logging policies.

### Accounting and privacy

- PostgreSQL's existing admission guard serializes quota reservation, job creation and question admission across replicas. Store the selected mode with each queued job; a later request cannot change that decision.
- Invalid input, parsing failures, oversize demo documents and unsigned requests do not consume AI credits. Accepted AI jobs reserve a credit. Deletion/expiry before provider work starts releases it; once the worker starts model work, provider failures/timeouts still consume it. No automatic paid retries. Individual provider failures produce an explicitly labeled, full-text preview with partial AI findings cleared.
- Q&A uses an independent pool. Abstentions and failed provider attempts consume a question credit. One in-flight question per network is allowed within the bounded provider deadline. Browser retries reuse an idempotency key; duplicate analyses return the existing private job and duplicate questions return their cached response, never another paid call.
- No remaining balances are exposed in the public session API or displayed in the UI. Q&A controls remain interactive after exhaustion and on preview reports when the deployment has AI configured. Preview reports cannot trigger paid Q&A; they return `QA_REQUIRES_AI_REPORT` (or `FREE_QUESTIONS_EXHAUSTED` when applicable). Existing AI reports keep their remaining independent Q&A access.
- IPv4 addresses are canonicalized; IPv6 addresses are grouped by `/64`. A keyed HMAC of that network is persisted, not the raw IP. `ai_demo_visitors` counters intentionally outlive the 24-hour document retention. HMACs are pseudonymous data and need corresponding privacy-policy wording. Shared networks share allowances, and VPNs/new networks can bypass per-network limits.
- `ai_demo_requests` contains request hashes, reservations and cached Q&A content; delete it with the associated report and purge it on expiry. Replay nonces expire in two minutes. Quota counters contain no report or question content and are not deleted by report cleanup. Hosting ingress logs/providers/backups have their own retention: avoid recording IP attestation headers or contract text in application logs.
- Per-IP quotas are **not a global spending guarantee**. A monetary cap is intentionally deferred. Bound document size and completion tokens, monitor provider usage, and use provider-enforced hard caps if available; budget-alert settings alone may not stop charges. Tune the default 4,000-token completion limit against synthetic samples; truncated/invalid provider output falls back explicitly instead of presenting an incomplete AI report.

The shipped signing path is specific to Vercel. Local/Compose AI remains unrestricted with `AI_DEMO_ENABLED=false`. Enabling demo quotas without a verified proxy identity fails closed to preview rather than trusting arbitrary forwarded-IP headers.

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

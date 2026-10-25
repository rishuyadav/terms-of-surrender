# Terms of Surrender

An evidence-first contract interpreter: understand consequential terms, verify the source, and decide what to do next.

## Implementation status

The first vertical slice is implemented. This is an early preview, not a complete legal-advice product. See [the architecture](docs/architecture.md) and [roadmap](docs/roadmap.md) for scope and safety boundaries. The original [idea.txt](idea.txt) is retained as brainstorming context.

### Available now

- Pasted text and text-based PDFs, with bounded input and extraction warnings.
- Independent US/India location and consumer/freelance agreement choices.
- Offline keyword preview, or explicitly enabled OpenAI-compatible model analysis.
- Exact evidence validation, source highlights, topic filters, and practical questions.
- Responsive desktop split view and mobile source/findings navigation.
- Persistent background jobs, live status, refresh recovery, and polling fallback.
- Private anonymous sessions, deletion, 24-hour default retention, and session quotas.
- Private JSON report downloads and three clearly fictional sample agreements.

**Not yet implemented:** URL ingestion, photo OCR, reviewed legal overlays, contract chat, opt-out drafts, negotiation/redline exports, and share images. Context selection does not enable jurisdiction-specific legal advice.

### Local development

Requirements: Java 21, Maven 3.9+, Node.js 24 LTS (recommended) and npm.

```sh
# Terminal 1
cd backend
mvn spring-boot:run

# Terminal 2
cd frontend
npm ci
npm run dev
```

Open http://localhost:3000. The frontend proxies `/api` to the backend at http://localhost:8081, keeping browser requests same-origin. No model API key or Docker is required for the clearly labeled rule-based preview. Local data is stored in `backend/.data/` and expires after 24 hours by default.

The project-local Maven settings use Maven Central rather than machine-wide corporate mirrors. They do not modify your global Maven configuration.

### Enable model-assisted analysis

Export these variables in the backend shell before starting Maven:

```sh
export MODEL_ENABLED=true
export MODEL_API_KEY='your-provider-key'
export MODEL_NAME='your-provider-model-id'
# Optional: an OpenAI-compatible HTTPS base URL, default https://api.openai.com/v1
export MODEL_BASE_URL='https://api.openai.com/v1'
mvn spring-boot:run
```

The adapter requires chat completions with JSON-object responses and `max_completion_tokens` support. It does not use autonomous tools. Requests have bounded input, response size, request count, and full-response timeouts. Provider failures mark a job as failed rather than silently substituting keyword results. Exact-source checks reject invented quotes, but **do not prove an interpretation is correct**.

**Privacy:** extracted text is sent to your configured provider without guaranteed anonymization. Review that provider's retention, training, and regional-processing terms before using private documents. No paid-provider call is required by the test suite.

### Verify

```sh
# From backend/
mvn -B -ntp verify
# Apply the same Java formatting checked in CI:
mvn spotless:apply

# From frontend/
npm run format:check
npm run lint
npm run typecheck
npm test
npm run build

# Full browser flow; starts the API and frontend automatically
npx playwright install chromium
npm run test:e2e
```

Browser tests use isolated ports (13000 / 18080) and an in-memory database when starting their own backend. If a test service is already running on those ports, development tests reuse it; use only test/sample documents. CI starts fresh services. Desktop and mobile flows exercise analysis, exact source navigation, session isolation, refresh, and deletion.

Do not run two Maven builds in the same `backend/target` directory concurrently, or run Next.js development and production builds against the same `.next` directory concurrently. Independent backend and frontend checks can run in parallel.

`npm run build` prepares a standalone production server with local static assets; `npm start` runs that server. Set `PORT` and `HOSTNAME` to change its listener. `API_BASE_URL` is captured at build time.

### PostgreSQL and containers

With Docker Compose installed:

```sh
cp .env.example .env
# Set a strong DATABASE_PASSWORD in .env, then:
docker compose up --build
```

Only the frontend is exposed, on `127.0.0.1:3000`. The API and PostgreSQL communicate on the container network. Container configuration is provided but must be verified in a Docker-capable environment. For a public deployment, use HTTPS, set `SECURE_COOKIES=true` and exact `ALLOWED_ORIGINS`, configure ingress rate limits and streaming, and complete the [deployment checklist](docs/operations.md). Do not expose this development setup as a production legal service.

See [API documentation](docs/api.md) for endpoints and [operations](docs/operations.md) for configuration and known limits.

## Trust boundaries

- Findings must point to exact passages in the supplied document.
- Contract explanation is not a conclusion about legality or enforceability.
- US and India are jurisdiction contexts; freelance is a contract category.
- No jurisdiction-specific legal claims are enabled without a reviewed legal corpus.
- Private documents are never published or cached across anonymous sessions.
- No notices are sent, and no legal deadlines are invented.
- Do not upload sensitive production documents to an unreviewed development deployment.

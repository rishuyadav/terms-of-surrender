# Verification guide

Use synthetic agreements only. Automated checks verify implemented behavior; they are not legal validation, a penetration test, or proof that arbitrary hostile PDFs are safe to parse.

## Unit and integration checks

```sh
# backend/
mvn -B -ntp verify

# frontend/
npm ci
npm run format:check
npm run lint
npm run typecheck
npm test
```

Backend tests cover evidence validity, text boundaries and Unicode, PDF extraction, provider response validation and timeout cancellation, worker failure behavior, cookie security, ownership, CSRF, quotas, concurrent admission/claiming, SSE, expiry, and deletion.

Frontend component tests cover source segmentation, API errors, evidence/clipboard controls, session retries, submission retries, and invalid replacement uploads.

Do not run Maven verification and a Maven-started browser-test server against the same `backend/target` directory concurrently. Finish backend checks before starting browser tests.

## Production-server browser matrix

```sh
# frontend/
npx playwright install chromium firefox webkit
API_BASE_URL=http://localhost:18080 CI=true npm run build
CI=true npm run test:e2e
```

The matrix runs Chromium desktop/mobile, Firefox desktop, WebKit desktop, and WebKit mobile (Safari emulation). These are browser engines and device emulation, not physical-device certification.

Coverage includes:

- Every fictional sample and independent jurisdiction/category choices.
- Actual generated PDFs, forged files, scanned-only and partial PDFs, and size/page limits.
- Exact source navigation in both directions, filters, report downloads, clipboard success/fallback, and cancelled/confirmed deletion.
- Private session headers, cross-session denial, CSRF rejection, and source HTML rendered as inert text.
- Session/submission/read retries, SSE-disconnection polling, failed jobs, empty reports, and model-mode presentation.
- Automated accessibility checks for intake, upload, preview reports, model reports, and failed-job states.

Model-mode UI and failure scenarios use controlled responses. They do not certify a live AI provider's output quality or legal accuracy. External provider calls remain disabled throughout verification.

## Isolated PostgreSQL/container stack

Run from the repository root. Replace `podman` with `docker` where appropriate. A running container engine and Compose provider are required.

```sh
export DATABASE_PASSWORD=verification-only-password
export WEB_PORT=13001
export ALLOWED_ORIGINS=http://localhost:13001
export MODEL_ENABLED=false

podman compose -p tos-verification -f compose.yml -f compose.verification.yml up --build -d
```

This creates a separate project and exposes only loopback test ports:

- Frontend: `13001`
- API: `18081`
- PostgreSQL: `15432`

Once `/actuator/health` on port 18081 and `/api/session` on port 13001 respond successfully:

```sh
# backend/: use the test database, never a development or production database
mvn -B -ntp verify \
  -Dspring.datasource.url=jdbc:postgresql://localhost:15432/contracts_test \
  -Dspring.datasource.username=contracts \
  -Dspring.datasource.password=verification-only-password

# frontend/: do not start duplicate services or rebuild the host frontend
E2E_EXTERNAL_SERVICES=true E2E_BASE_URL=http://localhost:13001 npm run test:e2e

# repository root: restarts ONLY the verification project's backend
python3 scripts/verify-containers.py --engine podman
```

The Python verifier checks actual HTTP requests, completed jobs, evidence, ownership, CSRF, non-caching, persistence across an API-container restart, exact text limits, the servlet multipart limit, and deletion. It removes its own reports. Keep the verification environment variables exported when invoking it, because Compose also reads them during restart.

The verification database initializer creates a separate `contracts_test` database on a fresh volume. Never run integration tests against the live worker's `contracts` database. If reusing a volume created before this initializer existed, create `contracts_test` once using the database's `psql` client, or recreate only the disposable verification project and its volume.

Stop and remove only this disposable test project when finished:

```sh
podman compose -p tos-verification -f compose.yml -f compose.verification.yml down --volumes
```

The command removes this project's test database volume. Do not substitute another project name or use a production environment.

# Terms of Surrender

An evidence-first contract interpreter: understand consequential terms, verify the source, and decide what to do next.

## Implementation status

This repository is building the first working vertical slice, not claiming a complete legal-advice product. See [the architecture](docs/architecture.md) and [roadmap](docs/roadmap.md) for scope and safety boundaries. The original [idea.txt](idea.txt) is retained as brainstorming context.

### Local development

Requirements: Java 21+, Maven 3.9+, Node.js 22+ and npm.

```sh
# Terminal 1
cd backend
mvn spring-boot:run

# Terminal 2
cd frontend
npm ci
npm run dev
```

Open http://localhost:3000. The frontend proxies `/api` to the backend at http://localhost:8080, keeping browser requests same-origin. No model API key or Docker is required for the clearly labeled rule-based preview. Local data is stored in `backend/.data/` and expires after 24 hours by default.

Configuration, verification commands, and deployment notes are documented as the corresponding components are implemented.

## Trust boundaries

- Findings must point to exact passages in the supplied document.
- Contract explanation is not a conclusion about legality or enforceability.
- US and India are jurisdiction contexts; freelance is a contract category.
- No jurisdiction-specific legal claims are enabled without a reviewed legal corpus.
- Private documents are never published or cached across anonymous sessions.
- No notices are sent, and no legal deadlines are invented.
- Do not upload sensitive production documents to an unreviewed development deployment.

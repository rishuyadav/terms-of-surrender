# Terms of Surrender

**Paste a contract. Get findings you can actually verify.**

Terms of Surrender is an evidence-first contract interpreter. It reads the agreements you're about to sign — subscriptions, gym memberships, app terms, freelance contracts — and tells you what the important clauses actually mean, in plain English, with the exact source text so you can check every claim.

It is not a law firm and not an autonomous AI lawyer. It tells you what the contract *says*, surfaces the questions worth asking, and lets you read the evidence yourself.

---

## What it does

1. **You paste text, upload a PDF/DOCX, or drop a public URL.** Terms of Surrender extracts the readable text.

2. **It finds consequential clauses.** Automatic-renewal traps, one-sided arbitration, broad IP assignments, unilateral change rights, and more — matched against the actual words in your document.

3. **Every finding has a source.** Click any finding to see the exact paragraph it came from, highlighted in the document. No citation, no finding.

4. **A "Start here" section** highlights the 2-3 most important things to read first, so you know where to focus.

5. **A "Before you agree" checklist** gives you concrete actions: what to ask the other party (ASK), what facts to check (VERIFY), and what options you have (CONSIDER).

6. **You can ask questions.** With an AI provider configured, type a specific question ("What happens if I cancel after 30 days?") and get a grounded answer that cites the exact clause — or an explicit "not found" if the document doesn't say.

7. **Download your report.** Export as JSON for your records or HTML to read offline.

---

## What it doesn't do

- It doesn't give legal advice or tell you whether a clause is enforceable.
- It doesn't know what law applies to your specific situation.
- It can't read scanned PDFs or images (text-based PDFs and DOCX only).
- It doesn't store your documents after 24 hours, and it has no account system.
- Public AI demos can retain a keyed network identifier and usage counters beyond report expiry to prevent resetting free allowances. Raw IPs are not stored in that quota ledger.

---

## Try it now

**No account. No login. Paste and go.**

The home page includes four fictional sample agreements — a US gym membership, an Indian app's terms, a US freelance contract, and an Indian freelance agreement — so you can see a real report before uploading anything of your own.

---

## Running locally

**Requirements:** Java 21, Maven 3.9+, Node.js 20+ and npm.

```sh
# Terminal 1 — backend
cd backend
mvn spring-boot:run

# Terminal 2 — frontend
cd frontend
npm ci
npm run dev
```

Open **http://localhost:3000**. The frontend proxies `/api` to the backend at port 8081.

No AI key is required. Without one, you get an offline rules-based preview: local patterns identify explicit renewal deadlines, trial conversion, cancellation costs, conditional freelance payment, IP transfer before payment, and unpaid work on termination. Other matches are labeled as topics to review, not established adverse consequences. Local data is stored in `backend/.data/` and expires automatically.

---

## Enable AI-assisted analysis

Export these variables before starting the backend:

```sh
export MODEL_ENABLED=true
export MODEL_API_KEY='your-provider-key'
export MODEL_NAME='your-provider-model-id'
# OpenAI-compatible base URL (default: https://api.openai.com/v1)
export MODEL_BASE_URL='https://api.openai.com/v1'
cd backend && mvn spring-boot:run
```

With a model configured, you get:
- Full clause-by-clause AI analysis with explanations
- A document summary and cross-section awareness
- Defined-term resolution (the model knows what "the Service" means)
- The "Ask the Contract" Q&A panel
- Server-derived priorities and action checklist

**Privacy note:** Extracted text is sent to your configured provider. Review that provider's data retention and regional processing terms before uploading private documents. The test suite never calls a paid provider.

For the public Vercel/Render deployment, enable the persistent IP-limited demo: five AI analyses and eight independent AI questions per network, with no automatic reset. No quota counters or advance warnings are shown. Further analyses use keyword preview; further questions receive an inline service message without hiding the Q&A controls. See [deployment instructions](docs/operations.md#public-ai-demo-vercel--render) before enabling the provider in production. Local AI remains unrestricted unless `AI_DEMO_ENABLED=true`.

---

## Docker / containers

With Docker Compose installed:

```sh
cp .env.example .env
# Set a strong DATABASE_PASSWORD in .env, then:
docker compose up --build
```

The frontend is available at `http://localhost:3000`. PostgreSQL and the API communicate on the internal container network.

For a public deployment, set `SECURE_COOKIES=true`, configure exact `ALLOWED_ORIGINS`, use HTTPS, and add ingress-level rate limits. See [`docs/operations.md`](docs/operations.md).

---

## Verifying the build

```sh
# Backend — from backend/
mvn -B -ntp verify
mvn spotless:apply    # apply Java formatting

# Frontend — from frontend/
npm run typecheck
npm run lint
npm run build
```

---

## How findings work

- **HIGH** — explicit wording with a potentially significant practical consequence, not a judgment that a term is illegal or unenforceable. In the offline preview, an ordinary renewal mention alone is not enough.
- **REVIEW** — a clause worth reading carefully, where context or a missing attachment might change its meaning.
- **FAVORABLE** — a clause that explicitly grants you a right, protection, or option (e.g. refund provision, explicit opt-out, notice before price changes).

Every finding must point to an exact passage from your document. The source validation rejects invented quotes — if the evidence can't be found in the text, the finding is withheld.

The `preview-2.1` engine highlights the operative sentence and keeps its surrounding passage separately available for checking conditions and exceptions. Recognized unresolved references, separate qualifications, and split paragraphs downgrade findings to review. It does not resolve distant exceptions, choose between plans or regions, or follow linked policies. It assumes the reader is the customer for consumer agreements and the service provider for freelance agreements. Zero high-impact matches is not a safety endorsement. See [preview rules and evaluation](docs/preview-rules.md).

The four original fictional samples contain membership/plan schedules and, for freelance engagements, included statements of work and commercial schedules. They are product demonstrations, **not templates to sign or reuse**. One deliberately balanced sample demonstrates that realistic contracts need not produce high-impact findings.

---

## Architecture in brief

| Layer | Technology |
|---|---|
| Frontend | Next.js · React · TypeScript |
| Backend | Spring Boot 3 · Java 21 |
| Local storage | H2 (file-backed) |
| Production database | PostgreSQL |
| AI analysis | Any OpenAI-compatible chat-completions API |
| File parsing | Isolated worker processes (Apache PDFBox, Apache POI) |
| Session | Anonymous cookie-based, 24-hour retention |

Anonymous sessions expire automatically. No account or identifying information is required from the user. Public-demo quota identifiers are pseudonymous network data, not a claim of complete anonymity.

---

## Trust boundaries

- **Findings are not legal opinions.** They explain what the contract text says, not whether it's enforceable or what a court would decide.
- **Jurisdiction is context, not law.** Choosing "United States" focuses analysis on consumer-relevant patterns; it does not invoke a reviewed legal corpus.
- **Citations are verified, not infallible.** Quote validation confirms the passage exists in the document; it does not prove the interpretation is correct.
- **External documents are out of scope.** If a contract references "Schedule A" and Schedule A isn't in the supplied text, that content is not analyzed.
- **Q&A answers require document evidence.** If a question can't be answered from the text, the answer is withheld — not guessed.

See [`docs/architecture.md`](docs/architecture.md) for the full design and [`docs/operations.md`](docs/operations.md) for deployment configuration.

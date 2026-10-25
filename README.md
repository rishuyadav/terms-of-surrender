<div align="center">

<img src="https://img.shields.io/badge/early%20preview-open-6d293b?style=flat-square&labelColor=1a1916" alt="Early Preview" />
<img src="https://img.shields.io/badge/no%20account%20required-✓-416047?style=flat-square&labelColor=1a1916" alt="No account required" />
<img src="https://img.shields.io/badge/self--hostable-✓-8b641c?style=flat-square&labelColor=1a1916" alt="Self-hostable" />
<img src="https://img.shields.io/github/license/rishuyadav/terms-of-surrender?style=flat-square&labelColor=1a1916&color=555" alt="License" />

<br /><br />

```
┌─────────────────────────────────────────────────────┐
│                                                     │
│   terms of surrender.                               │
│                                                     │
│   Paste a contract. Get plain-English findings      │
│   with the exact source text behind every claim.   │
│                                                     │
└─────────────────────────────────────────────────────┘
```

**You shouldn't need a law degree to know what you're agreeing to.**

[What it does](#what-it-does) · [Demo](#demo) · [Run locally](#run-locally) · [Architecture](#architecture) · [Self-host](#docker--self-host)

</div>

---

## The problem

Every week you click *"I agree"* on contracts you don't read — gym memberships, app subscriptions, freelance agreements. Buried inside is an auto-renewal trap, a one-sided arbitration clause, or a clause that hands your IP to someone else.

**Terms of Surrender makes the fine print legible.** Paste the text, get a report in seconds. Every finding links back to the exact paragraph it came from. No citation, no finding.

---

## Demo

> No account, no login. Paste and go — or use one of the four built-in sample agreements.

```
Automatic-renewal trap found in: EVERGREEN FITNESS MEMBERSHIP AGREEMENT
─────────────────────────────────────────────────────────────────────────

  ⚠ HIGH   Auto-renewal · Cancellation window
  ───────────────────────────────────────────────────────────
  The membership renews automatically for another twelve-month
  term unless written cancellation is received at least thirty
  days before the current term ends.

  Evidence ▾
  ┌──────────────────────────────────────────────────────────┐
  │ "The membership renews automatically for another         │
  │  twelve-month term unless the club receives written      │
  │  cancellation notice at least thirty days before the     │
  │  current term ends."                    — §2, page 1     │
  └──────────────────────────────────────────────────────────┘

  Suggested question for the other party:
  "If I miss the 30-day window by even one day, am I locked
   in for another full year?"                        📋 Copy
```

```
  ✓ FAVORABLE   Refund on permanent closure
  ───────────────────────────────────────────────────────────
  If the club closes permanently, prepaid amounts for unused
  membership months will be refunded.

  ⚠ REVIEW   Arbitration clause — opt-out available
  ───────────────────────────────────────────────────────────
  Disputes resolved by binding arbitration, but you may
  opt out within 30 days of signing by written notice.
  The opt-out address is only in the welcome pack.
```

---

## What it does

| Step | What happens |
|------|-------------|
| **1. Paste, upload, or drop a URL** | Text, native PDF, DOCX, or a public URL — Terms of Surrender extracts the readable content |
| **2. Findings are surfaced** | Auto-renewal, one-sided arbitration, broad IP assignment, unilateral change rights, liability caps, and more |
| **3. Every finding has a source** | The exact paragraph is highlighted in the original document — click any finding to jump to it |
| **4. Start here** | 2–3 highest-priority items to read first, so you know where to focus |
| **5. Before you agree checklist** | Concrete actions: what to **ASK** the other party, what to **VERIFY**, what to **CONSIDER** |
| **6. Ask the contract** | Type a specific question — *"What happens if I cancel after 30 days?"* — and get a grounded answer that cites the exact clause, or an explicit "not found" |
| **7. Export** | Download your report as JSON or offline-readable HTML |

---

## Finding severity

```
  ⚠ HIGH      — Potentially significant consequence
                e.g. automatic renewal, broad IP transfer,
                unilateral termination without cause

  ~ REVIEW    — Worth reading carefully; context matters
                e.g. arbitration clause, liability cap,
                undefined attachment referenced

  ✓ FAVORABLE — Explicitly grants you a right or protection
                e.g. refund provision, price-change opt-out,
                explicit cancellation window
```

Every finding is validated: if the quoted evidence can't be found verbatim in the document, the finding is withheld.

---

## What it doesn't do

- ✗ Give legal advice or tell you whether a clause is enforceable
- ✗ Read scanned PDFs or images (text-based only)
- ✗ Retain your documents after 24 hours, or require an account
- ✗ Know what law applies to your specific situation

---

## Run locally

**Requirements:** Java 21, Maven 3.9+, Node.js 20+

```sh
# Terminal 1 — backend
cd backend
mvn spring-boot:run

# Terminal 2 — frontend
cd frontend
npm ci
npm run dev
```

Open **http://localhost:3000**.

No AI key is required. Without one you get a rule-based keyword analysis — clearly labeled, useful for the most common clause patterns. Local data lives in `backend/.data/` and expires automatically.

### Enable AI-assisted analysis

```sh
export MODEL_ENABLED=true
export MODEL_API_KEY='your-key'
export MODEL_NAME='gpt-4o'                          # or any OpenAI-compatible model
export MODEL_BASE_URL='https://api.openai.com/v1'   # default

cd backend && mvn spring-boot:run
```

With a model configured you also get:
- Full clause-by-clause analysis with explanations
- Document summary with cross-section awareness
- Defined-term resolution (the model knows what "the Service" means)
- The **Ask the Contract** Q&A panel
- Server-derived priorities and action checklist

> **Privacy:** Extracted text is sent to your configured provider. Review that provider's data retention policy before uploading private documents.

---

## Docker / self-host

```sh
cp .env.example .env
# Set a strong DATABASE_PASSWORD in .env, then:
docker compose up --build
```

Frontend at `http://localhost:3000`. PostgreSQL and the API talk on the internal container network.

For a public deployment: set `SECURE_COOKIES=true`, configure exact `ALLOWED_ORIGINS`, use HTTPS, and add ingress-level rate limits. See [`docs/operations.md`](docs/operations.md).

---

## Architecture

```
┌─────────────────────┐     ┌──────────────────────────────────────────┐
│                     │     │                                          │
│   Browser           │────▶│   Next.js frontend                       │
│   (React 19)        │◀────│   TypeScript · plain CSS · SSE streaming │
│                     │     │                                          │
└─────────────────────┘     └───────────────┬──────────────────────────┘
                                            │ /api proxy
                                            ▼
                             ┌──────────────────────────┐
                             │                          │
                             │   Spring Boot 3          │
                             │   Java 21                │
                             │                          │
                             │  ┌─────────┐ ┌────────┐ │
                             │  │ Ingest  │ │  Jobs  │ │
                             │  │ & parse │ │ engine │ │
                             │  └────┬────┘ └───┬────┘ │
                             │       │           │      │
                             │  ┌────▼───────────▼────┐ │
                             │  │  Evidence validator  │ │
                             │  │  (quote must exist   │ │
                             │  │   in source text)    │ │
                             │  └──────────────────────┘ │
                             │                          │
                             └──────────┬───────────────┘
                                        │
                          ┌─────────────┼──────────────┐
                          ▼             ▼               ▼
                    ┌──────────┐  ┌──────────┐  ┌──────────────┐
                    │ H2 (dev) │  │PostgreSQL│  │  AI provider │
                    │ file-    │  │(prod)    │  │  (optional,  │
                    │ backed   │  │          │  │  OpenAI-compat│
                    └──────────┘  └──────────┘  └──────────────┘
```

| Layer | Technology |
|-------|-----------|
| Frontend | Next.js · React 19 · TypeScript |
| Backend | Spring Boot 3 · Java 21 |
| Local storage | H2 (file-backed, zero setup) |
| Production database | PostgreSQL |
| AI analysis | Any OpenAI-compatible chat-completions API |
| File parsing | Isolated JVM worker processes (Apache PDFBox, Apache POI) |
| Sessions | Anonymous cookie-based · 24-hour retention · no PII |

PDF and DOCX parsing runs in short-lived, isolated JVM worker processes outside the main API process. See [`docs/parser-isolation.md`](docs/parser-isolation.md).

---

## Trust model

> *"The model said so"* is not evidence. Every finding must cite a passage that exists verbatim in the document you supplied.

- **Findings explain, not advise.** They describe what the contract text says — not whether it's enforceable or what a court would decide.
- **Context is descriptive, not legal.** Selecting "United States" focuses analysis on relevant patterns; it does not invoke a reviewed legal corpus.
- **Citations are validated.** Quote validation confirms the passage exists; it does not prove the interpretation is correct.
- **External references are out of scope.** If a contract says "see Schedule A" and Schedule A isn't in the text, it isn't analysed.
- **Q&A answers require document evidence.** If a question can't be answered from the supplied text, the answer is withheld — not guessed.

---

## Verify the build

```sh
# Backend
cd backend
mvn -B -ntp verify
mvn spotless:apply      # apply Java formatting

# Frontend
cd frontend
npm run typecheck
npm run lint
npm run test
npm run build
```

---

## Roadmap

See [`docs/roadmap.md`](docs/roadmap.md) for what's planned. In brief:

- Scanned PDF support (OCR pipeline)
- Side-by-side contract comparison
- Clause history / redline tracking
- Reviewed legal-rule corpus (jurisdiction-specific patterns with cited sources)
- Browser extension for in-page analysis

---

## Contributing

Bug reports, feature requests, and pull requests are welcome. Open an issue to discuss anything substantial before sending a PR.

```sh
# Run the full check suite before opening a PR
cd backend && mvn -B -ntp verify
cd frontend && npm run typecheck && npm run lint && npm run test
```

---

<div align="center">

**Terms of Surrender** is not a law firm. It reads contracts — you decide what to do with the findings.

<br />

⭐ If this saves you from a bad contract, a star helps others find it.

</div>

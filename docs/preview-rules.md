# Offline preview v2

This is a deterministic, local-pattern preview, not AI analysis, legal advice, an enforceability test, or a full contract interpreter. Existing `preview-1`, `preview-2` and model reports remain readable. Newly generated reports use `preview-2.1`, which fixes evidence granularity without changing the report schema or detection rules.

## Supported consequences

| Detector | Required local evidence | Not sufficient |
| --- | --- | --- |
| Renewal deadline | Automatic renewal and an advance notice/cancellation period in the same sentence | Ordinary recurring billing; a reminder date by itself |
| Trial conversion | Trial plus explicit automatic subscription/billing transition | Merely offering a trial; a cancellation instruction without a conversion obligation |
| Cancellation cost | Cancellation/early termination plus an express fee, remaining-payment formula, or forfeiture | A general refund policy; a waived fee |
| Conditional freelance payment | Payment/invoicing dependent on approval, acceptance, or upstream payment | An invoice mention, ordinary net terms, or an inferred missing deadline |
| IP before payment | Transfer/vesting to the client expressly on creation or before payment | Merely mentioning intellectual property; transfer only after payment |
| Unpaid work on termination | Termination plus express exclusion of compensation for unaccepted/in-progress work | Merely allowing termination; absence of a payment provision |

Consumer rules assume the reviewing reader is the customer/member. Freelance rules assume the reader is the service provider. Other/unspecified categories receive neutral topic navigation. A location selection does not prove a regional clause applies. No economic or legal risk score is calculated.

## Evidence and conservatism

- Normalize whitespace, typographic apostrophes and common hyphens only for matching; cite the original, exactly validated operative sentence. Preserve its absolute offset while splitting, so repeated identical sentences are not confused.
- Match within bounded sentence windows, never by concatenating amounts and conditions from unrelated paragraphs. Ignore recognized headings for matching. Highlight only primary evidence, not the paragraph used for context.
- Inspect the surrounding block for local qualifications and retain it in the document. The finding card and HTML export offer a separate expandable surrounding passage; that context is not highlighted. Link an immediately following recognized qualifying paragraph. References and paragraph fragmentation lower consequence findings to REVIEW; a reference is not automatically resolved.
- A two-sentence relationship can be missed. Distant qualifications, unusual drafting, incorrect category choices, scanned text, and missing policies can change the interpretation. Limitations remain visible.
- Inspect the whole supplied document before selecting up to three distinct passages per consequence family. Repeated normalized sentences and multiple hits in the same block are deduplicated within that family. Other topics have one representative passage each; this is not an exhaustive inventory.
- Rank HIGH before REVIEW before FAVORABLE. Among consequence findings use a documented practical-attention order: cancellation cost, unpaid work, IP transfer, conditional payment, renewal deadline, trial conversion. This is a deterministic navigation policy, not a legal severity ranking or comparison of monetary losses. Tie-break by source position.
- Favorable claims require affirmative wording for the reviewing party. Non-refundability, wrong-party refunds, confidentiality prohibitions and non-exclusive licenses must not become protections or work restrictions. Protections do not subtract from or cancel consequential findings.
- No absence claims: not finding a cap, opt-out, deadline or protection cannot establish that it is absent. No automatic external-policy fetches or new provider calls.

Source rendering splits at all evidence boundaries without duplicating text. Unselected shared ranges use HIGH > REVIEW > FAVORABLE; a selected finding instead owns its active color. Shared ranges expose an explicit finding chooser, including for previously saved broad-range reports. Selecting an already active range navigates back to its card; the adjacent count button still allows choosing another finding. The surrounding block is marked with a border, not a full-paragraph fill. Finding-card filters do not hide source highlights. Existing saved report ranges are not rewritten; reanalyze an older preview to obtain narrower sentence evidence.

The 2.1 highlighting correction was verified by rerunning the existing 153 backend tests, 18 frontend tests and 55 selected browser checks, plus direct browser checks of identical, nested and crossing overlaps, active colors, source integrity, repeated/Unicode sentence offsets, context export, chooser dismissal and keyboard focus return. No new test cases were added; existing expectations were updated for sentence evidence and the engine version.

## Product examples and testing

`frontend/src/lib/sample-documents.json` contains the exact original fictional documents displayed by the product. Maven copies this one file into test resources; the backend sample tests analyze those same paragraphs. Do not copy new production dependencies into the backend from the frontend. The four expected high-impact detector sets are:

| Sample | Expected HIGH detector families |
| --- | --- |
| US gym | Cancellation cost; advance renewal deadline |
| India app | Trial conversion |
| US freelance | Conditional payment; IP before payment; unpaid work on termination |
| India freelance | None; ordinary review topics and a qualified portfolio option remain |

All samples are unsigned demonstrations, not reusable legal templates. Most schedules are included; the app privacy notice and gym arbitration rules deliberately illustrate external-document limitations.

The test sets serve different purposes:

1. `PreviewConsequencesTest` and `PreviewProtectionsTest`: original positive/negative clause pairs, role assumptions, exceptions, stable offsets, repeated wording, output limits, split blocks and maximum-size input.
2. `PreviewPublishedPatternsTest`: 12 original paraphrases inspired by Adobe, Spotify, Zoho and WordPress.com terms, with source URLs and provenance in `backend/src/test/resources/preview/published-patterns.json`. These are development regressions, **not an independent holdout**, exact current company terms, or legal conclusions about those companies.
3. `PreviewSamplesTest`: complete product examples with independently stated expected detector sets.
4. `PreviewExtractionTest`: generated PDF line wrapping and DOCX runs through extraction and consequence classification.
5. `frontend/e2e/preview-v2.spec.ts`: live offline analysis of all samples, classifications, exact source navigation, expandable topics, checklist and JSON/HTML exports. Existing model/legacy rendering stays separate.

No tests require paid AI or live web access. Passing these tests does not establish a general 95% precision claim. Before expanding supported rules or publishing accuracy claims, obtain independently reviewed document-level labels, split by source/document family, freeze a holdout, and report emitted/correct/incorrect counts, recall per detector, false favorable claims, omitted qualifications and applicability errors. A tiny set with no errors is not representative accuracy evidence.

## Verification run — 29 October 2026

- Backend: `mvn -B -ntp verify` — 153 tests passed, including Checkstyle and Spotless verification.
- Frontend: 18 Vitest tests passed; ESLint, TypeScript checking and the production build passed.
- Selected production-browser regressions: 55 passed without retries across Chromium desktop/mobile, Firefox and WebKit desktop/mobile. This includes all `preview-v2.spec.ts` and `contract-flow.spec.ts` cases plus the existing model-mode accessibility regression. It is not a claim that the entire historical Playwright suite was run.
- Browser checks cover exact source offsets, sample classifications, filters, source navigation in both directions, expanded topic accessibility, legacy report presentation, model report presentation, private JSON/HTML exports and deletion. Two contrast failures found during verification were fixed in the report's JSON button and checklist note.
- The complete sample lengths are approximately 1,433 / 1,502 / 1,671 / 1,932 words (gym / app / US freelance / India freelance), all below the ingestion character limit.

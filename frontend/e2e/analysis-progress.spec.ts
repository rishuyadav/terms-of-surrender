import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import type { AnalysisView, Finding } from "../src/lib/types";

const text =
  "This agreement renews automatically each year. Give written notice thirty days before renewal.";
const quote = text.slice(0, text.indexOf(".") + 1);
const finding: Finding = {
  id: "finding-1",
  category: "Renewal",
  impact: "HIGH",
  title: "Check the renewal deadline",
  explanation: "The agreement describes annual renewal.",
  whyItMatters: "Written notice is required before renewal.",
  evidence: { blockId: "p1-b1", start: 0, end: quote.length, quote },
  suggestedQuestion: "How do I provide notice?",
};
const secondFinding: Finding = {
  ...finding,
  id: "finding-2",
  impact: "REVIEW",
  title: "Confirm the notice process",
  evidence: {
    blockId: "p1-b1",
    start: quote.length + 1,
    end: text.length,
    quote: text.slice(quote.length + 1),
  },
};
const initial: AnalysisView = {
  id: "00000000-0000-4000-8000-000000000001",
  status: "PROCESSING",
  stage: "Reviewing text group 2 of 6",
  document: {
    title: "Your subscription agreement",
    sourceType: "TEXT",
    fingerprint: "fixture",
    text,
    blocks: [{ id: "p1-b1", page: 1, start: 0, end: text.length, text }],
    pageCount: 1,
    warnings: [],
  },
  context: { jurisdiction: "UNSPECIFIED", category: "CONSUMER", region: "" },
  report: null,
  errorCode: null,
  createdAt: "2026-10-31T00:00:00Z",
  expiresAt: "2026-11-01T00:00:00Z",
  version: 3,
  progress: { completedGroups: 1, totalGroups: 6, findings: [finding] },
};

test("early findings remain usable through updates, refresh, and completion", async ({ page }) => {
  let snapshot = initial;
  await page.route(`**/api/analyses/${initial.id}`, (route) => route.fulfill({ json: snapshot }));
  await page.route(`**/api/analyses/${initial.id}/events`, (route) => route.abort());
  await page.goto(`/report/${initial.id}`);
  await expect(page.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "1");
  await expect(page.locator(".finding-card")).toHaveCount(1);
  await expect(page.getByText("Early findings, not the full report.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Download report as HTML" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Download report as JSON" })).toBeDisabled();
  await expect(
    page.locator(".report-summary-card, .start-here-section, .action-checklist, .qa-section"),
  ).toHaveCount(0);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.getByRole("button", { name: "Switch to dark mode" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect
    .poll(() => page.evaluate(() => getComputedStyle(document.body).backgroundColor))
    .toBe("rgb(26, 25, 22)");
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

  await page.getByRole("button", { name: "Read the source while we review" }).click();
  await expect(page.locator(".source-pane")).toBeVisible();
  await expect(page.locator("#source-title")).toBeFocused();
  snapshot = {
    ...initial,
    version: 4,
    stage: "Reviewing text group 3 of 6",
    progress: { ...initial.progress!, completedGroups: 2, findings: [finding, secondFinding] },
  };
  await expect(page.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "2", {
    timeout: 10000,
  });
  await expect(page.locator(".source-pane")).toBeVisible();
  await expect(page.locator(".finding-card")).toHaveCount(2);
  await page.reload();
  await expect(page.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "2");
  await expect(page.locator(".finding-card")).toHaveCount(2);

  snapshot = {
    ...initial,
    status: "COMPLETED",
    stage: "Analysis complete",
    version: 5,
    progress: null,
    report: {
      mode: "MODEL_ASSISTED",
      engineVersion: "model-1",
      findings: [finding, secondFinding],
      limitations: ["Exact source checks do not verify interpretations."],
      legalContextAvailable: false,
      contextLabel: "Not specified",
      summary: "This agreement describes annual renewal.",
      priorities: [finding.id],
      checklist: [{ action: "VERIFY", text: "Check the written notice deadline." }],
    },
  };
  await expect(page.locator(".analysis-mode-banner")).toBeVisible({ timeout: 10000 });
  await expect(page.getByRole("progressbar")).toHaveCount(0);
  await expect(page.locator(".partial-findings-notice")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Download report as HTML" })).toBeEnabled();
  await expect(page.locator(".start-here-section, .action-checklist")).toHaveCount(2);
});

test("failed partial analysis never presents a complete report", async ({ page }) => {
  await page.route(`**/api/analyses/${initial.id}`, (route) =>
    route.fulfill({
      json: {
        ...initial,
        status: "FAILED",
        stage: "Analysis could not be completed",
        errorCode: "ANALYSIS_FAILED",
        version: 4,
      },
    }),
  );
  await page.goto(`/report/${initial.id}`);
  const tab = page.getByRole("button", { name: /^Findings \(/ });
  if (await tab.isVisible()) await tab.click();
  await expect(page.getByText("Incomplete analysis.", { exact: true })).toBeVisible();
  await expect(page.locator(".finding-card")).toHaveCount(1);
  await expect(page.locator(".analysis-mode-banner, .qa-section")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Download report as JSON" })).toBeDisabled();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

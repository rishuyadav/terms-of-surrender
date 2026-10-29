import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { analyzeSample, openFindings } from "./helpers";
import type { AnalysisView } from "../src/lib/types";

const cases = [
  { sample: "The gym membership", expected: ["cancellation-cost", "renewal-deadline"] },
  { sample: "The app subscription", expected: ["trial-conversion"] },
  {
    sample: "The freelance contract",
    expected: ["ip-before-payment", "payment-condition", "unpaid-work-on-termination"],
  },
  { sample: "The India services contract", expected: [] },
];

for (const example of cases) {
  test(`preview-2 analyzes the complete ${example.sample}`, async ({ page, context }) => {
    await analyzeSample(page, example.sample);
    const id = page.url().split("/").at(-1);
    const response = await context.request.get(`/api/analyses/${id}`);
    expect(response.ok()).toBe(true);
    const view = (await response.json()) as AnalysisView;
    expect(view.report?.engineVersion).toBe("preview-2.1");
    const high = view.report!.findings.filter((finding) => finding.impact === "HIGH");
    expect([...new Set(high.map((finding) => finding.id.replace(/-\d+$/, "")))].sort()).toEqual(
      [...example.expected].sort(),
    );
    expect(view.document.text.split(/\s+/).length).toBeGreaterThan(900);
    expect(view.report?.checklist?.length).toBeGreaterThan(0);
    for (const finding of view.report!.findings) {
      expect(view.document.text.slice(finding.evidence.start, finding.evidence.end)).toBe(
        finding.evidence.quote,
      );
    }
    await expect(
      page.getByText("High impact flags explicit potentially consequential conditions.", {
        exact: false,
      }),
    ).toBeVisible();
    await expect(page.locator(".action-checklist")).toBeVisible();
    if (high.length === 0) {
      await expect(page.locator(".summary-stat.high > span")).toHaveText("0");
    } else {
      await openFindings(page);
      await page.getByRole("button", { name: "High impact", exact: true }).click();
      await expect(page.locator(".finding-card")).toHaveCount(high.length);
      await page.getByRole("button", { name: "Show in document" }).first().click();
      await expect(page.locator(".source-highlight.active").first()).toBeVisible();
      await expect(page.locator(".source-highlight.active").first()).toContainText(
        high[0].evidence.quote,
      );
    }
  });
}

test("topic-only findings are expandable and source navigation remains usable", async ({
  page,
}) => {
  await analyzeSample(page);
  await openFindings(page);
  const topics = page.locator(".other-topics");
  await expect(topics).not.toHaveAttribute("open");
  await topics.locator("summary").first().click();
  await expect(topics.locator(".finding-card").first()).toBeVisible();
  const title = await topics.locator(".finding-card h3").first().textContent();
  await topics.getByRole("button", { name: "Show in document" }).first().click();
  await page.locator(".source-highlight.active").first().click();
  await expect(page.getByRole("heading", { name: title!, exact: true })).toBeVisible();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

test("legacy preview reports keep their original presentation", async ({ page, context }) => {
  await analyzeSample(page);
  const id = page.url().split("/").at(-1);
  const response = await context.request.get(`/api/analyses/${id}`);
  const view = (await response.json()) as AnalysisView;
  const legacy: AnalysisView = {
    ...view,
    report: {
      ...view.report!,
      engineVersion: "preview-1",
      summary: null,
      checklist: [],
      findings: view.report!.findings.map((finding) => ({ ...finding, impact: "REVIEW" })),
    },
  };
  await page.route(`**/api/analyses/${id}`, (route) => route.fulfill({ json: legacy }));
  await page.reload();
  await expect(
    page.getByText(
      "These are topics worth inspecting, not a verdict on whether the terms are harmful or enforceable.",
      { exact: false },
    ),
  ).toBeVisible();
  await expect(page.locator(".other-topics")).toHaveCount(0);
  await expect(page.locator(".finding-card")).toHaveCount(legacy.report!.findings.length);
});

test("JSON and HTML exports include the new consequences and checklist", async ({ page }) => {
  await analyzeSample(page);
  const jsonEvent = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download report as JSON", exact: true }).click();
  const json = await jsonEvent;
  const view = JSON.parse(await readFile((await json.path())!, "utf8")) as AnalysisView;
  expect(view.report?.engineVersion).toBe("preview-2.1");
  expect(view.report?.findings.some((finding) => finding.impact === "HIGH")).toBe(true);
  expect(view.report?.checklist?.length).toBeGreaterThan(0);
  const htmlEvent = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download report as HTML", exact: true }).click();
  const html = await htmlEvent;
  const text = await readFile((await html.path())!, "utf8");
  expect(text).toContain("Cancellation can carry a charge or forfeiture");
  expect(text).toContain("BEFORE YOU AGREE");
  expect(text).toContain("High impact");
});

import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { analyzeSample } from "./helpers";
import type { AnalysisView } from "../src/lib/types";

test("server-rendered intake controls carry disabled to prevent pre-hydration interaction", async ({
  request,
}) => {
  // Fetch raw server HTML — no JavaScript runs, so this asserts the static shell exactly.
  // The result is deterministic regardless of server speed or browser hydration timing.
  const response = await request.get("/");
  expect(response.ok()).toBe(true);
  const html = await response.text();
  // Every <textarea> and <select> in the intake form must have disabled="" in the server HTML.
  // useSyncExternalStore's server snapshot returns false, so !hydrated=true → disabled.
  const textareas = [...html.matchAll(/<textarea\b([^>]*)>/g)];
  const selects = [...html.matchAll(/<select\b([^>]*)>/g)];
  expect(textareas.length).toBeGreaterThan(0);
  expect(selects.length).toBeGreaterThan(0);
  for (const [, attrs] of [...textareas, ...selects]) {
    expect(attrs).toMatch(/\bdisabled\b/);
  }
});

test("intake accepts pasted text after hydration and shows the report", async ({ page }) => {
  const text =
    "The project calendar lists a meeting on Monday morning. The parties will meet at the office to discuss their schedule and review the agenda.";
  await page.goto("/");
  await expect(page.getByLabel("Contract text", { exact: true })).toBeEditable();
  await page.getByLabel("Contract text", { exact: true }).fill(text);
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByRole("heading", { name: "No topics flagged. Keep reading." })).toBeVisible(
    { timeout: 20000 },
  );
});

test("recovers from an unavailable session service", async ({ page }) => {
  let attempts = 0;
  await page.route("**/api/session", (route) =>
    ++attempts === 1 ? route.abort() : route.continue(),
  );
  await page.goto("/");
  await page.getByRole("button", { name: "Retry connection" }).click();
  await expect(page.getByRole("button", { name: "Read the fine print" })).toBeEnabled();
  await expect(page.locator("#main").getByRole("alert")).toHaveCount(0);
});

test("keeps input and recovers after admission is temporarily denied", async ({ page }) => {
  let attempts = 0;
  await page.route("**/api/analyses", (route) =>
    ++attempts === 1
      ? route.fulfill({
          status: 429,
          contentType: "application/problem+json",
          json: { detail: "The verification queue is temporarily busy." },
        })
      : route.continue(),
  );
  await page.goto("/");
  await page.getByRole("button", { name: /The gym membership/ }).click();
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.locator("#main").getByRole("alert")).toContainText("temporarily busy");
  await expect(page.getByLabel("Contract text", { exact: true })).toHaveValue(/EVERGREEN FITNESS/);
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByText("Keyword preview, not an AI or legal assessment.")).toBeVisible({
    timeout: 20000,
  });
});

test("falls back to saved-report polling if the event stream disconnects", async ({ page }) => {
  let reads = 0;
  let streamAttempted = false;
  await page.route(/\/api\/analyses\/[^/]+$/, async (route) => {
    if (route.request().method() !== "GET") return route.continue();
    const response = await route.fetch();
    const view = (await response.json()) as AnalysisView;
    reads++;
    await route.fulfill({
      response,
      json: reads === 1 ? { ...view, status: "PROCESSING", report: null } : view,
    });
  });
  await page.route("**/api/analyses/*/events", (route) => {
    streamAttempted = true;
    return route.abort();
  });
  await analyzeSample(page);
  expect(streamAttempted).toBe(true);
  expect(reads).toBeGreaterThanOrEqual(2);
});

test("a failed model job is explicit and does not silently show keyword findings", async ({
  page,
  context,
}) => {
  await analyzeSample(page);
  const id = page.url().split("/").at(-1);
  const view = (await (await context.request.get(`/api/analyses/${id}`)).json()) as AnalysisView;
  await page.route(`**/api/analyses/${id}`, (route) =>
    route.fulfill({
      json: { ...view, status: "FAILED", report: null, errorCode: "ANALYSIS_FAILED" },
    }),
  );
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "The analysis couldn’t be completed." }),
  ).toBeVisible();
  await expect(page.getByText(/We have not substituted a different analysis mode/)).toBeVisible();
  await expect(page.getByText("Keyword preview, not an AI or legal assessment.")).toHaveCount(0);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

test("model mode renders high-impact and favorable findings accessibly", async ({
  page,
  context,
}) => {
  await analyzeSample(page);
  const id = page.url().split("/").at(-1);
  const view = (await (await context.request.get(`/api/analyses/${id}`)).json()) as AnalysisView;
  const modelView: AnalysisView = {
    ...view,
    report: {
      ...view.report!,
      mode: "MODEL_ASSISTED",
      findings: view.report!.findings.map((finding, index) => ({
        ...finding,
        impact: index === 0 ? "HIGH" : "FAVORABLE",
      })),
    },
  };
  await page.route(`**/api/analyses/${id}`, (route) => route.fulfill({ json: modelView }));
  await page.reload();
  await expect(
    page.getByText("AI-assisted explanation. Verify before relying on it."),
  ).toBeVisible();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.getByRole("button", { name: "Protections", exact: true }).click();
  await expect(page.locator(".finding-card")).toHaveCount(modelView.report!.findings.length - 1);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

test("a report read can be retried after a temporary backend error", async ({ page }) => {
  await analyzeSample(page);
  let reads = 0;
  await page.route(/\/api\/analyses\/[^/]+$/, (route) =>
    ++reads === 1
      ? route.fulfill({ status: 503, json: { detail: "Report service temporarily unavailable." } })
      : route.continue(),
  );
  await page.reload();
  await expect(page.getByRole("heading", { name: "We couldn’t open this report." })).toBeVisible();
  await page.getByRole("button", { name: "Try again", exact: true }).click();
  await expect(page.getByText("Keyword preview, not an AI or legal assessment.")).toBeVisible();
});

test("private session and response headers are correct", async ({ page, context }) => {
  await analyzeSample(page);
  const cookie = (await context.cookies()).find((entry) => entry.name === "tos_session");
  expect(cookie?.httpOnly).toBe(true);
  expect(cookie?.sameSite).toBe("Lax");
  expect(cookie?.path).toBe("/api");
  expect(await page.evaluate(() => document.cookie.includes("tos_session"))).toBe(false);
  const id = page.url().split("/").at(-1);
  const response = await context.request.get(`/api/analyses/${id}`);
  expect(response.headers()["cache-control"]).toContain("no-store");
  const rejected = await context.request.delete(`/api/analyses/${id}`, {
    headers: { Origin: "https://attacker.example" },
  });
  expect(rejected.status()).toBe(403);
  expect((await context.request.get(`/api/analyses/${id}`)).status()).toBe(200);
});

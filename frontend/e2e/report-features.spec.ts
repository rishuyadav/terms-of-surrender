import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { analyzeSample, openFindings, pdfFixture } from "./helpers";
import type { AnalysisView } from "../src/lib/types";

test("uploads an actual PDF and shows verified page evidence", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Upload PDF / DOCX", exact: true }).click();
  await page
    .getByLabel("Upload contract PDF or DOCX")
    .setInputFiles({ name: "agreement.pdf", mimeType: "application/pdf", buffer: pdfFixture() });
  await expect(page.getByText("agreement.pdf", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByRole("heading", { name: "Check how renewal works" })).toBeVisible({
    timeout: 20000,
  });
  const renewal = page.getByRole("article", { name: "Check how renewal works" });
  await renewal.getByRole("button", { name: "Show in document" }).click();
  await expect(page.locator(".source-highlight.active").first()).toContainText(
    "renews automatically",
  );
  await expect(page.getByText("PDF · 1 PAGE", { exact: true })).toBeVisible();
});

test("rejects a forged PDF, then permits replacing it with a valid file", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Upload PDF / DOCX", exact: true }).click();
  await page.getByLabel("Upload contract PDF or DOCX").setInputFiles({
    name: "forged.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("This is not a PDF"),
  });
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.locator("#main").getByRole("alert")).toContainText("text-based PDF or DOCX");
  await page.getByRole("button", { name: "Remove file" }).click();
  await page
    .getByLabel("Upload contract PDF or DOCX")
    .setInputFiles({ name: "valid.pdf", mimeType: "application/pdf", buffer: pdfFixture() });
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByText("Keyword preview, not an AI or legal assessment.")).toBeVisible({
    timeout: 20000,
  });
});

test("rejects scanned-only PDFs and warns about partially unreadable PDFs", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Upload PDF / DOCX", exact: true }).click();
  const input = page.getByLabel("Upload contract PDF or DOCX");
  await input.setInputFiles({
    name: "scan.pdf",
    mimeType: "application/pdf",
    buffer: pdfFixture([[]]),
  });
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.locator("#main").getByRole("alert")).toContainText("OCR");
  await page.getByRole("button", { name: "Remove file" }).click();
  await input.setInputFiles({
    name: "partial.pdf",
    mimeType: "application/pdf",
    buffer: pdfFixture([
      [
        "This agreement renews automatically each year unless you give thirty days notice.",
        "The monthly membership costs fifty dollars and additional charges may apply.",
      ],
      [],
    ]),
  });
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByText("Some content may be missing", { exact: true })).toBeVisible();
  await expect(
    page.getByText("Page 2 has little readable text. Scanned content may be missing."),
  ).toBeVisible();
});

test("enforces file-size and page-count limits", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Upload PDF / DOCX", exact: true }).click();
  const input = page.getByLabel("Upload contract PDF or DOCX");
  await input.setInputFiles({
    name: "large.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.alloc(10 * 1024 * 1024 + 1),
  });
  await expect(page.locator("#main").getByRole("alert")).toContainText("smaller than 10 MB");
  await input.setInputFiles({
    name: "long.pdf",
    mimeType: "application/pdf",
    buffer: pdfFixture(
      Array.from({ length: 41 }, () => [
        "This agreement describes the terms and conditions of membership.",
      ]),
    ),
  });
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.locator("#main").getByRole("alert")).toContainText("40 pages or fewer");
});

test("filters findings, navigates both ways, and exports a complete private report", async ({
  page,
}) => {
  await analyzeSample(page);
  await page.getByRole("button", { name: "High impact", exact: true }).click();
  await expect(page.locator(".finding-card.impact-high").first()).toBeVisible();
  await expect(page.locator(".finding-card:not(.impact-high)")).toHaveCount(0);
  await page.getByRole("button", { name: "Review", exact: true }).click();
  const review = page.locator(".finding-card.impact-review").first();
  await expect(review).toBeVisible();
  const title = await review.getByRole("heading").textContent();
  await review.getByRole("button", { name: "Show in document" }).click();
  await page.locator(".source-highlight.active").first().click();
  await expect(page.getByRole("heading", { name: title!, exact: true })).toBeVisible();
  const downloadEvent = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download report as JSON" }).click();
  const download = await downloadEvent;
  expect(download.suggestedFilename()).toBe("contract-analysis.json");
  const path = await download.path();
  expect(path).not.toBeNull();
  const report = JSON.parse(await readFile(path!, "utf8")) as AnalysisView;
  expect(report.status).toBe("COMPLETED");
  expect(report.document.text).toContain("EVERGREEN FITNESS");
  for (const finding of report.report!.findings) {
    expect(report.document.text.slice(finding.evidence.start, finding.evidence.end)).toBe(
      finding.evidence.quote,
    );
  }
});

test("copies a question or gives a usable clipboard fallback", async ({
  page,
  context,
  browserName,
}) => {
  if (browserName === "chromium")
    await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await analyzeSample(page);
  const card = page.locator(".finding-card").first();
  await card.locator(".question-details > summary").click();
  const question = await card.locator(".question-details > p").first().textContent();
  await card.getByRole("button", { name: /^Copy (question|action)$/ }).click();
  if (browserName === "chromium") {
    await expect(card.getByRole("button", { name: "Copied", exact: true })).toBeVisible();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(question);
  } else {
    await expect(
      card
        .getByRole("button", { name: "Copied", exact: true })
        .or(
          card.getByText(/Clipboard unavailable\. Select the (question|action) above to copy it\./),
        ),
    ).toBeVisible();
  }
});

test("cancelled deletion leaves the report intact", async ({ page, context }) => {
  await analyzeSample(page);
  const id = page.url().split("/").at(-1);
  page.once("dialog", (dialog) => dialog.dismiss());
  await page.getByRole("button", { name: "Delete report", exact: true }).click();
  expect((await context.request.get(`/api/analyses/${id}`)).status()).toBe(200);
  await expect(page.getByText("Keyword preview, not an AI or legal assessment.")).toBeVisible();
});

test("reports no matches without claiming the agreement is safe", async ({ page }) => {
  await page.goto("/");
  await page
    .getByLabel("Contract text", { exact: true })
    .fill(
      "The project calendar lists a meeting on Monday morning. The parties will meet at the office to discuss their schedule and review the agenda.",
    );
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByRole("heading", { name: "No topics flagged. Keep reading." })).toBeVisible(
    { timeout: 20000 },
  );
  await expect(page.getByText(/This is not a safety endorsement/)).toBeVisible();
  await openFindings(page);
});

test("renders source HTML as text rather than executing it", async ({ page }) => {
  await page.goto("/");
  await page
    .getByLabel("Contract text", { exact: true })
    .fill(
      'This agreement renews automatically each year. <img src=x onerror="window.contractXssExecuted=true"> Written notice must be received thirty days before renewal.',
    );
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByRole("heading", { name: "Check how renewal works" })).toBeVisible({
    timeout: 20000,
  });
  await page.getByRole("button", { name: "Show in document" }).first().click();
  await expect(page.locator(".source-document")).toContainText("<img src=x");
  expect(await page.evaluate(() => "contractXssExecuted" in window)).toBe(false);
  await expect(page.locator(".source-document img")).toHaveCount(0);
});

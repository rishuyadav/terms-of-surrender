import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

test("intake and upload controls pass automated accessibility checks", async ({
  page,
}, testInfo) => {
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Read the fine print" })).toBeEnabled();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath("home.png"), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByRole("button", { name: "Upload PDF / DOCX", exact: true }).click();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

test("source-backed report passes automated accessibility checks", async ({ page }, testInfo) => {
  await page.goto("/");
  await page.getByRole("button", { name: /The app subscription/ }).click();
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByText("Keyword preview, not an AI or legal assessment.")).toBeVisible({
    timeout: 20000,
  });
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath("report.png"), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});

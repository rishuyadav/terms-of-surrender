import { expect, test } from "@playwright/test";

test("sample agreement → evidence-backed report → deletion", async ({ page, context }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /The fine print/ })).toBeVisible();
  await page.getByRole("button", { name: /The gym membership/ }).click();
  await expect(page.getByLabel("Contract text", { exact: true })).toHaveValue(/EVERGREEN FITNESS/);
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page).toHaveURL(/\/report\/[0-9a-f-]+/);
  await expect(page.getByText("Keyword preview, not an AI or legal assessment.")).toBeVisible({
    timeout: 20000,
  });
  await expect(page.getByRole("heading", { name: "Check how renewal works" })).toBeVisible();

  const reportUrl = page.url();
  const id = reportUrl.split("/").at(-1);
  const forbidden = await context.request.get(`/api/analyses/${id}`, {
    headers: { Cookie: "tos_session=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" },
  });
  expect(forbidden.status()).toBe(404);

  await page.getByRole("button", { name: "Show in document" }).first().click();
  await expect(page.locator(".source-highlight.active").first()).toBeVisible();
  await expect(page.locator(".source-highlight.active").first()).toContainText(
    "renews automatically",
  );
  await page.reload();
  await expect(page.getByText("Keyword preview, not an AI or legal assessment.")).toBeVisible();

  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Delete report", exact: true }).click();
  await expect(page).toHaveURL("/");
  await page.goto(reportUrl);
  await expect(page.getByRole("heading", { name: "We couldn’t open this report." })).toBeVisible();
});

test("India and freelance are independent context choices", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: /The freelance contract/ }).click();
  await page.getByLabel("Your location").selectOption("INDIA");
  await expect(page.getByLabel("Agreement type")).toHaveValue("FREELANCE");
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByText("India · Freelance agreement", { exact: true })).toBeVisible();
  await expect(page.getByText("Keyword preview, not an AI or legal assessment.")).toBeVisible({
    timeout: 20000,
  });
  await expect(
    page.getByRole("heading", { name: "Check ownership and license rights" }),
  ).toBeVisible();
});

test("short input produces an accessible error", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("Contract text", { exact: true }).fill("too short");
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.locator("#main").getByRole("alert")).toContainText("at least 80 characters");
});

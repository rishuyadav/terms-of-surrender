import { crc32 } from "node:zlib";
import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

/** Minimal valid OPC package, stored without compression; real ZIP headers and CRCs. */
function docxFixture(tracked = false): Buffer {
  const paragraph =
    "This agreement renews automatically each year unless written notice is received thirty days before renewal. An early termination fee of fifty dollars applies.";
  const entries: Record<string, string> = {
    "[Content_Types].xml":
      '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>',
    "_rels/.rels":
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>',
    "word/document.xml": `<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p>${tracked ? '<w:ins w:id="1">' : ""}<w:r><w:t>${paragraph}</w:t></w:r>${tracked ? "</w:ins>" : ""}</w:p></w:body></w:document>`,
  };
  const locals: Buffer[] = [];
  const directory: Buffer[] = [];
  let offset = 0;
  for (const [name, text] of Object.entries(entries)) {
    const filename = Buffer.from(name);
    const body = Buffer.from(text);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt32LE(crc32(body), 14);
    local.writeUInt32LE(body.length, 18);
    local.writeUInt32LE(body.length, 22);
    local.writeUInt16LE(filename.length, 26);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt32LE(crc32(body), 16);
    central.writeUInt32LE(body.length, 20);
    central.writeUInt32LE(body.length, 24);
    central.writeUInt16LE(filename.length, 28);
    central.writeUInt32LE(offset, 42);
    locals.push(local, filename, body);
    directory.push(central, filename);
    offset += local.length + filename.length + body.length;
  }
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(Object.keys(entries).length, 8);
  end.writeUInt16LE(Object.keys(entries).length, 10);
  end.writeUInt32LE(
    directory.reduce((size, part) => size + part.length, 0),
    12,
  );
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, ...directory, end]);
}

test("DOCX import shows evidence, extraction warnings, and honest source references", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Upload PDF / DOCX", exact: true }).click();
  await page.getByLabel("Upload contract PDF or DOCX").setInputFiles({
    name: "agreement.docx",
    mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    buffer: docxFixture(),
  });
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByRole("heading", { name: "Check how renewal works" })).toBeVisible({
    timeout: 20000,
  });
  await expect(page.getByText(/DOCX import reads body paragraphs and tables/)).toBeVisible();
  await expect(page.getByText(/DOCX has no reliable page mapping/)).toBeVisible();
  await page.locator(".finding-card").first().locator(".evidence-details > summary").click();
  await expect(
    page.locator(".finding-card").first().getByText("· extracted text", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Show in document" }).first().click();
  await expect(page.locator(".source-highlight.active").first()).toContainText(
    "renews automatically",
  );
  await expect(page.getByText("DOCX · EXTRACTED TEXT", { exact: true })).toBeVisible();
  await expect(page.locator(".block-page").first()).toHaveAttribute("aria-label", /Text block/);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.reload();
  await expect(page.getByText(/DOCX has no reliable page mapping/)).toBeVisible();
});

test("DOCX tracked changes are rejected and can be replaced without losing context", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByLabel("Your location").selectOption("INDIA");
  await page.getByLabel("Agreement type").selectOption("FREELANCE");
  await page.getByRole("button", { name: "Upload PDF / DOCX", exact: true }).click();
  const input = page.getByLabel("Upload contract PDF or DOCX");
  await input.setInputFiles({
    name: "draft.docx",
    mimeType: "application/octet-stream",
    buffer: docxFixture(true),
  });
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.locator("#main").getByRole("alert")).toContainText("tracked changes");
  await expect(page.getByLabel("Your location")).toHaveValue("INDIA");
  await expect(page.getByLabel("Agreement type")).toHaveValue("FREELANCE");
  await page.getByRole("button", { name: "Remove file" }).click();
  await input.setInputFiles({
    name: "final.docx",
    mimeType: "application/octet-stream",
    buffer: docxFixture(),
  });
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByRole("heading", { name: "Check how renewal works" })).toBeVisible({
    timeout: 20000,
  });
});

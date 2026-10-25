import { expect, type Page } from "@playwright/test";

/** A real, minimal PDF with valid byte offsets; no network or binary fixture required. */
export function pdfFixture(
  pages: string[][] = [
    [
      "SAMPLE MEMBERSHIP AGREEMENT",
      "This membership renews automatically each year.",
      "Give written notice thirty days before renewal.",
      "An early termination fee of fifty dollars applies.",
    ],
  ],
): Buffer {
  const pageIds = pages.map((_, index) => 4 + index * 2);
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    `<< /Type /Pages /Kids [${pageIds.map((id) => `${id} 0 R`).join(" ")}] /Count ${pages.length} >>`,
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
  ];
  pages.forEach((lines, index) => {
    const pageId = pageIds[index];
    const content =
      lines.length === 0
        ? ""
        : `BT /F1 11 Tf 50 750 Td 14 TL\n${lines.map((line) => `(${line.replace(/[\\()]/g, "\\$&")}) Tj T*`).join("\n")}\nET`;
    objects.push(
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents ${pageId + 1} 0 R >>`,
    );
    objects.push(`<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`);
  });
  let result = "%PDF-1.4\n";
  const offsets = [0];
  objects.forEach((object, index) => {
    offsets.push(Buffer.byteLength(result));
    result += `${index + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xref = Buffer.byteLength(result);
  result += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  result += offsets
    .slice(1)
    .map((offset) => `${String(offset).padStart(10, "0")} 00000 n \n`)
    .join("");
  result += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(result);
}

export async function analyzeSample(page: Page, sample = "The gym membership") {
  await page.goto("/");
  await page.getByRole("button", { name: new RegExp(sample) }).click();
  await page.getByRole("button", { name: "Read the fine print" }).click();
  await expect(page.getByText("Keyword preview, not an AI or legal assessment.")).toBeVisible({
    timeout: 20000,
  });
}

export async function openFindings(page: Page) {
  const tab = page.getByRole("button", { name: /^Findings \(/ });
  if (await tab.isVisible()) await tab.click();
}

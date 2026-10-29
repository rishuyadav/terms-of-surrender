import { describe, expect, it } from "vitest";
import { samples } from "./samples";

describe("fictional sample documents", () => {
  it("loads four distinct complete demonstrations within the ingestion limit", () => {
    expect(new Set(samples.map((sample) => sample.id)).size).toBe(4);
    for (const sample of samples) {
      expect(sample.text).toContain("Original fictional document");
      expect(sample.text).toContain("not a contract template");
      expect(sample.text.split(/\s+/).length).toBeGreaterThan(900);
      expect(sample.text.length).toBeLessThan(80_000);
      expect(sample.text).not.toContain("undefined");
      expect(sample.text).toContain(".example");
    }
  });

  it("includes the freelance statements of work and commercial schedules", () => {
    for (const sample of samples.filter((item) => item.context.category === "FREELANCE")) {
      expect(sample.text).toContain("SCHEDULE A —");
      expect(sample.text).toContain("SCHEDULE B —");
      expect(sample.text).not.toContain("That brief is not included");
    }
  });
});

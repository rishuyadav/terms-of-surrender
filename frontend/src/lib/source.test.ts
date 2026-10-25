import { describe, expect, it } from "vitest";
import { contextLabel, sourceSegments } from "./source";
import type { Finding, SourceBlock } from "./types";

const block: SourceBlock = { id: "p1-b1", page: 1, start: 10, end: 20, text: "abcdefghij" };
function finding(id: string, start: number, end: number): Finding {
  return { id, evidence: { blockId: block.id, start, end, quote: "" } } as Finding;
}

describe("sourceSegments", () => {
  it("preserves text and represents overlapping evidence without duplication", () => {
    const segments = sourceSegments(block, [finding("a", 12, 16), finding("b", 14, 18)]);
    expect(segments.map((segment) => segment.text).join("")).toBe(block.text);
    expect(segments).toEqual([
      { text: "ab", start: 10, findingIds: [] },
      { text: "cd", start: 12, findingIds: ["a"] },
      { text: "ef", start: 14, findingIds: ["a", "b"] },
      { text: "gh", start: 16, findingIds: ["b"] },
      { text: "ij", start: 18, findingIds: [] },
    ]);
  });

  it("ignores invalid spans and handles unflagged text", () => {
    expect(sourceSegments(block, [finding("outside", 0, 14), finding("empty", 15, 15)])).toEqual([
      { text: block.text, start: 10, findingIds: [] },
    ]);
  });

  it("keeps freelance category separate from jurisdiction", () => {
    expect(contextLabel("INDIA", "FREELANCE")).toBe("India · Freelance agreement");
    expect(contextLabel("US", "FREELANCE")).toBe("United States · Freelance agreement");
  });
});

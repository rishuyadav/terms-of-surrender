import type { Finding, SourceBlock } from "./types";

export interface SourceSegment {
  text: string;
  start: number;
  findingIds: string[];
}

/** Split on every evidence boundary so overlapping quotes never duplicate or lose text. */
export function sourceSegments(block: SourceBlock, findings: Finding[]): SourceSegment[] {
  const relevant = findings.filter(
    ({ evidence }) =>
      evidence.blockId === block.id &&
      evidence.start >= block.start &&
      evidence.end <= block.end &&
      evidence.start < evidence.end,
  );
  const boundaries = new Set([block.start, block.end]);
  relevant.forEach(({ evidence }) => {
    boundaries.add(evidence.start);
    boundaries.add(evidence.end);
  });
  const ordered = [...boundaries].sort((a, b) => a - b);
  return ordered.slice(0, -1).map((start, index) => {
    const end = ordered[index + 1];
    return {
      start,
      text: block.text.slice(start - block.start, end - block.start),
      findingIds: relevant
        .filter(({ evidence }) => evidence.start <= start && evidence.end >= end)
        .map(({ id }) => id),
    };
  });
}

export function contextLabel(jurisdiction: string, category: string): string {
  const country =
    jurisdiction === "US"
      ? "United States"
      : jurisdiction === "INDIA"
        ? "India"
        : "Location not specified";
  const kind =
    category === "FREELANCE"
      ? "Freelance agreement"
      : category === "CONSUMER"
        ? "Consumer agreement"
        : "Other agreement";
  return `${country} · ${kind}`;
}

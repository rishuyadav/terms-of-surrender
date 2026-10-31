import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchAnalysis } from "@/lib/api";
import type { AnalysisView } from "@/lib/types";
import { useAnalysis } from "./use-analysis";

vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  fetchAnalysis: vi.fn(),
}));

const snapshot: AnalysisView = {
  id: "test",
  status: "PROCESSING",
  stage: "Reviewing text group 1 of 2",
  document: {
    title: "Agreement",
    sourceType: "TEXT",
    fingerprint: "test",
    text: "",
    blocks: [],
    pageCount: 1,
    warnings: [],
  },
  context: { jurisdiction: "UNSPECIFIED", category: "CONSUMER", region: "" },
  report: null,
  errorCode: null,
  createdAt: "2026-10-31T00:00:00Z",
  expiresAt: "2026-11-01T00:00:00Z",
  version: 2,
  progress: { completedGroups: 0, totalGroups: 2, findings: [] },
};

let streams: TestStream[] = [];
class TestStream extends EventTarget {
  close = vi.fn();
  onerror: (() => void) | null = null;
  constructor() {
    super();
    streams.push(this);
  }
  emit(view: AnalysisView) {
    this.dispatchEvent(new MessageEvent("snapshot", { data: JSON.stringify(view) }));
  }
}

beforeEach(() => {
  streams = [];
  vi.stubGlobal("EventSource", TestStream);
  vi.mocked(fetchAnalysis).mockResolvedValue(snapshot);
});
afterEach(() => vi.unstubAllGlobals());

describe("useAnalysis live progress", () => {
  it("accepts live progress and ignores delayed older snapshots", async () => {
    const { result } = renderHook(() => useAnalysis(snapshot.id, 0));
    await waitFor(() => expect(streams).toHaveLength(1));
    const newer = {
      ...snapshot,
      version: 3,
      progress: { ...snapshot.progress!, completedGroups: 1 },
    };
    act(() => streams[0].emit(newer));
    expect(result.current.view?.progress?.completedGroups).toBe(1);
    act(() => streams[0].emit(snapshot));
    expect(result.current.view?.version).toBe(3);
    expect(result.current.view?.progress?.completedGroups).toBe(1);
  });

  it("keeps a completed state when an older in-flight snapshot arrives", async () => {
    const { result } = renderHook(() => useAnalysis(snapshot.id, 0));
    await waitFor(() => expect(streams).toHaveLength(1));
    act(() => streams[0].emit({ ...snapshot, version: 4, status: "COMPLETED", progress: null }));
    expect(streams[0].close).toHaveBeenCalledOnce();
    act(() => streams[0].emit(snapshot));
    expect(result.current.view?.status).toBe("COMPLETED");
  });
});

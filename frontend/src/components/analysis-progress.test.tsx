import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AnalysisProgress } from "./analysis-progress";
import type { AnalysisView } from "@/lib/types";

const view: AnalysisView = {
  id: "test",
  status: "PROCESSING",
  stage: "Reviewing text group 3 of 6",
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
  version: 3,
  progress: { completedGroups: 2, totalGroups: 6, findings: [] },
};

describe("AnalysisProgress", () => {
  it("reports actual completed work rather than an estimated countdown", () => {
    render(<AnalysisProgress view={view} onReadSource={vi.fn()} />);
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "2");
    expect(bar).toHaveAttribute("aria-valuemax", "6");
    expect(screen.getByRole("status")).toHaveTextContent("2 of 6 text groups reviewed");
    expect(screen.queryByText(/up to a minute/)).not.toBeInTheDocument();
  });

  it("does not invent a percentage while queued and makes source reading actionable", async () => {
    const onReadSource = vi.fn();
    render(
      <AnalysisProgress
        view={{ ...view, status: "QUEUED", progress: null }}
        onReadSource={onReadSource}
      />,
    );
    expect(screen.getByRole("progressbar")).not.toHaveAttribute("aria-valuenow");
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Read the source while we review" }));
    expect(onReadSource).toHaveBeenCalledOnce();
  });

  it("keeps finalization explicit after all groups are reviewed", () => {
    render(
      <AnalysisProgress
        view={{
          ...view,
          stage: "Preparing your complete report",
          progress: { completedGroups: 6, totalGroups: 6, findings: [] },
        }}
        onReadSource={vi.fn()}
      />,
    );
    expect(screen.getByRole("progressbar")).toHaveAttribute(
      "aria-valuetext",
      expect.stringContaining("preparing the complete report"),
    );
    expect(screen.getByRole("status")).toHaveTextContent("Preparing your complete report");
  });
});

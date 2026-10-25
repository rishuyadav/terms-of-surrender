import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { FindingCard } from "./finding-card";
import type { Finding } from "@/lib/types";

const finding: Finding = {
  id: "renewal",
  category: "Renewal",
  impact: "REVIEW",
  title: "Check how renewal works",
  explanation: "This clause mentions renewal.",
  whyItMatters: "Check the notice requirement.",
  evidence: { blockId: "p1-b1", start: 0, end: 38, quote: "This agreement renews automatically." },
  suggestedQuestion: "When must you receive my notice?",
};

describe("FindingCard", () => {
  it("exposes exact evidence and source navigation", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(<FindingCard finding={finding} index={0} selected={false} onSelect={onSelect} />);
    expect(screen.getByRole("heading", { name: finding.title })).toBeVisible();
    await user.click(screen.getByText("Exact wording"));
    expect(screen.getByText(finding.evidence.quote)).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Show in document" }));
    expect(onSelect).toHaveBeenCalledOnce();
  });

  it("copies the question only after a user action", async () => {
    const user = userEvent.setup();
    const write = vi.spyOn(navigator.clipboard, "writeText");
    render(<FindingCard finding={finding} index={0} selected={true} onSelect={vi.fn()} />);
    await user.click(screen.getByText("A question worth asking"));
    await user.click(screen.getByRole("button", { name: "Copy question" }));
    expect(write).toHaveBeenCalledWith(finding.suggestedQuestion);
    expect(screen.getByRole("button", { name: "Copied" })).toBeVisible();
  });
});

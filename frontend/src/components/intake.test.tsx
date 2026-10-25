import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderToString } from "react-dom/server";
import { Intake } from "./intake";
import { createAnalysis, establishSession } from "@/lib/api";

const push = vi.hoisted(() => vi.fn());
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
vi.mock("@/lib/api", () => ({ establishSession: vi.fn(), createAnalysis: vi.fn() }));

const config = {
  mode: "RULE_BASED_PREVIEW" as const,
  retentionHours: 24,
  legalContextAvailable: false,
  maxCharacters: 80000,
};

beforeEach(() => {
  vi.mocked(establishSession).mockReset().mockResolvedValue(config);
  vi.mocked(createAnalysis).mockReset();
  push.mockReset();
});

describe("contract intake", () => {
  it("renders server controls disabled until client handlers are attached", () => {
    const html = new DOMParser().parseFromString(renderToString(<Intake />), "text/html");
    for (const selector of ["textarea", "select", ".input-tab", ".sample-card"]) {
      const elements = html.querySelectorAll(selector);
      expect(elements.length).toBeGreaterThan(0);
      elements.forEach((element) => expect(element.hasAttribute("disabled")).toBe(true));
    }
    expect(establishSession).not.toHaveBeenCalled();
  });
  it("accepts a DOCX file and submits it with the selected context", async () => {
    vi.mocked(createAnalysis).mockResolvedValueOnce({ id: "docx-report" } as Awaited<
      ReturnType<typeof createAnalysis>
    >);
    const user = userEvent.setup();
    render(<Intake />);
    await user.click(screen.getByRole("button", { name: "Upload PDF / DOCX" }));
    const docx = new File(["fixture body"], "agreement.DOCX", {
      type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    });
    await user.upload(screen.getByLabelText("Upload contract PDF or DOCX"), docx);
    await user.selectOptions(screen.getByLabelText("Your location"), "US");
    await user.selectOptions(screen.getByLabelText("Agreement type"), "FREELANCE");
    await user.click(screen.getByRole("button", { name: "Read the fine print" }));
    expect(createAnalysis).toHaveBeenCalledWith(
      { file: docx },
      { jurisdiction: "US", category: "FREELANCE", region: "" },
    );
    expect(push).toHaveBeenCalledWith("/report/docx-report");
  });

  it("clears a previously valid file when its replacement is invalid", async () => {
    const user = userEvent.setup({ applyAccept: false });
    render(<Intake />);
    await user.click(screen.getByRole("button", { name: "Upload PDF / DOCX" }));
    const input = screen.getByLabelText("Upload contract PDF or DOCX");
    await user.upload(
      input,
      new File(["%PDF-sample"], "old-agreement.pdf", { type: "application/pdf" }),
    );
    expect(screen.getByText("old-agreement.pdf")).toBeVisible();
    await user.upload(input, new File(["not a pdf"], "replacement.txt", { type: "text/plain" }));
    expect(screen.queryByText("old-agreement.pdf")).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Please select a PDF");
    await user.click(screen.getByRole("button", { name: "Read the fine print" }));
    expect(createAnalysis).not.toHaveBeenCalled();
  });

  it("allows retrying a failed session connection", async () => {
    vi.mocked(establishSession).mockRejectedValueOnce(new Error("Service temporarily unavailable"));
    const user = userEvent.setup();
    render(<Intake />);
    await user.click(await screen.findByRole("button", { name: "Retry connection" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Read the fine print" })).toBeEnabled(),
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("preserves user input after a submission failure and can retry", async () => {
    vi.mocked(createAnalysis).mockRejectedValueOnce(new Error("The analysis queue is busy"));
    vi.mocked(createAnalysis).mockResolvedValueOnce({ id: "test-report" } as Awaited<
      ReturnType<typeof createAnalysis>
    >);
    const user = userEvent.setup();
    render(<Intake />);
    await user.click(screen.getByRole("button", { name: /The freelance contract/ }));
    await user.selectOptions(screen.getByLabelText("Your location"), "INDIA");
    await user.click(screen.getByRole("button", { name: "Read the fine print" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("queue is busy");
    expect((screen.getByLabelText("Contract text") as HTMLTextAreaElement).value).toContain(
      "NORTHSTAR",
    );
    await user.click(screen.getByRole("button", { name: "Read the fine print" }));
    expect(push).toHaveBeenCalledWith("/report/test-report");
    expect(createAnalysis).toHaveBeenLastCalledWith(
      expect.objectContaining({ text: expect.any(String) }),
      { jurisdiction: "INDIA", category: "FREELANCE", region: "" },
    );
  });

  it("rejects an oversized drop without keeping another file selected", async () => {
    const user = userEvent.setup();
    render(<Intake />);
    await user.click(screen.getByRole("button", { name: "Upload PDF / DOCX" }));
    const input = screen.getByLabelText("Upload contract PDF or DOCX");
    await user.upload(
      input,
      new File(["%PDF-sample"], "old-agreement.pdf", { type: "application/pdf" }),
    );
    const oversized = new File(["%PDF-sample"], "large.pdf", { type: "application/pdf" });
    Object.defineProperty(oversized, "size", { value: 10 * 1024 * 1024 + 1 });
    fireEvent.drop(input.parentElement!, { dataTransfer: { files: [oversized] } });
    expect(screen.queryByText("old-agreement.pdf")).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("smaller than 10 MB");
  });
});

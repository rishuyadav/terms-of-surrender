import { afterEach, describe, expect, it, vi } from "vitest";
import { createAnalysis, deleteAnalysis, establishSession } from "./api";

afterEach(() => vi.unstubAllGlobals());

describe("API client", () => {
  it("keeps session requests same-origin and non-cacheable", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ mode: "RULE_BASED_PREVIEW" }));
    vi.stubGlobal("fetch", fetch);
    await establishSession();
    expect(fetch).toHaveBeenCalledWith(
      "/api/session",
      expect.objectContaining({ credentials: "same-origin", cache: "no-store" }),
    );
  });

  it("surfaces safe problem details", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(Response.json({ detail: "Your report has expired." }, { status: 404 })),
    );
    await expect(establishSession()).rejects.toThrow("Your report has expired.");
  });

  it("supports no-content deletion and encodes IDs", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch);
    await expect(deleteAnalysis("a/b")).resolves.toBeUndefined();
    expect(fetch).toHaveBeenCalledWith(
      "/api/analyses/a%2Fb",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("sends context and exactly one input", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ id: "id" }));
    vi.stubGlobal("fetch", fetch);
    await createAnalysis(
      { text: "contract" },
      { jurisdiction: "INDIA", category: "FREELANCE", region: "Delhi" },
    );
    const body = fetch.mock.calls[0][1].body as FormData;
    expect(body.get("text")).toBe("contract");
    expect(body.has("file")).toBe(false);
    expect(body.get("jurisdiction")).toBe("INDIA");
    expect(body.get("category")).toBe("FREELANCE");
  });
});

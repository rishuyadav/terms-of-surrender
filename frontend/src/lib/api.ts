import type { AnalysisView, ContractContext, SessionConfig } from "./types";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, { ...init, credentials: "same-origin", cache: "no-store" });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new ApiError(
      "Cannot reach the analysis service. Please check your connection and retry.",
      0,
    );
  }
  if (!response.ok) {
    let message = "The request could not be completed. Please try again.";
    try {
      const problem: unknown = await response.json();
      if (
        typeof problem === "object" &&
        problem !== null &&
        "detail" in problem &&
        typeof problem.detail === "string"
      ) {
        message = problem.detail;
      }
    } catch {
      // A reverse proxy may return a non-JSON error page.
    }
    throw new ApiError(message, response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const establishSession = (signal?: AbortSignal) =>
  request<SessionConfig>("/api/session", { signal });

export function createAnalysis(
  input: { text: string } | { file: File },
  context: ContractContext,
): Promise<AnalysisView> {
  const body = new FormData();
  if ("file" in input) body.append("file", input.file);
  else body.append("text", input.text);
  body.append("jurisdiction", context.jurisdiction);
  body.append("category", context.category);
  body.append("region", context.region);
  return request("/api/analyses", { method: "POST", body });
}

export const fetchAnalysis = (id: string, signal?: AbortSignal) =>
  request<AnalysisView>(`/api/analyses/${encodeURIComponent(id)}`, { signal });

export const deleteAnalysis = (id: string) =>
  request<void>(`/api/analyses/${encodeURIComponent(id)}`, { method: "DELETE" });

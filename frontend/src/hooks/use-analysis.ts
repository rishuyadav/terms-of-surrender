"use client";

import { useEffect, useState } from "react";
import { ApiError, fetchAnalysis } from "@/lib/api";
import type { AnalysisView } from "@/lib/types";

export function useAnalysis(id: string, retry: number) {
  const [view, setView] = useState<AnalysisView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reconnecting, setReconnecting] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let stream: EventSource | null = null;
    let poll: ReturnType<typeof setTimeout> | null = null;
    let consecutiveFailures = 0;

    function done(snapshot: AnalysisView) {
      return snapshot.status === "COMPLETED" || snapshot.status === "FAILED";
    }

    function accept(snapshot: AnalysisView) {
      if (controller.signal.aborted) return;
      setView(snapshot);
      setError(null);
      setReconnecting(false);
      consecutiveFailures = 0;
      if (done(snapshot)) stream?.close();
    }

    async function pollSnapshot() {
      try {
        const snapshot = await fetchAnalysis(id, controller.signal);
        accept(snapshot);
        if (!done(snapshot) && !controller.signal.aborted) poll = setTimeout(pollSnapshot, 3000);
      } catch (cause) {
        if (controller.signal.aborted) return;
        consecutiveFailures++;
        if ((cause instanceof ApiError && cause.status === 404) || consecutiveFailures >= 5) {
          setError(cause instanceof Error ? cause.message : "Report unavailable.");
          setReconnecting(false);
        } else {
          setReconnecting(true);
          poll = setTimeout(pollSnapshot, Math.min(consecutiveFailures * 2000, 10000));
        }
      }
    }

    async function connect() {
      try {
        const snapshot = await fetchAnalysis(id, controller.signal);
        accept(snapshot);
        if (done(snapshot) || controller.signal.aborted) return;
        stream = new EventSource(`/api/analyses/${encodeURIComponent(id)}/events`);
        stream.addEventListener("snapshot", (event: MessageEvent<string>) => {
          try {
            accept(JSON.parse(event.data) as AnalysisView);
          } catch {
            stream?.close();
            if (!controller.signal.aborted) poll = setTimeout(pollSnapshot, 1000);
          }
        });
        stream.addEventListener("unavailable", () => {
          stream?.close();
          if (!controller.signal.aborted) setError("This report was deleted or has expired.");
        });
        stream.onerror = () => {
          stream?.close();
          if (!controller.signal.aborted) {
            setReconnecting(true);
            poll = setTimeout(pollSnapshot, 1500);
          }
        };
      } catch (cause) {
        if (!controller.signal.aborted)
          setError(cause instanceof Error ? cause.message : "Report unavailable.");
      }
    }

    void connect();
    return () => {
      controller.abort();
      stream?.close();
      if (poll) clearTimeout(poll);
    };
  }, [id, retry]);

  return { view, error, reconnecting };
}

"use client";

import { BookOpen, Check, LoaderCircle, ShieldCheck } from "lucide-react";
import type { AnalysisView } from "@/lib/types";

export function AnalysisProgress({
  view,
  onReadSource,
}: {
  view: AnalysisView;
  onReadSource: () => void;
}) {
  const queued = view.status === "QUEUED";
  const progress = view.progress;
  const count = progress?.findings.length ?? 0;
  const completed = progress?.completedGroups ?? 0;
  const total = progress?.totalGroups ?? 0;
  const finalizing = total > 0 && completed === total;

  return (
    <section
      className={`analysis-progress${count > 0 ? " has-findings" : ""}`}
      aria-labelledby="analysis-progress-title"
    >
      <div className="analysis-progress-heading">
        <div className="analysis-progress-emblem" aria-hidden="true">
          <LoaderCircle size={22} className="spin" />
        </div>
        <div>
          <span className="label-caption">A CLOSER LOOK, IN PROGRESS</span>
          <h2 id="analysis-progress-title">
            {queued
              ? "Your agreement is in the queue."
              : count > 0
                ? "Start reading. We’re still reviewing."
                : "Reading the fine print."}
          </h2>
        </div>
        {count > 0 && (
          <span className="analysis-progress-count">
            <ShieldCheck size={14} aria-hidden="true" /> {count}{" "}
            {count === 1 ? "finding" : "findings"} so far
          </span>
        )}
      </div>

      <div className="analysis-progress-detail" role="status" aria-atomic="true">
        <span>{queued ? "Waiting for analysis to start" : view.stage}</span>
        {total > 0 && (
          <span>
            {completed} of {total} text groups reviewed
          </span>
        )}
        {count > 0 && (
          <span className="sr-only">
            {count} findings available so far. Analysis is incomplete.
          </span>
        )}
      </div>
      <div
        className={`analysis-progress-track${total === 0 ? " indeterminate" : ""}`}
        role="progressbar"
        aria-label="Text groups reviewed"
        aria-valuemin={0}
        aria-valuemax={total || undefined}
        aria-valuenow={total ? completed : undefined}
        aria-valuetext={
          queued
            ? "Waiting in queue"
            : total
              ? `${completed} of ${total} text groups reviewed${finalizing ? "; preparing the complete report" : ""}`
              : "Review in progress"
        }
      >
        <span style={total ? { width: `${(completed / total) * 100}%` } : undefined} />
      </div>

      <div className="analysis-progress-footer">
        <p>
          <Check size={14} aria-hidden="true" /> Document received.
          {count > 0
            ? " Source quotes checked; the full review is not finished."
            : " Review continues even if you close this tab."}
        </p>
        <button type="button" className="text-button" onClick={onReadSource}>
          <BookOpen size={14} aria-hidden="true" /> Read the source while we review
        </button>
      </div>
    </section>
  );
}

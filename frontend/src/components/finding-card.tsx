"use client";

import { useState } from "react";
import {
  ArrowUpRight,
  Check,
  ChevronDown,
  Copy,
  Flag,
  Lightbulb,
  MessageCircleQuestion,
} from "lucide-react";
import type { Finding } from "@/lib/types";

export const impactLabels = {
  HIGH: "High impact",
  REVIEW: "Worth reviewing",
  FAVORABLE: "Potential protection",
} as const;

export function FindingCard({
  finding,
  index,
  selected,
  onSelect,
  sourceType = "PDF",
}: {
  finding: Finding;
  index: number;
  selected: boolean;
  onSelect: () => void;
  sourceType?: "TEXT" | "PDF" | "DOCX";
}) {
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);

  async function copyQuestion() {
    try {
      await navigator.clipboard.writeText(finding.suggestedQuestion);
      setCopied(true);
      setCopyError(false);
    } catch {
      setCopyError(true);
    }
  }

  return (
    <article
      id={`finding-${finding.id}`}
      className={`finding-card impact-${finding.impact.toLowerCase()}${selected ? " selected" : ""}`}
      aria-labelledby={`finding-title-${finding.id}`}
    >
      <div className="finding-meta">
        <span className={`impact-pill ${finding.impact.toLowerCase()}`}>
          <Flag size={11} /> {impactLabels[finding.impact]}
        </span>
        <span className="finding-index">{String(index + 1).padStart(2, "0")}</span>
      </div>
      <p className="finding-category">{finding.category}</p>
      <h3 id={`finding-title-${finding.id}`}>{finding.title}</h3>
      <p className="finding-explanation">{finding.explanation}</p>
      <div className="why-matters">
        <Lightbulb size={15} />
        <p>
          <strong>Why look closer</strong>
          {finding.whyItMatters}
        </p>
      </div>
      <details className="evidence-details">
        <summary>
          <span>
            Exact wording{" "}
            <span className="muted">
              {sourceType === "PDF"
                ? `· page ${finding.evidence.blockId.split("-")[0].slice(1)}`
                : "· extracted text"}
            </span>
          </span>
          <ChevronDown size={15} />
        </summary>
        <blockquote>{finding.evidence.quote}</blockquote>
      </details>
      <button type="button" className="source-link" onClick={onSelect}>
        {selected ? <Check size={14} /> : <ArrowUpRight size={14} />}
        {selected ? "Highlighted in source" : "Show in document"}
      </button>
      <details className="question-details">
        <summary>
          <MessageCircleQuestion size={15} /> A question worth asking <ChevronDown size={14} />
        </summary>
        <p>{finding.suggestedQuestion}</p>
        <button type="button" className="text-button" onClick={copyQuestion}>
          {copied ? <Check size={13} /> : <Copy size={13} />}
          {copied ? "Copied" : "Copy question"}
        </button>
        {copyError && (
          <p role="status" className="copy-error">
            Clipboard unavailable. Select the question above to copy it.
          </p>
        )}
      </details>
    </article>
  );
}

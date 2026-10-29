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
  onSelectRelated,
  sourceType = "PDF",
  contextText,
}: {
  finding: Finding;
  index: number;
  selected: boolean;
  onSelect: () => void;
  onSelectRelated?: (blockId: string) => void;
  sourceType?: "TEXT" | "PDF" | "DOCX";
  contextText?: string;
}) {
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);
  const isQuestion = finding.suggestedQuestion.trim().endsWith("?");

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
      <h3 id={`finding-title-${finding.id}`} tabIndex={-1}>
        {finding.title}
      </h3>
      <p className="finding-explanation">{finding.explanation}</p>
      <div className="why-matters">
        <Lightbulb size={15} />
        <p>
          <strong>Why look closer — </strong>
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
                : sourceType === "DOCX"
                  ? "· Word document"
                  : "· pasted text"}
            </span>
          </span>
          <ChevronDown size={15} />
        </summary>
        <blockquote>{finding.evidence.quote}</blockquote>
      </details>
      {contextText && contextText !== finding.evidence.quote && (
        <details className="evidence-details evidence-context">
          <summary>
            Surrounding passage (context) <ChevronDown size={15} />
          </summary>
          <p>
            Read this for conditions and exceptions. Only the exact wording above is highlighted.
          </p>
          <blockquote>{contextText}</blockquote>
        </details>
      )}
      <button type="button" className="source-link" onClick={onSelect}>
        {selected ? <Check size={14} /> : <ArrowUpRight size={14} />}
        {selected ? "Highlighted in source" : "Show in document"}
      </button>
      {finding.relatedBlockIds && finding.relatedBlockIds.length > 0 && onSelectRelated && (
        <div className="related-clauses">
          <span className="related-clauses-label">Also see:</span>
          {finding.relatedBlockIds.map((blockId) => (
            <button
              key={blockId}
              type="button"
              className="related-clause-btn"
              onClick={() => onSelectRelated(blockId)}
            >
              {sourceType === "PDF"
                ? `Page ${blockId.split("-")[0].slice(1)}`
                : `Section ${blockId}`}
            </button>
          ))}
        </div>
      )}
      <details className="question-details">
        <summary>
          <MessageCircleQuestion size={15} />{" "}
          {isQuestion ? "A question worth asking" : "Before you agree"} <ChevronDown size={14} />
        </summary>
        <p>{finding.suggestedQuestion}</p>
        <button type="button" className="text-button" onClick={copyQuestion}>
          {copied ? <Check size={13} /> : <Copy size={13} />}
          {copied ? "Copied" : isQuestion ? "Copy question" : "Copy action"}
        </button>
        {copyError && (
          <p role="status" className="copy-error">
            Clipboard unavailable. Select the {isQuestion ? "question" : "action"} above to copy it.
          </p>
        )}
      </details>
    </article>
  );
}

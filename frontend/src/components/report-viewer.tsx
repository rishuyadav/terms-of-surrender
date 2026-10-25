"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Check,
  CircleAlert,
  Clock3,
  Download,
  FileSearch,
  FileText,
  Info,
  ListFilter,
  LoaderCircle,
  LockKeyhole,
  RotateCcw,
  ShieldCheck,
  Trash2,
} from "lucide-react";
import { Brand } from "./brand";
import { FindingCard } from "./finding-card";
import { useAnalysis } from "@/hooks/use-analysis";
import { deleteAnalysis } from "@/lib/api";
import { contextLabel, sourceSegments } from "@/lib/source";
import type { Finding, Impact } from "@/lib/types";

export function ReportViewer({ id }: { id: string }) {
  const router = useRouter();
  const [retry, setRetry] = useState(0);
  const { view, error, reconnecting } = useAnalysis(id, retry);
  const [selected, setSelected] = useState<string | null>(null);
  const [filter, setFilter] = useState<Impact | "ALL">("ALL");
  const [mobilePane, setMobilePane] = useState<"findings" | "source">("findings");
  const [deleting, setDeleting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const findings = view?.report?.findings ?? [];

  function selectFinding(finding: Finding, fromSource = false) {
    setSelected(finding.id);
    if (fromSource) {
      setFilter("ALL");
      setMobilePane("findings");
      requestAnimationFrame(() =>
        document
          .getElementById(`finding-${finding.id}`)
          ?.scrollIntoView({ behavior: "smooth", block: "nearest" }),
      );
    } else {
      setMobilePane("source");
      requestAnimationFrame(() =>
        document
          .getElementById(`block-${finding.evidence.blockId}`)
          ?.scrollIntoView({ behavior: "smooth", block: "center" }),
      );
    }
  }

  async function removeReport() {
    if (!window.confirm("Delete this report and its extracted source text? This cannot be undone."))
      return;
    setDeleting(true);
    setActionError(null);
    try {
      await deleteAnalysis(id);
      router.replace("/");
    } catch (cause) {
      setActionError(cause instanceof Error ? cause.message : "Deletion failed. Please retry.");
      setDeleting(false);
    }
  }

  function downloadReport() {
    if (!view) return;
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(view, null, 2)], { type: "application/json" }),
    );
    const link = document.createElement("a");
    link.href = url;
    link.download = `terms-of-surrender-${id.slice(0, 8)}.json`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  return (
    <div className="report-shell">
      <header className="site-header report-header">
        <Brand />
        <div className="report-header-actions">
          <span className="private-badge">
            <LockKeyhole size={12} /> PRIVATE REPORT
          </span>
          <Link href="/" className="secondary-button small-button">
            New agreement <ArrowRight size={14} />
          </Link>
        </div>
      </header>
      <main id="main" className="report-main">
        <Link href="/" className="back-link">
          <ArrowLeft size={14} /> Back to start
        </Link>

        {error ? (
          <section className="empty-state" role="alert">
            <CircleAlert size={32} />
            <h1>We couldn’t open this report.</h1>
            <p>{error}</p>
            <p className="muted">
              Reports are private to the browser session that created them and expire automatically.
            </p>
            <button
              className="secondary-button"
              type="button"
              onClick={() => setRetry((value) => value + 1)}
            >
              <RotateCcw size={15} /> Try again
            </button>
          </section>
        ) : !view ? (
          <section className="empty-state" role="status">
            <LoaderCircle className="spin" size={30} />
            <h1>Opening your agreement…</h1>
            <p>Checking your private session.</p>
          </section>
        ) : (
          <>
            <div className="report-title-row">
              <div>
                <p className="eyebrow">THE AGREEMENT, UNPACKED</p>
                <h1>
                  Your fine-print field guide<span>.</span>
                </h1>
                <p className="report-context">
                  {contextLabel(view.context.jurisdiction, view.context.category)}
                  {view.context.region ? ` · ${view.context.region}` : ""}
                </p>
              </div>
              <div className="report-tools">
                <button
                  type="button"
                  className="icon-button"
                  aria-label="Download private report as JSON"
                  title="Download private report as JSON"
                  onClick={downloadReport}
                >
                  <Download size={18} />
                </button>
                <button
                  type="button"
                  className="icon-button danger-button"
                  aria-label="Delete report"
                  title="Delete report"
                  disabled={deleting}
                  onClick={removeReport}
                >
                  {deleting ? <LoaderCircle size={18} className="spin" /> : <Trash2 size={18} />}
                </button>
              </div>
            </div>

            {actionError && (
              <p className="form-error" role="alert">
                {actionError}
              </p>
            )}
            {reconnecting && (
              <p className="connection-notice" role="status">
                Live updates disconnected. Checking the saved report automatically…
              </p>
            )}

            {view.status === "QUEUED" || view.status === "PROCESSING" ? (
              <section className="processing-card" role="status">
                <div className="processing-icon">
                  <FileSearch size={26} />
                </div>
                <h2>
                  {view.status === "QUEUED"
                    ? "Your agreement is in the queue."
                    : "Reading between the lines."}
                </h2>
                <p>{view.stage}. You can refresh this page without losing progress.</p>
                <div className="processing-steps">
                  <span className="complete">
                    <Check size={14} /> Document received
                  </span>
                  <span>
                    <LoaderCircle size={14} className="spin" /> Checking terms
                  </span>
                  <span className="muted">Source-backed report</span>
                </div>
              </section>
            ) : view.status === "FAILED" ? (
              <section className="processing-card failed" role="alert">
                <CircleAlert size={28} />
                <h2>The analysis couldn’t be completed.</h2>
                <p>
                  {view.errorCode === "WORKER_TIMEOUT"
                    ? "The processing window expired. Your source is still available until the retention deadline."
                    : "The analysis service could not produce a complete report. We have not substituted a different analysis mode."}
                </p>
                <Link href="/" className="secondary-button">
                  Start a new analysis <ArrowRight size={15} />
                </Link>
              </section>
            ) : (
              <>
                <div
                  className={`analysis-mode-banner ${view.report?.mode === "RULE_BASED_PREVIEW" ? "preview" : "model"}`}
                >
                  <Info size={17} />
                  <div>
                    <strong>
                      {view.report?.mode === "RULE_BASED_PREVIEW"
                        ? "Keyword preview, not an AI or legal assessment."
                        : "AI-assisted explanation. Verify before relying on it."}
                    </strong>
                    <span>
                      {view.report?.mode === "RULE_BASED_PREVIEW"
                        ? "These are topics worth inspecting, not a verdict on whether the terms are harmful or enforceable."
                        : "Exact quotes are checked against your source. Interpretations can still be wrong."}{" "}
                      No reviewed legal guidance is enabled; jurisdiction context shapes focus, not conclusions.
                    </span>
                    {view.report?.contextLabel && view.report.contextLabel !== "Not specified" && (
                      <span className="context-label-pill">{view.report.contextLabel}</span>
                    )}
                  </div>
                </div>

                <section className="nutrition-label" aria-label="Report summary">
                  <div className="nutrition-intro">
                    <span className="label-caption">THE SHORT VERSION</span>
                    <h2>
                      {findings.length === 0
                        ? "No topics flagged. Keep reading."
                        : `${findings.length} ${view.report?.mode === "RULE_BASED_PREVIEW" ? "topics to look at" : "findings to consider"}.`}
                    </h2>
                    <p>No universal safety score. Just evidence you can inspect.</p>
                  </div>
                  <div className="summary-stat high">
                    <span>{findings.filter((finding) => finding.impact === "HIGH").length}</span>
                    <small>High impact</small>
                  </div>
                  <div className="summary-stat review">
                    <span>{findings.filter((finding) => finding.impact === "REVIEW").length}</span>
                    <small>Worth reviewing</small>
                  </div>
                  <div className="summary-stat favorable">
                    <span>
                      {findings.filter((finding) => finding.impact === "FAVORABLE").length}
                    </span>
                    <small>Potential protections</small>
                  </div>
                </section>
              </>
            )}

            {view.document.warnings.length > 0 && (
              <div className="extraction-warnings" role="status">
                <CircleAlert size={18} />
                <div>
                  <strong>Some content may be missing</strong>
                  {view.document.warnings.map((warning) => (
                    <p key={warning}>{warning}</p>
                  ))}
                </div>
              </div>
            )}

            <div className="mobile-pane-tabs" role="group" aria-label="Report view">
              <button
                type="button"
                aria-pressed={mobilePane === "findings"}
                onClick={() => setMobilePane("findings")}
              >
                <ListFilter size={16} /> Findings ({findings.length})
              </button>
              <button
                type="button"
                aria-pressed={mobilePane === "source"}
                onClick={() => setMobilePane("source")}
              >
                <BookOpen size={16} /> Source document
              </button>
            </div>

            <div className={`report-workspace mobile-${mobilePane}`}>
              <section className="source-pane" aria-labelledby="source-title">
                <div className="pane-header">
                  <div>
                    <FileText size={16} />
                    <h2 id="source-title">The original wording</h2>
                  </div>
                  <span>
                    {view.document.sourceType} ·{" "}
                    {view.document.sourceType === "PDF"
                      ? `${view.document.pageCount} ${view.document.pageCount === 1 ? "PAGE" : "PAGES"}`
                      : "EXTRACTED TEXT"}
                  </span>
                </div>
                <div
                  className="source-document"
                  role="region"
                  aria-label="Extracted contract text"
                  tabIndex={0}
                >
                  <div className="source-document-heading">
                    <span className="label-caption">YOUR SUPPLIED DOCUMENT</span>
                    <p>Extracted text · Formatting may differ from the original</p>
                  </div>
                  {view.document.blocks.map((block) => (
                    <div
                      key={block.id}
                      id={`block-${block.id}`}
                      className={`source-block${findings.some((finding) => finding.id === selected && finding.evidence.blockId === block.id) ? " focused" : ""}`}
                    >
                      <span
                        className="block-page"
                        aria-label={
                          view.document.sourceType === "PDF"
                            ? `Page ${block.page}`
                            : `Text block ${block.id}`
                        }
                      >
                        {view.document.sourceType === "PDF"
                          ? String(block.page).padStart(2, "0")
                          : "§"}
                      </span>
                      <p>
                        {sourceSegments(block, findings).map((segment) =>
                          segment.findingIds.length === 0 ? (
                            <span key={segment.start}>{segment.text}</span>
                          ) : (
                            <button
                              type="button"
                              key={segment.start}
                              className={`source-highlight${selected && segment.findingIds.includes(selected) ? " active" : ""}`}
                              aria-label={`View finding for: ${segment.text}`}
                              onClick={() => {
                                const finding = findings.find(
                                  (candidate) =>
                                    candidate.id ===
                                    (selected && segment.findingIds.includes(selected)
                                      ? selected
                                      : segment.findingIds[0]),
                                );
                                if (finding) selectFinding(finding, true);
                              }}
                            >
                              {segment.text}
                            </button>
                          ),
                        )}
                      </p>
                    </div>
                  ))}
                </div>
                <div className="source-pane-footer">
                  <ShieldCheck size={13} /> Every highlight maps to exact source characters.
                </div>
              </section>

              <section className="findings-pane" aria-labelledby="findings-title">
                <div className="pane-header">
                  <div>
                    <ListFilter size={16} />
                    <h2 id="findings-title">In plain English</h2>
                  </div>
                  <span>{findings.length} FINDINGS</span>
                </div>
                {findings.length > 0 && (
                  <div className="finding-filters" role="group" aria-label="Filter findings">
                    {(["ALL", "HIGH", "REVIEW", "FAVORABLE"] as const).map((value) => (
                      <button
                        type="button"
                        key={value}
                        aria-pressed={filter === value}
                        onClick={() => setFilter(value)}
                      >
                        {value === "ALL"
                          ? "All"
                          : value === "HIGH"
                            ? "High impact"
                            : value === "REVIEW"
                              ? "Review"
                              : "Protections"}
                      </button>
                    ))}
                  </div>
                )}
                <div className="finding-list">
                  {findings.length === 0 ? (
                    <div className="no-findings">
                      <FileSearch size={28} />
                      <h3>
                        {view.status === "COMPLETED"
                          ? "No findings were returned."
                          : "Findings will appear here."}
                      </h3>
                      <p>
                        {view.status === "COMPLETED"
                          ? "This is not a safety endorsement. Review the document and the analysis limitations before deciding."
                          : "You can inspect the extracted source while the analysis runs."}
                      </p>
                    </div>
                  ) : findings.filter((finding) => filter === "ALL" || finding.impact === filter)
                      .length === 0 ? (
                    <p className="no-filter-results">
                      No findings in this category. This does not establish the absence of risk.
                    </p>
                  ) : (
                    findings.map((finding, index) =>
                      filter !== "ALL" && finding.impact !== filter ? null : (
                        <FindingCard
                          key={finding.id}
                          finding={finding}
                          sourceType={view.document.sourceType}
                          index={index}
                          selected={selected === finding.id}
                          onSelect={() => selectFinding(finding)}
                        />
                      ),
                    )
                  )}
                </div>
              </section>
            </div>

            {view.report && (
              <details className="limitations-panel">
                <summary>
                  <Info size={16} /> What this report can — and cannot — tell you
                </summary>
                <ul>
                  {view.report.limitations.map((limitation) => (
                    <li key={limitation}>{limitation}</li>
                  ))}
                </ul>
              </details>
            )}
            <div className="report-retention">
              <Clock3 size={14} />
              <span>
                Automatically expires {new Date(view.expiresAt).toLocaleString()}. Delete sooner
                using the trash icon. Downloads remain on your device.
              </span>
            </div>
          </>
        )}
      </main>
      <footer className="report-footer">
        Clarity, not legal advice. Always verify before acting.
      </footer>
    </div>
  );
}

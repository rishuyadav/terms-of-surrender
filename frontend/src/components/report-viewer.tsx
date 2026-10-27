"use client";

import { useState, useMemo } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  Check,
  CircleAlert,
  Clock3,
  Copy,
  Download,
  FileSearch,
  FileText,
  Info,
  ListFilter,
  LoaderCircle,
  LockKeyhole,
  MessageCircleQuestion,
  RotateCcw,
  Search,
  Send,
  ShieldCheck,
  Trash2,
  X,
} from "lucide-react";
import { Brand } from "./brand";
import { ThemeToggle } from "./theme-toggle";
import { FindingCard } from "./finding-card";
import { useAnalysis } from "@/hooks/use-analysis";
import { deleteAnalysis, askContract } from "@/lib/api";
import type { QaAnswer } from "@/lib/api";
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
  const [allCopied, setAllCopied] = useState(false);
  const [sourceSearch, setSourceSearch] = useState("");
  const [qaQuestion, setQaQuestion] = useState("");
  const [qaAsking, setQaAsking] = useState(false);
  const [qaAnswers, setQaAnswers] = useState<QaAnswer[]>([]);
  const [qaError, setQaError] = useState<string | null>(null);
  const findings = view?.report?.findings ?? [];

  // Blocks that match the source search query (case-insensitive substring).
  const searchQuery = sourceSearch.trim().toLowerCase();
  const matchingBlockIds = useMemo(() => {
    if (!searchQuery || !view) return null;
    return new Set(
      view.document.blocks
        .filter((b) => b.text.toLowerCase().includes(searchQuery))
        .map((b) => b.id),
    );
  }, [searchQuery, view]);

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
      // Clear source search if the target block would be filtered out.
      if (matchingBlockIds && !matchingBlockIds.has(finding.evidence.blockId)) {
        setSourceSearch("");
      }
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

  async function copyAllQuestions() {
    const text = findings
      .map((f, i) => `${i + 1}. ${f.suggestedQuestion}`)
      .join("\n");
    try {
      await navigator.clipboard.writeText(text);
      setAllCopied(true);
      setTimeout(() => setAllCopied(false), 2000);
    } catch { /* ignore */ }
  }

  async function askQuestion(e: React.FormEvent) {
    e.preventDefault();
    if (!qaQuestion.trim() || qaAsking || !view) return;
    setQaAsking(true);
    setQaError(null);
    try {
      const answer = await askContract(view.id, qaQuestion.trim());
      setQaAnswers((prev) => [answer, ...prev]);
      setQaQuestion("");
    } catch (cause) {
      setQaError(cause instanceof Error ? cause.message : "Could not reach the Q&A service.");
    } finally {
      setQaAsking(false);
    }
  }

  function esc(raw: string): string {
    return raw
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function downloadReport() {
    if (!view || !view.report) return;
    const reportFindings = view.report.findings;
    const checklist = view.report.checklist ?? [];
    const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Contract Analysis — ${esc(view.document.title)}</title>
<style>
  body { font-family: system-ui, sans-serif; max-width: 720px; margin: 40px auto; padding: 0 24px; color: #1a1a1a; line-height: 1.6; }
  h1 { font-size: 22px; font-weight: 600; margin-bottom: 4px; }
  h2 { font-size: 15px; margin: 0 0 4px; }
  .meta { font-size: 13px; color: #666; margin-bottom: 32px; }
  .summary { background: #f5f5f0; border-left: 3px solid #888; padding: 14px 16px; margin-bottom: 28px; font-size: 14px; border-radius: 0 4px 4px 0; }
  .checklist { background: #f0f4f0; border-radius: 8px; padding: 16px 20px; margin-bottom: 28px; }
  .checklist h3 { font-size: 11px; font-weight: 700; letter-spacing: 0.08em; color: #666; margin: 0 0 12px; }
  .checklist li { font-size: 13px; margin-bottom: 8px; list-style: none; display: flex; gap: 10px; }
  .checklist .tag { font-size: 9px; font-weight: 700; padding: 2px 6px; border-radius: 4px; white-space: nowrap; align-self: flex-start; margin-top: 2px; }
  .tag-ask { background: #e8f0fe; color: #2952a3; }
  .tag-verify { background: #fff8e1; color: #7d6000; }
  .tag-consider { background: #e8f5e9; color: #2e7d32; }
  .finding { border: 1px solid #e0e0d8; border-radius: 6px; padding: 18px 20px; margin-bottom: 16px; }
  .badge { display: inline-block; font-size: 10px; font-weight: 600; padding: 2px 7px; border-radius: 10px; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 10px; }
  .badge.high { background: #fde8e8; color: #c0392b; }
  .badge.review { background: #fff8e1; color: #7d6000; }
  .badge.favorable { background: #e8f5e9; color: #2e7d32; }
  .explanation { font-size: 14px; margin: 8px 0; }
  blockquote { margin: 12px 0; padding: 10px 14px; background: #f9f9f5; border-left: 3px solid #ccc; font-size: 13px; color: #444; border-radius: 0 4px 4px 0; }
  .question { margin-top: 12px; font-size: 13px; color: #555; }
  .question strong { display: block; color: #333; }
  .limitations { margin-top: 40px; padding-top: 20px; border-top: 1px solid #e0e0d8; font-size: 12px; color: #888; }
  .limitations li { margin-bottom: 6px; }
  footer { margin-top: 32px; font-size: 11px; color: #aaa; text-align: center; }
</style>
</head>
<body>
<h1>${esc(view.document.title)}</h1>
<div class="meta">
  ${view.report.contextLabel && view.report.contextLabel !== "Not specified" ? `${esc(view.report.contextLabel)} · ` : ""}${new Date(view.createdAt).toLocaleDateString()} · ${view.report.mode === "RULE_BASED_PREVIEW" ? "Keyword preview" : "AI-assisted"}
</div>
${view.report.summary ? `<div class="summary">${esc(view.report.summary)}</div>` : ""}
${checklist.length > 0 ? `<div class="checklist"><h3>BEFORE YOU AGREE</h3><ul>${checklist.map((c) => `<li><span class="tag tag-${c.action.toLowerCase()}">${esc(c.action)}</span>${esc(c.text)}</li>`).join("")}</ul></div>` : ""}
${reportFindings.map((f, i) => `
<div class="finding">
  <span class="badge ${f.impact.toLowerCase()}">${f.impact === "HIGH" ? "High impact" : f.impact === "REVIEW" ? "Worth reviewing" : "Potential protection"}</span>
  <h2>${i + 1}. ${esc(f.title)}</h2>
  <p class="explanation">${esc(f.explanation)}</p>
  <blockquote>${esc(f.evidence.quote)}</blockquote>
  <div class="question"><strong>A question to ask:</strong> ${esc(f.suggestedQuestion)}</div>
</div>`).join("")}
<div class="limitations">
  <strong>Limitations</strong>
  <ul>${view.report.limitations.map((l) => `<li>${esc(l)}</li>`).join("")}</ul>
</div>
<footer>Terms of Surrender — Clarity, not legal advice. Always verify before acting.</footer>
</body>
</html>`;
    const url = URL.createObjectURL(new Blob([html], { type: "text/html" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = "contract-analysis.html";
    a.click();
    URL.revokeObjectURL(url);
  }

  function downloadJson() {
    if (!view) return;
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(view, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "contract-analysis.json";
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="report-shell">
      <header className="site-header report-header">
        <Brand />
        <div className="report-header-actions">
          <ThemeToggle />
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
                <h1>{view.document.title}<span>.</span></h1>
                <p className="report-context">
                  {contextLabel(view.context.jurisdiction, view.context.category)}
                  {view.context.region ? ` · ${view.context.region}` : ""}
                  {" · "}
                  {view.document.sourceType === "PDF"
                    ? `PDF · ${view.document.pageCount} ${view.document.pageCount === 1 ? "page" : "pages"}`
                    : view.document.sourceType === "DOCX"
                      ? "Word document"
                      : "Pasted text"}
                </p>
              </div>
              <div className="report-tools">
                <button
                  type="button"
                  className="icon-button"
                  aria-label="Download report as HTML"
                  title="Download report as HTML"
                  onClick={downloadReport}
                >
                  <Download size={18} />
                </button>
                <button
                  type="button"
                  className="text-button json-download-btn"
                  aria-label="Download report as JSON"
                  title="Download report as JSON"
                  onClick={downloadJson}
                >
                  JSON
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
                <p className="processing-stage">
                  {reconnecting ? "Reconnecting…" : view.stage}
                  {!reconnecting && <span className="stage-dots" aria-hidden="true" />}
                </p>
                {view.status === "PROCESSING" && !view.report && (
                  <p className="processing-hint">
                    AI analysis may take up to a minute. You can refresh this page without losing
                    progress.
                  </p>
                )}
                {view.status === "QUEUED" && (
                  <p className="processing-hint">
                    You can refresh this page without losing progress.
                  </p>
                )}
                <div className="processing-steps">
                  <span className="complete">
                    <Check size={14} /> Document received
                  </span>
                  <span>
                    <LoaderCircle size={14} className="spin" />{" "}
                    {view.status === "QUEUED" ? "Waiting in queue" : "Checking terms"}
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

                {view.report?.summary && (
                  <div className="report-summary-card">
                    <span className="label-caption">AT A GLANCE</span>
                    <p>{view.report.summary}</p>
                  </div>
                )}

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

                {/* "Start here" priority findings */}
                {view.report?.priorities && view.report.priorities.length > 0 && (() => {
                  const priorityFindings = view.report.priorities
                    .map((pid) => findings.find((f) => f.id === pid))
                    .filter((f): f is NonNullable<typeof f> => f !== undefined);
                  if (priorityFindings.length === 0) return null;
                  return (
                    <section className="start-here-section" aria-labelledby="start-here-title">
                      <div className="start-here-header">
                        <span className="label-caption" id="start-here-title">START HERE</span>
                        <p>The findings most worth your attention</p>
                      </div>
                      <ol className="start-here-list">
                        {priorityFindings.map((f) => (
                          <li key={f.id} className={`start-here-item impact-${f.impact.toLowerCase()}`}>
                            <button
                              type="button"
                              className="start-here-btn"
                              onClick={() => selectFinding(f)}
                            >
                              <span className={`start-here-pill ${f.impact.toLowerCase()}`}>
                                {f.impact === "HIGH" ? "High impact" : f.impact === "REVIEW" ? "Review" : "Protection"}
                              </span>
                              <span className="start-here-title">{f.title}</span>
                              <ArrowRight size={14} className="start-here-arrow" />
                            </button>
                            <p className="start-here-why">{f.whyItMatters}</p>
                          </li>
                        ))}
                      </ol>
                    </section>
                  );
                })()}

                {/* Action checklist */}
                {view.report?.checklist && view.report.checklist.length > 0 && (
                  <section className="action-checklist" aria-labelledby="checklist-title">
                    <div className="checklist-header">
                      <span className="label-caption" id="checklist-title">BEFORE YOU AGREE</span>
                      <button
                        type="button"
                        className="text-button copy-all-btn"
                        onClick={async () => {
                          const text = (view.report?.checklist ?? [])
                            .map((c, i) => `${i + 1}. [${c.action}] ${c.text}`)
                            .join("\n");
                          try {
                            await navigator.clipboard.writeText(text);
                          } catch { /* ignore */ }
                        }}
                      >
                        <Copy size={12} /> Copy checklist
                      </button>
                    </div>
                    <ul className="checklist-list">
                      {view.report.checklist.map((item, i) => (
                        <li key={i} className="checklist-item">
                          <span className={`checklist-tag action-${item.action.toLowerCase()}`}>
                            {item.action}
                          </span>
                          <span className="checklist-text">{item.text}</span>
                        </li>
                      ))}
                    </ul>
                    <p className="checklist-note">
                      Items derived from findings. Verify facts before acting.
                    </p>
                  </section>
                )}

                {/* Ask the Contract — only when model-assisted and completed */}
                {view.status === "COMPLETED" && view.report?.mode === "MODEL_ASSISTED" && (
                  <section className="qa-section" aria-labelledby="qa-title">
                    <div className="qa-header">
                      <MessageCircleQuestion size={16} />
                      <span className="label-caption" id="qa-title">ASK ABOUT THIS AGREEMENT</span>
                    </div>
                    <p className="qa-intro">
                      Ask a specific question. Answers come only from the contract text above — not general knowledge.
                    </p>
                    <form className="qa-form" onSubmit={askQuestion}>
                      <input
                        type="text"
                        className="qa-input"
                        placeholder="e.g. What happens if I cancel next month?"
                        value={qaQuestion}
                        maxLength={300}
                        onChange={(e) => setQaQuestion(e.target.value)}
                        disabled={qaAsking}
                        aria-label="Ask a question about this contract"
                      />
                      <button
                        type="submit"
                        className="primary-button qa-submit"
                        disabled={!qaQuestion.trim() || qaAsking}
                      >
                        {qaAsking ? <LoaderCircle size={15} className="spin" /> : <Send size={15} />}
                        {qaAsking ? "Asking…" : "Ask"}
                      </button>
                    </form>
                    {qaError && <p className="qa-error" role="alert">{qaError}</p>}
                    {/* Suggested starter questions derived from the report */}
                    {qaAnswers.length === 0 && findings.length > 0 && (
                      <div className="qa-suggestions">
                        <span className="qa-suggestions-label">Try asking:</span>
                        {findings.slice(0, 3).map((f) => (
                          <button
                            key={f.id}
                            type="button"
                            className="qa-suggestion-btn"
                            onClick={() => setQaQuestion(f.suggestedQuestion)}
                          >
                            {f.suggestedQuestion}
                          </button>
                        ))}
                      </div>
                    )}
                    {qaAnswers.length > 0 && (
                      <div className="qa-answers">
                        {qaAnswers.map((qa, i) => (
                          <div key={i} className={`qa-answer ${qa.abstain ? "abstain" : ""}`}>
                            <p className="qa-q">{qa.question}</p>
                            {qa.abstain ? (
                              <p className="qa-abstain-text">
                                <Info size={13} /> {qa.abstainReason || "No relevant text found in the document."}
                              </p>
                            ) : (
                              <>
                                <p className="qa-a">{qa.answer}</p>
                                {qa.quote && (
                                  <blockquote className="qa-citation">
                                    <button
                                      type="button"
                                      className="qa-citation-link"
                                      onClick={() => {
                                        if (qa.blockId) {
                                          if (matchingBlockIds && !matchingBlockIds.has(qa.blockId)) {
                                            setSourceSearch("");
                                          }
                                          setMobilePane("source");
                                          requestAnimationFrame(() =>
                                            document
                                              .getElementById(`block-${qa.blockId}`)
                                              ?.scrollIntoView({ behavior: "smooth", block: "center" }),
                                          );
                                        }
                                      }}
                                    >
                                      <ArrowUpRight size={11} /> Show in document
                                    </button>
                                    &ldquo;{qa.quote}&rdquo;
                                  </blockquote>
                                )}
                              </>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </section>
                )}
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
                {selected && mobilePane === "source" && (
                  <button
                    type="button"
                    className="mobile-back-banner"
                    onClick={() => setMobilePane("findings")}
                  >
                    <ArrowLeft size={14} />
                    Back to findings
                  </button>
                )}
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
                <div className="source-search-row">
                  <Search size={13} className="source-search-icon" />
                  <input
                    type="search"
                    className="source-search-input"
                    placeholder="Search the document…"
                    value={sourceSearch}
                    onChange={(e) => setSourceSearch(e.target.value)}
                    aria-label="Search document text"
                  />
                  {sourceSearch && (
                    <button
                      type="button"
                      className="source-search-clear"
                      aria-label="Clear search"
                      onClick={() => setSourceSearch("")}
                    >
                      <X size={12} />
                    </button>
                  )}
                  {searchQuery && matchingBlockIds !== null && (
                    <span className="source-search-count">
                      {matchingBlockIds.size} {matchingBlockIds.size === 1 ? "block" : "blocks"}
                    </span>
                  )}
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
                  {view.document.blocks
                    .filter((block) => !matchingBlockIds || matchingBlockIds.has(block.id))
                    .map((block) => (
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
                        {sourceSegments(block, findings).map((segment) => {
                          if (segment.findingIds.length === 0) {
                            return <span key={segment.start}>{segment.text}</span>;
                          }
                          const isActive = !!(selected && segment.findingIds.includes(selected));
                          // Resolve impact class using priority: HIGH > REVIEW > FAVORABLE
                          const segmentFindings = findings.filter((f) =>
                            segment.findingIds.includes(f.id),
                          );
                          const impactClass = segmentFindings.some((f) => f.impact === "HIGH")
                            ? "high"
                            : segmentFindings.some((f) => f.impact === "REVIEW")
                              ? "review"
                              : "favorable";
                          return (
                            <button
                              type="button"
                              key={segment.start}
                              className={`source-highlight ${impactClass}${isActive ? " active" : ""}`}
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
                          );
                        })}
                      </p>
                    </div>
                  ))}
                  {matchingBlockIds !== null && matchingBlockIds.size === 0 && (
                    <p className="source-search-empty">
                      No matching text found. Try a shorter phrase.
                    </p>
                  )}
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
                  {findings.length > 0 && (
                    <button
                      type="button"
                      className="text-button copy-all-btn"
                      onClick={copyAllQuestions}
                    >
                      {allCopied ? <Check size={13} /> : <Copy size={13} />}
                      {allCopied ? "Copied" : "Copy all questions"}
                    </button>
                  )}
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
                          ? "No topics flagged."
                          : "Findings will appear here."}
                      </h3>
                      <p>
                        {view.status === "COMPLETED"
                          ? view.report?.mode === "RULE_BASED_PREVIEW"
                            ? "No keyword patterns matched. This is not a safety endorsement — review the full source text and check the limitations below before deciding."
                            : "The AI found no specific concerns to highlight. Review the source text and the limitations below before relying on this result."
                          : "You can read the extracted source while the analysis runs."}
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
                          onSelectRelated={(blockId) => {
                            // Navigate to a related block in the source pane.
                            if (matchingBlockIds && !matchingBlockIds.has(blockId)) {
                              setSourceSearch("");
                            }
                            setSelected(finding.id);
                            setMobilePane("source");
                            requestAnimationFrame(() =>
                              document
                                .getElementById(`block-${blockId}`)
                                ?.scrollIntoView({ behavior: "smooth", block: "center" }),
                            );
                          }}
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

            {view.status === "COMPLETED" && (
              <div className="analyze-another">
                <p>Have another agreement to check?</p>
                <Link href="/" className="secondary-button">
                  Analyze another <ArrowRight size={15} />
                </Link>
              </div>
            )}
          </>
        )}
      </main>
      <footer className="report-footer">
        Clarity, not legal advice. Always verify before acting.
      </footer>
    </div>
  );
}

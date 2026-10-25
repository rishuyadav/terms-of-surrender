"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import {
  ArrowDown,
  ArrowRight,
  Check,
  CheckCheck,
  FileText,
  FileUp,
  Fingerprint,
  LoaderCircle,
  LockKeyhole,
  MoveUpRight,
  ScanText,
  ShieldCheck,
  X,
} from "lucide-react";
import { Brand } from "./brand";
import { createAnalysis, establishSession } from "@/lib/api";
import { samples } from "@/lib/samples";
import type { Category, Jurisdiction, SessionConfig } from "@/lib/types";
import { useHydrated } from "@/hooks/use-hydrated";

export function Intake() {
  const hydrated = useHydrated();
  const router = useRouter();
  const fileInput = useRef<HTMLInputElement>(null);
  const [inputMode, setInputMode] = useState<"text" | "file">("text");
  const [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [jurisdiction, setJurisdiction] = useState<Jurisdiction>("UNSPECIFIED");
  const [category, setCategory] = useState<Category>("CONSUMER");
  const [region, setRegion] = useState("");
  const [selectedSample, setSelectedSample] = useState<string | null>(null);
  const [config, setConfig] = useState<SessionConfig | null>(null);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    establishSession(controller.signal)
      .then(setConfig)
      .catch((cause: Error) => {
        if (cause.name !== "AbortError") setSessionError(cause.message);
      });
    return () => controller.abort();
  }, [retry]);

  function selectFile(candidate: File | undefined) {
    if (!candidate) return;
    setError(null);
    // Rejected replacements must never leave an older agreement ready to submit.
    setFile(null);
    if (fileInput.current) fileInput.current.value = "";
    if (!/\.(pdf|docx)$/i.test(candidate.name)) {
      setError("Please select a PDF or DOCX. Photos and web URLs aren’t supported yet.");
      return;
    }
    if (candidate.size > 10 * 1024 * 1024) {
      setError("Please select a document smaller than 10 MB.");
      return;
    }
    setFile(candidate);
    setSelectedSample(null);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (inputMode === "text" && text.trim().length < 80) {
      setError("Paste at least 80 characters of contract text, or try a sample below.");
      return;
    }
    if (inputMode === "file" && !file) {
      setError("Choose a PDF or DOCX to continue.");
      return;
    }
    setSubmitting(true);
    try {
      const result = await createAnalysis(inputMode === "file" && file ? { file } : { text }, {
        jurisdiction,
        category,
        region,
      });
      router.push(`/report/${result.id}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Analysis could not be started.");
      setSubmitting(false);
    }
  }

  return (
    <div className="home-shell">
      <header className="site-header">
        <Brand />
        <nav aria-label="Main navigation">
          <a href="#how-it-works">How it works</a>
          <a href="#privacy" className="nav-privacy">
            <LockKeyhole size={14} aria-hidden="true" /> Privacy first
          </a>
          <span className="beta-badge">
            <span /> EARLY PREVIEW
          </span>
        </nav>
      </header>

      <main id="main">
        <section className="hero" aria-labelledby="hero-title">
          <div className="eyebrow">
            <span className="tiny-rule" /> LESS LEGALESE. MORE LEVERAGE.
          </div>
          <h1 id="hero-title">
            The fine print.
            <br />
            <span>Finally, in plain English.</span>
          </h1>
          <p className="hero-description">
            You shouldn’t need a law degree to know what you’re agreeing to.
            <br className="desktop-break" /> Find the terms that matter. See the evidence. Know what
            to ask next.
          </p>
          <div className="hero-trust">
            <span>
              <Check size={14} /> No account needed
            </span>
            <span>
              <Check size={14} /> Source-backed findings
            </span>
            <span>
              <Check size={14} /> Your documents stay private
            </span>
          </div>
        </section>

        <section className="intake-card" aria-label="Analyze an agreement">
          <div className="intake-card-header">
            <div className="input-tabs" role="group" aria-label="Input type">
              <button
                type="button"
                className={inputMode === "text" ? "input-tab active" : "input-tab"}
                disabled={!hydrated}
                aria-pressed={inputMode === "text"}
                onClick={() => {
                  setInputMode("text");
                  setError(null);
                }}
              >
                <FileText size={16} /> Paste text
              </button>
              <button
                type="button"
                className={inputMode === "file" ? "input-tab active" : "input-tab"}
                disabled={!hydrated}
                aria-pressed={inputMode === "file"}
                onClick={() => {
                  setInputMode("file");
                  setError(null);
                }}
              >
                <FileUp size={16} /> Upload PDF / DOCX
              </button>
            </div>
            <span className="input-limit">ENGLISH · UP TO 80,000 CHARACTERS</span>
          </div>

          <form onSubmit={submit}>
            {inputMode === "text" ? (
              <div className="text-input-wrap">
                <label htmlFor="contract-text" className="sr-only">
                  Contract text
                </label>
                <textarea
                  id="contract-text"
                  disabled={!hydrated}
                  value={text}
                  maxLength={config?.maxCharacters ?? 80000}
                  onChange={(event) => {
                    setText(event.target.value);
                    setSelectedSample(null);
                  }}
                  placeholder={
                    "Paste the terms you’re about to agree to…\n\nA subscription. A gym membership. That freelance contract sitting in your inbox."
                  }
                  spellCheck={false}
                  aria-describedby="text-help"
                />
                <div id="text-help" className="text-input-footer">
                  <span>
                    {selectedSample
                      ? "Fictional sample loaded — edit or analyze below"
                      : "Or choose a fictional sample below to see how it works"}
                  </span>
                  <span>{text.length.toLocaleString()} / 80,000</span>
                </div>
              </div>
            ) : (
              <div
                className={`upload-zone${dragging ? " dragging" : ""}`}
                onDragOver={(event) => {
                  event.preventDefault();
                  setDragging(true);
                }}
                onDragLeave={() => setDragging(false)}
                onDrop={(event) => {
                  event.preventDefault();
                  setDragging(false);
                  selectFile(event.dataTransfer.files[0]);
                }}
              >
                <input
                  ref={fileInput}
                  id="contract-file"
                  aria-label="Upload contract PDF or DOCX"
                  className="sr-only"
                  type="file"
                  disabled={!hydrated}
                  accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                  onChange={(event) => selectFile(event.target.files?.[0])}
                />
                {file ? (
                  <>
                    <span className="upload-icon">
                      <FileText size={25} />
                    </span>
                    <strong className="file-name">{file.name}</strong>
                    <p>{(file.size / 1024).toFixed(0)} KB · Ready to read</p>
                    <button
                      type="button"
                      className="text-button"
                      onClick={() => {
                        setFile(null);
                        if (fileInput.current) fileInput.current.value = "";
                      }}
                    >
                      <X size={14} /> Remove file
                    </button>
                  </>
                ) : (
                  <>
                    <span className="upload-icon">
                      <FileUp size={25} />
                    </span>
                    <strong>Drop your agreement here</strong>
                    <p>PDF or DOCX · Up to 10 MB · PDFs up to 40 pages</p>
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => fileInput.current?.click()}
                    >
                      Choose a document <ArrowRight size={15} />
                    </button>
                    <span className="upload-note">
                      Scans and photos aren’t supported yet. DOCX reads body text and tables; accept
                      or reject tracked changes first.
                    </span>
                  </>
                )}
              </div>
            )}

            <details className="context-section">
              <summary>
                Add context (optional) — shapes what the analysis focuses on
                {(() => {
                  const parts: string[] = [];
                  if (jurisdiction === "US") parts.push("United States");
                  if (jurisdiction === "INDIA") parts.push("India");
                  if (category === "FREELANCE") parts.push("Freelance / services");
                  if (category === "OTHER") parts.push("Other agreement");
                  return parts.length > 0 ? (
                    <span className="context-summary-pill">{parts.join(" · ")}</span>
                  ) : null;
                })()}
                <span className="context-disclosure-hint">
                  Jurisdiction shapes analysis focus, not legal conclusions
                </span>
              </summary>
              <div className="context-fields">
                <label>
                  Your location
                  <select
                    value={jurisdiction}
                    disabled={!hydrated}
                    onChange={(event) => setJurisdiction(event.target.value as Jurisdiction)}
                  >
                    <option value="UNSPECIFIED">Not specified</option>
                    <option value="US">United States</option>
                    <option value="INDIA">India</option>
                  </select>
                </label>
                <label>
                  Agreement type
                  <select
                    value={category}
                    disabled={!hydrated}
                    onChange={(event) => setCategory(event.target.value as Category)}
                  >
                    <option value="CONSUMER">Consumer / subscription</option>
                    <option value="FREELANCE">Freelance / services</option>
                    <option value="OTHER">Other agreement</option>
                  </select>
                </label>
                <label>
                  State or region <span className="muted">(optional)</span>
                  <input
                    type="text"
                    value={region}
                    disabled={!hydrated}
                    maxLength={80}
                    onChange={(event) => setRegion(event.target.value)}
                    placeholder={jurisdiction === "INDIA" ? "e.g. Maharashtra" : "e.g. California"}
                  />
                </label>
              </div>
            </details>

            {sessionError && (
              <div className="form-error" role="alert">
                {sessionError}{" "}
                <button
                  type="button"
                  className="text-button"
                  onClick={() => {
                    setSessionError(null);
                    setRetry((value) => value + 1);
                  }}
                >
                  Retry connection
                </button>
              </div>
            )}
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}

            {config?.mode === "MODEL_ASSISTED" && (
              <p className="provider-notice">
                <ShieldCheck size={13} /> Extracted text will be sent to the configured AI provider
                for analysis. Do not upload documents containing sensitive personal information.
              </p>
            )}
            <div className="intake-submit-row">
              <div className="mode-caption">
                <span className="status-dot" />
                <span>
                  {config
                    ? config.mode === "RULE_BASED_PREVIEW"
                      ? "Keyword preview · No AI provider required"
                      : "AI-assisted · Verify every interpretation"
                    : "Connecting to analysis service…"}
                </span>
              </div>
              <button
                className="primary-button"
                type="submit"
                disabled={!hydrated || !config || submitting}
              >
                {submitting ? (
                  <>
                    <LoaderCircle className="spin" size={17} /> Starting analysis…
                  </>
                ) : (
                  <>
                    Read the fine print <ArrowRight size={18} />
                  </>
                )}
              </button>
            </div>
          </form>
        </section>

        <section className="sample-section" aria-label="Sample agreements">
          <div className="sample-intro">
            <span className="tiny-rule" /> JUST LOOKING? TRY A SAMPLE <ArrowDown size={13} />
          </div>
          <div className="sample-grid">
            {samples.map((sample) => (
              <button
                type="button"
                key={sample.id}
                className={`sample-card${selectedSample === sample.id ? " selected" : ""}`}
                disabled={!hydrated}
                onClick={() => {
                  setText(sample.text);
                  setFile(null);
                  setInputMode("text");
                  setJurisdiction(sample.context.jurisdiction);
                  setCategory(sample.context.category);
                  setRegion(sample.context.region);
                  setSelectedSample(sample.id);
                  setError(null);
                }}
              >
                <span className="sample-card-icon">
                  {String(samples.indexOf(sample) + 1).padStart(2, "0")}
                </span>
                <span>
                  <strong>{sample.label}</strong>
                  <small>{sample.description}</small>
                </span>
                {selectedSample === sample.id ? <Check size={17} /> : <MoveUpRight size={17} />}
              </button>
            ))}
          </div>
          <p className="sample-note">
            Fictional agreements. Real examples of the questions worth asking.
          </p>
        </section>

        <section id="how-it-works" className="how-section" aria-labelledby="how-title">
          <div className="section-heading">
            <p className="eyebrow">FROM “I AGREE” TO “I UNDERSTAND”</p>
            <h2 id="how-title">A second look, without the second degree.</h2>
          </div>
          <div className="feature-grid">
            <article>
              <span className="feature-icon">
                <ScanText size={22} />
              </span>
              <span className="feature-number">01 / UNDERSTAND</span>
              <h3>Less jargon. More clarity.</h3>
              <p>
                Find consequential topics in subscriptions, memberships, and freelance agreements.
              </p>
            </article>
            <article>
              <span className="feature-icon">
                <Fingerprint size={22} />
              </span>
              <span className="feature-number">02 / VERIFY</span>
              <h3>Don’t just take our word.</h3>
              <p>
                Every finding points to the exact words in your document. The evidence is always one
                click away.
              </p>
            </article>
            <article>
              <span className="feature-icon">
                <CheckCheck size={22} />
              </span>
              <span className="feature-number">03 / ASK</span>
              <h3>Know your next question.</h3>
              <p>
                Turn a confusing clause into a practical question to ask before you sign or renew.
              </p>
            </article>
          </div>
        </section>

        <section id="privacy" className="privacy-strip">
          <ShieldCheck size={30} strokeWidth={1.4} />
          <div>
            <h2>Your agreement. Not public content.</h2>
            <p>
              No account required. No public reports. Delete your report at any time; it expires
              after {config?.retentionHours ?? 24} hours by default.
              {config?.mode === "MODEL_ASSISTED"
                ? " Extracted text is sent to the configured AI provider; automatic anonymization is not provided."
                : " In keyword-preview mode, your text is not sent to an AI provider."}{" "}
              Don’t upload sensitive documents to an unreviewed deployment.
            </p>
          </div>
          <LockKeyhole className="privacy-lock" size={22} />
        </section>
      </main>

      <footer className="site-footer">
        <span>
          terms of surrender<span className="brand-period">.</span>
        </span>
        <p>Clarity, not legal advice. Always verify before acting.</p>
        <span className="footer-note">BUILT FOR THE PERSON SIGNING.</span>
      </footer>
    </div>
  );
}

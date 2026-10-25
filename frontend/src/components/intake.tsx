"use client";

import { useEffect, useRef, useState, useCallback, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import {
  ArrowDown,
  ArrowRight,
  Check,
  CheckCheck,
  FileText,
  FileUp,
  Fingerprint,
  Globe,
  LoaderCircle,
  LockKeyhole,
  MoveUpRight,
  ScanText,
  ShieldCheck,
  X,
} from "lucide-react";
import { Brand } from "./brand";
import { ThemeToggle } from "./theme-toggle";
import { createAnalysis, establishSession, fetchPageText } from "@/lib/api";
import { samples } from "@/lib/samples";
import type { Category, Jurisdiction, SessionConfig } from "@/lib/types";
import { useHydrated } from "@/hooks/use-hydrated";

export function Intake() {
  const hydrated = useHydrated();
  const router = useRouter();
  const fileInput = useRef<HTMLInputElement>(null);
  const [inputMode, setInputMode] = useState<"text" | "file">("text");
  const [text, setText] = useState(() => {
    // Restore draft text from sessionStorage on first render (client only).
    if (typeof window === "undefined") return "";
    try {
      const raw = sessionStorage.getItem("tos-draft");
      if (raw) {
        const d = JSON.parse(raw) as { text?: string };
        return d.text ?? "";
      }
    } catch { /* ignore */ }
    return "";
  });
  const [file, setFile] = useState<File | null>(null);
  const [jurisdiction, setJurisdiction] = useState<Jurisdiction>(() => {
    if (typeof window === "undefined") return "UNSPECIFIED";
    try {
      const raw = sessionStorage.getItem("tos-draft");
      if (raw) {
        const d = JSON.parse(raw) as { jurisdiction?: Jurisdiction };
        return d.jurisdiction ?? "UNSPECIFIED";
      }
    } catch { /* ignore */ }
    return "UNSPECIFIED";
  });
  const [category, setCategory] = useState<Category>(() => {
    if (typeof window === "undefined") return "CONSUMER";
    try {
      const raw = sessionStorage.getItem("tos-draft");
      if (raw) {
        const d = JSON.parse(raw) as { category?: Category };
        return d.category ?? "CONSUMER";
      }
    } catch { /* ignore */ }
    return "CONSUMER";
  });
  const [region, setRegion] = useState(() => {
    if (typeof window === "undefined") return "";
    try {
      const raw = sessionStorage.getItem("tos-draft");
      if (raw) {
        const d = JSON.parse(raw) as { region?: string };
        return typeof d.region === "string" ? d.region : "";
      }
    } catch { /* ignore */ }
    return "";
  });
  const [selectedSample, setSelectedSample] = useState<string | null>(null);
  const [config, setConfig] = useState<SessionConfig | null>(null);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [retry, setRetry] = useState(0);
  const [urlInput, setUrlInput] = useState("");
  const [urlFetching, setUrlFetching] = useState(false);
  const [urlError, setUrlError] = useState<string | null>(null);
  const [fetchedTitle, setFetchedTitle] = useState<string | null>(null);
  const [fetchedPreview, setFetchedPreview] = useState<string | null>(null);
  const [fetchTruncated, setFetchTruncated] = useState(false);
  // Abort controller ref for cancelling in-flight URL fetches when a new one starts.
  const fetchAbortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    establishSession(controller.signal)
      .then(setConfig)
      .catch((cause: Error) => {
        if (cause.name !== "AbortError") setSessionError(cause.message);
      });
    return () => controller.abort();
  }, [retry]);

  // Cancel any in-flight URL fetch when urlInput is cleared.
  useEffect(() => {
    if (!urlInput && fetchAbortRef.current) {
      fetchAbortRef.current.abort();
      fetchAbortRef.current = null;
      setUrlFetching(false);
    }
  }, [urlInput]);

  // Persist draft text and context to sessionStorage whenever they change.
  useEffect(() => {
    if (!hydrated) return;
    try {
      if (text || jurisdiction !== "UNSPECIFIED" || category !== "CONSUMER" || region) {
        sessionStorage.setItem(
          "tos-draft",
          JSON.stringify({ text, jurisdiction, category, region }),
        );
      } else {
        sessionStorage.removeItem("tos-draft");
      }
    } catch {
      // sessionStorage may be unavailable in some contexts — silently skip.
    }
  }, [hydrated, text, jurisdiction, category, region]);

  function selectFile(candidate: File | undefined) {
    if (!candidate) return;
    setError(null);
    // Rejected replacements must never leave an older agreement ready to submit.
    setFile(null);
    if (fileInput.current) fileInput.current.value = "";
    if (!/\.(pdf|docx)$/i.test(candidate.name)) {
      setError("Please select a PDF or DOCX. For web pages, paste the URL in the text tab.");
      return;
    }
    if (candidate.size > 10 * 1024 * 1024) {
      setError("Please select a document smaller than 10 MB.");
      return;
    }
    setFile(candidate);
    setSelectedSample(null);
  }

  function looksLikeUrl(value: string): boolean {
    const trimmed = value.trim();
    // Only trigger on a complete URL paste: must look like a full URL (has a dot in the host
    // part), be the only content in the field (no surrounding text), and not contain newlines.
    // Minimum length: "https://a.bc" = 13 chars.
    if (!trimmed.startsWith("https://") || trimmed.includes("\n") || trimmed.length < 13) {
      return false;
    }
    // The rest after "https://" must contain at least one dot (a real hostname).
    const afterScheme = trimmed.slice("https://".length);
    const hostEnd = afterScheme.search(/[/?#]/);
    const host = hostEnd === -1 ? afterScheme : afterScheme.slice(0, hostEnd);
    return host.includes(".");
  }

  const fetchUrl = useCallback(async () => {
    // Cancel any in-flight fetch before starting a new one.
    if (fetchAbortRef.current) {
      fetchAbortRef.current.abort();
    }
    const controller = new AbortController();
    fetchAbortRef.current = controller;

    setUrlFetching(true);
    setUrlError(null);
    setFetchedTitle(null);
    setFetchedPreview(null);
    setFetchTruncated(false);
    try {
      const result = await fetchPageText(urlInput, controller.signal);
      if (controller.signal.aborted) return;
      setText(result.text);
      setFetchedTitle(result.title);
      setFetchedPreview(result.text.slice(0, 300));
      setFetchTruncated(result.truncated ?? false);
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === "AbortError") return;
      setUrlError(
        cause instanceof Error
          ? cause.message
          : "The page could not be fetched. Paste the text directly.",
      );
    } finally {
      if (!controller.signal.aborted) setUrlFetching(false);
    }
  }, [urlInput]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (inputMode === "text" && urlInput && !text) {
      setError("Fetch the page text first, or paste the text directly.");
      return;
    }
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
      try { sessionStorage.removeItem("tos-draft"); } catch { /* ignore */ }
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
          <ThemeToggle />
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
            {inputMode === "text" && urlInput ? (
              <div className="url-fetch-card">
                <div className="url-fetch-header">
                  <Globe size={16} />
                  <span className="url-fetch-domain">
                    {(() => {
                      try {
                        return new URL(urlInput).hostname;
                      } catch {
                        return urlInput;
                      }
                    })()}
                  </span>
                  <button
                    type="button"
                    className="text-button"
                    onClick={() => {
                      setUrlInput("");
                      setUrlError(null);
                      setFetchedTitle(null);
                      setFetchedPreview(null);
                      // Keep fetched text in the textarea so users can review/edit it.
                      // If nothing was fetched yet, text is already empty.
                    }}
                  >
                    <X size={13} /> {fetchedTitle ? "Edit extracted text" : "Use text instead"}
                  </button>
                </div>
                <p className="url-fetch-address">
                  {urlInput.length > 80 ? urlInput.slice(0, 80) + "…" : urlInput}
                </p>

                {fetchedTitle ? (
                  <div className="url-fetched-result">
                    <p className="url-fetched-title">{fetchedTitle}</p>
                    {fetchedPreview && (
                      <p className="url-fetched-preview">
                        &ldquo;{fetchedPreview}
                        {fetchedPreview.length >= 300 ? "…" : ""}&rdquo;
                      </p>
                    )}
                    {fetchTruncated && (
                      <p className="url-fetch-truncated" role="status">
                        This page is long — only the first 80,000 characters were extracted. The
                        rest of the agreement was not analyzed. If important clauses appear near the
                        end, paste the full text directly.
                      </p>
                    )}
                    <p className="url-fetched-ready">
                      <Check size={13} /> Text extracted — ready to analyze below
                    </p>
                  </div>
                ) : (
                  <div className="url-fetch-actions">
                    <button
                      type="button"
                      className="secondary-button"
                      disabled={urlFetching || !hydrated}
                      onClick={fetchUrl}
                    >
                      {urlFetching ? (
                        <>
                          <LoaderCircle size={15} className="spin" /> Fetching page…
                        </>
                      ) : (
                        <>
                          <Globe size={15} /> Fetch page text
                        </>
                      )}
                    </button>
                    <span className="url-fetch-note">
                      Only public HTTPS pages. Login-protected or JavaScript-only pages may not
                      work.
                    </span>
                  </div>
                )}

                {urlError && (
                  <div className="url-fetch-error">
                    <p role="alert">{urlError}</p>
                    <button
                      type="button"
                      className="text-button"
                      onClick={() => {
                        setUrlInput("");
                        setUrlError(null);
                        setText("");
                      }}
                    >
                      Paste the text instead
                    </button>
                  </div>
                )}
              </div>
            ) : inputMode === "text" ? (
              <div className="text-input-wrap">
                <label htmlFor="contract-text" className="sr-only">
                  Contract text
                </label>
                {!text && !selectedSample && (
                  <div className="url-hint" aria-hidden="true">
                    <Globe size={13} />
                    <span>
                      Have a link? Paste a <strong>https://</strong> URL and we&rsquo;ll fetch the
                      text for you.
                    </span>
                  </div>
                )}
                <textarea
                  id="contract-text"
                  disabled={!hydrated}
                  value={text}
                  maxLength={config?.maxCharacters ?? 80000}
                  onChange={(event) => {
                    const value = event.target.value;
                    setText(value);
                    setSelectedSample(null);
                    if (looksLikeUrl(value)) {
                      setUrlInput(value.trim());
                      setText("");
                      setUrlError(null);
                      setFetchedTitle(null);
                      setFetchedPreview(null);
                    }
                  }}
                  placeholder={
                    "Paste the terms you're about to agree to…\n\nA subscription. A gym membership. That freelance contract sitting in your inbox.\n\nTip: paste a URL (https://…) to fetch text from a public page automatically."
                  }
                  spellCheck={false}
                  aria-describedby="text-help"
                />
                <div id="text-help" className="text-input-footer">
                  <span>
                    {selectedSample
                      ? "Fictional sample loaded — edit or analyze below"
                      : "Paste text or a URL · choose a sample below · or upload a PDF/DOCX"}
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
                  setUrlInput("");
                  setFetchedTitle(null);
                  setFetchedPreview(null);
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

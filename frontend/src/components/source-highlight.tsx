import type { Finding } from "@/lib/types";
import type { SourceSegment } from "@/lib/source";

/** One non-duplicated source segment, with explicit disambiguation for shared evidence. */
export function SourceHighlight({
  segment,
  findings,
  selected,
  choicesOpen,
  onSelect,
  onChoose,
}: {
  segment: SourceSegment;
  findings: Finding[];
  selected: string | null;
  choicesOpen: boolean;
  onSelect: (finding: Finding) => void;
  onChoose: (triggerId: string) => void;
}) {
  const active = findings.find((finding) => finding.id === selected);
  // The chosen finding owns its active color. Highest impact is only the unselected fallback.
  const impact =
    active?.impact ??
    (findings.some((finding) => finding.impact === "HIGH")
      ? "HIGH"
      : findings.some((finding) => finding.impact === "REVIEW")
        ? "REVIEW"
        : "FAVORABLE");
  const overlapping = findings.length > 1;
  const highlightId = `source-passage-${segment.start}`;
  const choicesId = `${highlightId}-choices`;

  return (
    <span className="source-segment">
      <button
        id={highlightId}
        type="button"
        className={`source-highlight ${impact.toLowerCase()}${active ? " active" : ""}`}
        aria-label={
          active
            ? `View selected finding for: ${segment.text}`
            : overlapping
              ? `Choose among ${findings.length} findings for: ${segment.text}`
              : `View finding for: ${segment.text}`
        }
        aria-expanded={overlapping ? choicesOpen : undefined}
        aria-controls={choicesOpen ? "source-overlap-chooser" : undefined}
        onClick={() => {
          if (overlapping && !active) onChoose(highlightId);
          else onSelect(active ?? findings[0]);
        }}
      >
        {segment.text}
      </button>
      {overlapping && (
        <button
          id={choicesId}
          type="button"
          className="source-overlap-trigger"
          aria-label={`${findings.length} findings — choose an overlapping finding`}
          aria-expanded={choicesOpen}
          aria-controls={choicesOpen ? "source-overlap-chooser" : undefined}
          onClick={() => onChoose(choicesId)}
        >
          {findings.length} findings
        </button>
      )}
    </span>
  );
}

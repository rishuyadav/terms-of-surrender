export type Jurisdiction = "US" | "INDIA" | "UNSPECIFIED";
export type Category = "CONSUMER" | "FREELANCE" | "OTHER";
export type Impact = "HIGH" | "REVIEW" | "FAVORABLE";
export type AnalysisMode = "RULE_BASED_PREVIEW" | "MODEL_ASSISTED";

export interface ContractContext {
  jurisdiction: Jurisdiction;
  category: Category;
  region: string;
}

export interface SourceBlock {
  id: string;
  page: number;
  start: number;
  end: number;
  text: string;
}

export interface ContractDocument {
  title: string;
  sourceType: "TEXT" | "PDF" | "DOCX";
  fingerprint: string;
  text: string;
  blocks: SourceBlock[];
  pageCount: number;
  warnings: string[];
}

export interface Finding {
  id: string;
  category: string;
  impact: Impact;
  title: string;
  explanation: string;
  whyItMatters: string;
  evidence: { blockId: string; start: number; end: number; quote: string };
  suggestedQuestion: string;
  relatedBlockIds?: string[];
}

export type ChecklistAction = "ASK" | "VERIFY" | "CONSIDER";

export interface ChecklistItem {
  action: ChecklistAction;
  text: string;
}

export interface Report {
  mode: AnalysisMode;
  engineVersion: string;
  findings: Finding[];
  limitations: string[];
  legalContextAvailable: boolean;
  contextLabel: string;
  summary?: string | null;
  priorities?: string[];
  checklist?: ChecklistItem[];
}

export interface AnalysisView {
  id: string;
  status: "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";
  stage: string;
  document: ContractDocument;
  context: ContractContext;
  report: Report | null;
  errorCode: string | null;
  createdAt: string;
  expiresAt: string;
  version: number;
  progress?: {
    completedGroups: number;
    totalGroups: number;
    findings: Finding[];
  } | null;
}

export interface SessionConfig {
  mode: AnalysisMode;
  retentionHours: number;
  legalContextAvailable: boolean;
  maxCharacters: number;
}

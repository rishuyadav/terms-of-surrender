import documents from "./sample-documents.json";
import type { ContractContext } from "./types";

export interface Sample {
  id: string;
  label: string;
  description: string;
  context: ContractContext;
  text: string;
}

// Original fictional demonstrations, not reusable legal templates or claims about real companies.
// Paragraphs live in a shared fixture so backend tests analyze exactly what the product loads.
export const samples: Sample[] = [
  {
    id: "gym",
    label: "The gym membership",
    description: "US · Consumer agreement",
    context: { jurisdiction: "US", category: "CONSUMER", region: "" },
    text: documents.gym.join("\n\n"),
  },
  {
    id: "app",
    label: "The app subscription",
    description: "India · Consumer agreement",
    context: { jurisdiction: "INDIA", category: "CONSUMER", region: "" },
    text: documents.app.join("\n\n"),
  },
  {
    id: "freelance",
    label: "The freelance contract",
    description: "US · Freelance agreement",
    context: { jurisdiction: "US", category: "FREELANCE", region: "" },
    text: documents.freelance.join("\n\n"),
  },
  {
    id: "india-freelance",
    label: "The India services contract",
    description: "India · Freelance agreement",
    context: { jurisdiction: "INDIA", category: "FREELANCE", region: "" },
    text: documents["india-freelance"].join("\n\n"),
  },
];

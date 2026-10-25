import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ReportViewer } from "@/components/report-viewer";

export const metadata: Metadata = {
  title: "Your private report — Terms of Surrender",
  robots: { index: false, follow: false },
};

export default async function ReportPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) notFound();
  return <ReportViewer id={id} />;
}

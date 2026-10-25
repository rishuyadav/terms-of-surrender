import type { Metadata } from "next";
import "@fontsource-variable/dm-sans";
import "@fontsource/libre-caslon-display/400.css";
import "./globals.css";

export const metadata: Metadata = {
  title: "Terms of Surrender — Know what you’re agreeing to",
  description:
    "An evidence-first contract interpreter. Understand consequential terms, verify the original wording, and explore practical next steps.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" data-scroll-behavior="smooth">
      <body>
        <a className="skip-link" href="#main">
          Skip to content
        </a>
        {children}
      </body>
    </html>
  );
}

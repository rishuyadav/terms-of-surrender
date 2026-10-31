import type { Metadata } from "next";
import { cookies } from "next/headers";
import Script from "next/script";
import "@fontsource-variable/dm-sans";
import "@fontsource/libre-caslon-display/400.css";
import "./globals.css";

export const metadata: Metadata = {
  title: "Terms of Surrender — Know what you're agreeing to",
  description:
    "An evidence-first contract interpreter. Understand consequential terms, verify the original wording, and explore practical next steps.",
};

/**
 * Inline script that runs synchronously before React hydrates.
 *
 * On the very first visit (no cookie yet) the server renders without
 * data-theme, so this script sets it immediately from localStorage / OS
 * preference to avoid a flash of the wrong theme.
 *
 * On subsequent visits the server already renders the correct data-theme
 * from the cookie, so this script is effectively a no-op.
 */
const themeInitScript = `
(function () {
  try {
    var stored = localStorage.getItem('tos-theme');
    if (stored === 'dark' || stored === 'light') {
      document.documentElement.setAttribute('data-theme', stored);
      return;
    }
  } catch (_) {}
  try {
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
      document.documentElement.setAttribute('data-theme', 'dark');
    }
  } catch (_) {}
})();
`.trim();

export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  // Read the theme cookie set by the client on toggle. This makes the server
  // render <html data-theme="..."> with the correct value so the initial HTML
  // and the client's useSyncExternalStore server snapshot both agree —
  // eliminating the hydration mismatch.
  const cookieStore = await cookies();
  const themeCookie = cookieStore.get("tos-theme")?.value;
  const theme = themeCookie === "dark" ? "dark" : themeCookie === "light" ? "light" : undefined;

  return (
    <html lang="en" data-scroll-behavior="smooth" {...(theme ? { "data-theme": theme } : {})}>
      <head>
        <Script
          id="theme-init"
          strategy="beforeInteractive"
          dangerouslySetInnerHTML={{ __html: themeInitScript }}
        />
      </head>
      <body>
        <a className="skip-link" href="#main">
          Skip to content
        </a>
        {children}
      </body>
    </html>
  );
}

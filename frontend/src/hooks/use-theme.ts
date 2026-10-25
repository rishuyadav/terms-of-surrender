"use client";

import { useCallback, useEffect, useSyncExternalStore } from "react";

export type Theme = "light" | "dark";

const STORAGE_KEY = "tos-theme";
const COOKIE_NAME = "tos-theme";

// ─── Helpers ────────────────────────────────────────────────────────────────

function readStoredTheme(): Theme | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "light" || stored === "dark") return stored;
  } catch {
    /* localStorage may be unavailable */
  }
  return null;
}

function getSystemPreference(): Theme {
  if (typeof window.matchMedia !== "function") return "light";
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function resolveClientTheme(): Theme {
  return readStoredTheme() ?? getSystemPreference();
}

function persistTheme(theme: Theme): void {
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    /* ignore */
  }
  // Write a cookie so the Server Component can read it on the next request
  // and render <html data-theme="..."> correctly — eliminating hydration mismatches.
  // SameSite=Lax is safe here; the value is non-sensitive UI preference only.
  try {
    const maxAge = 60 * 60 * 24 * 365; // 1 year
    document.cookie = `${COOKIE_NAME}=${theme}; path=/; max-age=${maxAge}; SameSite=Lax`;
  } catch {
    /* ignore */
  }
}

function applyTheme(theme: Theme): void {
  document.documentElement.setAttribute("data-theme", theme);
}

// ─── External store ─────────────────────────────────────────────────────────
//
// We model theme as an external store so that useSyncExternalStore can give
// React a stable server snapshot ("light") that matches the server-rendered
// HTML, while the client snapshot reads the real persisted preference.
// This eliminates the hydration mismatch on aria-label / aria-pressed.

let currentTheme: Theme = "light";
const listeners = new Set<() => void>();

function getClientSnapshot(): Theme {
  return currentTheme;
}

function getServerSnapshot(): Theme {
  // Always return "light" on the server. layout.tsx reads the cookie and
  // renders <html data-theme="..."> itself, so both server HTML and the
  // initial client snapshot agree on "light" before hydration completes.
  return "light";
}

function subscribeToTheme(callback: () => void): () => void {
  listeners.add(callback);
  return () => listeners.delete(callback);
}

function setStoreTheme(theme: Theme): void {
  currentTheme = theme;
  listeners.forEach((cb) => cb());
}

// ─── Hook ───────────────────────────────────────────────────────────────────

/**
 * Manages the active colour theme, hydration-safe.
 *
 * - Server render: always reports "light" so the initial HTML is stable.
 * - Client (after hydration): reads localStorage / OS preference and applies
 *   the correct theme to <html data-theme="...">.
 * - On toggle: persists to both localStorage and a cookie (so the server can
 *   read the choice on the next request and render the correct data-theme).
 */
export function useTheme(): { theme: Theme; toggleTheme: () => void } {
  const theme = useSyncExternalStore(subscribeToTheme, getClientSnapshot, getServerSnapshot);

  // On first client mount, initialise the store from persisted preference.
  useEffect(() => {
    const resolved = resolveClientTheme();
    setStoreTheme(resolved);
    applyTheme(resolved);
  }, []);

  // Keep the DOM attribute in sync whenever the store value changes.
  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  // Track OS preference changes while no explicit choice is stored.
  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    const handler = (e: MediaQueryListEvent) => {
      if (readStoredTheme() === null) {
        const next: Theme = e.matches ? "dark" : "light";
        setStoreTheme(next);
      }
    };
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);

  const toggleTheme = useCallback(() => {
    const next: Theme = currentTheme === "light" ? "dark" : "light";
    persistTheme(next);
    setStoreTheme(next);
  }, []);

  return { theme, toggleTheme };
}

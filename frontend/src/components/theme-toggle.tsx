"use client";

import { Moon, Sun } from "lucide-react";
import { useTheme } from "@/hooks/use-theme";
import { useHydrated } from "@/hooks/use-hydrated";

/**
 * A small accessible button that toggles between light and dark themes.
 * Rendered as an inert placeholder until the client has hydrated so the
 * icon never flashes the wrong state on first paint.
 */
export function ThemeToggle() {
  const hydrated = useHydrated();
  const { theme, toggleTheme } = useTheme();

  const isDark = theme === "dark";

  return (
    <button
      type="button"
      className="theme-toggle"
      onClick={hydrated ? toggleTheme : undefined}
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      title={isDark ? "Switch to light mode" : "Switch to dark mode"}
      aria-pressed={isDark}
      disabled={!hydrated}
    >
      <span className="theme-toggle-track" aria-hidden="true">
        <span className="theme-toggle-thumb">
          <Sun className="theme-toggle-icon theme-toggle-icon--sun" size={12} strokeWidth={2} />
          <Moon className="theme-toggle-icon theme-toggle-icon--moon" size={12} strokeWidth={2} />
        </span>
      </span>
    </button>
  );
}

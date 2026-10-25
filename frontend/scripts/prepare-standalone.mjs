import { cpSync, existsSync } from "node:fs";

// Next's standalone server omits static assets by default. Bundle them locally
// so development, browser verification, and containers exercise the same output.
cpSync(".next/static", ".next/standalone/.next/static", { recursive: true });
if (existsSync("public")) {
  cpSync("public", ".next/standalone/public", { recursive: true });
}

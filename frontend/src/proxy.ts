import { NextResponse, type NextRequest } from "next/server";
import { attestVisitor } from "./lib/visitor-attestation";

export function proxy(request: NextRequest) {
  const headers = attestVisitor(
    request.headers,
    request.method,
    request.nextUrl.pathname,
    request.cookies.get("tos_session")?.value,
    process.env.AI_DEMO_PROXY_SECRET,
    process.env.VERCEL === "1",
  );
  // Preserve upload and SSE handling through the existing rewrite.
  return NextResponse.next({ request: { headers } });
}

export const config = { matcher: "/api/:path*" };

import { createHash, createHmac, randomUUID } from "node:crypto";
import { isIP } from "node:net";

/** Server-only; never expose attestation headers or secrets. */
export function attestVisitor(
  headers: Headers,
  method: string,
  pathname: string,
  sessionToken: string | undefined,
  secret: string | undefined,
  vercel: boolean,
): Headers {
  const forwarded = new Headers(headers);
  for (const name of [...forwarded.keys()]) {
    if (name.startsWith("x-tos-visitor-")) forwarded.delete(name);
  }
  // Trust only Vercel's ingress-overwritten IP.
  const ip = headers.get("x-vercel-forwarded-for");
  const key = headers.get("idempotency-key");
  if (
    !vercel ||
    !secret ||
    secret.length < 32 ||
    !ip ||
    !isIP(ip) ||
    !sessionToken ||
    !/^[A-Za-z0-9_-]{43}$/.test(sessionToken) ||
    !key ||
    !/^[a-f0-9-]{36}$/.test(key)
  )
    return forwarded;

  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = randomUUID();
  const owner = createHash("sha256").update(sessionToken).digest("hex");
  const payload = ["tos-v1", method, pathname, owner, ip, timestamp, nonce, key].join("\n");
  forwarded.set("x-tos-visitor-ip", ip);
  forwarded.set("x-tos-visitor-time", timestamp);
  forwarded.set("x-tos-visitor-nonce", nonce);
  forwarded.set(
    "x-tos-visitor-signature",
    createHmac("sha256", secret).update(payload).digest("hex"),
  );
  return forwarded;
}

import Link from "next/link";
import { ScanLine } from "lucide-react";

export function Brand() {
  return (
    <Link href="/" className="brand">
      <span className="brand-icon">
        <ScanLine size={23} strokeWidth={1.7} aria-hidden="true" />
      </span>
      <span>
        terms of{" "}
        <span className="brand-second">
          surrender<span className="brand-period">.</span>
        </span>
      </span>
      <span className="sr-only"> home</span>
    </Link>
  );
}

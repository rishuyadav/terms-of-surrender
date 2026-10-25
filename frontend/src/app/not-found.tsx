import Link from "next/link";
import { Brand } from "@/components/brand";

export default function NotFound() {
  return (
    <div className="home-shell">
      <header className="site-header">
        <Brand />
      </header>
      <main id="main" className="empty-state">
        <h1>This page isn’t in the agreement.</h1>
        <p>
          The address may be incorrect. Private reports also require the browser session that
          created them.
        </p>
        <Link className="primary-button" href="/">
          Back to start
        </Link>
      </main>
    </div>
  );
}

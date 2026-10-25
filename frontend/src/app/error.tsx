"use client";

import Link from "next/link";

export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <main id="main" className="empty-state">
      <h1>Something interrupted this page.</h1>
      <p>Your private analysis is saved independently of this page. Try opening it again.</p>
      <button type="button" className="primary-button" onClick={reset}>
        Try again
      </button>
      <Link href="/" className="back-link">
        Back to start
      </Link>
    </main>
  );
}

"use client";

import { useEffect, useState } from "react";

type Health = { status: string; time: string };

export function HealthStatus() {
  const [health, setHealth] = useState<Health | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch("/api/v1/health")
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json() as Promise<Health>;
      })
      .then(setHealth)
      .catch((e: Error) => setError(e.message));
  }, []);

  return (
    <section className="card">
      <strong>API 연결 상태: </strong>
      {health && <span className="ok">{health.status}</span>}
      {error && <span className="err">실패 ({error})</span>}
      {!health && !error && <span className="muted">확인 중…</span>}
    </section>
  );
}

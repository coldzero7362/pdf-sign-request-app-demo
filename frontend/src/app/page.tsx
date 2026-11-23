"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { RequireLogin } from "@/components/require-login";
import { StatusBadge } from "@/components/status-badge";
import { api } from "@/lib/api";
import { fmtDate, type RequestSummary } from "@/lib/types";

export default function Home() {
  return <RequireLogin>{() => <RequestList />}</RequireLogin>;
}

function RequestList() {
  const [items, setItems] = useState<RequestSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<RequestSummary[]>("/api/v1/requests")
      .then(setItems)
      .catch((e: Error) => setError(e.message));
  }, []);

  return (
    <>
      <div className="page-head">
        <h1>내 요청</h1>
        <Link href="/requests/new" className="btn btn-primary">
          + 새 요청
        </Link>
      </div>
      {error && <p className="err">{error}</p>}
      {items === null && !error && <p className="muted">불러오는 중…</p>}
      {items?.length === 0 && (
        <section className="card empty">
          <p>아직 요청이 없습니다.</p>
          <p className="muted small">PDF를 올리고 입력 칸을 배치해 서명을 요청해 보세요.</p>
        </section>
      )}
      {items && items.length > 0 && (
        <section className="card">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>제목</th>
                  <th>상태</th>
                  <th>진행</th>
                  <th>문서</th>
                  <th>만든 날</th>
                </tr>
              </thead>
              <tbody>
                {items.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <Link href={r.status === "DRAFT" ? `/requests/${r.id}/edit` : `/requests/${r.id}`}>{r.title}</Link>
                    </td>
                    <td>
                      <StatusBadge status={r.status} />
                    </td>
                    <td>{r.signerCount > 0 ? `${r.completedCount}/${r.signerCount}` : "-"}</td>
                    <td className="muted">{r.documentName}</td>
                    <td className="muted">{fmtDate(r.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </>
  );
}

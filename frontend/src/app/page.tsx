"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { RequireLogin } from "@/components/require-login";
import { StatusBadge } from "@/components/status-badge";
import { api } from "@/lib/api";
import { fmtDate, type RequestSummary } from "@/lib/types";

export default function Home() {
  const [items, setItems] = useState<RequestSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<RequestSummary[]>("/api/v1/requests")
      .then(setItems)
      .catch((e: Error) => setError(e.message));
  }, []);

  const count = (pred: (r: RequestSummary) => boolean) => (items ? items.filter(pred).length : "-");

  return (
    <RequireLogin
      band={(me) => (
        <>
          <div className="page-head">
            <div>
              <h1>보낸 문서</h1>
              <p className="lead" style={{ margin: 0 }}>
                {me.name}님, 서명 요청은 API로 템플릿 번호와 수신자 정보를 보내면 됩니다.
              </p>
            </div>
            <Link href="/templates" className="btn btn-primary btn-lg">
              템플릿 관리
            </Link>
          </div>
          <div className="stats">
            <Stat label="진행 중" value={count((r) => r.status === "SENT" || r.status === "IN_PROGRESS")} />
            <Stat label="완료" value={count((r) => r.status === "COMPLETED")} />
            <Stat label="작성 중" value={count((r) => r.status === "DRAFT")} />
            <Stat label="종료 (거절·취소·만료)" value={count((r) => ["DECLINED", "CANCELED", "EXPIRED"].includes(r.status))} />
          </div>
        </>
      )}
    >
      {() => (
        <>
          {error && <p className="err">{error}</p>}
          {items === null && !error && <p className="muted">불러오는 중…</p>}
          {items?.length === 0 && (
            <section className="card card-feature empty">
              <h2>아직 보낸 문서가 없습니다</h2>
              <p className="muted">템플릿을 만들고, API 토큰으로 서명을 요청해 보세요.</p>
              <Link href="/templates" className="btn btn-accent">
                템플릿 만들기
              </Link>
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
      )}
    </RequireLogin>
  );
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="stat">
      <div className="stat-num">{value}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}

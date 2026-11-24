"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { RequireLogin } from "@/components/require-login";
import { StatusBadge } from "@/components/status-badge";
import { api } from "@/lib/api";
import { fmtDate, type DocumentSummary } from "@/lib/types";

// 보낸 문서 목록. 검색·필터·기간·숨긴 문서는 6단계에서 확장한다.
export default function Home() {
  const [items, setItems] = useState<DocumentSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<DocumentSummary[]>("/api/v1/documents?size=100")
      .then(setItems)
      .catch((e: Error) => setError(e.message));
  }, []);

  const count = (pred: (r: DocumentSummary) => boolean) => (items ? items.filter(pred).length : "-");

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
            <Stat label="진행 중" value={count((r) => r.status === "IN_PROGRESS")} />
            <Stat label="완료" value={count((r) => r.status === "COMPLETED")} />
            <Stat label="반려·회수" value={count((r) => r.status === "REJECTED" || r.status === "RECALLED")} />
            <Stat label="만료" value={count((r) => r.status === "EXPIRED")} />
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
                      <th>서명</th>
                      <th>현재 차례</th>
                      <th>템플릿</th>
                      <th>발송</th>
                      <th>기한</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((r) => (
                      <tr key={r.id}>
                        <td>
                          <Link href={`/documents/${r.id}`}>{r.title}</Link>
                        </td>
                        <td>
                          <StatusBadge status={r.status} />
                        </td>
                        <td>
                          {r.signedCount}/{r.signerCount}
                        </td>
                        <td>{r.currentSigner ?? <span className="muted">-</span>}</td>
                        <td>
                          <code>{r.templateNo}</code>
                        </td>
                        <td className="muted">{fmtDate(r.sentAt)}</td>
                        <td className="muted">{r.status === "IN_PROGRESS" ? fmtDate(r.expiresAt) : "-"}</td>
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

"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { RequireLogin } from "@/components/require-login";
import { api } from "@/lib/api";
import { fmtDate, SIGNING_MODE_LABEL, type TemplateSummary } from "@/lib/types";

export default function TemplatesPage() {
  const [items, setItems] = useState<TemplateSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState<string | null>(null);

  useEffect(() => {
    api<TemplateSummary[]>("/api/v1/templates")
      .then(setItems)
      .catch((e: Error) => setError(e.message));
  }, []);

  async function copy(no: string) {
    await navigator.clipboard.writeText(no);
    setCopied(no);
    setTimeout(() => setCopied(null), 1500);
  }

  return (
    <RequireLogin
      band={() => (
        <div className="page-head">
          <div>
            <h1>템플릿</h1>
            <p className="lead" style={{ margin: 0 }}>
              서명 요청에 쓸 양식을 만들어 두세요. 요청은 API로 템플릿 번호와 수신자 정보를 보내면 됩니다.
            </p>
          </div>
          <Link href="/templates/new" className="btn btn-primary btn-lg">
            + 새 템플릿
          </Link>
        </div>
      )}
    >
      {() => (
        <>
          {error && <p className="err">{error}</p>}
          {items === null && !error && <p className="muted">불러오는 중…</p>}
          {items?.length === 0 && (
            <section className="card card-feature empty">
              <h2>아직 템플릿이 없습니다</h2>
              <p className="muted">PDF를 올리고 서명자 자리와 입력 칸을 배치해 첫 템플릿을 만들어 보세요.</p>
              <Link href="/templates/new" className="btn btn-accent">
                첫 템플릿 만들기
              </Link>
            </section>
          )}
          {items && items.length > 0 && (
            <section className="card">
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>번호</th>
                      <th>이름</th>
                      <th>순서</th>
                      <th>자리 · 칸</th>
                      <th>상태</th>
                      <th>수정일</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((t) => (
                      <tr key={t.templateNo}>
                        <td>
                          <code>{t.templateNo}</code>{" "}
                          <button type="button" className="btn btn-ghost btn-small" onClick={() => copy(t.templateNo)} aria-label={`${t.templateNo} 복사`}>
                            {copied === t.templateNo ? "복사됨" : "복사"}
                          </button>
                        </td>
                        <td>
                          <Link href={`/templates/${t.templateNo}`}>{t.name}</Link>
                        </td>
                        <td>{SIGNING_MODE_LABEL[t.signingMode]}</td>
                        <td>
                          {t.slotCount} · {t.fieldCount}
                        </td>
                        <td>
                          {t.ready ? <span className="badge badge-completed">준비됨</span> : <span className="badge badge-draft">작성 중</span>}
                        </td>
                        <td className="muted">{fmtDate(t.updatedAt)}</td>
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

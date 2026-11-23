"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { PdfPages } from "@/components/pdf-pages";
import { RequireLogin } from "@/components/require-login";
import { StatusBadge } from "@/components/status-badge";
import { api } from "@/lib/api";
import { fetchPdf, type PDFDocumentProxy } from "@/lib/pdf";
import {
  FIELD_LABEL,
  fmtDate,
  RECIPIENT_COLORS,
  RECIPIENT_STATUS_LABEL,
  type AuditView,
  type RequestDetail,
} from "@/lib/types";

const AUDIT_LABEL: Record<string, string> = {
  CREATED: "요청 생성",
  SENT: "발송",
  CANCELED: "취소",
};

export default function RequestDetailPage() {
  return <RequireLogin>{() => <Detail />}</RequireLogin>;
}

function Detail() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [detail, setDetail] = useState<RequestDetail | null>(null);
  const [audit, setAudit] = useState<AuditView[]>([]);
  const [doc, setDoc] = useState<PDFDocumentProxy | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api<RequestDetail>(`/api/v1/requests/${id}`)
      .then(setDetail)
      .catch((e: Error) => setError(e.message));
    api<AuditView[]>(`/api/v1/requests/${id}/audit`)
      .then(setAudit)
      .catch(() => undefined);
  }, [id]);

  useEffect(load, [load]);

  const documentId = detail?.document.id;
  useEffect(() => {
    if (!documentId) return;
    let alive = true;
    let loaded: PDFDocumentProxy | null = null;
    fetchPdf(documentId)
      .then((d) => {
        loaded = d;
        if (alive) setDoc(d);
        else void d.loadingTask.destroy();
      })
      .catch(() => undefined);
    return () => {
      alive = false;
      void loaded?.loadingTask.destroy();
    };
  }, [documentId]);

  async function cancel() {
    if (!confirm("요청을 취소할까요? 수신자는 더 이상 작성할 수 없습니다.")) return;
    try {
      await api(`/api/v1/requests/${id}/cancel`, { method: "POST" });
      load();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function removeDraft() {
    if (!confirm("작성 중인 요청을 삭제할까요?")) return;
    try {
      await api(`/api/v1/requests/${id}`, { method: "DELETE" });
      router.push("/");
    } catch (e) {
      setError((e as Error).message);
    }
  }

  if (error) return <p className="err">{error}</p>;
  if (!detail) return <p className="muted">불러오는 중…</p>;

  const colorOf = (recipientId: string) =>
    RECIPIENT_COLORS[Math.max(0, detail.recipients.findIndex((r) => r.id === recipientId)) % RECIPIENT_COLORS.length];
  const open = detail.status === "SENT" || detail.status === "IN_PROGRESS";

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{detail.title}</h1>
          <p className="muted small" style={{ margin: 0 }}>
            <StatusBadge status={detail.status} /> · {detail.document.fileName} · {detail.document.pageCount}쪽
          </p>
        </div>
        <div className="actions">
          {detail.status === "DRAFT" && (
            <>
              <Link href={`/requests/${id}/edit`} className="btn btn-primary">
                편집
              </Link>
              <button type="button" className="btn btn-danger" onClick={removeDraft}>
                삭제
              </button>
            </>
          )}
          {open && (
            <button type="button" className="btn btn-danger" onClick={cancel}>
              요청 취소
            </button>
          )}
        </div>
      </div>

      {detail.message && <section className="card">{detail.message}</section>}

      <section className="card">
        <h2>수신자</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>이름</th>
                <th>이메일</th>
                <th>역할</th>
                <th>상태</th>
                <th>칸</th>
              </tr>
            </thead>
            <tbody>
              {detail.recipients.map((r) => (
                <tr key={r.id}>
                  <td>
                    <span className="dot" style={{ background: colorOf(r.id) }} />
                    {r.name}
                  </td>
                  <td>{r.email}</td>
                  <td>{r.role === "SIGNER" ? "서명자" : "열람자"}</td>
                  <td>{RECIPIENT_STATUS_LABEL[r.status]}</td>
                  <td>{detail.fields.filter((f) => f.recipientId === r.id).length}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="muted small">
          만든 날 {fmtDate(detail.createdAt)} · 발송 {fmtDate(detail.sentAt)} · 만료 {fmtDate(detail.expiresAt)}
        </p>
        {open && <p className="muted small">수신자 메일 발송과 작성 화면은 다음 단계에서 연결됩니다.</p>}
      </section>

      <section className="card">
        <h2>감사 기록</h2>
        <ul className="audit">
          {audit.map((a, i) => (
            <li key={i}>
              <span className="muted">{fmtDate(a.at)}</span> <strong>{AUDIT_LABEL[a.type] ?? a.type}</strong> · {a.actor}
              {a.detail ? ` · ${a.detail}` : ""}
            </li>
          ))}
        </ul>
      </section>

      <section className="card">
        <h2>문서 미리보기</h2>
        {!doc && <p className="muted">PDF를 불러오는 중…</p>}
        {doc && (
          <PdfPages
            doc={doc}
            pages={detail.document.pages}
            overlay={(page, scale) =>
              detail.fields
                .filter((f) => f.page === page.pageNo)
                .map((f) => {
                  const color = colorOf(f.recipientId);
                  return (
                    <div
                      key={f.id}
                      className="field-box readonly"
                      style={{
                        left: f.x * scale,
                        top: f.y * scale,
                        width: f.width * scale,
                        height: f.height * scale,
                        borderColor: color,
                        background: `${color}22`,
                      }}
                    >
                      <span className="field-box-label" style={{ color }}>
                        {f.type === "CHECKBOX" ? "✓" : f.label || FIELD_LABEL[f.type]}
                      </span>
                    </div>
                  );
                })
            }
          />
        )}
      </section>
    </>
  );
}

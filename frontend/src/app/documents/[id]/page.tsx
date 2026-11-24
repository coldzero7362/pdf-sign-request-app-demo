"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { PdfPages } from "@/components/pdf-pages";
import { RequireLogin } from "@/components/require-login";
import { StatusBadge } from "@/components/status-badge";
import { api } from "@/lib/api";
import { fetchPdfFrom, type PDFDocumentProxy } from "@/lib/pdf";
import {
  AUDIT_LABEL,
  FIELD_LABEL,
  FIXED_FIELD_COLOR,
  fmtDate,
  RECIPIENT_COLORS,
  RECIPIENT_STATUS_LABEL,
  SIGNING_MODE_LABEL,
  STATUS_LABEL,
  type DocumentDetail,
} from "@/lib/types";

export default function DocumentPage() {
  return <RequireLogin>{() => <Detail />}</RequireLogin>;
}

function Detail() {
  const { id } = useParams<{ id: string }>();
  const [d, setD] = useState<DocumentDetail | null>(null);
  const [doc, setDoc] = useState<PDFDocumentProxy | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<Record<string, { ok: boolean; text: string }>>({});
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api<DocumentDetail>(`/api/v1/documents/${id}`)
      .then(setD)
      .catch((e: Error) => setError(e.message));
  }, [id]);
  useEffect(load, [load]);

  useEffect(() => {
    let alive = true;
    let loaded: PDFDocumentProxy | null = null;
    fetchPdfFrom(`/api/v1/documents/${id}/pdf`)
      .then((p) => {
        loaded = p;
        if (alive) setDoc(p);
        else void p.loadingTask.destroy();
      })
      .catch(() => undefined);
    return () => {
      alive = false;
      void loaded?.loadingTask.destroy();
    };
  }, [id]);

  async function recall() {
    if (!confirm("문서를 회수할까요? 서명이 종료되고, 이미 요청을 받은 서명자에게 알림이 갑니다.")) return;
    setBusy(true);
    try {
      setD(await api<DocumentDetail>(`/api/v1/documents/${id}/recall`, { method: "POST" }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function remind(rid: string, email: string) {
    if (!confirm(`${email}(으)로 요청 메일을 다시 보낼까요? 새 링크가 발급되고 이전 링크는 쓸 수 없게 됩니다.`)) return;
    try {
      setD(await api<DocumentDetail>(`/api/v1/documents/${id}/recipients/${rid}/remind`, { method: "POST" }));
      setMsg((m) => ({ ...m, [rid]: { ok: true, text: "보냈습니다" } }));
    } catch (e) {
      setMsg((m) => ({ ...m, [rid]: { ok: false, text: (e as Error).message } }));
    }
  }

  if (error) return <p className="err">{error}</p>;
  if (!d) return <p className="muted">불러오는 중…</p>;

  const colorOf = (slot: string | null) => {
    if (!slot) return FIXED_FIELD_COLOR;
    const i = d.signers.findIndex((s) => s.slot === slot);
    return RECIPIENT_COLORS[Math.max(0, i) % RECIPIENT_COLORS.length];
  };
  const endedAt = d.completedAt ?? d.rejectedAt ?? d.recalledAt ?? d.expiredAt;

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{d.title}</h1>
          <p className="muted small" style={{ margin: 0 }}>
            <StatusBadge status={d.status} /> · 템플릿 <code>{d.templateNo}</code> ({d.templateRevision}판) ·{" "}
            {SIGNING_MODE_LABEL[d.signingMode]}
          </p>
        </div>
        <div className="actions">
          {d.status === "COMPLETED" && (
            <a className="btn btn-accent" href={`/api/v1/documents/${id}/result`}>
              완료본 내려받기
            </a>
          )}
          {d.canRecall && (
            <button type="button" className="btn btn-danger" onClick={recall} disabled={busy}>
              회수
            </button>
          )}
        </div>
      </div>

      <dl className="facts">
        <div>
          <dt>발송</dt>
          <dd>{fmtDate(d.sentAt)}</dd>
        </div>
        <div>
          <dt>서명 기한 ({d.periodDays}일)</dt>
          <dd>{fmtDate(d.expiresAt)}</dd>
        </div>
        <div>
          <dt>{d.status === "IN_PROGRESS" ? "진행" : STATUS_LABEL[d.status]}</dt>
          <dd>{endedAt ? fmtDate(endedAt) : `${d.signers.filter((s) => s.status === "SIGNED").length}/${d.signers.length} 서명`}</dd>
        </div>
        <div>
          <dt>원본</dt>
          <dd>
            {d.pdfFileName} · {d.pageCount}쪽
          </dd>
        </div>
      </dl>

      {d.message && <section className="card">{d.message}</section>}

      <section className="card">
        <h2>서명자</h2>
        <ol className="steps-list">
          {d.signers.map((s) => {
            const canRemind = d.status === "IN_PROGRESS" && (s.status === "REQUESTED" || s.status === "VIEWED");
            return (
              <li key={s.id}>
                <span className="step-order" style={{ background: colorOf(s.slot) }}>
                  {d.signingMode === "SEQUENTIAL" ? s.order : "·"}
                </span>
                <span className="step-who">
                  <strong>{s.name}</strong> <span className="muted">· {s.slotLabel}</span>
                  <span className="small muted">{s.email}</span>
                </span>
                <span className="small">
                  {RECIPIENT_STATUS_LABEL[s.status]}
                  {s.signedAt && ` · ${fmtDate(s.signedAt)}`}
                  {s.rejectedAt && ` · ${fmtDate(s.rejectedAt)}${s.rejectReason ? ` · "${s.rejectReason}"` : ""}`}
                </span>
                {canRemind && (
                  <span>
                    <button type="button" className="btn btn-small" onClick={() => remind(s.id, s.email)}>
                      요청 메일 다시 보내기
                    </button>
                    {msg[s.id] && <span className={`small ${msg[s.id].ok ? "ok" : "err"}`}> {msg[s.id].text}</span>}
                  </span>
                )}
              </li>
            );
          })}
        </ol>
        {d.viewers.length > 0 && (
          <p className="small muted" style={{ marginTop: 12 }}>
            참조자: {d.viewers.map((v) => `${v.name} <${v.email}>`).join(", ")}
          </p>
        )}
        {d.resultSha256 && (
          <p className="muted small" style={{ wordBreak: "break-all" }}>
            완료본 SHA-256 <code>{d.resultSha256}</code>
          </p>
        )}
      </section>

      {d.linked.length > 0 && (
        <section className="card">
          <h2>이어진 문서</h2>
          <ul className="audit">
            {d.linked.map((l) => (
              <li key={l.id}>
                {l.relation === "PREVIOUS" ? "이 문서의 원래 문서" : "이 문서를 고쳐 다시 보낸 문서"}:{" "}
                <Link href={`/documents/${l.id}`}>{l.title}</Link> <StatusBadge status={l.status} />
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="card">
        <h2>감사 추적</h2>
        <ul className="audit">
          {d.audit.map((a, i) => (
            <li key={i}>
              <span className="muted">{fmtDate(a.at)}</span> <strong>{AUDIT_LABEL[a.type] ?? a.type}</strong> · {a.actor}
              {a.detail ? ` · ${a.detail}` : ""}
              {a.ip ? <span className="muted"> · {a.ip}</span> : null}
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
            pages={d.pages}
            overlay={(page, scale) =>
              d.fields
                .filter((f) => f.page === page.pageNo)
                .map((f) => {
                  const color = colorOf(f.slot);
                  return (
                    <div
                      key={f.id}
                      className={`field-box readonly ${f.type === "TEXT_FIXED" ? "fixed" : ""}`}
                      style={{ left: f.x * scale, top: f.y * scale, width: f.width * scale, height: f.height * scale, borderColor: color, background: `${color}22` }}
                    >
                      <span className="field-box-label">
                        {f.type === "TEXT_FIXED" ? f.fixedValue : f.value ?? (f.hasFile ? "서명됨" : f.label || FIELD_LABEL[f.type])}
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

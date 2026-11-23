"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { PdfPages } from "@/components/pdf-pages";
import { api, ApiError } from "@/lib/api";
import { fetchPdf, type PDFDocumentProxy } from "@/lib/pdf";
import {
  FIELD_DEFAULT_SIZE,
  FIELD_LABEL,
  MIN_FIELD_SIZE,
  RECIPIENT_COLORS,
  type FieldType,
  type PageView,
  type RecipientRole,
  type RequestDetail,
} from "@/lib/types";

// ---------- 편집 상태 ----------
// key는 화면에서만 쓰는 식별자. 서버는 저장할 때마다 새 ID를 주므로 화면 상태를 기준으로 삼는다.

type EditRecipient = { key: string; email: string; name: string; role: RecipientRole };
type EditField = {
  key: string;
  recipientKey: string;
  type: FieldType;
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
  required: boolean;
  label: string;
};

type SaveState = "saved" | "dirty" | "saving" | "invalid" | "error";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MAX_RECIPIENTS = 10;
const MAX_FIELDS = 200;
const FIELD_TYPES: FieldType[] = ["SIGNATURE", "TEXT", "DATE", "CHECKBOX"];

let seq = 0;
const newKey = (p: string) => `${p}-${Date.now().toString(36)}-${(seq++).toString(36)}`;

const clamp = (v: number, min: number, max: number) => Math.min(Math.max(v, min), max);
const round = (v: number) => Math.round(v * 100) / 100;

function fromDetail(d: RequestDetail) {
  const recipients: EditRecipient[] = d.recipients.map((r) => ({ key: r.id, email: r.email, name: r.name, role: r.role }));
  const fields: EditField[] = d.fields.map((f) => ({
    key: f.id,
    recipientKey: f.recipientId,
    type: f.type,
    page: f.page,
    x: f.x,
    y: f.y,
    width: f.width,
    height: f.height,
    required: f.required,
    label: f.label ?? "",
  }));
  return { recipients, fields };
}

/** 저장 전에 화면에서 먼저 걸러낸다. 문제가 있으면 메시지, 없으면 null. */
function localProblem(recipients: EditRecipient[]): string | null {
  for (const r of recipients) {
    if (!r.name.trim()) return "수신자 이름을 입력해 주세요.";
    if (!EMAIL_RE.test(r.email.trim())) return `수신자 이메일 형식을 확인해 주세요: ${r.email || "(비어 있음)"}`;
  }
  const emails = recipients.map((r) => r.email.trim().toLowerCase());
  if (new Set(emails).size !== emails.length) return "같은 이메일의 수신자가 있습니다.";
  return null;
}

export function FieldEditor({ initial }: { initial: RequestDetail }) {
  const router = useRouter();
  const requestId = initial.id;
  const pages = initial.document.pages;

  const [doc, setDoc] = useState<PDFDocumentProxy | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [title, setTitle] = useState(initial.title);
  const [message, setMessage] = useState(initial.message ?? "");
  const [expiresOn, setExpiresOn] = useState(initial.expiresAt ? initial.expiresAt.slice(0, 10) : "");

  const init = useMemo(() => fromDetail(initial), [initial]);
  const [recipients, setRecipients] = useState<EditRecipient[]>(init.recipients);
  const [fields, setFields] = useState<EditField[]>(init.fields);
  const [activeRecipient, setActiveRecipient] = useState<string | null>(
    init.recipients.find((r) => r.role === "SIGNER")?.key ?? null,
  );
  const [placing, setPlacing] = useState<FieldType | null>(null);
  const [selected, setSelected] = useState<string | null>(null);

  const [saveState, setSaveState] = useState<SaveState>("saved");
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);

  // ---------- PDF ----------
  useEffect(() => {
    let alive = true;
    let loaded: PDFDocumentProxy | null = null;
    fetchPdf(initial.document.id)
      .then((d) => {
        loaded = d;
        if (alive) setDoc(d);
        else void d.loadingTask.destroy();
      })
      .catch((e: Error) => alive && setLoadError(e.message));
    return () => {
      alive = false;
      void loaded?.loadingTask.destroy();
    };
  }, [initial.document.id]);

  // ---------- 자동 저장 ----------
  const latest = useRef({ recipients, fields });
  const savingRef = useRef<Promise<boolean> | null>(null);
  const pendingRef = useRef(false);
  const firstRender = useRef(true);

  useEffect(() => {
    latest.current = { recipients, fields };
  }, [recipients, fields]);

  /** 지금 화면 상태를 한 번 저장한다. */
  const saveOnce = useCallback(async (): Promise<boolean> => {
    const { recipients: rs, fields: fs } = latest.current;
    const problem = localProblem(rs);
    if (problem) {
      setSaveState("invalid");
      setSaveMessage(problem);
      return false;
    }
    setSaveState("saving");
    try {
      await api<RequestDetail>(`/api/v1/requests/${requestId}/layout`, {
        method: "PUT",
        json: {
          recipients: rs.map((r) => ({ key: r.key, email: r.email.trim(), name: r.name.trim(), role: r.role })),
          fields: fs.map((f) => ({
            recipientKey: f.recipientKey,
            type: f.type,
            page: f.page,
            x: round(f.x),
            y: round(f.y),
            width: round(f.width),
            height: round(f.height),
            required: f.required,
            label: f.label.trim() || null,
          })),
        },
      });
      setSaveState("saved");
      setSaveMessage(null);
      return true;
    } catch (e) {
      setSaveState("error");
      setSaveMessage((e as Error).message);
      return false;
    }
  }, [requestId]);

  /** 저장이 겹치지 않게 한 번에 하나씩, 저장 중에 바뀐 내용이 있으면 이어서 한 번 더 저장한다. */
  const saveLayout = useCallback((): Promise<boolean> => {
    if (savingRef.current) {
      pendingRef.current = true;
      return savingRef.current;
    }
    const run = (async () => {
      let ok = false;
      do {
        pendingRef.current = false;
        ok = await saveOnce();
      } while (pendingRef.current);
      return ok;
    })().finally(() => {
      savingRef.current = null;
    });
    savingRef.current = run;
    return run;
  }, [saveOnce]);

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    setSaveState("dirty");
    setSendError(null); // 내용이 바뀌면 이전 발송 오류는 더 이상 맞지 않는다
    const t = setTimeout(() => void saveLayout(), 800);
    return () => clearTimeout(t);
  }, [recipients, fields, saveLayout]);

  // 저장 안 된 변경이 있으면 페이지를 떠날 때 경고
  useEffect(() => {
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (saveState !== "saved") e.preventDefault();
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [saveState]);

  async function saveInfo() {
    try {
      await api(`/api/v1/requests/${requestId}`, {
        method: "PUT",
        json: {
          title: title.trim() || initial.title,
          message: message.trim() || null,
          // 선택한 날짜의 끝(현지 시각 23:59:59)까지 유효
          expiresAt: expiresOn ? new Date(`${expiresOn}T23:59:59`).toISOString() : null,
        },
      });
      setSaveMessage(null);
    } catch (e) {
      setSaveState("error");
      setSaveMessage((e as Error).message);
    }
  }

  // ---------- 수신자 ----------
  const colorOf = useCallback(
    (key: string) => RECIPIENT_COLORS[Math.max(0, recipients.findIndex((r) => r.key === key)) % RECIPIENT_COLORS.length],
    [recipients],
  );
  const signers = recipients.filter((r) => r.role === "SIGNER");

  function addRecipient() {
    const key = newKey("r");
    setRecipients((rs) => [...rs, { key, email: "", name: "", role: "SIGNER" }]);
    setActiveRecipient(key);
  }

  function updateRecipient(key: string, patch: Partial<EditRecipient>) {
    if (patch.role === "VIEWER" && fields.some((f) => f.recipientKey === key)) {
      if (!confirm("열람자에게는 입력 칸을 줄 수 없습니다. 이 수신자의 칸을 모두 지울까요?")) return;
      setFields((fs) => fs.filter((f) => f.recipientKey !== key));
    }
    setRecipients((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));
    if (patch.role === "VIEWER" && activeRecipient === key) {
      setActiveRecipient(recipients.find((r) => r.key !== key && r.role === "SIGNER")?.key ?? null);
    }
  }

  function removeRecipient(key: string) {
    const count = fields.filter((f) => f.recipientKey === key).length;
    if (count > 0 && !confirm(`이 수신자의 칸 ${count}개도 함께 지웁니다. 계속할까요?`)) return;
    setFields((fs) => fs.filter((f) => f.recipientKey !== key));
    setRecipients((rs) => rs.filter((r) => r.key !== key));
    if (activeRecipient === key) setActiveRecipient(signers.find((r) => r.key !== key)?.key ?? null);
  }

  // ---------- 칸 ----------
  const pageOf = useCallback((no: number) => pages.find((p) => p.pageNo === no)!, [pages]);

  function startPlacing(type: FieldType) {
    if (!activeRecipient) {
      alert("먼저 서명자를 추가하고 선택해 주세요.");
      return;
    }
    setPlacing((cur) => (cur === type ? null : type));
    setSelected(null);
  }

  function placeAt(page: PageView, x: number, y: number) {
    if (!placing || !activeRecipient) {
      setSelected(null);
      return;
    }
    if (fields.length >= MAX_FIELDS) {
      alert(`칸은 최대 ${MAX_FIELDS}개까지 둘 수 있습니다.`);
      return;
    }
    const size = FIELD_DEFAULT_SIZE[placing];
    const width = Math.min(size.width, page.width);
    const height = Math.min(size.height, page.height);
    const f: EditField = {
      key: newKey("f"),
      recipientKey: activeRecipient,
      type: placing,
      page: page.pageNo,
      x: clamp(x - width / 2, 0, page.width - width),
      y: clamp(y - height / 2, 0, page.height - height),
      width,
      height,
      required: true,
      label: "",
    };
    setFields((fs) => [...fs, f]);
    setSelected(f.key);
    setPlacing(null);
  }

  function updateField(key: string, patch: Partial<EditField>) {
    setFields((fs) => fs.map((f) => (f.key === key ? { ...f, ...patch } : f)));
  }

  const removeField = useCallback((key: string) => {
    setFields((fs) => fs.filter((f) => f.key !== key));
    setSelected(null);
  }, []);

  // Delete/Backspace로 선택한 칸 삭제, Esc로 배치 취소. 입력창에서는 무시한다.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement).tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
      if (e.key === "Escape") {
        setPlacing(null);
        setSelected(null);
      }
      if ((e.key === "Delete" || e.key === "Backspace") && selected) {
        e.preventDefault();
        removeField(selected);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [selected, removeField]);

  /** 끌어서 옮기기·크기 조절. 포인터 이벤트라 마우스와 터치 모두 된다. */
  function startDrag(e: React.PointerEvent, f: EditField, scale: number, mode: "move" | "resize") {
    e.preventDefault();
    e.stopPropagation();
    setSelected(f.key);
    setPlacing(null);
    const page = pageOf(f.page);
    const startX = e.clientX;
    const startY = e.clientY;
    const origin = { ...f };
    const target = e.currentTarget as HTMLElement;
    target.setPointerCapture(e.pointerId);

    const onMove = (ev: PointerEvent) => {
      const dx = (ev.clientX - startX) / scale;
      const dy = (ev.clientY - startY) / scale;
      if (mode === "move") {
        updateField(f.key, {
          x: clamp(origin.x + dx, 0, page.width - origin.width),
          y: clamp(origin.y + dy, 0, page.height - origin.height),
        });
      } else {
        updateField(f.key, {
          width: clamp(origin.width + dx, MIN_FIELD_SIZE, page.width - origin.x),
          height: clamp(origin.height + dy, MIN_FIELD_SIZE, page.height - origin.y),
        });
      }
    };
    const onUp = () => {
      target.removeEventListener("pointermove", onMove);
      target.removeEventListener("pointerup", onUp);
      target.removeEventListener("pointercancel", onUp);
    };
    target.addEventListener("pointermove", onMove);
    target.addEventListener("pointerup", onUp);
    target.addEventListener("pointercancel", onUp);
  }

  // ---------- 발송 ----------
  function sendProblem(): string | null {
    if (signers.length === 0) return "서명자를 한 명 이상 추가해 주세요.";
    const withFields = new Set(fields.map((f) => f.recipientKey));
    const empty = signers.find((s) => !withFields.has(s.key));
    if (empty) return `${empty.name || empty.email || "서명자"}에게 입력 칸을 하나 이상 배치해 주세요.`;
    return localProblem(recipients);
  }

  async function send() {
    setSendError(null);
    const problem = sendProblem();
    if (problem) {
      setSendError(problem);
      return;
    }
    if (!confirm(`${recipients.length}명에게 요청을 발송할까요? 발송 후에는 수정할 수 없습니다.`)) return;
    setSending(true);
    try {
      await saveInfo();
      if (!(await saveLayout())) throw new Error("저장하지 못해 발송을 멈췄습니다.");
      await api(`/api/v1/requests/${requestId}/send`, { method: "POST" });
      router.push(`/requests/${requestId}`);
    } catch (e) {
      setSendError(e instanceof ApiError || e instanceof Error ? e.message : String(e));
      setSending(false);
    }
  }

  const selectedField = fields.find((f) => f.key === selected) ?? null;

  // ---------- 화면 ----------
  return (
    <div className="editor">
      <aside className="editor-side">
        <section className="panel">
          <h2>요청 정보</h2>
          <label className="field">
            제목
            <input value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} onBlur={saveInfo} />
          </label>
          <label className="field">
            메시지 (선택)
            <textarea value={message} maxLength={2000} rows={3} onChange={(e) => setMessage(e.target.value)} onBlur={saveInfo} />
          </label>
          <label className="field">
            만료일 (선택, 최대 90일)
            <input type="date" value={expiresOn} onChange={(e) => setExpiresOn(e.target.value)} onBlur={saveInfo} />
          </label>
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2>수신자</h2>
            <button type="button" className="btn btn-small" onClick={addRecipient} disabled={recipients.length >= MAX_RECIPIENTS}>
              + 추가
            </button>
          </div>
          {recipients.length === 0 && <p className="muted small">수신자를 추가하세요.</p>}
          {recipients.map((r, i) => (
            <div
              key={r.key}
              className={`recipient ${activeRecipient === r.key ? "active" : ""}`}
              style={{ borderLeftColor: colorOf(r.key) }}
            >
              <div className="recipient-head">
                <label className="check small">
                  <input
                    type="radio"
                    name="active-recipient"
                    checked={activeRecipient === r.key}
                    disabled={r.role !== "SIGNER"}
                    onChange={() => setActiveRecipient(r.key)}
                  />
                  {i + 1}번 {r.role === "SIGNER" ? `· 칸 ${fields.filter((f) => f.recipientKey === r.key).length}개` : ""}
                </label>
                <button type="button" className="btn btn-ghost btn-small btn-danger" onClick={() => removeRecipient(r.key)}>
                  삭제
                </button>
              </div>
              <input placeholder="이름" value={r.name} maxLength={100} onChange={(e) => updateRecipient(r.key, { name: e.target.value })} />
              <input
                placeholder="이메일"
                type="email"
                value={r.email}
                maxLength={255}
                onChange={(e) => updateRecipient(r.key, { email: e.target.value })}
              />
              <select value={r.role} onChange={(e) => updateRecipient(r.key, { role: e.target.value as RecipientRole })}>
                <option value="SIGNER">서명자 (칸 작성)</option>
                <option value="VIEWER">열람자 (완료본만 받음)</option>
              </select>
            </div>
          ))}
        </section>

        <section className="panel">
          <h2>칸 추가</h2>
          <p className="muted small">
            {activeRecipient
              ? "종류를 고른 뒤 PDF에서 놓을 위치를 클릭하세요."
              : "서명자를 추가하고 왼쪽 동그라미로 선택하세요."}
          </p>
          <div className="palette">
            {FIELD_TYPES.map((t) => (
              <button
                key={t}
                type="button"
                className={`btn ${placing === t ? "btn-primary" : ""}`}
                onClick={() => startPlacing(t)}
                disabled={!activeRecipient}
              >
                {FIELD_LABEL[t]}
              </button>
            ))}
          </div>
        </section>

        {selectedField && (
          <section className="panel">
            <div className="panel-head">
              <h2>선택한 칸</h2>
              <button type="button" className="btn btn-ghost btn-small btn-danger" onClick={() => removeField(selectedField.key)}>
                삭제
              </button>
            </div>
            <p className="muted small">
              {FIELD_LABEL[selectedField.type]} · {selectedField.page}쪽 · {Math.round(selectedField.width)}×
              {Math.round(selectedField.height)}pt
            </p>
            <label className="field">
              담당 서명자
              <select value={selectedField.recipientKey} onChange={(e) => updateField(selectedField.key, { recipientKey: e.target.value })}>
                {signers.map((s) => (
                  <option key={s.key} value={s.key}>
                    {s.name || s.email || "(이름 없음)"}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              안내 문구 (선택)
              <input
                value={selectedField.label}
                maxLength={100}
                placeholder="예: 성명"
                onChange={(e) => updateField(selectedField.key, { label: e.target.value })}
              />
            </label>
            <label className="check small">
              <input
                type="checkbox"
                checked={selectedField.required}
                onChange={(e) => updateField(selectedField.key, { required: e.target.checked })}
              />
              필수 입력
            </label>
          </section>
        )}

        <section className="panel send-panel">
          <SaveIndicator state={saveState} message={saveMessage} />
          {sendError && <p className="err small">{sendError}</p>}
          <button type="button" className="btn btn-accent btn-block" onClick={send} disabled={sending}>
            {sending ? "발송 중…" : "발송하기"}
          </button>
        </section>
      </aside>

      <div className="editor-canvas">
        {placing && (
          <div className="placing-hint">
            <strong>{FIELD_LABEL[placing]}</strong> 칸을 놓을 위치를 클릭하세요 · Esc로 취소
          </div>
        )}
        {loadError && <p className="err">{loadError}</p>}
        {!doc && !loadError && <p className="muted">PDF를 불러오는 중…</p>}
        {doc && (
          <PdfPages
            doc={doc}
            pages={pages}
            pageCursor={placing ? "crosshair" : undefined}
            onPageClick={placeAt}
            overlay={(page, scale) =>
              fields
                .filter((f) => f.page === page.pageNo)
                .map((f) => {
                  const color = colorOf(f.recipientKey);
                  const who = recipients.find((r) => r.key === f.recipientKey);
                  return (
                    <div
                      key={f.key}
                      className={`field-box ${selected === f.key ? "selected" : ""}`}
                      style={{
                        left: f.x * scale,
                        top: f.y * scale,
                        width: f.width * scale,
                        height: f.height * scale,
                        borderColor: color,
                        background: `${color}22`,
                      }}
                      title={`${FIELD_LABEL[f.type]} · ${who?.name || who?.email || ""}`}
                      onPointerDown={(e) => startDrag(e, f, scale, "move")}
                    >
                      <span className="field-box-label">
                        {f.type === "CHECKBOX" ? "✓" : f.label || FIELD_LABEL[f.type]}
                        {f.required && f.type !== "CHECKBOX" ? " *" : ""}
                      </span>
                      <span
                        className="field-box-handle"
                        style={{ background: color }}
                        onPointerDown={(e) => startDrag(e, f, scale, "resize")}
                      />
                    </div>
                  );
                })
            }
          />
        )}
      </div>
    </div>
  );
}

function SaveIndicator({ state, message }: { state: SaveState; message: string | null }) {
  const text: Record<SaveState, string> = {
    saved: "모든 변경 사항 저장됨",
    dirty: "변경 사항 저장 대기 중…",
    saving: "저장 중…",
    invalid: "저장 보류",
    error: "저장 실패",
  };
  const cls = state === "saved" ? "ok" : state === "invalid" || state === "error" ? "err" : "muted";
  return (
    <p className={`small ${cls}`} role="status">
      {text[state]}
      {message && state !== "saved" ? `: ${message}` : ""}
    </p>
  );
}

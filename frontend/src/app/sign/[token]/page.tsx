"use client";

import { useParams } from "next/navigation";
import { type FormEvent, Suspense, useCallback, useEffect, useRef, useState } from "react";
import { PdfPages } from "@/components/pdf-pages";
import { SignaturePad } from "@/components/sign/signature-pad";
import { api } from "@/lib/api";
import { loadPdf, type PDFDocumentProxy } from "@/lib/pdf";
import { FIELD_LABEL, fmtDate, type FieldType, type PageView, type RequestStatus } from "@/lib/types";

// ---------- API 형식 (backend SignDtos) ----------

type LinkInfo = {
  title: string;
  senderName: string;
  maskedEmail: string;
  role: "SIGNER" | "VIEWER";
  requestStatus: RequestStatus;
  verified: boolean;
};

type MyField = {
  id: string;
  type: FieldType;
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
  required: boolean;
  label: string | null;
  value: string | null;
  hasImage: boolean;
  filled: boolean;
};

type SigningView = {
  title: string;
  message: string | null;
  senderName: string;
  senderEmail: string;
  requestStatus: RequestStatus;
  expiresAt: string | null;
  recipientName: string;
  recipientEmail: string;
  role: "SIGNER" | "VIEWER";
  recipientStatus: "PENDING" | "SENT" | "VIEWED" | "COMPLETED" | "DECLINED";
  canEdit: boolean;
  resultAvailable: boolean;
  documentName: string;
  pages: PageView[];
  fields: MyField[];
};

type Stage = "loading" | "otp" | "ready" | "declined" | "closed";

export default function SignPage() {
  return (
    <Suspense>
      <SignPageInner />
    </Suspense>
  );
}

function SignPageInner() {
  const { token } = useParams<{ token: string }>();
  const [stage, setStage] = useState<Stage>("loading");
  const [info, setInfo] = useState<LinkInfo | null>(null);
  const [view, setView] = useState<SigningView | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadView = useCallback(async () => {
    const v = await api<SigningView>("/sign/api/request");
    setView(v);
    setStage("ready");
  }, []);

  useEffect(() => {
    (async () => {
      try {
        await api<void>("/sign/api/csrf"); // XSRF-TOKEN 쿠키 받기
        const i = await api<LinkInfo>("/sign/api/session/link", { method: "POST", json: { token } });
        setInfo(i);
        if (i.verified) await loadView();
        else setStage("otp");
      } catch (e) {
        setError((e as Error).message);
        setStage("closed");
      }
    })();
  }, [token, loadView]);

  return (
    <>
      <header className="header">
        <span className="brand">PDF 서명 요청</span>
      </header>
      <main className={stage === "ready" ? "container-wide" : "container narrow-sign"}>
        {stage === "loading" && <p className="muted">확인 중…</p>}
        {stage === "closed" && (
          <section className="card">
            <h1>문서를 열 수 없습니다</h1>
            <p className="err">{error}</p>
          </section>
        )}
        {stage === "otp" && info && <OtpStep token={token} info={info} onVerified={loadView} />}
        {stage === "ready" && view && (
          <Signing view={view} setView={setView} onDeclined={() => setStage("declined")} />
        )}
        {stage === "declined" && (
          <section className="card">
            <h1>요청을 거절했습니다</h1>
            <p className="muted">요청자에게 알렸습니다. 이 창을 닫으셔도 됩니다.</p>
          </section>
        )}
      </main>
    </>
  );
}

// ---------- 1단계: 인증번호 ----------

function OtpStep({ token, info, onVerified }: { token: string; info: LinkInfo; onVerified: () => Promise<void> }) {
  const [sent, setSent] = useState(false);
  const [code, setCode] = useState("");
  const [wait, setWait] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (wait <= 0) return;
    const t = setTimeout(() => setWait((w) => w - 1), 1000);
    return () => clearTimeout(t);
  }, [wait]);

  async function send() {
    setError(null);
    setBusy(true);
    try {
      const r = await api<{ resendAfterSeconds: number }>("/sign/api/session/otp", { method: "POST", json: { token } });
      setSent(true);
      setWait(r.resendAfterSeconds);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function verify(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api<void>("/sign/api/session/verify", { method: "POST", json: { token, code } });
      await onVerified();
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  const done = info.requestStatus === "COMPLETED";

  return (
    <section className="card">
      <h1>{info.title}</h1>
      <p className="muted">
        {done
          ? "모든 참여자가 작성을 마쳤습니다. 본인 확인 후 완료된 문서를 내려받을 수 있습니다."
          : `${info.senderName}님이 작성과 서명을 요청했습니다.`}
      </p>
      <p>
        본인 확인을 위해 <strong>{info.maskedEmail}</strong>(으)로 인증번호를 보내 드립니다.
      </p>
      {!sent ? (
        <button type="button" className="btn btn-primary btn-block" onClick={send} disabled={busy}>
          인증번호 받기
        </button>
      ) : (
        <form className="otp-form" onSubmit={verify}>
          <input
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="\d{6}"
            maxLength={6}
            placeholder="6자리 숫자"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
            autoFocus
            aria-label="인증번호"
          />
          <button type="submit" className="btn btn-primary" disabled={busy || code.length !== 6}>
            확인
          </button>
          <button type="button" className="btn btn-ghost" onClick={send} disabled={busy || wait > 0}>
            {wait > 0 ? `다시 받기 (${wait}초)` : "다시 받기"}
          </button>
        </form>
      )}
      {sent && <p className="muted small">메일이 보이지 않으면 스팸함도 확인해 주세요. 인증번호는 10분 동안 유효합니다.</p>}
      {error && <p className="err">{error}</p>}
    </section>
  );
}

// ---------- 2단계: 작성 ----------

function Signing({
  view,
  setView,
  onDeclined,
}: {
  view: SigningView;
  setView: (v: SigningView) => void;
  onDeclined: () => void;
}) {
  const [doc, setDoc] = useState<PDFDocumentProxy | null>(null);
  const [editing, setEditing] = useState<MyField | null>(null);
  const [images, setImages] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const imagesRef = useRef(images);

  useEffect(() => {
    imagesRef.current = images;
  }, [images]);

  useEffect(() => {
    let alive = true;
    let loaded: PDFDocumentProxy | null = null;
    fetch("/sign/api/document", { credentials: "same-origin" })
      .then((r) => {
        if (!r.ok) throw new Error(`문서를 불러오지 못했습니다 (HTTP ${r.status})`);
        return r.arrayBuffer();
      })
      .then(loadPdf)
      .then((d) => {
        loaded = d;
        if (alive) setDoc(d);
        else void d.loadingTask.destroy();
      })
      .catch((e: Error) => alive && setError(e.message));
    return () => {
      alive = false;
      void loaded?.loadingTask.destroy();
      Object.values(imagesRef.current).forEach(URL.revokeObjectURL);
    };
  }, []);

  // 이미 올린 서명 이미지 미리보기
  const loadImage = useCallback(async (fieldId: string) => {
    const r = await fetch(`/sign/api/fields/${fieldId}/image`, { credentials: "same-origin" });
    if (!r.ok) return;
    const url = URL.createObjectURL(await r.blob());
    setImages((m) => {
      if (m[fieldId]) URL.revokeObjectURL(m[fieldId]);
      return { ...m, [fieldId]: url };
    });
  }, []);

  useEffect(() => {
    view.fields.filter((f) => f.hasImage).forEach((f) => void loadImage(f.id));
    // 처음 한 번만
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function replaceField(f: MyField) {
    setView({ ...view, fields: view.fields.map((x) => (x.id === f.id ? f : x)) });
  }

  async function saveValue(f: MyField, value: string | null) {
    setError(null);
    try {
      replaceField(await api<MyField>(`/sign/api/fields/${f.id}`, { method: "PUT", json: { value } }));
      return true;
    } catch (e) {
      setError((e as Error).message);
      return false;
    }
  }

  async function saveImage(f: MyField, blob: Blob) {
    setError(null);
    const form = new FormData();
    form.append("file", blob, "signature.png");
    try {
      replaceField(await api<MyField>(`/sign/api/fields/${f.id}/image`, { method: "POST", body: form }));
      await loadImage(f.id);
      return true;
    } catch (e) {
      setError((e as Error).message);
      return false;
    }
  }

  function onFieldClick(f: MyField) {
    if (!view.canEdit) return;
    if (f.type === "CHECKBOX") {
      void saveValue(f, f.value === "true" ? "false" : "true");
      return;
    }
    setEditing(f);
  }

  async function submit() {
    setError(null);
    if (!confirm("제출하면 더 이상 수정할 수 없습니다. 제출할까요?")) return;
    setBusy(true);
    try {
      setView(await api<SigningView>("/sign/api/submit", { method: "POST" }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function decline() {
    const reason = prompt("거절 사유를 적어 주세요 (선택). 거절하면 이 요청은 종료됩니다.");
    if (reason === null) return;
    setBusy(true);
    try {
      await api<void>("/sign/api/decline", { method: "POST", json: { reason } });
      onDeclined();
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  const required = view.fields.filter((f) => f.required);
  const filledRequired = required.filter((f) => f.filled).length;
  const submitted = view.recipientStatus === "COMPLETED";

  return (
    <div className="sign-layout">
      <section className="card sign-head">
        <div>
          <h1>{view.title}</h1>
          <p className="muted small">
            요청자 {view.senderName} ({view.senderEmail}) · {view.documentName}
            {view.expiresAt ? ` · 기한 ${fmtDate(view.expiresAt)}` : ""}
          </p>
          {view.message && <p className="sign-message">{view.message}</p>}
        </div>
        <div className="sign-actions">
          {view.resultAvailable && (
            // 페이지 이동이 아니라 API의 파일 응답이라 <Link> 대신 <a>를 쓴다
            // eslint-disable-next-line @next/next/no-html-link-for-pages
            <a className="btn btn-primary" href="/sign/api/result">
              완료된 문서 내려받기
            </a>
          )}
          {view.canEdit && (
            <>
              <span className="small muted">
                필수 {filledRequired}/{required.length}
              </span>
              <button type="button" className="btn btn-primary" onClick={submit} disabled={busy || filledRequired < required.length}>
                제출
              </button>
              <button type="button" className="btn btn-danger" onClick={decline} disabled={busy}>
                거절
              </button>
            </>
          )}
        </div>
      </section>

      {submitted && !view.resultAvailable && (
        <p className="notice-bar">제출했습니다. 다른 참여자가 모두 마치면 완료된 문서를 메일로 보내 드립니다.</p>
      )}
      {view.role === "VIEWER" && !view.resultAvailable && <p className="notice-bar">문서가 완료되면 내려받을 수 있습니다.</p>}
      {view.canEdit && <p className="muted small">노란 칸을 눌러 작성하세요. 체크 칸은 누를 때마다 바뀝니다.</p>}
      {error && <p className="err">{error}</p>}

      {!doc && !error && <p className="muted">문서를 불러오는 중…</p>}
      {doc && (
        <PdfPages
          doc={doc}
          pages={view.pages}
          overlay={(page, scale) =>
            view.fields
              .filter((f) => f.page === page.pageNo)
              .map((f) => (
                <button
                  type="button"
                  key={f.id}
                  className={`sign-field ${f.filled ? "filled" : ""} ${f.required ? "required" : ""}`}
                  style={{ left: f.x * scale, top: f.y * scale, width: f.width * scale, height: f.height * scale }}
                  onClick={() => onFieldClick(f)}
                  disabled={!view.canEdit}
                  aria-label={`${f.label || FIELD_LABEL[f.type]}${f.required ? " (필수)" : ""}`}
                >
                  <FieldContent f={f} scale={scale} image={images[f.id]} />
                </button>
              ))
          }
        />
      )}

      {editing && (
        <FieldDialog
          field={editing}
          onClose={() => setEditing(null)}
          onSaveValue={async (v) => (await saveValue(editing, v)) && setEditing(null)}
          onSaveImage={async (b) => (await saveImage(editing, b)) && setEditing(null)}
        />
      )}
    </div>
  );
}

function FieldContent({ f, scale, image }: { f: MyField; scale: number; image?: string }) {
  if (f.type === "SIGNATURE" && image) {
    // blob: URL 미리보기라 next/image(최적화 서버)를 쓰지 않는다
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={image} alt="서명" className="sign-field-img" />;
  }
  if (f.type === "CHECKBOX") {
    return <span style={{ fontSize: f.height * scale * 0.8 }}>{f.value === "true" ? "✓" : ""}</span>;
  }
  if (f.filled && f.value) {
    return (
      <span className="sign-field-text" style={{ fontSize: Math.min(f.height * scale * 0.6, 16) }}>
        {f.value}
      </span>
    );
  }
  return (
    <span className="sign-field-hint">
      {f.label || FIELD_LABEL[f.type]}
      {f.required ? " *" : ""}
    </span>
  );
}

function FieldDialog({
  field,
  onClose,
  onSaveValue,
  onSaveImage,
}: {
  field: MyField;
  onClose: () => void;
  onSaveValue: (v: string | null) => Promise<unknown>;
  onSaveImage: (b: Blob) => Promise<unknown>;
}) {
  const today = new Date().toLocaleDateString("sv-SE"); // YYYY-MM-DD
  const [value, setValue] = useState(field.value ?? (field.type === "DATE" ? today : ""));
  const [blob, setBlob] = useState<Blob | null>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    setBusy(true);
    if (field.type === "SIGNATURE") {
      if (blob) await onSaveImage(blob);
    } else {
      await onSaveValue(value.trim() || null);
    }
    setBusy(false);
  }

  return (
    <div className="dialog-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h2>{field.label || FIELD_LABEL[field.type]}</h2>
        {field.type === "TEXT" && (
          <input autoFocus value={value} maxLength={500} onChange={(e) => setValue(e.target.value)} />
        )}
        {field.type === "DATE" && <input type="date" autoFocus value={value} onChange={(e) => setValue(e.target.value)} />}
        {field.type === "SIGNATURE" && (
          <>
            <SignaturePad onChange={setBlob} />
            <label className="small muted upload">
              또는 서명·도장 이미지 올리기 (PNG, JPEG, 2MB 이하)
              <input
                type="file"
                accept="image/png,image/jpeg"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) void onSaveImage(f);
                }}
              />
            </label>
          </>
        )}
        <div className="dialog-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            취소
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={save}
            disabled={busy || (field.type === "SIGNATURE" ? !blob : false)}
          >
            저장
          </button>
        </div>
      </div>
    </div>
  );
}


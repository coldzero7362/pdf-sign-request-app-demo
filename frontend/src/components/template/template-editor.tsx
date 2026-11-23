"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { PdfPages } from "@/components/pdf-pages";
import { api } from "@/lib/api";
import { fetchPdfFrom, type PDFDocumentProxy } from "@/lib/pdf";
import {
  FIXED_FIELD_COLOR,
  MIN_FIELD_SIZE,
  RECIPIENT_COLORS,
  SIGNING_MODE_LABEL,
  TEMPLATE_FIELD_LABEL,
  TEMPLATE_FIELD_SIZE,
  type PageView,
  type SigningMode,
  type TemplateFieldType,
  type TemplateView,
} from "@/lib/types";

// ---------- 편집 상태 ----------
// 자리 key는 API 요청 JSON의 "slot" 값이 되므로 한 번 정하면 바꾸지 않는다.

type EditSlot = { key: string; label: string };
type EditField = {
  key: string;
  slotKey: string | null;
  type: TemplateFieldType;
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
  required: boolean;
  label: string;
  fixedValue: string;
};
type SaveState = "saved" | "dirty" | "saving" | "invalid" | "error";

const MAX_SLOTS = 10;
const MAX_FIELDS = 300;
const PALETTE: TemplateFieldType[] = ["SIGNATURE", "TEXT", "DATE", "CHECKBOX", "TEXT_FIXED"];

let seq = 0;
const newKey = () => `f-${Date.now().toString(36)}-${(seq++).toString(36)}`;
const clamp = (v: number, min: number, max: number) => Math.min(Math.max(v, min), max);
const round = (v: number) => Math.round(v * 100) / 100;

function nextSlotKey(slots: EditSlot[]) {
  for (let i = 1; ; i++) if (!slots.some((s) => s.key === `signer${i}`)) return `signer${i}`;
}

function localProblem(name: string, slots: EditSlot[], fields: EditField[]): string | null {
  if (!name.trim()) return "템플릿 이름을 입력해 주세요.";
  if (slots.some((s) => !s.label.trim())) return "수신자 자리 이름을 입력해 주세요.";
  if (fields.some((f) => f.type === "TEXT_FIXED" && !f.fixedValue.trim())) return "고정 텍스트 칸에 값을 입력해 주세요.";
  return null;
}

export function TemplateEditor({ initial }: { initial: TemplateView }) {
  const router = useRouter();
  const templateNo = initial.templateNo;
  const pages = initial.pdf.pages;

  const [doc, setDoc] = useState<PDFDocumentProxy | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [name, setName] = useState(initial.name);
  const [mode, setMode] = useState<SigningMode>(initial.signingMode);
  const [periodDays, setPeriodDays] = useState(initial.defaultPeriodDays);
  const [slots, setSlots] = useState<EditSlot[]>(initial.slots.map((s) => ({ key: s.key, label: s.label })));
  const [fields, setFields] = useState<EditField[]>(
    initial.fields.map((f) => ({
      key: f.id,
      slotKey: f.slotKey,
      type: f.type,
      page: f.page,
      x: f.x,
      y: f.y,
      width: f.width,
      height: f.height,
      required: f.required,
      label: f.label ?? "",
      fixedValue: f.fixedValue ?? "",
    })),
  );
  const [activeSlot, setActiveSlot] = useState<string | null>(initial.slots[0]?.key ?? null);
  const [placing, setPlacing] = useState<TemplateFieldType | null>(null);
  const [selected, setSelected] = useState<string | null>(null);

  const [server, setServer] = useState<TemplateView>(initial);
  const [saveState, setSaveState] = useState<SaveState>("saved");
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  // ---------- PDF ----------
  useEffect(() => {
    let alive = true;
    let loaded: PDFDocumentProxy | null = null;
    fetchPdfFrom(`/api/v1/templates/${templateNo}/pdf`)
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
  }, [templateNo]);

  // ---------- 자동 저장 ----------
  const latest = useRef({ name, mode, periodDays, slots, fields });
  const savingRef = useRef<Promise<boolean> | null>(null);
  const pendingRef = useRef(false);
  const firstRender = useRef(true);

  useEffect(() => {
    latest.current = { name, mode, periodDays, slots, fields };
  }, [name, mode, periodDays, slots, fields]);

  const saveOnce = useCallback(async (): Promise<boolean> => {
    const s = latest.current;
    const problem = localProblem(s.name, s.slots, s.fields);
    if (problem) {
      setSaveState("invalid");
      setSaveMessage(problem);
      return false;
    }
    setSaveState("saving");
    try {
      const res = await api<TemplateView>(`/api/v1/templates/${templateNo}`, {
        method: "PUT",
        json: {
          name: s.name.trim(),
          signingMode: s.mode,
          defaultPeriodDays: s.periodDays,
          slots: s.slots.map((x) => ({ key: x.key, label: x.label.trim() })),
          fields: s.fields.map((f) => ({
            slotKey: f.type === "TEXT_FIXED" ? null : f.slotKey,
            type: f.type,
            page: f.page,
            x: round(f.x),
            y: round(f.y),
            width: round(f.width),
            height: round(f.height),
            required: f.type === "TEXT_FIXED" ? false : f.required,
            label: f.label.trim() || null,
            fixedValue: f.type === "TEXT_FIXED" ? f.fixedValue.trim() : null,
          })),
        },
      });
      setServer(res);
      setSaveState("saved");
      setSaveMessage(null);
      return true;
    } catch (e) {
      setSaveState("error");
      setSaveMessage((e as Error).message);
      return false;
    }
  }, [templateNo]);

  const save = useCallback((): Promise<boolean> => {
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
    const t = setTimeout(() => void save(), 800);
    return () => clearTimeout(t);
  }, [name, mode, periodDays, slots, fields, save]);

  useEffect(() => {
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (saveState !== "saved") e.preventDefault();
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [saveState]);

  // ---------- 자리 ----------
  const colorOf = useCallback(
    (slotKey: string | null) => {
      if (!slotKey) return FIXED_FIELD_COLOR;
      const i = slots.findIndex((s) => s.key === slotKey);
      return RECIPIENT_COLORS[Math.max(0, i) % RECIPIENT_COLORS.length];
    },
    [slots],
  );

  function addSlot() {
    const key = nextSlotKey(slots);
    setSlots((s) => [...s, { key, label: `서명자 ${s.length + 1}` }]);
    setActiveSlot(key);
  }

  function moveSlot(index: number, dir: -1 | 1) {
    setSlots((s) => {
      const j = index + dir;
      if (j < 0 || j >= s.length) return s;
      const next = [...s];
      [next[index], next[j]] = [next[j], next[index]];
      return next;
    });
  }

  function removeSlot(key: string) {
    if (slots.length <= 1) {
      alert("수신자 자리는 하나 이상 있어야 합니다.");
      return;
    }
    const count = fields.filter((f) => f.slotKey === key).length;
    if (count > 0 && !confirm(`이 자리의 칸 ${count}개도 함께 지웁니다. 계속할까요?`)) return;
    setFields((fs) => fs.filter((f) => f.slotKey !== key));
    setSlots((s) => s.filter((x) => x.key !== key));
    if (activeSlot === key) setActiveSlot(slots.find((x) => x.key !== key)?.key ?? null);
  }

  // ---------- 칸 ----------
  const pageOf = useCallback((no: number) => pages.find((p) => p.pageNo === no)!, [pages]);

  function startPlacing(type: TemplateFieldType) {
    if (type !== "TEXT_FIXED" && !activeSlot) {
      alert("먼저 칸을 채울 수신자 자리를 고르세요.");
      return;
    }
    setPlacing((cur) => (cur === type ? null : type));
    setSelected(null);
  }

  function placeAt(page: PageView, x: number, y: number) {
    if (!placing) {
      setSelected(null);
      return;
    }
    if (fields.length >= MAX_FIELDS) {
      alert(`칸은 최대 ${MAX_FIELDS}개까지 둘 수 있습니다.`);
      return;
    }
    const size = TEMPLATE_FIELD_SIZE[placing];
    const width = Math.min(size.width, page.width);
    const height = Math.min(size.height, page.height);
    const fixed = placing === "TEXT_FIXED";
    const f: EditField = {
      key: newKey(),
      slotKey: fixed ? null : activeSlot,
      type: placing,
      page: page.pageNo,
      x: clamp(x - width / 2, 0, page.width - width),
      y: clamp(y - height / 2, 0, page.height - height),
      width,
      height,
      required: !fixed && placing !== "CHECKBOX",
      label: "",
      fixedValue: "",
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

  function startDrag(e: React.PointerEvent, f: EditField, scale: number, dragMode: "move" | "resize") {
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
      if (dragMode === "move") {
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

  async function removeTemplate() {
    if (!confirm(`템플릿 ${templateNo}을(를) 삭제할까요? 이미 보낸 문서에는 영향이 없습니다.`)) return;
    try {
      await api(`/api/v1/templates/${templateNo}`, { method: "DELETE" });
      router.push("/templates");
    } catch (e) {
      setSaveMessage((e as Error).message);
    }
  }

  async function copyExample() {
    await navigator.clipboard.writeText(JSON.stringify(server.requestExample, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  const selectedField = fields.find((f) => f.key === selected) ?? null;

  return (
    <div className="editor">
      <aside className="editor-side">
        <section className="panel">
          <div className="panel-head">
            <h2>{templateNo}</h2>
            <button type="button" className="btn btn-ghost btn-small btn-danger" onClick={removeTemplate}>
              삭제
            </button>
          </div>
          <label className="field">
            템플릿 이름
            <input value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
          </label>
          <div className="field">
            서명 순서
            <div className="segmented" role="radiogroup" aria-label="서명 순서">
              {(["SEQUENTIAL", "PARALLEL"] as SigningMode[]).map((m) => (
                <button
                  key={m}
                  type="button"
                  role="radio"
                  aria-checked={mode === m}
                  className={mode === m ? "on" : ""}
                  onClick={() => setMode(m)}
                >
                  {SIGNING_MODE_LABEL[m]}
                </button>
              ))}
            </div>
            <span className="muted small">
              {mode === "SEQUENTIAL" ? "위 순서대로, 앞 사람이 끝나야 다음 사람에게 요청이 갑니다." : "모든 서명자에게 한 번에 요청이 갑니다."}
            </span>
          </div>
          <label className="field">
            기본 서명 기간 (일, 최대 31)
            <input
              type="number"
              min={1}
              max={31}
              value={periodDays}
              onChange={(e) => setPeriodDays(clamp(Number(e.target.value) || 1, 1, 31))}
            />
          </label>
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2>수신자 자리</h2>
            <button type="button" className="btn btn-small" onClick={addSlot} disabled={slots.length >= MAX_SLOTS}>
              + 추가
            </button>
          </div>
          <p className="muted small">요청할 때 자리마다 실제 서명자를 넣습니다. 칸을 놓을 자리를 동그라미로 고르세요.</p>
          {slots.map((s, i) => (
            <div key={s.key} className={`recipient ${activeSlot === s.key ? "active" : ""}`} style={{ borderLeftColor: colorOf(s.key) }}>
              <div className="recipient-head">
                <label className="check small">
                  <input type="radio" name="active-slot" checked={activeSlot === s.key} onChange={() => setActiveSlot(s.key)} />
                  {mode === "SEQUENTIAL" ? `${i + 1}번째 · ` : ""}칸 {fields.filter((f) => f.slotKey === s.key).length}개
                </label>
                <span className="actions">
                  <button type="button" className="btn btn-ghost btn-small" aria-label="위로" onClick={() => moveSlot(i, -1)} disabled={i === 0}>
                    ↑
                  </button>
                  <button type="button" className="btn btn-ghost btn-small" aria-label="아래로" onClick={() => moveSlot(i, 1)} disabled={i === slots.length - 1}>
                    ↓
                  </button>
                  <button type="button" className="btn btn-ghost btn-small btn-danger" onClick={() => removeSlot(s.key)}>
                    삭제
                  </button>
                </span>
              </div>
              <input value={s.label} maxLength={50} placeholder="자리 이름 (예: 근로자)" onChange={(e) => setSlots((xs) => xs.map((x) => (x.key === s.key ? { ...x, label: e.target.value } : x)))} />
              <span className="muted small">
                API 자리 키 <code>{s.key}</code>
              </span>
            </div>
          ))}
        </section>

        <section className="panel">
          <h2>칸 추가</h2>
          <p className="muted small">종류를 고른 뒤 PDF에서 놓을 위치를 클릭하세요. 고정 텍스트는 요청자가 값을 미리 채우는 칸입니다.</p>
          <div className="palette">
            {PALETTE.map((t) => (
              <button
                key={t}
                type="button"
                className={`btn ${placing === t ? "btn-primary" : ""}`}
                onClick={() => startPlacing(t)}
                disabled={t !== "TEXT_FIXED" && !activeSlot}
              >
                {TEMPLATE_FIELD_LABEL[t]}
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
              {TEMPLATE_FIELD_LABEL[selectedField.type]} · {selectedField.page}쪽 · {Math.round(selectedField.width)}×{Math.round(selectedField.height)}pt
            </p>
            {selectedField.type === "TEXT_FIXED" ? (
              <label className="field">
                고정 값 (서명자는 고칠 수 없음)
                <textarea
                  rows={2}
                  maxLength={500}
                  value={selectedField.fixedValue}
                  onChange={(e) => updateField(selectedField.key, { fixedValue: e.target.value })}
                />
              </label>
            ) : (
              <>
                <label className="field">
                  채울 자리
                  <select value={selectedField.slotKey ?? ""} onChange={(e) => updateField(selectedField.key, { slotKey: e.target.value })}>
                    {slots.map((s) => (
                      <option key={s.key} value={s.key}>
                        {s.label || s.key}
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
                  <input type="checkbox" checked={selectedField.required} onChange={(e) => updateField(selectedField.key, { required: e.target.checked })} />
                  필수 입력
                </label>
              </>
            )}
          </section>
        )}

        <section className="panel">
          <div className="panel-head">
            <h2>API로 요청하기</h2>
            <button type="button" className="btn btn-small" onClick={copyExample}>
              {copied ? "복사됨" : "복사"}
            </button>
          </div>
          <p className="muted small">
            <code>POST /api/v1/documents</code> 요청 예시입니다 (서명 요청 API는 다음 단계에서 열립니다).
          </p>
          <pre className="code-block">{JSON.stringify(server.requestExample, null, 2)}</pre>
        </section>

        <section className="panel send-panel">
          <SaveIndicator state={saveState} message={saveMessage} />
          {server.ready ? (
            <p className="small ok">요청에 쓸 수 있습니다 · {server.revision}번째 판</p>
          ) : (
            <ul className="small err problems">
              {server.problems.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          )}
        </section>
      </aside>

      <div className="editor-canvas">
        {placing && (
          <div className="placing-hint">
            <strong>{TEMPLATE_FIELD_LABEL[placing]}</strong> 칸을 놓을 위치를 클릭하세요 · Esc로 취소
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
                  const color = colorOf(f.slotKey);
                  const slot = slots.find((s) => s.key === f.slotKey);
                  const text =
                    f.type === "TEXT_FIXED"
                      ? f.fixedValue || "고정 텍스트"
                      : f.type === "CHECKBOX"
                        ? "✓"
                        : (f.label || TEMPLATE_FIELD_LABEL[f.type]) + (f.required ? " *" : "");
                  return (
                    <div
                      key={f.key}
                      className={`field-box ${selected === f.key ? "selected" : ""} ${f.type === "TEXT_FIXED" ? "fixed" : ""}`}
                      style={{
                        left: f.x * scale,
                        top: f.y * scale,
                        width: f.width * scale,
                        height: f.height * scale,
                        borderColor: color,
                        background: `${color}22`,
                      }}
                      title={`${TEMPLATE_FIELD_LABEL[f.type]}${slot ? ` · ${slot.label}` : ""}`}
                      onPointerDown={(e) => startDrag(e, f, scale, "move")}
                    >
                      <span className="field-box-label">{text}</span>
                      <span className="field-box-handle" style={{ background: color }} onPointerDown={(e) => startDrag(e, f, scale, "resize")} />
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

"use client";

import { useEffect, useRef, useState } from "react";

/** 손글씨 서명 패드. 마우스·터치·펜 모두 포인터 이벤트로 받는다. */
export function SignaturePad({ onChange }: { onChange: (blob: Blob | null) => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const last = useRef<{ x: number; y: number } | null>(null);
  const [empty, setEmpty] = useState(true);

  useEffect(() => {
    const c = canvasRef.current!;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = c.getBoundingClientRect();
    c.width = Math.round(rect.width * dpr);
    c.height = Math.round(rect.height * dpr);
    const ctx = c.getContext("2d")!;
    ctx.scale(dpr, dpr);
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.lineWidth = 2.5;
    ctx.strokeStyle = "#111";
  }, []);

  function point(e: React.PointerEvent) {
    const r = canvasRef.current!.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  }

  function down(e: React.PointerEvent) {
    e.preventDefault();
    canvasRef.current!.setPointerCapture(e.pointerId);
    drawing.current = true;
    last.current = point(e);
  }

  function move(e: React.PointerEvent) {
    if (!drawing.current || !last.current) return;
    const p = point(e);
    const ctx = canvasRef.current!.getContext("2d")!;
    ctx.beginPath();
    ctx.moveTo(last.current.x, last.current.y);
    ctx.lineTo(p.x, p.y);
    ctx.stroke();
    last.current = p;
    if (empty) setEmpty(false);
  }

  function up() {
    if (!drawing.current) return;
    drawing.current = false;
    last.current = null;
    canvasRef.current!.toBlob((b) => onChange(b), "image/png");
  }

  function clear() {
    const c = canvasRef.current!;
    c.getContext("2d")!.clearRect(0, 0, c.width, c.height);
    setEmpty(true);
    onChange(null);
  }

  return (
    <div className="sigpad">
      <canvas
        ref={canvasRef}
        className="sigpad-canvas"
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={up}
        onPointerCancel={up}
        aria-label="서명 입력 영역"
      />
      <div className="sigpad-foot">
        <span className="muted small">{empty ? "여기에 서명하세요" : ""}</span>
        <button type="button" className="btn btn-ghost btn-small" onClick={clear}>
          지우기
        </button>
      </div>
    </div>
  );
}

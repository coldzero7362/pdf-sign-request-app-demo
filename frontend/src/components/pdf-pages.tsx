"use client";

import { type ReactNode, useEffect, useRef, useState } from "react";
import { renderPage, type PDFDocumentProxy } from "@/lib/pdf";
import type { PageView } from "@/lib/types";

const MAX_WIDTH = 900;

type Props = {
  doc: PDFDocumentProxy;
  pages: PageView[];
  /** 페이지 위에 겹쳐 그릴 내용 (칸 등). scale = CSS 픽셀 / pt */
  overlay?: (page: PageView, scale: number) => ReactNode;
  /** 페이지 빈 곳 클릭. 좌표는 pt, 왼쪽 위 원점 */
  onPageClick?: (page: PageView, x: number, y: number) => void;
  pageCursor?: string;
};

/** PDF 페이지를 세로로 이어서 그린다. 컨테이너 폭에 맞춰 크기를 정한다. */
export function PdfPages({ doc, pages, overlay, onPageClick, pageCursor }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const ro = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  return (
    <div ref={wrapRef} className="pdf-pages">
      {width > 0 &&
        pages.map((p) => {
          const scale = Math.min(width, MAX_WIDTH) / p.width;
          return (
            <PdfPage
              key={p.pageNo}
              doc={doc}
              page={p}
              scale={scale}
              overlay={overlay}
              onPageClick={onPageClick}
              cursor={pageCursor}
            />
          );
        })}
    </div>
  );
}

function PdfPage({
  doc,
  page,
  scale,
  overlay,
  onPageClick,
  cursor,
}: {
  doc: PDFDocumentProxy;
  page: PageView;
  scale: number;
  overlay?: Props["overlay"];
  onPageClick?: Props["onPageClick"];
  cursor?: string;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const w = page.width * scale;
  const h = page.height * scale;

  useEffect(() => {
    let cancelled = false;
    let task: { cancel: () => void; promise: Promise<void> } | null = null;
    const canvas = canvasRef.current;
    if (!canvas) return;
    renderPage(doc, page.pageNo, canvas, scale).then((t) => {
      if (cancelled) {
        t.cancel();
        return;
      }
      task = t;
      t.promise.catch(() => undefined); // 취소 시 생기는 오류는 무시
    });
    return () => {
      cancelled = true;
      task?.cancel();
    };
  }, [doc, page.pageNo, scale]);

  return (
    <div className="pdf-page-wrap">
      <div className="pdf-page-no">{page.pageNo}쪽</div>
      <div
        className="pdf-page"
        style={{ width: w, height: h, cursor }}
        onClick={(e) => {
          if (!onPageClick || e.target !== e.currentTarget.lastElementChild) return;
          const rect = e.currentTarget.getBoundingClientRect();
          onPageClick(page, (e.clientX - rect.left) / scale, (e.clientY - rect.top) / scale);
        }}
      >
        <canvas ref={canvasRef} style={{ width: w, height: h }} />
        <div className="pdf-overlay">{overlay?.(page, scale)}</div>
      </div>
    </div>
  );
}

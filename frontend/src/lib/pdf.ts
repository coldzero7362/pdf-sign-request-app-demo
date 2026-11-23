// pdf.js 래퍼 (브라우저 전용).
// 보안: 캔버스에만 그린다. 텍스트·주석 레이어와 PDF 내장 스크립트는 쓰지 않는다.
// pdf.js 6.2.108 미만에는 악성 PDF로 스크립트를 실행할 수 있는 취약점(CVE-2026-16633)이 있었다.
// legacy 빌드를 쓴다: 최신 빌드는 아주 최근 브라우저 기능(Map.getOrInsertComputed 등)을 써서
// 수신자가 쓰는 구형 휴대폰 브라우저에서 깨질 수 있다. legacy 빌드에는 보완 코드가 들어 있다.
import type { PDFDocumentProxy } from "pdfjs-dist";

export type { PDFDocumentProxy };

type PdfJs = typeof import("pdfjs-dist");
let lib: Promise<PdfJs> | null = null;

function pdfjs() {
  if (!lib) {
    lib = (import("pdfjs-dist/legacy/build/pdf.mjs") as Promise<PdfJs>).then((m) => {
      m.GlobalWorkerOptions.workerSrc = "/pdf.worker.min.mjs";
      return m;
    });
  }
  return lib;
}

export async function loadPdf(data: ArrayBuffer): Promise<PDFDocumentProxy> {
  const m = await pdfjs();
  return m.getDocument({ data, enableXfa: false, stopAtErrors: false }).promise;
}

export async function fetchPdfFrom(url: string): Promise<PDFDocumentProxy> {
  const res = await fetch(url, { credentials: "same-origin" });
  if (!res.ok) throw new Error(`PDF를 불러오지 못했습니다 (HTTP ${res.status})`);
  return loadPdf(await res.arrayBuffer());
}

export async function fetchPdf(documentId: string): Promise<PDFDocumentProxy> {
  return fetchPdfFrom(`/api/v1/documents/${documentId}/file`);
}

/** 페이지를 캔버스에 그린다. scale은 CSS 픽셀/pt, 화면 선명도를 위해 devicePixelRatio를 곱한다. */
export async function renderPage(doc: PDFDocumentProxy, pageNo: number, canvas: HTMLCanvasElement, scale: number) {
  const m = await pdfjs();
  const page = await doc.getPage(pageNo);
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const viewport = page.getViewport({ scale: scale * dpr });
  canvas.width = Math.floor(viewport.width);
  canvas.height = Math.floor(viewport.height);
  const task = page.render({ canvas, viewport, annotationMode: m.AnnotationMode.ENABLE });
  return task;
}

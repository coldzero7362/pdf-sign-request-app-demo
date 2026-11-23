// pdf.js 워커를 public/으로 복사한다 (빌드·개발 서버 시작 전에 자동 실행).
// 번들러 설정 없이 같은 출처에서 워커를 불러오기 위해서다.
import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
mkdirSync(join(root, "public"), { recursive: true });
copyFileSync(
  join(root, "node_modules/pdfjs-dist/legacy/build/pdf.worker.min.mjs"),
  join(root, "public/pdf.worker.min.mjs"),
);

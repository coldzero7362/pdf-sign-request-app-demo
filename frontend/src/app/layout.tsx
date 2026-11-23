import type { Metadata } from "next";
import { Inter, Noto_Sans_KR, Titillium_Web } from "next/font/google";
import "./globals.css";

// 글꼴은 빌드할 때 내려받아 같은 서버에서 제공한다 (브라우저가 Google에 직접 요청하지 않음).
// 제목: Titillium Web / 본문: Inter / 한글: Noto Sans KR (두 글꼴에 한글이 없어 보완)
const display = Titillium_Web({ subsets: ["latin"], weight: ["600", "700"], variable: "--font-display", display: "swap" });
const body = Inter({ subsets: ["latin"], variable: "--font-body", display: "swap" });
// 한글 하위 집합 옵션이 없어 미리 불러오지 않는다. 글자 범위별 파일 중 필요한 것만 받는다.
const korean = Noto_Sans_KR({ subsets: ["latin"], variable: "--font-kr", display: "swap", preload: false });

export const metadata: Metadata = {
  title: "PDF 서명 요청",
  description: "PDF에 입력 칸을 지정해 외부 사용자에게 작성과 서명을 요청하는 서비스 (MVP)",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className={`${display.variable} ${body.variable} ${korean.variable}`}>
      <body>{children}</body>
    </html>
  );
}

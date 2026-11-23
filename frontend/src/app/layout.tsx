import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "PDF 서명 요청",
  description: "PDF에 입력 칸을 지정해 외부 사용자에게 작성과 서명을 요청하는 서비스 (MVP)",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}

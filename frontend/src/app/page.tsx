import { HealthStatus } from "./health-status";

// 골격 단계 화면: 백엔드 연결만 확인한다.
// 대시보드, 요청 작성, 서명 화면은 이후 단계에서 추가한다.
export default function Home() {
  return (
    <main className="container">
      <h1 style={{ fontSize: 24, margin: "0 0 8px" }}>PDF 서명 요청</h1>
      <p className="muted" style={{ margin: "0 0 24px" }}>
        PDF에 입력 칸을 지정해 외부 사용자에게 작성과 서명을 요청하는 서비스 (MVP)
      </p>
      <HealthStatus />
    </main>
  );
}

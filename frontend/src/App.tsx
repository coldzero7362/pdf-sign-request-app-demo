import { useEffect, useState } from 'react'

type Health = { status: string; time: string }

// 골격 단계 화면: 백엔드 연결만 확인한다.
// 대시보드, 요청 작성, 서명 화면은 이후 단계에서 라우팅과 함께 추가한다.
export default function App() {
  const [health, setHealth] = useState<Health | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/v1/health')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<Health>
      })
      .then(setHealth)
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <main style={{ maxWidth: 640, margin: '0 auto', padding: '48px 16px' }}>
      <h1 style={{ fontSize: 24, margin: '0 0 8px' }}>PDF 서명 요청</h1>
      <p style={{ color: 'var(--muted)', margin: '0 0 24px' }}>
        PDF에 입력 칸을 지정해 외부 사용자에게 작성과 서명을 요청하는 서비스 (MVP)
      </p>
      <section
        style={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 8,
          padding: 16,
        }}
      >
        <strong>API 연결 상태: </strong>
        {health && <span style={{ color: 'var(--ok)' }}>{health.status}</span>}
        {error && <span style={{ color: 'var(--err)' }}>실패 ({error})</span>}
        {!health && !error && <span style={{ color: 'var(--muted)' }}>확인 중…</span>}
      </section>
    </main>
  )
}

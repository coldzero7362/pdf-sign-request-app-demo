"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Header } from "@/components/header";
import { useMe } from "@/components/use-me";

// 대시보드. 요청 목록은 다음 단계에서 붙인다.
export default function Home() {
  const router = useRouter();
  const { me, error } = useMe();

  useEffect(() => {
    if (me === null) router.replace("/login");
  }, [me, router]);

  return (
    <>
      <Header me={me} />
      <main className="container">
        {error && <p className="err">불러오지 못했습니다: {error}</p>}
        {me === undefined && !error && <p className="muted">확인 중…</p>}
        {me && (
          <>
            <h1>안녕하세요, {me.name}님</h1>
            <section className="card">
              <p style={{ marginTop: 0 }}>서명 요청 기능은 다음 단계에서 추가됩니다.</p>
              <p className="muted" style={{ marginBottom: 0 }}>
                API로 연동하려면 <Link href="/settings/tokens">API 토큰</Link>을 발급하세요.
              </p>
            </section>
          </>
        )}
      </main>
    </>
  );
}

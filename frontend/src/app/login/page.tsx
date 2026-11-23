"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect } from "react";
import { fetchMe, GOOGLE_LOGIN_URL } from "@/lib/api";

function LoginForm() {
  const router = useRouter();
  const failed = useSearchParams().has("error");

  useEffect(() => {
    // 이미 로그인했으면 대시보드로
    fetchMe()
      .then((me) => {
        if (me) router.replace("/");
      })
      .catch(() => undefined);
  }, [router]);

  return (
    <>
      {failed && <p className="err">로그인하지 못했습니다. 다시 시도해 주세요.</p>}
      <a className="btn btn-primary btn-block" href={GOOGLE_LOGIN_URL}>
        Google 계정으로 로그인
      </a>
    </>
  );
}

export default function LoginPage() {
  return (
    <main className="container narrow">
      <h1>PDF 서명 요청</h1>
      <p className="muted">PDF에 입력 칸을 지정해 외부 사용자에게 작성과 서명을 요청합니다.</p>
      <Suspense>
        <LoginForm />
      </Suspense>
    </main>
  );
}

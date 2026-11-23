"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect } from "react";
import { Header } from "@/components/header";
import { fetchMe, GOOGLE_LOGIN_URL } from "@/lib/api";

const STEPS = [
  { title: "PDF 올리기", text: "계약서, 신청서 등 서명이나 입력을 받을 PDF를 올립니다." },
  { title: "칸 배치하기", text: "서명, 이름, 날짜, 체크 칸을 문서 위에 끌어다 놓고 받을 사람을 정합니다." },
  { title: "서명 받기", text: "받는 사람은 메일 링크와 인증번호로 들어와 작성합니다. 완료본은 자동으로 공유됩니다." },
];

function LoginAction() {
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
      <a className="btn btn-accent btn-lg" href={GOOGLE_LOGIN_URL}>
        Google 계정으로 시작하기
      </a>
    </>
  );
}

export default function LoginPage() {
  return (
    <>
      <Header />
      <section className="band">
        <div className="band-inner">
          <h1 style={{ fontSize: "clamp(34px, 6vw, 56px)", lineHeight: 1.1, maxWidth: 640 }}>
            PDF 한 장으로 서명 요청을 끝내세요
          </h1>
          <p className="lead">입력 칸을 지정해 외부 사용자에게 작성과 서명을 요청하고, 완료된 문서를 바로 공유합니다.</p>
          <Suspense>
            <LoginAction />
          </Suspense>
        </div>
      </section>
      <main className="container">
        <div className="steps">
          {STEPS.map((s, i) => (
            <section key={s.title} className="card card-feature step">
              <div className="step-no">STEP {i + 1}</div>
              <h3>{s.title}</h3>
              <p className="muted" style={{ margin: 0 }}>
                {s.text}
              </p>
            </section>
          ))}
        </div>
      </main>
    </>
  );
}

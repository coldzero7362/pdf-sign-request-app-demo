"use client";

import { useRouter } from "next/navigation";
import { type ReactNode, useEffect } from "react";
import { Header } from "@/components/header";
import { useMe } from "@/components/use-me";
import type { Me } from "@/lib/api";

/**
 * 로그인이 필요한 화면의 공통 틀. 로그인 안 했으면 /login으로 보낸다.
 * band: 본문 위에 화면 폭 전체로 까는 연한 구획 띠 (Jobright feature-band)
 */
export function RequireLogin({
  children,
  band,
  wide,
}: {
  children: (me: Me) => ReactNode;
  band?: (me: Me) => ReactNode;
  wide?: boolean;
}) {
  const router = useRouter();
  const { me, error } = useMe();

  useEffect(() => {
    if (me === null) router.replace("/login");
  }, [me, router]);

  return (
    <>
      <Header me={me} />
      {me && band && (
        <section className="band">
          <div className="band-inner">{band(me)}</div>
        </section>
      )}
      <main className={wide ? "container-wide" : "container"}>
        {error && <p className="err">불러오지 못했습니다: {error}</p>}
        {me === undefined && !error && <p className="muted">확인 중…</p>}
        {me && children(me)}
      </main>
    </>
  );
}

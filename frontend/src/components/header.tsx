"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { logout, type Me } from "@/lib/api";

export function Header({ me }: { me: Me | null | undefined }) {
  const router = useRouter();

  async function onLogout() {
    await logout();
    router.replace("/login");
  }

  return (
    <header className="header">
      <Link href="/" className="brand">
        PDF 서명 요청
      </Link>
      {me && (
        <nav className="nav">
          <Link href="/settings/tokens">API 토큰</Link>
          <span className="muted">{me.email}</span>
          <button type="button" className="btn btn-ghost" onClick={onLogout}>
            로그아웃
          </button>
        </nav>
      )}
    </header>
  );
}

"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { logout, type Me } from "@/lib/api";

/**
 * 상단 바 (Jobright nav-bar: 흰 바탕, 오른쪽에 진한 알약 버튼).
 * 좁은 화면에서는 링크를 메뉴로 접고 "+ 새 요청" 알약만 남긴다.
 */
export function Header({ me }: { me?: Me | null }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);

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
          <div id="nav-links" className={`nav-links ${open ? "open" : ""}`} onClick={() => setOpen(false)}>
            <Link href="/">내 요청</Link>
            <Link href="/settings/tokens">API 토큰</Link>
            <span className="nav-user">{me.email}</span>
            <button type="button" className="btn btn-ghost btn-small" onClick={onLogout}>
              로그아웃
            </button>
          </div>
          <Link href="/requests/new" className="btn btn-primary btn-small">
            + 새 요청
          </Link>
          <button
            type="button"
            className="btn btn-ghost btn-small menu-btn"
            aria-expanded={open}
            aria-controls="nav-links"
            aria-label="메뉴"
            onClick={() => setOpen((o) => !o)}
          >
            {open ? "✕" : "☰"}
          </button>
        </nav>
      )}
    </header>
  );
}

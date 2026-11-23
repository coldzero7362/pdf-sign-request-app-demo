"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Header } from "@/components/header";
import { useMe } from "@/components/use-me";
import { api } from "@/lib/api";

type TokenView = {
  id: string;
  name: string;
  prefix: string;
  scopes: string[];
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
  active: boolean;
};

const SCOPES = [
  { value: "requests:read", label: "요청 조회" },
  { value: "requests:write", label: "요청 생성·변경" },
];

const fmt = (iso: string | null) => (iso ? new Date(iso).toLocaleString("ko-KR") : "-");

export default function TokensPage() {
  const router = useRouter();
  const { me } = useMe();
  const [tokens, setTokens] = useState<TokenView[]>([]);
  const [name, setName] = useState("");
  const [scopes, setScopes] = useState<string[]>(["requests:read"]);
  const [days, setDays] = useState(90);
  const [created, setCreated] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api<TokenView[]>("/api/v1/me/tokens")
      .then(setTokens)
      .catch((e: Error) => setError(e.message));
  }, []);

  useEffect(() => {
    if (me === null) router.replace("/login");
    if (me) load();
  }, [me, load, router]);

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const res = await api<{ token: string }>("/api/v1/me/tokens", {
        method: "POST",
        json: { name, scopes, expiresInDays: days },
      });
      setCreated(res.token);
      setName("");
      load();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function onRevoke(t: TokenView) {
    if (!confirm(`'${t.name}' 토큰을 폐기할까요? 이 토큰을 쓰는 연동은 바로 중단됩니다.`)) return;
    try {
      await api<void>(`/api/v1/me/tokens/${t.id}`, { method: "DELETE" });
      load();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  function toggleScope(value: string) {
    setScopes((s) => (s.includes(value) ? s.filter((v) => v !== value) : [...s, value]));
  }

  return (
    <>
      <Header me={me} />
      <main className="container">
        <h1>API 토큰</h1>
        <p className="muted">
          외부 시스템에서 API를 호출할 때 <code>Authorization: Bearer &lt;토큰&gt;</code> 헤더로 사용합니다.
        </p>

        {created && (
          <section className="card notice">
            <strong>새 토큰이 발급되었습니다. 지금만 볼 수 있으니 안전한 곳에 보관하세요.</strong>
            <div className="token-box">
              <code>{created}</code>
              <button type="button" className="btn" onClick={() => navigator.clipboard.writeText(created)}>
                복사
              </button>
            </div>
            <button type="button" className="btn btn-ghost" onClick={() => setCreated(null)}>
              닫기
            </button>
          </section>
        )}

        {error && <p className="err">{error}</p>}

        <form className="card form" onSubmit={onCreate}>
          <h2>새 토큰 발급</h2>
          <label>
            이름
            <input value={name} onChange={(e) => setName(e.target.value)} maxLength={50} required placeholder="예: 사내 결재 연동" />
          </label>
          <fieldset>
            <legend>권한</legend>
            {SCOPES.map((s) => (
              <label key={s.value} className="check">
                <input type="checkbox" checked={scopes.includes(s.value)} onChange={() => toggleScope(s.value)} />
                {s.label} <code>{s.value}</code>
              </label>
            ))}
          </fieldset>
          <label>
            만료
            <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
              <option value={30}>30일</option>
              <option value={90}>90일</option>
              <option value={365}>365일</option>
            </select>
          </label>
          <button type="submit" className="btn btn-primary" disabled={busy || !name.trim() || scopes.length === 0}>
            발급
          </button>
        </form>

        <section className="card">
          <h2>발급한 토큰</h2>
          {tokens.length === 0 ? (
            <p className="muted">아직 없습니다.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>이름</th>
                    <th>토큰</th>
                    <th>권한</th>
                    <th>만료</th>
                    <th>마지막 사용</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {tokens.map((t) => (
                    <tr key={t.id} className={t.active ? "" : "inactive"}>
                      <td>{t.name}</td>
                      <td>
                        <code>{t.prefix}…</code>
                      </td>
                      <td>{t.scopes.join(", ")}</td>
                      <td>{fmt(t.expiresAt)}</td>
                      <td>{fmt(t.lastUsedAt)}</td>
                      <td>
                        {t.active ? (
                          <button type="button" className="btn btn-danger" onClick={() => onRevoke(t)}>
                            폐기
                          </button>
                        ) : (
                          <span className="muted">{t.revokedAt ? "폐기됨" : "만료됨"}</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>
    </>
  );
}

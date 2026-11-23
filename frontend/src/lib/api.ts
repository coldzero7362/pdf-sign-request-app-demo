// 백엔드 API 호출 도우미 (브라우저 전용).
// 세션 쿠키로 인증하므로, 변경 요청에는 Spring Security CSRF 토큰(XSRF-TOKEN 쿠키)을 헤더로 함께 보낸다.

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

function csrfToken(): string | undefined {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const { json, headers, ...rest } = init;
  const method = (rest.method ?? "GET").toUpperCase();
  const h = new Headers(headers);
  h.set("Accept", "application/json");
  if (json !== undefined) h.set("Content-Type", "application/json");
  if (method !== "GET" && method !== "HEAD") {
    const token = csrfToken();
    if (token) h.set("X-XSRF-TOKEN", token);
  }

  const res = await fetch(path, {
    ...rest,
    method,
    headers: h,
    credentials: "same-origin",
    body: json !== undefined ? JSON.stringify(json) : rest.body,
  });

  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const problem = await res.json();
      message = problem.detail ?? problem.title ?? message;
    } catch {
      // 본문이 JSON이 아니면 상태 코드만 쓴다
    }
    throw new ApiError(res.status, message);
  }
  return (res.status === 204 ? undefined : await res.json()) as T;
}

export type Me = {
  id: string;
  email: string;
  name: string;
  authType: "session" | "token";
  scopes: string[];
};

/** 로그인 안 했으면 null. */
export async function fetchMe(): Promise<Me | null> {
  try {
    return await api<Me>("/api/v1/me");
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null;
    throw e;
  }
}

export async function logout(): Promise<void> {
  await api<void>("/logout", { method: "POST" });
}

export const GOOGLE_LOGIN_URL = "/oauth2/authorization/google";

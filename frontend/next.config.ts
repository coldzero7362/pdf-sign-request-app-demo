import type { NextConfig } from "next";

// 백엔드(Spring Boot) 주소. next.config는 빌드 시점에 고정되므로 빌드 인자로 넘긴다.
// - 로컬 개발: http://localhost:8080
// - Docker: http://api:8080 (compose 내부 네트워크)
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

// 백엔드로 넘길 경로. 브라우저 입장에서는 같은 출처라 CORS 설정이 필요 없다.
const backendPaths = [
  "/api/:path*",
  "/oauth2/:path*",
  "/login/oauth2/:path*",
  "/logout",
  "/sign/api/:path*",
  "/swagger-ui/:path*",
  "/v3/api-docs/:path*",
];

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  cacheComponents: true,
  partialPrefetching: true,
  // 이미지 최적화 API(/_next/image)는 쓰지 않으므로 끈다. (2026년 RCE 권고 2건의 공격 지점)
  images: { unoptimized: true },
  async rewrites() {
    return backendPaths.map((source) => ({
      source,
      destination: `${backendUrl}${source}`,
    }));
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;

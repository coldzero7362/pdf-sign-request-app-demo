# Next.js 화면 (standalone 출력)
FROM node:26-slim AS deps
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

FROM node:26-slim AS build
WORKDIR /src
ENV NEXT_TELEMETRY_DISABLED=1
# next.config의 rewrites 대상은 빌드 시점에 고정된다
ARG BACKEND_URL=http://api:8080
ENV BACKEND_URL=${BACKEND_URL}
COPY --from=deps /src/node_modules ./node_modules
COPY frontend/ ./
RUN npm run build

FROM node:26-slim
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0 PORT=3000
COPY --from=build --chown=node:node /src/.next/standalone ./
COPY --from=build --chown=node:node /src/.next/static ./.next/static
COPY --from=build --chown=node:node /src/public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]

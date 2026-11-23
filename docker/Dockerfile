# 1) 프런트 빌드
FROM node:22-slim AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2) 백엔드 빌드 (프런트 결과를 static으로 포함)
FROM gradle:8.14.3-jdk21 AS backend
WORKDIR /src/backend
COPY backend/ ./
COPY --from=frontend /src/frontend/dist /src/frontend/dist
RUN ./gradlew bootJar --no-daemon -q -PfrontendDist=/src/frontend/dist \
 && cp build/libs/*-SNAPSHOT.jar /app.jar

# 3) 실행
FROM eclipse-temurin:21-jre
RUN groupadd -g 1001 app && useradd -u 1001 -g app -m app \
 && mkdir -p /data/storage && chown app:app /data/storage
USER app
COPY --from=backend /app.jar /app/app.jar
ENV APP_STORAGE_DIR=/data/storage
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

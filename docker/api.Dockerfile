# Spring Boot API
FROM gradle:9.9.2-jdk25 AS build
WORKDIR /src
COPY backend/ ./
RUN ./gradlew bootJar --no-daemon -q && cp build/libs/*-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:25-jre
RUN groupadd -g 1001 app && useradd -u 1001 -g app -m app \
 && mkdir -p /data/storage && chown app:app /data/storage
USER app
COPY --from=build /app.jar /app/app.jar
ENV APP_STORAGE_DIR=/data/storage
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

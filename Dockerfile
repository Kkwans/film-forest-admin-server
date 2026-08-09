# syntax=docker/dockerfile:1.7

FROM --platform=$BUILDPLATFORM docker.io/library/maven:3.9.12-eclipse-temurin-17@sha256:a0603aab698040d9c94259f379ec0487da1678560748d6c7508483034033c53d AS builder

WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress -DskipTests package

FROM docker.io/library/eclipse-temurin:17.0.19_10-jre-jammy@sha256:a9a83259bb576657930d10b003c251f17d9e42d33e0024e718aefe8228b984d6

WORKDIR /app
COPY --from=builder --chown=10001:10001 /workspace/target/film-forest-admin-*.jar /app/app.jar

USER 10001:10001
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl --fail --silent --show-error http://127.0.0.1:8081/api/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]

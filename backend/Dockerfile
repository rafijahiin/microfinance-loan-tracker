# Multi-stage: the JDK and the Maven cache stay in the build stage, so the
# image that ships carries a JRE and one jar rather than a toolchain.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are resolved from the POM alone first. Layer caching then means
# a source-only change does not re-download the world on every build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Runs as a non-root user. A container process that does not need root should
# not have it, and this one only needs to read a jar and open a socket.
RUN addgroup -S app && adduser -S -G app app
COPY --from=build /build/target/loan-tracker-1.0.0.jar app.jar
USER app

EXPOSE 8080

# Container-aware heap sizing: without it the JVM reads the host's memory
# rather than the container limit and can be killed by the OOM killer under a
# tight cgroup.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

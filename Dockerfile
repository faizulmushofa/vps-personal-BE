# Stage 1: Dependencies (cached)
FROM eclipse-temurin:21-jdk-jammy AS dependency-cache

WORKDIR /build

COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY .mvn .mvn
COPY pom.xml pom.xml

RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Stage 2: Build
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

COPY --from=dependency-cache /build /build
COPY src src

# Single-threaded build with aggressive memory limit
ENV MAVEN_OPTS="-Xmx512m"
RUN ./mvnw clean package -Dmaven.test.skip=true -B -T 1C

# Stage 3: Runtime (ultra-minimal for 1GB VPS)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /build/target/*.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup -s /sbin/nologin && \
    mkdir -p /app/Data && \
    chown -R appuser:appgroup /app
USER appuser

EXPOSE 8090
EXPOSE 50051

# JVM tuned for 1GB: AGGRESSIVE settings
# -Xms128m -Xmx512m: minimal heap (accept slower startup/GC frequency)
# -XX:+UseZGC: Use ZGC instead of G1 (lower latency, simpler, better for 1GB)
# -XX:+AlwaysPreTouch: pre-allocate heap (predictable behavior on constrained VPS)
# -XX:+UnlockExperimentalVMOptions -XX:ZUncommitDelay=30s: uncommit unused pages
# -Djava.util.concurrent.ForkJoinPool.common.parallelism=2: limit to 2 vCPU
# -Dspring.jmx.enabled=false: disable JMX
# -Dlogging.level.io.grpc=WARN: reduce logs
# -XX:-OmitStackTraceInFastThrow: preserve stack traces (for debugging on small VPS)
ENTRYPOINT ["java", \
  "-Xms128m", \
  "-Xmx512m", \
  "-XX:+UseZGC", \
  "-XX:+AlwaysPreTouch", \
  "-XX:+UnlockExperimentalVMOptions", \
  "-XX:ZUncommitDelay=30", \
  "-Djava.util.concurrent.ForkJoinPool.common.parallelism=2", \
  "-Dspring.jmx.enabled=false", \
  "-Dlogging.level.io.grpc=WARN", \
  "-XX:-OmitStackTraceInFastThrow", \
  "-Djava.net.preferIPv4Stack=true", \
  "-jar", "app.jar"]

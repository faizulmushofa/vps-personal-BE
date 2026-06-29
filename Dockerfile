# Dockerfile optimized for GHA pre-built JAR and 1GB VPS
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install curl for health checking or utility purposes
RUN apk add --no-cache curl

# Copy the pre-compiled JAR file built by the GitHub Actions runner
COPY target/*.jar app.jar

# Run as unprivileged user for security
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

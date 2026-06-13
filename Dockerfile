# ── Stage 1: Build ────────────────────────────────────────────────────────────
# eclipse-temurin:21-jdk-jammy is the canonical JDK 21 build image.
# frontend-maven-plugin downloads its own Node/npm into target/ during build,
# so we don't need a separate Node layer.
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Cache the Maven dependency layer separately from application source.
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build the fat jar + React bundle in one pass.
COPY src src
COPY frontend frontend
RUN ./mvnw clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for least-privilege runtime
RUN addgroup -S ledgerbridge && adduser -S ledgerbridge -G ledgerbridge
USER ledgerbridge

COPY --from=build /workspace/target/*.jar app.jar

# Render injects PORT (default 10000). Use shell form so ${PORT:-8080} expands.
EXPOSE 10000

ENTRYPOINT ["sh", "-c", \
  "exec java -Xmx200m -Xms64m -XX:+UseSerialGC \
   -Djava.security.egd=file:/dev/./urandom \
   -Dserver.port=${PORT:-8080} \
   -jar /app/app.jar"]

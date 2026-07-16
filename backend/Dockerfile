# OpenCRM Backend — Multi-Stage-Build (docs/11-deployment-und-betrieb.md, Abschnitt 4)
# Build:   maven:3.9-eclipse-temurin-21
# Runtime: eclipse-temurin:21-jre, non-root

# --- Build-Stage ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencies separat aufloesen, damit Docker-Layer-Caching greift,
# solange sich nur src/ aendert:
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp package -DskipTests

# --- Runtime-Stage -------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

# Non-root User (fester UID/GID fuer runAsUser im Pod-SecurityContext)
RUN groupadd --system --gid 10001 opencrm \
    && useradd --system --uid 10001 --gid opencrm --home-dir /app --shell /usr/sbin/nologin opencrm

WORKDIR /app
COPY --from=build /workspace/target/opencrm-backend-*.jar /app/app.jar

USER 10001
EXPOSE 8080

# MaxRAMPercentage statt fixem -Xmx: Heap folgt dem Container-Memory-Limit
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

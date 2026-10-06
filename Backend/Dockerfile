# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
# Cache dependency resolution as its own layer.
RUN ./mvnw -B dependency:go-offline || true

COPY src src
RUN ./mvnw -B clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system nexus && adduser --system --ingroup nexus nexus
COPY --from=build /build/target/*.jar app.jar
RUN chown nexus:nexus app.jar
USER nexus

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=docker
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

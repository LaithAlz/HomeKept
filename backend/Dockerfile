# HomeKept backend — Spring Boot 4.1 / Java 17.
# Multi-stage: build the boot jar with the Gradle wrapper, run it on a slim JRE.
# Used by Render (select the "Docker" runtime, Root Directory = backend).

# ---- build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
# bootJar does not run tests (they need Docker/Testcontainers, unavailable here).
RUN chmod +x gradlew && ./gradlew clean bootJar --no-daemon

# ---- run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
# The plain jar is disabled in build.gradle, so this is the single boot jar.
COPY --from=build /app/build/libs/*.jar app.jar
# The app reads server.port from the platform-injected PORT (default 8080).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

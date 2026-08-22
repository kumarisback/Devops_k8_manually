# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy Gradle files
COPY gradle gradle
COPY gradlew .
COPY settings.gradle .
COPY build.gradle .

# Grant execute permission and resolve dependencies
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy source code and build
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user with fixed numeric uid and gid
RUN addgroup -g 1000 spring && adduser -u 1000 -S spring -G spring
USER 1000:1000

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

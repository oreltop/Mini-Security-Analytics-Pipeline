# ---- Build stage: compile and package the jar with the pinned Maven wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Resolve dependencies first so this layer is cached across source-only changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

# Build the application (tests run via `./mvnw test`, not during image build).
COPY src ./src
RUN ./mvnw -B -DskipTests package

# ---- Run stage: slim JRE with just the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

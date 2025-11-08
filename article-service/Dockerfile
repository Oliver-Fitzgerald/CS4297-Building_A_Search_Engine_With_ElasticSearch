# Build stage
FROM gradle:8.7-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle clean bootJar --no-daemon

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/article-service-0.0.1.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

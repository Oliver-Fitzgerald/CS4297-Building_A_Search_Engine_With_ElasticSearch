# ---- build stage ----
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /home/gradle/project

# copy the whole repo so Gradle can resolve multi-module deps
COPY . .

# build only the search-service jar
RUN gradle :search-service:bootJar --no-daemon

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# copy the built jar
COPY --from=build /home/gradle/project/search-service/build/libs/*.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m"
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar"]

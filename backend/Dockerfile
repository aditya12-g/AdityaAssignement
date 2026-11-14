FROM maven:3.9.9-eclipse-temurin-21 AS source
WORKDIR /workspace
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src

FROM source AS test
CMD ["mvn", "--batch-mode", "--no-transfer-progress", "test"]

FROM source AS build
RUN mvn --batch-mode --no-transfer-progress package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /workspace/target/task-auth-app-1.0.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21.0.12_8-jdk-noble AS build
WORKDIR /workspace
COPY . .
ARG APP_MODULE
RUN --mount=type=cache,id=phongkham-maven,target=/root/.m2,sharing=locked \
    chmod +x ./mvnw && ./mvnw -B -Dmaven.test.skip=true -pl ${APP_MODULE} -am package

FROM eclipse-temurin:21.0.12_8-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
ARG APP_MODULE
ARG APP_JAR
COPY --from=build /workspace/${APP_MODULE}/target/${APP_JAR}.jar /app/app.jar
ENV SERVER_PORT=8081
EXPOSE 8081
USER 10001:10001
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-Duser.timezone=UTC","-jar","/app/app.jar"]
HEALTHCHECK --interval=15s --timeout=4s --start-period=45s --retries=6 CMD curl -fsS http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness || exit 1


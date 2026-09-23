FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
ARG OTEL_AGENT_VERSION=2.31.1
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "export OTEL_EXPORTER_OTLP_ENDPOINT=${OTEL_EXPORTER_OTLP_ENDPOINT:-http://${COLLECTOR_HOSTPORT:-otel-collector:4318}}; export INVENTORY_SERVICE_URL=${INVENTORY_SERVICE_URL:-http://${INVENTORY_HOSTPORT:-inventory-service:8083}}; export PAYMENT_SERVICE_URL=${PAYMENT_SERVICE_URL:-http://${PAYMENT_HOSTPORT:-payment-service:8082}}; exec java -XX:MaxRAMPercentage=75 -javaagent:/otel/opentelemetry-javaagent.jar -jar /app/app.jar"]

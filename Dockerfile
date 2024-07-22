FROM maven:3-openjdk-17-slim AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml clean package

FROM openjdk:17-oracle
COPY --from=build /usr/src/app/target/remote-falcon-control-panel.jar /usr/app/remote-falcon-control-panel.jar
EXPOSE 8080

ARG COLLECT_OTEL
ARG OTEL_COLLECTOR_ENDPOINT

ARG OTEL_CONFIG
RUN if [ "$COLLECT_OTEL" = "true" ]; then OTEL_CONFIG="-javaagent:/usr/app/opentelemetry-javaagent.jar -Dotel.exporter.otlp.endpoint=${OTEL_COLLECTOR_ENDPOINT} -Dotel.resource.attributes=service.name=remote-falcon-control-panel" ; else OTEL_CONFIG= ; fi

ADD 'https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar' /usr/app/opentelemetry-javaagent.jar

ENTRYPOINT exec java $JAVA_OPTS $OTEL_CONFIG -XX:FlightRecorderOptions=stackdepth=256 -jar /usr/app/remote-falcon-control-panel.jar
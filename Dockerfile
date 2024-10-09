FROM maven:3-openjdk-17-slim AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml clean package

FROM openjdk:17-oracle
COPY --from=build /usr/src/app/target/remote-falcon-control-panel.jar /usr/app/remote-falcon-control-panel.jar
EXPOSE 8080

ARG OTEL_OPTS
ARG OTEL_EXPORTER_OTLP_ENDPOINT
ARG OTEL_EXPORTER_OTLP_PROTOCOL
ARG OTEL_SERVICE_NAME
ARG OTEL_RESOURCE_ATTRIBUTES
ENV OTEL_OPTS=${OTEL_OPTS}
ENV OTEL_EXPORTER_OTLP_ENDPOINT=${OTEL_EXPORTER_OTLP_ENDPOINT}
ENV OTEL_EXPORTER_OTLP_PROTOCOL=${OTEL_EXPORTER_OTLP_PROTOCOL}
ENV OTEL_SERVICE_NAME=${OTEL_SERVICE_NAME}
ENV OTEL_RESOURCE_ATTRIBUTES=${OTEL_RESOURCE_ATTRIBUTES}

ADD 'https://github.com/grafana/grafana-opentelemetry-java/releases/latest/download/grafana-opentelemetry-java.jar' /usr/app/grafana-opentelemetry-java.jar

ENTRYPOINT exec java $JAVA_OPTS $OTEL_OPTS -XX:FlightRecorderOptions=stackdepth=256 -XX:MaxRAMPercentage=75.0 -jar /usr/app/remote-falcon-control-panel.jar
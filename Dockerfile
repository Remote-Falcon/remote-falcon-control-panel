FROM maven:3-openjdk-17-slim AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml clean package

FROM openjdk:17-oracle
COPY --from=build /usr/src/app/target/remote-falcon-control-panel.jar /usr/app/remote-falcon-control-panel.jar
EXPOSE 8080

ARG OTEL_OPTS
ENV OTEL_OPTS=${OTEL_OPTS}

ADD 'https://dtdg.co/latest-java-tracer' /usr/app/dd-java-agent.jar

ENTRYPOINT exec java $JAVA_OPTS $OTEL_OPTS -XX:FlightRecorderOptions=stackdepth=256 -XX:MaxRAMPercentage=75.0 -jar /usr/app/remote-falcon-control-panel.jar
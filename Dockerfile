FROM maven:3-openjdk-17-slim AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml clean package

FROM openjdk:17-oracle
COPY --from=build /usr/src/app/target/remote-falcon-control-panel.jar /usr/app/remote-falcon-control-panel.jar
EXPOSE 8080

ADD 'https://dtdg.co/latest-java-tracer' /usr/app/dd-java-agent.jar

ARG DD_GIT_REPOSITORY_URL
ARG DD_GIT_COMMIT_SHA
ENV DD_GIT_REPOSITORY_URL=${DD_GIT_REPOSITORY_URL}
ENV DD_GIT_COMMIT_SHA=${DD_GIT_COMMIT_SHA}

RUN echo ${DD_GIT_REPOSITORY_URL}
RUN echo ${DD_GIT_COMMIT_SHA}

ENTRYPOINT exec java $JAVA_OPTS -javaagent:/usr/app/dd-java-agent.jar -Ddd.logs.injection=true \
    -Ddd.service=remote-falcon-control-panel -Ddd.profiling.enabled=true -XX:FlightRecorderOptions=stackdepth=256 \
    -Ddd.version=${DD_GIT_COMMIT_SHA} -Ddd.dynamic.instrumentation.enabled=true -jar /usr/app/remote-falcon-control-panel.jar
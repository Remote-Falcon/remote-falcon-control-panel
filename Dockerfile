FROM ghcr.io/graalvm/native-image-community:21 AS build
WORKDIR /usr/src/app
RUN microdnf install -y maven && microdnf clean all
COPY pom.xml .
COPY src ./src
RUN mvn -Pnative -DskipTests package

FROM registry.access.redhat.com/ubi9/ubi-minimal:9.2
WORKDIR /usr/app
RUN microdnf install -y zlib && microdnf clean all
COPY --from=build /usr/src/app/target/remote-falcon-control-panel /usr/app/remote-falcon-control-panel
EXPOSE 8080
ENTRYPOINT ["/usr/app/remote-falcon-control-panel"]

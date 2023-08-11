FROM clojure:openjdk-19-bullseye AS builder

ENV DISPLAY :99

RUN apt-get -qq update && \
    apt-get -qq -y install xvfb libgtk3.0-cil\
    && rm -rf /var/lib/apt/lists/* /var/log/dpkg.log

RUN mkdir -p /build

WORKDIR /build

COPY deps.edn /build/

RUN clojure -A:log:client:oxygen -P &&\
    clojure -A:log:server -P &&\
    clojure -A:build -P

COPY ./ /build

RUN Xvfb :99 -screen 0 800x600x8 -nolisten tcp &\
    clojure -T:build build-server

FROM openjdk:19-jdk-bullseye

RUN mkdir -p /service
WORKDIR /service

ENV JDK_JAVA_OPTIONS "-XshowSettings:system -XX:+UseContainerSupport -XX:MaxRAMPercentage=90"

COPY --from=builder /build/org.zdl.lex.server.jar /service

ENTRYPOINT ["java", "-jar", "/service/org.zdl.lex.server.jar"]

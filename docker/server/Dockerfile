FROM clojure:temurin-19-tools-deps AS builder

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

FROM clojure:temurin-19-tools-deps

RUN mkdir -p /service
WORKDIR /service

ENV JDK_JAVA_OPTIONS "-XshowSettings:system -XX:+UseContainerSupport -XX:MaxRAMPercentage=75"

COPY --from=builder /build/org.zdl.lex.server.jar /service

ENTRYPOINT ["java", "-jar", "/service/org.zdl.lex.server.jar"]

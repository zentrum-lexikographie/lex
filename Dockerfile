FROM clojure:temurin-17-tools-deps AS builder

ENV DISPLAY=:99

RUN apt-get -qq update && \
    apt-get -qq -y install xvfb libgtk3.0-cil\
    && rm -rf /var/lib/apt/lists/* /var/log/dpkg.log

RUN mkdir -p /build

WORKDIR /build

COPY deps.edn /build/

RUN clojure -A:client:oxygen -P && clojure -A:build -P

COPY ./ /build

RUN Xvfb :99 -screen 0 800x600x8 -nolisten tcp &\
    clojure -T:build client

FROM clojure:temurin-24-tools-deps

RUN mkdir -p /service
WORKDIR /service

COPY deps.edn /service/

RUN clojure -A:server -P

COPY ./ /service

COPY --from=builder\
    /build/oxygen/plugin/lib/org.zdl.lex.client.jar\
    /service/oxygen/plugin/lib/org.zdl.lex.client.jar

RUN clojure -T:build server


ENTRYPOINT ["clojure", "-M:server", "-m", "zdl.lex.server"]

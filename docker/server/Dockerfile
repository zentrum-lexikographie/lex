FROM openjdk:14-jdk-alpine
COPY org.zdl.lex.server.jar /app/org.zdl.lex.server.jar

ENV ZDL_LEX_DATA_DIR "/data"
VOLUME "/data"
WORKDIR "/data"

ENTRYPOINT ["java", "-cp", "/app/org.zdl.lex.server.jar", "clojure.main", "-m", "zdl-lex-server.core"]

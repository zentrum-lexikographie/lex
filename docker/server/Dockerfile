FROM openjdk:14-jdk-buster

ENV ZDL_LEX_USER "lex"
ENV ZDL_LEX_UID "1010"
ENV ZDL_LEX_GROUP "lex"
ENV ZDL_LEX_GID "1010"
ENV ZDL_LEX_DATA_DIR "/data"
VOLUME "/data"
WORKDIR "/data"

RUN set -ex; \
  groupadd -r --gid "$ZDL_LEX_GID" "$ZDL_LEX_GROUP"; \
  useradd -r --uid "$ZDL_LEX_UID" --gid "$ZDL_LEX_GID" \
    --home-dir /data "$ZDL_LEX_USER"

COPY org.zdl.lex.server.jar /app/org.zdl.lex.server.jar

RUN set -ex; \
  chown -R $ZDL_LEX_USER:$ZDL_LEX_GROUP /app /data

USER $ZDL_LEX_USER
ENTRYPOINT ["java", "-cp", "/app/org.zdl.lex.server.jar", "clojure.main", "-m", "zdl-lex-server.core"]

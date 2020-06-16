FROM openjdk:14-jdk-buster

ENV ZDL_LEX_USER "lex"
ENV ZDL_LEX_UID "1010"
ENV ZDL_LEX_GROUP "lex"
ENV ZDL_LEX_GID "1010"

ENV ZDL_LEX_DATA_DIR "/data"

ENV GIT_AUTHOR_NAME "ZDL-Lex"
ENV GIT_AUTHOR_EMAIL "noreply@lex.dwds.de"

ENV GIT_COMMITTER_NAME "ZDL-Lex"
ENV GIT_COMMITTER_EMAIL "noreply@lex.dwds.de"

ENV GIT_SSH_COMMAND "ssh -o StrictHostKeyChecking=no -i /data/.ssh/id_rsa"

VOLUME "/data"
WORKDIR "/data"

RUN set -ex; \
  groupadd -r --gid "$ZDL_LEX_GID" "$ZDL_LEX_GROUP"; \
  useradd -r --uid "$ZDL_LEX_UID" --gid "$ZDL_LEX_GID" \
    --home-dir /data "$ZDL_LEX_USER"

RUN set -ex; chown -R $ZDL_LEX_USER:$ZDL_LEX_GROUP /data

COPY --chown=lex:lex org.zdl.lex.server.jar /app/org.zdl.lex.server.jar

USER $ZDL_LEX_USER
ENTRYPOINT ["java", "-jar", "/app/org.zdl.lex.server.jar"]

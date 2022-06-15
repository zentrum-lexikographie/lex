FROM clojure:openjdk-16-tools-deps

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

RUN apt-get update && apt-get install -y \
    git \
    openssh-client \
    && rm -rf /var/lib/apt/lists/*

WORKDIR "/data"

RUN set -ex; \
  groupadd -r --gid "$ZDL_LEX_GID" "$ZDL_LEX_GROUP"; \
  useradd -r --uid "$ZDL_LEX_UID" --gid "$ZDL_LEX_GID" \
  --home-dir /data "$ZDL_LEX_USER"

RUN set -ex; chown -R $ZDL_LEX_USER:$ZDL_LEX_GROUP /data

RUN mkdir -p /app; chown lex:lex /app

USER $ZDL_LEX_USER
WORKDIR /app

COPY --chown=lex:lex deps.edn /app
RUN clojure -A:log:server -P

COPY --chown=lex:lex src /app/src
COPY --chown=lex:lex oxygen /app/oxygen

ENTRYPOINT ["clojure", "-X:log:server", "zdl.lex.server/start!"]


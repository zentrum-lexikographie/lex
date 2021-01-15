FROM openjdk:14-jdk-buster

ENV ZDL_LEX_DATA_DIR "/data"

ENV GIT_AUTHOR_NAME "ZDL-Lex"
ENV GIT_AUTHOR_EMAIL "noreply@lex.dwds.de"

ENV GIT_COMMITTER_NAME "ZDL-Lex"
ENV GIT_COMMITTER_EMAIL "noreply@lex.dwds.de"

ENV GIT_SSH_COMMAND "ssh -o StrictHostKeyChecking=no -i /data/.ssh/id_rsa"

VOLUME "/data"
WORKDIR "/data"

COPY org.zdl.lex.server.jar /app/org.zdl.lex.server.jar

ENTRYPOINT ["java", "-jar", "/app/org.zdl.lex.server.jar"]

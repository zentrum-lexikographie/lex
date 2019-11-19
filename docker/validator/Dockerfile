FROM openjdk:14-jdk-alpine
COPY org.zdl.lex.validator.jar /app/org.zdl.lex.validator.jar
ENTRYPOINT ["java", "-cp", "/app/org.zdl.lex.validator.jar", "clojure.main", "-m", "zdl-lex-validator.core"]

FROM openjdk:11

# This assumes you've run `lein uberjar` locally to produce the jar
COPY target/huge-feedback-0.1.0-SNAPSHOT-standalone.jar /

EXPOSE 80

CMD ["java", "-jar", "huge-feedback-0.1.0-SNAPSHOT-standalone.jar"]
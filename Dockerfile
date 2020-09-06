FROM openjdk:11

# This assumes you've run `lein uberjar` locally to produce the jar
COPY target/huge-feedback-0.2.0-standalone.jar /

EXPOSE 80

ENTRYPOINT ["java", "-jar", "huge-feedback-0.2.0-standalone.jar"]
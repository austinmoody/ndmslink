FROM openjdk:19-jdk-buster
RUN apt-get update && apt-get install -y netcat
COPY datastore/target/link-datastore.jar /opt/application/link-datastore.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=90.0", "-XX:InitialRAMPercentage=90.0", "-jar", "/opt/application/link-datastore.jar"]
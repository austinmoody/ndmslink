FROM openjdk:17-oracle
# docker build --no-cache --tag thsa-link-cli --file ./cli/Dockerfile .
COPY ../../cli/target/link-cli.jar /app/link-cli.jar
CMD ["java","-Dspring.shell.interactive.enabled=false","-Dspring.config.location=file:/cli/cli-config.yml","-jar","/app/link-cli.jar","generate-and-submit"]
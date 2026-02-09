FROM maven:3.9-eclipse-temurin-21

RUN git clone https://github.com/waabox/datadog-mcp-server.git /app
WORKDIR /app

RUN mvn clean package -DskipTests \
    && cp target/datadog-mcp-server-*.jar target/datadog-mcp-server.jar

ENTRYPOINT ["java", "--enable-preview", "-jar", "target/datadog-mcp-server.jar"]

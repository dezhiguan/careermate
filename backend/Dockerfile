FROM eclipse-temurin:17-jre
WORKDIR /app
ARG JAR_FILE=target/careermate-backend-0.1.0.jar
ENV JAVA_OPTS="-Xms512m -Xmx1g"
COPY ${JAR_FILE} app.jar
EXPOSE 18080
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]

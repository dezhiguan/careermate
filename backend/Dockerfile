ARG BASE_IMAGE=eclipse-temurin:21-jre-jammy
FROM ${BASE_IMAGE}
WORKDIR /app
ARG JAR_FILE=target/careermate-backend-0.1.0.jar
ENV JAVA_OPTS="-Xms512m -Xmx1g"
RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app
COPY --chown=app:app ${JAR_FILE} app.jar
EXPOSE 18080
USER app
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]

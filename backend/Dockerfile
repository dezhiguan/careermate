ARG BASE_IMAGE=eclipse-temurin:21-jre-jammy
FROM ${BASE_IMAGE}
WORKDIR /app
ARG JAR_FILE=target/careermate-backend-0.1.0.jar
ENV JAVA_OPTS="-Xms512m -Xmx1g"
# 安装 Noto CJK 字体，使简历 PDF 导出时中文字体被真正内嵌（candidateFontPaths 已含 Noto 路径），
# 修复无 CJK 字体镜像下回退到非内嵌 STSong 导致移动端阅读器字形替换错乱（如项目符号显示成“煉”）。
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app
COPY --chown=app:app ${JAR_FILE} app.jar
EXPOSE 18080
USER app
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]

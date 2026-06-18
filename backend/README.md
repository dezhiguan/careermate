# 本地启动前置

后端必须使用 JDK 21。`pom.xml` 已固定 `<java.version>21</java.version>`，本地启动前先切换到 JDK 21：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

推荐从仓库根目录使用统一脚本启动：

```bash
scripts/dev-start.sh
```

脚本会先执行 `mvn clean package -DskipTests`，再以 `dev` Profile 启动后端到 `8082` 端口，并仅在本地开发启动中启用 SMS Mock。

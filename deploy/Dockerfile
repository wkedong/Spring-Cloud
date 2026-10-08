# =============================================================================
# Spring-Cloud 教学仓库通用镜像（多阶段 + 分层拷贝）
#
# 用法（构建上下文必须是仓库根目录，因为要读多模块 Maven 工程）：
#   docker build -f deploy/Dockerfile --build-arg MODULE=service-producer \
#                --build-arg APP_PORT=6070 -t spring-cloud/service-producer:0.0.1 .
#
# 为什么 build context 是仓库根目录而不是 deploy/：多模块 Maven 工程在
# `-pl <module> -am` 时仍要能读到根 pom.xml 与全部子模块 pom.xml。
#
# ⚠️ 本机没有 Docker 环境，本文件未在本机实跑过（见 docs/29-容器化部署.md
#    「未能核实之处」）；但其中每个命令都在本机用 JDK 17 单独验证过，
#    尤其是 `-Djarmode=layertools` 的 list / extract 输出。
# =============================================================================

# -----------------------------------------------------------------------------
# 阶段 1：构建——用 Maven 镜像产出可执行 jar，并就地做分层提取
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

ARG MODULE=service-producer

WORKDIR /workspace

# 先整仓拷入：多模块工程缺任何一个子模块 pom 都会让 Maven 解析失败。
# 代价是「改一行代码」会失效这一层的缓存；想优化可以自己加 .dockerignore
# 排除 target/ 与 .git/，再把 COPY 拆成「先拷全部 pom → 再拷源码」两步。
COPY . .

# 只构建目标模块及其依赖（父 pom），不做 mvn clean，不碰其他模块。
# 本仓库 java.version=1.8、只用 javax.*，JDK 17 编译通过（实测 Build-Jdk-Spec: 17）。
RUN mvn -B -pl ${MODULE} -am -DskipTests package

# 分层提取：把 fat jar 拆成 4 个目录，供下一阶段逐个 COPY。
# ⚠️ 目标目录必须写成「空格分隔」的 --destination <dir>；
#    写成 --destination=<dir> 会报 Unknown option，而且**退出码仍是 0**，很难发现。
RUN java -Djarmode=layertools -jar ${MODULE}/target/*.jar extract --destination /workspace/extracted \
 && ls -1 /workspace/extracted

# -----------------------------------------------------------------------------
# 阶段 2：运行——只带 JRE，按层拷贝，非 root 运行
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre AS runtime

ARG MODULE=service-producer
ARG APP_PORT=8080

# JAVA_OPTS 由使用者在 run/compose 时传入覆盖（-e JAVA_OPTS=...）。
# MaxRAMPercentage：让 JVM 按容器内存限额算堆，而不是按宿主机物理内存——JDK 11+ 容器感知
# （UseContainerSupport）默认开启，这里显式把堆上限压到限额的 75%，给元空间/线程栈留余量。
ENV TZ=Asia/Shanghai \
    APP_PORT=${APP_PORT} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"

# curl 只服务于 HEALTHCHECK 与排障；tzdata 保证 TZ=Asia/Shanghai 生效。
# 若不想要这两个包，请同时去掉下面的 HEALTHCHECK（改用编排层探针）。
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && rm -rf /var/lib/apt/lists/*

# 非 root：固定 uid/gid，避免同一个卷在不同宿主机上属主漂移
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --create-home --home-dir /home/app app

WORKDIR /app

# 顺序即层顺序：最不容易变的放最前面，改业务代码时只有 application 层失效。
# 追加快照依赖时只有 snapshot-dependencies 层失效（本仓库当前该层为空）。
COPY --from=builder --chown=app:app /workspace/extracted/dependencies/          ./
COPY --from=builder --chown=app:app /workspace/extracted/spring-boot-loader/   ./
COPY --from=builder --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /workspace/extracted/application/          ./

USER app

# EXPOSE 只是文档性声明：它不发布端口，真正发布靠 `docker run -p` 或 compose 的 ports。
# 各模块的端口见 docs/09-本地运行指南.md 的端口表（如 producer 6070、gateway 6050）。
EXPOSE ${APP_PORT}

# 有 actuator 的模块才有 /actuator/health（health 默认就暴露）。
# 例外：config 模块没有引入 actuator，compose 里已单独换成配置中心自带的 REST 探针。
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${APP_PORT}/actuator/health" || exit 1

# 用 JarLauncher 从「解压后的分层目录」启动，而不是再打一个 fat jar。
# "$@" 让 `docker run image --server.port=6070` 这类参数能追加到 java 命令后面。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.JarLauncher \"$@\"", "--"]

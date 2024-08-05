FROM registry.cn-hangzhou.aliyuncs.com/sanbin/jdk17-springboot332:latest as base
WORKDIR /app
COPY . .
RUN mvn -s settings_default.xml -U -B -T 0.8C clean install -DskipTests

#分层
FROM registry.cn-hangzhou.aliyuncs.com/sanbin/openjdk17:bullseye-slim as builder
WORKDIR /app
COPY --from=base /app/target/application.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# 执行
FROM registry.cn-hangzhou.aliyuncs.com/sanbin/openjdk17:bullseye-slim
WORKDIR /app
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./
COPY --from=base /app/start.sh .

ENV TZ=Asia/Shanghai

RUN  mkdir -p /app/logs &&  \
    mkdir -p /app/logs/gc &&  \
    chmod 700 -R /app/logs

RUN  echo 'networkaddress.cache.ttl=60' >> /etc/java-17-openjdk/security/java.security \
    && chmod a+x *.sh \
    && mv start.sh /usr/bin

CMD ["start.sh"]



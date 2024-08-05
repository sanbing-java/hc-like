#!/bin/bash
#
# 抖音关注：程序员三丙
# 知识星球：https://t.zsxq.com/j9b21
#

#
# 抖音关注：程序员三丙
#


echo "Starting Server ..."

export JAVA_APP_OPTS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=10 -XX:MaxRAMPercentage=60 \
                                 -Xlog:gc*,heap*,age*,safepoint=debug:file=/app/logs/gc/gc.log:time,uptime,level,tags:filecount=10,filesize=10M \
                                 -XX:+IgnoreUnrecognizedVMOptions -XX:+HeapDumpOnOutOfMemoryError \
                                 -XX:HeapDumpPath=/var/log/gc/ \
                                 -XX:+UseTLAB -XX:+ResizeTLAB -XX:+PerfDisableSharedMem -XX:+UseCondCardMark \
                                 -XX:+UseG1GC -XX:MaxGCPauseMillis=500 -XX:+UseStringDeduplication -XX:+ParallelRefProcEnabled -XX:MaxTenuringThreshold=10 \
                                 -Xss512k -XX:NewRatio=2 -XX:ConcGCThreads=1 -XX:G1ReservePercent=20 \
                                 -XX:-OmitStackTraceInFastThrow \
                                 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m"

#export JAVA_OPTS_EXTEND="-Xdebug -Xrunjdwp:transport=dt_socket,address=0.0.0.0:8000,server=y,suspend=n"

exec java $JAVA_APP_OPTS $JAVA_OPTS_EXTEND -jar application.jar

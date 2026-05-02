#!/bin/bash
# ============================================
# 影视森林 - 服务部署脚本（需在 NAS 上执行）
# 位置: /volume1/docker/film-forest/deploy.sh
# 用法:
#   bash deploy.sh start   - 启动所有服务
#   bash deploy.sh stop    - 停止所有服务
#   bash deploy.sh restart - 重启所有服务
#   bash deploy.sh status  - 查看状态
# ============================================

set -e

BASE_DIR="/volume1/docker/film-forest"
JAVA_CMD="java"
CLIENT_JAR="$BASE_DIR/backend/film-forest-backend-new.jar"
ADMIN_JAR="$BASE_DIR/backend/film-forest-admin-0.0.1-SNAPSHOT-new.jar"

MYSQL_HOST="127.0.0.1"
MYSQL_DB="film_forest"
MYSQL_USER="root"
MYSQL_PWD="Root2026"

CLIENT_URL="jdbc:mysql://${MYSQL_HOST}:3306/${MYSQL_DB}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
ADMIN_URL="jdbc:mysql://${MYSQL_HOST}:3306/${MYSQL_DB}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"

do_start() {
    echo "=== 启动影视森林服务 ==="
    
    # 停止旧进程
    do_stop
    
    sleep 3
    
    # 启动 client-server
    echo "启动 client-server (8080)..."
    nohup $JAVA_CMD -jar "$CLIENT_JAR" \
        --spring.datasource.url="$CLIENT_URL" \
        --spring.datasource.username=$MYSQL_USER \
        --spring.datasource.password=$MYSQL_PWD \
        > /var/log/film-forest-client.log 2>&1 &
    echo "  PID: $!"
    
    # 启动 admin-server
    echo "启动 admin-server (8081)..."
    nohup $JAVA_CMD -jar "$ADMIN_JAR" \
        --server.port=8081 \
        --spring.datasource.url="$ADMIN_URL" \
        --spring.datasource.username=$MYSQL_USER \
        --spring.datasource.password=$MYSQL_PWD \
        > /var/log/film-forest-admin.log 2>&1 &
    echo "  PID: $!"
    
    sleep 5
    do_status
}

do_stop() {
    echo "=== 停止影视森林服务 ==="
    pkill -f "java.*film-forest-backend" && echo "  client-server 已停止" || echo "  client-server 未运行"
    pkill -f "java.*film-forest-admin" && echo "  admin-server 已停止" || echo "  admin-server 未运行"
    pkill -f "next.*300" && echo "  Next.js 已停止" || echo "  Next.js 未运行"
}

do_status() {
    echo "=== 服务状态 ==="
    C_PID=$(pgrep -f "java.*film-forest-backend" 2>/dev/null)
    A_PID=$(pgrep -f "java.*film-forest-admin" 2>/dev/null)
    N_PID=$(pgrep -f "next.*300" 2>/dev/null)
    
    [ -n "$C_PID" ] && echo "  client-server: 运行中 (PID $C_PID)" || echo "  client-server: 未运行"
    [ -n "$A_PID" ] && echo "  admin-server:  运行中 (PID $A_PID)" || echo "  admin-server:  未运行"
    [ -n "$N_PID" ] && echo "  Next.js:       运行中 (PID $N_PID)" || echo "  Next.js:       未运行"
    
    echo ""
    echo "  API 测试:"
    curl -sf http://localhost:8080/api/movies?page=1\&size=1 > /dev/null 2>&1 && echo "  client-server (8080): OK" || echo "  client-server (8080): 异常"
    curl -sf http://localhost:8081/api/crawler/status > /dev/null 2>&1 && echo "  admin-server (8081):  OK" || echo "  admin-server (8081):  异常"
    curl -sf http://localhost:3000/ > /dev/null 2>&1 && echo "  client-ui (3000):     OK" || echo "  client-ui (3000):     未运行"
    curl -sf http://localhost:3001/ > /dev/null 2>&1 && echo "  admin-ui (3001):      OK" || echo "  admin-ui (3001):      未运行"
}

case "$1" in
    start)  do_start ;;
    stop)   do_stop ;;
    restart) do_stop; sleep 3; do_start ;;
    status) do_status ;;
    *)
        echo "用法: bash deploy.sh {start|stop|restart|status}"
        exit 1
        ;;
esac
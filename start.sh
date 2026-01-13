#!/bin/bash

# ================================================
# 数据库双写验证工具 - 启动脚本
# ================================================

echo ""
echo "================================================"
echo "数据库双写验证工具"
echo "================================================"
echo ""

# 检查 Java 环境
echo "[1/3] 检查 Java 环境..."
if ! command -v java &> /dev/null; then
    echo "[错误] 未找到 Java 环境，请先安装 JDK 11 或更高版本"
    exit 1
fi
echo "[成功] Java 环境检查通过"
echo ""

# 检查 Maven
echo "[2/3] 检查 Maven..."
if ! command -v mvn &> /dev/null; then
    echo "[错误] 未找到 Maven，请先安装 Maven"
    exit 1
fi
echo "[成功] Maven 检查通过"
echo ""

# 构建项目
echo "[3/3] 构建项目..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "[错误] 项目构建失败"
    exit 1
fi
echo "[成功] 项目构建完成"
echo ""

# 启动应用
echo "================================================"
echo "启动应用..."
echo "================================================"
echo ""

# 设置环境变量（可选）
# export ORACLE_PASSWORD=your_oracle_password
# export POSTGRES_PASSWORD=your_postgres_password

# 指定环境配置文件（dev/prod）
SPRING_PROFILES_ACTIVE=dev

# 启动应用
java -jar target/db-validator-1.0.0.jar --spring.profiles.active=$SPRING_PROFILES_ACTIVE

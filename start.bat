@echo off
REM ================================================
REM 数据库双写验证工具 - 启动脚本
REM ================================================

echo.
echo ================================================
echo 数据库双写验证工具
echo ================================================
echo.

REM 检查 Java 环境
echo [1/3] 检查 Java 环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Java 环境，请先安装 JDK 11 或更高版本
    pause
    exit /b 1
)
echo [成功] Java 环境检查通过
echo.

REM 检查 Maven
echo [2/3] 检查 Maven...
mvn -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Maven，请先安装 Maven
    pause
    exit /b 1
)
echo [成功] Maven 检查通过
echo.

REM 构建项目
echo [3/3] 构建项目...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [错误] 项目构建失败
    pause
    exit /b 1
)
echo [成功] 项目构建完成
echo.

REM 启动应用
echo ================================================
echo 启动应用...
echo ================================================
echo.

REM 设置环境变量（可选）
REM set ORACLE_PASSWORD=your_oracle_password
REM set POSTGRES_PASSWORD=your_postgres_password

REM 指定环境配置文件（dev/prod）
set SPRING_PROFILES_ACTIVE=dev

REM 启动应用
java -jar target\db-validator-1.0.0.jar --spring.profiles.active=%SPRING_PROFILES_ACTIVE%

pause

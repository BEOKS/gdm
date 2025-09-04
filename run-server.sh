#!/bin/bash

# Gabia Dev MCP Server 실행 스크립트

echo "Gabia Dev MCP Server를 시작합니다..."

# JAR 파일 경로
JAR_FILE="app/build/libs/gabia-dev-mcp-server-1.0.0.jar"

# JAR 파일이 존재하는지 확인
if [ ! -f "$JAR_FILE" ]; then
    echo "JAR 파일을 찾을 수 없습니다. 먼저 빌드를 실행하세요:"
    echo "./gradlew shadowJar"
    exit 1
fi

# Java 버전 확인
echo "Java 버전 확인 중..."
java -version

echo ""
echo "서버를 시작합니다..."
echo "종료하려면 Ctrl+C를 누르세요."
echo ""

# 서버 실행
java -jar "$JAR_FILE"

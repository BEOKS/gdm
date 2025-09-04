#!/bin/bash

# Gabia Dev MCP Server 빌드 및 실행 스크립트

echo "Gabia Dev MCP Server 빌드 및 실행을 시작합니다..."

# 1. 프로젝트 빌드
echo "1. 프로젝트 빌드 중..."
./gradlew build

if [ $? -ne 0 ]; then
    echo "빌드 실패!"
    exit 1
fi

# 2. Fat JAR 생성
echo "2. Fat JAR 생성 중..."
./gradlew shadowJar

if [ $? -ne 0 ]; then
    echo "JAR 생성 실패!"
    exit 1
fi

# 3. 서버 실행
echo "3. 서버 실행 중..."
./run-server.sh

package com.gabia.devmcp

import com.gabia.devmcp.gitlab.addGitLabMergeRequestTools
import com.gabia.devmcp.gitlab.addGitLabIssueTools
import com.gabia.devmcp.confluence.addConfluenceTools
import com.gabia.devmcp.figma.addFigmaTools
import com.gabia.devmcp.mattermost.addMattermostTools
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.coroutines.*
import kotlinx.io.*

/**
 * Gabia Dev MCP Server
 * 
 * 이 서버는 MCP (Model Context Protocol)를 사용하여 다음 기능들을 제공합니다:
 * 1. GitLab MR 관리: GitLab API를 사용하여 MR 조회, 변경사항 조회, 토론 조회
 */

/**
 * MCP 서버 실행 함수
 */
suspend fun runMcpServer() {
    // MCP 서버 인스턴스 생성
    val server = Server(
        serverInfo = Implementation(
            name = "gabia-dev-mcp-server",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true)
            )
        )
    )

    // GitLab MR 관련 도구들 등록
    server.addGitLabMergeRequestTools()

    // GitLab Issue 관련 도구들 등록
    server.addGitLabIssueTools()

    // Confluence 관련 도구들 등록
    server.addConfluenceTools()

    // Figma 관련 도구들 등록
    server.addFigmaTools()
    
    // Mattermost 관련 도구들 등록
    server.addMattermostTools()

    // 표준 입출력을 통한 전송 설정
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )

    // 서버 연결 및 실행
    runBlocking {
        server.connect(transport)
        val done = Job()
        server.onClose {
            done.complete()
        }
        done.join()
    }
}

fun main() = runBlocking {
    println("Gabia Dev MCP Server 시작 중...")
    println("- GitLab MR 관리 API: GitLab")
    println("- Confluence 검색/페이지 API: Atlassian Confluence")
    println("- Figma 파일/이미지 API: Figma")
    runMcpServer()
}

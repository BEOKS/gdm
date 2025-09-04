package com.gabia.devmcp.gitlab

import com.gabia.devmcp.gitlab.mergerequest.MergeRequestTools
import com.gabia.devmcp.gitlab.issue.IssueTools
import io.modelcontextprotocol.kotlin.sdk.server.Server

/**
 * GitLab 도구들을 MCP 서버에 등록하는 진입점
 * 
 * 이 파일은 GitLab 관련 MCP 도구들을 서버에 등록하는 간단한 진입점 역할을 합니다.
 * 실제 구현은 각각의 전용 클래스에서 처리됩니다.
 */

/**
 * GitLab MR 관련 MCP 도구들을 서버에 등록하는 함수
 * 
 * 이 함수는 기존 코드와의 호환성을 위해 유지됩니다.
 */
fun Server.addGitLabMergeRequestTools() {
    val tools = MergeRequestTools()
    tools.addToServer(this)
}

/**
 * GitLab Issue 관련 MCP 도구들을 서버에 등록하는 함수
 */
fun Server.addGitLabIssueTools() {
    val tools = IssueTools()
    tools.addToServer(this)
}

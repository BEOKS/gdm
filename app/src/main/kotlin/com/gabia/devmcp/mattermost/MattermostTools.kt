package com.gabia.devmcp.mattermost

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.serialization.json.*

/**
 * Mattermost MCP 도구들
 * Mattermost API를 사용한 검색 및 조회 기능을 제공하는 MCP 도구들
 */
class MattermostTools {
    
    private val apiClient = MattermostApiClient()
    
    /**
     * Mattermost 관련 MCP 도구들을 서버에 등록하는 함수
     */
    fun addToServer(server: Server) {
        addSearchPostsTool(server)
        addSearchFilesTool(server)
        addGetTeamsTool(server)
        addGetChannelsTool(server)
        addGetUsersTool(server)
    }

    /**
     * 포스트 검색 도구
     */
    private fun addSearchPostsTool(server: Server) {
        server.addTool(
            name = "mattermost_search_posts",
            description = "Mattermost에서 포스트를 검색합니다",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("team_id") {
                        put("type", "string")
                        put("description", "검색할 팀 ID (선택사항)")
                    }
                    putJsonObject("terms") {
                        put("type", "string")
                        put("description", "검색할 키워드")
                    }
                    putJsonObject("is_or_search") {
                        put("type", "boolean")
                        put("description", "OR 검색 여부 (기본값: false)")
                    }
                    putJsonObject("page") {
                        put("type", "integer")
                        put("description", "페이지 번호 (기본값: 0)")
                    }
                    putJsonObject("per_page") {
                        put("type", "integer")
                        put("description", "페이지당 결과 수 (기본값: 20)")
                    }
                    putJsonObject("include_deleted_channels") {
                        put("type", "boolean")
                        put("description", "삭제된 채널 포함 여부 (기본값: false)")
                    }
                    putJsonObject("time_zone_offset") {
                        put("type", "integer")
                        put("description", "시간대 오프셋 (선택사항)")
                    }
                },
                required = listOf("terms")
            )
        ) { request ->
            val teamId = request.arguments["team_id"]?.jsonPrimitive?.contentOrNull
            val terms = request.arguments["terms"]?.jsonPrimitive?.content
            val isOrSearch = request.arguments["is_or_search"]?.jsonPrimitive?.booleanOrNull ?: false
            val page = request.arguments["page"]?.jsonPrimitive?.intOrNull ?: 0
            val perPage = request.arguments["per_page"]?.jsonPrimitive?.intOrNull ?: 20
            val includeDeletedChannels = request.arguments["include_deleted_channels"]?.jsonPrimitive?.booleanOrNull ?: false
            val timeZoneOffset = request.arguments["time_zone_offset"]?.jsonPrimitive?.intOrNull

            if (terms == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("terms 파라미터가 필요합니다.")),
                    isError = true
                )
            }

            return@addTool try {
                val result = apiClient.searchPosts(
                    teamId = teamId,
                    terms = terms,
                    isOrSearch = isOrSearch,
                    page = page,
                    perPage = perPage,
                    includeDeletedChannels = includeDeletedChannels,
                    timeZoneOffset = timeZoneOffset
                )
                CallToolResult(content = listOf(TextContent(Json.encodeToString(result))))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Mattermost 포스트 검색 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }

    /**
     * 파일 검색 도구
     */
    private fun addSearchFilesTool(server: Server) {
        server.addTool(
            name = "mattermost_search_files",
            description = "Mattermost에서 파일을 검색합니다",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("team_id") {
                        put("type", "string")
                        put("description", "검색할 팀 ID (선택사항)")
                    }
                    putJsonObject("terms") {
                        put("type", "string")
                        put("description", "검색할 키워드")
                    }
                    putJsonObject("is_or_search") {
                        put("type", "boolean")
                        put("description", "OR 검색 여부 (기본값: false)")
                    }
                    putJsonObject("page") {
                        put("type", "integer")
                        put("description", "페이지 번호 (기본값: 0)")
                    }
                    putJsonObject("per_page") {
                        put("type", "integer")
                        put("description", "페이지당 결과 수 (기본값: 20)")
                    }
                    putJsonObject("include_deleted_channels") {
                        put("type", "boolean")
                        put("description", "삭제된 채널 포함 여부 (기본값: false)")
                    }
                    putJsonObject("time_zone_offset") {
                        put("type", "integer")
                        put("description", "시간대 오프셋 (선택사항)")
                    }
                },
                required = listOf("terms")
            )
        ) { request ->
            val teamId = request.arguments["team_id"]?.jsonPrimitive?.contentOrNull
            val terms = request.arguments["terms"]?.jsonPrimitive?.content
            val isOrSearch = request.arguments["is_or_search"]?.jsonPrimitive?.booleanOrNull ?: false
            val page = request.arguments["page"]?.jsonPrimitive?.intOrNull ?: 0
            val perPage = request.arguments["per_page"]?.jsonPrimitive?.intOrNull ?: 20
            val includeDeletedChannels = request.arguments["include_deleted_channels"]?.jsonPrimitive?.booleanOrNull ?: false
            val timeZoneOffset = request.arguments["time_zone_offset"]?.jsonPrimitive?.intOrNull

            if (terms == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("terms 파라미터가 필요합니다.")),
                    isError = true
                )
            }

            return@addTool try {
                val result = apiClient.searchFiles(
                    teamId = teamId,
                    terms = terms,
                    isOrSearch = isOrSearch,
                    page = page,
                    perPage = perPage,
                    includeDeletedChannels = includeDeletedChannels,
                    timeZoneOffset = timeZoneOffset
                )
                CallToolResult(content = listOf(TextContent(Json.encodeToString(result))))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Mattermost 파일 검색 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }

    /**
     * 팀 목록 조회 도구
     */
    private fun addGetTeamsTool(server: Server) {
        server.addTool(
            name = "mattermost_get_teams",
            description = "Mattermost 팀 목록을 조회합니다",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("page") {
                        put("type", "integer")
                        put("description", "페이지 번호 (기본값: 0)")
                    }
                    putJsonObject("per_page") {
                        put("type", "integer")
                        put("description", "페이지당 결과 수 (기본값: 20)")
                    }
                }
            )
        ) { request ->
            val page = request.arguments["page"]?.jsonPrimitive?.intOrNull ?: 0
            val perPage = request.arguments["per_page"]?.jsonPrimitive?.intOrNull ?: 20

            return@addTool try {
                val result = apiClient.getTeams(page = page, perPage = perPage)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(result))))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Mattermost 팀 목록 조회 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }

    /**
     * 채널 목록 조회 도구
     */
    private fun addGetChannelsTool(server: Server) {
        server.addTool(
            name = "mattermost_get_channels",
            description = "특정 팀의 채널 목록을 조회합니다",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("team_id") {
                        put("type", "string")
                        put("description", "채널을 조회할 팀 ID")
                    }
                    putJsonObject("page") {
                        put("type", "integer")
                        put("description", "페이지 번호 (기본값: 0)")
                    }
                    putJsonObject("per_page") {
                        put("type", "integer")
                        put("description", "페이지당 결과 수 (기본값: 20)")
                    }
                },
                required = listOf("team_id")
            )
        ) { request ->
            val teamId = request.arguments["team_id"]?.jsonPrimitive?.content
            val page = request.arguments["page"]?.jsonPrimitive?.intOrNull ?: 0
            val perPage = request.arguments["per_page"]?.jsonPrimitive?.intOrNull ?: 20

            if (teamId == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("team_id 파라미터가 필요합니다.")),
                    isError = true
                )
            }

            return@addTool try {
                val result = apiClient.getChannels(teamId = teamId, page = page, perPage = perPage)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(result))))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Mattermost 채널 목록 조회 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }

    /**
     * 사용자 목록 조회 도구
     */
    private fun addGetUsersTool(server: Server) {
        server.addTool(
            name = "mattermost_get_users",
            description = "Mattermost 사용자 목록을 조회합니다",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("page") {
                        put("type", "integer")
                        put("description", "페이지 번호 (기본값: 0)")
                    }
                    putJsonObject("per_page") {
                        put("type", "integer")
                        put("description", "페이지당 결과 수 (기본값: 20)")
                    }
                }
            )
        ) { request ->
            val page = request.arguments["page"]?.jsonPrimitive?.intOrNull ?: 0
            val perPage = request.arguments["per_page"]?.jsonPrimitive?.intOrNull ?: 20

            return@addTool try {
                val result = apiClient.getUsers(page = page, perPage = perPage)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(result))))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Mattermost 사용자 목록 조회 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }
}

/**
 * Mattermost 도구들을 서버에 등록하는 확장 함수
 */
fun Server.addMattermostTools() {
    val mattermostTools = MattermostTools()
    mattermostTools.addToServer(this)
}
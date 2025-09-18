package com.gabia.devmcp.mattermost

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import java.net.URLEncoder

/**
 * Mattermost API 클라이언트
 * Mattermost API와의 통신을 담당하는 클라이언트 클래스
 */
class MattermostApiClient {
    
    // Mattermost API 설정
    private val apiUrl = System.getenv("MATTERMOST_API_URL") ?: "https://mattermost.gabia.com/api/v4"
    private val token = System.getenv("MATTERMOST_TOKEN")
    
    // Mattermost 전용 JSON 설정
    private val jsonConfig = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    // Mattermost 전용 HTTP 클라이언트
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }
    }
    
    /**
     * Mattermost 에러 처리 함수
     */
    private suspend fun handleMattermostError(response: io.ktor.client.statement.HttpResponse): Nothing {
        val errorBody = response.body<String>()
        throw Exception("Mattermost API error: ${response.status.value} ${response.status.description}\n$errorBody")
    }
    
    /**
     * 포스트 검색 함수
     * Search posts in Mattermost
     */
    suspend fun searchPosts(
        teamId: String? = null,
        terms: String,
        isOrSearch: Boolean = false,
        page: Int = 0,
        perPage: Int = 20,
        includeDeletedChannels: Boolean = false,
        timeZoneOffset: Int? = null
    ): MattermostSearchResponse {
        val route = if (teamId != null) {
            "$apiUrl/teams/$teamId/posts/search"
        } else {
            "$apiUrl/posts/search"
        }
        
        val params = buildJsonObject {
            put("terms", terms)
            put("is_or_search", isOrSearch)
            put("page", page)
            put("per_page", perPage)
            put("include_deleted_channels", includeDeletedChannels)
            timeZoneOffset?.let { put("time_zone_offset", it) }
        }
        
        val response = httpClient.post(route) {
            contentType(ContentType.Application.Json)
            setBody(params)
            token?.let { 
                header("Authorization", "Bearer $it")
            }
        }
        
        if (!response.status.isSuccess()) {
            handleMattermostError(response)
        }
        
        return response.body()
    }
    
    /**
     * 파일 검색 함수
     * Search files in Mattermost
     */
    suspend fun searchFiles(
        teamId: String? = null,
        terms: String,
        isOrSearch: Boolean = false,
        page: Int = 0,
        perPage: Int = 20,
        includeDeletedChannels: Boolean = false,
        timeZoneOffset: Int? = null
    ): MattermostFileSearchResponse {
        val route = if (teamId != null) {
            "$apiUrl/teams/$teamId/files/search"
        } else {
            "$apiUrl/files/search"
        }
        
        val params = buildJsonObject {
            put("terms", terms)
            put("is_or_search", isOrSearch)
            put("page", page)
            put("per_page", perPage)
            put("include_deleted_channels", includeDeletedChannels)
            timeZoneOffset?.let { put("time_zone_offset", it) }
        }
        
        val response = httpClient.post(route) {
            contentType(ContentType.Application.Json)
            setBody(params)
            token?.let { 
                header("Authorization", "Bearer $it")
            }
        }
        
        if (!response.status.isSuccess()) {
            handleMattermostError(response)
        }
        
        return response.body()
    }
    
    /**
     * 사용자가 멤버인 팀 목록 조회 함수
     * Get teams that the user is a member of
     */
    suspend fun getTeams(
        page: Int = 0,
        perPage: Int = 20
    ): List<MattermostTeam> {
        val response = httpClient.get("$apiUrl/users/me/teams") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
            }
            token?.let { 
                header("Authorization", "Bearer $it")
            }
        }
        
        if (!response.status.isSuccess()) {
            handleMattermostError(response)
        }
        
        return response.body()
    }
    
    /**
     * 채널 목록 조회 함수
     * Get channels for a team
     */
    suspend fun getChannels(
        teamId: String,
        page: Int = 0,
        perPage: Int = 20
    ): List<MattermostChannel> {
        val response = httpClient.get("$apiUrl/teams/$teamId/channels") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
            }
            token?.let { 
                header("Authorization", "Bearer $it")
            }
        }
        
        if (!response.status.isSuccess()) {
            handleMattermostError(response)
        }
        
        return response.body()
    }
    
    /**
     * 사용자 목록 조회 함수
     * Get users list
     */
    suspend fun getUsers(
        page: Int = 0,
        perPage: Int = 20
    ): List<MattermostUser> {
        val response = httpClient.get("$apiUrl/users") {
            url {
                parameters.append("page", page.toString())
                parameters.append("per_page", perPage.toString())
            }
            token?.let { 
                header("Authorization", "Bearer $it")
            }
        }
        
        if (!response.status.isSuccess()) {
            handleMattermostError(response)
        }
        
        return response.body()
    }
    
    /**
     * 리소스 정리
     */
    fun close() {
        httpClient.close()
    }
}

package com.gabia.devmcp.gitlab.mergerequest

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
 * GitLab Merge Request API 클라이언트
 * GitLab Merge Request API와의 통신을 담당하는 클라이언트 클래스
 */
class MergeRequestApiClient {
    
    // GitLab API 설정
    private val apiUrl = System.getenv("GITLAB_API_URL") ?: "https://gitlab.gabia.com/api/v4"
    private val token = System.getenv("GITLAB_TOKEN")
    
    // GitLab 전용 JSON 설정 (모든 JSON 파싱에 적용)
    private val jsonConfig = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    // GitLab 전용 HTTP 클라이언트
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }
    }
    
    /**
     * GitLab 에러 처리 함수
     */
    private suspend fun handleGitLabError(response: io.ktor.client.statement.HttpResponse): Nothing {
        val errorBody = response.body<String>()
        throw Exception("GitLab API error: ${response.status.value} ${response.status.description}\n$errorBody")
    }
    
    /**
     * 프로젝트 ID 처리 함수
     */
    private fun getEffectiveProjectId(projectId: String): String {
        return URLEncoder.encode(projectId, "UTF-8")
    }
    
    /**
     * 단일 MR 조회 함수
     * Get merge request details
     */
    suspend fun getMergeRequest(
        projectId: String,
        mergeRequestIid: String? = null,
        sourceBranch: String? = null
    ): MergeRequest {
        val decodedProjectId = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url: String
        
        if (mergeRequestIid != null) {
            url = "$apiUrl/projects/${getEffectiveProjectId(decodedProjectId)}/merge_requests/$mergeRequestIid"
        } else if (sourceBranch != null) {
            url = "$apiUrl/projects/${getEffectiveProjectId(decodedProjectId)}/merge_requests?source_branch=${URLEncoder.encode(sourceBranch, "UTF-8")}"
        } else {
            throw Exception("Either mergeRequestIid or sourceBranch must be provided")
        }
        
        val response = httpClient.get(url) {
            headers {
                append("Authorization", "Bearer $token")
                append("Content-Type", "application/json")
            }
        }
        
        if (!response.status.isSuccess()) {
            handleGitLabError(response)
        }
        
        val data = response.body<JsonElement>()
        
        // If response is an array (comes from branchName search), return the first item
        if (data is JsonArray && data.isNotEmpty()) {
            return jsonConfig.decodeFromJsonElement<MergeRequest>(data.first())
        }
        
        return jsonConfig.decodeFromJsonElement<MergeRequest>(data)
    }
    
    /**
     * MR 변경사항 조회 함수
     * Get merge request changes/diffs
     */
    suspend fun getMergeRequestDiffs(
        projectId: String,
        mergeRequestIid: String? = null,
        sourceBranch: String? = null,
        view: String? = null
    ): List<Diff> {
        val decodedProjectId = java.net.URLDecoder.decode(projectId, "UTF-8")
        
        if (mergeRequestIid == null && sourceBranch == null) {
            throw Exception("Either mergeRequestIid or sourceBranch must be provided")
        }
        
        val actualMergeRequestIid = if (mergeRequestIid != null) {
            mergeRequestIid
        } else {
            val mergeRequest = getMergeRequest(projectId, null, sourceBranch)
            mergeRequest.iid.toString()
        }
        
        val url = buildString {
            append("$apiUrl/projects/${getEffectiveProjectId(decodedProjectId)}/merge_requests/$actualMergeRequestIid/changes")
            if (view != null) {
                append("?view=$view")
            }
        }
        
        val response = httpClient.get(url) {
            headers {
                append("Authorization", "Bearer $token")
                append("Content-Type", "application/json")
            }
        }
        
        if (!response.status.isSuccess()) {
            handleGitLabError(response)
        }
        
        val data = response.body<ChangesResponse>()
        return data.changes
    }
    
    /**
     * MR 토론 조회 함수
     * List merge request discussion items
     */
    suspend fun listMergeRequestDiscussions(
        projectId: String,
        mergeRequestIid: String,
        page: Int? = null,
        perPage: Int? = null
    ): PaginatedDiscussionsResponse {
        val decodedProjectId = java.net.URLDecoder.decode(projectId, "UTF-8")
        
        val url = buildString {
            append("$apiUrl/projects/${getEffectiveProjectId(decodedProjectId)}/merge_requests/$mergeRequestIid/discussions")
            val params = mutableListOf<String>()
            if (page != null) params.add("page=$page")
            if (perPage != null) params.add("per_page=$perPage")
            if (params.isNotEmpty()) {
                append("?${params.joinToString("&")}")
            }
        }
        
        val response = httpClient.get(url) {
            headers {
                append("Authorization", "Bearer $token")
                append("Content-Type", "application/json")
            }
        }
        
        if (!response.status.isSuccess()) {
            handleGitLabError(response)
        }
        
        val discussions = response.body<List<Discussion>>()
        
        // Parse pagination info from headers
        val pagination = Pagination(
            page = response.headers["x-page"]?.toIntOrNull() ?: 1,
            per_page = response.headers["x-per-page"]?.toIntOrNull() ?: 20,
            total = response.headers["x-total"]?.toIntOrNull() ?: 0,
            total_pages = response.headers["x-total-pages"]?.toIntOrNull() ?: 0
        )
        
        return PaginatedDiscussionsResponse(
            items = discussions,
            pagination = pagination
        )
    }
    
    /**
     * MR 목록 검색 함수
     * List merge requests with filtering options
     */
    suspend fun listMergeRequests(
        projectId: String,
        options: ListMergeRequestsOptions = ListMergeRequestsOptions()
    ): PaginatedMergeRequestsResponse {
        val decodedProjectId = java.net.URLDecoder.decode(projectId, "UTF-8")
        
        val url = buildString {
            append("$apiUrl/projects/${getEffectiveProjectId(decodedProjectId)}/merge_requests")
            
            val params = mutableListOf<String>()
            
            // 모든 옵션 파라미터 추가
            options.assignee_id?.let { params.add("assignee_id=$it") }
            options.assignee_username?.let { params.add("assignee_username=${URLEncoder.encode(it, "UTF-8")}") }
            options.author_id?.let { params.add("author_id=$it") }
            options.author_username?.let { params.add("author_username=${URLEncoder.encode(it, "UTF-8")}") }
            options.reviewer_id?.let { params.add("reviewer_id=$it") }
            options.reviewer_username?.let { params.add("reviewer_username=${URLEncoder.encode(it, "UTF-8")}") }
            options.created_after?.let { params.add("created_after=${URLEncoder.encode(it, "UTF-8")}") }
            options.created_before?.let { params.add("created_before=${URLEncoder.encode(it, "UTF-8")}") }
            options.updated_after?.let { params.add("updated_after=${URLEncoder.encode(it, "UTF-8")}") }
            options.updated_before?.let { params.add("updated_before=${URLEncoder.encode(it, "UTF-8")}") }
            options.labels?.let { params.add("labels=${it.joinToString(",")}") }
            options.milestone?.let { params.add("milestone=${URLEncoder.encode(it, "UTF-8")}") }
            options.scope?.let { params.add("scope=$it") }
            options.search?.let { params.add("search=${URLEncoder.encode(it, "UTF-8")}") }
            options.state?.let { params.add("state=$it") }
            options.wip?.let { params.add("wip=$it") }
            options.with_merge_status_recheck?.let { params.add("with_merge_status_recheck=$it") }
            options.order_by?.let { params.add("order_by=$it") }
            options.sort?.let { params.add("sort=$it") }
            options.view?.let { params.add("view=$it") }
            options.my_reaction_emoji?.let { params.add("my_reaction_emoji=${URLEncoder.encode(it, "UTF-8")}") }
            options.source_branch?.let { params.add("source_branch=${URLEncoder.encode(it, "UTF-8")}") }
            options.target_branch?.let { params.add("target_branch=${URLEncoder.encode(it, "UTF-8")}") }
            options.page?.let { params.add("page=$it") }
            options.per_page?.let { params.add("per_page=$it") }
            
            // not 옵션 처리
            options.not?.let { notOptions ->
                notOptions.labels?.let { params.add("not[labels]=${it.joinToString(",")}") }
                notOptions.milestone?.let { params.add("not[milestone]=${URLEncoder.encode(it, "UTF-8")}") }
                notOptions.author_id?.let { params.add("not[author_id]=$it") }
                notOptions.author_username?.let { params.add("not[author_username]=${URLEncoder.encode(it, "UTF-8")}") }
                notOptions.assignee_id?.let { params.add("not[assignee_id]=$it") }
                notOptions.assignee_username?.let { params.add("not[assignee_username]=${URLEncoder.encode(it, "UTF-8")}") }
                notOptions.reviewer_id?.let { params.add("not[reviewer_id]=$it") }
                notOptions.reviewer_username?.let { params.add("not[reviewer_username]=${URLEncoder.encode(it, "UTF-8")}") }
                notOptions.my_reaction_emoji?.let { params.add("not[my_reaction_emoji]=${URLEncoder.encode(it, "UTF-8")}") }
            }
            
            if (params.isNotEmpty()) {
                append("?${params.joinToString("&")}")
            }
        }
        
        val response = httpClient.get(url) {
            headers {
                append("Authorization", "Bearer $token")
                append("Content-Type", "application/json")
            }
        }
        
        if (!response.status.isSuccess()) {
            handleGitLabError(response)
        }
        
        val mergeRequests = response.body<List<MergeRequest>>()
        
        // Parse pagination info from headers
        val pagination = Pagination(
            page = response.headers["x-page"]?.toIntOrNull() ?: 1,
            per_page = response.headers["x-per-page"]?.toIntOrNull() ?: 20,
            total = response.headers["x-total"]?.toIntOrNull() ?: 0,
            total_pages = response.headers["x-total-pages"]?.toIntOrNull() ?: 0
        )
        
        return PaginatedMergeRequestsResponse(
            items = mergeRequests,
            pagination = pagination
        )
    }

    /**
     * MR 생성 함수
     * Create a new merge request
     */
    suspend fun createMergeRequest(
        projectId: String,
        request: CreateMergeRequestRequest
    ): MergeRequest {
        val decodedProjectId = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url = "$apiUrl/projects/${getEffectiveProjectId(decodedProjectId)}/merge_requests"
        
        val response = httpClient.post(url) {
            headers {
                append("Authorization", "Bearer $token")
                append("Content-Type", "application/json")
            }
            setBody(request)
        }
        
        if (!response.status.isSuccess()) {
            handleGitLabError(response)
        }
        
        return response.body<MergeRequest>()
    }
    
    /**
     * HTTP 클라이언트 종료
     */
    fun close() {
        httpClient.close()
    }
}

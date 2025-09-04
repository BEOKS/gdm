package com.gabia.devmcp.gitlab.issue

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.net.URLEncoder

class IssueApiClient {
    private val apiUrl = System.getenv("GITLAB_API_URL") ?: "https://gitlab.gabia.com/api/v4"
    private val token = System.getenv("GITLAB_TOKEN")

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(jsonConfig) }
    }

    private fun getEffectiveProjectId(projectId: String): String = URLEncoder.encode(projectId, "UTF-8")

    private suspend fun handleError(response: io.ktor.client.statement.HttpResponse): Nothing {
        val body = response.body<String>()
        throw Exception("GitLab API error: ${response.status.value} ${response.status.description}\n$body")
    }

    suspend fun createIssue(projectId: String, request: CreateIssueRequest): Issue {
        val decoded = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url = "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues"
        val response = httpClient.post(url) {
            headers {
                append("Authorization", "Bearer $token")
                append("Content-Type", "application/json")
            }
            setBody(request)
        }
        if (!response.status.isSuccess()) handleError(response)
        return response.body()
    }

    suspend fun listIssues(projectId: String?, options: Map<String, Any?>): PaginatedIssues {
        val decoded = projectId?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        val base = if (decoded != null) "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues" else "$apiUrl/issues"
        val params = mutableListOf<String>()
        options.forEach { (k, v) ->
            if (v != null) {
                when (v) {
                    is List<*> -> v.filterNotNull().forEach { params.add("${k}[]=${URLEncoder.encode(it.toString(), "UTF-8")}") }
                    else -> params.add("$k=${URLEncoder.encode(v.toString(), "UTF-8")}")
                }
            }
        }
        val url = if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
        val response = httpClient.get(url) {
            headers {
                append("Authorization", "Bearer $token")
                append("Content-Type", "application/json")
            }
        }
        if (!response.status.isSuccess()) handleError(response)
        val items = response.body<List<Issue>>()
        val pagination = Pagination(
            page = response.headers["x-page"]?.toIntOrNull() ?: 1,
            per_page = response.headers["x-per-page"]?.toIntOrNull() ?: 20,
            total = response.headers["x-total"]?.toIntOrNull() ?: 0,
            total_pages = response.headers["x-total-pages"]?.toIntOrNull() ?: 0
        )
        return PaginatedIssues(items, pagination)
    }

    suspend fun getIssue(projectId: String, issueIid: String): Issue {
        val decoded = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url = "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues/$issueIid"
        val response = httpClient.get(url) { headers { append("Authorization", "Bearer $token"); append("Content-Type", "application/json") } }
        if (!response.status.isSuccess()) handleError(response)
        return response.body()
    }

    suspend fun updateIssue(projectId: String, issueIid: String, request: UpdateIssueRequest): Issue {
        val decoded = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url = "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues/$issueIid"
        val response = httpClient.put(url) {
            headers { append("Authorization", "Bearer $token"); append("Content-Type", "application/json") }
            setBody(request)
        }
        if (!response.status.isSuccess()) handleError(response)
        return response.body()
    }

    suspend fun deleteIssue(projectId: String, issueIid: String) {
        val decoded = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url = "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues/$issueIid"
        val response = httpClient.delete(url) { headers { append("Authorization", "Bearer $token") } }
        if (!response.status.isSuccess()) handleError(response)
    }

    suspend fun listIssueDiscussions(projectId: String, issueIid: String, page: Int?, perPage: Int?): PaginatedIssueDiscussions {
        val decoded = java.net.URLDecoder.decode(projectId, "UTF-8")
        val params = mutableListOf<String>()
        if (page != null) params.add("page=$page")
        if (perPage != null) params.add("per_page=$perPage")
        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val url = "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues/$issueIid/discussions$query"
        val response = httpClient.get(url) { headers { append("Authorization", "Bearer $token") } }
        if (!response.status.isSuccess()) handleError(response)
        val items = response.body<List<IssueDiscussion>>()
        val pagination = Pagination(
            page = response.headers["x-page"]?.toIntOrNull() ?: 1,
            per_page = response.headers["x-per-page"]?.toIntOrNull() ?: 20,
            total = response.headers["x-total"]?.toIntOrNull() ?: 0,
            total_pages = response.headers["x-total-pages"]?.toIntOrNull() ?: 0
        )
        return PaginatedIssueDiscussions(items, pagination)
    }

    suspend fun createIssueNote(projectId: String, issueIid: String, discussionId: String, body: String, createdAt: String?): IssueDiscussionNote {
        val decoded = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url = "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues/$issueIid/discussions/$discussionId/notes"
        val payload = mutableMapOf<String, Any>("body" to body).apply { if (createdAt != null) put("created_at", createdAt) }
        val response = httpClient.post(url) {
            headers { append("Authorization", "Bearer $token"); append("Content-Type", "application/json") }
            setBody(payload)
        }
        if (!response.status.isSuccess()) handleError(response)
        return response.body()
    }

    suspend fun updateIssueNote(projectId: String, issueIid: String, discussionId: String, noteId: String, body: String): IssueDiscussionNote {
        val decoded = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url = "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues/$issueIid/discussions/$discussionId/notes/$noteId"
        val response = httpClient.put(url) {
            headers { append("Authorization", "Bearer $token"); append("Content-Type", "application/json") }
            setBody(mapOf("body" to body))
        }
        if (!response.status.isSuccess()) handleError(response)
        return response.body()
    }

    suspend fun listIssueLinks(projectId: String, issueIid: String): List<IssueLink> {
        val decoded = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url = "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues/$issueIid/links"
        val response = httpClient.get(url) { headers { append("Authorization", "Bearer $token") } }
        if (!response.status.isSuccess()) handleError(response)
        return response.body()
    }

    suspend fun getIssueLink(projectId: String, issueIid: String, issueLinkId: String): IssueLink {
        val decoded = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url = "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues/$issueIid/links/$issueLinkId"
        val response = httpClient.get(url) { headers { append("Authorization", "Bearer $token") } }
        if (!response.status.isSuccess()) handleError(response)
        return response.body()
    }

    suspend fun createIssueLink(projectId: String, issueIid: String, targetProjectId: String, targetIssueIid: String, linkType: String?): IssueLink {
        val decoded = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url = "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues/$issueIid/links"
        val payload = mutableMapOf<String, Any>(
            "target_project_id" to targetProjectId,
            "target_issue_iid" to targetIssueIid
        ).apply { if (linkType != null) put("link_type", linkType) }
        val response = httpClient.post(url) {
            headers { append("Authorization", "Bearer $token"); append("Content-Type", "application/json") }
            setBody(payload)
        }
        if (!response.status.isSuccess()) handleError(response)
        return response.body()
    }

    suspend fun deleteIssueLink(projectId: String, issueIid: String, issueLinkId: String) {
        val decoded = java.net.URLDecoder.decode(projectId, "UTF-8")
        val url = "$apiUrl/projects/${getEffectiveProjectId(decoded)}/issues/$issueIid/links/$issueLinkId"
        val response = httpClient.delete(url) { headers { append("Authorization", "Bearer $token") } }
        if (!response.status.isSuccess()) handleError(response)
    }

    fun close() = httpClient.close()
}


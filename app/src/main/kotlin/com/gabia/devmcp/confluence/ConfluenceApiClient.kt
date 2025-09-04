package com.gabia.devmcp.confluence

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import java.util.Base64

/**
 * Confluence API 클라이언트
 */
class ConfluenceApiClient {
    private val baseUrl: String = (System.getenv("CONFLUENCE_BASE_URL") ?: "").trimEnd('/')
    private val bearer: String? = System.getenv("ATLASSIAN_OAUTH_ACCESS_TOKEN")
    private val basicUser: String? = System.getenv("CONFLUENCE_USERNAME") ?: System.getenv("ATLASSIAN_EMAIL")
    private val basicToken: String? = System.getenv("CONFLUENCE_API_TOKEN") ?: System.getenv("ATLASSIAN_API_TOKEN")

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }
        defaultRequest {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            val authHeader = buildAuthHeader()
            if (authHeader != null) header(HttpHeaders.Authorization, authHeader)
        }
    }

    private fun buildAuthHeader(): String? {
        return when {
            !bearer.isNullOrBlank() -> "Bearer $bearer"
            !basicUser.isNullOrBlank() && !basicToken.isNullOrBlank() -> {
                val token = Base64.getEncoder().encodeToString("$basicUser:$basicToken".toByteArray())
                "Basic $token"
            }
            else -> null
        }
    }

    suspend fun search(cql: String, limit: Int): JsonObject {
        val url = "$baseUrl/rest/api/search"
        val response = httpClient.get(url) {
            url {
                parameters.append("cql", cql)
                parameters.append("limit", limit.toString())
            }
        }
        if (!response.status.isSuccess()) {
            val errorBody = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Confluence API error: ${response.status.value} ${response.status.description}\n${errorBody ?: ""}")
        }
        return response.body()
    }

    suspend fun getPageById(pageId: String, expand: String?): JsonObject {
        val url = "$baseUrl/rest/api/content/$pageId"
        val response = httpClient.get(url) {
            if (!expand.isNullOrBlank()) {
                url { parameters.append("expand", expand) }
            }
        }
        if (!response.status.isSuccess()) {
            val errorBody = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Confluence API error: ${response.status.value} ${response.status.description}\n${errorBody ?: ""}")
        }
        return response.body()
    }

    suspend fun getPageByTitle(spaceKey: String, title: String, expand: String?): JsonObject {
        val url = "$baseUrl/rest/api/content"
        val response = httpClient.get(url) {
            url {
                parameters.append("spaceKey", spaceKey)
                parameters.append("title", title)
                if (!expand.isNullOrBlank()) parameters.append("expand", expand)
            }
        }
        if (!response.status.isSuccess()) {
            val errorBody = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Confluence API error: ${response.status.value} ${response.status.description}\n${errorBody ?: ""}")
        }
        val obj: JsonObject = response.body()
        val results = obj["results"] as? JsonArray ?: JsonArray(emptyList())
        if (results.isEmpty()) throw Exception("Page not found by title and space_key")
        return results.first().jsonObject
    }

    suspend fun getLabels(pageId: String): List<String> {
        val url = "$baseUrl/rest/api/content/$pageId/label"
        val response = httpClient.get(url)
        if (!response.status.isSuccess()) {
            val errorBody = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Confluence API error: ${response.status.value} ${response.status.description}\n${errorBody ?: ""}")
        }
        val obj: JsonObject = response.body()
        val results = obj["results"] as? JsonArray ?: JsonArray(emptyList())
        return results.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
    }

    suspend fun createPage(
        spaceKey: String,
        title: String,
        bodyValue: String,
        parentId: String? = null,
        representation: String = "storage"
    ): JsonObject {
        val url = "$baseUrl/rest/api/content"
        val payload = buildJsonObject {
            put("type", JsonPrimitive("page"))
            put("title", JsonPrimitive(title))
            putJsonObject("space") { put("key", JsonPrimitive(spaceKey)) }
            putJsonObject("body") {
                putJsonObject("storage") {
                    put("value", JsonPrimitive(bodyValue))
                    put("representation", JsonPrimitive(representation))
                }
            }
            if (!parentId.isNullOrBlank()) {
                putJsonArray("ancestors") {
                    add(buildJsonObject { put("id", JsonPrimitive(parentId)) })
                }
            }
        }
        val response = httpClient.post(url) { setBody(payload) }
        if (!response.status.isSuccess()) {
            val errorBody = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Confluence API error: ${response.status.value} ${response.status.description}\n${errorBody ?: ""}")
        }
        return response.body()
    }

    suspend fun updatePage(
        pageId: String,
        title: String,
        bodyValue: String,
        representation: String = "storage",
        minorEdit: Boolean = false,
        versionComment: String? = null,
        parentId: String? = null
    ): JsonObject {
        // Fetch current to get version and optionally ancestors
        val current = getPageById(pageId, expand = "version,ancestors")
        val currentVersion = current["version"]?.jsonObject?.get("number")?.jsonPrimitive?.intOrNull ?: 1
        val newVersion = currentVersion + 1

        val url = "$baseUrl/rest/api/content/$pageId"
        val payload = buildJsonObject {
            put("id", JsonPrimitive(pageId))
            put("type", JsonPrimitive("page"))
            put("title", JsonPrimitive(title))
            putJsonObject("body") {
                putJsonObject("storage") {
                    put("value", JsonPrimitive(bodyValue))
                    put("representation", JsonPrimitive(representation))
                }
            }
            putJsonObject("version") {
                put("number", JsonPrimitive(newVersion))
                put("minorEdit", JsonPrimitive(minorEdit))
                if (!versionComment.isNullOrBlank()) put("message", JsonPrimitive(versionComment))
            }
            if (!parentId.isNullOrBlank()) {
                putJsonArray("ancestors") {
                    add(buildJsonObject { put("id", JsonPrimitive(parentId)) })
                }
            }
        }
        val response = httpClient.put(url) { setBody(payload) }
        if (!response.status.isSuccess()) {
            val errorBody = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Confluence API error: ${response.status.value} ${response.status.description}\n${errorBody ?: ""}")
        }
        return response.body()
    }

    suspend fun deletePage(pageId: String): Boolean {
        val url = "$baseUrl/rest/api/content/$pageId"
        val response = httpClient.delete(url)
        if (!response.status.isSuccess()) {
            val errorBody = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Confluence API error: ${response.status.value} ${response.status.description}\n${errorBody ?: ""}")
        }
        return true
    }

    suspend fun addComment(pageId: String, bodyValue: String, representation: String = "storage"): JsonObject {
        // Create a comment as content, attached to the page as container
        val url = "$baseUrl/rest/api/content"
        val payload = buildJsonObject {
            put("type", JsonPrimitive("comment"))
            putJsonObject("container") {
                put("type", JsonPrimitive("page"))
                put("id", JsonPrimitive(pageId))
            }
            putJsonObject("body") {
                putJsonObject("storage") {
                    put("value", JsonPrimitive(bodyValue))
                    put("representation", JsonPrimitive(representation))
                }
            }
        }
        val response = httpClient.post(url) { setBody(payload) }
        if (!response.status.isSuccess()) {
            val errorBody = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Confluence API error: ${response.status.value} ${response.status.description}\n${errorBody ?: ""}")
        }
        return response.body()
    }

    fun close() {
        httpClient.close()
    }
}

/**
 * 결과를 단순화하는 헬퍼 (Python 예시 포맷 참고)
 */
object ConfluenceResultFormatter {
    fun toSimpleResults(payload: JsonObject, baseUrl: String?): JsonArray {
        val results = payload["results"] as? JsonArray ?: JsonArray(emptyList())
        val simplified = results.map { itemEl ->
            val item = itemEl.jsonObject
            val title = item["title"]?.jsonPrimitive?.contentOrNull
                ?: item["content"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
            val contentId = item["id"]?.jsonPrimitive?.contentOrNull
                ?: item["content"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                ?: item["content"]?.jsonObject?.get("_id")?.jsonPrimitive?.contentOrNull
            val excerpt = item["excerpt"]?.jsonPrimitive?.contentOrNull
                ?: item["content"]?.jsonObject?.get("excerpt")?.jsonPrimitive?.contentOrNull
            val spaceKey = item["space"]?.jsonObject?.get("key")?.jsonPrimitive?.contentOrNull
                ?: item["content"]?.jsonObject?.get("space")?.jsonObject?.get("key")?.jsonPrimitive?.contentOrNull

            val url = if (!baseUrl.isNullOrBlank() && !contentId.isNullOrBlank() && !spaceKey.isNullOrBlank()) {
                "$baseUrl/spaces/$spaceKey/pages/$contentId"
            } else null

            buildJsonObject {
                put("id", contentId?.let { JsonPrimitive(it) } ?: JsonNull)
                put("title", title?.let { JsonPrimitive(it) } ?: JsonNull)
                put("spaceKey", spaceKey?.let { JsonPrimitive(it) } ?: JsonNull)
                put("url", url?.let { JsonPrimitive(it) } ?: JsonNull)
                put("excerpt", excerpt?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }
        return JsonArray(simplified)
    }
}

object ConfluenceQueryHelper {
    fun wrapSimpleQueryToCql(query: String): String {
        val isCql = listOf("=", "~", ">", "<", " AND ", " OR ", "currentUser()").any { query.contains(it) }
        if (isCql) return query
        val term = query.replace("\"", "\\\"")
        return "siteSearch ~ \"$term\""
    }

    fun applySpacesFilter(cql: String, spacesFilter: String?): String {
        if (spacesFilter.isNullOrBlank()) return cql
        val keys = spacesFilter.split(',').mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
        if (keys.isEmpty()) return cql
        val keyClause = keys.joinToString(" OR ") { "space = \"$it\"" }
        return "($keyClause) AND ($cql)"
    }
}

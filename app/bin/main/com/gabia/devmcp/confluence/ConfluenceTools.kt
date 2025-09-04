package com.gabia.devmcp.confluence

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.serialization.json.*

/**
 * Confluence 관련 MCP 도구 등록 클래스
 */
class ConfluenceTools {
    private val apiClient = ConfluenceApiClient()

    fun addToServer(server: Server) {
        addConfluenceSearchTool(server)
        addConfluenceGetPageTool(server)
        addConfluenceCreatePageTool(server)
        addConfluenceUpdatePageTool(server)
        addConfluenceDeletePageTool(server)
        addConfluenceAddCommentTool(server)
    }

    private fun addConfluenceSearchTool(server: Server) {
        server.addTool(
            name = "confluence_search",
            description = "Search Atlassian Confluence with simple text or CQL; returns simplified JSON results.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", """
                        Search query supporting both simple text and CQL (Confluence Query Language).
                        
                        Simple text: Basic keyword search (e.g., "deployment guide")
                        
                        CQL syntax: Structured queries using fields, operators, and keywords
                        - Fields: space, title, text, type, creator, label, created, lastmodified, parent, ancestor
                        - Operators: = != ~ !~ > >= < <= IN NOT IN (~ for text search, = for exact match)
                        - Keywords: AND OR NOT ORDER BY
                        - Functions: currentUser() startOfDay() endOfMonth() now("-4w")
                        
                        Examples:
                        - space = "DEV" AND type = page
                        - title ~ "API*" AND created >= startOfMonth()
                        - label IN (important, critical) OR creator = currentUser()
                        - text ~ "kubernetes" AND lastmodified > now("-2w")
                        - ancestor = 12345 AND type != attachment
                        
                        Use parentheses for complex queries: (space = DEV AND type = page) OR (label = urgent)
                        Text fields (title, text) support fuzzy matching with ~
                    """.trimIndent())
                    }
                    putJsonObject("limit") {
                        put("type", "number")
                        put("description", "Maximum number of results (1-50)")
                    }
                    putJsonObject("spaces_filter") {
                        put("type", "string")
                        put("description", "Comma-separated space keys; overrides CONFLUENCE_SPACES_FILTER. Use empty to disable.")
                    }
                },
                required = listOf("query")
            )
        ) { request ->
            val query = request.arguments["query"]?.jsonPrimitive?.content
            val limit = request.arguments["limit"]?.jsonPrimitive?.intOrNull ?: 10
            val spacesFilterArg = request.arguments["spaces_filter"]?.jsonPrimitive?.contentOrNull

            if (query == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("query 파라미터가 필요합니다.")),
                    isError = true
                )
            }

            val envSpaces = System.getenv("CONFLUENCE_SPACES_FILTER")
            val effectiveSpaces = when (spacesFilterArg) {
                null -> envSpaces
                "" -> null
                else -> spacesFilterArg
            }

            val cql = ConfluenceQueryHelper.applySpacesFilter(
                ConfluenceQueryHelper.wrapSimpleQueryToCql(query),
                effectiveSpaces
            )

            return@addTool try {
                val payload = apiClient.search(cql = cql, limit = limit.coerceIn(1, 50))
                val simplified = ConfluenceResultFormatter.toSimpleResults(payload, System.getenv("CONFLUENCE_BASE_URL")?.trimEnd('/'))
                CallToolResult(content = listOf(TextContent(Json.encodeToString(simplified))))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Confluence 검색 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }

    fun close() {
        apiClient.close()
    }

    private fun addConfluenceGetPageTool(server: Server) {
        server.addTool(
            name = "confluence_get_page",
            description = "Fetch a Confluence page by page_id or (title + space_key) and return a detailed JSON.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("page_id") {
                        put("type", "string")
                        put("description", "Confluence page ID (numeric, from URL). If provided, 'title' and 'space_key' are ignored.")
                    }
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Exact page title. Requires 'space_key' when 'page_id' is not provided.")
                    }
                    putJsonObject("space_key") {
                        put("type", "string")
                        put("description", "Space key (e.g., 'ENG', 'TEAM') when using 'title' lookup.")
                    }
                    putJsonObject("include_metadata") {
                        put("type", "boolean")
                        put("description", "Include metadata like version, labels, timestamps.")
                        put("default", true)
                    }
                    putJsonObject("convert_to_markdown") {
                        put("type", "boolean")
                        put("description", "Convert page to Markdown (true) or return HTML (false).")
                        put("default", true)
                    }
                },
                required = emptyList()
            )
        ) { request ->
            val pageId = request.arguments["page_id"]?.jsonPrimitive?.contentOrNull
            val title = request.arguments["title"]?.jsonPrimitive?.contentOrNull
            val spaceKey = request.arguments["space_key"]?.jsonPrimitive?.contentOrNull
            val includeMetadata = request.arguments["include_metadata"]?.jsonPrimitive?.booleanOrNull ?: true
            val convertToMarkdown = request.arguments["convert_to_markdown"]?.jsonPrimitive?.booleanOrNull ?: true

            if (pageId.isNullOrBlank() && (title.isNullOrBlank() || spaceKey.isNullOrBlank())) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("'page_id' 또는 ('title' + 'space_key')를 제공해야 합니다.")),
                    isError = true
                )
            }

            val expandWithMeta = "body.export_view,body.storage,version,space,history"
            val expandNoMeta = "body.export_view,body.storage"

            return@addTool try {
                val pageObj = if (!pageId.isNullOrBlank()) {
                    apiClient.getPageById(pageId, if (includeMetadata) expandWithMeta else expandNoMeta)
                } else {
                    apiClient.getPageByTitle(spaceKey!!, title!!, if (includeMetadata) expandWithMeta else expandNoMeta)
                }

                val labels: List<String> = if (includeMetadata) {
                    val pid = pageObj["id"]?.jsonPrimitive?.contentOrNull
                    if (!pid.isNullOrBlank()) runCatching { apiClient.getLabels(pid) }.getOrDefault(emptyList()) else emptyList()
                } else emptyList()

                val simplified = simplifyPage(pageObj, labels, convertToMarkdown)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(simplified))))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Confluence 페이지 조회 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }

    private fun addConfluenceCreatePageTool(server: Server) {
        server.addTool(
            name = "confluence_create_page",
            description = "Create a new Confluence page in a space; supports markdown/wiki/storage body.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("space") {
                        put("type", "string")
                        put("description", "Space key where the page will be created.")
                    }
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Page title.")
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "Page content in the given format.")
                    }
                    putJsonObject("parent_id") {
                        put("type", "string")
                        put("description", "Optional parent page ID to nest under.")
                    }
                    putJsonObject("format") {
                        put("type", "string")
                        put("description", "Body format: markdown|wiki|storage")
                        put("default", "markdown")
                    }
                },
                required = listOf("space", "title", "content")
            )
        ) { request ->
            val space = request.arguments["space"]?.jsonPrimitive?.content
            val title = request.arguments["title"]?.jsonPrimitive?.content
            val content = request.arguments["content"]?.jsonPrimitive?.content
            val parentId = request.arguments["parent_id"]?.jsonPrimitive?.contentOrNull
            val format = request.arguments["format"]?.jsonPrimitive?.contentOrNull ?: "markdown"

            if (space == null || title == null || content == null) {
                return@addTool CallToolResult(content = listOf(TextContent("space, title, content가 필요합니다.")), isError = true)
            }

            val (body, repr) = normalizeBody(content, format)

            return@addTool try {
                val created = apiClient.createPage(spaceKey = space, title = title, bodyValue = body, parentId = parentId, representation = repr)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(created))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Confluence 페이지 생성 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addConfluenceUpdatePageTool(server: Server) {
        server.addTool(
            name = "confluence_update_page",
            description = "Update an existing Confluence page by ID; supports markdown/wiki/storage body.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("page_id") { put("type", "string"); put("description", "Target page ID.") }
                    putJsonObject("title") { put("type", "string"); put("description", "New title.") }
                    putJsonObject("content") { put("type", "string"); put("description", "New content.") }
                    putJsonObject("minor_edit") { put("type", "boolean"); put("description", "Mark as minor edit."); put("default", false) }
                    putJsonObject("version_comment") { put("type", "string"); put("description", "Version comment.") }
                    putJsonObject("parent_id") { put("type", "string"); put("description", "Optional new parent ID.") }
                    putJsonObject("format") { put("type", "string"); put("description", "Body format: markdown|wiki|storage"); put("default", "markdown") }
                },
                required = listOf("page_id", "title", "content")
            )
        ) { request ->
            val pageId = request.arguments["page_id"]?.jsonPrimitive?.content
            val title = request.arguments["title"]?.jsonPrimitive?.content
            val content = request.arguments["content"]?.jsonPrimitive?.content
            val minor = request.arguments["minor_edit"]?.jsonPrimitive?.booleanOrNull ?: false
            val comment = request.arguments["version_comment"]?.jsonPrimitive?.contentOrNull
            val parentId = request.arguments["parent_id"]?.jsonPrimitive?.contentOrNull
            val format = request.arguments["format"]?.jsonPrimitive?.contentOrNull ?: "markdown"

            if (pageId == null || title == null || content == null) {
                return@addTool CallToolResult(content = listOf(TextContent("page_id, title, content가 필요합니다.")), isError = true)
            }

            val (body, repr) = normalizeBody(content, format)

            return@addTool try {
                val updated = apiClient.updatePage(pageId = pageId, title = title, bodyValue = body, representation = repr, minorEdit = minor, versionComment = comment, parentId = parentId)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(updated))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Confluence 페이지 수정 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addConfluenceDeletePageTool(server: Server) {
        server.addTool(
            name = "confluence_delete_page",
            description = "Delete a Confluence page by ID.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("page_id") { put("type", "string"); put("description", "Page ID to delete.") }
                },
                required = listOf("page_id")
            )
        ) { request ->
            val pageId = request.arguments["page_id"]?.jsonPrimitive?.content
            if (pageId == null) return@addTool CallToolResult(content = listOf(TextContent("page_id가 필요합니다.")), isError = true)
            return@addTool try {
                apiClient.deletePage(pageId)
                CallToolResult(content = listOf(TextContent("{\"success\": true, \"page_id\": \"$pageId\"}")))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Confluence 페이지 삭제 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addConfluenceAddCommentTool(server: Server) {
        server.addTool(
            name = "confluence_add_comment",
            description = "Add a comment to a Confluence page by ID.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("page_id") { put("type", "string"); put("description", "Target page ID.") }
                    putJsonObject("content") { put("type", "string"); put("description", "Comment body.") }
                    putJsonObject("format") { put("type", "string"); put("description", "Body format: markdown|wiki|storage"); put("default", "markdown") }
                },
                required = listOf("page_id", "content")
            )
        ) { request ->
            val pageId = request.arguments["page_id"]?.jsonPrimitive?.content
            val content = request.arguments["content"]?.jsonPrimitive?.content
            val format = request.arguments["format"]?.jsonPrimitive?.contentOrNull ?: "markdown"
            if (pageId == null || content == null) return@addTool CallToolResult(content = listOf(TextContent("page_id, content가 필요합니다.")), isError = true)
            val (body, repr) = normalizeBody(content, format)
            return@addTool try {
                val created = apiClient.addComment(pageId, body, repr)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(created))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Confluence 댓글 추가 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun normalizeBody(content: String, format: String): Pair<String, String> {
        val fmt = format.lowercase()
        return when (fmt) {
            "storage" -> content to "storage"
            "wiki" -> content to "wiki"
            else -> markdownToStorage(content) to "storage"
        }
    }

    private fun markdownToStorage(markdown: String): String {
        // Lightweight Markdown -> HTML converter sufficient for Confluence storage rendering
        // Handles: headings, paragraphs, unordered/ordered lists, code blocks, inline code,
        // bold/italic, links, blockquotes, and simple tables.

        fun escapeHtml(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        fun renderInline(raw: String): String {
            var s = escapeHtml(raw)
            // Links [text](url)
            s = s.replace(Regex("\\[([^]]+)\\]\\(([^)]+)\\)")) { m ->
                val text = m.groupValues[1]
                val url = m.groupValues[2]
                "<a href=\"${escapeHtml(url)}\">${text}</a>"
            }
            // Inline code `code`
            s = s.replace(Regex("`([^`]+)`")) { m -> "<code>${m.groupValues[1]}</code>" }
            // Bold **text** or __text__
            s = s.replace(Regex("\\*\\*([^*]+)\\*\\*")) { m -> "<strong>${m.groupValues[1]}</strong>" }
            s = s.replace(Regex("__([^_]+)__")) { m -> "<strong>${m.groupValues[1]}</strong>" }
            // Italic *text* or _text_
            s = s.replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")) { m -> "<em>${m.groupValues[1]}</em>" }
            s = s.replace(Regex("(?<!_)_([^_]+)_(?!_ )")) { m -> "<em>${m.groupValues[1]}</em>" }
            return s
        }

        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = StringBuilder()
        var i = 0

        var inUl = false
        var inOl = false
        var inPara = false
        var inQuote = false
        var inCode = false
        var codeLang: String? = null

        fun closePara() { if (inPara) { out.append("</p>\n"); inPara = false } }
        fun closeLists() {
            if (inUl) { out.append("</ul>\n"); inUl = false }
            if (inOl) { out.append("</ol>\n"); inOl = false }
        }
        fun closeQuote() { if (inQuote) { out.append("</blockquote>\n"); inQuote = false } }

        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()

            // Code fences
            if (!inCode && line.matches(Regex("^```.*$"))) {
                closePara(); closeLists(); closeQuote()
                inCode = true
                codeLang = line.removePrefix("```").trim().ifBlank { null }
                out.append("<pre><code>")
                i++
                continue
            }
            if (inCode) {
                if (line == "```") {
                    out.append("</code></pre>\n")
                    inCode = false
                    codeLang = null
                } else {
                    out.append(escapeHtml(raw)).append('\n')
                }
                i++
                continue
            }

            // Blank line -> close blocks
            if (line.isBlank()) {
                closePara(); closeLists(); closeQuote()
                i++
                continue
            }

            // Table detection: header + separator
            fun isSeparatorRow(s: String): Boolean = s.trim().matches(Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$"))
            if (line.contains('|') && i + 1 < lines.size && isSeparatorRow(lines[i + 1])) {
                closePara(); closeLists(); closeQuote()
                // Parse header
                fun parseCells(s: String): List<String> = s.trim().trim('|').split('|').map { it.trim() }
                val header = parseCells(line)
                i += 2 // skip separator
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains('|') && !lines[i].trim().startsWith("#")) {
                    rows.add(parseCells(lines[i]))
                    i++
                }
                out.append("<table>\n<thead><tr>")
                header.forEach { out.append("<th>").append(renderInline(it)).append("</th>") }
                out.append("</tr></thead>\n<tbody>\n")
                rows.forEach { r ->
                    out.append("<tr>")
                    r.forEach { c -> out.append("<td>").append(renderInline(c)).append("</td>") }
                    out.append("</tr>\n")
                }
                out.append("</tbody>\n</table>\n")
                continue
            }

            // Horizontal rule
            if (line.matches(Regex("^\\s*(\\*{3,}|-{3,}|_{3,})\\s*$"))) {
                closePara(); closeLists(); closeQuote()
                out.append("<hr/>\n")
                i++
                continue
            }

            // Headings
            val h = Regex("^(#{1,6})\\s+(.*)$").find(line)
            if (h != null) {
                closePara(); closeLists(); closeQuote()
                val level = h.groupValues[1].length
                val text = h.groupValues[2]
                out.append("<h$level>").append(renderInline(text)).append("</h$level>\n")
                i++
                continue
            }

            // Blockquote
            if (line.startsWith(">")) {
                if (!inQuote) { closePara(); closeLists(); out.append("<blockquote>\n"); inQuote = true }
                val quoteText = line.removePrefix(">").trimStart()
                if (!inPara) { out.append("<p>"); inPara = true }
                out.append(renderInline(quoteText)).append(' ')
                i++
                continue
            } else {
                closeQuote()
            }

            // Lists
            val ulMatch = Regex("^[-*]\\s+(.+)$").find(line)
            val olMatch = Regex("^\\d+\\.\\s+(.+)$").find(line)
            if (ulMatch != null) {
                if (!inUl) { closePara(); if (inOl) { out.append("</ol>\n"); inOl = false }; out.append("<ul>\n"); inUl = true }
                out.append("<li>").append(renderInline(ulMatch.groupValues[1])).append("</li>\n")
                i++
                continue
            }
            if (olMatch != null) {
                if (!inOl) { closePara(); if (inUl) { out.append("</ul>\n"); inUl = false }; out.append("<ol>\n"); inOl = true }
                out.append("<li>").append(renderInline(olMatch.groupValues[1])).append("</li>\n")
                i++
                continue
            }

            // Paragraph text
            if (!inPara) { out.append("<p>"); inPara = true }
            out.append(renderInline(line)).append(' ')
            i++
        }

        // Close any open blocks
        closePara(); closeLists(); closeQuote()
        return out.toString().trim()
    }

    private fun extractBody(content: JsonObject, preferHtml: Boolean): Pair<String, String> {
        val body = content["body"]?.jsonObject ?: buildJsonObject { }
        val exportView = body["export_view"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
        val storage = body["storage"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
        val html = exportView ?: storage ?: ""
        return if (preferHtml) "html" to html else "markdown" to htmlToMarkdownLight(html)
    }

    private fun buildPageUrl(baseUrl: String?, spaceKey: String?, pageId: String?): String? {
        if (baseUrl.isNullOrBlank() || spaceKey.isNullOrBlank() || pageId.isNullOrBlank()) return null
        return "$baseUrl/spaces/$spaceKey/pages/$pageId"
    }

    private fun simplifyPage(content: JsonObject, labels: List<String>, convertToMarkdown: Boolean): JsonObject {
        val pageId = content["id"]?.jsonPrimitive?.contentOrNull
        val title = content["title"]?.jsonPrimitive?.contentOrNull
            ?: content["title"]?.jsonPrimitive?.contentOrNull
        val spaceKey = content["space"]?.jsonObject?.get("key")?.jsonPrimitive?.contentOrNull
        val (fmt, bodyText) = extractBody(content, preferHtml = !convertToMarkdown)
        val url = buildPageUrl(System.getenv("CONFLUENCE_BASE_URL")?.trimEnd('/'), spaceKey, pageId)
        val versionObj = content["version"]?.jsonObject
        val historyObj = content["history"]?.jsonObject
        val createdAt = historyObj?.get("createdDate")?.jsonPrimitive?.contentOrNull
        val lastUpdatedAt = historyObj?.get("lastUpdated")?.jsonObject?.get("when")?.jsonPrimitive?.contentOrNull

        return buildJsonObject {
            put("id", pageId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("title", title?.let { JsonPrimitive(it) } ?: JsonNull)
            put("spaceKey", spaceKey?.let { JsonPrimitive(it) } ?: JsonNull)
            put("url", url?.let { JsonPrimitive(it) } ?: JsonNull)
            put("format", JsonPrimitive(fmt))
            put("body", JsonPrimitive(bodyText))
            putJsonObject("version") {
                put("number", versionObj?.get("number")?.jsonPrimitive ?: JsonNull)
                put("when", versionObj?.get("when")?.jsonPrimitive ?: JsonNull)
                val by = versionObj?.get("by")?.jsonObject?.get("displayName")?.jsonPrimitive
                put("by", by ?: JsonNull)
            }
            put("labels", JsonArray(labels.map { JsonPrimitive(it) }))
            put("createdAt", createdAt?.let { JsonPrimitive(it) } ?: JsonNull)
            put("lastUpdatedAt", lastUpdatedAt?.let { JsonPrimitive(it) } ?: JsonNull)
        }
    }

    private fun htmlToMarkdownLight(html: String): String {
        var text = html
        text = text.replace(Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "# $1\n\n")
        text = text.replace(Regex("<h2[^>]*>(.*?)</h2>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "## $1\n\n")
        text = text.replace(Regex("<h3[^>]*>(.*?)</h3>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "### $1\n\n")
        text = text.replace(Regex("<p[^>]*>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "$1\n\n")
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<strong[^>]*>(.*?)</strong>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "**$1**")
        text = text.replace(Regex("<b[^>]*>(.*?)</b>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "**$1**")
        text = text.replace(Regex("<em[^>]*>(.*?)</em>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "*$1*")
        text = text.replace(Regex("<i[^>]*>(.*?)</i>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "*$1*")
        text = text.replace(Regex("<a [^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "[$2]($1)")
        text = text.replace(Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "- $1\n")
        text = text.replace(Regex("</?ul[^>]*>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("</?ol[^>]*>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<pre[^>]*><code[^>]*>(.*?)</code></pre>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "```\n$1\n```")
        text = text.replace(Regex("<code[^>]*>(.*?)</code>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "`$1`")
        text = text.replace(Regex("<[^>]+>", setOf(RegexOption.DOT_MATCHES_ALL)), "")
        return text.trim()
    }
}

fun Server.addConfluenceTools() {
    val tools = ConfluenceTools()
    tools.addToServer(this)
}

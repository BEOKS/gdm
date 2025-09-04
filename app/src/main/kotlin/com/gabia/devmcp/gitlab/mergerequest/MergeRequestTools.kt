package com.gabia.devmcp.gitlab.mergerequest

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json

/**
 * GitLab Merge Request 관련 MCP 도구들을 서버에 등록하는 함수들
 */
class MergeRequestTools {
    
    private val apiClient = MergeRequestApiClient()
    
    /**
     * GitLab MR 관련 MCP 도구들을 서버에 등록하는 함수
     */
    fun addToServer(server: Server) {
        addGetMergeRequestTool(server)
        addGetMergeRequestDiffsTool(server)
        addMrDiscussionsTool(server)
        addCreateMergeRequestTool(server)
        addListMergeRequestsTool(server)
    }
    
    /**
     * 1. get_merge_request 도구 등록
     */
    private fun addGetMergeRequestTool(server: Server) {
        server.addTool(
            name = "get_merge_request",
            description = """
                Get details of a merge request (Either mergeRequestIid or sourceBranch must be provided)
                MR 상세 정보를 조회합니다 (mergeRequestIid 또는 sourceBranch 중 하나는 필수입니다)
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") {
                        put("type", "string")
                        put("description", "Project ID or URL-encoded path")
                    }
                    putJsonObject("merge_request_id") {
                        put("type", "string")
                        put("description", "The IID of a merge request")
                    }
                    putJsonObject("source_branch") {
                        put("type", "string")
                        put("description", "Source branch name")
                    }
                },
                required = listOf("project_id")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val mergeRequestIid = request.arguments["merge_request_id"]?.jsonPrimitive?.content
            val sourceBranch = request.arguments["source_branch"]?.jsonPrimitive?.content
            
            if (projectId == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("project_id 파라미터가 필요합니다.")),
                    isError = true
                )
            }
            
            if (mergeRequestIid == null && sourceBranch == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("merge_request_id 또는 source_branch 중 하나는 필수입니다.")),
                    isError = true
                )
            }
            
            try {
                val mergeRequest = apiClient.getMergeRequest(projectId, mergeRequestIid, sourceBranch)
                val result = Json.encodeToString(mergeRequest)
                CallToolResult(content = listOf(TextContent(result)))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("MR 정보를 가져오는 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }
    
    /**
     * 2. get_merge_request_diffs 도구 등록
     */
    private fun addGetMergeRequestDiffsTool(server: Server) {
        server.addTool(
            name = "get_merge_request_diffs",
            description = """
                Get the changes/diffs of a merge request (Either mergeRequestIid or sourceBranch must be provided)
                MR 변경사항을 조회합니다 (mergeRequestIid 또는 sourceBranch 중 하나는 필수입니다)
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") {
                        put("type", "string")
                        put("description", "Project ID or URL-encoded path")
                    }
                    putJsonObject("merge_request_id") {
                        put("type", "string")
                        put("description", "The IID of a merge request")
                    }
                    putJsonObject("source_branch") {
                        put("type", "string")
                        put("description", "Source branch name")
                    }
                    putJsonObject("view") {
                        put("type", "string")
                        put("description", "Diff view type (inline or parallel)")
                        put("enum", buildJsonArray {
                            add("inline")
                            add("parallel")
                        })
                    }
                },
                required = listOf("project_id")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val mergeRequestIid = request.arguments["merge_request_id"]?.jsonPrimitive?.content
            val sourceBranch = request.arguments["source_branch"]?.jsonPrimitive?.content
            val view = request.arguments["view"]?.jsonPrimitive?.content
            
            if (projectId == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("project_id 파라미터가 필요합니다.")),
                    isError = true
                )
            }
            
            if (mergeRequestIid == null && sourceBranch == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("merge_request_id 또는 source_branch 중 하나는 필수입니다.")),
                    isError = true
                )
            }
            
            try {
                val diffs = apiClient.getMergeRequestDiffs(projectId, mergeRequestIid, sourceBranch, view)
                val result = Json.encodeToString(diffs)
                CallToolResult(content = listOf(TextContent(result)))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("MR 변경사항을 가져오는 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }
    
    /**
     * 3. mr_discussions 도구 등록
     */
    private fun addMrDiscussionsTool(server: Server) {
        server.addTool(
            name = "mr_discussions",
            description = """
                List discussion items for a merge request
                MR 토론 목록을 조회합니다
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") {
                        put("type", "string")
                        put("description", "Project ID or URL-encoded path")
                    }
                    putJsonObject("merge_request_id") {
                        put("type", "string")
                        put("description", "The IID of a merge request")
                    }
                    putJsonObject("page") {
                        put("type", "number")
                        put("description", "Page number for pagination (default: 1)")
                    }
                    putJsonObject("per_page") {
                        put("type", "number")
                        put("description", "Number of items per page (max: 100, default: 20)")
                    }
                },
                required = listOf("project_id", "merge_request_id")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val mergeRequestIid = request.arguments["merge_request_id"]?.jsonPrimitive?.content
            val page = request.arguments["page"]?.jsonPrimitive?.intOrNull
            val perPage = request.arguments["per_page"]?.jsonPrimitive?.intOrNull
            
            if (projectId == null || mergeRequestIid == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("project_id와 merge_request_id 파라미터가 필요합니다.")),
                    isError = true
                )
            }
            
            try {
                val discussions = apiClient.listMergeRequestDiscussions(projectId, mergeRequestIid, page, perPage)
                val result = Json.encodeToString(discussions)
                CallToolResult(content = listOf(TextContent(result)))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("MR 토론을 가져오는 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }
    
    /**
     * 4. create_merge_request 도구 등록
     */
    private fun addCreateMergeRequestTool(server: Server) {
        server.addTool(
            name = "create_merge_request",
            description = """
                Create a new merge request in a GitLab project
                GitLab 프로젝트에 새로운 병합 요청을 생성합니다
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") {
                        put("type", "string")
                        put("description", "Project ID or complete URL-encoded path to project")
                    }
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Merge request title")
                    }
                    putJsonObject("description") {
                        put("type", "string")
                        put("description", "Merge request description")
                    }
                    putJsonObject("source_branch") {
                        put("type", "string")
                        put("description", "Branch containing changes")
                    }
                    putJsonObject("target_branch") {
                        put("type", "string")
                        put("description", "Branch to merge into")
                    }
                    putJsonObject("target_project_id") {
                        put("type", "number")
                        put("description", "Numeric ID of the target project")
                    }
                    putJsonObject("assignee_ids") {
                        put("type", "array")
                        put("description", "The ID of the users to assign the MR to")
                        putJsonObject("items") {
                            put("type", "number")
                        }
                    }
                    putJsonObject("reviewer_ids") {
                        put("type", "array")
                        put("description", "The ID of the users to assign as reviewers of the MR")
                        putJsonObject("items") {
                            put("type", "number")
                        }
                    }
                    putJsonObject("labels") {
                        put("type", "array")
                        put("description", "Labels for the MR")
                        putJsonObject("items") {
                            put("type", "string")
                        }
                    }
                    putJsonObject("draft") {
                        put("type", "boolean")
                        put("description", "Create as draft merge request")
                    }
                    putJsonObject("allow_collaboration") {
                        put("type", "boolean")
                        put("description", "Allow commits from upstream members")
                    }
                    putJsonObject("remove_source_branch") {
                        put("type", "boolean")
                        put("description", "Flag indicating if a merge request should remove the source branch when merging")
                    }
                    putJsonObject("squash") {
                        put("type", "boolean")
                        put("description", "If true, squash all commits into a single commit on merge")
                    }
                },
                required = listOf("project_id", "title", "source_branch", "target_branch")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val title = request.arguments["title"]?.jsonPrimitive?.content
            val description = request.arguments["description"]?.jsonPrimitive?.content
            val sourceBranch = request.arguments["source_branch"]?.jsonPrimitive?.content
            val targetBranch = request.arguments["target_branch"]?.jsonPrimitive?.content
            val targetProjectId = request.arguments["target_project_id"]?.jsonPrimitive?.intOrNull
            val assigneeIds = request.arguments["assignee_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }
            val reviewerIds = request.arguments["reviewer_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }
            val labels = request.arguments["labels"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            val draft = request.arguments["draft"]?.jsonPrimitive?.booleanOrNull
            val allowCollaboration = request.arguments["allow_collaboration"]?.jsonPrimitive?.booleanOrNull
            val removeSourceBranch = request.arguments["remove_source_branch"]?.jsonPrimitive?.booleanOrNull
            val squash = request.arguments["squash"]?.jsonPrimitive?.booleanOrNull
            
            if (projectId == null || title == null || sourceBranch == null || targetBranch == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("project_id, title, source_branch, target_branch 파라미터가 필요합니다.")),
                    isError = true
                )
            }
            
            try {
                val createRequest = CreateMergeRequestRequest(
                    title = title,
                    description = description,
                    source_branch = sourceBranch,
                    target_branch = targetBranch,
                    target_project_id = targetProjectId,
                    assignee_ids = assigneeIds,
                    reviewer_ids = reviewerIds,
                    labels = labels,
                    draft = draft,
                    allow_collaboration = allowCollaboration,
                    remove_source_branch = removeSourceBranch,
                    squash = squash
                )
                
                val mergeRequest = apiClient.createMergeRequest(projectId, createRequest)
                val result = Json.encodeToString(mergeRequest)
                CallToolResult(content = listOf(TextContent(result)))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("MR 생성 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }
    
    /**
     * 5. list_merge_requests 도구 등록
     */
    private fun addListMergeRequestsTool(server: Server) {
        server.addTool(
            name = "list_merge_requests",
            description = """
                List merge requests in a GitLab project with filtering options
                GitLab 프로젝트의 MR 목록을 다양한 필터 옵션과 함께 조회합니다
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") {
                        put("type", "string")
                        put("description", "Project ID or URL-encoded path")
                    }
                    putJsonObject("assignee_id") {
                        put("type", "string")
                        put("description", "Return merge requests assigned to the given user ID. user id or none or any")
                    }
                    putJsonObject("assignee_username") {
                        put("type", "string")
                        put("description", "Returns merge requests assigned to the given username")
                    }
                    putJsonObject("author_id") {
                        put("type", "string")
                        put("description", "Returns merge requests created by the given user ID")
                    }
                    putJsonObject("author_username") {
                        put("type", "string")
                        put("description", "Returns merge requests created by the given username")
                    }
                    putJsonObject("reviewer_id") {
                        put("type", "string")
                        put("description", "Returns merge requests which have the user as a reviewer. user id or none or any")
                    }
                    putJsonObject("reviewer_username") {
                        put("type", "string")
                        put("description", "Returns merge requests which have the user as a reviewer")
                    }
                    putJsonObject("created_after") {
                        put("type", "string")
                        put("description", "Return merge requests created after the given time")
                    }
                    putJsonObject("created_before") {
                        put("type", "string")
                        put("description", "Return merge requests created before the given time")
                    }
                    putJsonObject("updated_after") {
                        put("type", "string")
                        put("description", "Return merge requests updated after the given time")
                    }
                    putJsonObject("updated_before") {
                        put("type", "string")
                        put("description", "Return merge requests updated before the given time")
                    }
                    putJsonObject("labels") {
                        put("type", "array")
                        put("description", "Array of label names")
                        putJsonObject("items") {
                            put("type", "string")
                        }
                    }
                    putJsonObject("milestone") {
                        put("type", "string")
                        put("description", "Milestone title")
                    }
                    putJsonObject("scope") {
                        put("type", "string")
                        put("description", "Return merge requests from a specific scope")
                        put("enum", buildJsonArray {
                            add("created_by_me")
                            add("assigned_to_me")
                            add("all")
                        })
                    }
                    putJsonObject("search") {
                        put("type", "string")
                        put("description", "Search for specific terms")
                    }
                    putJsonObject("state") {
                        put("type", "string")
                        put("description", "Return merge requests with the given state")
                        put("enum", buildJsonArray {
                            add("opened")
                            add("closed")
                            add("locked")
                            add("merged")
                            add("all")
                        })
                    }
                    putJsonObject("wip") {
                        put("type", "string")
                        put("description", "Filter merge requests by their work in progress status")
                        put("enum", buildJsonArray {
                            add("yes")
                            add("no")
                        })
                    }
                    putJsonObject("with_merge_status_recheck") {
                        put("type", "boolean")
                        put("description", "Return merge requests that need their merge status rechecked")
                    }
                    putJsonObject("order_by") {
                        put("type", "string")
                        put("description", "Order merge requests by created_at, updated_at, or title")
                        put("enum", buildJsonArray {
                            add("created_at")
                            add("updated_at")
                            add("title")
                        })
                    }
                    putJsonObject("sort") {
                        put("type", "string")
                        put("description", "Sort order (asc or desc)")
                        put("enum", buildJsonArray {
                            add("asc")
                            add("desc")
                        })
                    }
                    putJsonObject("view") {
                        put("type", "string")
                        put("description", "If simple, returns the iid, URL, title, description, and basic state")
                        put("enum", buildJsonArray {
                            add("simple")
                            add("detailed")
                        })
                    }
                    putJsonObject("my_reaction_emoji") {
                        put("type", "string")
                        put("description", "Return merge requests reacted by the authenticated user by the given emoji")
                    }
                    putJsonObject("source_branch") {
                        put("type", "string")
                        put("description", "Return merge requests with the given source branch")
                    }
                    putJsonObject("target_branch") {
                        put("type", "string")
                        put("description", "Return merge requests with the given target branch")
                    }
                    putJsonObject("page") {
                        put("type", "number")
                        put("description", "Page number for pagination (default: 1)")
                    }
                    putJsonObject("per_page") {
                        put("type", "number")
                        put("description", "Number of items per page (max: 100, default: 20)")
                    }
                },
                required = listOf("project_id")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val assigneeId = request.arguments["assignee_id"]?.jsonPrimitive?.content
            val assigneeUsername = request.arguments["assignee_username"]?.jsonPrimitive?.content
            val authorId = request.arguments["author_id"]?.jsonPrimitive?.content
            val authorUsername = request.arguments["author_username"]?.jsonPrimitive?.content
            val reviewerId = request.arguments["reviewer_id"]?.jsonPrimitive?.content
            val reviewerUsername = request.arguments["reviewer_username"]?.jsonPrimitive?.content
            val createdAfter = request.arguments["created_after"]?.jsonPrimitive?.content
            val createdBefore = request.arguments["created_before"]?.jsonPrimitive?.content
            val updatedAfter = request.arguments["updated_after"]?.jsonPrimitive?.content
            val updatedBefore = request.arguments["updated_before"]?.jsonPrimitive?.content
            val labels = request.arguments["labels"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            val milestone = request.arguments["milestone"]?.jsonPrimitive?.content
            val scope = request.arguments["scope"]?.jsonPrimitive?.content
            val search = request.arguments["search"]?.jsonPrimitive?.content
            val state = request.arguments["state"]?.jsonPrimitive?.content
            val wip = request.arguments["wip"]?.jsonPrimitive?.content
            val withMergeStatusRecheck = request.arguments["with_merge_status_recheck"]?.jsonPrimitive?.booleanOrNull
            val orderBy = request.arguments["order_by"]?.jsonPrimitive?.content
            val sort = request.arguments["sort"]?.jsonPrimitive?.content
            val view = request.arguments["view"]?.jsonPrimitive?.content
            val myReactionEmoji = request.arguments["my_reaction_emoji"]?.jsonPrimitive?.content
            val sourceBranch = request.arguments["source_branch"]?.jsonPrimitive?.content
            val targetBranch = request.arguments["target_branch"]?.jsonPrimitive?.content
            val page = request.arguments["page"]?.jsonPrimitive?.intOrNull
            val perPage = request.arguments["per_page"]?.jsonPrimitive?.intOrNull
            
            if (projectId == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("project_id 파라미터가 필요합니다.")),
                    isError = true
                )
            }
            
            try {
                val options = ListMergeRequestsOptions(
                    assignee_id = assigneeId,
                    assignee_username = assigneeUsername,
                    author_id = authorId,
                    author_username = authorUsername,
                    reviewer_id = reviewerId,
                    reviewer_username = reviewerUsername,
                    created_after = createdAfter,
                    created_before = createdBefore,
                    updated_after = updatedAfter,
                    updated_before = updatedBefore,
                    labels = labels,
                    milestone = milestone,
                    scope = scope,
                    search = search,
                    state = state,
                    wip = wip,
                    with_merge_status_recheck = withMergeStatusRecheck,
                    order_by = orderBy,
                    sort = sort,
                    view = view,
                    my_reaction_emoji = myReactionEmoji,
                    source_branch = sourceBranch,
                    target_branch = targetBranch,
                    page = page,
                    per_page = perPage
                )
                
                val result = apiClient.listMergeRequests(projectId, options)
                val resultJson = Json.encodeToString(result)
                CallToolResult(content = listOf(TextContent(resultJson)))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("MR 목록을 가져오는 중 오류가 발생했습니다: ${e.message}")),
                    isError = true
                )
            }
        }
    }
    
    /**
     * 리소스 정리
     */
    fun close() {
        apiClient.close()
    }
}

package com.gabia.devmcp.gitlab.issue

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json

class IssueTools {

    private val apiClient = IssueApiClient()

    fun addToServer(server: Server) {
        addCreateIssue(server)
        addListIssues(server)
        addGetIssue(server)
        addUpdateIssue(server)
        addDeleteIssue(server)
//        addMyIssues(server)
        addListIssueDiscussions(server)
        addCreateIssueNote(server)
        addUpdateIssueNote(server)
        addListIssueLinks(server)
//        addGetIssueLink(server)
//        addCreateIssueLink(server)
//        addDeleteIssueLink(server)
    }

    private fun addCreateIssue(server: Server) {
        server.addTool(
            name = "create_issue",
            description = "Create a new issue in a GitLab project",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string"); put("description", "Project ID or URL-encoded path") }
                    putJsonObject("title") { put("type", "string"); put("description", "Issue title") }
                    putJsonObject("description") { put("type", "string"); put("description", "Issue description") }
                    putJsonObject("assignee_ids") { put("type", "array"); putJsonObject("items") { put("type", "number") } }
                    putJsonObject("milestone_id") { put("type", "string") }
                    putJsonObject("labels") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
                    putJsonObject("issue_type") { put("type", "string"); put("enum", buildJsonArray { add("issue"); add("incident"); add("test_case"); add("task") }) }
                },
                required = listOf("project_id", "title")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val title = request.arguments["title"]?.jsonPrimitive?.content
            if (projectId == null || title == null) {
                return@addTool CallToolResult(content = listOf(TextContent("project_id, title 파라미터가 필요합니다.")), isError = true)
            }
            val description = request.arguments["description"]?.jsonPrimitive?.contentOrNull
            val assigneeIds = request.arguments["assignee_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }
            val milestoneId = request.arguments["milestone_id"]?.jsonPrimitive?.contentOrNull
            val labels = request.arguments["labels"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            val issueType = request.arguments["issue_type"]?.jsonPrimitive?.contentOrNull
            return@addTool try {
                val created = apiClient.createIssue(projectId, CreateIssueRequest(title, description, assigneeIds, milestoneId, labels, issueType))
                CallToolResult(content = listOf(TextContent(Json.encodeToString(created))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 생성 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addListIssues(server: Server) {
        server.addTool(
            name = "list_issues",
            description = "List issues in a project or across all accessible projects",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("assignee_id") { put("type", "string") }
                    putJsonObject("assignee_username") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
                    putJsonObject("author_id") { put("type", "string") }
                    putJsonObject("author_username") { put("type", "string") }
                    putJsonObject("confidential") { put("type", "boolean") }
                    putJsonObject("created_after") { put("type", "string") }
                    putJsonObject("created_before") { put("type", "string") }
                    putJsonObject("due_date") { put("type", "string") }
                    putJsonObject("labels") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
                    putJsonObject("milestone") { put("type", "string") }
                    putJsonObject("issue_type") { put("type", "string") }
                    putJsonObject("iteration_id") { put("type", "string") }
                    putJsonObject("scope") { put("type", "string"); put("enum", buildJsonArray { add("created_by_me"); add("assigned_to_me"); add("all") }) }
                    putJsonObject("search") { put("type", "string") }
                    putJsonObject("state") { put("type", "string"); put("enum", buildJsonArray { add("opened"); add("closed"); add("all") }) }
                    putJsonObject("updated_after") { put("type", "string") }
                    putJsonObject("updated_before") { put("type", "string") }
                    putJsonObject("weight") { put("type", "number") }
                    putJsonObject("my_reaction_emoji") { put("type", "string") }
                    putJsonObject("order_by") { put("type", "string"); put("enum", buildJsonArray { add("created_at"); add("updated_at"); add("priority"); add("due_date"); add("relative_position"); add("label_priority"); add("milestone_due"); add("popularity"); add("weight") }) }
                    putJsonObject("sort") { put("type", "string"); put("enum", buildJsonArray { add("asc"); add("desc") }) }
                    putJsonObject("with_labels_details") { put("type", "boolean") }
                    putJsonObject("page") { put("type", "number") }
                    putJsonObject("per_page") { put("type", "number") }
                }
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.contentOrNull
            val options: Map<String, Any?> = request.arguments.mapValues { (_, v) ->
                when {
                    v is JsonArray -> v.map { it.jsonPrimitive.content }
                    v is JsonPrimitive && v.isString -> v.content
                    v is JsonPrimitive && v.booleanOrNull != null -> v.boolean
                    v is JsonPrimitive && v.intOrNull != null -> v.int
                    else -> null
                }
            }.toMutableMap().also { it.remove("project_id") }
            return@addTool try {
                val result = apiClient.listIssues(projectId, options)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(result))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 목록 조회 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addGetIssue(server: Server) {
        server.addTool(
            name = "get_issue",
            description = "Get details of a specific issue in a GitLab project",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("issue_iid") { put("type", "string") }
                },
                required = listOf("project_id", "issue_iid")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val issueIid = request.arguments["issue_iid"]?.jsonPrimitive?.content
            if (projectId == null || issueIid == null) {
                return@addTool CallToolResult(content = listOf(TextContent("project_id, issue_iid 파라미터가 필요합니다.")), isError = true)
            }
            return@addTool try {
                val issue = apiClient.getIssue(projectId, issueIid)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(issue))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 조회 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addUpdateIssue(server: Server) {
        server.addTool(
            name = "update_issue",
            description = "Update an issue in a GitLab project",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("issue_iid") { put("type", "string") }
                    putJsonObject("title") { put("type", "string") }
                    putJsonObject("description") { put("type", "string") }
                    putJsonObject("assignee_ids") { put("type", "array"); putJsonObject("items") { put("type", "number") } }
                    putJsonObject("confidential") { put("type", "boolean") }
                    putJsonObject("discussion_locked") { put("type", "boolean") }
                    putJsonObject("due_date") { put("type", "string") }
                    putJsonObject("labels") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
                    putJsonObject("milestone_id") { put("type", "string") }
                    putJsonObject("state_event") { put("type", "string"); put("enum", buildJsonArray { add("close"); add("reopen") }) }
                    putJsonObject("weight") { put("type", "number") }
                    putJsonObject("issue_type") { put("type", "string"); put("enum", buildJsonArray { add("issue"); add("incident"); add("test_case"); add("task") }) }
                },
                required = listOf("project_id", "issue_iid")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val issueIid = request.arguments["issue_iid"]?.jsonPrimitive?.content
            if (projectId == null || issueIid == null) {
                return@addTool CallToolResult(content = listOf(TextContent("project_id, issue_iid 파라미터가 필요합니다.")), isError = true)
            }
            val title = request.arguments["title"]?.jsonPrimitive?.contentOrNull
            val description = request.arguments["description"]?.jsonPrimitive?.contentOrNull
            val assigneeIds = request.arguments["assignee_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }
            val confidential = request.arguments["confidential"]?.jsonPrimitive?.booleanOrNull
            val discussionLocked = request.arguments["discussion_locked"]?.jsonPrimitive?.booleanOrNull
            val dueDate = request.arguments["due_date"]?.jsonPrimitive?.contentOrNull
            val labels = request.arguments["labels"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            val milestoneId = request.arguments["milestone_id"]?.jsonPrimitive?.contentOrNull
            val stateEvent = request.arguments["state_event"]?.jsonPrimitive?.contentOrNull
            val weight = request.arguments["weight"]?.jsonPrimitive?.intOrNull
            val issueType = request.arguments["issue_type"]?.jsonPrimitive?.contentOrNull
            return@addTool try {
                val updated = apiClient.updateIssue(
                    projectId, issueIid,
                    UpdateIssueRequest(title, description, assigneeIds, confidential, discussionLocked, dueDate, labels, milestoneId, stateEvent, weight, issueType)
                )
                CallToolResult(content = listOf(TextContent(Json.encodeToString(updated))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 업데이트 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addDeleteIssue(server: Server) {
        server.addTool(
            name = "delete_issue",
            description = "Delete an issue from a GitLab project",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("issue_iid") { put("type", "string") }
                },
                required = listOf("project_id", "issue_iid")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val issueIid = request.arguments["issue_iid"]?.jsonPrimitive?.content
            if (projectId == null || issueIid == null) {
                return@addTool CallToolResult(content = listOf(TextContent("project_id, issue_iid 파라미터가 필요합니다.")), isError = true)
            }
            return@addTool try {
                apiClient.deleteIssue(projectId, issueIid)
                CallToolResult(content = listOf(TextContent("{\"message\":\"Issue deleted successfully\"}")))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 삭제 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addMyIssues(server: Server) {
        server.addTool(
            name = "my_issues",
            description = "List issues assigned to the authenticated user (defaults to open issues)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("state") { put("type", "string"); put("enum", buildJsonArray { add("opened"); add("closed"); add("all") }) }
                    putJsonObject("labels") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
                    putJsonObject("milestone") { put("type", "string") }
                    putJsonObject("search") { put("type", "string") }
                    putJsonObject("created_after") { put("type", "string") }
                    putJsonObject("created_before") { put("type", "string") }
                    putJsonObject("updated_after") { put("type", "string") }
                    putJsonObject("updated_before") { put("type", "string") }
                    putJsonObject("per_page") { put("type", "number") }
                    putJsonObject("page") { put("type", "number") }
                }
            )
        ) { request ->
            // Emulate my_issues by calling list_issues with current username filter is non-trivial without user API; keep minimal: scope=assigned_to_me
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.contentOrNull
            val options = mutableMapOf<String, Any?>()
            request.arguments["state"]?.jsonPrimitive?.contentOrNull?.let { options["state"] = it }
            request.arguments["labels"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.let { options["labels"] = it }
            request.arguments["milestone"]?.jsonPrimitive?.contentOrNull?.let { options["milestone"] = it }
            request.arguments["search"]?.jsonPrimitive?.contentOrNull?.let { options["search"] = it }
            request.arguments["created_after"]?.jsonPrimitive?.contentOrNull?.let { options["created_after"] = it }
            request.arguments["created_before"]?.jsonPrimitive?.contentOrNull?.let { options["created_before"] = it }
            request.arguments["updated_after"]?.jsonPrimitive?.contentOrNull?.let { options["updated_after"] = it }
            request.arguments["updated_before"]?.jsonPrimitive?.contentOrNull?.let { options["updated_before"] = it }
            request.arguments["per_page"]?.jsonPrimitive?.intOrNull?.let { options["per_page"] = it }
            request.arguments["page"]?.jsonPrimitive?.intOrNull?.let { options["page"] = it }
            options["scope"] = "assigned_to_me"
            return@addTool try {
                val result = apiClient.listIssues(projectId, options)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(result))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("내 이슈 조회 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addListIssueDiscussions(server: Server) {
        server.addTool(
            name = "list_issue_discussions",
            description = "List discussions for an issue in a GitLab project",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("issue_iid") { put("type", "string") }
                    putJsonObject("page") { put("type", "number") }
                    putJsonObject("per_page") { put("type", "number") }
                },
                required = listOf("project_id", "issue_iid")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val issueIid = request.arguments["issue_iid"]?.jsonPrimitive?.content
            val page = request.arguments["page"]?.jsonPrimitive?.intOrNull
            val perPage = request.arguments["per_page"]?.jsonPrimitive?.intOrNull
            if (projectId == null || issueIid == null) {
                return@addTool CallToolResult(content = listOf(TextContent("project_id, issue_iid 파라미터가 필요합니다.")), isError = true)
            }
            return@addTool try {
                val result = apiClient.listIssueDiscussions(projectId, issueIid, page, perPage)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(result))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 토론 조회 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addCreateIssueNote(server: Server) {
        server.addTool(
            name = "create_issue_note",
            description = "Add a new note to an existing issue thread",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("issue_iid") { put("type", "string") }
                    putJsonObject("discussion_id") { put("type", "string") }
                    putJsonObject("body") { put("type", "string") }
                    putJsonObject("created_at") { put("type", "string") }
                },
                required = listOf("project_id", "issue_iid", "discussion_id", "body")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val issueIid = request.arguments["issue_iid"]?.jsonPrimitive?.content
            val discussionId = request.arguments["discussion_id"]?.jsonPrimitive?.content
            val body = request.arguments["body"]?.jsonPrimitive?.content
            val createdAt = request.arguments["created_at"]?.jsonPrimitive?.contentOrNull
            if (projectId == null || issueIid == null || discussionId == null || body == null) {
                return@addTool CallToolResult(content = listOf(TextContent("project_id, issue_iid, discussion_id, body 파라미터가 필요합니다.")), isError = true)
            }
            return@addTool try {
                val note = apiClient.createIssueNote(projectId, issueIid, discussionId, body, createdAt)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(note))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 노트 생성 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addUpdateIssueNote(server: Server) {
        server.addTool(
            name = "update_issue_note",
            description = "Modify an existing issue thread note",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("issue_iid") { put("type", "string") }
                    putJsonObject("discussion_id") { put("type", "string") }
                    putJsonObject("note_id") { put("type", "string") }
                    putJsonObject("body") { put("type", "string") }
                },
                required = listOf("project_id", "issue_iid", "discussion_id", "note_id", "body")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val issueIid = request.arguments["issue_iid"]?.jsonPrimitive?.content
            val discussionId = request.arguments["discussion_id"]?.jsonPrimitive?.content
            val noteId = request.arguments["note_id"]?.jsonPrimitive?.content
            val body = request.arguments["body"]?.jsonPrimitive?.content
            if (projectId == null || issueIid == null || discussionId == null || noteId == null || body == null) {
                return@addTool CallToolResult(content = listOf(TextContent("필수 파라미터 누락 (project_id, issue_iid, discussion_id, note_id, body)")), isError = true)
            }
            return@addTool try {
                val note = apiClient.updateIssueNote(projectId, issueIid, discussionId, noteId, body)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(note))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 노트 수정 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addListIssueLinks(server: Server) {
        server.addTool(
            name = "list_issue_links",
            description = "List all issue links for a specific issue",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("issue_iid") { put("type", "string") }
                },
                required = listOf("project_id", "issue_iid")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val issueIid = request.arguments["issue_iid"]?.jsonPrimitive?.content
            if (projectId == null || issueIid == null) {
                return@addTool CallToolResult(content = listOf(TextContent("project_id, issue_iid 파라미터가 필요합니다.")), isError = true)
            }
            return@addTool try {
                val links = apiClient.listIssueLinks(projectId, issueIid)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(links))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 링크 목록 조회 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addGetIssueLink(server: Server) {
        server.addTool(
            name = "get_issue_link",
            description = "Get a specific issue link",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("issue_iid") { put("type", "string") }
                    putJsonObject("issue_link_id") { put("type", "string") }
                },
                required = listOf("project_id", "issue_iid", "issue_link_id")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val issueIid = request.arguments["issue_iid"]?.jsonPrimitive?.content
            val issueLinkId = request.arguments["issue_link_id"]?.jsonPrimitive?.content
            if (projectId == null || issueIid == null || issueLinkId == null) {
                return@addTool CallToolResult(content = listOf(TextContent("필수 파라미터 누락 (project_id, issue_iid, issue_link_id)")), isError = true)
            }
            return@addTool try {
                val link = apiClient.getIssueLink(projectId, issueIid, issueLinkId)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(link))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 링크 조회 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addCreateIssueLink(server: Server) {
        server.addTool(
            name = "create_issue_link",
            description = "Create an issue link between two issues",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("issue_iid") { put("type", "string") }
                    putJsonObject("target_project_id") { put("type", "string") }
                    putJsonObject("target_issue_iid") { put("type", "string") }
                    putJsonObject("link_type") { put("type", "string"); put("enum", buildJsonArray { add("relates_to"); add("blocks"); add("is_blocked_by") }) }
                },
                required = listOf("project_id", "issue_iid", "target_project_id", "target_issue_iid")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val issueIid = request.arguments["issue_iid"]?.jsonPrimitive?.content
            val targetProjectId = request.arguments["target_project_id"]?.jsonPrimitive?.content
            val targetIssueIid = request.arguments["target_issue_iid"]?.jsonPrimitive?.content
            val linkType = request.arguments["link_type"]?.jsonPrimitive?.contentOrNull
            if (projectId == null || issueIid == null || targetProjectId == null || targetIssueIid == null) {
                return@addTool CallToolResult(content = listOf(TextContent("필수 파라미터 누락 (project_id, issue_iid, target_project_id, target_issue_iid)")), isError = true)
            }
            return@addTool try {
                val link = apiClient.createIssueLink(projectId, issueIid, targetProjectId, targetIssueIid, linkType)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(link))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 링크 생성 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    private fun addDeleteIssueLink(server: Server) {
        server.addTool(
            name = "delete_issue_link",
            description = "Delete an issue link",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project_id") { put("type", "string") }
                    putJsonObject("issue_iid") { put("type", "string") }
                    putJsonObject("issue_link_id") { put("type", "string") }
                },
                required = listOf("project_id", "issue_iid", "issue_link_id")
            )
        ) { request ->
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val issueIid = request.arguments["issue_iid"]?.jsonPrimitive?.content
            val issueLinkId = request.arguments["issue_link_id"]?.jsonPrimitive?.content
            if (projectId == null || issueIid == null || issueLinkId == null) {
                return@addTool CallToolResult(content = listOf(TextContent("필수 파라미터 누락 (project_id, issue_iid, issue_link_id)")), isError = true)
            }
            return@addTool try {
                apiClient.deleteIssueLink(projectId, issueIid, issueLinkId)
                CallToolResult(content = listOf(TextContent("{\"message\":\"Issue link deleted successfully\"}")))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("이슈 링크 삭제 중 오류: ${e.message}")), isError = true)
            }
        }
    }

    fun close() { apiClient.close() }
}

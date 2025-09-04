package com.gabia.devmcp.gitlab.issue

import kotlinx.serialization.Serializable

@Serializable
data class IssueUser(
    val id: Int,
    val username: String,
    val name: String,
    val state: String,
    val avatar_url: String? = null,
    val web_url: String
)

@Serializable
data class Issue(
    val id: Int,
    val iid: Int,
    val project_id: Int,
    val title: String,
    val description: String? = null,
    val state: String,
    val created_at: String,
    val updated_at: String,
    val closed_at: String? = null,
    val closed_by: IssueUser? = null,
    val labels: List<String> = emptyList(),
    val milestone: String? = null,
    val assignees: List<IssueUser> = emptyList(),
    val author: IssueUser,
    val web_url: String,
    val due_date: String? = null,
    val confidential: Boolean = false,
    val discussion_locked: Boolean? = null,
    val weight: Int? = null
)

@Serializable
data class IssueDiscussionNote(
    val id: Int,
    val type: String? = null,
    val body: String,
    val author: IssueUser,
    val created_at: String,
    val updated_at: String,
    val system: Boolean,
    val noteable_id: Int,
    val noteable_type: String,
    val noteable_iid: Int? = null,
    val resolvable: Boolean,
    val confidential: Boolean,
    val internal: Boolean,
    val active: Boolean,
    val resolved: Boolean? = null,
    val resolved_at: String? = null
)

@Serializable
data class IssueDiscussion(
    val id: String,
    val individual_note: Boolean,
    val notes: List<IssueDiscussionNote>
)

@Serializable
data class IssueLink(
    val source_issue: Issue,
    val target_issue: Issue,
    val link_type: String
)

@Serializable
data class Pagination(
    val page: Int,
    val per_page: Int,
    val total: Int,
    val total_pages: Int
)

@Serializable
data class PaginatedIssueDiscussions(
    val items: List<IssueDiscussion>,
    val pagination: Pagination
)

@Serializable
data class PaginatedIssues(
    val items: List<Issue>,
    val pagination: Pagination
)

@Serializable
data class CreateIssueRequest(
    val title: String,
    val description: String? = null,
    val assignee_ids: List<Int>? = null,
    val milestone_id: String? = null,
    val labels: List<String>? = null,
    val issue_type: String? = null
)

@Serializable
data class UpdateIssueRequest(
    val title: String? = null,
    val description: String? = null,
    val assignee_ids: List<Int>? = null,
    val confidential: Boolean? = null,
    val discussion_locked: Boolean? = null,
    val due_date: String? = null,
    val labels: List<String>? = null,
    val milestone_id: String? = null,
    val state_event: String? = null,
    val weight: Int? = null,
    val issue_type: String? = null
)


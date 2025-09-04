package com.gabia.devmcp.gitlab.mergerequest

import kotlinx.serialization.*

/**
 * GitLab Merge Request API 응답을 위한 데이터 클래스들
 */

@Serializable
data class User(
    val id: Int,
    val username: String,
    val name: String,
    val state: String,
    val avatar_url: String? = null,
    val web_url: String
)

@Serializable
data class MergeRequestDiffRef(
    val base_sha: String,
    val head_sha: String,
    val start_sha: String
)

@Serializable
data class MergeRequest(
    val id: Int,
    val iid: Int,
    val project_id: Int,
    val title: String,
    val description: String? = null,
    val state: String,
    val merged: Boolean? = null,
    val draft: Boolean? = null,
    val author: User,
    val assignees: List<User>? = null,
    val reviewers: List<User>? = null,
    val merged_by: User? = null,
    val merge_user: User? = null,
    val closed_by: User? = null,
    val source_branch: String,
    val target_branch: String,
    val diff_refs: MergeRequestDiffRef? = null,
    val web_url: String,
    val created_at: String,
    val updated_at: String,
    val merged_at: String? = null,
    val closed_at: String? = null,
    val merge_commit_sha: String? = null,
    val detailed_merge_status: String? = null,
    val merge_status: String? = null,
    val merge_error: String? = null,
    val work_in_progress: Boolean? = null,
    val blocking_discussions_resolved: Boolean? = null,
    val should_remove_source_branch: Boolean? = null,
    val force_remove_source_branch: Boolean? = null,
    val allow_collaboration: Boolean? = null,
    val allow_maintainer_to_push: Boolean? = null,
    val changes_count: String? = null,
    val merge_when_pipeline_succeeds: Boolean? = null,
    val squash: Boolean? = null,
    val labels: List<String>? = null
)

@Serializable
data class Diff(
    val old_path: String,
    val new_path: String,
    val a_mode: String,
    val b_mode: String,
    val diff: String,
    val new_file: Boolean,
    val renamed_file: Boolean,
    val deleted_file: Boolean
)

@Serializable
data class DiffPosition(
    val base_sha: String,
    val start_sha: String,
    val head_sha: String,
    val old_path: String,
    val new_path: String,
    val position_type: String,
    val old_line: Int? = null,
    val new_line: Int? = null
)

@Serializable
data class DiscussionNote(
    val id: String,
    val type: String? = null,
    val body: String,
    val attachment: String? = null,
    val author: User,
    val created_at: String,
    val updated_at: String,
    val system: Boolean,
    val noteable_id: Int,
    val noteable_type: String,
    val position: DiffPosition? = null,
    val resolvable: Boolean,
    val resolved: Boolean? = null,
    val resolved_by: User? = null,
    val resolved_at: String? = null
)

@Serializable
data class Discussion(
    val id: String,
    val individual_note: Boolean,
    val notes: List<DiscussionNote>
)

@Serializable
data class Pagination(
    val page: Int,
    val per_page: Int,
    val total: Int,
    val total_pages: Int
)

@Serializable
data class PaginatedDiscussionsResponse(
    val items: List<Discussion>,
    val pagination: Pagination
)

@Serializable
data class ChangesResponse(
    val changes: List<Diff>
)

/**
 * MR 생성 요청을 위한 데이터 클래스
 */
@Serializable
data class CreateMergeRequestRequest(
    val title: String,
    val description: String? = null,
    val source_branch: String,
    val target_branch: String,
    val target_project_id: Int? = null,
    val assignee_ids: List<Int>? = null,
    val reviewer_ids: List<Int>? = null,
    val labels: List<String>? = null,
    val draft: Boolean? = null,
    val allow_collaboration: Boolean? = null,
    val remove_source_branch: Boolean? = null,
    val squash: Boolean? = null
)

/**
 * MR 목록 검색을 위한 필터 옵션
 */
@Serializable
data class ListMergeRequestsOptions(
    val assignee_id: String? = null,
    val assignee_username: String? = null,
    val author_id: String? = null,
    val author_username: String? = null,
    val reviewer_id: String? = null,
    val reviewer_username: String? = null,
    val created_after: String? = null,
    val created_before: String? = null,
    val updated_after: String? = null,
    val updated_before: String? = null,
    val labels: List<String>? = null,
    val milestone: String? = null,
    val scope: String? = null, // "created_by_me", "assigned_to_me", "all"
    val search: String? = null,
    val state: String? = null, // "opened", "closed", "locked", "merged", "all"
    val wip: String? = null, // "yes", "no"
    val with_merge_status_recheck: Boolean? = null,
    val order_by: String? = null, // "created_at", "updated_at", "title"
    val sort: String? = null, // "asc", "desc"
    val view: String? = null, // "simple", "detailed"
    val my_reaction_emoji: String? = null,
    val source_branch: String? = null,
    val target_branch: String? = null,
    val not: NotOptions? = null,
    val page: Int? = null,
    val per_page: Int? = null
)

@Serializable
data class NotOptions(
    val labels: List<String>? = null,
    val milestone: String? = null,
    val author_id: String? = null,
    val author_username: String? = null,
    val assignee_id: String? = null,
    val assignee_username: String? = null,
    val reviewer_id: String? = null,
    val reviewer_username: String? = null,
    val my_reaction_emoji: String? = null
)

/**
 * MR 목록 응답을 위한 데이터 클래스
 */
@Serializable
data class PaginatedMergeRequestsResponse(
    val items: List<MergeRequest>,
    val pagination: Pagination
)

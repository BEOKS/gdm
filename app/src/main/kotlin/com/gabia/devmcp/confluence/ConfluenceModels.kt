package com.gabia.devmcp.confluence

import kotlinx.serialization.*

/**
 * Confluence API 응답을 위한 데이터 클래스들
 */

@Serializable
data class ConfluenceSpace(
    val id: Int,
    val key: String,
    val name: String,
    val type: String,
    val status: String,
    val description: String? = null,
    val homepage: ConfluenceContent? = null,
    val icon: ConfluenceIcon? = null,
    val creator: ConfluenceUser? = null,
    val creationDate: String? = null,
    val lastModifier: ConfluenceUser? = null,
    val lastModificationDate: String? = null,
    val metadata: ConfluenceSpaceMetadata? = null,
    val retentionPolicy: ConfluenceRetentionPolicy? = null,
    val permissions: ConfluencePermissions? = null,
    val _links: ConfluenceLinks? = null
)

@Serializable
data class ConfluenceIcon(
    val path: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val isDefault: Boolean? = null
)

@Serializable
data class ConfluenceSpaceMetadata(
    val labels: List<String>? = null
)

@Serializable
data class ConfluenceRetentionPolicy(
    val type: String? = null,
    val value: Int? = null
)

@Serializable
data class ConfluencePermissions(
    val use: ConfluencePermission? = null
)

@Serializable
data class ConfluencePermission(
    val operation: String? = null,
    val restrictions: List<String>? = null
)

@Serializable
data class ConfluenceLinks(
    val webui: String? = null,
    val self: String? = null,
    val base: String? = null,
    val context: String? = null,
    val next: String? = null,
    val prev: String? = null
)

@Serializable
data class ConfluenceUser(
    val type: String,
    val username: String,
    val userKey: String,
    val displayName: String,
    val email: String? = null,
    val _links: ConfluenceLinks? = null
)

@Serializable
data class ConfluenceVersion(
    val by: ConfluenceUser,
    @SerialName("when") val whenDate: String,
    val friendlyWhen: String? = null,
    val message: String? = null,
    val number: Int,
    val minorEdit: Boolean? = null,
    val hidden: Boolean? = null,
    val syncRev: String? = null,
    val content: ConfluenceContent? = null,
    val contentRef: ConfluenceContentRef? = null
)

@Serializable
data class ConfluenceContentRef(
    val id: String? = null,
    val type: String? = null,
    val status: String? = null,
    val title: String? = null
)

@Serializable
data class ConfluenceHistory(
    val latest: Boolean,
    val createdBy: ConfluenceUser,
    val createdDate: String,
    val previousVersion: ConfluenceVersion? = null,
    val nextVersion: ConfluenceVersion? = null,
    val lastUpdated: ConfluenceUser? = null,
    val contributors: ConfluenceContributors? = null,
    val lastUpdatedRef: ConfluenceVersion? = null,
    val nextVersionRef: ConfluenceVersion? = null,
    val previousVersionRef: ConfluenceVersion? = null,
    val contentParentRef: ConfluenceContentRef? = null,
    val _links: ConfluenceLinks? = null
)

@Serializable
data class ConfluenceContributors(
    val publishers: List<ConfluenceUser>? = null,
    val total: Int? = null
)

@Serializable
data class ConfluenceBody(
    val storage: ConfluenceStorage? = null,
    val view: ConfluenceStorage? = null,
    val export_view: ConfluenceStorage? = null,
    val styled_view: ConfluenceStorage? = null,
    val _expandable: Map<String, String>? = null
)

@Serializable
data class ConfluenceStorage(
    val value: String,
    val representation: String,
    val _expandable: Map<String, String>? = null
)

@Serializable
data class ConfluenceContent(
    val id: String,
    val type: String,
    val status: String,
    val title: String,
    val space: ConfluenceSpace? = null,
    val version: ConfluenceVersion? = null,
    val body: ConfluenceBody? = null,
    val history: ConfluenceHistory? = null,
    val _links: ConfluenceLinks? = null,
    val _expandable: Map<String, String>? = null
)

@Serializable
data class ConfluenceSearchResult(
    val id: String,
    val type: String,
    val status: String,
    val title: String,
    val space: ConfluenceSpace? = null,
    val version: ConfluenceVersion? = null,
    val body: ConfluenceBody? = null,
    val history: ConfluenceHistory? = null,
    val excerpt: String? = null,
    val ancestors: List<ConfluenceContent>? = null,
    val position: Int? = null,
    val operations: List<ConfluenceOperation>? = null,
    val children: ConfluenceChildren? = null,
    val descendants: ConfluenceDescendants? = null,
    val metadata: Map<String, String>? = null,
    val extensions: Map<String, String>? = null,
    val restrictions: ConfluenceRestrictions? = null,
    val relevantViewRestrictions: ConfluenceRelevantViewRestrictions? = null,
    val extractedTextLink: String? = null,
    val historyRef: ConfluenceHistoryRef? = null,
    val spaceRef: ConfluenceSpaceRef? = null,
    val containerRef: ConfluenceContainerRef? = null,
    val versionRef: ConfluenceVersionRef? = null,
    val _links: ConfluenceLinks? = null,
    val _expandable: Map<String, String>? = null
)

@Serializable
data class ConfluenceOperation(
    val operation: String,
    val targetType: String
)

@Serializable
data class ConfluenceChildren(
    val attachment: ConfluenceChildrenInfo? = null,
    val comment: ConfluenceChildrenInfo? = null,
    val page: ConfluenceChildrenInfo? = null
)

@Serializable
data class ConfluenceChildrenInfo(
    val results: List<ConfluenceContent>? = null,
    val start: Int? = null,
    val limit: Int? = null,
    val size: Int? = null,
    val _links: ConfluenceLinks? = null
)

@Serializable
data class ConfluenceDescendants(
    val attachment: ConfluenceChildrenInfo? = null,
    val comment: ConfluenceChildrenInfo? = null,
    val page: ConfluenceChildrenInfo? = null
)

@Serializable
data class ConfluenceRestrictions(
    val use: ConfluencePermission? = null
)

@Serializable
data class ConfluenceRelevantViewRestrictions(
    val idProperties: Map<String, String>? = null,
    val expanded: Boolean? = null
)

@Serializable
data class ConfluenceHistoryRef(
    val idProperties: Map<String, String>? = null,
    val expanded: Boolean? = null
)

@Serializable
data class ConfluenceSpaceRef(
    val idProperties: Map<String, String>? = null,
    val expanded: Boolean? = null
)

@Serializable
data class ConfluenceContainerRef(
    val idProperties: Map<String, String>? = null,
    val expanded: Boolean? = null
)

@Serializable
data class ConfluenceVersionRef(
    val idProperties: Map<String, String>? = null,
    val expanded: Boolean? = null
)

@Serializable
data class ConfluenceSearchResponse(
    val results: List<ConfluenceSearchResult>,
    val totalCount: Int? = null,
    val start: Int,
    val limit: Int,
    val size: Int,
    val _links: ConfluenceLinks? = null
)

@Serializable
data class ConfluenceUserSearchResult(
    val user: ConfluenceUser,
    val _links: ConfluenceLinks? = null
)

@Serializable
data class ConfluenceUserSearchResponse(
    val results: List<ConfluenceUserSearchResult>,
    val start: Int,
    val limit: Int,
    val size: Int,
    val _links: ConfluenceLinks? = null
)

/**
 * Confluence 페이지 정보를 나타내는 데이터 클래스
 */
@Serializable
data class ConfluencePage(
    val id: String,
    val title: String,
    val spaceKey: String,
    val content: String,
    val url: String,
    val created: String? = null,
    val modified: String? = null,
    val author: String? = null,
    val excerpt: String? = null
)

/**
 * Confluence 사용자 정보를 나타내는 데이터 클래스
 */
@Serializable
data class ConfluenceUserInfo(
    val userKey: String,
    val username: String,
    val displayName: String,
    val email: String? = null
)

/**
 * 문서 검색 옵션 (고급 파라미터 지원)
 */
@Serializable
data class DocumentSearchOptions(
    val query: String,
    val limit: Int = 10,
    val start: Int = 0,
    val spacesFilter: String? = null,
    val expand: String? = null,
    val orderBy: String? = null,
    val cqlContext: String? = null
)

/**
 * 사용자 검색 옵션
 */
@Serializable
data class UserSearchOptions(
    val query: String,
    val limit: Int = 10
)

/**
 * 페이지 내용 조회 옵션
 */
@Serializable
data class PageContentOptions(
    val pageId: String
)

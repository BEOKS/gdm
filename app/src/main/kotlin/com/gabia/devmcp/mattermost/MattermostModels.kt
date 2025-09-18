package com.gabia.devmcp.mattermost

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mattermost API 응답 모델들
 */

@Serializable
data class MattermostSearchResponse(
    val posts: Map<String, MattermostPost> = emptyMap(),
    val order: List<String> = emptyList(),
    val next_post_id: String? = null,
    val prev_post_id: String? = null,
    val has_more: Boolean = false
)

@Serializable
data class MattermostPost(
    val id: String = "",
    val create_at: Long = 0,
    val update_at: Long = 0,
    val edit_at: Long = 0,
    val delete_at: Long = 0,
    val is_pinned: Boolean = false,
    val user_id: String = "",
    val channel_id: String = "",
    val root_id: String = "",
    val parent_id: String = "",
    val original_id: String = "",
    val message: String = "",
    val type: String = "",
    val props: JsonElement? = null,
    val hashtags: String = "",
    val file_ids: List<String> = emptyList(),
    val pending_post_id: String = "",
    val metadata: MattermostPostMetadata? = null
)

@Serializable
data class MattermostPostMetadata(
    val priority: MattermostPostPriority? = null,
    val files: List<MattermostFileInfo> = emptyList(),
    val reactions: List<MattermostReaction> = emptyList(),
    val embeds: List<MattermostEmbed> = emptyList(),
    val emojis: List<MattermostEmoji> = emptyList()
)

@Serializable
data class MattermostPostPriority(
    val priority: String = "",
    val requested_ack: Boolean = false,
    val persistent_notifications: Boolean = false
)

@Serializable
data class MattermostFileInfo(
    val id: String = "",
    val user_id: String = "",
    val post_id: String = "",
    val create_at: Long = 0,
    val update_at: Long = 0,
    val delete_at: Long = 0,
    val name: String = "",
    val extension: String = "",
    val size: Long = 0,
    val mime_type: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val has_preview_image: Boolean = false,
    val mini_preview: String = "",
    val thumb_path: String = "",
    val path: String = "",
    val remote_path: String = ""
)

@Serializable
data class MattermostReaction(
    val user_id: String = "",
    val post_id: String = "",
    val emoji_name: String = "",
    val create_at: Long = 0
)

@Serializable
data class MattermostEmbed(
    val type: String = "",
    val url: String = "",
    val data: JsonElement? = null
)

@Serializable
data class MattermostEmoji(
    val name: String = "",
    val image: String = ""
)

@Serializable
data class MattermostFileSearchResponse(
    val order: List<String> = emptyList(),
    val file_infos: Map<String, MattermostFileInfo> = emptyMap(),
    val next_file_info_id: String? = null,
    val prev_file_info_id: String? = null,
    val has_more: Boolean = false
)

@Serializable
data class MattermostTeam(
    val id: String = "",
    val create_at: Long = 0,
    val update_at: Long = 0,
    val delete_at: Long = 0,
    val display_name: String = "",
    val name: String = "",
    val description: String = "",
    val email: String = "",
    val type: String = "",
    val company_name: String = "",
    val allowed_domains: String = "",
    val invite_id: String = "",
    val allow_open_invite: Boolean = false,
    val scheme_id: String = "",
    val group_constrained: Boolean = false,
    val policy_id: String? = null
)

@Serializable
data class MattermostChannel(
    val id: String = "",
    val create_at: Long = 0,
    val update_at: Long = 0,
    val delete_at: Long = 0,
    val team_id: String = "",
    val type: String = "",
    val display_name: String = "",
    val name: String = "",
    val header: String = "",
    val purpose: String = "",
    val last_post_at: Long = 0,
    val total_msg_count: Long = 0,
    val extra_update_at: Long = 0,
    val creator_id: String = "",
    val scheme_id: String? = null,
    val group_constrained: Boolean = false,
    val shared: Boolean = false,
    val total_msg_count_root: Long = 0,
    val policy_id: String? = null
)

@Serializable
data class MattermostUser(
    val id: String = "",
    val create_at: Long = 0,
    val update_at: Long = 0,
    val delete_at: Long = 0,
    val username: String = "",
    val first_name: String = "",
    val last_name: String = "",
    val nickname: String = "",
    val email: String = "",
    val email_verified: Boolean = false,
    val auth_service: String = "",
    val roles: String = "",
    val locale: String = "",
    val notify_props: MattermostUserNotifyProps? = null,
    val last_activity_at: Long = 0,
    val last_ping_at: Long = 0,
    val allow_marketing: Boolean = false,
    val is_bot: Boolean = false,
    val bot_description: String = "",
    val is_system_admin: Boolean = false,
    val is_guest: Boolean = false,
    val timezone: MattermostTimezone? = null,
    val terms_of_service_id: String = "",
    val terms_of_service_create_at: Long = 0
)

@Serializable
data class MattermostUserNotifyProps(
    val email: String = "",
    val push: String = "",
    val desktop: String = "",
    val desktop_sound: String = "",
    val mention_keys: String = "",
    val channel: String = "",
    val first_name: String = ""
)

@Serializable
data class MattermostTimezone(
    val use_automatic_timezone: Boolean = false,
    val manual_timezone: String = "",
    val automatic_timezone: String = ""
)

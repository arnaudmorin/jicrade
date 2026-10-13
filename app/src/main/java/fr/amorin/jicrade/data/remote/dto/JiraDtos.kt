package fr.amorin.jicrade.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TenantInfoDto(val cloudId: String)

@Serializable
data class JiraUserDto(
    val accountId: String,
    val displayName: String? = null,
    val emailAddress: String? = null,
    val avatarUrls: Map<String, String> = emptyMap(),
)

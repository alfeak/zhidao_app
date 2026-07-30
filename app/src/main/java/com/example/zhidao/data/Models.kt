package com.example.zhidao.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    val picture: String? = null,
    val createdAt: String? = null
)

@Serializable
data class MarkdownBlock(
    val index: Int,
    val content: String,
    val pageIndex: Int? = null
)

@Serializable
data class MarkdownResponse(
    val content: String,
    val blocks: List<MarkdownBlock>,
    val isTranslation: Boolean
)

@Serializable
data class Translation(
    val targetLanguage: String,
    val archivePath: String
)

@Serializable
data class Paper(
    val id: String,
    val title: String,
    val url: String,
    val isDecoded: Boolean,
    val decodeStatus: String, // idle, pending, processing, done, failed
    val decodeError: String? = null,
    val importedAt: String,
    val markdownObjectKey: String? = null,
    val translations: List<Translation>? = null
)

@Serializable
data class Language(
    val code: String,
    val name: String
)

@Serializable
data class TranslationLanguagesResponse(
    val languages: List<Language>
)

@Serializable
data class AuthMeResponse(
    val user: User?
)

@Serializable
data class LoginResponse(
    val success: Boolean = false,
    val user: User? = null,
    val sessionId: String? = null
)

@Serializable
data class ExchangeTempTokenRequest(
    val tempToken: String
)

@Serializable
data class SearchSource(
    val source: String,
    val language: String? = null
)

@Serializable
data class SearchResult(
    @SerialName("paperId")
    val id: String,
    val title: String,
    val sources: List<SearchSource> = emptyList()
)

@Serializable
data class SearchResponse(
    val results: List<SearchResult>
)

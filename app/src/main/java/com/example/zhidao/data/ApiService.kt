package com.example.zhidao.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ApiService(private val baseUrl: String, private var sessionId: String? = null) {
    fun updateSessionId(id: String?) {
        sessionId = id
    }

    private val client = HttpClient(Android) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
        install(HttpCookies)
        install(Logging) {
            level = LogLevel.INFO
        }
        defaultRequest {
            // Robustly ensure path ends with /api/
            val uri = Url(baseUrl)
            url {
                takeFrom(uri)
                val path = uri.encodedPath.removeSuffix("/")
                encodedPath = if (path.endsWith("/api")) "$path/" else "$path/api/"
            }
            this@ApiService.sessionId?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }
    }

    suspend fun getFriendlyErrorMessage(e: Exception): String {
        if (e is kotlinx.serialization.SerializationException) {
            return "Server response format mismatch"
        }
        return if (e is ResponseException) {
            try {
                val bodyString = e.response.bodyAsText()
                val json = Json { ignoreUnknownKeys = true }
                val element = json.parseToJsonElement(bodyString)
                element.jsonObject["detail"]?.jsonPrimitive?.content ?: e.message ?: "Server error"
            } catch (ignore: Exception) {
                e.message ?: "Unknown network error"
            }
        } else {
            e.message ?: "Connection failed"
        }
    }

    suspend fun exchangeTempToken(tempToken: String): LoginResponse {
        return client.post("auth/exchange-temp-token") {
            contentType(ContentType.Application.Json)
            setBody(ExchangeTempTokenRequest(tempToken))
        }.body()
    }

    suspend fun getMe(): User? {
        return try {
            val response: AuthMeResponse = client.get("auth/me").body()
            response.user
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPapers(): List<Paper> {
        return client.get("papers").body()
    }

    suspend fun getPaper(id: String): Paper {
        return client.get("papers/$id").body()
    }

    suspend fun getMarkdown(paperId: String, targetLanguage: String? = null): MarkdownResponse {
        return client.get("papers/$paperId/markdown") {
            targetLanguage?.let { parameter("targetLanguage", it) }
        }.body()
    }

    suspend fun getTranslationLanguages(): List<Language> {
        val response: TranslationLanguagesResponse = client.get("translation-languages").body()
        return response.languages
    }

    suspend fun searchPapers(query: String): List<SearchResult> {
        val response: SearchResponse = client.get("search") {
            parameter("q", query)
            parameter("limit", 30)
        }.body()
        return response.results
    }

    suspend fun deletePaper(id: String) {
        client.delete("papers/$id")
    }

    suspend fun retryDecode(id: String) {
        client.post("papers/$id/decode")
    }

    suspend fun startTranslation(paperId: String, targetLanguage: String) {
        client.post("papers/$paperId/translations") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("targetLanguage" to targetLanguage))
        }
    }
}

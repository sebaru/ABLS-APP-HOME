package fr.abls.habitat.home.api

import fr.abls.habitat.home.auth.AuthManager
import fr.abls.habitat.home.session.Session
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class ApiException(val status: Int, message: String) : Exception(message)

/** Équivalent de Send_to_API() dans common.js. */
class ApiClient(
    val http: HttpClient,
    private val auth: AuthManager,
    private val session: Session,
) {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    private fun baseUrl() = session.config.apiUrl.trimEnd('/')

    suspend fun raw(method: HttpMethod, path: String, query: Map<String, String?> = emptyMap(), body: JsonElement? = null): String {
        val url = buildString {
            append(baseUrl()).append(path)
            val q = query.filterValues { it != null }
            if (q.isNotEmpty()) append('?').append(q.entries.joinToString("&") { "${it.key}=${it.value!!.encodeURLParameter()}" })
        }
        val resp = http.request(url) {
            this.method = method
            auth.accessToken?.let { header("Authorization", "Bearer $it") }
            session.domainUuid?.let { header("X-ABLS-DOMAIN", it) }
            if (method != HttpMethod.Get) {
                contentType(ContentType.Application.Json)
                setBody((body ?: JsonObject(emptyMap())).toString())
            }
        }
        val text = resp.bodyAsText()
        if (!resp.status.isSuccess()) {
            val apiError = runCatching { json.decodeFromString<ApiError>(text).api_error }.getOrNull()
            throw ApiException(resp.status.value, apiError?.let { "Une erreur est survenue: $it" }
                ?: "Une erreur ${resp.status.value} est survenue: ${resp.status.description}")
        }
        return text
    }

    suspend inline fun <reified T> get(path: String, query: Map<String, String?> = emptyMap()): T =
        json.decodeFromString(raw(HttpMethod.Get, path, query))

    suspend inline fun <reified T> post(path: String, body: JsonElement? = null): T =
        json.decodeFromString(raw(HttpMethod.Post, path, body = body))

    suspend fun postNoResult(path: String, body: JsonElement? = null) { raw(HttpMethod.Post, path, body = body) }

    suspend fun deleteNoResult(path: String, body: JsonElement? = null) { raw(HttpMethod.Delete, path, body = body) }

    suspend fun postJson(path: String, body: JsonElement? = null): JsonObject =
        json.parseToJsonElement(raw(HttpMethod.Post, path, body = body)) as JsonObject
}

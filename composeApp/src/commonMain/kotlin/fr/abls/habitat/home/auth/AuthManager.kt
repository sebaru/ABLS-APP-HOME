package fr.abls.habitat.home.auth

import com.russhwolf.settings.Settings
import fr.abls.habitat.home.config.AppConfig
import fr.abls.habitat.home.platform.createSettings
import fr.abls.habitat.home.platform.currentTimeMillis
import fr.abls.habitat.home.platform.openAuthUrl
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

const val REDIRECT_SCHEME = "abls.habitat.home"
const val REDIRECT_URI = "$REDIRECT_SCHEME://callback"

@Serializable
data class TokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val id_token: String? = null,
    val expires_in: Long = 300,
    val refresh_expires_in: Long = 0,
    val token_type: String = "Bearer",
)

@Serializable
data class OidcDiscovery(
    val issuer: String,
    val authorization_endpoint: String,
    val token_endpoint: String,
    val end_session_endpoint: String? = null,
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String?,
    val expiresAtMillis: Long,
) {
    val claims: JsonObject? by lazy { parseJwtClaims(accessToken) }
    val displayName: String
        get() = claims?.let { c ->
            listOf("name", "preferred_username", "given_name", "email")
                .firstNotNullOfOrNull { k -> c[k]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } }
        } ?: "Unknown"
    val issuer: String? get() = claims?.get("iss")?.jsonPrimitive?.content
}

sealed interface AuthState {
    data object Idle : AuthState
    data object InProgress : AuthState
    data class Authenticated(val tokens: AuthTokens) : AuthState
    data class Error(val message: String) : AuthState
}

/** Remplace keycloak-js : login par navigateur système (Authorization Code + PKCE), refresh périodique, logout. */
class AuthManager(private val http: HttpClient, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val settings: Settings by lazy { createSettings("abls_auth") }
    private var discovery: OidcDiscovery? = null
    private var config: AppConfig? = null
    private var refreshJob: Job? = null

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state

    val accessToken: String? get() = (_state.value as? AuthState.Authenticated)?.tokens?.accessToken

    suspend fun discover(config: AppConfig): OidcDiscovery {
        this.config = config
        discovery?.let { if (it.issuer == config.issuer) return it }
        val resp = http.get("${config.issuer}/.well-known/openid-configuration")
        if (!resp.status.isSuccess()) error("IDP injoignable (${resp.status})")
        return json.decodeFromString<OidcDiscovery>(resp.bodyAsText()).also { discovery = it }
    }

    /** Tente une reprise de session à partir du refresh_token persisté. */
    suspend fun restore(config: AppConfig): Boolean {
        val refresh = settings.getStringOrNull("refresh_token") ?: return false
        return try {
            val d = discover(config)
            val t = refreshWith(d, config, refresh)
            setAuthenticated(t)
            true
        } catch (e: Exception) {
            settings.remove("refresh_token")
            false
        }
    }

    suspend fun login(config: AppConfig) {
        _state.value = AuthState.InProgress
        try {
            val d = discover(config)
            val verifier = Pkce.randomString(48)
            val state = Pkce.randomString(16)
            val nonce = Pkce.randomString(16)
            val url = URLBuilder(d.authorization_endpoint).apply {
                parameters.append("client_id", config.idpClientId)
                parameters.append("redirect_uri", REDIRECT_URI)
                parameters.append("response_type", "code")
                parameters.append("scope", "openid profile email")
                parameters.append("state", state)
                parameters.append("nonce", nonce)
                parameters.append("code_challenge", Pkce.challengeFor(verifier))
                parameters.append("code_challenge_method", "S256")
            }.buildString()

            openAuthUrl(url, REDIRECT_SCHEME)

            val redirect = withTimeoutOrNull(5 * 60_000L) { AuthRedirectBus.redirects.first() }
                ?: run { _state.value = AuthState.Error("Connexion annulée (délai dépassé)"); return }
            val params = Url(redirect).parameters
            if (params["state"] != state) { _state.value = AuthState.Error("State OIDC invalide"); return }
            params["error"]?.let { _state.value = AuthState.Error(params["error_description"] ?: it); return }
            val code = params["code"] ?: run { _state.value = AuthState.Error("Code OIDC absent"); return }

            val resp = http.submitForm(
                url = d.token_endpoint,
                formParameters = Parameters.build {
                    append("grant_type", "authorization_code")
                    append("client_id", config.idpClientId)
                    append("code", code)
                    append("redirect_uri", REDIRECT_URI)
                    append("code_verifier", verifier)
                }
            )
            if (!resp.status.isSuccess()) { _state.value = AuthState.Error("Echange du code refusé : ${resp.bodyAsText()}"); return }
            setAuthenticated(resp.body<TokenResponse>().toTokens())
        } catch (e: Exception) {
            _state.value = AuthState.Error(e.message ?: "Erreur d'authentification")
        }
    }

    private suspend fun refreshWith(d: OidcDiscovery, config: AppConfig, refresh: String): AuthTokens {
        val resp = http.submitForm(
            url = d.token_endpoint,
            formParameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("client_id", config.idpClientId)
                append("refresh_token", refresh)
            }
        )
        if (!resp.status.isSuccess()) error("Refresh refusé : ${resp.status}")
        return resp.body<TokenResponse>().toTokens()
    }

    private fun setAuthenticated(t: AuthTokens) {
        t.refreshToken?.let { settings.putString("refresh_token", it) }
        _state.value = AuthState.Authenticated(t)
        scheduleRefresh()
    }

    /* Équivalent du setInterval(updateToken(30), 60s) de common.js */
    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                val tokens = (_state.value as? AuthState.Authenticated)?.tokens ?: return@launch
                val waitMs = (tokens.expiresAtMillis - currentTimeMillis() - 30_000L).coerceIn(5_000L, 60_000L)
                delay(waitMs)
                val cfg = config ?: return@launch
                val d = discovery ?: return@launch
                val refresh = tokens.refreshToken ?: return@launch
                try {
                    val nt = refreshWith(d, cfg, refresh)
                    nt.refreshToken?.let { settings.putString("refresh_token", it) }
                    _state.value = AuthState.Authenticated(nt)
                } catch (e: Exception) {
                    if (tokens.expiresAtMillis < currentTimeMillis()) {
                        _state.value = AuthState.Error("Session expirée")
                        return@launch
                    }
                }
            }
        }
    }

    suspend fun logout() {
        val tokens = (_state.value as? AuthState.Authenticated)?.tokens
        refreshJob?.cancel()
        settings.remove("refresh_token")
        _state.value = AuthState.Idle
        val d = discovery ?: return
        val cfg = config ?: return
        try {
            d.end_session_endpoint?.let { ep ->
                tokens?.refreshToken?.let { refresh ->
                    http.submitForm(
                        url = ep,
                        formParameters = Parameters.build {
                            append("client_id", cfg.idpClientId)
                            append("refresh_token", refresh)
                        }
                    )
                }
            }
        } catch (_: Exception) { }
    }

    private fun TokenResponse.toTokens() = AuthTokens(
        accessToken = access_token,
        refreshToken = refresh_token,
        idToken = id_token,
        expiresAtMillis = currentTimeMillis() + expires_in * 1000,
    )
}

fun parseJwtClaims(jwt: String): JsonObject? = try {
    val payload = jwt.split('.').getOrNull(1) ?: return null
    Json.parseToJsonElement(Pkce.decodeBase64Url(payload).decodeToString()) as JsonObject
} catch (e: Exception) { null }

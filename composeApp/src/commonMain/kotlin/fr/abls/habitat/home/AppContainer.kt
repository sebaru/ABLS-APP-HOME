package fr.abls.habitat.home

import fr.abls.habitat.home.api.ApiClient
import fr.abls.habitat.home.auth.AuthManager
import fr.abls.habitat.home.config.AppConfig
import fr.abls.habitat.home.config.ConfigStore
import fr.abls.habitat.home.mqtt.MqttManager
import fr.abls.habitat.home.session.Session
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json

/** Graphe d'objets de l'application (singleton process). */
class AppContainer(config: AppConfig = ConfigStore.load()) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http = HttpClient {
        install(WebSockets)
        install(HttpTimeout) { requestTimeoutMillis = 300_000; connectTimeoutMillis = 15_000 }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        expectSuccess = false
    }

    val session = Session(config)
    val auth = AuthManager(http, scope)
    val api = ApiClient(http, auth, session)
    val mqtt = MqttManager(http, session, scope)

    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 16)
    val toasts: SharedFlow<Toast> = _toasts

    fun showError(message: String) { _toasts.tryEmit(Toast(message, isError = true)) }
    fun showOk(message: String) { _toasts.tryEmit(Toast(message, isError = false)) }

    companion object {
        val instance: AppContainer by lazy { AppContainer() }
    }
}

data class Toast(val message: String, val isError: Boolean)

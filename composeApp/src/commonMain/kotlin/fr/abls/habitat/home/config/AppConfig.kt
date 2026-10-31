package fr.abls.habitat.home.config

import com.russhwolf.settings.Settings
import fr.abls.habitat.home.platform.createSettings

/** Équivalent de public/js/config.js : paramètres d'accès à l'API et à l'IDP Keycloak. */
data class AppConfig(
    val apiUrl: String = "",
    val idpUrl: String = "",
    val idpRealm: String = "abls-habitat",
    val idpClientId: String = "abls-app-home",
) {
    val isValid: Boolean
        get() = apiUrl.startsWith("http") && idpUrl.startsWith("http") &&
                idpRealm.isNotBlank() && idpClientId.isNotBlank() &&
                !apiUrl.contains("exemple.com") && !idpUrl.contains("exemple.com")

    val issuer: String get() = "${idpUrl.trimEnd('/')}/realms/$idpRealm"
}

object ConfigStore {
    private val settings: Settings by lazy { createSettings("abls_config") }

    fun load(): AppConfig = AppConfig(
        apiUrl = settings.getString("api_url", ""),
        idpUrl = settings.getString("idp_url", ""),
        idpRealm = settings.getString("idp_realm", "abls-habitat"),
        idpClientId = settings.getString("idp_client_id", "abls-app-home"),
    )

    fun save(config: AppConfig) {
        settings.putString("api_url", config.apiUrl.trimEnd('/'))
        settings.putString("idp_url", config.idpUrl.trimEnd('/'))
        settings.putString("idp_realm", config.idpRealm.trim())
        settings.putString("idp_client_id", config.idpClientId.trim())
    }
}

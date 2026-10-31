package fr.abls.habitat.home.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import fr.abls.habitat.home.api.Profil
import fr.abls.habitat.home.config.AppConfig
import fr.abls.habitat.home.platform.createSettings

/** Équivalent du localStorage/sessionStorage de common.js (domaine courant, static_data_url, MQTT...). */
class Session(var config: AppConfig) {
    private val settings: Settings by lazy { createSettings("abls_session") }

    var domainUuid: String? by mutableStateOf(settings.getStringOrNull("domain_uuid"))
        private set
    var domainName: String? by mutableStateOf(settings.getStringOrNull("domain_name"))
        private set
    var staticDataUrl: String by mutableStateOf(settings.getString("static_data_url", "https://static.abls-habitat.fr"))
        private set
    var accessLevel: Int by mutableStateOf(settings.getInt("access_level", 0))
        private set
    var mqttHostname: String? = null
    var mqttPort: Int = 0
    var mqttOverSsl: Boolean = false
    var browserPassword: String? = null
    var profil: Profil? by mutableStateOf(null)
        private set
    var domainImage: String? by mutableStateOf(null)

    fun applyProfil(p: Profil) {
        profil = p
        if (p.default_domain_uuid == null) {
            clearDomain()
            return
        }
        setDomain(p.default_domain_uuid, p.default_domain_name ?: "")
        p.static_data_url?.let { staticDataUrl = it; settings.putString("static_data_url", it) }
        accessLevel = p.access_level; settings.putInt("access_level", p.access_level)
        mqttHostname = p.mqtt_hostname
        mqttPort = p.mqtt_port
        mqttOverSsl = p.mqtt_over_ssl
        browserPassword = p.browser_password
    }

    fun setDomain(uuid: String, name: String) {
        domainUuid = uuid; settings.putString("domain_uuid", uuid)
        domainName = name; settings.putString("domain_name", name)
    }

    fun clearDomain() {
        domainUuid = null; domainName = null
        settings.remove("domain_uuid"); settings.remove("domain_name")
    }

    fun imgUrl(file: String): String = "${staticDataUrl.trimEnd('/')}/img/$file"
}

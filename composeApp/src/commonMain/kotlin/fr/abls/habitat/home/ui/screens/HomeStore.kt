package fr.abls.habitat.home.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.abls.habitat.home.AppContainer
import fr.abls.habitat.home.api.ArchiveResponse
import fr.abls.habitat.home.api.HistoList
import fr.abls.habitat.home.api.HistoMsg
import fr.abls.habitat.home.api.SynStatus
import fr.abls.habitat.home.api.Synoptique
import fr.abls.habitat.home.api.Tableau
import fr.abls.habitat.home.api.TableauMap
import fr.abls.habitat.home.api.Visuel
import fr.abls.habitat.home.api.VisuelEtat
import fr.abls.habitat.home.api.toArchiveResponse
import fr.abls.habitat.home.mqtt.MqttMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** État d'un tableau (courbes ou valeurs). */
class TableauState(val tableau: Tableau, val maps: List<TableauMap>) {
    var periode by mutableStateOf(tableau.periode)
    var data by mutableStateOf<ArchiveResponse?>(null)
    var loading by mutableStateOf(false)
    var expanded by mutableStateOf(false)
}

/** Équivalent de home.js + common_home.js + visuels.js : un synoptique et son flux temps réel. */
class HomeStore(private val app: AppContainer, private val scope: CoroutineScope) {
    var synoptique by mutableStateOf<Synoptique?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    /** Dernier état reçu par visuel (clé tech_id:acronyme). */
    val etats = mutableStateMapOf<String, VisuelEtat>()
    /** Bits d'anomalie par synoptique enfant : syn_id -> (bit -> etat). */
    val synBits = mutableStateMapOf<Int, Map<String, Boolean>>()
    val messages = mutableStateListOf<HistoMsg>()
    val tableaux = mutableStateListOf<TableauState>()

    private var mqttJob: Job? = null
    private var refreshJobs = mutableListOf<Job>()

    fun start() {
        if (mqttJob?.isActive == true) return
        app.mqtt.start()
        app.mqtt.subscribe("SYN_STATUS/#")
        mqttJob = scope.launch { app.mqtt.messages.collect { onMqtt(it) } }
    }

    fun stop() {
        mqttJob?.cancel(); mqttJob = null
        refreshJobs.forEach { it.cancel() }; refreshJobs.clear()
        synoptique?.let { unsubscribe(it) }
    }

    private fun unsubscribe(s: Synoptique) {
        app.mqtt.unsubscribe("DLS_VISUEL/${s.page}")
        app.mqtt.unsubscribe("DLS_HISTO/${s.page}")
        app.mqtt.unsubscribe("DLS_HISTO/#")
    }

    fun load(page: String?) {
        scope.launch {
            loading = true; error = null
            try {
                val s: Synoptique = app.api.get("/syn/show", mapOf("syn_page" to page))
                synoptique?.let { unsubscribe(it) }
                synoptique = s
                etats.clear()
                tableaux.clear()
                refreshJobs.forEach { it.cancel() }; refreshJobs.clear()
                app.mqtt.subscribe("DLS_VISUEL/${s.page}")
                if (s.syn_id != 1) app.mqtt.subscribe("DLS_HISTO/${s.page}") else app.mqtt.subscribe("DLS_HISTO/#")
                s.tableaux.forEach { t ->
                    val st = TableauState(t, s.tableaux_map.filter { it.tableau_id == t.tableau_id })
                    tableaux.add(st)
                    loadTableau(st)
                }
                loadMessages(s.page)
            } catch (e: Exception) {
                error = e.message
                app.showError(e.message ?: "Erreur de chargement du synoptique")
            } finally { loading = false }
        }
    }

    fun reload() = load(synoptique?.page)

    private suspend fun loadMessages(page: String) {
        try {
            val list: HistoList = app.api.get("/histo/alive", mapOf("syn_page" to page))
            messages.clear(); messages.addAll(list.histo_msgs.sortedByDescending { it.date_create })
        } catch (e: Exception) { app.showError(e.message ?: "Erreur /histo/alive") }
    }

    fun loadTableau(st: TableauState) {
        scope.launch {
            st.loading = true
            try {
                val req = buildJsonObject {
                    put("courbes", JsonArray(st.maps.map { m ->
                        buildJsonObject { put("tech_id", m.tech_id); put("acronyme", m.acronyme); m.methode?.let { put("methode", it) } }
                    }))
                    put("period", st.periode)
                }
                st.data = app.api.postJson("/archive/get", req).toArchiveResponse(st.maps.size)
            } catch (e: Exception) { app.showError(e.message ?: "Erreur /archive/get") }
            finally { st.loading = false }
            /* Rafraîchissement 60 s pour les courbes BY_HOUR, comme sur le site */
            if (st.periode == "BY_HOUR") {
                refreshJobs.add(launch { delay(60_000); if (isActive) loadTableau(st) })
            }
        }
    }

    fun changePeriode(st: TableauState, periode: String) {
        st.periode = periode
        loadTableau(st)
    }

    private fun onMqtt(m: MqttMessage) {
        val s = synoptique
        when (m.tag) {
            "DLS_VISUEL" -> if (s != null) {
                runCatching { app.api.json.decodeFromString<VisuelEtat>(m.payload) }.getOrNull()?.let { e ->
                    if (s.visuels.any { it.tech_id == e.tech_id && it.acronyme == e.acronyme }) etats["${e.tech_id}:${e.acronyme}"] = e
                }
            }
            "SYN_STATUS" -> {
                val bit = m.target ?: return
                runCatching { app.api.json.decodeFromString<SynStatus>(m.payload) }.getOrNull()?.let { st ->
                    if (st.syn_id == 1) return
                    val old = synBits[st.syn_id] ?: emptyMap()
                    synBits[st.syn_id] = old + (bit to st.etat)
                }
            }
            "DLS_HISTO" -> {
                runCatching { app.api.json.decodeFromString<HistoMsg>(m.payload) }.getOrNull()?.let { msg ->
                    messages.removeAll { it.tech_id == msg.tech_id && it.acronyme == msg.acronyme }
                    if (msg.alive && (s == null || s.page == msg.syn_page)) {
                        messages.add(0, msg)
                    }
                }
            }
        }
    }

    /* ------------------------------------------------ Actions ------------------------------------------------- */
    fun clic(v: Visuel, long: Boolean = false) {
        val etat = etats[v.key]
        if (etat?.disable == true) return
        scope.launch {
            try {
                app.api.postNoResult("/syn/clic", buildJsonObject {
                    put("tech_id", v.tech_id); put("acronyme", v.acronyme); if (long) put("long", true)
                })
            } catch (e: Exception) { app.showError(e.message ?: "Erreur /syn/clic") }
        }
    }

    fun setCadran(v: Visuel, valeur: Double) {
        scope.launch {
            try {
                app.api.postNoResult("/syn/set_cadran", buildJsonObject {
                    put("tech_id", v.tech_id); put("acronyme", v.acronyme); put("valeur", JsonPrimitive(valeur))
                })
                app.showOk("Valeur mise à jour.")
            } catch (e: Exception) { app.showError("Erreur lors de la mise à jour de la valeur.") }
        }
    }

    fun acquitterSynoptique() {
        val s = synoptique ?: return
        scope.launch {
            try {
                app.api.postNoResult("/syn/ack", buildJsonObject { put("syn_page", s.page) })
                app.showOk("Synoptique acquitté")
            } catch (e: Exception) { app.showError(e.message ?: "Erreur /syn/ack") }
        }
    }

    fun acquitterMessage(msg: HistoMsg) {
        scope.launch {
            try {
                app.api.postNoResult("/histo/acquit", buildJsonObject {
                    put("histo_msg_id", msg.histo_msg_id); put("tech_id", msg.tech_id); put("acronyme", msg.acronyme)
                })
                synoptique?.let { loadMessages(it.page) }
            } catch (e: Exception) { app.showError(e.message ?: "Erreur /histo/acquit") }
        }
    }

    /** Vignette d'anomalie d'un synoptique enfant (Set_syn_vars dans passerelles.js). */
    fun vignetteFor(synId: Int): Pair<String, Boolean>? {
        val bits = synBits[synId] ?: return null
        fun on(b: String) = bits[b] == true
        return when {
            on("MEMSSP_DANGER") -> "croix_red.svg" to true
            on("MEMSSB_ALERTE") -> "bouclier_red.svg" to true
            on("MEMSA_ALARME") -> "pignon_red.svg" to true
            on("MEMSA_DEFAUT") -> "pignon_yellow.svg" to true
            on("MEMSSP_DERANGEMENT") -> "croix_orange.svg" to true
            on("MEMSSB_ALERTE_FIXE") -> "bouclier_red.svg" to false
            on("MEMSA_ALARME_FIXE") -> "pignon_red.svg" to false
            on("MEMSA_DEFAUT_FIXE") -> "pignon_yellow.svg" to false
            on("MEMSSP_DANGER_FIXE") -> "croix_red.svg" to false
            on("MEMSSP_DERANGEMENT_FIXE") -> "croix_orange.svg" to false
            else -> null
        }
    }
}

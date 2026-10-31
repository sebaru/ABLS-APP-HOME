package fr.abls.habitat.home.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.abls.habitat.home.AppContainer
import fr.abls.habitat.home.api.HistoList
import fr.abls.habitat.home.api.HistoMsg
import fr.abls.habitat.home.api.MSG_TYPOLOGIES
import fr.abls.habitat.home.ui.components.HistoMsgRow
import fr.abls.habitat.home.ui.theme.AblsColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Équivalent de messages.js : messages au fil de l'eau (tous synoptiques), filtre typologie, acquit, MQTT DLS_HISTO/#. */
@Composable
fun MessagesScreen(app: AppContainer, onOpenPage: (String) -> Unit, onOpenHistorique: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<HistoMsg>() }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }

    suspend fun reload() {
        try {
            val list: HistoList = app.api.get("/histo/alive")
            messages.clear(); messages.addAll(list.histo_msgs.sortedByDescending { it.date_create })
        } catch (e: Exception) { app.showError(e.message ?: "Erreur /histo/alive") }
        finally { loading = false }
    }

    LaunchedEffect(Unit) { reload() }

    DisposableEffect(Unit) {
        app.mqtt.start()
        app.mqtt.subscribe("DLS_HISTO/#")
        val job = scope.launch {
            app.mqtt.messages.collect { m ->
                if (m.tag != "DLS_HISTO") return@collect
                runCatching { app.api.json.decodeFromString<HistoMsg>(m.payload) }.getOrNull()?.let { msg ->
                    messages.removeAll { it.tech_id == msg.tech_id && it.acronyme == msg.acronyme }
                    if (msg.alive) messages.add(0, msg)
                }
            }
        }
        onDispose { job.cancel(); app.mqtt.unsubscribe("DLS_HISTO/#") }
    }

    val counts = remember(messages.size, messages.toList()) { messages.groupingBy { it.typologie }.eachCount() }
    val visible = if (filter == null) messages else messages.filter { it.typologie == filter }

    Column(Modifier.fillMaxSize()) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Messages au fil de l'eau", color = AblsColors.TextWhite, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { menu = true }) {
                    Icon(Icons.Default.FilterList, null, tint = AblsColors.Primary)
                    Spacer(Modifier.width(4.dp))
                    Text(filter?.let { "${MSG_TYPOLOGIES[it].label} (${counts[it] ?: 0})" } ?: "Toutes (${messages.size})")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Toutes (${messages.size})") }, onClick = { filter = null; menu = false })
                    MSG_TYPOLOGIES.forEachIndexed { i, t ->
                        if ((counts[i] ?: 0) > 0 || filter == i)
                            DropdownMenuItem(text = { Text("${t.label} (${counts[i] ?: 0})") }, onClick = { filter = i; menu = false })
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(visible, key = { it.histo_msg_id }) { msg ->
                HistoMsgRow(
                    msg, showPage = true, allowAck = true,
                    onAck = { m ->
                        scope.launch {
                            try {
                                app.api.postNoResult("/histo/acquit", buildJsonObject {
                                    put("histo_msg_id", m.histo_msg_id); put("tech_id", m.tech_id); put("acronyme", m.acronyme)
                                })
                                reload()
                            } catch (e: Exception) { app.showError(e.message ?: "Erreur /histo/acquit") }
                        }
                    },
                    onPageClick = onOpenPage,
                    onObjetClick = onOpenHistorique,
                )
            }
        }
    }
}

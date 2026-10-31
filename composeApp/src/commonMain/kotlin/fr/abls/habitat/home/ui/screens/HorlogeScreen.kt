package fr.abls.habitat.home.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.abls.habitat.home.AppContainer
import fr.abls.habitat.home.api.Horloge
import fr.abls.habitat.home.api.HorlogeList
import fr.abls.habitat.home.api.HorlogeTick
import fr.abls.habitat.home.api.HorlogeTickList
import fr.abls.habitat.home.ui.components.DarkCard
import fr.abls.habitat.home.ui.theme.AblsColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val JOURS = listOf("Lu", "Ma", "Me", "Je", "Ve", "Sa", "Di")

/** Équivalent de horloge.js : édition des ticks (jours + heure) d'une horloge DLS. */
@Composable
fun HorlogeScreen(app: AppContainer, techId: String) {
    val scope = rememberCoroutineScope()
    var horloges by remember { mutableStateOf<List<Horloge>>(emptyList()) }
    val ticks = remember { mutableStateMapOf<Int, List<HorlogeTick>>() }
    var loading by remember { mutableStateOf(true) }

    suspend fun loadTicks(h: Horloge) {
        try { ticks[h.id] = app.api.get<HorlogeTickList>("/api/horloge/ticks/list", mapOf("horloge_id" to h.id.toString())).horloge_ticks }
        catch (e: Exception) { app.showError(e.message ?: "Erreur ticks/list") }
    }

    LaunchedEffect(techId) {
        try {
            horloges = app.api.get<HorlogeList>("/api/horloge/get", mapOf("tech_id" to techId)).horloges
            horloges.forEach { loadTicks(it) }
        } catch (e: Exception) { app.showError(e.message ?: "Erreur /api/horloge/get") }
        finally { loading = false }
    }

    fun setTick(h: Horloge, t: HorlogeTick) {
        scope.launch {
            try {
                app.api.postNoResult("/api/horloge/ticks/set", buildJsonObject {
                    put("id", t.id); put("lundi", t.lundi); put("mardi", t.mardi); put("mercredi", t.mercredi); put("jeudi", t.jeudi)
                    put("vendredi", t.vendredi); put("samedi", t.samedi); put("dimanche", t.dimanche); put("heure", t.heure); put("minute", t.minute)
                })
                loadTicks(h)
            } catch (e: Exception) { app.showError(e.message ?: "Erreur ticks/set") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize()) {
            items(horloges, key = { it.id }) { h ->
                DarkCard(Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h.libelle, color = AblsColors.TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        app.api.postNoResult("/api/horloge/ticks/set", buildJsonObject {
                                            put("horloge_id", h.id)
                                            listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche").forEach { put(it, false) }
                                            put("heure", 12); put("minute", 0)
                                        })
                                        loadTicks(h)
                                    } catch (e: Exception) { app.showError(e.message ?: "Erreur ticks/set") }
                                }
                            }) { Icon(Icons.Default.Add, null); Text("Ajouter") }
                        }
                        HorizontalDivider(color = AblsColors.SurfaceVariant)
                        (ticks[h.id] ?: emptyList()).forEach { t ->
                            TickRow(t, onChange = { setTick(h, it) }, onDelete = {
                                scope.launch {
                                    try {
                                        app.api.deleteNoResult("/api/horloge/ticks/del", buildJsonObject { put("id", t.id) })
                                        loadTicks(h)
                                    } catch (e: Exception) { app.showError(e.message ?: "Erreur ticks/del") }
                                }
                            })
                            HorizontalDivider(color = AblsColors.SurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TickRow(t: HorlogeTick, onChange: (HorlogeTick) -> Unit, onDelete: () -> Unit) {
    var heure by remember(t.id, t.heure, t.minute) { mutableStateOf("${t.heure.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}") }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            val flags = listOf(t.lundi, t.mardi, t.mercredi, t.jeudi, t.vendredi, t.samedi, t.dimanche)
            flags.forEachIndexed { i, on ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(JOURS[i], color = AblsColors.TextWhite, fontSize = 10.sp)
                    Checkbox(checked = on, onCheckedChange = { v ->
                        onChange(when (i) {
                            0 -> t.copy(lundi = v); 1 -> t.copy(mardi = v); 2 -> t.copy(mercredi = v); 3 -> t.copy(jeudi = v)
                            4 -> t.copy(vendredi = v); 5 -> t.copy(samedi = v); else -> t.copy(dimanche = v)
                        })
                    })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                heure, { heure = it }, label = { Text("Heure (HH:MM)") }, singleLine = true,
                modifier = Modifier.width(150.dp).height(60.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                val parts = heure.split(':')
                val hh = parts.getOrNull(0)?.trim()?.toIntOrNull(); val mm = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (hh != null && mm != null && hh in 0..23 && mm in 0..59) onChange(t.copy(heure = hh, minute = mm))
            }) { Text("OK") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Effacer", tint = AblsColors.Danger) }
        }
    }
}

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.abls.habitat.home.AppContainer
import fr.abls.habitat.home.api.HistoList
import fr.abls.habitat.home.api.HistoMsg
import fr.abls.habitat.home.ui.components.HistoMsgRow
import fr.abls.habitat.home.ui.theme.AblsColors
import kotlinx.coroutines.launch

private val PERIODES = listOf("1d" to "1 jour", "1w" to "1 semaine", "1m" to "1 mois", "3m" to "3 mois", "6m" to "6 mois", "12m" to "1 an")

/** Équivalent de historique.js : recherche dans l'historique des messages. */
@Composable
fun HistoriqueScreen(app: AppContainer, initialSearch: String?, onOpenPage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialSearch ?: "") }
    var periode by remember { mutableStateOf("1d") }
    var menu by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<HistoMsg>?>(null) }

    fun search() {
        scope.launch {
            loading = true
            try {
                val list: HistoList = app.api.get("/histo/search", mapOf("search" to query, "period" to periode))
                results = list.histo_msgs
            } catch (e: Exception) { app.showError(e.message ?: "Erreur /histo/search") }
            finally { loading = false }
        }
    }

    LaunchedEffect(initialSearch) { if (!initialSearch.isNullOrBlank()) search() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it }, label = { Text("Recherche à effectuer") }, singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
            )
            Spacer(Modifier.width(6.dp))
            Box {
                OutlinedButton(onClick = { menu = true }) { Text(PERIODES.first { it.first == periode }.second) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    PERIODES.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { periode = v; menu = false; if (query.isNotBlank()) search() }) }
                }
            }
            Spacer(Modifier.width(6.dp))
            Button(onClick = { search() }) { Icon(Icons.Default.Search, null) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        results?.let { list ->
            Text("${list.size} résultat(s)", color = AblsColors.Secondary, modifier = Modifier.padding(horizontal = 8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { "${it.histo_msg_id}-${it.date_create}" }) { msg ->
                    HistoMsgRow(msg, showPage = true, allowAck = false, onAck = null, onPageClick = onOpenPage)
                }
            }
        }
    }
}

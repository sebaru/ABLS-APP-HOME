package fr.abls.habitat.home.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.abls.habitat.home.AppContainer
import fr.abls.habitat.home.api.PERIODES_TABLEAU
import fr.abls.habitat.home.api.STATIC_ABLS
import fr.abls.habitat.home.api.Visuel
import fr.abls.habitat.home.mqtt.MqttState
import fr.abls.habitat.home.ui.components.DarkCard
import fr.abls.habitat.home.ui.components.HistoMsgRow
import fr.abls.habitat.home.ui.components.RemoteImage
import fr.abls.habitat.home.ui.components.SectionTitle
import fr.abls.habitat.home.ui.theme.AblsColors
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    app: AppContainer,
    store: HomeStore,
    initialPage: String?,
    onNavigateHorloge: (techId: String) -> Unit,
    onBreadcrumbChange: (String) -> Unit,
) {
    val session = app.session
    val syn = store.synoptique
    val mqttState by app.mqtt.state.collectAsState()
    var cadranTarget by remember { mutableStateOf<Visuel?>(null) }

    LaunchedEffect(initialPage) { store.load(initialPage) }
    LaunchedEffect(syn?.libelle) { syn?.let { onBreadcrumbChange(it.libelle) } }

    DisposableEffect(Unit) {
        store.start()
        onDispose { store.stop() }
    }

    Column(Modifier.fillMaxSize()) {
        if (store.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (mqttState != MqttState.CONNECTED) {
            Text(
                if (mqttState == MqttState.CONNECTING) "Connexion temps réel en cours..." else "Connexion perdue !",
                color = AblsColors.Warning, modifier = Modifier.fillMaxWidth().background(AblsColors.SurfaceVariant).padding(6.dp),
                textAlign = TextAlign.Center, fontSize = 12.sp,
            )
        }
        if (syn == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(store.error ?: "Chargement du synoptique...", color = AblsColors.TextWhite)
            }
            return
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            /* ---------------------------------- Fil d'Ariane ---------------------------------- */
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    syn.parent_syns.reversed().forEach { p ->
                        RemoteImage(
                            p.image?.let { session.imgUrl(it) }, Modifier.size(36.dp).clickable { store.load(p.page) },
                            contentDescription = p.libelle,
                        )
                        Icon(Icons.Default.ChevronRight, null, tint = AblsColors.Secondary)
                    }
                    Text(syn.libelle, color = AblsColors.TextWhite, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (syn.mode_affichage) {
                        TextButton(onClick = { store.acquitterSynoptique() }) { Text("Acquitter") }
                    }
                }
            }

            /* ---------------------------------- Passerelles + horloges -------------------------- */
            if (syn.child_syns.isNotEmpty() || syn.horloges.isNotEmpty()) item {
                FlowRow(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.Center) {
                    syn.child_syns.forEach { child ->
                        val vignette = store.vignetteFor(child.syn_id)
                        Column(
                            Modifier.width(120.dp).padding(6.dp).clickable { store.load(child.page) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(100.dp)) {
                                RemoteImage(
                                    if (child.image == "custom") child.image else child.image?.let { session.imgUrl(it) },
                                    Modifier.size(100.dp), contentDescription = child.libelle,
                                )
                                vignette?.let { (img, blink) ->
                                    RemoteImage("$STATIC_ABLS/img/$img", Modifier.size(34.dp).align(Alignment.TopEnd), blink = blink)
                                }
                            }
                            Text(child.libelle, color = AblsColors.TextWhite, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                    syn.horloges.forEach { h ->
                        Column(
                            Modifier.width(120.dp).padding(6.dp).clickable { onNavigateHorloge(h.tech_id) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RemoteImage("$STATIC_ABLS/img/syn_horloge.svg", Modifier.size(100.dp))
                            Text(h.dls_name, color = AblsColors.TextWhite, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }

            /* ---------------------------------- Caméras ----------------------------------------- */
            items(syn.cameras, key = { "cam-${it.syn_camera_id}" }) { cam ->
                CameraCard(cam, app.http, app.auth.accessToken, Modifier.fillMaxWidth())
            }

            /* ---------------------------------- Visuels (mode léger, ou lourd replié en grille) -- */
            if (syn.visuels.isNotEmpty()) item {
                if (syn.mode_affichage) {
                    Text(
                        "Synoptique « ${syn.page} » affiché en mode grille (mode plan non supporté sur mobile)",
                        color = AblsColors.Secondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                val visibles = syn.visuels.filter { v ->
                    !(v.controle == "complexe" && v.forme in setOf("encadre", "comment"))
                }
                FlowRow(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.Center) {
                    visibles.forEach { v ->
                        VisuelCard(
                            session, v, store.etats[v.key],
                            onClick = {
                                if (v.controle == "complexe" && v.forme == "cadran" && v.rw) cadranTarget = v else store.clic(v)
                            },
                            onLongClick = { store.clic(v, long = true) },
                            modifier = Modifier.width(130.dp),
                        )
                    }
                }
            }

            /* ---------------------------------- Tableaux ---------------------------------------- */
            items(store.tableaux, key = { "tab-${it.tableau.tableau_id}" }) { st -> TableauCard(st, store) }

            /* ---------------------------------- Messages ---------------------------------------- */
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = AblsColors.SurfaceVariant)
                SectionTitle("Messages (${store.messages.size})")
            }
            items(store.messages, key = { "msg-${it.histo_msg_id}" }) { msg ->
                HistoMsgRow(msg, showPage = false, allowAck = true, onAck = { store.acquitterMessage(it) })
            }
        }
    }

    cadranTarget?.let { v ->
        CadranDialog(v, store.etats[v.key], onDismiss = { cadranTarget = null }) { valeur ->
            store.setCadran(v, valeur); cadranTarget = null
        }
    }
}

@Composable
fun TableauCard(st: TableauState, store: HomeStore) {
    var menu by remember { mutableStateOf(false) }
    DarkCard(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.tableau.titre ?: "Tableau", color = AblsColors.TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!st.tableau.period_lock) {
                    Box {
                        TextButton(onClick = { menu = true }) {
                            Icon(Icons.Default.Schedule, null, tint = AblsColors.Primary)
                            Spacer(Modifier.width(4.dp))
                            Text(PERIODES_TABLEAU.firstOrNull { it.first == st.periode }?.second ?: st.periode, fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            PERIODES_TABLEAU.forEach { (value, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; store.changePeriode(st, value) })
                            }
                        }
                    }
                }
            }
            if (st.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val data = st.data
            if (data == null) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Text("Chargement...", color = AblsColors.Secondary) }
            } else if (st.tableau.mode == 1) {
                ValuesTable(data)
            } else {
                LineChart(data, st.maps.mapIndexed { i, m -> parseCssColor(m.color, i) })
            }
        }
    }
}

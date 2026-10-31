package fr.abls.habitat.home.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import fr.abls.habitat.home.AppContainer
import fr.abls.habitat.home.api.Domain
import fr.abls.habitat.home.api.DomainList
import fr.abls.habitat.home.api.STATIC_ABLS
import fr.abls.habitat.home.ui.components.AccessLevelBadge
import fr.abls.habitat.home.ui.components.DarkCard
import fr.abls.habitat.home.ui.components.RemoteImage
import fr.abls.habitat.home.ui.theme.AblsColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Équivalent de domains.js : choix du domaine par défaut. */
@Composable
fun DomainsScreen(app: AppContainer, onDomainChosen: () -> Unit) {
    var domains by remember { mutableStateOf<List<Domain>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try { domains = app.api.get<DomainList>("/domain/list").domains }
        catch (e: Exception) { app.showError(e.message ?: "Erreur /domain/list") }
        finally { loading = false }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        if (app.session.domainUuid == null) {
            Text("Vous devez choisir un domaine par défaut ou créer votre domaine.", color = AblsColors.Info, modifier = Modifier.padding(8.dp))
        }
        if (!loading && domains.isEmpty()) Text("Aucun domaine disponible.", color = AblsColors.TextWhite, modifier = Modifier.padding(8.dp))
        LazyVerticalGrid(GridCells.Adaptive(160.dp), contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(domains, key = { it.domain_uuid }) { d ->
                DarkCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccessLevelBadge(d.access_level)
                            Spacer(Modifier.width(6.dp))
                            Text(d.domain_name, color = AblsColors.TextWhite, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(8.dp))
                        RemoteImage(d.image ?: "$STATIC_ABLS/img/syn_accueil.png", Modifier.fillMaxWidth().height(110.dp).clickable { choose(app, d, scope, onDomainChosen) })
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { choose(app, d, scope, onDomainChosen) }) { Text("Choisir") }
                    }
                }
            }
        }
    }
}

private fun choose(app: AppContainer, d: Domain, scope: kotlinx.coroutines.CoroutineScope, onDone: () -> Unit) {
    app.session.setDomain(d.domain_uuid, d.domain_name)
    scope.launch {
        try {
            app.api.postNoResult("/user/set_domain", buildJsonObject {
                put("domain_uuid", d.domain_uuid); put("domain_name", d.domain_name); put("access_level", d.access_level)
            })
            onDone()
        } catch (e: Exception) { app.showError(e.message ?: "Erreur /user/set_domain") }
    }
}

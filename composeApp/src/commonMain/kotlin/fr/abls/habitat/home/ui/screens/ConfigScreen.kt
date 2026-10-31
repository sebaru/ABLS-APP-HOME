package fr.abls.habitat.home.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.abls.habitat.home.api.STATIC_ABLS
import fr.abls.habitat.home.auth.AuthState
import fr.abls.habitat.home.auth.REDIRECT_URI
import fr.abls.habitat.home.config.AppConfig
import fr.abls.habitat.home.ui.components.RemoteImage
import fr.abls.habitat.home.ui.theme.AblsColors

/** Équivalent de config.html : saisie des URLs API / IDP. */
@Composable
fun ConfigScreen(initial: AppConfig, onSave: (AppConfig) -> Unit, onCancel: (() -> Unit)?) {
    var api by remember { mutableStateOf(initial.apiUrl) }
    var idp by remember { mutableStateOf(initial.idpUrl) }
    var realm by remember { mutableStateOf(initial.idpRealm) }
    var client by remember { mutableStateOf(initial.idpClientId) }
    val cfg = AppConfig(api.trim(), idp.trim(), realm.trim(), client.trim())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RemoteImage("$STATIC_ABLS/img/abls.svg", Modifier.size(96.dp))
        Spacer(Modifier.height(8.dp))
        Text("Configuration Abls-Habitat", color = AblsColors.TextWhite, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(api, { api = it }, label = { Text("URL de l'API (https://api.exemple.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(idp, { idp = it }, label = { Text("URL de l'IDP Keycloak (https://idp.exemple.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(realm, { realm = it }, label = { Text("Realm Keycloak") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(client, { client = it }, label = { Text("Client ID Keycloak (public, PKCE)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            "Le client Keycloak doit autoriser la redirect URI : $REDIRECT_URI",
            color = AblsColors.Secondary, textAlign = TextAlign.Center, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(cfg) }, enabled = cfg.isValid, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }
        onCancel?.let { TextButton(onClick = it) { Text("Annuler") } }
    }
}

@Composable
fun LoginScreen(state: AuthState, domainName: String?, onLogin: () -> Unit, onConfigure: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        RemoteImage("$STATIC_ABLS/img/abls.svg", Modifier.size(120.dp))
        Spacer(Modifier.height(16.dp))
        Text("Abls-Habitat Home", color = AblsColors.TextWhite, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        domainName?.let { Text(it, color = AblsColors.Secondary) }
        Spacer(Modifier.height(24.dp))
        when (state) {
            AuthState.InProgress -> { CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("Authentification en cours dans le navigateur...", color = AblsColors.TextWhite, textAlign = TextAlign.Center) }
            is AuthState.Error -> { Text(state.message, color = AblsColors.Danger, textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)); Button(onClick = onLogin) { Text("Réessayer") } }
            else -> Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Se connecter") }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onConfigure) { Text("Paramètres de connexion") }
    }
}

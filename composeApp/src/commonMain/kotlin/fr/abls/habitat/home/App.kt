package fr.abls.habitat.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import fr.abls.habitat.home.api.DomainImage
import fr.abls.habitat.home.api.Profil
import fr.abls.habitat.home.api.STATIC_ABLS
import fr.abls.habitat.home.auth.AuthState
import fr.abls.habitat.home.config.AppConfig
import fr.abls.habitat.home.config.ConfigStore
import fr.abls.habitat.home.platform.GpsWatcher
import fr.abls.habitat.home.platform.openExternalUrl
import fr.abls.habitat.home.platform.platformDecoders
import fr.abls.habitat.home.ui.components.RemoteImage
import fr.abls.habitat.home.ui.screens.ConfigScreen
import fr.abls.habitat.home.ui.screens.DomainsScreen
import fr.abls.habitat.home.ui.screens.HistoriqueScreen
import fr.abls.habitat.home.ui.screens.HomeScreen
import fr.abls.habitat.home.ui.screens.HomeStore
import fr.abls.habitat.home.ui.screens.HorlogeScreen
import fr.abls.habitat.home.ui.screens.LoginScreen
import fr.abls.habitat.home.ui.screens.MessagesScreen
import fr.abls.habitat.home.ui.theme.AblsColors
import fr.abls.habitat.home.ui.theme.AblsTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Routes (équivalent de router.js). */
sealed interface Route {
    data class Home(val page: String? = null) : Route
    data object Domains : Route
    data object Messages : Route
    data class Historique(val search: String? = null) : Route
    data class Horloge(val techId: String) : Route
    data object Settings : Route
}

sealed interface Phase {
    data object NeedsConfig : Phase
    data object Login : Phase
    data object LoadingProfil : Phase
    data object Ready : Phase
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(app: AppContainer = AppContainer.instance) {
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader.Builder(ctx)
            .components { add(KtorNetworkFetcherFactory(app.http)); add(SvgDecoder.Factory()) }
            .platformDecoders()
            .crossfade(true)
            .build()
    }

    AblsTheme {
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        var config by remember { mutableStateOf(app.session.config) }
        var phase by remember { mutableStateOf<Phase>(if (config.isValid) Phase.Login else Phase.NeedsConfig) }
        val authState by app.auth.state.collectAsState()
        val backStack = remember { mutableStateOf<List<Route>>(listOf(Route.Home())) }
        val route = backStack.value.last()
        var title by remember { mutableStateOf("Abls-Habitat") }
        var menuOpen by remember { mutableStateOf(false) }
        val homeStore = remember { HomeStore(app, app.scope) }

        fun navigate(r: Route) { backStack.value = backStack.value + r }
        fun back() { if (backStack.value.size > 1) backStack.value = backStack.value.dropLast(1) }
        fun home() { backStack.value = listOf(Route.Home()) }

        LaunchedEffect(Unit) {
            app.toasts.collect { t ->
                snackbar.showSnackbar(t.message, duration = if (t.isError) SnackbarDuration.Long else SnackbarDuration.Short)
            }
        }

        /* Reprise de session au démarrage, puis chargement du profil (Load_common dans common.js). */
        LaunchedEffect(phase, authState) {
            if (phase == Phase.Login && authState == AuthState.Idle) {
                if (app.auth.restore(config)) phase = Phase.LoadingProfil
            }
            if (authState is AuthState.Authenticated && (phase == Phase.Login || phase == Phase.LoadingProfil)) {
                phase = Phase.LoadingProfil
                try {
                    val p: Profil = app.api.get("/user/profil")
                    app.session.applyProfil(p)
                    if (p.default_domain_uuid == null) backStack.value = listOf(Route.Domains)
                    else runCatching { app.api.get<DomainImage>("/domain/image").image }.getOrNull().let { app.session.domainImage = it }
                    phase = Phase.Ready
                } catch (e: Exception) {
                    app.showError("Unable to request profil. ${e.message ?: ""}")
                    phase = Phase.Login
                }
            }
            if (authState is AuthState.Error && phase == Phase.Ready) { phase = Phase.Login; app.mqtt.stop() }
        }

        when (phase) {
            Phase.NeedsConfig -> ConfigScreen(config, onSave = { c -> ConfigStore.save(c); config = c; app.session.config = c; phase = Phase.Login }, onCancel = null)
            Phase.Login -> LoginScreen(authState, app.session.domainName, onLogin = { scope.launch { app.auth.login(config) } }, onConfigure = { phase = Phase.NeedsConfig })
            Phase.LoadingProfil -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.padding(8.dp)); Text("Chargement du profil...", color = AblsColors.TextWhite) }
            }
            Phase.Ready -> {
                val tokens = (authState as? AuthState.Authenticated)?.tokens
                val session = app.session

                /* Envoi GPS (Load_page de home.js) */
                GpsWatcher(enabled = true) { pos ->
                    scope.launch {
                        runCatching {
                            app.api.postNoResult("/user/set_gps", buildJsonObject {
                                put("latitude", JsonPrimitive(pos.latitude)); put("longitude", JsonPrimitive(pos.longitude))
                                pos.accuracy?.let { put("accuracy", JsonPrimitive(it)) }
                            })
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = AblsColors.Surface),
                            navigationIcon = {
                                if (backStack.value.size > 1) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                                else RemoteImage(session.domainImage ?: "$STATIC_ABLS/img/syn_maison.png", Modifier.size(40.dp).padding(start = 8.dp))
                            },
                            title = {
                                Column {
                                    Text(if (route is Route.Home) title else routeTitle(route), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(session.domainName ?: "", fontSize = 11.sp, color = AblsColors.Secondary, maxLines = 1)
                                }
                            },
                            actions = {
                                if (route is Route.Home) IconButton(onClick = { homeStore.reload() }) { Icon(Icons.Default.Refresh, "Recharger") }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, "Menu") }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        Text(tokens?.displayName ?: "", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = AblsColors.Secondary, fontSize = 12.sp)
                                        HorizontalDivider()
                                        MenuItem("Accueil", Icons.Default.Home) { menuOpen = false; home() }
                                        MenuItem("Messages", Icons.Default.Notifications) { menuOpen = false; navigate(Route.Messages) }
                                        MenuItem("Historique", Icons.Default.History) { menuOpen = false; navigate(Route.Historique()) }
                                        MenuItem("Domaines", Icons.Default.Domain) { menuOpen = false; navigate(Route.Domains) }
                                        HorizontalDivider()
                                        if (session.accessLevel >= 6 && session.profil?.console_url != null)
                                            MenuItem("Console", Icons.Default.Build) { menuOpen = false; openExternalUrl(session.profil!!.console_url!!) }
                                        tokens?.issuer?.let { iss -> MenuItem("Mon compte", Icons.Default.AccountCircle) { menuOpen = false; openExternalUrl("$iss/account/") } }
                                        MenuItem("Paramètres", Icons.Default.Settings) { menuOpen = false; navigate(Route.Settings) }
                                        HorizontalDivider()
                                        MenuItem("Déconnexion", Icons.Default.Logout) {
                                            menuOpen = false
                                            scope.launch { app.mqtt.stop(); app.auth.logout(); phase = Phase.Login; home() }
                                        }
                                    }
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        session.profil?.domain_notification?.takeIf { it.isNotBlank() }?.let { NotificationBar(it, AblsColors.Info) }
                        session.profil?.domain_notification_warning?.takeIf { it.isNotBlank() }?.let { NotificationBar(it, AblsColors.Warning) }
                        when (val r = route) {
                            is Route.Home -> HomeScreen(
                                app, homeStore, r.page,
                                onNavigateHorloge = { navigate(Route.Horloge(it)) },
                                onBreadcrumbChange = { title = it },
                            )
                            Route.Domains -> DomainsScreen(app) {
                                scope.launch {
                                    runCatching { app.api.get<Profil>("/user/profil") }.getOrNull()?.let { app.session.applyProfil(it) }
                                    app.mqtt.stop(); home()
                                }
                            }
                            Route.Messages -> MessagesScreen(app, onOpenPage = { backStack.value = listOf(Route.Home(it)) }, onOpenHistorique = { navigate(Route.Historique("\"$it\"")) })
                            is Route.Historique -> HistoriqueScreen(app, r.search, onOpenPage = { backStack.value = listOf(Route.Home(it)) })
                            is Route.Horloge -> HorlogeScreen(app, r.techId)
                            Route.Settings -> ConfigScreen(config, onSave = { c ->
                                ConfigStore.save(c); config = c; app.session.config = c
                                scope.launch { app.mqtt.stop(); app.auth.logout(); phase = Phase.Login; home() }
                            }, onCancel = { back() })
                        }
                    }
                }
            }
        }
    }
}

private fun routeTitle(r: Route) = when (r) {
    is Route.Home -> "Accueil"
    Route.Domains -> "Domaines"
    Route.Messages -> "Messages"
    is Route.Historique -> "Historique"
    is Route.Horloge -> "Horloge"
    Route.Settings -> "Paramètres"
}

@Composable
private fun MenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun NotificationBar(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, fontSize = 13.sp)
    }
}

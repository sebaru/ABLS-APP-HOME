package fr.abls.habitat.home.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import com.russhwolf.settings.Settings

/** Stockage clé/valeur persistant (SharedPreferences / NSUserDefaults). */
expect fun createSettings(name: String): Settings

/** Ouvre l'URL d'autorisation OIDC dans le navigateur système. Le retour arrive via [AuthRedirectBus]. */
expect fun openAuthUrl(url: String, redirectScheme: String)

/** Ouvre une URL externe (console, compte Keycloak...). */
expect fun openExternalUrl(url: String)

/** Lecteur vidéo natif pour les flux HLS / MP4 (ExoPlayer sur Android, AVPlayer sur iOS). */
@Composable
expect fun NativeVideoPlayer(url: String, bearerToken: String?, modifier: Modifier = Modifier)

/** Position GPS. */
data class GpsPosition(val latitude: Double, val longitude: Double, val accuracy: Double?)

/** Démarre l'écoute GPS (demande la permission si nécessaire) et appelle [onPosition] à chaque mise à jour. */
@Composable
expect fun GpsWatcher(enabled: Boolean, onPosition: (GpsPosition) -> Unit)

expect val platformName: String

expect fun currentTimeMillis(): Long

/** Ajoute les décodeurs spécifiques plateforme (GIF animé sur Android). */
expect fun ImageLoader.Builder.platformDecoders(): ImageLoader.Builder

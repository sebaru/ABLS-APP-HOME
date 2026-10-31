package fr.abls.habitat.home.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import fr.abls.habitat.home.AblsApplication

actual fun createSettings(name: String): Settings =
    SharedPreferencesSettings(AblsApplication.context.getSharedPreferences(name, Context.MODE_PRIVATE))

actual fun openAuthUrl(url: String, redirectScheme: String) {
    val ctx = AblsApplication.context
    val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
    intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.launchUrl(ctx, Uri.parse(url))
}

actual fun openExternalUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    AblsApplication.context.startActivity(intent)
}

@SuppressLint("UnsafeOptInUsageError")
@OptIn(UnstableApi::class)
@Composable
actual fun NativeVideoPlayer(url: String, bearerToken: String?, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(url, bearerToken) {
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        bearerToken?.let { httpFactory.setDefaultRequestProperties(mapOf("Authorization" to "Bearer $it")) }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build().apply {
                setMediaItem(MediaItem.fromUri(url))
                volume = 0f
                playWhenReady = true
                repeatMode = ExoPlayer.REPEAT_MODE_OFF
                prepare()
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> PlayerView(ctx).apply { useController = true; this.player = player } },
        update = { it.player = player },
    )
}

@Composable
actual fun GpsWatcher(enabled: Boolean, onPosition: (GpsPosition) -> Unit) {
    if (!enabled) return
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPosition)
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.any { it }
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    if (!granted) return
    DisposableEffect(granted) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                callback(GpsPosition(location.latitude, location.longitude, location.accuracy.toDouble()))
            }
            @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            /* maximumAge 10 min / low accuracy comme dans home.js : réseau en priorité, GPS en secours */
            val provider = when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> null
            }
            provider?.let {
                lm.getLastKnownLocation(it)?.let { loc -> callback(GpsPosition(loc.latitude, loc.longitude, loc.accuracy.toDouble())) }
                lm.requestLocationUpdates(it, 600_000L, 50f, listener, Looper.getMainLooper())
            }
        } catch (_: SecurityException) { }
        onDispose { lm.removeUpdates(listener) }
    }
}

actual val platformName: String = "Android ${android.os.Build.VERSION.SDK_INT}"

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun ImageLoader.Builder.platformDecoders(): ImageLoader.Builder = components { add(AnimatedImageDecoder.Factory()) }

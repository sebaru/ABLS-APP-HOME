package fr.abls.habitat.home.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import coil3.ImageLoader
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import fr.abls.habitat.home.auth.AuthRedirectBus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.darwin.NSObject

actual fun createSettings(name: String): Settings =
    NSUserDefaultsSettings(NSUserDefaults(suiteName = name))

private var authSession: ASWebAuthenticationSession? = null
private val presentationProvider = object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow ?: ASPresentationAnchor()
}

actual fun openAuthUrl(url: String, redirectScheme: String) {
    val session = ASWebAuthenticationSession(
        uRL = NSURL(string = url),
        callbackURLScheme = redirectScheme,
    ) { callbackUrl, _ ->
        callbackUrl?.absoluteString?.let { AuthRedirectBus.emit(it) }
        authSession = null
    }
    session.presentationContextProvider = presentationProvider
    session.prefersEphemeralWebBrowserSession = false
    authSession = session
    session.start()
}

actual fun openExternalUrl(url: String) {
    NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null) }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeVideoPlayer(url: String, bearerToken: String?, modifier: Modifier) {
    val controller = remember(url, bearerToken) {
        val options = bearerToken?.let { mapOf<Any?, Any>("AVURLAssetHTTPHeaderFieldsKey" to mapOf("Authorization" to "Bearer $it")) }
        val asset = AVURLAsset(uRL = NSURL(string = url), options = options)
        val player = AVPlayer(playerItem = AVPlayerItem(asset = asset)).apply { muted = true }
        AVPlayerViewController().apply { this.player = player; showsPlaybackControls = true }
    }
    DisposableEffect(controller) {
        controller.player?.play()
        onDispose { controller.player?.pause(); controller.player = null }
    }
    UIKitView(factory = { controller.view }, modifier = modifier)
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun GpsWatcher(enabled: Boolean, onPosition: (GpsPosition) -> Unit) {
    if (!enabled) return
    val callback by rememberUpdatedState(onPosition)
    DisposableEffect(Unit) {
        val manager = CLLocationManager()
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val loc = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                loc.coordinate.useContents { callback(GpsPosition(latitude, longitude, loc.horizontalAccuracy)) }
            }
        }
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.distanceFilter = 50.0
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
        onDispose { manager.stopUpdatingLocation(); manager.delegate = null }
    }
}

actual val platformName: String = "iOS ${UIDevice.currentDevice.systemVersion}"

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun ImageLoader.Builder.platformDecoders(): ImageLoader.Builder = this

package fr.abls.habitat.home.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.abls.habitat.home.api.Camera
import fr.abls.habitat.home.platform.NativeVideoPlayer
import fr.abls.habitat.home.platform.currentTimeMillis
import fr.abls.habitat.home.ui.theme.AblsColors
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

fun Camera.isMjpeg(): Boolean = listOf("format=mjpeg", "format=mjpg", ".mjpeg", ".mjpg", "mjpeg").any { url.contains(it) }

@Composable
fun CameraCard(camera: Camera, http: HttpClient, bearerToken: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(AblsColors.Surface)) {
            if (camera.isMjpeg()) MjpegView(camera.url, http, Modifier.fillMaxSize())
            else NativeVideoPlayer(camera.url, bearerToken, Modifier.fillMaxSize())
        }
        Text(camera.camera_name, color = AblsColors.TextWhite, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

/** Lecture d'un flux MJPEG multipart : extraction des images JPEG (FFD8...FFD9) et affichage. */
@Composable
fun MjpegView(url: String, http: HttpClient, modifier: Modifier = Modifier) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        while (isActive) {
            try {
                val src = url + (if (url.contains("?")) "&" else "?") + "t=" + currentTimeMillis()
                http.prepareGet(src).execute { resp ->
                    val channel = resp.bodyAsChannel()
                    var buffer = ByteArray(0)
                    val chunk = ByteArray(64 * 1024)
                    while (isActive) {
                        val n = channel.readAvailable(chunk, 0, chunk.size)
                        if (n < 0) break
                        if (n == 0) continue
                        buffer += chunk.copyOf(n)
                        /* Cherche le dernier JPEG complet dans le buffer */
                        var soi = indexOfMarker(buffer, 0xD8, 0)
                        while (soi >= 0) {
                            val eoi = indexOfMarker(buffer, 0xD9, soi + 2)
                            if (eoi < 0) break
                            val jpeg = buffer.copyOfRange(soi, eoi + 2)
                            buffer = buffer.copyOfRange(eoi + 2, buffer.size)
                            val bmp = withContext(Dispatchers.Default) { runCatching { jpeg.decodeToImageBitmap() }.getOrNull() }
                            if (bmp != null) { frame = bmp; error = null }
                            soi = indexOfMarker(buffer, 0xD8, 0)
                        }
                        if (buffer.size > 4 * 1024 * 1024) buffer = ByteArray(0)
                    }
                }
            } catch (e: Exception) {
                error = e.message
            }
            delay(5000)
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        frame?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: if (error != null) Text("Flux indisponible", color = AblsColors.Secondary) else CircularProgressIndicator()
    }
}

private fun indexOfMarker(buf: ByteArray, second: Int, from: Int): Int {
    var i = maxOf(from, 0)
    while (i < buf.size - 1) {
        if (buf[i] == 0xFF.toByte() && (buf[i + 1].toInt() and 0xff) == second) return i
        i++
    }
    return -1
}

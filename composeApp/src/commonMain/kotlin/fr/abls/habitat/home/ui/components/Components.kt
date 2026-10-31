package fr.abls.habitat.home.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.abls.habitat.home.ui.theme.AblsColors
import fr.abls.habitat.home.ui.theme.bootstrapColor

/** Image distante (PNG/SVG/GIF) : remplace les <img src="static/img/..."> du site. */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    grayscale: Boolean = false,
    blink: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (url.isNullOrBlank()) { Box(modifier); return }
    val filter = if (grayscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier.then(if (blink) Modifier.blink() else Modifier),
        colorFilter = filter,
        contentScale = contentScale,
    )
}

/** Équivalent de la classe CSS wtd-cligno. */
@Composable
fun Modifier.blink(): Modifier {
    val transition = rememberInfiniteTransition(label = "cligno")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "cligno-alpha",
    )
    return this.alpha(alpha)
}

/** Clic court / clic long (1 s), comme sur les visuels du synoptique. */
fun Modifier.clickAndLongClick(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    pointerInput(onClick, onLongClick) {
        detectTapGestures(
            onTap = { onClick() },
            onLongPress = { onLongClick?.invoke() },
        )
    }

@Composable
fun Badge(colorKey: String, text: String, modifier: Modifier = Modifier) {
    val bg = bootstrapColor(colorKey)
    val fg = if (colorKey == "warning" || colorKey == "info") Color.Black else Color.White
    Text(
        text,
        modifier = modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
    )
}

private val ACCESS_LEVELS = listOf(
    "Accès de niveau 0" to "success", "Accès de niveau 1" to "info", "Accès de niveau 2" to "info",
    "Accès de niveau 3" to "info", "Accès de niveau 4" to "secondary", "Accès de niveau 5" to "primary",
    "Technicien délégué du domaine" to "warning", "Technicien du domaine" to "warning",
    "Administrateur délégué du domaine" to "warning", "Administrateur du domaine" to "danger",
)

@Composable
fun AccessLevelBadge(level: Int) {
    val (_, color) = ACCESS_LEVELS.getOrElse(level) { "?" to "secondary" }
    Badge(color, level.toString())
}

fun accessLevelName(level: Int) = ACCESS_LEVELS.getOrNull(level)?.first ?: "Niveau $level"

@Composable
fun DarkCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = AblsColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        content = { content() },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = AblsColors.TextWhite, modifier = modifier.padding(8.dp))
}

@Composable
fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AblsColors.Secondary, modifier = Modifier.width(120.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, color = AblsColors.TextWhite)
    }
}

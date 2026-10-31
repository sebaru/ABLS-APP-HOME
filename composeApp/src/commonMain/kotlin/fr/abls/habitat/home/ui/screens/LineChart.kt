package fr.abls.habitat.home.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import fr.abls.habitat.home.api.ArchiveResponse
import fr.abls.habitat.home.ui.theme.AblsColors

private val PALETTE = listOf(Color(0xFF0064FF), Color(0xFFFF6384), Color(0xFF4BC0C0), Color(0xFFFFCE56), Color(0xFF9966FF), Color(0xFFFF9F40))

fun parseCssColor(s: String?, index: Int): Color {
    if (s == null) return PALETTE[index % PALETTE.size]
    val hex = s.trim().removePrefix("#")
    return runCatching {
        when (hex.length) {
            6 -> Color(("ff$hex").toLong(16))
            8 -> Color(hex.toLong(16))
            else -> PALETTE[index % PALETTE.size]
        }
    }.getOrDefault(PALETTE[index % PALETTE.size])
}

/** Graphe en lignes multi-courbes (remplace Chart.js). */
@Composable
fun LineChart(data: ArchiveResponse, colors: List<Color>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            data.courbes.forEachIndexed { i, c ->
                Spacer(Modifier.size(10.dp).background(colors.getOrElse(i) { PALETTE[i % PALETTE.size] }, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text("${c.libelle ?: "Courbe ${i + 1}"} (${c.unite ?: ""})", color = AblsColors.TextWhite, fontSize = 11.sp)
                Spacer(Modifier.width(12.dp))
            }
        }
        val all = data.valeurs.flatMap { it.valeurs }.filterNotNull()
        val minY = all.minOrNull() ?: 0.0
        val maxY = all.maxOrNull() ?: 1.0
        val spanY = if (maxY - minY == 0.0) 1.0 else (maxY - minY)
        val n = data.valeurs.size
        Row(Modifier.fillMaxWidth().height(220.dp)) {
            Column(Modifier.width(54.dp).fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                Text(formatAxis(maxY), color = AblsColors.TextWhite, fontSize = 10.sp)
                Text(formatAxis(minY + spanY / 2), color = AblsColors.TextWhite, fontSize = 10.sp)
                Text(formatAxis(minY), color = AblsColors.TextWhite, fontSize = 10.sp)
            }
            Canvas(Modifier.weight(1f).height(220.dp)) {
                val w = size.width; val h = size.height
                val grid = Color.White.copy(alpha = 0.15f)
                for (k in 0..4) {
                    val y = h * k / 4f
                    drawLine(grid, Offset(0f, y), Offset(w, y), 1f)
                }
                if (n < 2) return@Canvas
                data.courbes.indices.forEach { ci ->
                    val path = Path()
                    var started = false
                    data.valeurs.forEachIndexed { i, row ->
                        val v = row.valeurs.getOrNull(ci) ?: run { started = false; return@forEachIndexed }
                        val x = w * i / (n - 1).toFloat()
                        val y = (h - (h * ((v - minY) / spanY))).toFloat()
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                    }
                    drawPath(path, colors.getOrElse(ci) { PALETTE[ci % PALETTE.size] }, style = Stroke(width = 2f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 54.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text(data.valeurs.firstOrNull()?.date?.take(16) ?: "", color = AblsColors.Secondary, fontSize = 10.sp)
            Text(data.valeurs.lastOrNull()?.date?.take(16) ?: "", color = AblsColors.Secondary, fontSize = 10.sp)
        }
    }
}

private fun formatAxis(v: Double): String {
    val r = kotlin.math.round(v * 100) / 100
    return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString()
}

/** Tableau de valeurs (mode 1 des tableaux). */
@Composable
fun ValuesTable(data: ArchiveResponse, modifier: Modifier = Modifier) {
    Column(modifier.height(260.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().background(AblsColors.SurfaceVariant).padding(4.dp)) {
            Text("Date", color = AblsColors.TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
            data.courbes.forEach { c -> Text("${c.libelle ?: ""} ${c.unite?.let { "($it)" } ?: ""}", color = AblsColors.TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) }
        }
        data.valeurs.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(row.date, color = AblsColors.TextWhite, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                row.valeurs.forEach { v -> Text(v?.let { formatAxis(it) } ?: "", color = AblsColors.TextWhite, fontSize = 11.sp, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

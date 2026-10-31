package fr.abls.habitat.home.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.abls.habitat.home.api.STATIC_ABLS
import fr.abls.habitat.home.api.Visuel
import fr.abls.habitat.home.api.VisuelEtat
import fr.abls.habitat.home.session.Session
import fr.abls.habitat.home.ui.components.RemoteImage
import fr.abls.habitat.home.ui.components.clickAndLongClick
import fr.abls.habitat.home.ui.theme.AblsColors
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/** Calcule l'URL de l'image d'un visuel selon son contrôle et son état (Creer_light_visuel / Changer_etat_visuel_*). */
fun visuelImageUrl(session: Session, v: Visuel, e: VisuelEtat?): String? {
    val ext = v.extension ?: "svg"
    val mode = e?.mode ?: v.mode
    val color = e?.color ?: v.color
    return when (v.controle) {
        "static" -> session.imgUrl("${v.forme}.$ext")
        "by_mode" -> if (mode == "default") "$STATIC_ABLS/img/question.png" else session.imgUrl("${v.forme}_$mode.$ext")
        "by_color" -> session.imgUrl("${v.forme}_$color.$ext")
        "by_mode_color" -> if (mode == "default") "$STATIC_ABLS/img/question.png" else session.imgUrl("${v.forme}_${mode}_$color.$ext")
        "by_js" -> v.forme?.let { session.imgUrl("$it.$ext") }
        else -> if (v.forme == null && v.icone != null) session.imgUrl("${v.icone}.gif") else null
    }
}

fun formatValeur(valeur: Double?, nbDecimal: Int, unite: String?): String {
    if (valeur == null) return "-"
    val factor = 10.0.pow(nbDecimal)
    val rounded = (valeur * factor).roundToLong()
    val intPart = rounded / factor.toLong()
    val txt = if (nbDecimal <= 0) intPart.toString()
              else "$intPart.${(kotlin.math.abs(rounded) % factor.toLong()).toString().padStart(nbDecimal, '0')}"
    return if (unite.isNullOrBlank()) txt else "$txt $unite"
}

fun formatHoraire(tenths: Double?): String {
    if (tenths == null) return "-"
    var t = tenths / 10.0
    val h = floor(t / 3600).toInt(); t %= 3600
    val m = floor(t / 60).toInt(); val s = floor(t % 60).toInt()
    return "${h}h ${m.toString().padStart(2, '0')}m ${s.toString().padStart(2, '0')}s"
}

/** Vignette d'un visuel en mode "léger" (grille). */
@Composable
fun VisuelCard(session: Session, v: Visuel, etat: VisuelEtat?, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    if (etat?.noshow == true) return
    val disabled = etat?.disable == true
    val cligno = etat?.cligno == true && (v.controle != "by_mode" || (etat.mode ?: v.mode) != "default")
    val footer = etat?.libelle ?: v.input_libelle ?: v.libelle

    Column(
        modifier.padding(6.dp).clickAndLongClick(onClick, onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        v.dls_owner_shortname?.let { Text(it, color = AblsColors.TextWhite, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1) }
        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            when {
                v.controle == "complexe" && v.forme == "cadran" -> CadranContent(v, etat)
                v.controle == "complexe" && v.forme == "bouton" -> Button(onClick = onClick) { Text(v.libelle, fontSize = 12.sp) }
                v.controle == "complexe" -> Text(v.libelle, color = AblsColors.TextWhite, textAlign = TextAlign.Center, fontSize = 13.sp)
                else -> RemoteImage(
                    url = visuelImageUrl(session, v, etat),
                    modifier = Modifier.size(100.dp).alpha(if (disabled) 0.7f else 1f),
                    grayscale = disabled,
                    blink = cligno,
                )
            }
            /* Badge haut-droite : cadenas (lock) ou auto/manu */
            val badgeImg = when (etat?.badge) {
                "lock" -> session.imgUrl("cadenas_ferme.svg")
                "manu" -> session.imgUrl("auto_manu_manu.svg")
                else -> null
            }
            if (badgeImg != null) RemoteImage(badgeImg, Modifier.size(28.dp).align(Alignment.TopEnd))
        }
        Text(footer, color = AblsColors.TextWhite, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun CadranContent(v: Visuel, etat: VisuelEtat?) {
    val valeur = etat?.valeur ?: v.valeur
    val unite = etat?.unite ?: v.unite
    val nbDec = etat?.nb_decimal ?: v.nb_decimal
    val mode = v.mode ?: "texte"
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (mode.startsWith("progress")) {
            val min = v.minimum ?: 0.0
            val max = v.maximum ?: 100.0
            val capee = (valeur ?: min).coerceIn(minOf(min, max), maxOf(min, max))
            val position = if (max != min) ((capee - min) / (max - min)).toFloat() else 0f
            val color = when (mode) {
                "progress-rov" -> when {
                    v.seuil_ntb != null && capee <= v.seuil_ntb -> AblsColors.Danger
                    v.seuil_nb != null && capee <= v.seuil_nb -> AblsColors.Warning
                    else -> AblsColors.Success
                }
                "progress-vor" -> when {
                    v.seuil_nth != null && capee >= v.seuil_nth -> AblsColors.Danger
                    v.seuil_nh != null && capee >= v.seuil_nh -> AblsColors.Warning
                    else -> AblsColors.Success
                }
                "progress-rovor" -> when {
                    v.seuil_ntb != null && capee <= v.seuil_ntb -> AblsColors.Danger
                    v.seuil_nb != null && capee <= v.seuil_nb -> AblsColors.Warning
                    v.seuil_nth != null && capee >= v.seuil_nth -> AblsColors.Danger
                    v.seuil_nh != null && capee >= v.seuil_nh -> AblsColors.Warning
                    else -> AblsColors.Success
                }
                else -> AblsColors.Primary
            }
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { position },
                modifier = Modifier.fillMaxWidth().height(14.dp).background(AblsColors.SurfaceVariant, RoundedCornerShape(7.dp)),
                color = color, trackColor = AblsColors.SurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        val txt = if (mode == "horaire") formatHoraire(valeur) else formatValeur(valeur, nbDec, unite)
        Text(txt, color = AblsColors.TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun Spacer8() = Spacer(Modifier.width(8.dp))

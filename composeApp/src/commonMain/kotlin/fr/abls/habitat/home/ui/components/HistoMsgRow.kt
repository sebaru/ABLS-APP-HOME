package fr.abls.habitat.home.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.abls.habitat.home.api.HistoMsg
import fr.abls.habitat.home.api.MSG_TYPOLOGIES
import fr.abls.habitat.home.api.STATIC_ABLS
import fr.abls.habitat.home.ui.theme.AblsColors
import fr.abls.habitat.home.ui.theme.bootstrapColor

/**
 * Ligne d'un message histo (équivalent des DataTables idTableMessages / idTableHISTOS).
 * Une ligne acquittable est cliquable (acquit) comme sur le site.
 */
@Composable
fun HistoMsgRow(
    msg: HistoMsg,
    showPage: Boolean,
    allowAck: Boolean,
    onAck: ((HistoMsg) -> Unit)?,
    onPageClick: ((String) -> Unit)? = null,
    onObjetClick: ((String) -> Unit)? = null,
) {
    val typo = MSG_TYPOLOGIES.getOrElse(msg.typologie) { MSG_TYPOLOGIES[0] }
    val color = bootstrapColor(typo.colorKey)
    val ackable = allowAck && msg.isAckable && onAck != null
    Row(
        Modifier.fillMaxWidth()
            .then(if (ackable) Modifier.clickable { onAck?.invoke(msg) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = "$STATIC_ABLS/img/${typo.img}",
            modifier = Modifier.size(32.dp),
            blink = typo.cligno && msg.date_fixe == null,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(msg.date_create ?: "", color = AblsColors.Secondary, fontSize = 11.sp)
                if (showPage && !msg.syn_page.isNullOrBlank()) {
                    Text(
                        msg.syn_page, color = AblsColors.Info, fontSize = 11.sp, textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(enabled = onPageClick != null) { onPageClick?.invoke(msg.syn_page) },
                    )
                }
            }
            Text(
                msg.dls_shortname ?: "", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                textDecoration = if (onObjetClick != null) TextDecoration.Underline else null,
                modifier = Modifier.clickable(enabled = onObjetClick != null) { onObjetClick?.invoke(msg.dls_shortname ?: "") },
            )
            Text(msg.libelle ?: "", color = color, fontSize = 13.sp)
            msg.nom_ack?.let { Text("Acquitté par $it", color = AblsColors.Secondary, fontSize = 11.sp) }
        }
        if (ackable) {
            OutlinedButton(onClick = { onAck?.invoke(msg) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Text("Acquit", fontSize = 12.sp)
            }
        }
    }
    HorizontalDivider(color = AblsColors.SurfaceVariant)
}

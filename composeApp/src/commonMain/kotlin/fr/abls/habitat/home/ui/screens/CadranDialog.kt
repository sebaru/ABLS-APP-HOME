package fr.abls.habitat.home.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.abls.habitat.home.api.Visuel
import fr.abls.habitat.home.api.VisuelEtat
import kotlin.math.floor

/** Saisie d'une valeur de cadran RW (modales idModalCadranTexte / idModalCadranHoraire du site). */
@Composable
fun CadranDialog(v: Visuel, etat: VisuelEtat?, onDismiss: () -> Unit, onValidate: (Double) -> Unit) {
    val valeur = etat?.valeur ?: v.valeur
    if (v.mode == "horaire") {
        var t = (valeur ?: 0.0) / 10.0
        val h0 = floor(t / 3600).toInt(); t %= 3600
        val m0 = floor(t / 60).toInt(); val s0 = floor(t % 60).toInt()
        var h by remember { mutableStateOf(h0.toString()) }
        var m by remember { mutableStateOf(m0.toString()) }
        var s by remember { mutableStateOf(s0.toString()) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(v.libelle) },
            text = {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(h, { h = it }, label = { Text("H") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(m, { m = it }, label = { Text("Mn") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(s, { s = it }, label = { Text("Sec") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val total = ((h.toIntOrNull() ?: 0) * 3600 + (m.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)) * 10.0
                    onValidate(total)
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        )
    } else {
        var txt by remember { mutableStateOf(valeur?.let { formatValeur(it, v.nb_decimal, null) } ?: "") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(v.libelle) },
            text = {
                Column {
                    OutlinedTextField(
                        txt, { txt = it }, label = { Text("Valeur ${v.unite ?: ""}") },
                        supportingText = { Text("Min ${v.minimum ?: "-"} / Max ${v.maximum ?: "-"}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { txt.replace(',', '.').toDoubleOrNull()?.let(onValidate) }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        )
    }
}

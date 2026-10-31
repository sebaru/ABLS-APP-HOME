package fr.abls.habitat.home.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/* L'API renvoie parfois des booléens sous forme 0/1 ou "true" : on tolère tout. */
object FlexBoolSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexBool", PrimitiveKind.BOOLEAN)
    override fun deserialize(decoder: Decoder): Boolean {
        val el = (decoder as JsonDecoder).decodeJsonElement()
        if (el is JsonNull) return false
        val p = el.jsonPrimitive
        return p.booleanOrNull ?: p.intOrNull?.let { it != 0 } ?: (p.contentOrNull?.lowercase() in setOf("true", "1", "yes"))
    }
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

object FlexDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexDouble", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double? {
        val el = (decoder as JsonDecoder).decodeJsonElement()
        if (el is JsonNull) return null
        return el.jsonPrimitive.doubleOrNull ?: el.jsonPrimitive.contentOrNull?.toDoubleOrNull()
    }
    override fun serialize(encoder: Encoder, value: Double?) { if (value == null) encoder.encodeNull() else encoder.encodeDouble(value) }
}

object FlexIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int {
        val el = (decoder as JsonDecoder).decodeJsonElement()
        if (el is JsonNull) return 0
        val p = el.jsonPrimitive
        return p.intOrNull ?: p.booleanOrNull?.let { if (it) 1 else 0 } ?: p.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0
    }
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

typealias FlexBool = @Serializable(FlexBoolSerializer::class) Boolean
typealias FlexDouble = @Serializable(FlexDoubleSerializer::class) Double?
typealias FlexInt = @Serializable(FlexIntSerializer::class) Int

@Serializable
data class ApiError(val api_error: String? = null)

/* ------------------------------------------------ /user/profil ------------------------------------------------ */
@Serializable
data class Profil(
    val default_domain_uuid: String? = null,
    val default_domain_name: String? = null,
    val static_data_url: String? = null,
    val access_level: FlexInt = 0,
    val mqtt_hostname: String? = null,
    val mqtt_port: FlexInt = 0,
    val mqtt_over_ssl: FlexBool = false,
    val browser_password: String? = null,
    val domain_notification: String? = null,
    val domain_notification_warning: String? = null,
    val console_url: String? = null,
    val home_url: String? = null,
    val abls_api_version: String? = null,
)

/* ------------------------------------------------ /domain/list ------------------------------------------------ */
@Serializable
data class DomainList(val domains: List<Domain> = emptyList())

@Serializable
data class Domain(
    val domain_uuid: String,
    val domain_name: String = "",
    val access_level: FlexInt = 0,
    val image: String? = null,
)

@Serializable
data class DomainImage(val image: String? = null)

/* ------------------------------------------------ /syn/show --------------------------------------------------- */
@Serializable
data class Synoptique(
    val syn_id: FlexInt = 0,
    val page: String = "",
    val libelle: String = "",
    val mode_affichage: FlexBool = false,
    val nbr_visuels: FlexInt = 0,
    val nbr_tableaux: FlexInt = 0,
    val parent_syns: List<SynLink> = emptyList(),
    val child_syns: List<SynLink> = emptyList(),
    val horloges: List<HorlogeRef> = emptyList(),
    val cameras: List<Camera> = emptyList(),
    val visuels: List<Visuel> = emptyList(),
    val tableaux: List<Tableau> = emptyList(),
    val tableaux_map: List<TableauMap> = emptyList(),
)

@Serializable
data class SynLink(
    val syn_id: FlexInt = 0,
    val page: String = "",
    val image: String? = null,
    val libelle: String = "",
)

@Serializable
data class HorlogeRef(val tech_id: String = "", val dls_name: String = "")

@Serializable
data class Camera(val syn_camera_id: FlexInt = 0, val camera_name: String = "", val url: String = "")

@Serializable
data class Visuel(
    val tech_id: String = "",
    val acronyme: String = "",
    val libelle: String = "",
    val input_libelle: String? = null,
    val dls_owner_shortname: String? = null,
    val controle: String = "",
    val forme: String? = null,
    val mode: String? = null,
    val color: String? = null,
    val extension: String? = null,
    val icone: String? = null,
    val rw: FlexBool = false,
    val minimum: FlexDouble = null,
    val maximum: FlexDouble = null,
    val seuil_ntb: FlexDouble = null,
    val seuil_nb: FlexDouble = null,
    val seuil_nh: FlexDouble = null,
    val seuil_nth: FlexDouble = null,
    val nb_decimal: FlexInt = 0,
    val valeur: FlexDouble = null,
    val unite: String? = null,
) {
    val key: String get() = "$tech_id:$acronyme"
}

/** Message MQTT DLS_VISUEL : nouvel état d'un visuel. */
@Serializable
data class VisuelEtat(
    val tech_id: String = "",
    val acronyme: String = "",
    val mode: String? = null,
    val color: String? = null,
    val cligno: FlexBool = false,
    val disable: FlexBool = false,
    val noshow: FlexBool = false,
    val badge: String? = null,
    val libelle: String? = null,
    val valeur: FlexDouble = null,
    val unite: String? = null,
    val nb_decimal: FlexInt = 0,
)

/** Message MQTT SYN_STATUS/<bit> : état d'un bit d'anomalie pour un synoptique enfant. */
@Serializable
data class SynStatus(val syn_id: FlexInt = 0, val etat: FlexBool = false)

@Serializable
data class Tableau(
    val tableau_id: FlexInt = 0,
    val titre: String? = null,
    val mode: FlexInt = 0,
    val periode: String = "BY_HOUR",
    val period_lock: FlexBool = false,
)

@Serializable
data class TableauMap(
    val tableau_id: FlexInt = 0,
    val tech_id: String = "",
    val acronyme: String = "",
    val methode: String? = null,
    val color: String? = null,
)

/* ------------------------------------------------ /histo -------------------------------------------------------- */
@Serializable
data class HistoList(val histo_msgs: List<HistoMsg> = emptyList())

@Serializable
data class HistoMsg(
    val histo_msg_id: FlexInt = 0,
    val tech_id: String = "",
    val acronyme: String = "",
    val typologie: FlexInt = 0,
    val date_create: String? = null,
    val date_fixe: String? = null,
    val dls_shortname: String? = null,
    val libelle: String? = null,
    val nom_ack: String? = null,
    val syn_page: String? = null,
    val alive: FlexBool = true,
) {
    val isAckable: Boolean get() = typologie !in setOf(0, 4, 5) && nom_ack == null
}

/* ------------------------------------------------ /archive/get ------------------------------------------------ */
@Serializable
data class ArchiveCourbe(val tech_id: String? = null, val acronyme: String? = null, val libelle: String? = null, val unite: String? = null)

/** Réponse brute : { courbe1: {...}, courbe2: {...}, valeurs: [ {date, valeur1, valeur2...} ] } */
data class ArchiveResponse(val courbes: List<ArchiveCourbe>, val valeurs: List<ArchiveRow>)
data class ArchiveRow(val date: String, val valeurs: List<Double?>)

fun JsonObject.toArchiveResponse(nbCourbes: Int): ArchiveResponse {
    val courbes = (1..nbCourbes).map { i ->
        (this["courbe$i"] as? JsonObject)?.let { c ->
            ArchiveCourbe(
                libelle = c["libelle"]?.jsonPrimitive?.contentOrNull,
                unite = c["unite"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: ArchiveCourbe(libelle = "Courbe $i")
    }
    val rows = (this["valeurs"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
        (el as? JsonObject)?.let { o ->
            ArchiveRow(
                date = o["date"]?.jsonPrimitive?.contentOrNull ?: "",
                valeurs = (1..nbCourbes).map { i ->
                    (o["valeur$i"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
                }
            )
        }
    } ?: emptyList()
    return ArchiveResponse(courbes, rows)
}

/* ------------------------------------------------ /api/horloge -------------------------------------------------- */
@Serializable
data class HorlogeList(val horloges: List<Horloge> = emptyList())

@Serializable
data class Horloge(val id: FlexInt = 0, val libelle: String = "", val tech_id: String? = null)

@Serializable
data class HorlogeTickList(val horloge_ticks: List<HorlogeTick> = emptyList())

@Serializable
data class HorlogeTick(
    val id: FlexInt = 0,
    val horloge_id: FlexInt = 0,
    val lundi: FlexBool = false,
    val mardi: FlexBool = false,
    val mercredi: FlexBool = false,
    val jeudi: FlexBool = false,
    val vendredi: FlexBool = false,
    val samedi: FlexBool = false,
    val dimanche: FlexBool = false,
    val heure: FlexInt = 12,
    val minute: FlexInt = 0,
)

val PERIODES_TABLEAU = listOf(
    "BY_MINUTE_ON_2_HOURS" to "Sur 2 heures",
    "BY_10_MINUTE_ON_1_DAY" to "Sur 1 jour",
    "BY_HOUR_ON_2_DAYS" to "Sur 2 jours",
    "BY_10_MINUTE_ON_3_DAYS" to "Sur 3 jours",
    "BY_30_MINUTE_ON_1_WEEK" to "Sur 1 semaine",
    "BY_HOUR_ON_2_WEEKS" to "Sur 2 semaines",
    "BY_DAY_ON_2_MONTHS" to "Sur 2 mois",
    "BY_WEEK_ON_4_MONTHS" to "Sur 4 mois",
    "BY_MONTH_ON_12_MONTHS" to "Sur 1 an",
    "BY_YEAR_ON_2_YEARS" to "Sur 2 ans",
)

data class MsgTypologie(val label: String, val cligno: Boolean, val img: String, val colorKey: String)

val MSG_TYPOLOGIES = listOf(
    MsgTypologie("État", false, "info.svg", "white"),
    MsgTypologie("Alerte", true, "bouclier_orange.svg", "warning"),
    MsgTypologie("Défaut", true, "pignon_orange.svg", "warning"),
    MsgTypologie("Alarme", true, "pignon_red.svg", "danger"),
    MsgTypologie("Veille", false, "bouclier_green.svg", "success"),
    MsgTypologie("Attente", false, "panneau_danger.svg", "white"),
    MsgTypologie("Danger", true, "croix_red.svg", "danger"),
    MsgTypologie("Dérangement", true, "croix_orange.svg", "warning"),
)

const val STATIC_ABLS = "https://static.abls-habitat.fr"

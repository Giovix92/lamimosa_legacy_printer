package com.giovix92.lamimosalegacyprinter.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Mirrors the subset of the order.mjs schema (POST /api/order shape) needed
 * to show a list and print a receipt. Deliberately not the full schema -
 * only fields actually read by MainActivity's list/receipt building.
 */
data class Order(
    val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val operatore: String,
    val sede: String,
    val occasione: String,
    val occasioneAltro: String,
    val categoria: String,
    val peso: String,
    val tipoPaste: String,
    val tipologiaTorta: String,
    val tipologiaTortaAltro: String,
    val gusti: List<String>,
    val gustoTortaAltro: String,
    val topping: String,
    val panna: String,
    val scritta: String,
    val testoScritta: String,
    val stampa: Boolean,
    val fotoEsempio: Boolean,
    val sesso: String,
    val eta: String,
    val candeline: String,
    val numeroCandeline: String,
    val eventDate: String,
    val orario: String,
    val message: String,
    val createdAt: String,
    val status: String,
    val archivedFrom: String
) {
    companion object {
        fun fromJson(o: JSONObject): Order = Order(
            id = o.optString("id"),
            name = o.optString("name"),
            phone = o.optString("phone"),
            email = o.optString("email"),
            operatore = o.optString("operatore"),
            sede = o.optString("sede"),
            occasione = o.optString("occasione"),
            occasioneAltro = o.optString("occasioneAltro"),
            categoria = o.optString("categoria", "torta"),
            peso = o.optString("peso"),
            tipoPaste = o.optString("tipoPaste"),
            tipologiaTorta = o.optString("tipologiaTorta"),
            tipologiaTortaAltro = o.optString("tipologiaTortaAltro"),
            gusti = o.optJSONArray("gusti").toStringList(),
            gustoTortaAltro = o.optString("gustoTortaAltro"),
            topping = o.optString("topping"),
            panna = o.optString("panna"),
            scritta = o.optString("scritta"),
            testoScritta = o.optString("testoScritta"),
            stampa = o.optBoolean("stampa"),
            fotoEsempio = o.optBoolean("fotoEsempio"),
            sesso = o.optString("sesso"),
            eta = o.optString("eta"),
            candeline = o.optString("candeline"),
            numeroCandeline = o.optString("numeroCandeline"),
            eventDate = o.optString("eventDate"),
            orario = o.optString("orario"),
            message = o.optString("message"),
            createdAt = o.optString("createdAt"),
            status = o.optString("status"),
            archivedFrom = if (o.isNull("archivedFrom")) "" else o.optString("archivedFrom")
        )

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            val list = ArrayList<String>(length())
            for (i in 0 until length()) list.add(optString(i))
            return list
        }
    }
}

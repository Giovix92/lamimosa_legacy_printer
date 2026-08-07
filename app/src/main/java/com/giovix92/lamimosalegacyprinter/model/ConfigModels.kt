package com.giovix92.lamimosalegacyprinter.model

import org.json.JSONArray
import org.json.JSONObject

/** id + Italian label only — the admin panel (and this app) only ever shows Italian. */
data class Option(val id: String, val labelIt: String) {
    override fun toString(): String = labelIt
}

data class Gusto(val id: String, val labelIt: String, val isTopping: Boolean) {
    override fun toString(): String = labelIt
}

data class Tipologia(
    val id: String,
    val labelIt: String,
    val allowedGusti: List<String>?,   // null → all allowed
    val allowedTopping: List<String>?, // null → all allowed; empty → none
    val gustiLiberi: Boolean,
    val gustiDisabilitati: Boolean
) {
    override fun toString(): String = labelIt
}

private fun JSONObject.itLabel(): String = optJSONObject("label")?.optString("it") ?: optString("id")

fun parseOptions(array: JSONArray?): List<Option> {
    if (array == null) return emptyList()
    val list = ArrayList<Option>(array.length())
    for (i in 0 until array.length()) {
        val o = array.getJSONObject(i)
        list.add(Option(o.optString("id"), o.itLabel()))
    }
    return list
}

fun parseGusti(array: JSONArray?): List<Gusto> {
    if (array == null) return emptyList()
    val list = ArrayList<Gusto>(array.length())
    for (i in 0 until array.length()) {
        val o = array.getJSONObject(i)
        list.add(Gusto(o.optString("id"), o.itLabel(), o.optBoolean("isTopping")))
    }
    return list
}

private fun JSONObject.optStringListOrNull(key: String): List<String>? {
    if (!has(key) || isNull(key)) return null
    val arr = optJSONArray(key) ?: return null
    val list = ArrayList<String>(arr.length())
    for (i in 0 until arr.length()) list.add(arr.optString(i))
    return list
}

fun parseTipologie(array: JSONArray?): List<Tipologia> {
    if (array == null) return emptyList()
    val list = ArrayList<Tipologia>(array.length())
    for (i in 0 until array.length()) {
        val o = array.getJSONObject(i)
        list.add(
            Tipologia(
                id = o.optString("id"),
                labelIt = o.itLabel(),
                allowedGusti = o.optStringListOrNull("allowedGusti"),
                allowedTopping = o.optStringListOrNull("allowedTopping"),
                gustiLiberi = o.optBoolean("gustiLiberi"),
                gustiDisabilitati = o.optBoolean("gustiDisabilitati")
            )
        )
    }
    return list
}

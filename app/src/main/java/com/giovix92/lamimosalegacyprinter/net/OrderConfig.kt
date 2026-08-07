package com.giovix92.lamimosalegacyprinter.net

import com.giovix92.lamimosalegacyprinter.Config
import com.giovix92.lamimosalegacyprinter.model.Gusto
import com.giovix92.lamimosalegacyprinter.model.Option
import com.giovix92.lamimosalegacyprinter.model.Tipologia
import com.giovix92.lamimosalegacyprinter.model.parseGusti
import com.giovix92.lamimosalegacyprinter.model.parseOptions
import com.giovix92.lamimosalegacyprinter.model.parseTipologie
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mirrors js/order-config.js + GET /api/config: server-driven option lists
 * (sedi, occasioni, tipologie, gusti, panna, "scritta"/candeline, tipi pasta),
 * plus the handful of choices that are hardcoded client-side on the website
 * too (sesso, età, categoria) rather than coming from the backend.
 *
 * Singleton with a one-shot fetch: this app is only ever open for a print/
 * edit session at a time, config rarely changes mid-shift, so no need for
 * the website's live lm:orderconfigchange refresh mechanism.
 */
object OrderConfig {
    const val ID_ALTRO = "altro"
    const val ID_NESSUNO = "nessuno"
    const val GUSTI_MAX_SELEZIONABILI = 2

    val SESSO_OPZIONI = listOf(Option("neutro", "Neutro"), Option("maschio", "Maschio"), Option("femmina", "Femmina"))
    val ETA_OPZIONI = listOf(Option("adulto", "Adulto"), Option("bambino", "Bambino"))
    val CATEGORIE_ORDINE = listOf(Option("torta", "Torta"), Option("vassoio", "Vassoio"))

    var sedi: List<Option> = emptyList(); private set
    var occasioni: List<Option> = emptyList(); private set
    var tipologieTorta: List<Tipologia> = emptyList(); private set
    var gustiTorta: List<Gusto> = emptyList(); private set
    var tipiPasta: List<Option> = emptyList(); private set
    var pannaOpzioni: List<Option> = emptyList(); private set
    var scrittaOpzioni: List<Option> = emptyList(); private set

    var loaded = false; private set

    fun fetch(): Boolean {
        return try {
            val url = URL("${Config.ORDERS_API_BASE_URL}/api/config")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return false
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            sedi = parseOptions(json.optJSONArray("sedi"))
            occasioni = parseOptions(json.optJSONArray("occasioni"))
            tipologieTorta = parseTipologie(json.optJSONArray("tipologieTorta"))
            gustiTorta = parseGusti(json.optJSONArray("gustiTorta"))
            tipiPasta = parseOptions(json.optJSONArray("tipiPasta"))
            pannaOpzioni = parseOptions(json.optJSONArray("pannaOpzioni"))
            scrittaOpzioni = parseOptions(json.optJSONArray("scrittaOpzioni"))
            loaded = true
            true
        } catch (e: Exception) {
            false
        }
    }

    fun findTipologia(id: String?): Tipologia? = tipologieTorta.find { it.id == id }

    // Ported from order-config.js gustiPerTipologia().
    fun gustiPerTipologia(tipologiaId: String?): List<Gusto> {
        val t = findTipologia(tipologiaId)
        if (t != null && (t.gustiLiberi || t.gustiDisabilitati)) return emptyList()
        if (t?.allowedGusti.isNullOrEmpty()) return gustiTorta
        val allowed = gustiTorta.filter { t!!.allowedGusti!!.contains(it.id) }
        return if (allowed.any { it.id == ID_ALTRO }) {
            allowed
        } else {
            val altro = gustiTorta.find { it.id == ID_ALTRO }
            if (altro != null) allowed + altro else allowed
        }
    }

    // Ported from order-config.js gustiDisabilitatiPerTipologia().
    fun gustiDisabilitatiPerTipologia(tipologiaId: String?): Boolean =
        findTipologia(tipologiaId)?.gustiDisabilitati ?: false

    // Ported from order-config.js toppingPerTipologia(). "Nessuno" is a
    // synthetic option, not part of the server's gustiTorta list.
    fun toppingPerTipologia(tipologiaId: String?): List<Option> {
        val nessuno = Option(ID_NESSUNO, "Nessuno")
        val t = findTipologia(tipologiaId)
        if (t == null || t.allowedTopping == null) {
            return listOf(nessuno) + gustiTorta.filter { it.isTopping }.map { Option(it.id, it.labelIt) }
        }
        if (t.allowedTopping.isEmpty()) return emptyList()
        return listOf(nessuno) + gustiTorta.filter { it.isTopping && t.allowedTopping.contains(it.id) }.map { Option(it.id, it.labelIt) }
    }
}

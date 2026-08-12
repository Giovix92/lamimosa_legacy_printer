package com.giovix92.lamimosalegacyprinter.printer

import com.giovix92.lamimosalegacyprinter.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ported near-verbatim from lamimosa_android's
 * domain/printer/EpsonFiscalXmlBuilder.kt, which builds the same fpMate SOAP
 * XML this app's PrinterActivity already sends successfully in production.
 * Kept identical on purpose so both apps produce byte-for-byte the same
 * receipt layout on the same printer. Only change: reads from the local
 * Order model here instead of the main app's PrintJob/moshi-parsed type.
 */
object EpsonFiscalXmlBuilder {

    private const val DEFAULT_MAX_LINE_CHARS = 32

    // Ordine dello scontrino (tenuto identico a lamimosa_android's
    // EpsonFiscalXmlBuilder.buildFromJob()): data in cima, poi i dettagli del prodotto (torta o
    // vassoio), poi in fondo i dati di ritiro/consegna e cliente, e per ultimo l'operatore.
    fun buildFromOrder(order: Order, operator: String = "1", maxLineChars: Int = DEFAULT_MAX_LINE_CHARS): String {
        val sb = StringBuilder()
        sb.append("<printerNonFiscal>")
        sb.append("<beginNonFiscal operator=\"$operator\" />")

        sb.append(printLine("====================", operator, maxLineChars))
        sb.append(printLine("     LA MIMOSA      ", operator, maxLineChars))
        sb.append(printLine("  ORDINE: ${order.id} ", operator, maxLineChars))
        sb.append(printLine("====================", operator, maxLineChars))

        sb.append(printLine("DATA EVENTO: ${formatEventDate(order.eventDate)}", operator, maxLineChars))
        sb.append(printLine("--------------------", operator, maxLineChars))

        val isVassoio = order.categoria == "vassoio"

        if (isVassoio) {
            sb.append(printLine("--- DETTAGLI VASSOIO ---", operator, maxLineChars))
            sb.append(printLine("TIPO: Vassoio", operator, maxLineChars))
            if (order.peso.isNotEmpty()) sb.append(printLine("PESO: ${order.peso}kg", operator, maxLineChars))
            if (order.tipoPaste.isNotEmpty()) sb.append(printLine("TIPO PASTE: ${order.tipoPaste}", operator, maxLineChars))
            sb.append(printLine("--------------------", operator, maxLineChars))
        } else {
            sb.append(printLine("--- DETTAGLI TORTA ---", operator, maxLineChars))
            if (order.tipologiaTorta.isNotEmpty()) sb.append(printLine("TIPO: ${order.tipologiaTorta}", operator, maxLineChars))
            if (order.peso.isNotEmpty()) sb.append(printLine("PESO: ${order.peso}kg", operator, maxLineChars))

            if (order.gusti.isNotEmpty()) sb.append(printLine("GUSTI: ${order.gusti.joinToString(", ")}", operator, maxLineChars))
            if (order.gustoTortaAltro.isNotEmpty()) sb.append(printWrapped("ALTRO GUSTO:", order.gustoTortaAltro, operator, maxLineChars))
            if (order.topping.isNotEmpty()) sb.append(printLine("AGGIUNTE: ${order.topping}", operator, maxLineChars))
            if (order.panna.isNotEmpty()) sb.append(printLine("PANNA: ${order.panna}", operator, maxLineChars))

            if (order.occasione.isNotEmpty()) sb.append(printLine("OCCASIONE: ${order.occasione}", operator, maxLineChars))
            if (order.occasioneAltro.isNotEmpty()) sb.append(printWrapped(" ALTRA OCC.:", order.occasioneAltro, operator, maxLineChars))

            if (order.eta.isNotEmpty() || order.sesso.isNotEmpty()) {
                sb.append(printLine("ETA/SESSO: ${order.eta.ifEmpty { "-" }} / ${order.sesso.ifEmpty { "-" }}", operator, maxLineChars))
            }

            if (order.candeline == "Sì") {
                sb.append(printLine("CANDELINE: SI (${order.numeroCandeline.ifEmpty { "0" }})", operator, maxLineChars))
            }

            if (order.scritta == "Sì") {
                sb.append(printLine("SCRITTA: SI", operator, maxLineChars))
                if (order.testoScritta.isNotEmpty()) sb.append(printWrapped(" TESTO:", order.testoScritta, operator, maxLineChars))
            }

            if (order.stampa) sb.append(printLine("CIALDA (STAMPA FOTO): SI", operator, maxLineChars))
            if (order.fotoEsempio) sb.append(printLine("FOTO ESEMPIO: SI", operator, maxLineChars))

            sb.append(printLine("--------------------", operator, maxLineChars))
        }

        if (order.message.isNotEmpty()) {
            sb.append(printWrapped("NOTE:", order.message, operator, maxLineChars))
            sb.append(printLine("--------------------", operator, maxLineChars))
        }

        if (order.sede.isNotEmpty()) sb.append(printLine("RITIRO: ${order.sede.uppercase(Locale.getDefault())}", operator, maxLineChars))
        sb.append(printLine("ORARIO: ${order.orario}", operator, maxLineChars))
        sb.append(printLine("NOME: ${order.name}", operator, maxLineChars))
        if (order.phone.isNotEmpty()) sb.append(printLine("TEL: ${order.phone}", operator, maxLineChars))
        if (order.email.isNotEmpty()) sb.append(printLine("EMAIL: ${order.email}", operator, maxLineChars))
        sb.append(printLine("--------------------", operator, maxLineChars))

        if (order.operatore.isNotEmpty()) sb.append(printLine("OPERATORE: ${order.operatore}", operator, maxLineChars))

        val footerDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        sb.append(printLine(footerDate, operator, maxLineChars))

        sb.append("<endNonFiscal operator=\"$operator\" />")
        sb.append("</printerNonFiscal>")

        return wrapInSoap(sb.toString())
    }

    private fun formatEventDate(eventDate: String): String {
        if (eventDate.isEmpty()) return "-"
        val parts = eventDate.split("-")
        if (parts.size != 3) return eventDate
        return "${parts[2]}/${parts[1]}/${parts[0]}"
    }

    fun buildTestPage(operator: String = "1", deviceName: String, appVersion: String): String {
        val sb = StringBuilder()
        sb.append("<printerNonFiscal>")
        sb.append("<beginNonFiscal operator=\"$operator\" />")
        sb.append(printLine("====================", operator))
        sb.append(printLine(" LA MIMOSA - TEST   ", operator))
        sb.append(printLine("====================", operator))
        sb.append(printLine("Dispositivo: $deviceName", operator))
        sb.append(printLine("Versione App: $appVersion", operator))
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        sb.append(printLine("Data: $date", operator))
        sb.append(printLine("====================", operator))
        sb.append("<endNonFiscal operator=\"$operator\" />")
        sb.append("</printerNonFiscal>")
        return wrapInSoap(sb.toString())
    }

    private fun printLine(data: String, operator: String, maxChars: Int = DEFAULT_MAX_LINE_CHARS): String {
        if (data.length <= maxChars) return printLineRaw(data, operator)
        val sb = StringBuilder()
        wrapText(data, maxChars).forEach { sb.append(printLineRaw(it, operator)) }
        return sb.toString()
    }

    private fun printLineRaw(data: String, operator: String): String {
        return "<printNormal operator=\"$operator\" font=\"1\" data=\"${escapeXml(data)}\" />"
    }

    private fun wrapText(text: String, maxChars: Int = DEFAULT_MAX_LINE_CHARS): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        text.split(" ").forEach { word ->
            var remaining = word
            while (remaining.length > maxChars) {
                if (current.isNotEmpty()) { lines.add(current.toString()); current = StringBuilder() }
                lines.add(remaining.take(maxChars))
                remaining = remaining.drop(maxChars)
            }
            val candidate = if (current.isEmpty()) remaining else "$current $remaining"
            if (candidate.length > maxChars) {
                lines.add(current.toString())
                current = StringBuilder(remaining)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun printWrapped(label: String?, text: String, operator: String, maxChars: Int = DEFAULT_MAX_LINE_CHARS): String {
        val sb = StringBuilder()
        label?.let { sb.append(printLineRaw(it, operator)) }
        wrapText(text, maxChars).forEach { sb.append(printLineRaw(it, operator)) }
        return sb.toString()
    }

    private fun wrapInSoap(content: String): String {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "<s:Body>\n" +
                content +
                "\n</s:Body>\n" +
                "</s:Envelope>"
    }

    private fun escapeXml(data: String): String {
        return data.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

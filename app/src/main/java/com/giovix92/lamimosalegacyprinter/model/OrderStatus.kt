package com.giovix92.lamimosalegacyprinter.model

/**
 * Mirrors js/admin.js's STATUS_LABEL/STATUS_CYCLE/nextStatus() - text only.
 *
 * The status labels used to be emoji-prefixed ("🖨 Stampato", "🟡 Nuovo",
 * etc.) but most of those pictographs (added to Unicode well after 2012)
 * aren't in Jelly Bean's font at all and render as nothing/tofu - confirmed
 * on real hardware. Colour is conveyed with a real dot View instead (see
 * MainActivity's OrdersAdapter / item_order.xml), which works regardless of
 * font/emoji support since it's drawn, not a glyph.
 */
object OrderStatus {
    val LABEL = mapOf(
        "nuovo" to "Nuovo",
        "stampato" to "Stampato",
        "gestito" to "Gestito",
        "in_lavorazione" to "In Lavorazione",
        "consegnato" to "Consegnato",
        "archiviato" to "Archiviato"
    )

    // Hex strings (for HTML <font color> in OrderDetailActivity) and matching
    // ints (for the dot View's GradientDrawable) - kept as one map so they
    // can't drift out of sync with each other.
    val COLOR_HEX = mapOf(
        "nuovo" to "#F2A900",
        "stampato" to "#3B82C4",
        "gestito" to "#2E9E5B",
        "in_lavorazione" to "#8A5CF6",
        "consegnato" to "#2E9E5B",
        "archiviato" to "#8A6D72"
    )

    private val CYCLE = listOf("nuovo", "stampato", "in_lavorazione", "consegnato")

    fun next(current: String): String {
        val idx = CYCLE.indexOf(current)
        return CYCLE[(if (idx == -1) 0 else idx + 1) % CYCLE.size]
    }

    fun label(status: String): String = LABEL[status] ?: status

    fun colorHex(status: String): String = COLOR_HEX[status] ?: "#8A6D72"

    fun colorInt(status: String): Int = android.graphics.Color.parseColor(colorHex(status))
}

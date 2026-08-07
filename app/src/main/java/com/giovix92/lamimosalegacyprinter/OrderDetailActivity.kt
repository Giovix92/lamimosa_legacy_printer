package com.giovix92.lamimosalegacyprinter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.giovix92.lamimosalegacyprinter.model.Order
import com.giovix92.lamimosalegacyprinter.model.OrderStatus
import com.giovix92.lamimosalegacyprinter.net.MutationResult
import com.giovix92.lamimosalegacyprinter.net.OrderMutationClient
import com.giovix92.lamimosalegacyprinter.net.PrintResult
import com.giovix92.lamimosalegacyprinter.net.PrinterClient
import com.giovix92.lamimosalegacyprinter.net.PrinterPrefs
import com.giovix92.lamimosalegacyprinter.printer.EpsonFiscalXmlBuilder
import com.giovix92.lamimosalegacyprinter.util.showError
import org.json.JSONObject

/**
 * Full order detail (mirrors the expanded .order-card-detail block in
 * js/admin.js) plus the same action set: Stampa, Segna come <next status>,
 * Modifica, Archivia/Ripristina, Elimina.
 */
class OrderDetailActivity : Activity() {

    companion object {
        const val EXTRA_ORDER_JSON = "order_json"
    }

    private lateinit var orderJson: JSONObject
    private lateinit var order: Order
    private lateinit var printerPrefs: PrinterPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_detail)
        printerPrefs = PrinterPrefs(this)

        orderJson = JSONObject(intent.getStringExtra(EXTRA_ORDER_JSON) ?: "{}")
        order = Order.fromJson(orderJson)
        title = order.name.ifEmpty { order.id }

        render()

        findViewById<Button>(R.id.btnPrint).setOnClickListener { printOrder() }
        findViewById<Button>(R.id.btnEdit).setOnClickListener {
            startActivityForResult(
                Intent(this, OrderEditActivity::class.java).putExtra(OrderEditActivity.EXTRA_ORDER_JSON, orderJson.toString()),
                REQUEST_EDIT
            )
        }
        findViewById<Button>(R.id.btnDelete).setOnClickListener { confirmDelete() }
        updateStatusButtons()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT && resultCode == RESULT_OK) {
            // Order was PATCHed by OrderEditActivity - this screen's local copy is now
            // stale. Simplest correct thing: report "changed" upward and close, rather
            // than trying to patch our in-memory JSONObject back into sync by hand.
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun render() {
        val sb = StringBuilder()
        fun row(label: String, value: String?) {
            if (!value.isNullOrEmpty()) sb.append("<b>$label:</b> ${TextUtils.htmlEncode(value)}<br>")
        }
        sb.append("<b>Stato:</b> <font color=\"${OrderStatus.colorHex(order.status)}\"><b>${OrderStatus.label(order.status)}</b></font><br><br>")
        row("Cliente", order.name)
        row("Telefono", order.phone)
        row("Email", order.email)
        row("Operatore", order.operatore)
        row("Sede", order.sede)
        sb.append("<br>")
        if (order.categoria == "vassoio") {
            row("Categoria", "Vassoio")
            row("Peso", if (order.peso.isNotEmpty()) "${order.peso}kg" else null)
            row("Tipo Paste", order.tipoPaste)
        } else {
            row("Occasione", if (order.occasione == "altro") order.occasioneAltro else order.occasione)
            row("Sesso festeggiato", order.sesso)
            row("Età festeggiato", order.eta)
            row("Candeline", if (order.candeline == "Sì") "Sì (${order.numeroCandeline})" else order.candeline)
            row("Tipo Torta", if (order.tipologiaTorta == "altro") order.tipologiaTortaAltro else order.tipologiaTorta)
            if (order.gusti.isNotEmpty()) row("Gusti", order.gusti.joinToString(", "))
            row("Altro gusto", order.gustoTortaAltro)
            row("Aggiunta", order.topping)
            row("Panna", order.panna)
            row("Scritta", if (order.scritta == "Sì") "Sì — \"${order.testoScritta}\"" else "No")
            row("Cialda commestibile", if (order.stampa) "Sì" else "No")
            if (order.fotoEsempio) sb.append("<b>Foto esempio:</b> cliente ce l'ha già<br>")
        }
        sb.append("<br>")
        row("Data evento", order.eventDate)
        row("Orario", order.orario)
        row("Note", order.message)
        sb.append("<br>")
        row("Ordine creato il", order.createdAt)

        findViewById<TextView>(R.id.txtDetail).text = Html.fromHtml(sb.toString())
    }

    private fun updateStatusButtons() {
        val btnStatus = findViewById<Button>(R.id.btnStatus)
        val btnArchive = findViewById<Button>(R.id.btnArchive)

        if (order.status != "archiviato" && order.status != "gestito") {
            val next = OrderStatus.next(order.status)
            btnStatus.visibility = android.view.View.VISIBLE
            btnStatus.text = "Segna come ${OrderStatus.label(next)}"
            btnStatus.setOnClickListener { patchStatus(next, null) }
        } else {
            btnStatus.visibility = android.view.View.GONE
        }

        if (order.status == "archiviato") {
            btnArchive.text = "Ripristina"
            btnArchive.setOnClickListener { patchStatus(order.archivedFrom.ifEmpty { "consegnato" }, null) }
        } else {
            btnArchive.text = "Archivia"
            btnArchive.setOnClickListener { patchStatus("archiviato", order.status) }
        }
    }

    private fun patchStatus(newStatus: String, archivedFrom: String?) {
        val fields = JSONObject()
        fields.put("status", newStatus)
        if (archivedFrom != null) fields.put("archivedFrom", archivedFrom)
        PatchTask(fields) { result ->
            if (result.success) {
                Toast.makeText(this, "Aggiornato.", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                showError(result.errorMessage)
            }
        }.execute()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Eliminare questo ordine?")
            .setPositiveButton("Elimina") { _, _ ->
                DeleteTask { result ->
                    if (result.success) {
                        Toast.makeText(this, "Ordine eliminato.", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        showError(result.errorMessage)
                    }
                }.execute()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun printOrder() {
        if (!printerPrefs.isConfigured()) {
            Toast.makeText(this, R.string.configure_printer_first, Toast.LENGTH_LONG).show()
            return
        }
        PrintTask().execute()
    }

    private inner class PatchTask(
        private val fields: JSONObject,
        private val callback: (MutationResult) -> Unit
    ) : AsyncTask<Void, Void, MutationResult>() {
        override fun doInBackground(vararg params: Void?): MutationResult = OrderMutationClient.patch(order.id, fields)
        override fun onPostExecute(result: MutationResult) = callback(result)
    }

    private inner class DeleteTask(private val callback: (MutationResult) -> Unit) : AsyncTask<Void, Void, MutationResult>() {
        override fun doInBackground(vararg params: Void?): MutationResult = OrderMutationClient.delete(order.id)
        override fun onPostExecute(result: MutationResult) = callback(result)
    }

    private inner class PrintTask : AsyncTask<Void, Void, PrintResult>() {
        override fun doInBackground(vararg params: Void?): PrintResult {
            val xml = EpsonFiscalXmlBuilder.buildFromOrder(order, printerPrefs.operatorId, printerPrefs.maxLineChars)
            return PrinterClient.print(printerPrefs.ip, printerPrefs.port, printerPrefs.endpoint, xml)
        }

        override fun onPostExecute(result: PrintResult) {
            if (result.success) {
                Toast.makeText(this@OrderDetailActivity, R.string.print_success, Toast.LENGTH_SHORT).show()
            } else {
                showError(getString(R.string.print_failure, result.errorMessage ?: "errore sconosciuto"))
            }
        }
    }
}

private const val REQUEST_EDIT = 1001

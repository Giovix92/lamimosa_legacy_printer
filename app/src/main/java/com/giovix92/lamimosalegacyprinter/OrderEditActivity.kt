package com.giovix92.lamimosalegacyprinter

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.giovix92.lamimosalegacyprinter.model.Option
import com.giovix92.lamimosalegacyprinter.net.MutationResult
import com.giovix92.lamimosalegacyprinter.net.OrderConfig
import com.giovix92.lamimosalegacyprinter.net.OrderMutationClient
import com.giovix92.lamimosalegacyprinter.util.showError
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Full-parity edit form - mirrors js/admin.js's openOrderFormModal()/EDITABLE
 * field set in order.mjs (minus status/archivedFrom, which OrderDetailActivity's
 * own buttons handle, exactly like the web admin's card buttons vs. edit modal
 * split; and minus the legacy creme/gustoTorta fields).
 */
class OrderEditActivity : Activity() {

    companion object {
        const val EXTRA_ORDER_JSON = "order_json"
    }

    private lateinit var orderId: String
    private lateinit var orderJson: JSONObject
    private var selectedGusti: MutableList<String> = mutableListOf()

    // Views
    private lateinit var edName: EditText
    private lateinit var edPhone: EditText
    private lateinit var edEmail: EditText
    private lateinit var edOperatore: EditText
    private lateinit var spSede: Spinner
    private lateinit var spCategoria: Spinner
    private lateinit var groupTorta: View
    private lateinit var spOccasione: Spinner
    private lateinit var groupOccasioneAltro: View
    private lateinit var edOccasioneAltro: EditText
    private lateinit var spSesso: Spinner
    private lateinit var spEta: Spinner
    private lateinit var spCandeline: Spinner
    private lateinit var groupNumeroCandeline: View
    private lateinit var edNumeroCandeline: EditText
    private lateinit var spTipologia: Spinner
    private lateinit var groupTipologiaAltro: View
    private lateinit var edTipologiaAltro: EditText
    private lateinit var edPesoTorta: EditText
    private lateinit var groupGusti: View
    private lateinit var txtGustiSelected: TextView
    private lateinit var groupGustoAltro: View
    private lateinit var edGustoAltro: EditText
    private lateinit var groupTopping: View
    private lateinit var spTopping: Spinner
    private lateinit var spPanna: Spinner
    private lateinit var spScritta: Spinner
    private lateinit var groupTestoScritta: View
    private lateinit var edTestoScritta: EditText
    private lateinit var cbStampa: CheckBox
    private lateinit var cbFotoEsempio: CheckBox
    private lateinit var groupVassoio: View
    private lateinit var edPeso: EditText
    private lateinit var spTipoPaste: Spinner
    private lateinit var edEventDate: EditText
    private lateinit var edOrario: EditText
    private lateinit var edMessage: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_edit)
        title = getString(R.string.title_edit_order)

        orderJson = JSONObject(intent.getStringExtra(EXTRA_ORDER_JSON) ?: "{}")
        orderId = orderJson.optString("id")
        title = if (orderId.isEmpty()) getString(R.string.title_new_order) else getString(R.string.title_edit_order)
        val gustiArr = orderJson.optJSONArray("gusti")
        if (gustiArr != null) for (i in 0 until gustiArr.length()) selectedGusti.add(gustiArr.optString(i))

        bindViews()

        if (OrderConfig.loaded) {
            setupForm()
        } else {
            Toast.makeText(this, "Caricamento configurazione…", Toast.LENGTH_SHORT).show()
            ConfigFetchTask().execute()
        }
    }

    private fun bindViews() {
        edName = findViewById(R.id.edName)
        edPhone = findViewById(R.id.edPhone)
        edEmail = findViewById(R.id.edEmail)
        edOperatore = findViewById(R.id.edOperatore)
        spSede = findViewById(R.id.spSede)
        spCategoria = findViewById(R.id.spCategoria)
        groupTorta = findViewById(R.id.groupTorta)
        spOccasione = findViewById(R.id.spOccasione)
        groupOccasioneAltro = findViewById(R.id.groupOccasioneAltro)
        edOccasioneAltro = findViewById(R.id.edOccasioneAltro)
        spSesso = findViewById(R.id.spSesso)
        spEta = findViewById(R.id.spEta)
        spCandeline = findViewById(R.id.spCandeline)
        groupNumeroCandeline = findViewById(R.id.groupNumeroCandeline)
        edNumeroCandeline = findViewById(R.id.edNumeroCandeline)
        spTipologia = findViewById(R.id.spTipologia)
        groupTipologiaAltro = findViewById(R.id.groupTipologiaAltro)
        edTipologiaAltro = findViewById(R.id.edTipologiaAltro)
        edPesoTorta = findViewById(R.id.edPesoTorta)
        groupGusti = findViewById(R.id.groupGusti)
        txtGustiSelected = findViewById(R.id.txtGustiSelected)
        groupGustoAltro = findViewById(R.id.groupGustoAltro)
        edGustoAltro = findViewById(R.id.edGustoAltro)
        groupTopping = findViewById(R.id.groupTopping)
        spTopping = findViewById(R.id.spTopping)
        spPanna = findViewById(R.id.spPanna)
        spScritta = findViewById(R.id.spScritta)
        groupTestoScritta = findViewById(R.id.groupTestoScritta)
        edTestoScritta = findViewById(R.id.edTestoScritta)
        cbStampa = findViewById(R.id.cbStampa)
        cbFotoEsempio = findViewById(R.id.cbFotoEsempio)
        groupVassoio = findViewById(R.id.groupVassoio)
        edPeso = findViewById(R.id.edPeso)
        spTipoPaste = findViewById(R.id.spTipoPaste)
        edEventDate = findViewById(R.id.edEventDate)
        edOrario = findViewById(R.id.edOrario)
        edMessage = findViewById(R.id.edMessage)
    }

    // ── Setup & prefill ─────────────────────────────────────────────────

    private fun setupForm() {
        edName.setText(orderJson.optString("name"))
        edPhone.setText(orderJson.optString("phone"))
        edEmail.setText(orderJson.optString("email"))
        edOperatore.setText(orderJson.optString("operatore"))
        edOccasioneAltro.setText(orderJson.optString("occasioneAltro"))
        edNumeroCandeline.setText(orderJson.optString("numeroCandeline"))
        edTipologiaAltro.setText(orderJson.optString("tipologiaTortaAltro"))
        edGustoAltro.setText(orderJson.optString("gustoTortaAltro"))
        edTestoScritta.setText(orderJson.optString("testoScritta"))
        edPeso.setText(orderJson.optString("peso"))
        edPesoTorta.setText(orderJson.optString("peso"))
        // New order: default to today, like admin.js's "Nuovo Ordine" (avoids an
        // empty-looking summary and matches what /ordina prefills too).
        val defaultDate = if (orderId.isEmpty()) java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) else ""
        edEventDate.setText(orderJson.optString("eventDate", defaultDate).ifEmpty { defaultDate })
        edOrario.setText(orderJson.optString("orario"))
        edMessage.setText(orderJson.optString("message"))
        cbStampa.isChecked = orderJson.optBoolean("stampa")
        cbFotoEsempio.isChecked = orderJson.optBoolean("fotoEsempio")

        setupSpinner(spSede, OrderConfig.sedi, orderJson.optString("sede"))
        setupSpinner(spCategoria, OrderConfig.CATEGORIE_ORDINE, orderJson.optString("categoria", "torta"))
        setupSpinner(spOccasione, OrderConfig.occasioni, orderJson.optString("occasione"))
        setupSpinner(spSesso, OrderConfig.SESSO_OPZIONI, orderJson.optString("sesso", "neutro"))
        setupSpinner(spEta, OrderConfig.ETA_OPZIONI, orderJson.optString("eta"))
        setupSpinner(spCandeline, OrderConfig.scrittaOpzioni, orderJson.optString("candeline", "No"))
        setupSpinner(spTipologia, OrderConfig.tipologieTorta.map { Option(it.id, it.labelIt) }, orderJson.optString("tipologiaTorta"))
        setupSpinner(spPanna, OrderConfig.pannaOpzioni, orderJson.optString("panna"))
        setupSpinner(spScritta, OrderConfig.scrittaOpzioni, orderJson.optString("scritta", "No"))
        setupSpinner(spTipoPaste, OrderConfig.tipiPasta, orderJson.optString("tipoPaste"))

        renderGustiAndTopping()

        // Listeners
        spCategoria.onSelected { updateCategoria() }
        spOccasione.onSelected { groupOccasioneAltro.visibility = visIf(selectedId(spOccasione) == OrderConfig.ID_ALTRO) }
        spCandeline.onSelected { groupNumeroCandeline.visibility = visIf(selectedId(spCandeline) == "Sì") }
        spScritta.onSelected { groupTestoScritta.visibility = visIf(selectedId(spScritta) == "Sì") }
        spTipologia.onSelected { renderGustiAndTopping() }

        findViewById<android.widget.Button>(R.id.btnGusti).setOnClickListener { showGustiDialog() }
        findViewById<android.widget.Button>(R.id.btnPickDate).setOnClickListener { pickDate() }
        findViewById<android.widget.Button>(R.id.btnPickTime).setOnClickListener { pickTime() }
        findViewById<android.widget.Button>(R.id.btnSave).setOnClickListener { save() }

        updateCategoria()
        groupOccasioneAltro.visibility = visIf(selectedId(spOccasione) == OrderConfig.ID_ALTRO)
        groupNumeroCandeline.visibility = visIf(selectedId(spCandeline) == "Sì")
        groupTestoScritta.visibility = visIf(selectedId(spScritta) == "Sì")
    }

    private fun setupSpinner(spinner: Spinner, options: List<Option>, selectedId: String) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val idx = options.indexOfFirst { it.id == selectedId }
        if (idx >= 0) spinner.setSelection(idx)
    }

    private fun selectedId(spinner: Spinner): String? = (spinner.selectedItem as? Option)?.id

    private fun Spinner.onSelected(action: () -> Unit) {
        this.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = action()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun visIf(condition: Boolean): Int = if (condition) View.VISIBLE else View.GONE

    private fun updateCategoria() {
        val vassoio = selectedId(spCategoria) == "vassoio"
        groupTorta.visibility = visIf(!vassoio)
        groupVassoio.visibility = visIf(vassoio)
    }

    // ── Gusti / Topping (ported from ofRenderGustiOptions/ofRenderToppingOptions) ──

    private fun renderGustiAndTopping() {
        val tipologiaId = selectedId(spTipologia)
        val disabilitati = OrderConfig.gustiDisabilitatiPerTipologia(tipologiaId)
        val list = OrderConfig.gustiPerTipologia(tipologiaId)
        val liberi = list.isEmpty() && !disabilitati
        groupGusti.visibility = visIf(!liberi && !disabilitati)
        selectedGusti = selectedGusti.filter { sel -> list.any { it.id == sel } }.toMutableList()
        updateGustiSummary()
        updateGustoAltroVisibility(liberi, disabilitati)

        val toppingOptions = OrderConfig.toppingPerTipologia(tipologiaId)
        groupTopping.visibility = visIf(toppingOptions.isNotEmpty())
        if (toppingOptions.isNotEmpty()) {
            setupSpinner(spTopping, toppingOptions, orderJson.optString("topping").ifEmpty { OrderConfig.ID_NESSUNO })
        }
    }

    private fun updateGustoAltroVisibility(liberiIn: Boolean? = null, disabilitatiIn: Boolean? = null) {
        val tipologiaId = selectedId(spTipologia)
        val disabilitati = disabilitatiIn ?: OrderConfig.gustiDisabilitatiPerTipologia(tipologiaId)
        val liberi = liberiIn ?: (OrderConfig.gustiPerTipologia(tipologiaId).isEmpty() && !disabilitati)
        val isAltro = selectedGusti.contains(OrderConfig.ID_ALTRO)
        val show = !disabilitati && (liberi || isAltro)
        groupGustoAltro.visibility = visIf(show)
    }

    private fun updateGustiSummary() {
        txtGustiSelected.text = if (selectedGusti.isEmpty()) {
            "Nessun gusto selezionato"
        } else {
            val labels = OrderConfig.gustiTorta.filter { selectedGusti.contains(it.id) }.map { it.labelIt }
            labels.joinToString(", ")
        }
    }

    private fun showGustiDialog() {
        val tipologiaId = selectedId(spTipologia)
        val options = OrderConfig.gustiPerTipologia(tipologiaId)
        if (options.isEmpty()) return
        val labels = options.map { it.labelIt }.toTypedArray()
        val checked = options.map { selectedGusti.contains(it.id) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Gusti (max ${OrderConfig.GUSTI_MAX_SELEZIONABILI})")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                val picked = options.filterIndexed { i, _ -> checked[i] }.map { it.id }
                if (picked.size > OrderConfig.GUSTI_MAX_SELEZIONABILI) {
                    Toast.makeText(this, "Massimo ${OrderConfig.GUSTI_MAX_SELEZIONABILI} gusti: ho tenuto solo i primi ${OrderConfig.GUSTI_MAX_SELEZIONABILI}.", Toast.LENGTH_LONG).show()
                    selectedGusti = picked.take(OrderConfig.GUSTI_MAX_SELEZIONABILI).toMutableList()
                } else {
                    selectedGusti = picked.toMutableList()
                }
                updateGustiSummary()
                updateGustoAltroVisibility()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ── Date / time pickers ──────────────────────────────────────────────

    private fun pickDate() {
        val cal = Calendar.getInstance()
        val parts = edEventDate.text.toString().split("-")
        if (parts.size == 3) {
            parts[0].toIntOrNull()?.let { cal.set(Calendar.YEAR, it) }
            parts[1].toIntOrNull()?.let { cal.set(Calendar.MONTH, it - 1) }
            parts[2].toIntOrNull()?.let { cal.set(Calendar.DAY_OF_MONTH, it) }
        }
        DatePickerDialog(this, { _, year, month, day ->
            edEventDate.setText(String.format("%04d-%02d-%02d", year, month + 1, day))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime() {
        val cal = Calendar.getInstance()
        val parts = edOrario.text.toString().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.HOUR_OF_DAY)
        val m = parts.getOrNull(1)?.toIntOrNull() ?: cal.get(Calendar.MINUTE)
        TimePickerDialog(this, { _, hour, minute ->
            edOrario.setText(String.format("%02d:%02d", hour, minute))
        }, h, m, true).show()
    }

    // ── Save ──────────────────────────────────────────────────────────

    private fun save() {
        val vassoio = selectedId(spCategoria) == "vassoio"
        val fields = JSONObject()
        fields.put("name", edName.text.toString().trim())
        fields.put("phone", edPhone.text.toString().trim())
        fields.put("email", edEmail.text.toString().trim())
        fields.put("operatore", edOperatore.text.toString().trim())
        fields.put("sede", selectedId(spSede) ?: "")
        fields.put("categoria", if (vassoio) "vassoio" else "torta")

        if (vassoio) {
            fields.put("peso", edPeso.text.toString().trim())
            fields.put("tipoPaste", selectedId(spTipoPaste) ?: "")
            fields.put("occasione", "")
            fields.put("occasioneAltro", "")
            fields.put("sesso", "neutro")
            fields.put("eta", "")
            fields.put("candeline", "No")
            fields.put("numeroCandeline", "")
            fields.put("tipologiaTorta", "")
            fields.put("tipologiaTortaAltro", "")
            fields.put("gusti", JSONArray())
            fields.put("gustoTortaAltro", "")
            fields.put("topping", "")
            fields.put("panna", "")
            fields.put("scritta", "No")
            fields.put("testoScritta", "")
            fields.put("stampa", false)
            fields.put("fotoEsempio", false)
        } else {
            fields.put("peso", edPesoTorta.text.toString().trim())
            fields.put("tipoPaste", "")
            fields.put("occasione", selectedId(spOccasione) ?: "")
            fields.put("occasioneAltro", edOccasioneAltro.text.toString().trim())
            fields.put("sesso", selectedId(spSesso) ?: "neutro")
            fields.put("eta", selectedId(spEta) ?: "")
            fields.put("candeline", selectedId(spCandeline) ?: "No")
            fields.put("numeroCandeline", edNumeroCandeline.text.toString().trim())
            fields.put("tipologiaTorta", selectedId(spTipologia) ?: "")
            fields.put("tipologiaTortaAltro", edTipologiaAltro.text.toString().trim())
            fields.put("gusti", JSONArray(selectedGusti))
            fields.put("gustoTortaAltro", edGustoAltro.text.toString().trim())
            fields.put("topping", if (groupTopping.visibility == View.VISIBLE) (selectedId(spTopping) ?: "") else "")
            fields.put("panna", selectedId(spPanna) ?: "")
            fields.put("scritta", selectedId(spScritta) ?: "No")
            fields.put("testoScritta", edTestoScritta.text.toString().trim())
            fields.put("stampa", cbStampa.isChecked)
            fields.put("fotoEsempio", cbFotoEsempio.isChecked)
        }

        fields.put("eventDate", edEventDate.text.toString().trim())
        fields.put("orario", edOrario.text.toString().trim())
        fields.put("message", edMessage.text.toString().trim())

        SaveTask(fields).execute()
    }

    private inner class ConfigFetchTask : AsyncTask<Void, Void, Boolean>() {
        override fun doInBackground(vararg params: Void?): Boolean = OrderConfig.fetch()
        override fun onPostExecute(success: Boolean) {
            if (success) {
                setupForm()
            } else {
                showError("Impossibile caricare la configurazione ordini. Controlla la connessione e riprova.", onDismiss = { finish() })
            }
        }
    }

    private inner class SaveTask(private val fields: JSONObject) : AsyncTask<Void, Void, MutationResult>() {
        override fun doInBackground(vararg params: Void?): MutationResult =
            if (orderId.isEmpty()) OrderMutationClient.create(fields) else OrderMutationClient.patch(orderId, fields)

        override fun onPostExecute(result: MutationResult) {
            if (result.success) {
                val message = if (orderId.isEmpty()) "Ordine creato." else "Ordine aggiornato."
                Toast.makeText(this@OrderEditActivity, message, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                showError(result.errorMessage)
            }
        }
    }
}

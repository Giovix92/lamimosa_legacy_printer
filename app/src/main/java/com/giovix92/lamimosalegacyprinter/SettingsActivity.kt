package com.giovix92.lamimosalegacyprinter

import android.app.Activity
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.giovix92.lamimosalegacyprinter.net.PrintResult
import com.giovix92.lamimosalegacyprinter.net.PrinterClient
import com.giovix92.lamimosalegacyprinter.net.PrinterPrefs
import com.giovix92.lamimosalegacyprinter.printer.EpsonFiscalXmlBuilder
import com.giovix92.lamimosalegacyprinter.service.OrderCheckService
import com.giovix92.lamimosalegacyprinter.util.showError

class SettingsActivity : Activity() {

    private lateinit var prefs: PrinterPrefs
    private lateinit var editIp: EditText
    private lateinit var editPort: EditText
    private lateinit var editEndpoint: EditText
    private lateinit var editOperator: EditText
    private lateinit var editMaxLineChars: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = PrinterPrefs(this)

        editIp = findViewById(R.id.editIp)
        editPort = findViewById(R.id.editPort)
        editEndpoint = findViewById(R.id.editEndpoint)
        editOperator = findViewById(R.id.editOperator)
        editMaxLineChars = findViewById(R.id.editMaxLineChars)

        editIp.setText(prefs.ip)
        editPort.setText(prefs.port.toString())
        editEndpoint.setText(prefs.endpoint)
        editOperator.setText(prefs.operatorId)
        editMaxLineChars.setText(prefs.maxLineChars.toString())

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.ip = editIp.text.toString().trim()
            prefs.port = editPort.text.toString().trim().toIntOrNull() ?: 80
            prefs.endpoint = editEndpoint.text.toString().trim().ifEmpty { "/cgi-bin/fpmate.cgi" }
            prefs.operatorId = editOperator.text.toString().trim().ifEmpty { "1" }
            prefs.maxLineChars = editMaxLineChars.text.toString().trim().toIntOrNull() ?: 32
            Toast.makeText(this, "Impostazioni salvate", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Deliberately tests against whatever is currently typed, not what's
        // saved - lets staff verify an IP/port change before committing to it.
        findViewById<Button>(R.id.btnTestPrint).setOnClickListener { testPrint() }

        // On-demand trigger for the background order-check (service/OrderCheckService.kt)
        // instead of waiting up to 5 minutes for the next AlarmManager tick - mainly
        // for verifying the notification actually fires without a long wait.
        findViewById<Button>(R.id.btnCheckOrdersNow).setOnClickListener {
            startService(Intent(this, OrderCheckService::class.java))
            Toast.makeText(this, R.string.check_orders_now_started, Toast.LENGTH_LONG).show()
        }
    }

    private fun testPrint() {
        val ip = editIp.text.toString().trim()
        if (ip.isEmpty()) {
            showError("Inserisci prima l'IP della stampante.")
            return
        }
        val port = editPort.text.toString().trim().toIntOrNull() ?: 80
        val endpoint = editEndpoint.text.toString().trim().ifEmpty { "/cgi-bin/fpmate.cgi" }
        val operatorId = editOperator.text.toString().trim().ifEmpty { "1" }

        TestPrintTask(ip, port, endpoint, operatorId).execute()
    }

    private inner class TestPrintTask(
        private val ip: String,
        private val port: Int,
        private val endpoint: String,
        private val operatorId: String
    ) : AsyncTask<Void, Void, PrintResult>() {
        override fun doInBackground(vararg params: Void?): PrintResult {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val xml = EpsonFiscalXmlBuilder.buildTestPage(operatorId, deviceName, appVersion = "1.0")
            return PrinterClient.print(ip, port, endpoint, xml)
        }

        override fun onPostExecute(result: PrintResult) {
            if (result.success) {
                Toast.makeText(this@SettingsActivity, R.string.test_print_success, Toast.LENGTH_SHORT).show()
            } else {
                showError(getString(R.string.test_print_failure, result.errorMessage ?: "errore sconosciuto"))
            }
        }
    }
}

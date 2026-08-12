package com.giovix92.lamimosalegacyprinter

import android.app.Activity
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.giovix92.lamimosalegacyprinter.model.OrderStatus
import com.giovix92.lamimosalegacyprinter.net.OrderEntry
import com.giovix92.lamimosalegacyprinter.net.OrdersApiClient
import com.giovix92.lamimosalegacyprinter.net.OrdersResult
import com.giovix92.lamimosalegacyprinter.util.showError

class MainActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var txtStatus: TextView
    private var entries: List<OrderEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // TLS setup + background order-check alarm scheduling now live in
        // App.onCreate() (see App.kt) - it always runs before any Activity,
        // including when a background component starts the process first.

        listView = findViewById(R.id.listOrders)
        txtStatus = findViewById(R.id.txtStatus)

        findViewById<Button>(R.id.btnRefresh).setOnClickListener { loadOrders() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnNewOrder).setOnClickListener {
            // Empty JSON == "create" mode for OrderEditActivity (no "id" field).
            startActivityForResult(
                Intent(this, OrderEditActivity::class.java).putExtra(OrderEditActivity.EXTRA_ORDER_JSON, "{}"),
                REQUEST_DETAIL
            )
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            startActivityForResult(
                Intent(this, OrderDetailActivity::class.java)
                    .putExtra(OrderDetailActivity.EXTRA_ORDER_JSON, entries[position].json.toString()),
                REQUEST_DETAIL
            )
        }

        loadOrders()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Any change made in OrderDetailActivity (print doesn't set RESULT_OK,
        // status/archive/delete/edit do) invalidates our cached list.
        if (requestCode == REQUEST_DETAIL && resultCode == RESULT_OK) {
            loadOrders()
        }
    }

    private fun loadOrders() {
        txtStatus.text = getString(R.string.loading)
        FetchOrdersTask().execute()
    }

    private inner class FetchOrdersTask : AsyncTask<Void, Void, OrdersResult>() {
        override fun doInBackground(vararg params: Void?): OrdersResult = OrdersApiClient.fetchOrders()

        override fun onPostExecute(result: OrdersResult) {
            when (result) {
                is OrdersResult.Success -> {
                    entries = result.entries
                    listView.adapter = OrdersAdapter(entries)
                    txtStatus.text = if (entries.isEmpty()) getString(R.string.no_orders) else "${entries.size} ordini"
                }
                is OrdersResult.Failure -> {
                    txtStatus.text = result.message
                    showError(result.message)
                }
            }
        }
    }

    private inner class OrdersAdapter(private val items: List<OrderEntry>) : ArrayAdapter<OrderEntry>(this@MainActivity, R.layout.item_order, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_order, parent, false)
            val order = items[position].order

            view.findViewById<TextView>(R.id.txtTitle).text = order.name.ifEmpty { order.id }

            val product = if (order.categoria == "vassoio") {
                "Vassoio${if (order.peso.isNotEmpty()) " ${order.peso}kg" else ""}"
            } else {
                order.tipologiaTorta.ifEmpty { "Torta" } + (if (order.peso.isNotEmpty()) " ${order.peso}kg" else "")
            }
            view.findViewById<TextView>(R.id.txtSubtitle).text = "${OrderStatus.label(order.status)} · $product"
            // .mutate() is required: drawables inflated from the same XML resource
            // share a ConstantState by default, so setColor() without it would
            // recolor every row in the list to whichever status was set last.
            val dot = view.findViewById<View>(R.id.dotStatus).background?.mutate()
            if (dot is android.graphics.drawable.GradientDrawable) dot.setColor(OrderStatus.colorInt(order.status))

            val meta = listOf(order.sede, order.orario, order.eventDate).filter { it.isNotEmpty() }.joinToString(" · ")
            view.findViewById<TextView>(R.id.txtMeta).text = meta

            return view
        }
    }
}

private const val REQUEST_DETAIL = 2001

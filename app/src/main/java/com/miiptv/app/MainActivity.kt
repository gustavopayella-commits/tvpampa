package com.miiptv.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var host: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var status: TextView
    private lateinit var list: LinearLayout

    private var api: XtreamApi? = null
    private var allChannels: List<Channel> = emptyList()
    private var categories: List<Category> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.rgb(16, 18, 26))
        }

        val title = TextView(this).apply {
            text = "MI IPTV"
            textSize = 26f
            setTextColor(Color.WHITE)
            setPadding(8, 12, 8, 12)
        }
        root.addView(title)

        host = EditText(this).apply {
            setText("tv.m3uts.xyz")
            hint = "Servidor / Host"
            setSingleLine(true)
            setTextColor(Color.WHITE)
        }
        root.addView(host)

        username = EditText(this).apply {
            setText("m")
            hint = "Usuario"
            setSingleLine(true)
            setTextColor(Color.WHITE)
        }
        root.addView(username)

        password = EditText(this).apply {
            setText("m")
            hint = "Contraseña"
            setSingleLine(true)
            setTextColor(Color.WHITE)
        }
        root.addView(password)

        val connectButton = Button(this).apply {
            text = "Conectar e importar lista M3U"
        }
        root.addView(connectButton)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val liveButton = Button(this).apply { text = "TV EN VIVO" }
        val eventsButton = Button(this).apply { text = "EVENTOS" }
        tabs.addView(liveButton, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(eventsButton, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(tabs)

        status = TextView(this).apply {
            text = "Ingresá los datos y tocá Conectar."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(8, 12, 8, 12)
        }
        root.addView(status)

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        connectButton.setOnClickListener(View.OnClickListener {
            list.removeAllViews()
            status.text = "Descargando lista M3U original del servidor..."

            val server = host.text.toString().trim()
            val user = username.text.toString().trim()
            val pass = password.text.toString()
            if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                status.text = "Completá servidor, usuario y contraseña."
                return@OnClickListener
            }

            api = XtreamApi(server, user, pass)
            scope.launch {
                try {
                    val connected = api!!
                    allChannels = connected.channels()
                    categories = connected.categories()
                    status.text = "Lista M3U importada • ${allChannels.size} canales • ${categories.size} grupos"
                    showChannels(allChannels)
                } catch (e: Exception) {
                    status.text = "Error importando M3U:\n${e.message ?: e.javaClass.simpleName}"
                }
            }
        })

        liveButton.setOnClickListener(View.OnClickListener {
            showChannels(allChannels)
        })

        eventsButton.setOnClickListener(View.OnClickListener {
            showEvents()
        })
    }

    private fun showChannels(items: List<Channel>) {
        list.removeAllViews()

        if (items.isEmpty()) {
            val msg = TextView(this).apply {
                text = "Conectá para importar los canales."
                setTextColor(Color.WHITE)
                setPadding(12, 16, 12, 16)
            }
            list.addView(msg)
            return
        }

        val options = listOf("Todos los grupos") + categories.map { it.name }
        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        list.addView(spinner)

        val channelBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addView(channelBox)

        fun populate(groupName: String) {
            channelBox.removeAllViews()
            val filtered = if (groupName == "Todos los grupos") items else items.filter { it.categoryId == groupName }
            filtered.forEach { channel ->
                val button = Button(this).apply {
                    text = "▶  ${channel.name}"
                    setOnClickListener(View.OnClickListener {
                        val currentApi = api
                        if (currentApi == null) {
                            status.text = "Primero conectá al servidor."
                            return@OnClickListener
                        }
                        val intent = Intent(this@MainActivity, PlayerActivity::class.java)
                        intent.putExtra("title", channel.name)
                        // Stream URL comes from the M3U itself and is not rewritten.
                        intent.putExtra("url", currentApi.streamUrl(channel))
                        intent.putExtra("alternative_url", "")
                        startActivity(intent)
                    })
                }
                channelBox.addView(button)
            }
            if (filtered.isEmpty()) {
                channelBox.addView(TextView(this).apply {
                    text = "No hay canales en este grupo."
                    setTextColor(Color.WHITE)
                    setPadding(12, 16, 12, 16)
                })
            }
        }

        populate(options.first())
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                populate(options[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun showEvents() {
        list.removeAllViews()
        list.addView(TextView(this).apply {
            text = """
                EVENTOS

                Sección preparada para:
                • Eventos deportivos
                • Fútbol y boxeo
                • Shows y espectáculos
                • Agenda y horarios
                • Imágenes y enlaces de reproducción

                Los eventos se agregarán desde una futura administración.
            """.trimIndent()
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
        })
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

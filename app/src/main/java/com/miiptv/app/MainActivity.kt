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

    private val scope = CoroutineScope(
        Dispatchers.Main + SupervisorJob()
    )

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

        // CONTENEDOR PRINCIPAL

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(18, 18, 18, 18)
        root.setBackgroundColor(Color.rgb(16, 18, 26))

        // TÍTULO

        val title = TextView(this)
        title.text = "MI IPTV"
        title.textSize = 26f
        title.setTextColor(Color.WHITE)
        title.setPadding(8, 12, 8, 12)

        root.addView(title)

        // SERVIDOR

        host = EditText(this)
        host.setText("tv.m3uts.xyz")
        host.hint = "Servidor / Host"
        host.setSingleLine(true)
        host.setTextColor(Color.WHITE)

        root.addView(host)

        // USUARIO

        username = EditText(this)
        username.setText("m")
        username.hint = "Usuario"
        username.setSingleLine(true)
        username.setTextColor(Color.WHITE)

        root.addView(username)

        // CONTRASEÑA

        password = EditText(this)
        password.setText("m")
        password.hint = "Contraseña"
        password.setSingleLine(true)
        password.setTextColor(Color.WHITE)

        root.addView(password)

        // BOTÓN CONECTAR

        val connectButton = Button(this)
        connectButton.text = "Conectar e importar canales"

        root.addView(connectButton)

        // MENÚ TV / EVENTOS

        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL

        val liveButton = Button(this)
        liveButton.text = "TV EN VIVO"

        val eventsButton = Button(this)
        eventsButton.text = "EVENTOS"

        tabs.addView(
            liveButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        tabs.addView(
            eventsButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(tabs)

        // ESTADO

        status = TextView(this)
        status.text = "Ingresá los datos y tocá Conectar."
        status.textSize = 14f
        status.setTextColor(Color.WHITE)
        status.setPadding(8, 12, 8, 12)

        root.addView(status)

        // LISTA DESPLAZABLE

        val scrollView = ScrollView(this)

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL

        scrollView.addView(list)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        // CONECTAR E IMPORTAR

        connectButton.setOnClickListener(
            View.OnClickListener {

                status.text = "Conectando al servidor..."
                list.removeAllViews()

                val server = host.text.toString().trim()
                val user = username.text.toString().trim()
                val pass = password.text.toString()

                if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                    status.text = "Completá servidor, usuario y contraseña."
                    return@OnClickListener
                }

                api = XtreamApi(server, user, pass)

                scope.launch {

                    try {

                        status.text = "Importando categorías..."

                        val connectedApi = api!!

                        categories = connectedApi.categories()

                        status.text = "Importando canales..."

                        allChannels = connectedApi.channels()

                        status.text =
                            "Conectado • ${allChannels.size} canales • ${categories.size} categorías"

                        showChannels(allChannels)

                    } catch (e: Exception) {

                        status.text =
                            "Error de conexión:\n${e.message ?: "No se pudo conectar al servidor"}"

                    }
                }
            }
        )

        // BOTÓN TV EN VIVO

        liveButton.setOnClickListener(
            View.OnClickListener {

                showChannels(allChannels)

            }
        )

        // BOTÓN EVENTOS

        eventsButton.setOnClickListener(
            View.OnClickListener {

                showEvents()

            }
        )
    }

    // ==================================================
    // MOSTRAR CANALES
    // ==================================================

    private fun showChannels(items: List<Channel>) {

        list.removeAllViews()

        if (items.isEmpty()) {

            val message = TextView(this)

            message.text = "Conectá para cargar los canales."
            message.setTextColor(Color.WHITE)
            message.textSize = 16f
            message.setPadding(12, 16, 12, 16)

            list.addView(message)

            return
        }

        // SPINNER CATEGORÍAS

        val spinner = Spinner(this)

        val options = mutableListOf<String>()

        options.add("Todas las categorías")

        for (category in categories) {
            options.add(category.name)
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        spinner.adapter = adapter

        list.addView(spinner)

        // CONTENEDOR DE CANALES

        val channelBox = LinearLayout(this)
        channelBox.orientation = LinearLayout.VERTICAL

        list.addView(channelBox)

        // FILTRAR CANALES

        fun populateChannels(categoryName: String) {

            channelBox.removeAllViews()

            val selectedCategory = categories.find {
                it.name == categoryName
            }

            val filteredChannels =
                if (selectedCategory == null) {
                    items
                } else {
                    items.filter {
                        it.categoryId == selectedCategory.id
                    }
                }

            // BOTONES DE CANALES

            for (channel in filteredChannels) {

                val channelButton = Button(this)

                channelButton.text = "▶  ${channel.name}"

                channelButton.setOnClickListener(
                    View.OnClickListener {

                        val currentApi = api

                        if (currentApi == null) {

                            status.text =
                                "Primero conectá al servidor."

                            return@OnClickListener
                        }

                        // ABRIR REPRODUCTOR

                        val intent = Intent(
                            this@MainActivity,
                            PlayerActivity::class.java
                        )

                        intent.putExtra(
                            "title",
                            channel.name
                        )

                        // URL PRINCIPAL TS

                        intent.putExtra(
                            "url",
                            currentApi.streamUrl(channel)
                        )

                        // URL ALTERNATIVA HLS

                        intent.putExtra(
                            "alternative_url",
                            currentApi.streamUrlHls(channel)
                        )

                        startActivity(intent)

                    }
                )

                channelBox.addView(channelButton)
            }

            // SI NO HAY CANALES

            if (filteredChannels.isEmpty()) {

                val emptyMessage = TextView(this)

                emptyMessage.text =
                    "No hay canales en esta categoría."

                emptyMessage.setTextColor(Color.WHITE)
                emptyMessage.textSize = 16f
                emptyMessage.setPadding(12, 16, 12, 16)

                channelBox.addView(emptyMessage)
            }
        }

        // CARGA INICIAL

        populateChannels("Todas las categorías")

        // CAMBIO DE CATEGORÍA

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    val selectedName = options[position]

                    populateChannels(selectedName)
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                    // No hay selección
                }
            }
    }

    // ==================================================
    // EVENTOS
    // ==================================================

    private fun showEvents() {

        list.removeAllViews()

        val eventsText = TextView(this)

        eventsText.text = """
            EVENTOS EN VIVO

            Próximas funciones:

            • Eventos deportivos
            • Fútbol
            • Boxeo
            • Shows y espectáculos
            • Eventos próximos
            • Fecha y horario
            • Imágenes de eventos
            • Reproductor de eventos
            • Administración de eventos

            Los eventos se podrán agregar
            y administrar en futuras versiones.
        """.trimIndent()

        eventsText.textSize = 16f
        eventsText.setTextColor(Color.WHITE)
        eventsText.setPadding(16, 16, 16, 16)

        list.addView(eventsText)
    }

    // ==================================================
    // CERRAR ACTIVIDAD
    // ==================================================

    override fun onDestroy() {

        scope.cancel()

        super.onDestroy()
    }
}
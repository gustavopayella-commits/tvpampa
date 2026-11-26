package com.miiptv.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import kotlinx.coroutines.*

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
    private var eventsMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,18,18,18); setBackgroundColor(Color.rgb(16,18,26)) }
        fun text(s: String, size: Float=18f) = TextView(this).apply { text=s; textSize=size; setTextColor(Color.WHITE); setPadding(8,12) }
        root.addView(text("MI IPTV", 26f))
        host = EditText(this).apply { setText("tv.m3uts.xyz"); hint="Servidor / Host"; setSingleLine(); setTextColor(Color.WHITE) }
        username = EditText(this).apply { setText("m"); hint="Usuario"; setSingleLine(); setTextColor(Color.WHITE) }
        password = EditText(this).apply { setText("m"); hint="Contraseña"; setSingleLine(); setTextColor(Color.WHITE) }
        root.addView(host); root.addView(username); root.addView(password)
        val connect = Button(this).apply { text="Conectar e importar canales" }
        root.addView(connect)
        val tabs = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val live = Button(this).apply { text="TV EN VIVO" }
        val events = Button(this).apply { text="EVENTOS" }
        tabs.addView(live, LinearLayout.LayoutParams(0, -2, 1f)); tabs.addView(events, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(tabs)
        status = text("Ingresá los datos y tocá Conectar.", 14f); root.addView(status)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(list); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)

        connect.setOnClickListener {
            status.text = "Conectando…"; list.removeAllViews()
            api = XtreamApi(host.text.toString(), username.text.toString(), password.text.toString())
            scope.launch {
                try {
                    categories = api!!.categories()
                    allChannels = api!!.channels()
                    status.text = "Conectado • ${allChannels.size} canales • ${categories.size} categorías"
                    showChannels(allChannels)
                } catch (e: Exception) { status.text = "Error de conexión: ${e.message ?: "revisá servidor y credenciales"}" }
            }
        }
        live.setOnClickListener { eventsMode=false; showChannels(allChannels) }
        events.setOnClickListener { eventsMode=true; showEvents() }
    }

    private fun showChannels(items: List<Channel>) {
        list.removeAllViews()
        if (items.isEmpty()) { list.addView(TextView(this).apply { text="Conectá para cargar canales."; setTextColor(Color.WHITE); setPadding(12,16,12,16) }); return }
        val filter = Spinner(this)
        val opts = listOf("Todas las categorías") + categories.map { it.name }
        filter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opts)
        list.addView(filter)
        val channelBox = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        list.addView(channelBox)
        fun populate(categoryName: String) {
            channelBox.removeAllViews()
            val categoryId = categories.find { it.name == categoryName }?.id
            val filtered = if (categoryId == null) items else items.filter { it.categoryId == categoryId }
            filtered.forEach { ch ->
                val b = Button(this).apply { text="▶  ${ch.name}"; setOnClickListener {
                    val intent=Intent(this@MainActivity, PlayerActivity::class.java)
                    intent.putExtra("title", ch.name); intent.putExtra("url", api!!.streamUrl(ch)); startActivity(intent)
                } }
                channelBox.addView(b)
            }
        }
        populate(opts.first())
        filter.onItemSelectedListener = object: android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = populate(opts[pos])
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }
    private fun showEvents() {
        list.removeAllViews()
        list.addView(TextView(this).apply {
            text = "EVENTOS\n\nEsta sección queda preparada para eventos en vivo y próximos eventos.\n\nPara agregarlos, se podrán incorporar desde una futura pantalla de administración con título, imagen, fecha, horario, categoría y enlace de reproducción.\n\nLos eventos no se inventan ni se extraen automáticamente de la lista de canales."
            textSize=16f; setTextColor(Color.WHITE); setPadding(16,16,16,16)
        })
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

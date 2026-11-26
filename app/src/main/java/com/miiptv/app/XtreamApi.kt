package com.miiptv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Channel(
    val id: String,
    val name: String,
    val categoryId: String,
    val icon: String?
)

data class Category(
    val id: String,
    val name: String
)

class XtreamApi(
    private val host: String,
    private val user: String,
    private val pass: String
) {

    private fun enc(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private val root: String = host.trim()
        .trimEnd('/')
        .let {
            if (it.startsWith("http://") || it.startsWith("https://")) {
                it
            } else {
                "http://$it"
            }
        }

    private suspend fun request(path: String): String =
        withContext(Dispatchers.IO) {

            val connection =
                URL("$root/$path").openConnection() as HttpURLConnection

            connection.connectTimeout = 15000
            connection.readTimeout = 20000

            connection.setRequestProperty(
                "User-Agent",
                "MiIPTV Android"
            )

            try {
                connection.inputStream.bufferedReader().use {
                    it.readText()
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun authQuery(): String {
        return "username=${enc(user)}&password=${enc(pass)}"
    }

    // CARGAR CATEGORÍAS

    suspend fun categories(): List<Category> {

        val response = request(
            "player_api.php?${authQuery()}&action=get_live_categories"
        )

        val json = JSONArray(response)

        return (0 until json.length()).map { index ->

            val item = json.getJSONObject(index)

            Category(
                id = item.optString("category_id"),
                name = item.optString("category_name")
            )
        }
    }

    // CARGAR CANALES

    suspend fun channels(): List<Channel> {

        val response = request(
            "player_api.php?${authQuery()}&action=get_live_streams"
        )

        val json = JSONArray(response)

        return (0 until json.length()).map { index ->

            val item = json.getJSONObject(index)

            Channel(
                id = item.optString("stream_id"),
                name = item.optString("name"),
                categoryId = item.optString("category_id"),
                icon = item.optString("stream_icon")
                    .takeIf { it.isNotBlank() }
            )
        }
    }

    // URL DE TRANSMISIÓN IPTV

    fun streamUrl(channel: Channel): String {

        return "$root/live/${enc(user)}/${enc(pass)}/${channel.id}.ts"
    }

    // URL ALTERNATIVA HLS

    fun streamUrlHls(channel: Channel): String {

        return "$root/live/${enc(user)}/${enc(pass)}/${channel.id}.m3u8"
    }
}
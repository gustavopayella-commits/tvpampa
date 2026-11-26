package com.miiptv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Channel(val id: String, val name: String, val categoryId: String, val icon: String?)
data class Category(val id: String, val name: String)

class XtreamApi(private val host: String, private val user: String, private val pass: String) {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val root = host.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }

    private suspend fun request(path: String): String = withContext(Dispatchers.IO) {
        val conn = (URL("$root/$path").openConnection() as HttpURLConnection)
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "MiIPTV Android")
        try { conn.inputStream.bufferedReader().use { it.readText() } }
        finally { conn.disconnect() }
    }
    private fun authQuery() = "username=${enc(user)}&password=${enc(pass)}"

    suspend fun categories(): List<Category> {
        val arr = JSONArray(request("player_api.php?${authQuery()}&action=get_live_categories"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i); Category(o.optString("category_id"), o.optString("category_name"))
        }
    }
    suspend fun channels(): List<Channel> {
        val arr = JSONArray(request("player_api.php?${authQuery()}&action=get_live_streams"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Channel(o.optString("stream_id"), o.optString("name"), o.optString("category_id"), o.optString("stream_icon").takeIf { it.isNotBlank() })
        }
    }
    fun streamUrl(channel: Channel): String = "$root/${enc(user)}/${enc(pass)}/${channel.id}.m3u8"
}

package com.miiptv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Channel(
    val id: String,
    val name: String,
    val categoryId: String,
    val icon: String?,
    val streamUrl: String
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
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private val root: String = host.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it
        else "http://$it"
    }

    private var playlistCache: String? = null
    private var parsedChannels: List<Channel>? = null
    private var parsedCategories: List<Category>? = null

    private suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "MiIPTV Android")
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code al descargar playlist: ${body.take(250)}")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun playlist(): String {
        if (playlistCache != null) return playlistCache!!
        val playlistUrl = "$root/get.php?username=${enc(user)}&password=${enc(pass)}&type=m3u_plus&output=ts"
        val text = getText(playlistUrl)
        if (!text.trimStart().startsWith("#EXTM3U", ignoreCase = true)) {
            throw IllegalStateException("El servidor no devolvió una lista M3U válida.")
        }
        playlistCache = text
        return text
    }

    suspend fun categories(): List<Category> {
        if (parsedCategories != null) return parsedCategories!!
        parsePlaylist()
        return parsedCategories ?: emptyList()
    }

    suspend fun channels(): List<Channel> {
        if (parsedChannels != null) return parsedChannels!!
        parsePlaylist()
        return parsedChannels ?: emptyList()
    }

    private suspend fun parsePlaylist() {
        if (parsedChannels != null && parsedCategories != null) return

        val text = playlist()
        val groupNames = linkedSetOf<String>()
        val temp = mutableListOf<Channel>()
        var pendingName = "Canal"
        var pendingGroup = "Otros"
        var pendingLogo: String? = null
        var counter = 0

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val comma = line.indexOf(',')
                val metadata = if (comma >= 0) line.substring(0, comma) else line
                pendingName = if (comma >= 0) line.substring(comma + 1).trim() else "Canal ${counter + 1}"
                pendingGroup = attribute(metadata, "group-title")?.takeIf { it.isNotBlank() } ?: "Otros"
                pendingLogo = attribute(metadata, "tvg-logo")
            } else if (!line.startsWith("#")) {
                counter++
                groupNames.add(pendingGroup)
                val absolute = if (line.startsWith("http://", true) || line.startsWith("https://", true)) {
                    line // Preserve playlist stream URL exactly when it is absolute.
                } else {
                    URL(URL(root + "/"), line).toString()
                }
                temp.add(
                    Channel(
                        id = counter.toString(),
                        name = pendingName.ifBlank { "Canal $counter" },
                        categoryId = pendingGroup,
                        icon = pendingLogo,
                        streamUrl = absolute
                    )
                )
                pendingName = "Canal"
                pendingGroup = "Otros"
                pendingLogo = null
            }
        }

        parsedCategories = groupNames.map { Category(id = it, name = it) }
        parsedChannels = temp
    }

    private fun attribute(text: String, key: String): String? {
        val regex = Regex("""(?:^|\s)$key\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.getOrNull(1)
    }

    // Return the exact URL from the playlist, without changing its stream format.
    fun streamUrl(channel: Channel): String = channel.streamUrl
}

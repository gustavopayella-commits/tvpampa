package com.miiptv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private val root: String = host.trim().trimEnd('/').let {
        if (it.startsWith("http://", true) ||
            it.startsWith("https://", true)
        ) it else "http://$it"
    }

    private var playlistCache: String? = null
    private var parsedChannels: List<Channel>? = null
    private var parsedCategories: List<Category>? = null

    private suspend fun getText(url: String): String =
        withContext(Dispatchers.IO) {
            val connection =
                (URL(url).openConnection() as HttpURLConnection)

            connection.connectTimeout = 15000
            connection.readTimeout = 25000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "MiIPTV Android"
            )
            connection.setRequestProperty(
                "Accept",
                "*/*"
            )

            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val body = stream?.bufferedReader()?.use {
                    it.readText()
                }.orEmpty()

                if (code !in 200..299) {
                    throw IllegalStateException(
                        "HTTP $code: ${body.take(200)}"
                    )
                }

                body
            } finally {
                connection.disconnect()
            }
        }

    private fun playlistUrl(): String =
        "$root/get.php?username=${enc(user)}" +
        "&password=${enc(pass)}" +
        "&type=m3u_plus&output=ts"

    private fun apiUrl(action: String? = null): String {
        val base =
            "$root/player_api.php" +
            "?username=${enc(user)}" +
            "&password=${enc(pass)}"

        return if (action.isNullOrBlank()) {
            base
        } else {
            "$base&action=$action"
        }
    }

    private suspend fun playlist(): String {
        if (playlistCache != null) {
            return playlistCache!!
        }

        val text = getText(playlistUrl())

        if (!text.trimStart().startsWith(
                "#EXTM3U",
                ignoreCase = true
            )
        ) {
            throw IllegalStateException(
                "El servidor no devolvió una lista M3U válida."
            )
        }

        playlistCache = text
        return text
    }

    suspend fun categories(): List<Category> {
        if (parsedCategories != null) {
            return parsedCategories!!
        }

        parsePlaylistOrApi()
        return parsedCategories ?: emptyList()
    }

    suspend fun channels(): List<Channel> {
        if (parsedChannels != null) {
            return parsedChannels!!
        }

        parsePlaylistOrApi()
        return parsedChannels ?: emptyList()
    }

    private suspend fun parsePlaylistOrApi() {
        if (parsedChannels != null &&
            parsedCategories != null
        ) return

        // Primero intentar obtener M3U tradicional.
        try {
            val text = playlist()
            parseM3U(text)
            return
        } catch (m3uError: Exception) {
            // Si get.php no existe, probar API Xtream.
        }

        try {
            parseXtreamApi()
        } catch (apiError: Exception) {
            throw IllegalStateException(
                "No se pudo importar la lista.\n" +
                "M3U: get.php no disponible.\n" +
                "API: ${apiError.message ?: "Error desconocido"}"
            )
        }
    }

    private fun parseM3U(text: String) {
        val groupNames = linkedMapOf<String, Category>()
        val temp = mutableListOf<Channel>()

        var pendingName = "Canal"
        var pendingGroup = "Otros"
        var pendingLogo: String? = null
        var counter = 0

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith(
                    "#EXTINF",
                    ignoreCase = true
                )
            ) {
                val comma = line.indexOf(',')

                val metadata = if (comma >= 0) {
                    line.substring(0, comma)
                } else {
                    line
                }

                pendingName = if (comma >= 0) {
                    line.substring(comma + 1).trim()
                } else {
                    "Canal ${counter + 1}"
                }

                pendingGroup =
                    attribute(metadata, "group-title")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Otros"

                pendingLogo = attribute(
                    metadata,
                    "tvg-logo"
                )
            } else if (!line.startsWith("#")) {
                counter++

                groupNames.putIfAbsent(
                    pendingGroup,
                    Category(
                        id = pendingGroup,
                        name = pendingGroup
                    )
                )

                val stream = if (
                    line.startsWith("http://", true) ||
                    line.startsWith("https://", true)
                ) {
                    line
                } else {
                    URL(URL("$root/"), line).toString()
                }

                temp.add(
                    Channel(
                        id = counter.toString(),
                        name = pendingName.ifBlank {
                            "Canal $counter"
                        },
                        categoryId = pendingGroup,
                        icon = pendingLogo,
                        streamUrl = stream
                    )
                )

                pendingName = "Canal"
                pendingGroup = "Otros"
                pendingLogo = null
            }
        }

        if (temp.isEmpty()) {
            throw IllegalStateException(
                "La lista M3U no contiene canales."
            )
        }

        parsedCategories = groupNames.values.toList()
        parsedChannels = temp
    }

    private suspend fun parseXtreamApi() {
        // Validar credenciales y acceso al servidor.
        val authText = getText(apiUrl())

        val authJson = JSONObject(authText)

        if (authJson.optString("user_info")
                .isNotBlank()
        ) {
            val userInfo = authJson.optJSONObject(
                "user_info"
            )

            if (userInfo != null &&
                userInfo.has("auth") &&
                userInfo.optInt("auth") == 0
            ) {
                throw IllegalStateException(
                    "Usuario o contraseña incorrectos."
                )
            }
        }

        // Obtener categorías de canales en vivo.
        val categoriesJson = JSONArray(
            getText(apiUrl("get_live_categories"))
        )

        val categoryMap =
            linkedMapOf<String, Category>()

        for (i in 0 until categoriesJson.length()) {
            val item = categoriesJson.optJSONObject(i)
                ?: continue

            val id = item.optString("category_id")
            val name = item.optString(
                "category_name",
                "Otros"
            )

            if (id.isNotBlank()) {
                categoryMap[id] = Category(
                    id = id,
                    name = name
                )
            }
        }

        // Obtener canales.
        val channelsJson = JSONArray(
            getText(apiUrl("get_live_streams"))
        )

        val temp = mutableListOf<Channel>()

        for (i in 0 until channelsJson.length()) {
            val item = channelsJson.optJSONObject(i)
                ?: continue

            val streamId = item.optString("stream_id")
            if (streamId.isBlank()) continue

            val name = item.optString(
                "name",
                "Canal $streamId"
            )

            val categoryId = item.optString(
                "category_id",
                "0"
            )

            val icon = item.optString(
                "stream_icon"
            ).takeIf {
                it.isNotBlank() &&
                it != "null"
            }

            val streamType = item.optString(
                "stream_type",
                "ts"
            ).ifBlank { "ts" }

            val streamUrl =
                "$root/live/${enc(user)}/${enc(pass)}/" +
                "$streamId.$streamType"

            if (!categoryMap.containsKey(categoryId)) {
                categoryMap[categoryId] = Category(
                    id = categoryId,
                    name = "Otros"
                )
            }

            temp.add(
                Channel(
                    id = streamId,
                    name = name,
                    categoryId = categoryId,
                    icon = icon,
                    streamUrl = streamUrl
                )
            )
        }

        if (temp.isEmpty()) {
            throw IllegalStateException(
                "La API respondió, pero no devolvió canales en vivo."
            )
        }

        parsedCategories = categoryMap.values.toList()
        parsedChannels = temp
    }

    private fun attribute(
        text: String,
        key: String
    ): String? {
        val regex = Regex(
            """(?:^|\s)$key\s*=\s*["']([^"']*)["']""",
            RegexOption.IGNORE_CASE
        )

        return regex.find(text)
            ?.groupValues
            ?.getOrNull(1)
    }

    // Devuelve la URL de reproducción del canal.
    fun streamUrl(channel: Channel): String =
        channel.streamUrl
}
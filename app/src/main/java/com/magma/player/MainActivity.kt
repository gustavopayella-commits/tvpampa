
package com.pampatv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

private val MagmaRed = Color(0xFF9E4350)
private val MagmaGold = Color(0xFFE5A63B)
private val MagmaBg = Color(0xFFF7F5F8)
private val MagmaInk = Color(0xFF17313A)
private val MagmaNavy = Color(0xFF06212A)

data class MediaEntry(
    val id: String,
    val name: String,
    val url: String,
    val category: String = "",
    val logo: String = "",
    val kind: String = "live",
    val seriesId: String = ""
)

private object MediaEntryStorage {
    fun encode(entries: List<MediaEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("name", entry.name)
                put("url", entry.url)
                put("category", entry.category)
                put("logo", entry.logo)
                put("kind", entry.kind)
                put("seriesId", entry.seriesId)
            })
        }
        return array.toString()
    }

    fun decode(value: String?): List<MediaEntry> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(value)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                MediaEntry(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    url = item.optString("url"),
                    category = item.optString("category"),
                    logo = item.optString("logo"),
                    kind = item.optString("kind", "live"),
                    seriesId = item.optString("seriesId")
                )
            }
        }.getOrDefault(emptyList())
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PaMpATVRoot() }
    }
}

@Composable
private fun PaMpATVRoot() {
    var splashVisible by remember { mutableStateOf(true) }
    val transition = rememberInfiniteTransition(label = "pampatv_splash")
    val logoScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    LaunchedEffect(Unit) {
        delay(1900)
        splashVisible = false
    }

    Box(Modifier.fillMaxSize()) {
        PaMpATVApp()
        AnimatedVisibility(
            visible = splashVisible,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(450))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF06212A)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pampatv_logo),
                    contentDescription = "PaMpATV",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaMpATVApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("magma_settings", 0) }
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf(prefs.getString("source", "M3U") ?: "M3U") }
    var section by remember { mutableStateOf("TV") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var setupOpen by remember { mutableStateOf(prefs.getBoolean("setup", true)) }
    var m3uUrl by remember { mutableStateOf(prefs.getString("m3u", "") ?: "") }
    var localM3u by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(prefs.getString("server", "") ?: "") }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }

    val cachedLive = remember { MediaEntryStorage.decode(prefs.getString("cached_live", null)) }
    val cachedMovies = remember { MediaEntryStorage.decode(prefs.getString("cached_movies", null)) }
    val cachedSeries = remember { MediaEntryStorage.decode(prefs.getString("cached_series", null)) }

    var live by remember { mutableStateOf(cachedLive) }
    var movies by remember { mutableStateOf(cachedMovies) }
    var series by remember { mutableStateOf(cachedSeries) }

    var status by remember {
        mutableStateOf(
            if (cachedLive.isNotEmpty() || cachedMovies.isNotEmpty() || cachedSeries.isNotEmpty()) {
                "Conectado: ${cachedLive.size} canales · ${cachedMovies.size} películas · ${cachedSeries.size} series"
            } else "Agregá una lista M3U o conectá tu cuenta"
        )
    }

    var search by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    var categories by remember {
        mutableStateOf(
            (cachedLive + cachedMovies + cachedSeries)
                .map { it.category }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        )
    }

    var category by remember { mutableStateOf("Todos") }
    var selected by remember { mutableStateOf<MediaEntry?>(null) }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }
    var showFavorites by remember { mutableStateOf(false) }
    var pairingCode by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            m3uUrl = ""
            localM3u = runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            status = if (localM3u.isNotBlank()) {
                "Archivo M3U listo para conectar"
            } else {
                "No se pudo leer el archivo"
            }
        }
    }

    fun saveSettings() {
        prefs.edit()
            .putString("source", source)
            .putString("m3u", m3uUrl)
            .putString("server", server)
            .putString("username", username)
            .putString("password", password)
            .putBoolean("setup", false)
            .apply()
        setupOpen = false
    }

    suspend fun loadAll() {
        busy = true
        status = "Conectando y cargando contenido..."

        try {
            val loaded = withContext(Dispatchers.IO) {
                if (source == "M3U") {
                    require(localM3u.isNotBlank() || m3uUrl.isNotBlank()) {
                        "Ingresá una URL o elegí un archivo M3U"
                    }
                    val text = if (localM3u.isNotBlank()) {
                        localM3u
                    } else {
                        M3uParser.download(m3uUrl)
                    }
                    Triple(M3uParser.parseText(text), emptyList(), emptyList())
                } else {
                    require(
                        server.isNotBlank() &&
                            username.isNotBlank() &&
                            password.isNotBlank()
                    ) {
                        "Completá servidor, usuario y contraseña"
                    }
                    val api = XtreamApi(server, username, password)
                    Triple(api.live(), api.vod(), api.series())
                }
            }

            live = loaded.first
            movies = loaded.second
            series = loaded.third

            categories = (live + movies + series)
                .map { it.category }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            status = "Conectado: ${live.size} canales · ${movies.size} películas · ${series.size} series"

            prefs.edit()
                .putString("cached_live", MediaEntryStorage.encode(live))
                .putString("cached_movies", MediaEntryStorage.encode(movies))
                .putString("cached_series", MediaEntryStorage.encode(series))
                .apply()

            saveSettings()
        } catch (e: Exception) {
            status = "No se pudo conectar: ${e.message ?: "revisá los datos"}"
        } finally {
            busy = false
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = MagmaRed,
            secondary = MagmaGold,
            background = MagmaBg
        )
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Color(0xFFFFF9FC)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(MagmaRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔥", fontSize = 43.sp)
                            Text(
                                "PaMpATV",
                                color = Color.White,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    listOf(
                        "TV", "Películas", "Series", "Playlists",
                        "Ajustes", "Emparejar TV", "Premium", "Telegram"
                    ).forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item) },
                            selected = section == item,
                            onClick = {
                                section = item
                                scope.launch { drawerState.close() }
                                showFavorites = false
                                category = "Todos"
                            },
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                            icon = {
                                Icon(
                                    when (item) {
                                        "TV" -> Icons.Default.Tv
                                        "Películas" -> Icons.Default.Movie
                                        "Series" -> Icons.Default.LiveTv
                                        "Playlists" -> Icons.Default.PlaylistPlay
                                        "Ajustes" -> Icons.Default.Settings
                                        "Emparejar TV" -> Icons.Default.Cast
                                        "Premium" -> Icons.Default.Star
                                        else -> Icons.Default.Send
                                    },
                                    null
                                )
                            }
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    Text(
                        "PaMpATV · Android",
                        Modifier.padding(18.dp),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        ) {
            Column(Modifier.fillMaxSize().background(MagmaBg)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MagmaRed)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, null, tint = Color.White)
                    }

                    Text(
                        "PaMpATV",
                        Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            showFavorites = !showFavorites
                            section = "TV"
                        }
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            null,
                            tint = if (showFavorites) MagmaGold else Color.White
                        )
                    }

                    IconButton(onClick = { setupOpen = true }) {
                        Icon(Icons.Default.Add, null, tint = Color.White)
                    }
                }

                if (section in listOf("TV", "Películas", "Series")) {
                    LazyRow(
                        Modifier.fillMaxWidth().background(Color.White),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(listOf("TV", "Películas", "Series", "Playlists")) { tab ->
                            TextButton(
                                onClick = {
                                    section = tab
                                    showFavorites = false
                                    category = "Todos"
                                }
                            ) {
                                Text(
                                    tab,
                                    color = if (section == tab) MagmaRed else MagmaInk,
                                    fontWeight = if (section == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                when (section) {
                    "Ajustes" -> SettingsPage(
                        status, source,
                        { setupOpen = true },
                        { scope.launch { loadAll() } }
                    )

                    "Emparejar TV" -> PairPage(
                        pairingCode,
                        { pairingCode = it }
                    )

                    "Premium" -> InfoPage(
                        "Premium",
                        "La activación Premium depende del proveedor oficial de la aplicación. Esta reconstrucción no puede validar compras ni licencias del servicio original."
                    )

                    "Telegram" -> InfoPage(
                        "Telegram",
                        "Configurá aquí el enlace de soporte de tu proveedor cuando dispongas de él."
                    )

                    "Playlists" -> PlaylistPage(
                        source = source,
                        status = status,
                        onManage = { setupOpen = true },
                        onRefresh = { scope.launch { loadAll() } },
                        onDelete = {
                            live = emptyList()
                            movies = emptyList()
                            series = emptyList()
                            categories = emptyList()
                            favorites = emptySet()
                            source = "M3U"
                            m3uUrl = ""
                            localM3u = ""
                            server = ""
                            username = ""
                            password = ""
                            status = "Agregá una lista M3U o conectá tu cuenta"
                            prefs.edit().clear().putBoolean("setup", true).apply()
                            setupOpen = true
                        }
                    )

                    else -> {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            placeholder = { Text("Buscar ${section.lowercase()}...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(13.dp)
                        )

                        val sourceItems = when (section) {
                            "Películas" -> movies
                            "Series" -> series
                            else -> live
                        }

                        val filtered = sourceItems.filter {
                            (it.name.contains(search, true) ||
                                it.category.contains(search, true)) &&
                                (category == "Todos" || it.category == category) &&
                                (!showFavorites || favorites.contains(it.id))
                        }

                        LazyRow(
                            Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = category == "Todos",
                                    onClick = { category = "Todos" },
                                    label = { Text("Todos") }
                                )
                            }

                            items(categories) { c ->
                                FilterChip(
                                    selected = category == c,
                                    onClick = { category = c },
                                    label = { Text(c) }
                                )
                            }
                        }

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (showFavorites) "Favoritos" else "${filtered.size} elementos",
                                color = MagmaInk,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                status,
                                color = MagmaRed,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

                        if (filtered.isEmpty()) {
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.LiveTv,
                                        null,
                                        tint = MagmaRed,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        "Todavía no hay contenido",
                                        color = MagmaInk,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Agregá una lista M3U o una cuenta Smarters",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    Button(
                                        onClick = { setupOpen = true },
                                        modifier = Modifier.padding(top = 10.dp)
                                    ) {
                                        Text("Agregar lista")
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(105.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(9.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered, key = { it.id }) { entry ->
                                    Card(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { selected = entry },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color.White
                                        )
                                    ) {
                                        Column(
                                            Modifier.padding(9.dp).height(112.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(48.dp)
                                                    .background(
                                                        Color(0xFFFFF0E5),
                                                        RoundedCornerShape(12.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (entry.logo.startsWith("http", true)) {
                                                    AsyncImage(
                                                        model = entry.logo,
                                                        contentDescription = entry.name,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(3.dp),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                } else {
                                                    Text(
                                                        if (entry.kind == "live") "▶"
                                                        else if (entry.kind == "series") "▣"
                                                        else "🎬",
                                                        fontSize = 23.sp,
                                                        color = MagmaRed
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.height(6.dp))

                                            Text(
                                                entry.name,
                                                maxLines = 2,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MagmaInk
                                            )

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    entry.category.ifBlank { section },
                                                    fontSize = 9.sp,
                                                    color = MagmaRed,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1
                                                )

                                                IconButton(
                                                    onClick = {
                                                        favorites = if (favorites.contains(entry.id)) {
                                                            favorites - entry.id
                                                        } else {
                                                            favorites + entry.id
                                                        }
                                                        prefs.edit()
                                                            .putStringSet("favorites", favorites)
                                                            .apply()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Favorite,
                                                        null,
                                                        tint = if (favorites.contains(entry.id)) {
                                                            MagmaRed
                                                        } else {
                                                            Color.LightGray
                                                        },
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MagmaGold)
                        .padding(7.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "PaMpATV",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        selected?.let { entry ->
            if (entry.kind == "series") {
                SeriesEpisodesDialog(
                    entry,
                    server,
                    username,
                    password,
                    onPlay = { selected = it },
                    onClose = { selected = null }
                )
            } else {
                PlayerDialog(entry) { selected = null }
            }
        }

        if (setupOpen) {
            AlertDialog(
                onDismissRequest = { setupOpen = false },
                title = { Text("Agregar playlist") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = source == "M3U",
                                onClick = { source = "M3U" },
                                label = { Text("M3U / M3U8") }
                            )
                            FilterChip(
                                selected = source == "Smarters",
                                onClick = { source = "Smarters" },
                                label = { Text("Smarters") }
                            )
                        }

                        if (source == "M3U") {
                            OutlinedTextField(
                                m3uUrl,
                                { m3uUrl = it; localM3u = "" },
                                label = { Text("URL de lista M3U") },
                                singleLine = true
                            )
                            OutlinedButton(
                                onClick = { filePicker.launch(arrayOf("*/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Elegir archivo M3U")
                            }
                        } else {
                            OutlinedTextField(
                                server,
                                { server = it },
                                label = { Text("Servidor / URL") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                username,
                                { username = it },
                                label = { Text("Usuario") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                password,
                                { password = it },
                                label = { Text("Contraseña") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true
                            )
                        }

                        Text(status, fontSize = 11.sp, color = MagmaRed)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { scope.launch { loadAll() } },
                        enabled = !busy
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                Modifier.size(17.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Conectar")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { setupOpen = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsPage(
    status: String,
    source: String,
    onEdit: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Ajustes",
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = MagmaInk
        )
        Text("Fuente actual: $source", color = MagmaInk)
        Text(status, color = MagmaRed, fontSize = 12.sp)

        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text("Editar conexión")
        }

        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("Actualizar contenido")
        }

        Text(
            "La reproducción depende de que tu proveedor permita el acceso y entregue enlaces compatibles.",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PlaylistPage(
    source: String,
    status: String,
    onManage: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Playlists",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = MagmaInk,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onManage) {
                Text("+ Añadir lista")
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PlaylistPlay,
                    null,
                    tint = MagmaRed,
                    modifier = Modifier.size(34.dp)
                )

                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        if (source == "M3U") "Lista M3U" else "Cuenta Smarters",
                        fontWeight = FontWeight.Bold,
                        color = MagmaInk
                    )
                    Text(status, color = Color.Gray, fontSize = 11.sp)
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opciones de lista",
                            tint = MagmaInk
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar conexión") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuExpanded = false
                                onManage()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Borrar lista") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MagmaRed
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                confirmDelete = true
                            }
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Actualizar lista")
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar lista") },
            text = {
                Text("¿Querés borrar esta lista y sus datos guardados de este dispositivo?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text("Borrar", color = MagmaRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PairPage(code: String, onCode: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text(
            "Emparejar TV",
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = MagmaInk
        )
        Text("Ingresá el código que se muestra en el televisor.", color = MagmaInk)

        OutlinedTextField(
            code,
            onCode,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Código de TV") },
            singleLine = true
        )

        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Añadir TV")
        }

        Card(colors = CardDefaults.cardColors(containerColor = MagmaNavy)) {
            Column(
                Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("AYUDA", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "1. Abrí la aplicación en el televisor.\n" +
                        "2. Entrá a la pantalla para agregar playlist.\n" +
                        "3. Ubicá el código en el televisor.\n" +
                        "4. Ingresá el código en este dispositivo.",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        Text(
            "La vinculación real necesita el servicio oficial de emparejamiento de PaMpATV; esta pantalla replica el flujo visual.",
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun InfoPage(title: String, message: String) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = MagmaInk
        )
        Text(message, color = MagmaInk)
    }
}

@Composable
private fun SeriesEpisodesDialog(
    entry: MediaEntry,
    server: String,
    user: String,
    pass: String,
    onPlay: (MediaEntry) -> Unit,
    onClose: () -> Unit
) {
    var episodes by remember(entry.id) {
        mutableStateOf<List<MediaEntry>>(emptyList())
    }
    var loading by remember(entry.id) { mutableStateOf(true) }
    var error by remember(entry.id) { mutableStateOf("") }

    LaunchedEffect(entry.id) {
        try {
            episodes = withContext(Dispatchers.IO) {
                XtreamApi(server, user, pass).episodes(entry.seriesId, entry.name)
            }
        } catch (e: Exception) {
            error = e.message ?: "No se pudieron cargar los episodios"
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(entry.name) },
        text = {
            when {
                loading -> CircularProgressIndicator()
                error.isNotBlank() -> Text(error)
                episodes.isEmpty() -> Text("No se encontraron episodios para esta serie.")
                else -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(episodes.size) { index ->
                        val ep = episodes[index]
                        ListItem(
                            headlineContent = { Text(ep.name) },
                            supportingContent = { Text(ep.category) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.PlayCircle,
                                    null,
                                    tint = MagmaRed
                                )
                            },
                            modifier = Modifier.clickable { onPlay(ep) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun PlayerDialog(
    entry: MediaEntry,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var fullscreen by remember { mutableStateOf(false) }
    var zoomMode by remember { mutableIntStateOf(0) }
    var playbackError by remember(entry.url) { mutableStateOf("") }

    val player = remember(entry.url) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
            )
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                val cleanUrl = entry.url
                    .substringBefore('?')
                    .substringBefore('#')
                    .lowercase()

                val mimeType = when {
                    cleanUrl.endsWith(".m3u8") ->
                        MimeTypes.APPLICATION_M3U8

                    cleanUrl.endsWith(".mpd") ->
                        MimeTypes.APPLICATION_MPD

                    cleanUrl.endsWith(".ts") ->
                        MimeTypes.VIDEO_MP2T

                    else -> null
                }

                val itemBuilder = MediaItem.Builder()
                    .setUri(Uri.parse(entry.url))

                if (mimeType != null) {
                    itemBuilder.setMimeType(mimeType)
                }

                setMediaItem(itemBuilder.build())

                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                                "No se pudo conectar con el servidor."

                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                                "El servidor rechazó el enlace (HTTP)."

                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                                "El formato de video no es compatible o el enlace está incompleto."

                            else ->
                                "Error de reproducción (${error.errorCodeName}). ${error.message.orEmpty()}"
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            playbackError = ""
                        }
                    }
                })

                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    DisposableEffect(fullscreen) {
        val previousOrientation = activity?.requestedOrientation

        if (fullscreen) {
            activity?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        onDispose {
            if (previousOrientation != null) {
                activity?.requestedOrientation = previousOrientation
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = if (fullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .wrapContentHeight()
            },
            color = Color.Black,
            shape = if (fullscreen) {
                RoundedCornerShape(0.dp)
            } else {
                RoundedCornerShape(16.dp)
            }
        ) {
            Column(
                modifier = if (fullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF171717))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )

                    IconButton(onClick = { fullscreen = !fullscreen }) {
                        Icon(
                            imageVector = if (fullscreen) {
                                Icons.Default.FullscreenExit
                            } else {
                                Icons.Default.Fullscreen
                            },
                            contentDescription = "Pantalla completa",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (fullscreen) {
                                Modifier.weight(1f)
                            } else {
                                Modifier.height(250.dp)
                            }
                        )
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                                controllerShowTimeoutMs = 5000
                                controllerAutoShow = true

                                resizeMode = if (zoomMode == 0) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                } else {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }

                                setShowBuffering(
                                    PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                                )
                            }
                        },
                        update = { view ->
                            view.player = player
                            view.resizeMode = if (zoomMode == 0) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (playbackError.isNotBlank()) {
                        Text(
                            text = playbackError,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(20.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF171717))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { player.seekBack() }) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text("10 s", color = Color.White)
                    }

                    TextButton(
                        onClick = {
                            player.playWhenReady = !player.isPlaying
                        }
                    ) {
                        Icon(
                            if (player.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text("Play", color = Color.White)
                    }

                    TextButton(onClick = { player.seekForward() }) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text("10 s", color = Color.White)
                    }

                    TextButton(
                        onClick = {
                            zoomMode = if (zoomMode == 0) 1 else 0
                        }
                    ) {
                        Icon(
                            Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            if (zoomMode == 0) "Ajustar" else "Zoom",
                            color = Color.White
                        )
                    }
                }

                if (!fullscreen) {
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 8.dp)
                    ) {
                        Text("Cerrar reproductor", color = Color.White)
                    }
                }
            }
        }
    }
}

private object M3uParser {
    fun download(address: String): String {
        require(
            address.startsWith("http://") ||
                address.startsWith("https://")
        ) {
            "Ingresá una URL M3U válida"
        }

        return URL(address).openConnection().apply {
            connectTimeout = 15000
            readTimeout = 30000
        }.getInputStream().bufferedReader().use { it.readText() }
    }

    fun parseText(text: String): List<MediaEntry> {
        val out = mutableListOf<MediaEntry>()
        var name = "Canal"
        var logo = ""
        var group = "TV"
        var id = 0

        text.lineSequence().forEach { raw ->
            val line = raw.trim()

            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfterLast(',', "Canal")
                    .trim().ifBlank { "Canal" }

                logo = Regex("tvg-logo=\"([^\"]*)\"")
                    .find(line)?.groupValues?.get(1).orEmpty()

                group = Regex("group-title=\"([^\"]*)\"")
                    .find(line)?.groupValues?.get(1) ?: "TV"

            } else if (
                line.isNotBlank() &&
                !line.startsWith("#") &&
                (
                    line.startsWith("http", true) ||
                        line.startsWith("rtmp", true)
                    )
            ) {
                out.add(
                    MediaEntry(
                        "m3u-${id++}",
                        name,
                        line,
                        group,
                        logo,
                        "live"
                    )
                )
                name = "Canal"
                logo = ""
                group = "TV"
            }
        }

        return out
    }
}

private class XtreamApi(
    host: String,
    user: String,
    pass: String
) {
    private val base = host.trimEnd('/')
    private val u = Uri.encode(user)
    private val p = Uri.encode(pass)

    private fun get(action: String): String {
        val url = "$base/player_api.php?username=$u&password=$p&action=$action"

        return URL(url).openConnection().apply {
            connectTimeout = 15000
            readTimeout = 30000
        }.getInputStream().bufferedReader().use { it.readText() }
    }

    private fun array(action: String) = JSONArray(get(action))

    private fun categories(action: String): Map<String, String> {
        val a = array(action)
        return (0 until a.length()).associate { i ->
            val o = a.getJSONObject(i)
            o.optString("category_id") to o.optString("category_name")
        }
    }

    fun live(): List<MediaEntry> {
        val cats = categories("get_live_categories")
        val a = array("get_live_streams")

        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val id = o.optString("stream_id")
            val ext = o.optString("container_extension")
                .ifBlank { "ts" }

            MediaEntry(
                "live-$id",
                o.optString("name", "Canal"),
                "$base/live/$u/$p/$id.$ext",
                cats[o.optString("category_id")].orEmpty(),
                o.optString("stream_icon"),
                "live"
            )
        }
    }

    fun vod(): List<MediaEntry> {
        val cats = categories("get_vod_categories")
        val a = array("get_vod_streams")

        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val id = o.optString("stream_id")
            val ext = o.optString("container_extension")
                .ifBlank { "mp4" }

            MediaEntry(
                "vod-$id",
                o.optString("name", "Película"),
                "$base/movie/$u/$p/$id.$ext",
                cats[o.optString("category_id")].orEmpty(),
                o.optString("stream_icon"),
                "vod"
            )
        }
    }

    fun series(): List<MediaEntry> {
        val cats = categories("get_series_categories")
        val a = array("get_series")

        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val id = o.optString("series_id")

            MediaEntry(
                "series-$id",
                o.optString("name", "Serie"),
                "",
                cats[o.optString("category_id")].orEmpty(),
                o.optString("cover"),
                "series",
                id
            )
        }
    }

    fun episodes(
        seriesId: String,
        title: String
    ): List<MediaEntry> {
        val root = JSONObject(
            get(
                "get_series_info&series_id=${
                    URLEncoder.encode(seriesId, "UTF-8")
                }"
            )
        )

        val all = root.optJSONObject("episodes") ?: return emptyList()
        val out = mutableListOf<MediaEntry>()
        val keys = all.keys()

        while (keys.hasNext()) {
            val season = keys.next()
            val arr = all.optJSONArray(season) ?: continue

            for (i in 0 until arr.length()) {
                val ep = arr.getJSONObject(i)
                val id = ep.optString("id")
                val ext = ep.optString("container_extension")
                    .ifBlank { "mp4" }

                out.add(
                    MediaEntry(
                        "episode-$id",
                        "T$season · ${ep.optString("title", "Episodio")}",
                        "$base/series/$u/$p/$id.$ext",
                        title,
                        "",
                        "episode"
                    )
                )
            }
        }

        return out
    }
}

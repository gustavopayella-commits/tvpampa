# Magma Android – reconstrucción para GitHub Actions

Proyecto Android nativo en Kotlin + Jetpack Compose, preparado para compilar un APK de prueba desde GitHub Actions.

## Funciones implementadas
- Interfaz inspirada en las capturas del video: cabecera roja/dorada, menú lateral, navegación TV/Películas/Series/Playlists y tarjetas.
- Importar playlist M3U/M3U8 desde una URL o archivo local.
- Conexión IPTV tipo Xtream Codes (servidor, usuario y contraseña; compatible con credenciales de acceso usadas por IPTV Smarters).
- Carga de canales en vivo, películas, series y episodios desde Xtream Codes.
- Búsqueda por nombre y categoría.
- Filtro de categorías y favoritos persistentes.
- Reproductor integrado Media3/ExoPlayer.
- Pantallas de Ajustes y Emparejar TV (la vinculación queda como interfaz, necesita el servicio oficial).
- Workflow de GitHub Actions que genera `app-debug.apk` como artefacto.

## Compilar en GitHub
1. Descomprimí el ZIP y subí el contenido a un repositorio GitHub.
2. Abrí la pestaña **Actions**.
3. Ejecutá **Build Magma APK** con **Run workflow**, o hacé push a `main`.
4. Al finalizar, descargá el artefacto **Magma-debug-apk**.

## Limitaciones importantes
- Es una reconstrucción funcional basada en el APK y las pantallas de referencia, no el código fuente original ni una copia binaria exacta.
- Las funciones Premium, Telegram y emparejamiento TV necesitan los servicios/API oficiales de Magma; no se pueden activar solamente copiando la interfaz.
- La reproducción depende de que la lista/proveedor tenga enlaces válidos y permita el acceso desde esta aplicación.
- El parser M3U muestra los elementos como canales en vivo; la clasificación automática de películas/series de una M3U depende de los metadatos que entregue la lista.
- No incluye listas IPTV, usuarios, contraseñas ni contenido audiovisual.

Usá únicamente listas y servicios para los que tengas autorización.

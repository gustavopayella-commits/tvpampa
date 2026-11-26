package com.miiptv.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null

    private lateinit var statusText: TextView
    private lateinit var playerView: PlayerView

    private var primaryUrl: String = ""
    private var alternativeUrl: String = ""
    private var triedAlternative = false

    private var channelTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        channelTitle = intent.getStringExtra("title") ?: "Canal"
        primaryUrl = intent.getStringExtra("url") ?: ""
        alternativeUrl = intent.getStringExtra("alternative_url") ?: ""

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Color.BLACK)

        val titleText = TextView(this)
        titleText.text = channelTitle
        titleText.textSize = 20f
        titleText.setTextColor(Color.WHITE)
        titleText.setPadding(16, 16, 16, 16)
        layout.addView(titleText)

        playerView = PlayerView(this)
        playerView.setBackgroundColor(Color.BLACK)

        layout.addView(
            playerView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        statusText = TextView(this)
        statusText.text = "Preparando transmisión..."
        statusText.textSize = 15f
        statusText.setTextColor(Color.WHITE)
        statusText.setPadding(16, 12, 16, 12)
        layout.addView(statusText)

        val backButton = TextView(this)
        backButton.text = "‹  VOLVER A LOS CANALES"
        backButton.textSize = 16f
        backButton.setTextColor(Color.WHITE)
        backButton.setPadding(16, 18, 16, 18)
        backButton.setOnClickListener {
            finish()
        }
        layout.addView(backButton)

        setContentView(layout)

        if (primaryUrl.isBlank()) {
            statusText.text = "ERROR: No se recibió la URL del canal."
            return
        }

        startPlayer()
    }

    private fun startPlayer() {

        try {

            val exo = ExoPlayer.Builder(this).build()
            player = exo
            playerView.player = exo

            exo.addListener(
                object : Player.Listener {

                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_IDLE ->
                                statusText.text = "Reproductor esperando..."

                            Player.STATE_BUFFERING ->
                                statusText.text = "Conectando y cargando transmisión..."

                            Player.STATE_READY ->
                                statusText.text = "Reproduciendo: $channelTitle"

                            Player.STATE_ENDED ->
                                statusText.text = "La transmisión finalizó."
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {

                        if (!triedAlternative && alternativeUrl.isNotBlank()) {

                            triedAlternative = true

                            statusText.text =
                                "Falló el primer formato. Probando formato alternativo..."

                            exo.stop()
                            exo.clearMediaItems()
                            exo.setMediaItem(
                                MediaItem.fromUri(alternativeUrl)
                            )
                            exo.prepare()
                            exo.play()

                        } else {

                            statusText.text =
                                "ERROR DE REPRODUCCIÓN\n\n" +
                                "Código: ${error.errorCode}\n" +
                                "Tipo: ${error.errorCodeName}\n" +
                                "Detalle: ${error.message ?: "Sin detalle"}\n\n" +
                                "Se probaron los formatos disponibles."
                        }
                    }
                }
            )

            statusText.text = "Probando transmisión TS..."

            exo.setMediaItem(MediaItem.fromUri(primaryUrl))
            exo.prepare()
            exo.play()

        } catch (e: Exception) {

            statusText.text =
                "Error al crear reproductor:\n${e.javaClass.simpleName}\n${e.message}"
        }
    }

    override fun onPause() {
        player?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
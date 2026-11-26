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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channelTitle =
            intent.getStringExtra("title") ?: "Canal de TV"

        val streamUrl =
            intent.getStringExtra("url")

        // Construir pantalla
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

        // Verificar que exista URL
        if (streamUrl.isNullOrBlank()) {
            statusText.text = "Error: no se recibió el enlace del canal."
            return
        }

        // Inicializar reproductor de forma segura
        try {

            val exoPlayer = ExoPlayer.Builder(this).build()

            player = exoPlayer
            playerView.player = exoPlayer

            exoPlayer.addListener(
                object : Player.Listener {

                    override fun onPlaybackStateChanged(
                        playbackState: Int
                    ) {

                        when (playbackState) {

                            Player.STATE_IDLE -> {
                                statusText.text =
                                    "Reproductor esperando..."
                            }

                            Player.STATE_BUFFERING -> {
                                statusText.text =
                                    "Cargando transmisión..."
                            }

                            Player.STATE_READY -> {
                                statusText.text =
                                    "Reproduciendo: $channelTitle"
                            }

                            Player.STATE_ENDED -> {
                                statusText.text =
                                    "La transmisión finalizó."
                            }
                        }
                    }

                    override fun onPlayerError(
                        error: PlaybackException
                    ) {

                        statusText.text =
                            "No se pudo reproducir este canal. " +
                            "Probá con otro o revisá la señal."

                    }
                }
            )

            val mediaItem = MediaItem.fromUri(streamUrl)

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()

        } catch (e: Exception) {

            statusText.text =
                "Error al iniciar el reproductor: ${e.message}"

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
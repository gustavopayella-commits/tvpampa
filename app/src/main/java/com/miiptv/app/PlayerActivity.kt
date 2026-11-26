package com.miiptv.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
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

        val title = intent.getStringExtra("title") ?: "Canal"
        val url = intent.getStringExtra("url") ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        layout.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
        })

        playerView = PlayerView(this)
        layout.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))

        statusText = TextView(this).apply {
            text = "Preparando URL original de la lista..."
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(16, 12, 16, 12)
        }
        layout.addView(statusText)

        layout.addView(TextView(this).apply {
            text = "‹  VOLVER A LOS CANALES"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(16, 18, 16, 18)
            setOnClickListener { finish() }
        })
        setContentView(layout)

        if (url.isBlank()) {
            statusText.text = "Error: la lista no entregó una URL para este canal."
            return
        }

        try {
            val exo = ExoPlayer.Builder(this).build()
            player = exo
            playerView.player = exo

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    statusText.text = when (state) {
                        Player.STATE_IDLE -> "Reproductor esperando..."
                        Player.STATE_BUFFERING -> "Cargando URL original..."
                        Player.STATE_READY -> "Reproduciendo: $title"
                        Player.STATE_ENDED -> "La transmisión finalizó."
                        else -> statusText.text
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    statusText.text = "Error de reproducción\nCódigo: ${error.errorCode}\n${error.errorCodeName}\n${error.message ?: "Sin detalles"}"
                }
            })

            // Play exactly the stream URL supplied in the M3U playlist.
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.play()
        } catch (e: Exception) {
            statusText.text = "Error iniciando reproductor:\n${e.javaClass.simpleName}\n${e.message}"
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

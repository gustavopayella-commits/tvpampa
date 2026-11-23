
package com.pampatv

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import android.graphics.Color
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var urlInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 22, 32))
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = "📺 PaMpaTV"
            textSize = 28f
            setTextColor(Color.WHITE)
            setPadding(8, 16, 8, 16)
        }

        urlInput = EditText(this).apply {
            hint = "Pega tu enlace M3U8, TS o MP4"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setSingleLine(true)
        }

        val playButton = Button(this).apply {
            text = "REPRODUCIR"
            setOnClickListener {
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) playUrl(url)
            }
        }

        playerView = PlayerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 240
            )
        }

        layout.addView(title)
        layout.addView(urlInput)
        layout.addView(playButton)
        layout.addView(playerView)

        setContentView(layout)
    }

    private fun playUrl(url: String) {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build()
                playerView.player = player
            }

            player?.setMediaItem(MediaItem.fromUri(url))
            player?.prepare()
            player?.play()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo reproducir el enlace", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}

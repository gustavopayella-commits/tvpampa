
package com.pampatv

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 30, 20, 20)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF101820.toInt())
        }

        val titulo = Button(this).apply {
            text = "PaMpaTV"
            isAllCaps = false
        }
        layout.addView(titulo)

        val url = EditText(this).apply {
            hint = "Pegar enlace M3U8 o MP4"
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFBBBBBB.toInt())
        }
        layout.addView(url)

        val reproducir = Button(this).apply {
            text = "REPRODUCIR"
            setOnClickListener {
                val enlace = url.text.toString().trim()
                if (enlace.isNotEmpty()) {
                    try {
                        player?.setMediaItem(MediaItem.fromUri(enlace))
                        player?.prepare()
                        player?.play()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "No se pudo reproducir el enlace",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        layout.addView(reproducir)

        playerView = PlayerView(this)
        layout.addView(
            playerView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                240
            )
        )

        setContentView(layout)
    }

    override fun onStart() {
        super.onStart()
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
    }

    override fun onStop() {
        playerView.player = null
        player?.release()
        player = null
        super.onStop()
    }
}

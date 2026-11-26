package com.miiptv.app

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.common.MediaItem

class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title") ?: "Reproductor"
        val url = intent.getStringExtra("url") ?: return
        val layout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        layout.addView(TextView(this).apply { text=title; textSize=18f; setPadding(12,12,12,12) })
        val pv = PlayerView(this)
        layout.addView(pv, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(layout)
        player = ExoPlayer.Builder(this).build().also { exo ->
            pv.player = exo
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
        }
    }
    override fun onStop() { player?.release(); player=null; super.onStop() }
}

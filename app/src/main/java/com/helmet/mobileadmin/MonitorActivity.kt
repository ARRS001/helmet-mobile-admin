package com.helmet.mobileadmin

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class MonitorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceId = intent.getStringExtra("deviceId") ?: ""
        val name = intent.getStringExtra("name") ?: deviceId

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            gravity = Gravity.CENTER
        }

        val label = TextView(this).apply {
            text = name; setTextColor(0xFFFFFFFF.toInt()); textSize = 16f
            setPadding(0, 24, 0, 12); gravity = Gravity.CENTER
        }
        root.addView(label)

        // HLS 播放器
        val videoView = VideoView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            val hlsUrl = "${ApiService.serverBase}/live/$deviceId/hls.m3u8"
            setVideoURI(Uri.parse(hlsUrl))
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, _, _ ->
                label.text = "$name — 加载失败"; false
            }
            start()
        }
        root.addView(videoView)

        setContentView(root)
    }
}

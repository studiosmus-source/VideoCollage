package com.studiosmus.videocollage

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class CollageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    data class Tile(val uri: Uri, val container: FrameLayout, val player: ExoPlayer, val view: PlayerView)

    private val tiles = mutableListOf<Tile>()

    init { setBackgroundColor(Color.BLACK) }

    fun setVideos(uris: List<Uri>) {
        release()
        removeAllViews()
        uris.take(4).forEach { addTile(it) }
        post { layoutTiles() }
    }

    private fun addTile(uri: Uri) {
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = true
            clipToPadding = true
        }
        val playerView = PlayerView(context).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setBackgroundColor(Color.BLACK)
        }
        container.addView(playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(container)

        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 1f
            prepare()
            playWhenReady = true
        }
        playerView.player = player
        installGestures(playerView)
        tiles += Tile(uri, container, player, playerView)
    }

    private fun installGestures(view: PlayerView) {
        var lastX = 0f
        var lastY = 0f
        var scale = 1f
        val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scale = (scale * detector.scaleFactor).coerceIn(1f, 5f)
                view.scaleX = scale
                view.scaleY = scale
                return true
            }
        })
        view.setOnTouchListener { v, e ->
            scaler.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; true }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaler.isInProgress && e.pointerCount == 1) {
                        v.translationX += e.x - lastX
                        v.translationY += e.y - lastY
                        lastX = e.x
                        lastY = e.y
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun layoutTiles() {
        if (tiles.isEmpty()) return
        val gap = dp(3)
        val w = width
        val h = height
        tiles.forEachIndexed { i, tile ->
            val lp = when (tiles.size) {
                1 -> LayoutParams(w, h)
                2 -> LayoutParams((w - gap) / 2, h).apply {
                    leftMargin = i * ((w + gap) / 2)
                }
                3 -> if (i == 0) {
                    LayoutParams((w - gap) / 2, h)
                } else {
                    LayoutParams((w - gap) / 2, (h - gap) / 2).apply {
                        leftMargin = (w + gap) / 2
                        topMargin = (i - 1) * ((h + gap) / 2)
                    }
                }
                else -> LayoutParams((w - gap) / 2, (h - gap) / 2).apply {
                    leftMargin = (i % 2) * ((w + gap) / 2)
                    topMargin = (i / 2) * ((h + gap) / 2)
                }
            }
            lp.gravity = Gravity.TOP or Gravity.START
            tile.container.layoutParams = lp
        }
    }

    fun resetTransforms() {
        tiles.forEach {
            it.view.translationX = 0f
            it.view.translationY = 0f
            it.view.scaleX = 1f
            it.view.scaleY = 1f
        }
    }

    fun release() {
        tiles.forEach { it.player.release() }
        tiles.clear()
    }

    fun selectedUris(): List<Uri> = tiles.map { it.uri }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

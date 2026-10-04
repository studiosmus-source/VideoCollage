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
    private var layoutVariant = 0

    init { setBackgroundColor(Color.BLACK) }

    fun setVideos(uris: List<Uri>) {
        release()
        removeAllViews()
        uris.take(4).forEach { addTile(it) }
        layoutVariant = 0
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
        var lastRawX = 0f
        var lastRawY = 0f
        var scale = 1f
        var scaling = false
        val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaling = true
                view.pivotX = detector.focusX
                view.pivotY = detector.focusY
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scale = (scale * detector.scaleFactor).coerceIn(1f, 5f)
                view.scaleX = scale
                view.scaleY = scale
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                scaling = false
            }
        })
        view.setOnTouchListener { v, e ->
            scaler.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = e.rawX
                    lastRawY = e.rawY
                    true
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_POINTER_DOWN -> {
                    lastRawX = e.rawX
                    lastRawY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaling && !scaler.isInProgress && e.pointerCount == 1) {
                        val dx = e.rawX - lastRawX
                        val dy = e.rawY - lastRawY
                        v.translationX += dx
                        v.translationY += dy
                    }
                    lastRawX = e.rawX
                    lastRawY = e.rawY
                    true
                }
                else -> true
            }
        }
    }

    fun cycleLayout() {
        if (tiles.size < 2) return
        layoutVariant = (layoutVariant + 1) % layoutCount()
        layoutTiles()
    }

    fun layoutLabel(): String = when (tiles.size) {
        2 -> if (layoutVariant == 0) "2 verticali" else "2 orizzontali"
        3 -> when (layoutVariant) {
            0 -> "1 grande + 2"
            1 -> "2 + 1 grande"
            else -> "3 colonne"
        }
        4 -> if (layoutVariant == 0) "Griglia 2×2" else "4 colonne"
        else -> "Intero"
    }

    private fun layoutCount() = when (tiles.size) {
        2 -> 2
        3 -> 3
        4 -> 2
        else -> 1
    }

    private fun layoutTiles() {
        if (tiles.isEmpty()) return
        val gap = dp(3)
        val w = width
        val h = height
        tiles.forEachIndexed { i, tile ->
            val lp = when (tiles.size) {
                1 -> LayoutParams(w, h)
                2 -> if (layoutVariant == 0) {
                    LayoutParams((w - gap) / 2, h).apply { leftMargin = i * ((w + gap) / 2) }
                } else {
                    LayoutParams(w, (h - gap) / 2).apply { topMargin = i * ((h + gap) / 2) }
                }
                3 -> when (layoutVariant) {
                    0 -> if (i == 0) LayoutParams((w - gap) / 2, h) else
                        LayoutParams((w - gap) / 2, (h - gap) / 2).apply {
                            leftMargin = (w + gap) / 2
                            topMargin = (i - 1) * ((h + gap) / 2)
                        }
                    1 -> if (i == 2) LayoutParams((w - gap) / 2, h).apply { leftMargin = (w + gap) / 2 } else
                        LayoutParams((w - gap) / 2, (h - gap) / 2).apply { topMargin = i * ((h + gap) / 2) }
                    else -> LayoutParams((w - 2 * gap) / 3, h).apply { leftMargin = i * ((w + gap) / 3) }
                }
                else -> if (layoutVariant == 0) {
                    LayoutParams((w - gap) / 2, (h - gap) / 2).apply {
                        leftMargin = (i % 2) * ((w + gap) / 2)
                        topMargin = (i / 2) * ((h + gap) / 2)
                    }
                } else {
                    LayoutParams((w - 3 * gap) / 4, h).apply { leftMargin = i * ((w + gap) / 4) }
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

    data class Transform(val scale: Float, val translationX: Float, val translationY: Float)

    fun selectedUris(): List<Uri> = tiles.map { it.uri }

    fun transforms(): List<Transform> = tiles.map {
        Transform(it.view.scaleX, it.view.translationX, it.view.translationY)
    }

    fun gridSize(): Pair<Int, Int> = when (tiles.size) {
        2 -> 2 to 1
        3, 4 -> 2 to 2
        else -> 1 to 1
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

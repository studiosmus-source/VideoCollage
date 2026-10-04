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
import kotlin.math.roundToInt

class CollageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    data class Transform(val scale: Float, val translationX: Float, val translationY: Float)
    data class Cell(val left: Float, val top: Float, val right: Float, val bottom: Float)
    data class ExportState(
        val layoutVariant: Int,
        val aspectRatio: Float,
        val cells: List<Cell>,
        val transforms: List<Transform>
    )
    data class Tile(val uri: Uri, val container: FrameLayout, val player: ExoPlayer, val view: PlayerView)

    private val tiles = mutableListOf<Tile>()
    private var layoutVariant = 0
    private var canvasRatio = 16f / 9f

    init { setBackgroundColor(Color.BLACK) }

    fun setVideos(uris: List<Uri>) {
        release()
        removeAllViews()
        uris.take(4).forEach { addTile(it) }
        layoutVariant = 0
        post { layoutTiles() }
    }

    fun setCanvasRatio(ratio: Float) {
        canvasRatio = ratio.coerceIn(0.4f, 2.5f)
        requestLayout()
        post { layoutTiles() }
    }

    fun aspectRatio(): Float = canvasRatio

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec)
        val maxH = MeasureSpec.getSize(heightMeasureSpec)
        var w = maxW
        var h = (w / canvasRatio).roundToInt()
        if (h > maxH) {
            h = maxH
            w = (h * canvasRatio).roundToInt()
        }
        setMeasuredDimension(w.coerceAtLeast(2), h.coerceAtLeast(2))
        val cw = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        measureChildren(cw, ch)
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
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                view.pivotX = detector.focusX
                view.pivotY = detector.focusY
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scale = (scale * detector.scaleFactor).coerceIn(1f, 6f)
                view.scaleX = scale
                view.scaleY = scale
                return true
            }
        })
        view.setOnTouchListener { v, e ->
            scaler.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.x
                    lastY = e.y
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> if (!scaler.isInProgress && e.pointerCount == 1) {
                    v.translationX += e.x - lastX
                    v.translationY += e.y - lastY
                    lastX = e.x
                    lastY = e.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            true
        }
    }

    fun cycleLayout() {
        if (tiles.size < 2) return
        layoutVariant = (layoutVariant + 1) % layoutCount()
        resetTransforms()
        layoutTiles()
    }

    fun layoutLabel(): String = when (tiles.size) {
        2 -> if (layoutVariant == 0) "2 affiancati" else "2 sovrapposti"
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

    private fun normalizedCells(): List<Cell> = when (tiles.size) {
        1 -> listOf(Cell(0f, 0f, 1f, 1f))
        2 -> if (layoutVariant == 0)
            listOf(Cell(0f,0f,.5f,1f), Cell(.5f,0f,1f,1f))
        else listOf(Cell(0f,0f,1f,.5f), Cell(0f,.5f,1f,1f))
        3 -> when (layoutVariant) {
            0 -> listOf(Cell(0f,0f,.5f,1f), Cell(.5f,0f,1f,.5f), Cell(.5f,.5f,1f,1f))
            1 -> listOf(Cell(0f,0f,.5f,.5f), Cell(0f,.5f,.5f,1f), Cell(.5f,0f,1f,1f))
            else -> listOf(Cell(0f,0f,1f/3f,1f), Cell(1f/3f,0f,2f/3f,1f), Cell(2f/3f,0f,1f,1f))
        }
        else -> if (layoutVariant == 0)
            listOf(Cell(0f,0f,.5f,.5f), Cell(.5f,0f,1f,.5f), Cell(0f,.5f,.5f,1f), Cell(.5f,.5f,1f,1f))
        else listOf(Cell(0f,0f,.25f,1f), Cell(.25f,0f,.5f,1f), Cell(.5f,0f,.75f,1f), Cell(.75f,0f,1f,1f))
    }

    private fun layoutTiles() {
        if (tiles.isEmpty() || width == 0 || height == 0) return
        val gap = dp(3)
        normalizedCells().forEachIndexed { i, cell ->
            val l = (cell.left * width).roundToInt()
            val t = (cell.top * height).roundToInt()
            val r = (cell.right * width).roundToInt()
            val b = (cell.bottom * height).roundToInt()
            val lp = LayoutParams((r-l-gap/2).coerceAtLeast(2), (b-t-gap/2).coerceAtLeast(2)).apply {
                leftMargin = l
                topMargin = t
                gravity = Gravity.TOP or Gravity.START
            }
            tiles[i].container.layoutParams = lp
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { layoutTiles() }
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

    fun transforms(): List<Transform> = tiles.map {
        Transform(it.view.scaleX, it.view.translationX, it.view.translationY)
    }

    fun exportState(): ExportState = ExportState(layoutVariant, canvasRatio, normalizedCells(), transforms())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

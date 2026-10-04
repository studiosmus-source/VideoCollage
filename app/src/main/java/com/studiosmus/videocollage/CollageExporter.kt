package com.studiosmus.videocollage

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.math.max
import kotlin.math.min

@OptIn(UnstableApi::class)
class CollageExporter(private val context: Context) {

    data class Source(val uri: Uri, val durationMs: Long)

    fun export(
        uris: List<Uri>,
        state: CollageView.ExportState,
        onProgress: (String) -> Unit,
        onDone: (Uri) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            require(uris.size in 2..4)
            val sources = uris.map { Source(it, durationOf(it)) }
            val longestIndex = sources.indices.maxBy { sources[it].durationMs }
            val sequences = sources.mapIndexed { index, source ->
                val item = EditedMediaItem.Builder(MediaItem.fromUri(source.uri)).build()
                EditedMediaItemSequence.Builder(item)
                    .setIsLooping(index != longestIndex)
                    .build()
            }

            val composition = Composition.Builder(sequences)
                .setVideoCompositorSettings(GridCompositor(uris.size, state))
                .build()

            val temp = File(context.cacheDir, "videocollage-${System.currentTimeMillis()}.mp4")
            if (temp.exists()) temp.delete()

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        try {
                            onProgress("Salvataggio in Galleria…")
                            val saved = publishToGallery(temp)
                            temp.delete()
                            onDone(saved)
                        } catch (t: Throwable) {
                            temp.delete()
                            onError(t)
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        temp.delete()
                        onError(exportException)
                    }
                })
                .build()

            onProgress("Creazione video…")
            transformer.start(composition, temp.absolutePath)
        } catch (t: Throwable) {
            onError(t)
        }
    }

    private fun durationOf(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("Durata video non disponibile")
        } finally {
            retriever.release()
        }
    }

    private fun publishToGallery(file: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "VideoCollage_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoCollage")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Impossibile creare il video in Galleria")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Impossibile scrivere il video")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private class GridCompositor(
        private val count: Int,
        private val state: CollageView.ExportState
    ) : VideoCompositorSettings {

        override fun getOutputSize(inputSizes: List<Size>): Size {
            val maxInputW = inputSizes.maxOf { it.width }.coerceAtMost(3840)
            val maxInputH = inputSizes.maxOf { it.height }.coerceAtMost(3840)
            val sourceArea = maxInputW.toLong() * maxInputH.toLong()
            val ratio = state.aspectRatio
            var outW = kotlin.math.sqrt(sourceArea * ratio).toInt().coerceAtLeast(720)
            var outH = (outW / ratio).toInt()
            val scale = min(1f, min(3840f / outW, 3840f / outH))
            outW = max(2, (outW * scale).toInt() and -2)
            outH = max(2, (outH * scale).toInt() and -2)
            return Size(outW, outH)
        }

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            val cell = state.cells.getOrNull(inputId) ?: CollageView.Cell(0f, 0f, 1f, 1f)
            val t = state.transforms.getOrNull(inputId) ?: CollageView.Transform(1f, 0f, 0f)
            val cellW = cell.right - cell.left
            val cellH = cell.bottom - cell.top
            val baseX = -1f + (cell.left + cell.right)
            val baseY = 1f - (cell.top + cell.bottom)
            val moveX = (t.translationX / 1000f).coerceIn(-cellW, cellW)
            val moveY = (-t.translationY / 1000f).coerceIn(-cellH, cellH)

            return StaticOverlaySettings.Builder()
                .setScale(t.scale * cellW, t.scale * cellH)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(baseX + moveX, baseY + moveY)
                .build()
        }
    }
}

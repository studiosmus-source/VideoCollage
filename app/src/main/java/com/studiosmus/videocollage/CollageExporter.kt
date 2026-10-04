package com.studiosmus.videocollage

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.C
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
        transforms: List<CollageView.Transform>,
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
                EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
                    .addItem(item)
                    .setIsLooping(index != longestIndex)
                    .build()
            }

            val composition = Composition.Builder(sequences)
                .setVideoCompositorSettings(GridCompositor(uris.size, transforms))
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
        private val transforms: List<CollageView.Transform>
    ) : VideoCompositorSettings {

        override fun getOutputSize(inputSizes: List<Size>): Size {
            val cols = 2
            val rows = if (count == 2) 1 else 2
            val cellW = inputSizes.maxOf { it.width }
            val cellH = inputSizes.maxOf { it.height }
            val rawW = cellW * cols
            val rawH = cellH * rows
            val limitW = 3840
            val limitH = 2160
            val factor = min(1f, min(limitW.toFloat() / rawW, limitH.toFloat() / rawH))
            return Size(max(2, (rawW * factor).toInt() and -2), max(2, (rawH * factor).toInt() and -2))
        }

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            val cols = 2
            val rows = if (count == 2) 1 else 2
            val col = inputId % cols
            val row = inputId / cols
            val t = transforms.getOrNull(inputId) ?: CollageView.Transform(1f, 0f, 0f)

            val baseX = -1f + (col + .5f) * (2f / cols)
            val baseY = 1f - (row + .5f) * (2f / rows)
            val moveX = (t.translationX / 1000f).coerceIn(-0.45f, 0.45f)
            val moveY = (-t.translationY / 1000f).coerceIn(-0.45f, 0.45f)

            return StaticOverlaySettings.Builder()
                .setScale(t.scale / cols, t.scale / rows)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(baseX + moveX, baseY + moveY)
                .build()
        }
    }
}

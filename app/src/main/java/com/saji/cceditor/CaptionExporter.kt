package com.saji.cceditor

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
class CaptionOverlay(
    private val cues: List<CaptionCue>,
    private val style: CaptionStyleState
) : CanvasOverlay(true) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(218, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val timeMs = presentationTimeUs / 1000L
        val cue = cues.firstOrNull { timeMs >= it.startMs && timeMs <= it.endMs } ?: return
        val text = cue.text.trim()
        if (text.isEmpty()) return

        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val maxTextWidth = (width * 0.84f).toInt().coerceAtLeast(120)
        val textSize = (width * 0.055f * style.fontScale).coerceIn(24f, 96f)
        val typefaceStyle = when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = runCatching { Color.parseColor(this@CaptionOverlay.style.colorHex) }.getOrDefault(Color.WHITE)
            this.textSize = textSize
            typeface = Typeface.create("sans-serif", typefaceStyle)
            isUnderlineText = this@CaptionOverlay.style.underline
            textAlign = Paint.Align.LEFT
            setShadowLayer(2f, 0f, 2f, Color.BLACK)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(3)
            .build()

        val horizontalPadding = width * 0.035f
        val verticalPadding = textSize * 0.34f
        val blockWidth = layout.width + horizontalPadding * 2
        val blockHeight = layout.height + verticalPadding * 2
        val left = (width - blockWidth) / 2f
        val top = height * 0.76f - blockHeight / 2f
        val rect = RectF(left, top, left + blockWidth, top + blockHeight)

        when (style.background) {
            CaptionBackground.PILL -> canvas.drawRoundRect(rect, textSize * 0.55f, textSize * 0.55f, backgroundPaint)
            CaptionBackground.OUTLINE -> canvas.drawRoundRect(rect, textSize * 0.42f, textSize * 0.42f, outlinePaint)
            CaptionBackground.PLAIN -> Unit
        }

        canvas.save()
        canvas.translate((width - layout.width) / 2f, top + verticalPadding)
        layout.draw(canvas)
        canvas.restore()
    }
}

@OptIn(UnstableApi::class)
fun exportCaptionedMedia(
    context: Context,
    asset: MediaAsset,
    cues: List<CaptionCue>,
    style: CaptionStyleState,
    onStarted: () -> Unit,
    onExportCompleted: (String) -> Unit,
    onExportError: (String) -> Unit
) {
    val tempFile = File(context.cacheDir, "cc-export-" + System.currentTimeMillis() + ".mp4")
    val mediaItemBuilder = MediaItem.Builder().setUri(asset.uri)
    if (!asset.isVideo) mediaItemBuilder.setImageDurationMs(6_000L)

    val overlayEffect = OverlayEffect(listOf(CaptionOverlay(cues, style)))
    val effects = Effects(emptyList(), listOf(overlayEffect))
    val editedItem = EditedMediaItem.Builder(mediaItemBuilder.build())
        .setEffects(effects)
        .build()

    val listener = object : Transformer.Listener {
        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
            try {
                val saved = saveToGallery(context, tempFile)
                onExportCompleted(saved)
            } catch (error: Exception) {
                onExportError(error.message ?: "Export save failed")
            }
        }

        override fun onError(
            composition: androidx.media3.transformer.Composition,
            exportResult: ExportResult,
            exportException: ExportException
        ) {
            onExportError(exportException.message ?: "Video export failed")
            tempFile.delete()
        }
    }

    try {
        val transformer = Transformer.Builder(context.applicationContext)
            .addListener(listener)
            .build()
        onStarted()
        transformer.start(editedItem, tempFile.absolutePath)
    } catch (error: Exception) {
        tempFile.delete()
        onExportError(error.message ?: "Could not start export")
    }
}

private fun saveToGallery(context: Context, source: File): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "CC_Editor_$stamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CC Editor")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create file in Movies")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Could not open exported file")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            source.delete()
            return uri.toString()
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
    val folder = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: context.filesDir
    val destination = File(folder, source.name)
    source.copyTo(destination, overwrite = true)
    source.delete()
    return destination.absolutePath
}

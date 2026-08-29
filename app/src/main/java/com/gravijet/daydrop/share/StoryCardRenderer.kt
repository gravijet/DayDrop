package com.gravijet.daydrop.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.gravijet.daydrop.data.model.Drop
import com.gravijet.daydrop.ui.theme.paletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a drop as a 1080x1920 story card. Drawn from scratch rather than
 * screenshotted, so the shared image is composed for a 9:16 canvas instead of
 * being a crop of a phone screen.
 */
object StoryCardRenderer {

    private const val W = 1080
    private const val H = 1920
    private const val MARGIN = 88f

    suspend fun render(context: Context, drop: Drop): Bitmap = withContext(Dispatchers.Default) {
        val palette = paletteFor(drop.type)
        val top = palette.top.toArgb()
        val bottom = palette.bottom.toArgb()

        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        })

        // Photo, if the card has one, tinted into the gradient.
        drop.imageUrl?.let { url ->
            loadBitmap(context, url)?.let { photo ->
                val src = centreCrop(photo.width, photo.height, W, H)
                canvas.drawBitmap(photo, src, Rect(0, 0, W, H), Paint().apply { alpha = 158 })
                canvas.drawPaint(Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, H.toFloat(),
                        intArrayOf(
                            withAlpha(bottom, 0.10f),
                            withAlpha(bottom, 0.60f),
                            withAlpha(bottom, 0.98f)
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                })
            }
        }

        val chalk = Color.rgb(244, 243, 251)

        // Eyebrow pill.
        val eyebrow = "${drop.type.emoji}  ${drop.eyebrow.uppercase()}"
        val eyebrowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = chalk
            textSize = 34f
            letterSpacing = 0.14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val pillW = eyebrowPaint.measureText(eyebrow) + 64f
        canvas.drawRoundRect(
            RectF(MARGIN, 150f, MARGIN + pillW, 244f), 47f, 47f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(Color.BLACK, 0.30f) }
        )
        canvas.drawText(eyebrow, MARGIN + 32f, 205f, eyebrowPaint)

        // Body first (measured bottom-up so long text never pushes the title off).
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(chalk, 0.92f)
            textSize = 40f
            typeface = Typeface.DEFAULT
        }
        val bodyText = drop.quiz?.explanation ?: drop.body
        val bodyLayout = layout(bodyText, bodyPaint, (W - 2 * MARGIN).toInt(), 12f)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = chalk
            textSize = if (drop.title.length > 70) 62f else 78f
            letterSpacing = -0.03f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleText = drop.quiz?.question ?: drop.title
        val titleLayout = layout(titleText, titlePaint, (W - 2 * MARGIN).toInt(), 6f)

        val footerTop = H - 200f
        val blockBottom = footerTop - 70f
        val bodyTop = blockBottom - bodyLayout.height
        val titleTop = bodyTop - 36f - titleLayout.height

        canvas.withTranslation(MARGIN, titleTop) { titleLayout.draw(canvas) }
        canvas.withTranslation(MARGIN, bodyTop) { bodyLayout.draw(canvas) }

        // Footer wordmark.
        canvas.drawLine(
            MARGIN, footerTop, W - MARGIN, footerTop,
            Paint().apply { color = withAlpha(chalk, 0.22f); strokeWidth = 2f }
        )
        canvas.drawText(
            "DayDrop · dein täglicher Drop",
            MARGIN, footerTop + 76f,
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(chalk, 0.72f)
                textSize = 34f
                letterSpacing = 0.06f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )

        bitmap
    }

    /** Writes the card to the share cache and fires the system share sheet. */
    suspend fun share(context: Context, drop: Drop) {
        val bitmap = render(context, drop)
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "daydrop-${drop.id.hashCode().toUInt()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val text = buildString {
            append(drop.title)
            if (drop.body.isNotBlank() && drop.quiz == null) append("\n\n").append(drop.body)
            drop.sourceUrl?.let { append("\n\n").append(it) }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Drop teilen")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ---- drawing helpers ---------------------------------------------------

    private fun layout(text: String, paint: TextPaint, width: Int, extra: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(extra, 1f)
            .setIncludePad(false)
            .build()

    private inline fun Canvas.withTranslation(dx: Float, dy: Float, block: () -> Unit) {
        val saved = save()
        translate(dx, dy)
        block()
        restoreToCount(saved)
    }

    private fun centreCrop(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
        val srcRatio = srcW.toFloat() / srcH
        val dstRatio = dstW.toFloat() / dstH
        return if (srcRatio > dstRatio) {
            val w = (srcH * dstRatio).toInt()
            Rect((srcW - w) / 2, 0, (srcW - w) / 2 + w, srcH)
        } else {
            val h = (srcW / dstRatio).toInt()
            Rect(0, (srcH - h) / 2, srcW, (srcH - h) / 2 + h)
        }
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        ImageLoader(context).execute(request).drawable?.toBitmap()
    }.getOrNull()

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}

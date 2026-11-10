package com.example.memeker.encoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoder
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoderOptions
import com.aureusapps.android.webpandroid.encoder.WebPConfig
import com.aureusapps.android.webpandroid.encoder.WebPMuxAnimParams
import com.aureusapps.android.webpandroid.encoder.WebPPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class StickerEncoder(private val context: Context) {

    fun isAnimatedSource(uri: Uri): Boolean {
        val type = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        if (type != null) {
            if (type.contains("video") || type.contains("gif")) return true
            if (type.contains("webp")) return isAnimatedWebP(uri)
        }
        val path = uri.toString().lowercase()
        if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".gif") ||
            path.endsWith(".mov") || path.endsWith(".mkv")) return true
        
        if (path.endsWith(".webp")) return isAnimatedWebP(uri)
        
        return false
    }

    fun isAnimatedWebP(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(30)
                if (input.read(header) < 30) return false
                val riff = String(header.sliceArray(0..3)) == "RIFF"
                val webp = String(header.sliceArray(8..11)) == "WEBP"
                if (!riff || !webp) return false
                
                val chunkType = String(header.sliceArray(12..15))
                if (chunkType == "VP8X") {
                    val flags = header[20].toInt()
                    (flags and 0x02) != 0
                } else {
                    false
                }
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun isVideo(uri: Uri): Boolean {
        val type = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        return type?.contains("video") == true || 
               listOf(".mp4", ".webm", ".mov", ".mkv").any { uri.toString().lowercase().endsWith(it) }
    }

    fun isAnimatedWebP(file: File): Boolean {
        return isAnimatedWebP(Uri.fromFile(file))
    }

    fun isGif(uri: Uri): Boolean {
        val type = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        if (type != null) {
            return type.contains("gif") == true
        }
        return uri.toString().lowercase().endsWith(".gif")
    }

    private suspend fun ensureLocalUri(uri: Uri): Pair<Uri, File?> = withContext(Dispatchers.IO) {
        if (uri.scheme == "http" || uri.scheme == "https") {
            try {
                val tempFile = File(context.cacheDir, "temp_sticker_${System.currentTimeMillis()}")
                java.net.URL(uri.toString()).openStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext Uri.fromFile(tempFile) to tempFile
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext uri to null
    }

    suspend fun encodeStatic(
        sourceUri: Uri,
        outputFile: File,
        width: Int = 512,
        height: Int = 512,
        preserveAlpha: Boolean = true,
        maxSizeKb: Int = 100
    ): Boolean = withContext(Dispatchers.IO) {
        val (localUri, tempFile) = ensureLocalUri(sourceUri)
        try {
            val bitmap = loadAndScaleBitmap(localUri, width, height, preserveAlpha) ?: return@withContext false
            
            var quality = 95
            var success = false
            val compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

            while (quality >= 30) {
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(compressFormat, quality, out)
                }
                
                if (outputFile.length() <= maxSizeKb * 1024) {
                    success = true
                    break
                }
                quality -= 10
            }
            bitmap.recycle()
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            tempFile?.delete()
        }
    }

    suspend fun encodeAnimated(
        sourceUri: Uri,
        outputFile: File,
        policy: EncodePolicy,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.Default) {
        val (localUri, tempFile) = ensureLocalUri(sourceUri)
        var frameSource: FrameSource? = null
        var encoder: WebPAnimEncoder? = null
        try {
            val isAnim = isVideo(localUri)
            frameSource = if (isGif(localUri)) {
                try {
                    val vfs = VideoFrameSource(context, localUri)
                    if (vfs.durationMs > 0) vfs else GifFrameSource(context, localUri)
                } catch (e: Exception) {
                    GifFrameSource(context, localUri)
                }
            } else if (isAnim) {
                VideoFrameSource(context, localUri)
            } else {
                null
            }

            var currentQuality = policy.quality.toFloat()
            var success = false

            while (currentQuality >= 30) {
                val options = WebPAnimEncoderOptions().copy(
                    animParams = WebPMuxAnimParams().copy(loopCount = 0)
                )
                encoder = WebPAnimEncoder(context, 512, 512, options)

                val config = WebPConfig().copy(
                    quality = currentQuality
                )
                encoder.configure(config, WebPPreset.WEBP_PRESET_DEFAULT)

                if (frameSource != null) {
                    val maxDurationMs = 3000L
                    val durationMs = min(frameSource.durationMs, maxDurationMs)
                    val frameIntervalMs = 1000 / policy.fps
                    val frameCount = max(1, (durationMs / frameIntervalMs).toInt())

                    var timestamp = 0L
                    for (i in 0 until frameCount) {
                        val frame = frameSource.getFrame(timestamp * 1000)
                        if (frame != null) {
                            val scaledFrame = scaleBitmap(frame, 512, 512, true)
                            encoder.addFrame(timestamp, scaledFrame)
                            scaledFrame.recycle()
                            frame.recycle()
                        }
                        timestamp += frameIntervalMs
                        onProgress(i.toFloat() / frameCount)
                    }
                    encoder.assemble(timestamp, Uri.fromFile(outputFile))
                } else {
                    // Static image as one-frame animation
                    val bitmap = loadAndScaleBitmap(localUri, 512, 512, true)
                    if (bitmap != null) {
                        encoder.addFrame(0, bitmap)
                        encoder.assemble(100, Uri.fromFile(outputFile))
                        bitmap.recycle()
                    }
                }

                if (outputFile.exists() && outputFile.length() <= 500 * 1024) {
                    success = true
                    break
                }
                
                encoder.release()
                encoder = null
                currentQuality -= 15f
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            encoder?.release()
            frameSource?.release()
            tempFile?.delete()
        }
    }

    private fun loadAndScaleBitmap(uri: Uri, width: Int, height: Int, preserveAlpha: Boolean): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val source = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (source == null) return null
            scaleBitmap(source, width, height, preserveAlpha)
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int, preserveAlpha: Boolean): Bitmap {
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val srcWidth = source.width
        val srcHeight = source.height
        
        if (preserveAlpha) {
            val scale = min(targetWidth.toFloat() / srcWidth, targetHeight.toFloat() / srcHeight)
            val finalWidth = (srcWidth * scale).toInt()
            val finalHeight = (srcHeight * scale).toInt()
            val left = (targetWidth - finalWidth) / 2
            val top = (targetHeight - finalHeight) / 2
            canvas.drawBitmap(source, null, Rect(left, top, left + finalWidth, top + finalHeight), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val scale = max(targetWidth.toFloat() / srcWidth, targetHeight.toFloat() / srcHeight)
            val finalWidth = (srcWidth * scale).toInt()
            val finalHeight = (srcHeight * scale).toInt()
            val left = (targetWidth - finalWidth) / 2
            val top = (targetHeight - finalHeight) / 2
            canvas.drawBitmap(source, null, Rect(left, top, left + finalWidth, top + finalHeight), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        
        return output
    }
}

data class EncodePolicy(
    val fps: Int,
    val quality: Int
)

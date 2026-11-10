package com.example.memeker.encoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.net.Uri
import java.io.InputStream

class GifFrameSource(private val context: Context, private val uri: Uri) : FrameSource {
    private var movie: Movie? = null
    override var durationMs: Long = 0L

    init {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                movie = Movie.decodeStream(inputStream)
                durationMs = movie?.duration()?.toLong() ?: 0L
                inputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getFrame(timeUs: Long): Bitmap? {
        val m = movie ?: return null
        val width = m.width().coerceAtLeast(1)
        val height = m.height().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Movie.setTime takes milliseconds
        val timeMs = (timeUs / 1000).toInt()
        m.setTime(timeMs)
        m.draw(canvas, 0f, 0f)
        
        return bitmap
    }

    override fun release() {
        movie = null
    }
}

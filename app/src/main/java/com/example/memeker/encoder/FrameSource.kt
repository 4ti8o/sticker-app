package com.example.memeker.encoder

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

interface FrameSource {
    val durationMs: Long
    fun getFrame(timeUs: Long): Bitmap?
    fun release()
}

class VideoFrameSource(private val context: Context, private val uri: Uri) : FrameSource {
    private val retriever = MediaMetadataRetriever()
    
    override val durationMs: Long by lazy {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
    }

    override fun getFrame(timeUs: Long): Bitmap? {
        return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
    }

    override fun release() {
        retriever.release()
    }
}

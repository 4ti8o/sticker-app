package com.example.memeker.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

class PackRepository(private val context: Context) {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true 
    }
    private val stickersDir = File(context.filesDir, "stickers")

    init {
        if (!stickersDir.exists()) {
            stickersDir.mkdirs()
        }
    }

    fun getAllPacks(): List<StickerPack> {
        return stickersDir.listFiles { file -> file.isDirectory }?.mapNotNull { packDir ->
            getPack(packDir.name)
        } ?: emptyList()
    }

    fun getPack(identifier: String): StickerPack? {
        val packDir = File(stickersDir, identifier)
        val metadataFile = File(packDir, "metadata.json")
        if (!metadataFile.exists()) return null
        return try {
            json.decodeFromString<StickerPack>(metadataFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun savePack(pack: StickerPack) {
        val packDir = File(stickersDir, pack.identifier)
        if (!packDir.exists()) packDir.mkdirs()
        val metadataFile = File(packDir, "metadata.json")
        metadataFile.writeText(json.encodeToString(StickerPack.serializer(), pack))
    }

    fun deletePack(identifier: String) {
        val packDir = File(stickersDir, identifier)
        packDir.deleteRecursively()
    }
}

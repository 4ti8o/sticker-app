package com.example.memeker.data

import kotlinx.serialization.Serializable

@Serializable
data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String,
    val publisherEmail: String = "",
    val publisherWebsite: String = "",
    val privacyPolicyWebsite: String = "",
    val licenseAgreementWebsite: String = "",
    val imageDataVersion: String = "1",
    val avoidCache: Boolean = false,
    val animatedStickerPack: Boolean = false,
    val stickers: List<Sticker> = emptyList()
)

@Serializable
data class Sticker(
    val imageFile: String,
    val emojis: List<String> = emptyList()
)

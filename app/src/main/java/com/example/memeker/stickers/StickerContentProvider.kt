package com.example.memeker.stickers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.memeker.BuildConfig
import com.example.memeker.data.PackRepository
import java.io.File
import java.io.FileNotFoundException

class StickerContentProvider : ContentProvider() {

    companion object {
        private const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
        private const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
        private const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
        private const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
        private const val ANDROID_APP_QUERY_ID_IN_QUERY = "android_play_store_id"
        private const val IOS_APP_QUERY_ID_IN_QUERY = "ios_app_store_id"
        private const val PUBLISHER_EMAIL_IN_QUERY = "sticker_pack_publisher_email"
        private const val PUBLISHER_WEBSITE_IN_QUERY = "sticker_pack_publisher_website"
        private const val PRIVACY_POLICY_WEBSITE_IN_QUERY = "sticker_pack_privacy_policy_website"
        private const val LICENSE_AGREEMENT_WEBSITE_IN_QUERY = "sticker_pack_license_agreement_website"
        private const val IMAGE_DATA_VERSION_IN_QUERY = "image_data_version"
        private const val AVOID_CACHE_IN_QUERY = "avoid_cache"
        private const val ANIMATED_STICKER_PACK_IN_QUERY = "animated_sticker_pack"

        private const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
        private const val STICKER_EMOJI_IN_QUERY = "sticker_emoji"

        private const val METADATA = 1
        private const val METADATA_ID = 2
        private const val STICKERS = 3
        private const val STICKERS_ASSET = 4

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH)
    }

    private lateinit var repository: PackRepository

    override fun onCreate(): Boolean {
        val authority = BuildConfig.CONTENT_PROVIDER_AUTHORITY
        MATCHER.addURI(authority, "metadata", METADATA)
        MATCHER.addURI(authority, "metadata/*", METADATA_ID)
        MATCHER.addURI(authority, "stickers/*", STICKERS)
        MATCHER.addURI(authority, "stickers_asset/*/*", STICKERS_ASSET)
        
        repository = PackRepository(context!!)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val match = MATCHER.match(uri)
        Log.d("StickerProvider", "Query: $uri, match: $match")
        return when (match) {
            METADATA -> getPackMetadata(null)
            METADATA_ID -> getPackMetadata(uri.lastPathSegment)
            STICKERS -> getStickersForPack(uri.lastPathSegment)
            else -> null
        }
    }

    private fun getPackMetadata(identifier: String?): Cursor {
        val packs = if (identifier == null) {
            repository.getAllPacks()
        } else {
            repository.getPack(identifier)?.let { listOf(it) } ?: emptyList()
        }

        val cursor = MatrixCursor(
            arrayOf(
                STICKER_PACK_IDENTIFIER_IN_QUERY,
                STICKER_PACK_NAME_IN_QUERY,
                STICKER_PACK_PUBLISHER_IN_QUERY,
                STICKER_PACK_ICON_IN_QUERY,
                ANDROID_APP_QUERY_ID_IN_QUERY,
                IOS_APP_QUERY_ID_IN_QUERY,
                PUBLISHER_EMAIL_IN_QUERY,
                PUBLISHER_WEBSITE_IN_QUERY,
                PRIVACY_POLICY_WEBSITE_IN_QUERY,
                LICENSE_AGREEMENT_WEBSITE_IN_QUERY,
                IMAGE_DATA_VERSION_IN_QUERY,
                AVOID_CACHE_IN_QUERY,
                ANIMATED_STICKER_PACK_IN_QUERY
            )
        )
        for (pack in packs) {
            cursor.addRow(
                arrayOf(
                    pack.identifier,
                    pack.name,
                    pack.publisher,
                    pack.trayImageFile,
                    context?.packageName ?: "",
                    "",
                    pack.publisherEmail,
                    pack.publisherWebsite,
                    pack.privacyPolicyWebsite,
                    pack.licenseAgreementWebsite,
                    pack.imageDataVersion,
                    if (pack.avoidCache) 1 else 0,
                    if (pack.animatedStickerPack) 1 else 0
                )
            )
        }
        return cursor
    }

    private fun getStickersForPack(identifier: String?): Cursor? {
        if (identifier == null) return null
        val pack = repository.getPack(identifier) ?: return null
        val cursor = MatrixCursor(arrayOf(STICKER_FILE_NAME_IN_QUERY, STICKER_EMOJI_IN_QUERY))
        for (sticker in pack.stickers) {
            cursor.addRow(arrayOf(sticker.imageFile, sticker.emojis.joinToString(",")))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (MATCHER.match(uri)) {
            METADATA -> "vnd.android.cursor.dir/vnd.${BuildConfig.CONTENT_PROVIDER_AUTHORITY}.metadata"
            METADATA_ID -> "vnd.android.cursor.item/vnd.${BuildConfig.CONTENT_PROVIDER_AUTHORITY}.metadata"
            STICKERS -> "vnd.android.cursor.dir/vnd.${BuildConfig.CONTENT_PROVIDER_AUTHORITY}.stickers"
            STICKERS_ASSET -> "image/webp"
            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val match = MATCHER.match(uri)
        if (match == STICKERS_ASSET) {
            val pathSegments = uri.pathSegments
            val identifier = pathSegments[pathSegments.size - 2]
            val fileName = pathSegments[pathSegments.size - 1]
            return fetchFile(identifier, fileName)
        }
        return null
    }

    private fun fetchFile(identifier: String, fileName: String): AssetFileDescriptor? {
        val file = File(context?.filesDir, "stickers/$identifier/$fileName")
        if (!file.exists()) {
            Log.e("StickerProvider", "File not found: ${file.absolutePath}")
            return null
        }
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            AssetFileDescriptor(pfd, 0, file.length())
        } catch (e: FileNotFoundException) {
            Log.e("StickerProvider", "Error opening file", e)
            null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

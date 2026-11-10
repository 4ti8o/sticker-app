package com.example.memeker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.memeker.BuildConfig
import com.example.memeker.data.PackRepository
import com.example.memeker.data.Sticker
import com.example.memeker.data.StickerPack
import com.example.memeker.encoder.EncodePolicy
import com.example.memeker.encoder.StickerEncoder
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePackScreen(
    repository: PackRepository,
    isSingleSticker: Boolean = false,
    onNavigateBack: () -> Unit
) {
    var name by remember { mutableStateOf(if (isSingleSticker) "Single Sticker" else "") }
    var publisher by remember { mutableStateOf(if (isSingleSticker) "Memeker" else "") }
    var selectedImages by remember { mutableStateOf(emptyList<android.net.Uri>()) }
    var urlText by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }
    var isEncoding by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val encoder = remember { StickerEncoder(context) }

    val maxStickers = if (isSingleSticker) 1 else 30

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxStickers)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages = if (isSingleSticker) listOf(uris.first()) else (selectedImages + uris).take(maxStickers)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSingleSticker) "Create Sticker" else "Create Pack") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isEncoding) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEncoding) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                if (name.isNotBlank() && publisher.isNotBlank() && selectedImages.size >= 1) {
                                    scope.launch {
                                        isEncoding = true
                                        progress = 0f
                                        try {
                                            val identifier = UUID.randomUUID().toString()
                                            val packDir = File(context.filesDir, "stickers/$identifier")
                                            if (!packDir.exists()) packDir.mkdirs()

                                            val stickers = mutableListOf<Sticker>()
                                            val isPackAnimated = !isSingleSticker && selectedImages.any { uri ->
                                                encoder.isAnimatedSource(uri)
                                            }

                                            // Encode tray icon (96x96) - always static
                                            val trayFile = File(packDir, "tray.webp")
                                            encoder.encodeStatic(
                                                selectedImages.first(),
                                                trayFile,
                                                width = 96,
                                                height = 96,
                                                maxSizeKb = 50
                                            )

                                            selectedImages.forEachIndexed { index, uri ->
                                                val fileName = "$index.webp"
                                                val outputFile = File(packDir, fileName)
                                                
                                                // If it's a single sticker, encode based on source type.
                                                // If it's a pack, if the pack is animated, ALL must be encoded as animated.
                                                val shouldEncodeAsAnimated = if (isSingleSticker) {
                                                    encoder.isAnimatedSource(uri)
                                                } else {
                                                    isPackAnimated
                                                }

                                                val success = if (shouldEncodeAsAnimated) {
                                                    encoder.encodeAnimated(
                                                        uri,
                                                        outputFile,
                                                        EncodePolicy(fps = 15, quality = 60)
                                                    ) { p ->
                                                        progress = (index + p) / selectedImages.size
                                                    }
                                                } else {
                                                    encoder.encodeStatic(uri, outputFile)
                                                }

                                                if (success) {
                                                    stickers.add(Sticker(imageFile = fileName, emojis = listOf("😀")))
                                                }
                                                progress = (index + 1f) / selectedImages.size
                                            }

                                            if (stickers.size >= 1) {
                                                val pack = StickerPack(
                                                    identifier = identifier,
                                                    name = name,
                                                    publisher = publisher,
                                                    trayImageFile = "tray.webp",
                                                    animatedStickerPack = isPackAnimated || (isSingleSticker && encoder.isAnimatedSource(selectedImages.first())),
                                                    stickers = stickers
                                                )
                                                repository.savePack(pack)
                                                
                                                if (isSingleSticker) {
                                                    val sticker = stickers.first()
                                                    val stickerUri = android.net.Uri.parse("content://${BuildConfig.CONTENT_PROVIDER_AUTHORITY}/stickers_asset/$identifier/${sticker.imageFile}")
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "image/webp"
                                                        putExtra(android.content.Intent.EXTRA_STREAM, stickerUri)
                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        setPackage("com.whatsapp")
                                                    }
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                
                                                onNavigateBack()
                                            } else {
                                                Toast.makeText(context, "Failed to encode at least 1 sticker", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Encoding failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isEncoding = false
                                        }
                                    }
                                }
                            },
                            enabled = name.isNotBlank() && publisher.isNotBlank() && selectedImages.size >= 1
                        ) {
                            Icon(
                                if (isSingleSticker) Icons.Default.Share else Icons.Default.Check,
                                contentDescription = if (isSingleSticker) "Share" else "Save"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Pack Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isEncoding
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = publisher,
                onValueChange = { publisher = it },
                label = { Text("Publisher") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isEncoding
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    enabled = !isEncoding && selectedImages.size < maxStickers
                ) {
                    Text("Add Local")
                }
                Button(
                    onClick = { showUrlDialog = true },
                    enabled = !isEncoding && selectedImages.size < maxStickers
                ) {
                    Text("Add URL")
                }
                Text("${selectedImages.size}/$maxStickers stickers")
            }

            if (showUrlDialog) {
                AlertDialog(
                    onDismissRequest = { showUrlDialog = false },
                    title = { Text("Add Media URL") },
                    text = {
                        TextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            placeholder = { Text("https://example.com/image.jpg") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (urlText.isNotBlank()) {
                                    try {
                                        val newUri = android.net.Uri.parse(urlText)
                                        selectedImages = if (isSingleSticker) listOf(newUri) else (selectedImages + newUri).take(maxStickers)
                                        urlText = ""
                                        showUrlDialog = false
                                    } catch (e: Exception) {
                                        // Invalid URI
                                    }
                                }
                            }
                        ) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUrlDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            if (selectedImages.size < 1) {
                Text(
                    "Select a sticker to save",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (!isSingleSticker && selectedImages.size < 3) {
                Text(
                    "Note: WhatsApp requires at least 3 stickers per pack.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isEncoding) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Encoding stickers... ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(selectedImages) { uri ->
                    Box(modifier = Modifier.aspectRatio(1f)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (!isEncoding) {
                            IconButton(
                                onClick = { selectedImages = selectedImages - uri },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

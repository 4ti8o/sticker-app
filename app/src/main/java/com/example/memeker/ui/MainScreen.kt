package com.example.memeker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.example.memeker.data.StickerPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen {
    Main, CreatePack, CreateSticker
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: PackRepository) {
    var currentScreen by remember { mutableStateOf(Screen.Main) }

    when (currentScreen) {
        Screen.Main -> MainContent(
            repository,
            onCreatePack = { currentScreen = Screen.CreatePack },
            onCreateSticker = { currentScreen = Screen.CreateSticker }
        )
        Screen.CreatePack -> CreatePackScreen(repository, isSingleSticker = false, onNavigateBack = { currentScreen = Screen.Main })
        Screen.CreateSticker -> CreatePackScreen(repository, isSingleSticker = true, onNavigateBack = { currentScreen = Screen.Main })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    repository: PackRepository,
    onCreatePack: () -> Unit,
    onCreateSticker: () -> Unit
) {
    var packs by remember { mutableStateOf(emptyList<StickerPack>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refreshPacks() {
        scope.launch {
            isLoading = true
            packs = withContext(Dispatchers.IO) { repository.getAllPacks() }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshPacks()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memeker") },
                actions = {
                    IconButton(onClick = { refreshPacks() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (showAddMenu) {
                    ExtendedFloatingActionButton(
                        text = { Text("New Sticker") },
                        icon = { Icon(Icons.Default.Face, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            onCreateSticker()
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ExtendedFloatingActionButton(
                        text = { Text("New Pack") },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            onCreatePack()
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                FloatingActionButton(onClick = { showAddMenu = !showAddMenu }) {
                    Icon(if (showAddMenu) Icons.Default.Close else Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (packs.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No sticker packs yet", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onCreatePack) {
                        Text("Create your first pack")
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(packs) { pack ->
                        StickerPackItem(
                            pack = pack,
                            onDelete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repository.deletePack(pack.identifier) }
                                    refreshPacks()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StickerPackItem(pack: StickerPack, onDelete: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val trayFile = remember(pack) {
        File(File(context.filesDir, "stickers/${pack.identifier}"), pack.trayImageFile)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Pack") },
            text = { Text("Are you sure you want to delete '${pack.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = trayFile,
                        contentDescription = pack.name,
                        modifier = Modifier.size(100.dp).padding(8.dp),
                        contentScale = ContentScale.Fit,
                        error = null
                    )
                    if (pack.animatedStickerPack) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.BottomEnd).size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Animated",
                                modifier = Modifier.padding(4.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Text(pack.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("${pack.stickers.size} stickers", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                val isSingle = pack.stickers.size == 1
                Button(
                    onClick = {
                        if (isSingle) {
                            val sticker = pack.stickers.first()
                            val stickerUri = android.net.Uri.parse("content://${BuildConfig.CONTENT_PROVIDER_AUTHORITY}/stickers_asset/${pack.identifier}/${sticker.imageFile}")
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "image/webp"
                                putExtra(android.content.Intent.EXTRA_STREAM, stickerUri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                setPackage("com.whatsapp")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "WhatsApp not installed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (pack.stickers.size < 3) {
                                android.widget.Toast.makeText(context, "WhatsApp requires at least 3 stickers", android.widget.Toast.LENGTH_LONG).show()
                            }
                            val intent = android.content.Intent().apply {
                                action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
                                putExtra("sticker_pack_id", pack.identifier)
                                putExtra("sticker_pack_authority", BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                                putExtra("sticker_pack_name", pack.name)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "WhatsApp not installed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isSingle) "Share" else "Add",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

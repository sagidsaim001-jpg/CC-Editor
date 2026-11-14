package com.saji.cceditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private val Ink = ComposeColor(0xFF08090D)
private val Panel = ComposeColor(0xFF15171D)
private val PanelSoft = ComposeColor(0xFF1D2028)
private val Orange = ComposeColor(0xFFFF793D)
private val Muted = ComposeColor(0xFF969AA6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 9, 13)
        window.navigationBarColor = android.graphics.Color.rgb(8, 9, 13)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Orange,
                    secondary = ComposeColor(0xFFFFB18D),
                    background = Ink,
                    surface = Panel,
                    onPrimary = ComposeColor.Black,
                    onBackground = ComposeColor.White,
                    onSurface = ComposeColor.White
                )
            ) {
                CCEditorApp()
            }
        }
    }
}

private enum class Screen { HOME, GALLERY, EDITOR }
private enum class GalleryTab { ALL, VIDEOS, PHOTOS }

@Composable
private fun CCEditorApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var galleryTab by remember { mutableStateOf(GalleryTab.ALL) }
    var assets by remember { mutableStateOf<List<MediaAsset>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selectedAsset by remember { mutableStateOf<MediaAsset?>(null) }
    var permissionNote by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasGalleryAccess(context)) {
            screen = Screen.GALLERY
            loading = true
            scope.launch {
                assets = queryGallery(context, GalleryFilter.ALL)
                loading = false
            }
        } else {
            permissionNote = true
        }
    }

    fun openGallery() {
        if (hasGalleryAccess(context)) {
            screen = Screen.GALLERY
            loading = true
            scope.launch {
                assets = queryGallery(context, GalleryFilter.ALL)
                loading = false
            }
        } else {
            permissionLauncher.launch(galleryPermissions())
        }
    }

    fun loadTab(tab: GalleryTab) {
        galleryTab = tab
        loading = true
        scope.launch {
            val filter = when (tab) {
                GalleryTab.ALL -> GalleryFilter.ALL
                GalleryTab.VIDEOS -> GalleryFilter.VIDEOS
                GalleryTab.PHOTOS -> GalleryFilter.PHOTOS
            }
            assets = queryGallery(context, filter)
            loading = false
        }
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = when (screen) {
            Screen.EDITOR -> Screen.GALLERY
            Screen.GALLERY -> Screen.HOME
            Screen.HOME -> Screen.HOME
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        when (screen) {
            Screen.HOME -> HomeScreen(onAddVideo = ::openGallery)
            Screen.GALLERY -> GalleryScreen(
                assets = assets,
                loading = loading,
                selectedTab = galleryTab,
                onBack = { screen = Screen.HOME },
                onTab = ::loadTab,
                onRefresh = { loadTab(galleryTab) },
                onSelect = {
                    selectedAsset = it
                    screen = Screen.EDITOR
                }
            )
            Screen.EDITOR -> {
                val asset = selectedAsset
                if (asset != null) {
                    EditorScreen(
                        asset = asset,
                        onBack = { screen = Screen.GALLERY },
                        onPickAnother = ::openGallery
                    )
                }
            }
        }
    }

    if (permissionNote) {
        AlertDialog(
            onDismissRequest = { permissionNote = false },
            title = { Text("Media permission needed") },
            text = { Text("CC Editor needs photo and video access to show your in-app gallery. You can allow selected media or full access in Android permissions.") },
            confirmButton = {
                TextButton(onClick = { permissionNote = false }) { Text("OK") }
            },
            containerColor = Panel
        )
    }
}

@Composable
private fun HomeScreen(onAddVideo: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(ComposeColor(0xFF171018), Ink, Ink))
        ).padding(horizontal = 22.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(Orange, ComposeColor(0xFFFFAD69)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CC", color = ComposeColor.Black, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                Column {
                    Text("CC EDITOR", fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, fontSize = 17.sp)
                    Text("CAPTION STUDIO", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Your words.", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("Your style.", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Orange)
            Text(
                "Turn your clips into caption-ready videos.",
                color = Muted,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 7.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(ComposeColor(0xFF38202A), ComposeColor(0xFF171923), ComposeColor(0xFF28202B))))
                    .border(1.dp, ComposeColor(0x44FF793D), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = Orange, modifier = Modifier.size(54.dp))
                    Text("CAPTIONS THAT", fontSize = 12.sp, letterSpacing = 3.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                    Text("MAKE AN IMPACT", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Surface(color = ComposeColor(0xFF090A0E), shape = RoundedCornerShape(50)) {
                        Text("EDIT  •  STYLE  •  EXPORT", modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp), color = Orange, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    }
                }
            }
            Button(
                onClick = onAddVideo,
                modifier = Modifier.fillMaxWidth().height(62.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = ComposeColor.Black)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text("ADD VIDEO", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
            }
            Text(
                "Photos and videos from your device • No account required",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GalleryScreen(
    assets: List<MediaAsset>,
    loading: Boolean,
    selectedTab: GalleryTab,
    onBack: () -> Unit,
    onTab: (GalleryTab) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (MediaAsset) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column(modifier = Modifier.weight(1f)) {
                Text("Your Gallery", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Choose a photo or video", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Tune, contentDescription = "Refresh") }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(GalleryTab.ALL to "All", GalleryTab.VIDEOS to "Videos", GalleryTab.PHOTOS to "Photos").forEach { (tab, label) ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { onTab(tab) },
                    label = { Text(label) }
                )
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Orange)
            }
        } else if (assets.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Muted, modifier = Modifier.size(48.dp))
                    Text("No media found", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Try another tab or add photos and videos to your device gallery.", color = Muted, textAlign = TextAlign.Center, fontSize = 13.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(assets, key = { it.uri.toString() }) { asset ->
                    GalleryTile(asset = asset, onClick = { onSelect(asset) })
                }
            }
        }
    }
}

@Composable
private fun GalleryTile(asset: MediaAsset, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, asset.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(asset.uri, Size(360, 360), null)
                } else {
                    val id = asset.uri.lastPathSegment?.toLongOrNull() ?: return@runCatching null
                    if (asset.isVideo) {
                        MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                    } else {
                        MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null)
                    }
                }
            }.getOrNull()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.82f).background(PanelSoft), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = asset.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    if (asset.isVideo) Icons.Default.Movie else Icons.Default.Image,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(32.dp)
                )
            }
            if (asset.isVideo) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    color = ComposeColor(0xCC050609),
                    shape = RoundedCornerShape(5.dp)
                ) {
                    Text(formatClock(asset.durationMs), color = ComposeColor.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        }
        Text(
            asset.name,
            color = Muted,
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun EditorScreen(
    asset: MediaAsset,
    onBack: () -> Unit,
    onPickAnother: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cues by remember(asset.uri) { mutableStateOf(listOf(CaptionCue("Your caption here", 0L, 3_000L))) }
    var style by remember(asset.uri) { mutableStateOf(CaptionStyleState()) }
    var selectedIndex by remember(asset.uri) { mutableIntStateOf(0) }
    var currentPosition by remember(asset.uri) { mutableLongStateOf(0L) }
    var durationMs by remember(asset.uri) { mutableLongStateOf(if (asset.isVideo) asset.durationMs else 6_000L) }
    var canvasPreset by remember(asset.uri) { mutableStateOf(CanvasPreset.PORTRAIT) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textDraft by remember { mutableStateOf("") }
    var showExporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val undoStack = remember(asset.uri) { mutableStateListOf<List<CaptionCue>>() }
    val redoStack = remember(asset.uri) { mutableStateListOf<List<CaptionCue>>() }

    fun applyCues(next: List<CaptionCue>) {
        undoStack.add(cues)
        if (undoStack.size > 30) undoStack.removeAt(0)
        cues = next
        redoStack.clear()
        selectedIndex = selectedIndex.coerceIn(0, (next.size - 1).coerceAtLeast(0))
    }

    val player = remember(asset.uri) {
        if (asset.isVideo) ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(asset.uri))
            prepare()
            playWhenReady = false
        } else null
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }
    LaunchedEffect(player) {
        while (true) {
            if (player != null) {
                if (player.duration > 0) durationMs = player.duration
                if (player.isPlaying) currentPosition = player.currentPosition
            }
            delay(150)
        }
    }

    val importSrtLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val parsed = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { parseSrt(it.readText()) } ?: emptyList()
                    }.getOrElse { emptyList() }
                }
                if (parsed.isNotEmpty()) {
                    applyCues(parsed)
                    selectedIndex = 0
                    message = "SRT captions imported"
                } else {
                    message = "No valid captions found in this SRT file"
                }
            }
        }
    }
    val exportSrtLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(createSrt(cues)) }
                            ?: throw IOException("Could not write SRT file")
                    }
                }.onSuccess { message = "SRT file saved" }
                    .onFailure { message = it.message ?: "SRT save failed" }
            }
        }
    }

    val activeCue = cues.firstOrNull { currentPosition >= it.startMs && currentPosition <= it.endMs }
        ?: cues.getOrNull(selectedIndex)

    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column(modifier = Modifier.weight(1f)) {
                Text("CC Editor", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(if (asset.isVideo) "VIDEO PROJECT" else "PHOTO PROJECT", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
            }
            TextButton(onClick = { importSrtLauncher.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream")) }) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("SRT")
            }
            Button(
                onClick = {
                    exportCaptionedMedia(
                        context, asset, cues, style,
                        onStarted = { showExporting = true },
                        onCompleted = {
                            showExporting = false
                            message = if (Build.VERSION.SDK_INT >= 29) "Video saved in Movies / CC Editor" else "Video saved to app Movies folder"
                        },
                        onError = {
                            showExporting = false
                            message = "Export failed: $it"
                        }
                    )
                },
                enabled = !showExporting,
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = ComposeColor.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export", fontWeight = FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(ComposeColor(0xFF050609))
                .border(1.dp, ComposeColor(0x22FF793D), RoundedCornerShape(22.dp))
        ) {
            if (asset.isVideo && player != null) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setKeepContentOnPlayerReset(true)
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val bitmap by produceState<Bitmap?>(initialValue = null, asset.uri) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= 29) {
                                context.contentResolver.loadThumbnail(asset.uri, Size(1080, 1080), null)
                            } else {
                                context.contentResolver.openInputStream(asset.uri)?.use { BitmapFactory.decodeStream(it) }
                            }
                        }.getOrNull()
                    }
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = asset.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Muted, modifier = Modifier.align(Alignment.Center).size(48.dp))
                }
            }

            if (!asset.isVideo) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    color = ComposeColor(0xAA08090D),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("6 SEC PHOTO", color = Orange, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                }
            }

            if (!activeCue?.text.isNullOrBlank()) {
                val captionShape = when (style.background) {
                    CaptionBackground.PILL -> RoundedCornerShape(16.dp)
                    CaptionBackground.OUTLINE -> RoundedCornerShape(12.dp)
                    CaptionBackground.PLAIN -> RoundedCornerShape(2.dp)
                }
                Text(
                    activeCue?.text.orEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 42.dp)
                        .clip(captionShape)
                        .then(if (style.background == CaptionBackground.OUTLINE) Modifier.border(1.dp, ComposeColor.White, captionShape) else Modifier)
                        .background(if (style.background == CaptionBackground.PILL) ComposeColor(0xE8000000) else ComposeColor.Transparent)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = runCatching { ComposeColor(android.graphics.Color.parseColor(style.colorHex)) }.getOrDefault(ComposeColor.White),
                    fontSize = (22 * style.fontScale).sp,
                    fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (style.italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textDecoration = if (style.underline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                    textAlign = TextAlign.Center
                )
            }

            if (asset.isVideo && player != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    color = ComposeColor(0xAA08090D),
                    shape = CircleShape
                ) {
                    IconButton(onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                    }) {
                        Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play or pause", tint = ComposeColor.White)
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (asset.isVideo) formatClock(currentPosition) else "00:00", color = ComposeColor.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = currentPosition.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
                    onValueChange = {
                        currentPosition = it.toLong()
                        player?.seekTo(currentPosition)
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(formatClock(durationMs), color = Muted, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CAPTION TIMELINE", color = Muted, fontSize = 9.sp, letterSpacing = 1.3.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val current = cues.getOrNull(selectedIndex)
                    val nextStart = current?.endMs ?: currentPosition
                    applyCues(cues + CaptionCue("New caption", nextStart, nextStart + 2_000L))
                    selectedIndex = cues.lastIndex
                    textDraft = cues.lastOrNull()?.text ?: "New caption"
                    showTextDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add caption", tint = Orange)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                items(cues.size) { index ->
                    val cue = cues[index]
                    Surface(
                        color = if (index == selectedIndex) ComposeColor(0xFF39241D) else PanelSoft,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable {
                            selectedIndex = index
                            currentPosition = cue.startMs
                            player?.seekTo(cue.startMs)
                        }.border(1.dp, if (index == selectedIndex) Orange else ComposeColor.Transparent, RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.width(120.dp).padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(cue.text.replace("\n", " "), maxLines = 1, color = ComposeColor.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("${formatClock(cue.startMs)} - ${formatClock(cue.endMs)}", color = Muted, fontSize = 9.sp)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                EditorAction("Undo", Icons.Default.Undo, enabled = undoStack.isNotEmpty()) {
                    if (undoStack.isNotEmpty()) {
                        redoStack.add(cues)
                        cues = undoStack.removeAt(undoStack.lastIndex)
                        selectedIndex = selectedIndex.coerceIn(0, (cues.lastIndex).coerceAtLeast(0))
                    }
                }
                EditorAction("Redo", Icons.Default.Redo, enabled = redoStack.isNotEmpty()) {
                    if (redoStack.isNotEmpty()) {
                        undoStack.add(cues)
                        cues = redoStack.removeAt(redoStack.lastIndex)
                        selectedIndex = selectedIndex.coerceIn(0, (cues.lastIndex).coerceAtLeast(0))
                    }
                }
                EditorAction("Edit", Icons.Default.Edit) {
                    textDraft = cues.getOrNull(selectedIndex)?.text.orEmpty()
                    showTextDialog = true
                }
                EditorAction("Delete", Icons.Default.Delete, enabled = cues.isNotEmpty()) {
                    if (cues.isNotEmpty()) applyCues(cues.filterIndexed { index, _ -> index != selectedIndex })
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().background(Ink).padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EditorAction("Canvas", Icons.Default.Tune) {
                    canvasPreset = when (canvasPreset) {
                        CanvasPreset.PORTRAIT -> CanvasPreset.SQUARE
                        CanvasPreset.SQUARE -> CanvasPreset.LANDSCAPE
                        CanvasPreset.LANDSCAPE -> CanvasPreset.ORIGINAL
                        CanvasPreset.ORIGINAL -> CanvasPreset.PORTRAIT
                    }
                    message = "Canvas preset: " + canvasPreset.title
                }
                EditorAction("Break line", Icons.Default.ContentCut) {
                    val cue = cues.getOrNull(selectedIndex)
                    if (cue != null && cue.text.contains(' ')) {
                        val nextText = cue.text.replaceFirst(" ", "\n")
                        applyCues(cues.mapIndexed { index, item -> if (index == selectedIndex) item.copy(text = nextText) else item })
                    }
                }
                EditorAction("Color", Icons.Default.ColorLens) {
                    val colors = listOf("#FFFFFF", "#FFCA5C", "#FF793D", "#71E2C2", "#FF6B92", "#8AB4FF")
                    val index = colors.indexOf(style.colorHex).let { if (it < 0) 0 else it }
                    style = style.copy(colorHex = colors[(index + 1) % colors.size])
                }
                EditorAction("Bold", Icons.Default.FormatBold, active = style.bold) { style = style.copy(bold = !style.bold) }
                EditorAction("Italic", Icons.Default.FormatItalic, active = style.italic) { style = style.copy(italic = !style.italic) }
                EditorAction("Underline", Icons.Default.FormatUnderlined, active = style.underline) { style = style.copy(underline = !style.underline) }
                EditorAction("Style", Icons.Default.AutoAwesome) {
                    style = style.copy(background = when (style.background) {
                        CaptionBackground.PILL -> CaptionBackground.OUTLINE
                        CaptionBackground.OUTLINE -> CaptionBackground.PLAIN
                        CaptionBackground.PLAIN -> CaptionBackground.PILL
                    })
                }
                EditorAction("Prepare Post", Icons.Default.TextFields) {
                    textDraft = cues.getOrNull(selectedIndex)?.text.orEmpty()
                    showTextDialog = true
                }
                EditorAction("AI Denoise", Icons.Default.AutoAwesome, enabled = false) { }
            }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Edit caption") },
            text = {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    label = { Text("Caption text") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textDraft.isNotBlank()) {
                        val current = cues.getOrNull(selectedIndex)
                        if (current != null) applyCues(cues.mapIndexed { index, cue -> if (index == selectedIndex) cue.copy(text = textDraft.trim()) else cue })
                    }
                    showTextDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            },
            containerColor = Panel
        )
    }

    if (showExporting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Exporting your video") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = Orange, modifier = Modifier.size(28.dp))
                    Text("Rendering captions and saving the MP4. Keep CC Editor open.")
                }
            },
            confirmButton = {},
            containerColor = Panel
        )
    }

    if (message != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("CC Editor") },
            text = { Text(message.orEmpty()) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            containerColor = Panel
        )
    }
}

@Composable
private fun EditorAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(67.dp).clip(RoundedCornerShape(12.dp))
            .background(if (active) ComposeColor(0xFF38231D) else Panel)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (!enabled) ComposeColor(0xFF555963) else if (active) Orange else ComposeColor.White, modifier = Modifier.size(21.dp))
        Text(label, color = if (!enabled) ComposeColor(0xFF555963) else if (active) Orange else Muted, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

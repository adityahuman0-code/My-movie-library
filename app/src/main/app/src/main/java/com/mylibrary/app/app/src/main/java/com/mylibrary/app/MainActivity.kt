package com.mylibrary.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Black = Color(0xFF000000)
private val CardColor = Color(0xFF1C1C1E)
private val Accent = Color(0xFF0A84FF)
private val Muted = Color(0xFF8E8E93)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Black, surface = CardColor)) {
                App()
            }
        }
    }
}

fun fmt(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "$m min"
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(perm) == 0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var playing by remember { mutableStateOf<Video?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(perm) }
    LaunchedEffect(granted) {
        if (granted) videos = withContext(Dispatchers.IO) { Library.scan(ctx) }
    }
    BackHandler(enabled = playing != null) { playing = null; tick++ }

    Box(Modifier.fillMaxSize().background(Black)) {
        if (!granted) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Allow access to your videos", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Videos stay on your phone.", color = Muted)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { launcher.launch(perm) }) { Text("Allow access") }
            }
        } else if (videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No videos found on this phone", color = Muted)
            }
        } else {
            Library(videos, tick) { playing = it }
        }
        playing?.let { Player(it) }
    }
}

@Composable
fun Library(videos: List<Video>, tick: Int, onPlay: (Video) -> Unit) {
    val ctx = LocalContext.current
    val recent = remember(videos, tick) {
        videos.filter { Library.getLast(ctx, it.id) > 0 }.sortedByDescending { Library.getLast(ctx, it.id) }
    }
    val cont = remember(recent) {
        recent.filter {
            val p = Library.getPos(ctx, it.id)
            p > 5000 && p < it.durationMs * 0.95
        }
    }
    val hero = recent.firstOrNull() ?: videos.first()
    val sorted = remember(videos) { videos.sortedBy { it.title.lowercase() } }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("My Library", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val resuming = Library.getPos(ctx, hero.id) > 5000
            Box(
                Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(26.dp)).background(CardColor)
                    .clickable { onPlay(hero) }
            ) {
                Thumb(hero, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))))
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(if (resuming) "Continue watching" else "Up next", color = Color(0xFFD1D1D6), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(hero.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onPlay(hero) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) { Text(if (resuming) "Resume" else "Play", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
        if (cont.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Continue Watching", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(cont) { v -> Poster(v, Modifier.width(130.dp)) { onPlay(v) } }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("All Videos", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        items(sorted) { v -> Poster(v, Modifier) { onPlay(v) } }
    }
}

@Composable
fun Thumb(v: Video, modifier: Modifier) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(v.uri)
            .decoderFactory(VideoFrameDecoder.Factory())
            .videoFrameMillis(v.durationMs / 10)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
fun Poster(v: Video, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val pos = Library.getPos(ctx, v.id)
    val progress = if (v.durationMs > 0) (pos.toFloat() / v.durationMs).coerceIn(0f, 1f) else 0f
    Column(modifier.clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(CardColor)) {
            Thumb(v, Modifier.fillMaxSize())
            if (progress > 0.01f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp).height(4.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color.White,
                    trackColor = Color(0x55FFFFFF)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(v.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(fmt(v.durationMs), color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun Player(v: Video) {
    val ctx = LocalContext.current
    val player = remember(v) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(v.uri))
            val p = Library.getPos(ctx, v.id)
            if (p > 5000 && p < v.durationMs - 10000) seekTo(p)
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(3000)
            Library.savePos(ctx, v.id, player.currentPosition)
        }
    }
    DisposableEffect(player) {
        onDispose {
            val end = player.duration > 0 && player.currentPosition >= player.duration - 3000
            Library.savePos(ctx, v.id, if (end) 0L else player.currentPosition)
            player.release()
        }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; keepScreenOn = true } },
        modifier = Modifier.fillMaxSize().background(Black)
    )
}

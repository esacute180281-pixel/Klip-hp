package com.kliphp.app

import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Effects
import androidx.media3.effect.Presentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject

private val Bg = Color(0xFF101014)
private val Panel = Color(0xFF1D1D24)
private val Red = Color(0xFFE53935)
private val Muted = Color(0xFFB8B8C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KlipHpApp() }
    }
}

@Composable
fun KlipHpApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoName by remember { mutableStateOf("Belum ada video dipilih") }
    var durationMs by remember { mutableLongStateOf(0L) }
    var clipLength by remember { mutableIntStateOf(30) }
    var resolution by remember { mutableIntStateOf(720) }
    var apiKey by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Siap. Pilih video untuk mulai.") }
    var busy by remember { mutableStateOf(false) }
    var clipStartMs by remember { mutableLongStateOf(0L) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            videoUri = uri
            videoName = uri.lastPathSegment?.substringAfterLast('/') ?: "Video dipilih"
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                status = "Video siap dianalisis • ${durationMs / 1000} detik"
            } catch (e: Exception) { status = "Tidak bisa membaca video: ${e.message}" }
            finally { retriever.release() }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Red, background = Bg, surface = Panel, onBackground = Color.White,
        onSurface = Color.White, secondary = Color(0xFFFF7772)
    )) {
        Column(
            Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Red, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Movie, null, tint = Color.White) }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("KlipHP", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("VIDEO EDITOR • AUTO CLIP", fontSize = 10.sp, color = Muted, letterSpacing = 1.5.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Settings, contentDescription = "Pengaturan", tint = Muted)
            }

            Text("Ubah video panjang jadi konten pendek.", color = Muted, fontSize = 14.sp)
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. PILIH VIDEO", color = Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(videoName, color = Color.White, fontWeight = FontWeight.Medium)
                    Button(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.VideoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ambil dari Galeri")
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("2. PENGATURAN AUTO CLIP", color = Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Durasi klip")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { n ->
                            FilterChip(selected = clipLength == n, onClick = { clipLength = n }, label = { Text("$n detik") })
                        }
                    }
                    Text("Resolusi ekspor")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(720, 1080).forEach { n ->
                            FilterChip(selected = resolution == n, onClick = { resolution = n }, label = { Text("${n}p") })
                        }
                    }
                    Text("AI memilih segmen dengan perubahan visual paling aktif. Periksa hasil sebelum dipublikasikan.", color = Muted, fontSize = 12.sp)
                    Button(
                        enabled = videoUri != null && !busy,
                        onClick = {
                            val uri = videoUri ?: return@Button
                            busy = true
                            scope.launch {
                                try {
                                    val start = withContext(Dispatchers.IO) { findHighlightStart(context, uri, durationMs, clipLength * 1000L) }
                                    clipStartMs = start
                                    status = "Bagian pilihan otomatis: ${start / 1000}–${(start + clipLength * 1000L) / 1000} detik. Siap diekspor."
                                } catch (e: Exception) { status = "Analisis gagal: ${e.message}" }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pilih Bagian Menarik Otomatis")
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("3. SUBTITLE AI", color = Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        label = { Text("OpenAI API Key") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Kunci hanya disimpan sementara selama aplikasi terbuka. Video dikirim ke layanan transkripsi; batas API 25 MB.", color = Muted, fontSize = 11.sp)
                    OutlinedButton(
                        enabled = videoUri != null && apiKey.isNotBlank() && !busy,
                        onClick = {
                            val uri = videoUri ?: return@OutlinedButton
                            busy = true
                            scope.launch {
                                try {
                                    transcript = withContext(Dispatchers.IO) { transcribe(context, uri, apiKey.trim()) }
                                    status = "Transkripsi selesai. Teks subtitle tampil di bawah."
                                } catch (e: Exception) { status = "Subtitle gagal: ${e.message}" }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.ClosedCaption, null); Spacer(Modifier.width(8.dp)); Text("Buat Subtitle Indonesia") }
                    if (transcript.isNotBlank()) {
                        OutlinedTextField(value = transcript, onValueChange = { transcript = it }, label = { Text("Hasil transkripsi") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        Text("Catatan: versi awal menampilkan hasil transkripsi untuk disalin. Pembuatan file SRT dan pembakaran teks ke video adalah tahap pengembangan berikutnya.", color = Muted, fontSize = 11.sp)
                    }
                }
            }

            Button(
                enabled = videoUri != null && !busy,
                onClick = {
                    val uri = videoUri ?: return@Button
                    busy = true
                    status = "Menyiapkan ekspor MP4…"
                    exportClip(context, uri, clipStartMs, clipLength * 1000L, resolution,
                        onDone = { output -> status = "Berhasil disimpan: $output"; busy = false },
                        onError = { error -> status = "Ekspor gagal: $error"; busy = false })
                },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("EXPORT MP4 ${resolution}P", fontWeight = FontWeight.Bold)
            }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red)
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF292027))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Red)
                    Spacer(Modifier.width(10.dp))
                    Text(status, color = Color.White, fontSize = 12.sp)
                }
            }
            Text("KlipHP 1.0 • Build awal • Gunakan video pendek untuk uji coba pertama.", color = Muted, fontSize = 10.sp)
        }
    }
}

/**
 * Heuristik lokal: sampling thumbnail per 1.5 detik dan mencari rentang dengan
 * perubahan visual/warna paling tinggi. Ini bukan model AI semantik; disebut
 * Auto Highlight beta agar ekspektasi pengguna tetap akurat.
 */
private fun findHighlightStart(context: android.content.Context, uri: Uri, totalMs: Long, clipMs: Long): Long {
    if (totalMs <= clipMs) return 0L
    val retriever = MediaMetadataRetriever()
    retriever.setDataSource(context, uri)
    try {
        val step = 1500L
        val scores = mutableListOf<Pair<Long, Double>>()
        var previous: IntArray? = null
        var t = 0L
        while (t < totalMs) {
            val bmp = retriever.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bmp != null) {
                val sample = IntArray(16)
                for (i in 0 until 16) {
                    val x = (bmp.width * ((i % 4) + 0.5f) / 4f).toInt().coerceIn(0, bmp.width - 1)
                    val y = (bmp.height * ((i / 4) + 0.5f) / 4f).toInt().coerceIn(0, bmp.height - 1)
                    sample[i] = bmp.getPixel(x, y)
                }
                val diff = if (previous == null) 0.0 else sample.indices.map { i ->
                    val a = sample[i]; val b = previous!![i]
                    (kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                     kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                     kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))).toDouble() / 3.0
                }.average()
                scores.add(t to diff)
                previous = sample
                bmp.recycle()
            }
            t += step
        }
        val maxStart = (totalMs - clipMs).coerceAtLeast(0)
        val windows = scores.filter { it.first <= maxStart }
        return windows.maxByOrNull { it.second }?.first ?: 0L
    } finally { retriever.release() }
}

private fun exportClip(
    context: android.content.Context, uri: Uri, startMs: Long, lengthMs: Long, height: Int,
    onDone: (String) -> Unit, onError: (String) -> Unit
) {
    try {
        val out = File(context.getExternalFilesDir(null), "KlipHP_${System.currentTimeMillis()}.mp4")
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(startMs + lengthMs)
            .build()
        val mediaItem = MediaItem.Builder().setUri(uri).setClippingConfiguration(clipping).build()
        val effects = Effects(emptyList(), listOf(Presentation.createForHeight(height)))
        val edited = EditedMediaItem.Builder(mediaItem).setEffects(effects).build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onDone(out.absolutePath)
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    onError(exportException.message ?: "Kesalahan pemrosesan video")
                }
            }).build()
        transformer.start(edited, out.absolutePath)
    } catch (e: Exception) { onError(e.message ?: "Kesalahan tidak diketahui") }
}

private fun transcribe(context: android.content.Context, uri: Uri, key: String): String {
    val temp = File.createTempFile("kliphp_audio_", ".mp4", context.cacheDir)
    context.contentResolver.openInputStream(uri).use { input ->
        FileOutputStream(temp).use { output -> input?.copyTo(output) ?: error("Video tidak dapat dibaca") }
    }
    if (temp.length() > 25L * 1024L * 1024L) {
        temp.delete()
        error("Ukuran video melebihi batas 25 MB. Gunakan video lebih pendek atau kompres dahulu.")
    }
    val boundary = "----KlipHP${System.currentTimeMillis()}"
    val connection = (URL("https://api.openai.com/v1/audio/transcriptions").openConnection() as HttpURLConnection)
    try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 120000
        connection.setRequestProperty("Authorization", "Bearer $key")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.outputStream.use { out ->
            fun field(name: String, value: String) {
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("model", "whisper-1")
            field("language", "id")
            field("response_format", "text")
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"file\"; filename=\"clip.mp4\"\r\n".toByteArray())
            out.write("Content-Type: video/mp4\r\n\r\n".toByteArray())
            temp.inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            .bufferedReader().use { it.readText() }
        if (code !in 200..299) error("API $code: $body")
        return body
    } finally {
        connection.disconnect()
        temp.delete()
    }
}

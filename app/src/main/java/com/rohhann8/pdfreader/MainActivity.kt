package com.rohhann8.pdfreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.LinkedHashMap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PdfReaderApp() }
    }
}

@Composable
private fun PdfReaderApp(reader: PdfViewModel = viewModel()) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, 1) } catch (_: Exception) { }
        reader.open(context, uri)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(reader.title ?: "Light PDF Reader") }, actions = {
            IconButton(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("Open") }
        })
    }) { padding ->
        Surface(Modifier.padding(padding).fillMaxSize()) {
            if (reader.pageCount == 0) {
                EmptyState { picker.launch(arrayOf("application/pdf")) }
            } else {
                PdfPages(reader)
            }
        }
    }
}

@Composable
private fun EmptyState(onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Read PDFs with less memory", style = MaterialTheme.typography.headlineSmall)
        Text("Pages are rendered only when visible. Your files stay on this device.", Modifier.padding(top = 12.dp, bottom = 24.dp))
        Button(onClick = onOpen) { Text("Choose a PDF") }
    }
}

@Composable
private fun PdfPages(reader: PdfViewModel) {
    val state = rememberLazyListState()
    var zoom by remember { mutableFloatStateOf(1f) }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${state.firstVisibleItemIndex + 1} / ${reader.pageCount}", Modifier.weight(1f))
            IconButton(onClick = { zoom = (zoom - .1f).coerceAtLeast(.6f) }) { Text("−") }
            Text("${(zoom * 100).toInt()}%")
            IconButton(onClick = { zoom = (zoom + .1f).coerceAtMost(2f) }) { Text("+") }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            items((0 until reader.pageCount).toList(), key = { it }) { page ->
                PdfPage(reader, page, zoom)
            }
        }
    }
}

@Composable
private fun PdfPage(reader: PdfViewModel, page: Int, zoom: Float) {
    var bitmap by remember(page, zoom) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page, zoom, reader.documentKey) {
        bitmap = reader.render(page, zoom)
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(androidx.compose.ui.graphics.Color(0xFFE8E8E8)), contentAlignment = Alignment.Center) {
        if (bitmap == null) CircularProgressIndicator(Modifier.padding(48.dp))
        else Image(bitmap!!.asImageBitmap(), "Page ${page + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    }
}

class PdfViewModel : ViewModel() {
    var pageCount by mutableStateOf(0); private set
    var title by mutableStateOf<String?>(null); private set
    var documentKey by mutableStateOf(0); private set
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private val cache = object : LinkedHashMap<String, Bitmap>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 3
    }

    fun open(context: Context, uri: Uri) {
        closeDocument()
        try {
            descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            renderer = descriptor?.let { PdfRenderer(it) }
            pageCount = renderer?.pageCount ?: 0
            title = uri.lastPathSegment?.substringAfterLast('/') ?: "PDF"
            documentKey++
        } catch (_: IOException) { closeDocument() }
    }

    suspend fun render(page: Int, zoom: Float): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$documentKey:$page:${(zoom * 10).toInt()}"
        synchronized(cache) { cache[key]?.let { return@withContext it } }
        val pdf = renderer ?: return@withContext null
        synchronized(pdf) {
            if (page !in 0 until pdf.pageCount) return@withContext null
            pdf.openPage(page).use { source ->
                val width = (source.width * zoom).toInt().coerceAtMost(2200)
                val height = (source.height * width.toFloat() / source.width).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                bitmap.eraseColor(Color.WHITE)
                source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                synchronized(cache) { cache[key] = bitmap }
                bitmap
            }
        }
    }

    private fun closeDocument() {
        synchronized(cache) { cache.values.forEach { it.recycle() }; cache.clear() }
        renderer?.close(); renderer = null
        descriptor?.close(); descriptor = null
        pageCount = 0
    }

    override fun onCleared() { closeDocument() }
}

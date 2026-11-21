package science.prokulski.apknewsreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors

private val executor = Executors.newSingleThreadExecutor()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ReaderScreen() } }
    }
}

@Composable
private fun ReaderScreen() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    var address by remember { mutableStateOf("") }
    var stories by remember { mutableStateOf(store.articles()) }
    var sources by remember { mutableStateOf(store.sources()) }
    var status by remember { mutableStateOf("Add a website, RSS feed, podcast or YouTube channel URL.") }
    var loading by remember { mutableStateOf(false) }

    fun refresh(url: String) {
        if (loading) return
        loading = true
        status = "Loading $url"
        executor.execute {
            val result = runCatching {
                val found = AutoSource.discover(url)
                store.addSource(url)
                store.save(found)
                found.size
            }
            (context as ComponentActivity).runOnUiThread {
                loading = false
                result.onSuccess {
                    sources = store.sources()
                    stories = store.articles()
                    status = "Found $it entries. ${stories.size} cached."
                }.onFailure { status = "Could not load: ${it.message}" }
            }
        }
    }

    Scaffold(topBar = {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("News Reader", style = MaterialTheme.typography.headlineSmall)
            Row {
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Website or feed URL") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { refresh(address.trim()) }, enabled = !loading && address.startsWith("http"), modifier = Modifier.padding(top = 8.dp)) { Text("Add") }
            }
            if (sources.isNotEmpty()) {
                TextButton(onClick = {
                    if (!loading) {
                        val queue = sources.toList()
                        loading = true; status = "Refreshing ${queue.size} sources"
                        executor.execute {
                            var count = 0
                            for (url in queue) runCatching { val items = AutoSource.discover(url); store.save(items); count += items.size }
                            (context as ComponentActivity).runOnUiThread {
                                stories = store.articles(); loading = false; status = "Checked ${queue.size} sources, received $count entries"
                            }
                        }
                    }
                }, enabled = !loading) { Text("Refresh all (${sources.size})") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(stories, key = { it.id }) { story ->
                var expanded by remember(story.id) { mutableStateOf(false) }
                ElevatedCard(Modifier.fillMaxWidth().clickable {
                    expanded = !expanded
                    if (expanded) store.markRead(story.id)
                }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(story.source, style = MaterialTheme.typography.labelMedium)
                        Text(story.title, style = MaterialTheme.typography.titleMedium)
                        if (story.date.isNotBlank()) Text(story.date, style = MaterialTheme.typography.labelSmall)
                        if (expanded) {
                            Spacer(Modifier.height(12.dp))
                            Text(story.summary.ifBlank { "No excerpt provided by the publisher." })
                            Row {
                                TextButton(onClick = { store.bookmark(story.id) }) { Text("Save / unsave") }
                                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(story.link))) }) { Text("Open") }
                            }
                            if (story.mediaType.startsWith("audio/") && story.media.isNotBlank()) {
                                Button(onClick = {
                                    val intent = Intent(context, PodcastService::class.java)
                                        .setAction(PodcastService.PLAY)
                                        .putExtra(PodcastService.URL, story.media)
                                        .putExtra(PodcastService.TITLE, story.title)
                                    context.startForegroundService(intent)
                                }) { Text("Play episode") }
                                TextButton(onClick = {
                                    context.startService(Intent(context, PodcastService::class.java).setAction(PodcastService.PAUSE))
                                }) { Text("Pause") }
                                TextButton(onClick = {
                                    context.startService(Intent(context, PodcastService::class.java).setAction(PodcastService.STOP))
                                }) { Text("Stop") }
                            }
                        }
                    }
                }
            }
        }
    }
}

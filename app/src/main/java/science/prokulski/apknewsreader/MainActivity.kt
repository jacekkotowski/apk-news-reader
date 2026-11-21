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

private val worker = Executors.newSingleThreadExecutor()
private const val DEFAULT_FEED = "https://blog.prokulski.science/feed/"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ReaderScreen() } }
    }
}

@Composable
private fun ReaderScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("reader", 0) }
    var feed by remember { mutableStateOf(prefs.getString("feed", DEFAULT_FEED) ?: DEFAULT_FEED) }
    var stories by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var status by remember { mutableStateOf("Enter an RSS or Atom URL, then tap Refresh.") }
    var loading by remember { mutableStateOf(false) }
    fun refresh() {
        if (loading) return
        loading = true
        status = "Fetching…"
        prefs.edit().putString("feed", feed).apply()
        worker.execute {
            val result = runCatching { FeedReader.fetch(feed) }
            (context as ComponentActivity).runOnUiThread {
                loading = false
                result.onSuccess { stories = it; status = "${it.size} articles" }
                    .onFailure { status = "Unable to load feed: ${it.message}" }
            }
        }
    }
    Scaffold(topBar = {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("News Reader", style = MaterialTheme.typography.headlineSmall)
            Row {
                OutlinedTextField(value = feed, onValueChange = { feed = it }, label = { Text("Feed URL") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { refresh() }, enabled = !loading, modifier = Modifier.padding(top = 8.dp)) { Text("Refresh") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(stories, key = { it.id }) { story ->
                var expanded by remember(story.id) { mutableStateOf(false) }
                ElevatedCard(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(story.source, style = MaterialTheme.typography.labelMedium)
                        Text(story.title, style = MaterialTheme.typography.titleMedium)
                        if (story.date.isNotBlank()) Text(story.date, style = MaterialTheme.typography.labelSmall)
                        if (expanded) {
                            Spacer(Modifier.height(12.dp))
                            Text(story.summary.ifBlank { "No excerpt provided by publisher." })
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(story.link))) }) { Text("Open original article") }
                        }
                    }
                }
            }
        }
    }
}

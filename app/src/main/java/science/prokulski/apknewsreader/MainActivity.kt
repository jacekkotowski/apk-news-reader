package science.prokulski.apknewsreader

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
import androidx.compose.ui.unit.dp

private data class Story(val title: String, val source: String, val excerpt: String)
private val examples = listOf(
    Story("Your reading starts here", "Welcome", "Add RSS, blogs, YouTube channels and podcasts in upcoming milestones."),
    Story("Articles without RSS", "AutoSource", "Discover article lists from website pages, sitemaps and metadata."),
    Story("Listen while you read", "Podcasts", "Podcast playback will continue while browsing the feed.")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(topBar = { Text("News Reader", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.headlineMedium) }) { padding ->
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(examples) { story ->
                            var expanded by remember(story.title) { mutableStateOf(false) }
                            ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(story.source, style = MaterialTheme.typography.labelMedium)
                                    Text(story.title, style = MaterialTheme.typography.titleLarge)
                                    if (expanded) { Spacer(Modifier.height(12.dp)); Text(story.excerpt) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

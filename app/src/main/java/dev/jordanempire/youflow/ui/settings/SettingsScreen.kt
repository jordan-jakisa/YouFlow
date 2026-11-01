package dev.jordanempire.youflow.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import dev.jordanempire.youflow.R
import dev.jordanempire.youflow.local.history.HistoryRecordManager
import dev.jordanempire.youflow.ui.data.CommunityPacks
import dev.jordanempire.youflow.ui.data.YouTubeRepository
import dev.jordanempire.youflow.ui.data.YouTubeTakeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext

private val ThemeChoices = listOf(
    AppSettings.THEME_SYSTEM to "Follow system",
    AppSettings.THEME_LIGHT to "Light",
    AppSettings.THEME_DARK to "Dark",
    AppSettings.THEME_BLACK to "Pure black (OLED)"
)

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPage: (route: String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val settings = remember { AppSettings.get(app) }
    val theme by settings.theme.collectAsState()
    val dynamic by settings.dynamicColor.collectAsState()
    val autoplay by settings.autoplay.collectAsState()
    val navOrder by settings.navOrder.collectAsState()
    val showShorts by settings.showShorts.collectAsState()
    val frameThumbnails by settings.frameThumbnails.collectAsState()
    val hiddenTopics by settings.hiddenHomeTopics.collectAsState()
    var pickTopics by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf(0) }
    val scope = remember { CoroutineScope(Dispatchers.Main) }
    LaunchedEffect(Unit) { importedCount = withContext(Dispatchers.IO) { YouTubeTakeout.load(app).size } }
    val takeout = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            toast(context, "Importing YouTube history")
            scope.launch {
                val count = runCatching { YouTubeRepository(app).importTakeout(uri, uri.lastPathSegment.orEmpty()) }.getOrDefault(0)
                if (count > 0) importedCount = count
                toast(context, if (count > 0) "Imported $count videos from YouTube" else "No YouTube watch history found in that file")
            }
        }
    }
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(app) }
    var history by remember { mutableStateOf(prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)) }
    var resume by remember { mutableStateOf(prefs.getBoolean(context.getString(R.string.enable_playback_resume_key), true)) }
    var pickTheme by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
            item { Header("Appearance") }
            item {
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text(ThemeChoices.first { it.first == theme }.second) },
                    modifier = Modifier.clickable { pickTheme = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Use wallpaper colours") },
                    supportingContent = { Text("Material You colours instead of the YouFlow palette") },
                    trailingContent = { Switch(checked = dynamic, onCheckedChange = settings::setDynamicColor) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Navigation bar") },
                    supportingContent = { Text(navOrder.filter { showShorts || it != "Shorts" }.joinToString(" · ")) },
                    modifier = Modifier.clickable { onOpenPage("n|") }
                )
            }
            item { Header("Playback") }
            item {
                ListItem(
                    headlineContent = { Text("Autoplay next video") },
                    supportingContent = { Text("When a video ends, play the next one in the queue or a related video") },
                    trailingContent = { Switch(checked = autoplay, onCheckedChange = settings::setAutoplay) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Resume where you left off") },
                    trailingContent = {
                        Switch(checked = resume, onCheckedChange = {
                            resume = it
                            prefs.edit().putBoolean(context.getString(R.string.enable_playback_resume_key), it).apply()
                        })
                    }
                )
            }
            item { Header("Recommendations") }
            item {
                ListItem(
                    headlineContent = { Text("Topics on Home") },
                    supportingContent = {
                        Text(homeTopics.filter { it.first !in hiddenTopics }.joinToString(" · ") { it.second }.ifEmpty { "None" })
                    },
                    modifier = Modifier.clickable { pickTopics = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Frame thumbnails") },
                    supportingContent = { Text("A frame from the middle of each video instead of the uploader's thumbnail") },
                    trailingContent = { Switch(checked = frameThumbnails, onCheckedChange = settings::setFrameThumbnails) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Import YouTube watch history") },
                    supportingContent = {
                        Text(
                            if (importedCount > 0) {
                                "$importedCount videos imported. They shape For you and stay on this phone. Tap to replace."
                            } else {
                                "Pick watch-history.json, .html or the whole zip from Google Takeout. It shapes For you and stays on this phone."
                            }
                        )
                    },
                    modifier = Modifier.clickable { takeout.launch(arrayOf("*/*")) }
                )
            }
            if (importedCount > 0) {
                item {
                    ListItem(
                        headlineContent = { Text("Clear imported YouTube history") },
                        modifier = Modifier.clickable {
                            confirm = "Clear imported YouTube history?" to {
                                scope.launch(Dispatchers.IO) { YouTubeRepository(app).clearImportedHistory() }
                                importedCount = 0
                                toast(context, "Imported YouTube history cleared")
                            }
                        }
                    )
                }
            }
            item { Header("History and privacy") }
            item {
                ListItem(
                    headlineContent = { Text("Save watch history") },
                    trailingContent = {
                        Switch(checked = history, onCheckedChange = {
                            history = it
                            prefs.edit().putBoolean(context.getString(R.string.enable_watch_history_key), it).apply()
                        })
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Clear watch history") },
                    modifier = Modifier.clickable {
                        confirm = "Clear watch history and resume positions?" to {
                            scope.launch(Dispatchers.IO) {
                                val m = HistoryRecordManager(app)
                                runCatching {
                                    m.deleteWholeStreamHistory().await()
                                    m.deleteCompleteStreamStateHistory().await()
                                }
                            }
                            toast(context, "Watch history cleared")
                        }
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Clear search history") },
                    modifier = Modifier.clickable {
                        confirm = "Clear all searches?" to {
                            scope.launch(Dispatchers.IO) { runCatching { HistoryRecordManager(app).deleteCompleteSearchHistory().await() } }
                            toast(context, "Search history cleared")
                        }
                    }
                )
            }
            item { Header("Backup") }
            item { BackupRows() }
            item { Header("More") }
            item {
                ListItem(
                    headlineContent = { Text("Download settings") },
                    supportingContent = { Text("Folders, thread count, retries and mobile data") },
                    modifier = Modifier.clickable { onOpenPage("sd|") }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Content settings") },
                    supportingContent = { Text("Country, language, restricted mode and feed options") },
                    modifier = Modifier.clickable { onOpenPage("sc|") }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("New upload notifications") },
                    supportingContent = { Text("Check interval, network and per channel options") },
                    modifier = Modifier.clickable { onOpenPage("sn|") }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("About") },
                    supportingContent = {
                        Text("YouFlow 0.1.0 · GPL-3.0 · based on NewPipe and NewPipeExtractor. HN picks: data from HackerTube (hackertube.org), ODC-By 1.0.")
                    }
                )
            }
        }
    }

    if (pickTheme) {
        AlertDialog(
            onDismissRequest = { pickTheme = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeChoices.forEach { (value, label) ->
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().clickable {
                                settings.setTheme(value)
                                pickTheme = false
                            }.padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(selected = theme == value, onClick = {
                                settings.setTheme(value)
                                pickTheme = false
                            })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickTheme = false }) { Text("Close") } }
        )
    }
    if (pickTopics) {
        AlertDialog(
            onDismissRequest = { pickTopics = false },
            title = { Text("Topics on Home") },
            text = {
                Column {
                    homeTopics.forEach { (id, label) ->
                        val shown = id !in hiddenTopics
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().clickable { settings.setHomeTopicShown(id, !shown) }.padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Checkbox(checked = shown, onCheckedChange = { settings.setHomeTopicShown(id, it) })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickTopics = false }) { Text("Close") } }
        )
    }
    confirm?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    action()
                    confirm = null
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}

/** The Home topics that can be switched off: HN picks and the community packs. */
private val homeTopics = listOf(CommunityPacks.HN_ID to "HN picks") + CommunityPacks.all.map { it.id to it.title }

@Composable
private fun Header(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

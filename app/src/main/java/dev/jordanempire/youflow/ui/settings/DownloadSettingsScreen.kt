package dev.jordanempire.youflow.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.jordanempire.youflow.R
import dev.jordanempire.youflow.streams.io.StoredDirectoryHelper

@Composable
fun DownloadSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = rememberPrefs()
    var ask by remember { mutableStateOf(prefs.getBoolean(context.getString(R.string.downloads_storage_ask), false)) }
    var refresh by remember { mutableIntStateOf(0) }
    var pendingKey by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val key = pendingKey ?: return@rememberLauncherForActivityResult
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            if (DownloadStorage.save(context, key, uri)) {
                refresh++
            } else {
                Toast.makeText(context, R.string.no_available_dir, Toast.LENGTH_LONG).show()
            }
        }
    }
    val videoKey = context.getString(R.string.download_path_video_key)
    val audioKey = context.getString(R.string.download_path_audio_key)

    PrefScreen("Downloads", onBack) {
        item {
            SwitchPref(R.string.downloads_storage_ask, R.string.downloads_storage_ask_title, false, R.string.downloads_storage_ask_summary) { ask = it }
        }
        item(key = "video-$refresh") {
            ActionPref(
                context.getString(R.string.download_path_title),
                DownloadStorage.describe(context, videoKey) ?: context.getString(R.string.download_path_summary),
                enabled = !ask
            ) {
                pendingKey = videoKey
                picker.launch(StoredDirectoryHelper.getPicker(context))
            }
        }
        item(key = "audio-$refresh") {
            ActionPref(
                context.getString(R.string.download_path_audio_title),
                DownloadStorage.describe(context, audioKey) ?: context.getString(R.string.download_path_audio_summary),
                enabled = !ask
            ) {
                pendingKey = audioKey
                picker.launch(StoredDirectoryHelper.getPicker(context))
            }
        }
        item {
            ListPref(
                R.string.settings_file_charset_key,
                R.string.settings_file_charset_title,
                R.array.settings_filename_charset_name,
                R.array.settings_filename_charset,
                R.string.default_file_charset_value
            )
        }
        item {
            TextPref(
                R.string.settings_file_replacement_character_key,
                R.string.settings_file_replacement_character_title,
                R.string.settings_file_replacement_character_default_value,
                R.string.settings_file_replacement_character_summary
            )
        }
        item {
            ListPref(
                R.string.downloads_maximum_retry,
                R.string.max_retry_msg,
                R.array.downloads_maximum_retry_list,
                R.array.downloads_maximum_retry_list,
                R.string.downloads_maximum_retry_default,
                R.string.max_retry_desc
            )
        }
        item { SwitchPref(R.string.downloads_cross_network, R.string.pause_downloads_on_mobile, false, R.string.pause_downloads_on_mobile_desc) }
        item { SwitchPref(R.string.downloads_queue_limit, R.string.enable_queue_limit, true, R.string.enable_queue_limit_desc) }
    }
}

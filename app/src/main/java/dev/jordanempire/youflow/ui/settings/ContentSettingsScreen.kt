package dev.jordanempire.youflow.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import dev.jordanempire.youflow.DownloaderImpl
import dev.jordanempire.youflow.R
import dev.jordanempire.youflow.util.Localization
import dev.jordanempire.youflow.util.image.ImageStrategy
import dev.jordanempire.youflow.util.image.PreferredImageQuality
import org.schabi.newpipe.extractor.NewPipe

private fun durationNames(context: Context, raw: List<String>, values: List<String>) = raw.mapIndexed { i, name ->
    val seconds = values[i].toIntOrNull() ?: 0
    if (seconds <= 0) name else Localization.localizeDuration(context, seconds)
}

@Composable
fun ContentSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun applyLocalization() = NewPipe.setupLocalization(
        Localization.getPreferredLocalization(context),
        Localization.getPreferredContentCountry(context)
    )
    PrefScreen("Content", onBack) {
        item { PrefHeader("Region") }
        item {
            ListPref(R.string.content_language_key, R.string.content_language_title, R.array.language_names, R.array.language_codes, R.string.default_localization_key) { applyLocalization() }
        }
        item {
            ListPref(R.string.content_country_key, R.string.default_content_country_title, R.array.country_names, R.array.country_codes, R.string.default_localization_key) { applyLocalization() }
        }
        item {
            SwitchPref(R.string.youtube_restricted_mode_enabled, R.string.youtube_restricted_mode_enabled_title, false, R.string.youtube_restricted_mode_enabled_summary) {
                DownloaderImpl.getInstance().updateYoutubeRestrictedModeCookies(context)
            }
        }
        item { PrefHeader("Pictures") }
        item {
            ListPref(R.string.image_quality_key, R.string.image_quality_title, R.array.image_quality_description, R.array.image_quality_values, R.string.image_quality_default, R.string.image_quality_summary) { value ->
                ImageStrategy.setPreferredImageQuality(PreferredImageQuality.fromPreferenceKey(context, value))
                val loader = SingletonImageLoader.get(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
                Toast.makeText(context, R.string.thumbnail_cache_wipe_complete_notice, Toast.LENGTH_SHORT).show()
            }
        }
        item { PrefHeader("Video page") }
        item { SwitchPref(R.string.show_description_key, R.string.show_description_title, true, R.string.show_description_summary) }
        item { SwitchPref(R.string.show_comments_key, R.string.show_comments_title, true, R.string.show_comments_summary) }
        item { SwitchPref(R.string.show_next_video_key, R.string.show_next_and_similar_title, true) }
        item { PrefHeader("Channels") }
        item { MultiPref(R.string.show_channel_tabs_key, R.string.show_channel_tabs, R.array.show_channel_tabs_description_list, R.array.show_channel_tabs_value_list, R.string.show_channel_tabs_summary) }
        item { PrefHeader("Feed") }
        item {
            ListPref(
                R.string.feed_update_threshold_key,
                R.string.feed_update_threshold_title,
                R.array.feed_update_threshold_options,
                R.array.feed_update_threshold_values,
                R.string.feed_update_threshold_default_value,
                R.string.feed_update_threshold_summary,
                entryNames = ::durationNames
            )
        }
        item { SwitchPref(R.string.feed_use_dedicated_fetch_method_key, R.string.feed_use_dedicated_fetch_method_title, false, R.string.feed_use_dedicated_fetch_method_summary) }
        item { MultiPref(R.string.feed_fetch_channel_tabs_key, R.string.feed_fetch_channel_tabs, R.array.feed_fetch_channel_tabs_description_list, R.array.feed_fetch_channel_tabs_value_list, R.string.feed_fetch_channel_tabs_summary) }
    }
}

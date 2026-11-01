package dev.jordanempire.youflow.ui.settings

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** YouFlow's own UI preferences, stored beside the classic ones in the default SharedPreferences. */
class AppSettings private constructor(app: Application) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(app)

    init {
        // One time: move installs that saved the old default (wallpaper colours) to the YouFlow palette.
        if (!prefs.contains(KEY_PALETTE_VERSION)) {
            prefs.edit().putBoolean(KEY_DYNAMIC, false).putInt(KEY_PALETTE_VERSION, 1).apply()
        }
    }

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM)
    val theme: StateFlow<String> = _theme.asStateFlow()
    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()
    private val _autoplay = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, true))
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    private val _navOrder = MutableStateFlow(readNavOrder())

    /** Bottom bar tab keys in display order; every key is present exactly once. */
    val navOrder: StateFlow<List<String>> = _navOrder.asStateFlow()
    private val _showShorts = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SHORTS, true))
    val showShorts: StateFlow<Boolean> = _showShorts.asStateFlow()

    private val _frameThumbnails = MutableStateFlow(prefs.getBoolean(KEY_FRAME_THUMBNAILS, false))

    /** Show a frame from the middle of each video instead of the uploader's thumbnail. */
    val frameThumbnails: StateFlow<Boolean> = _frameThumbnails.asStateFlow()
    private val _hiddenHomeTopics = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_HOME_TOPICS, emptySet()).orEmpty().toSet())

    /** Ids of the Home topics (HN picks, community packs) switched off. */
    val hiddenHomeTopics: StateFlow<Set<String>> = _hiddenHomeTopics.asStateFlow()

    fun setFrameThumbnails(value: Boolean) {
        prefs.edit().putBoolean(KEY_FRAME_THUMBNAILS, value).apply()
        _frameThumbnails.value = value
    }

    fun setHomeTopicShown(id: String, shown: Boolean) {
        val updated = if (shown) _hiddenHomeTopics.value - id else _hiddenHomeTopics.value + id
        prefs.edit().putStringSet(KEY_HIDDEN_HOME_TOPICS, updated).apply()
        _hiddenHomeTopics.value = updated
    }

    private fun readNavOrder(): List<String> {
        val saved = prefs.getString(KEY_NAV_ORDER, null)?.split(',').orEmpty().filter { it in NAV_TABS }.distinct()
        return saved + NAV_TABS.filter { it !in saved }
    }

    /** Moves the tab at [index] to position [target]. */
    fun moveNavTab(index: Int, target: Int) {
        val list = _navOrder.value.toMutableList()
        if (index !in list.indices || target !in list.indices) return
        list.add(target, list.removeAt(index))
        prefs.edit().putString(KEY_NAV_ORDER, list.joinToString(",")).apply()
        _navOrder.value = list
    }

    fun setShowShorts(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SHORTS, value).apply()
        _showShorts.value = value
    }

    fun setTheme(value: String) {
        prefs.edit().putString(KEY_THEME, value).apply()
        _theme.value = value
    }

    fun setDynamicColor(value: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, value).apply()
        _dynamicColor.value = value
    }

    fun setAutoplay(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()
        _autoplay.value = value
    }

    companion object {
        const val KEY_THEME = "youflow_theme"
        const val KEY_DYNAMIC = "youflow_dynamic_color"
        const val KEY_AUTOPLAY = "youflow_autoplay"
        const val KEY_NAV_ORDER = "youflow_nav_order"
        const val KEY_SHOW_SHORTS = "youflow_show_shorts"
        const val KEY_FRAME_THUMBNAILS = "youflow_frame_thumbnails"
        const val KEY_HIDDEN_HOME_TOPICS = "youflow_hidden_home_topics"
        val NAV_TABS = listOf("Home", "Shorts", "Subscriptions", "You")
        private const val KEY_PALETTE_VERSION = "youflow_palette_version"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_BLACK = "black"

        @Volatile
        private var instance: AppSettings? = null

        fun get(app: Application): AppSettings = instance ?: synchronized(this) { instance ?: AppSettings(app).also { instance = it } }
    }
}

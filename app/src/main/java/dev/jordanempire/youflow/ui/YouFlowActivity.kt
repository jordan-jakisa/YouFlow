package dev.jordanempire.youflow.ui

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dev.jordanempire.youflow.local.feed.notifications.NotificationWorker
import dev.jordanempire.youflow.media.engine.PlaybackEngine
import dev.jordanempire.youflow.ui.settings.AppSettings
import dev.jordanempire.youflow.ui.theme.YouFlowTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class YouFlowActivity : ComponentActivity() {
    private val window2 = WatchWindowState()
    private lateinit var engine: PlaybackEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        engine = PlaybackEngine.get(application)
        setUpNotifications()
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            pipReceiver,
            android.content.IntentFilter(ACTION_PIP_TOGGLE),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            val settings = androidx.compose.runtime.remember { AppSettings.get(application) }
            val themeMode by settings.theme.collectAsState()
            val dynamic by settings.dynamicColor.collectAsState()
            val dark = when (themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK, AppSettings.THEME_BLACK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            YouFlowTheme(darkTheme = dark, dynamicColor = dynamic, pureBlack = themeMode == AppSettings.THEME_BLACK) {
                Surface {
                    YouFlowApp(window2)
                    // Fullscreen means landscape with the system bars hidden.
                    LaunchedEffect(Unit) {
                        snapshotFlow { window2.fullscreen }.collect { applyFullscreen(it) }
                    }
                }
            }
        }

        handleIntent(intent)

        // Auto enter PiP when leaving while a video is playing on the expanded watch page.
        lifecycleScope.launch {
            snapshotFlow { window2.expanded }.collect { updatePipParams() }
        }
        lifecycleScope.launch {
            engine.state.collect { updatePipParams() }
        }
    }

    /** Asks for the notification permission once, then schedules the new upload check. */
    private fun setUpNotifications() {
        val schedule = { NotificationWorker.initialize(this) }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { schedule() }
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            schedule()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Opens links shared from other apps or tapped in a browser. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == ACTION_OPEN_DOWNLOADS) {
            window2.incoming = IncomingLink.Downloads
            setIntent(Intent(this, YouFlowActivity::class.java))
            return
        }
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_SEARCH -> intent.getStringExtra(android.app.SearchManager.QUERY)
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() } ?: return
        window2.incoming = parseIncoming(text)
        // Clear the intent so rotating or recreating does not replay it.
        setIntent(Intent(this, YouFlowActivity::class.java))
    }

    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_TOGGLE) engine.togglePlayPause()
        }
    }

    private fun pipActions(): List<android.app.RemoteAction> {
        val paused = !engine.state.value.playWhenReady
        val pending = android.app.PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_PIP_TOGGLE).setPackage(packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (paused) "Play" else "Pause"
        return listOf(
            android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, if (paused) dev.jordanempire.youflow.R.drawable.ic_play_arrow else dev.jordanempire.youflow.R.drawable.ic_pause),
                title,
                title,
                pending
            )
        )
    }

    private fun updatePipParams() {
        val playing = engine.state.value.isPlaying && window2.expanded
        val aspect = engine.state.value.videoAspect.coerceIn(0.5f, 2.0f)
        setPictureInPictureParams(
            PictureInPictureParams.Builder()
                .setActions(pipActions())
                .setAspectRatio(Rational((aspect * 1000).toInt(), 1000))
                .setAutoEnterEnabled(playing)
                .setSeamlessResizeEnabled(true)
                .build()
        )
    }

    private fun applyFullscreen(fullscreen: Boolean) {
        requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        window2.inPip = isInPictureInPictureMode
    }

    override fun onDestroy() {
        unregisterReceiver(pipReceiver)
        super.onDestroy()
    }

    private companion object {
        const val ACTION_OPEN_DOWNLOADS = "dev.jordanempire.youflow.OPEN_DOWNLOADS"
        const val ACTION_PIP_TOGGLE = "dev.jordanempire.youflow.PIP_TOGGLE"
    }

    // Leave only audio running while the app is not visible.
    override fun onStop() {
        super.onStop()
        if (engine.state.value.isPlaying) engine.setVideoEnabled(false)
    }

    override fun onStart() {
        super.onStart()
        engine.setVideoEnabled(!engine.audioOnly.value)
    }
}

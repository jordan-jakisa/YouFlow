package dev.jordanempire.youflow.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.jordanempire.youflow.R
import dev.jordanempire.youflow.local.feed.notifications.NotificationHelper
import dev.jordanempire.youflow.local.feed.notifications.NotificationWorker
import dev.jordanempire.youflow.local.feed.notifications.ScheduleOptions
import dev.jordanempire.youflow.util.Localization

private fun durationNames(context: Context, raw: List<String>, values: List<String>) = raw.mapIndexed { i, name ->
    val seconds = values[i].toIntOrNull() ?: 0
    if (seconds <= 0) name else Localization.localizeDuration(context, seconds)
}

@Composable
fun NotificationsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationHelper.areNotificationsEnabledOnDevice(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = NotificationHelper.areNotificationsEnabledOnDevice(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var enabled by remember { mutableStateOf(NotificationHelper.areNewStreamsNotificationsEnabled(context)) }

    PrefScreen("Notifications", onBack) {
        if (!allowed) {
            item {
                ActionPref(
                    "Notifications are off for YouFlow",
                    "Tap to open the system settings and turn them on"
                ) { NotificationHelper.openNewPipeSystemNotificationSettings(context) }
            }
        }
        item {
            SwitchPref(R.string.enable_streams_notifications, R.string.enable_streams_notifications_title, false, R.string.enable_streams_notifications_summary, enabled = allowed) { on ->
                enabled = on
                if (on) NotificationWorker.schedule(context) else NotificationWorker.cancel(context)
            }
        }
        item {
            ListPref(
                R.string.streams_notifications_interval_key,
                R.string.streams_notifications_interval_title,
                R.array.streams_notifications_interval_description,
                R.array.streams_notifications_interval_values,
                R.string.streams_notifications_interval_default,
                enabled = allowed && enabled,
                entryNames = ::durationNames
            ) { NotificationWorker.schedule(context, ScheduleOptions.from(context), true) }
        }
        item {
            ListPref(
                R.string.streams_notifications_network_key,
                R.string.streams_notifications_network_title,
                R.array.streams_notifications_network_description,
                R.array.streams_notifications_network_values,
                R.string.streams_notifications_network_default,
                enabled = allowed && enabled
            ) { NotificationWorker.schedule(context, ScheduleOptions.from(context), true) }
        }
    }
}

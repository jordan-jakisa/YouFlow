package dev.jordanempire.youflow.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager

/** The classic default SharedPreferences, which the download engine, feed and extractor code read. */
@Composable
fun rememberPrefs(): SharedPreferences {
    val context = LocalContext.current
    return remember { PreferenceManager.getDefaultSharedPreferences(context.applicationContext) }
}

/** A settings page: top bar with back, then rows. */
@Composable
fun PrefScreen(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            content = content
        )
    }
}

@Composable
fun PrefHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

private fun Context.key(@StringRes id: Int) = getString(id)

@Composable
fun SwitchPref(
    @StringRes keyRes: Int,
    @StringRes titleRes: Int,
    default: Boolean,
    @StringRes summaryRes: Int? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = rememberPrefs()
    val key = context.key(keyRes)
    var checked by remember { mutableStateOf(prefs.getBoolean(key, default)) }
    ListItem(
        headlineContent = { Text(context.getString(titleRes)) },
        supportingContent = summaryRes?.let { { Text(context.getString(it)) } },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = {
                    checked = it
                    prefs.edit().putBoolean(key, it).apply()
                    onChange(it)
                }
            )
        },
        modifier = Modifier.clickable(enabled = enabled) {
            checked = !checked
            prefs.edit().putBoolean(key, checked).apply()
            onChange(checked)
        }
    )
}

/**
 * One choice out of a list. [entryNames] overrides the entries array when the labels are computed
 * (durations). The current choice is shown as the summary.
 */
@Composable
fun ListPref(
    @StringRes keyRes: Int,
    @StringRes titleRes: Int,
    @ArrayRes entriesRes: Int,
    @ArrayRes valuesRes: Int,
    @StringRes defaultRes: Int,
    @StringRes summaryRes: Int? = null,
    enabled: Boolean = true,
    entryNames: ((Context, List<String>, List<String>) -> List<String>)? = null,
    onChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = rememberPrefs()
    val key = context.key(keyRes)
    val values = remember { context.resources.getStringArray(valuesRes).toList() }
    val names = remember {
        val raw = context.resources.getStringArray(entriesRes).toList()
        entryNames?.invoke(context, raw, values) ?: raw
    }
    var current by remember { mutableStateOf(prefs.getString(key, null) ?: context.getString(defaultRes)) }
    var open by remember { mutableStateOf(false) }
    val currentName = names.getOrNull(values.indexOf(current)) ?: current
    ListItem(
        headlineContent = { Text(context.getString(titleRes)) },
        supportingContent = { Text(if (summaryRes != null) "${context.getString(summaryRes)}\n$currentName" else currentName) },
        modifier = Modifier.clickable(enabled = enabled) { open = true }
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(context.getString(titleRes)) },
            text = {
                Column {
                    values.forEachIndexed { index, value ->
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().clickable {
                                current = value
                                prefs.edit().putString(key, value).apply()
                                onChange(value)
                                open = false
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = value == current, onClick = null)
                            Text(names.getOrElse(index) { value }, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
        )
    }
}

/** Several choices out of a list, stored as a string set. Everything is on until the user changes it. */
@Composable
fun MultiPref(
    @StringRes keyRes: Int,
    @StringRes titleRes: Int,
    @ArrayRes entriesRes: Int,
    @ArrayRes valuesRes: Int,
    @StringRes summaryRes: Int? = null
) {
    val context = LocalContext.current
    val prefs = rememberPrefs()
    val key = context.key(keyRes)
    val values = remember { context.resources.getStringArray(valuesRes).toList() }
    val names = remember { context.resources.getStringArray(entriesRes).toList() }
    var selected by remember { mutableStateOf(prefs.getStringSet(key, null)?.toSet() ?: values.toSet()) }
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(context.getString(titleRes)) },
        supportingContent = summaryRes?.let { { Text(context.getString(it)) } },
        modifier = Modifier.clickable { open = true }
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(context.getString(titleRes)) },
            text = {
                Column {
                    values.forEachIndexed { index, value ->
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (value in selected) selected - value else selected + value
                                prefs.edit().putStringSet(key, selected).apply()
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = value in selected, onCheckedChange = null)
                            Text(names.getOrElse(index) { value }, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Done") } }
        )
    }
}

@Composable
fun TextPref(
    @StringRes keyRes: Int,
    @StringRes titleRes: Int,
    @StringRes defaultRes: Int,
    @StringRes summaryRes: Int? = null
) {
    val context = LocalContext.current
    val prefs = rememberPrefs()
    val key = context.key(keyRes)
    var value by remember { mutableStateOf(prefs.getString(key, null) ?: context.getString(defaultRes)) }
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }
    ListItem(
        headlineContent = { Text(context.getString(titleRes)) },
        supportingContent = {
            Text(listOfNotNull(summaryRes?.let { context.getString(it) }, value.ifEmpty { "(none)" }).joinToString("\n"))
        },
        modifier = Modifier.clickable {
            draft = value
            open = true
        }
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(context.getString(titleRes)) },
            text = { OutlinedTextField(draft, { draft = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    value = draft
                    prefs.edit().putString(key, draft).apply()
                    open = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        )
    }
}

/** A plain row that opens something. */
@Composable
fun ActionPref(title: String, summary: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}
